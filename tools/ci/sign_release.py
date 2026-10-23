"""Sign a CI APK using short-lived environment secrets; never persist passwords."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile


def run(*command):
    return subprocess.run(command, check=True, capture_output=True, text=True).stdout


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', required=True, type=Path)
    parser.add_argument('--output-dir', required=True, type=Path)
    parser.add_argument('--version', required=True)
    parser.add_argument('--build-tools', required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:[-.][A-Za-z0-9.-]+)?', args.version):
        raise ValueError('Invalid release version')
    secrets = {key: os.environ[key] for key in (
        'RELEASE_KEYSTORE_BASE64', 'RELEASE_STORE_PASSWORD', 'RELEASE_KEY_PASSWORD', 'RELEASE_KEY_ALIAS',
    )}
    if not all(secrets.values()):
        raise ValueError('Signing secrets are incomplete')
    suffix = '.exe' if os.name == 'nt' else ''
    aapt = str(args.build_tools / ('aapt' + suffix))
    zipalign = str(args.build_tools / ('zipalign' + suffix))
    signer = str(args.build_tools / 'lib/apksigner.jar')
    metadata = run(aapt, 'dump', 'badging', str(args.input))
    if f"versionName='{args.version}'" not in metadata or "name='com.bangdream.pet'" not in metadata:
        raise ValueError('APK package/version does not match the release')
    with zipfile.ZipFile(args.input) as apk:
        for name in ('libbangdreampet.so', 'libluajit.so', 'libzstd-jni-1.5.6-9.so'):
            if apk.getinfo('lib/arm64-v8a/' + name).file_size <= 0:
                raise ValueError('Missing native library')
    args.output_dir.mkdir(parents=True, exist_ok=True)
    output = args.output_dir / f'Live2dOnAndroid-{args.version}-release-arm64.apk'
    with tempfile.TemporaryDirectory(prefix='android-signing-') as directory:
        key = Path(directory) / 'release.keystore'
        key.write_bytes(base64.b64decode(secrets['RELEASE_KEYSTORE_BASE64'], validate=True))
        key.chmod(0o600)
        aligned = Path(directory) / 'aligned.apk'
        run(zipalign, '-f', '-P', '16', '4', str(args.input), str(aligned))
        run('java', '-jar', signer, 'sign', '--ks', str(key), '--ks-key-alias', secrets['RELEASE_KEY_ALIAS'],
            '--ks-pass', 'env:RELEASE_STORE_PASSWORD', '--key-pass', 'env:RELEASE_KEY_PASSWORD',
            '--out', str(output), str(aligned))
        verification = run('java', '-jar', signer, 'verify', '--verbose', '--print-certs', str(output))
        match = re.search(r'certificate SHA-256 digest:\s*([0-9a-fA-F]{64})', verification)
        certificate = match.group(1).lower() if match else ''
        if certificate != os.environ['EXPECTED_SIGNING_CERT_SHA256'].lower():
            output.unlink()
            raise ValueError('Signing certificate does not match existing releases')
        run(zipalign, '-c', '-P', '16', '4', str(output))
    # Ensure signing changed only signature records, never APK payload entries.
    with zipfile.ZipFile(args.input) as unsigned, zipfile.ZipFile(output) as signed:
        for entry in unsigned.namelist():
            if not entry.upper().startswith('META-INF/') and unsigned.read(entry) != signed.read(entry):
                output.unlink()
                raise ValueError('APK payload changed while signing')
    sha256 = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
    run_id = os.environ.get('GITHUB_RUN_ID', '')
    provenance = args.output_dir / 'build-provenance.json'
    provenance.write_text(json.dumps({
        'version': args.version,
        'commit': os.environ.get('GITHUB_SHA', ''),
        'workflow_run': f"https://github.com/{os.environ.get('GITHUB_REPOSITORY', '')}/actions/runs/{run_id}",
        'unsigned_apk_sha256': sha256(args.input),
        'signed_apk_sha256': sha256(output),
        'signing_certificate_sha256': certificate,
        'method': 'GitHub Actions build, zipalign and signing; APK payload preserved',
    }, indent=2) + '\n', encoding='utf-8')
    (args.output_dir / 'SHA256SUMS.txt').write_text(''.join(
        f'{sha256(path)}  {path.name}\n' for path in (output, provenance)
    ), encoding='utf-8')
    print(json.dumps({'apk': output.name, 'sha256': sha256(output), 'certificate_sha256': certificate}))


if __name__ == '__main__':
    main()
