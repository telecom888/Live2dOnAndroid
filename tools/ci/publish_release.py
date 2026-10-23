"""Publish only a draft whose attached files match the signed CI artifacts."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess


def gh(*args, check=True):
    return subprocess.run(['gh', *args], check=check, capture_output=True, text=True)


def api(path):
    return json.loads(gh('api', path).stdout)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--directory', required=True, type=Path)
    parser.add_argument('--version', required=True)
    args = parser.parse_args()
    repo = os.environ['GITHUB_REPOSITORY']
    commit = os.environ['GITHUB_SHA']
    provenance = json.loads((args.directory / 'build-provenance.json').read_text())
    if provenance['commit'] != commit or provenance['version'] != args.version:
        raise ValueError('Provenance does not match this workflow')
    tag = 'v' + args.version
    if os.environ['GITHUB_REF'].startswith('refs/tags/') and os.environ['GITHUB_REF'] != 'refs/tags/' + tag:
        raise ValueError('Git tag does not match the APK version')
    # Existing tags must point to this exact commit; never move a release tag.
    ref = gh('api', f'repos/{repo}/git/ref/tags/{tag}', check=False)
    if ref.returncode == 0:
        obj = json.loads(ref.stdout)['object']
        while obj['type'] == 'tag':
            obj = api(f"repos/{repo}/git/tags/{obj['sha']}")['object']
        if obj['sha'] != commit:
            raise ValueError('Existing tag points to another commit')
    existing = gh('release', 'view', tag, '--repo', repo, '--json', 'isDraft', check=False)
    if existing.returncode == 0 and not json.loads(existing.stdout)['isDraft']:
        raise ValueError('Release is already published; refusing to overwrite it')
    notes = args.directory / 'release-notes.md'
    notes.write_text(Path('.github/release-notes.md').read_text(encoding='utf-8') +
        f"\n\n构建来源：{provenance['workflow_run']}\n\n提交：`{commit}`\n", encoding='utf-8')
    if existing.returncode != 0:
        gh('release', 'create', tag, '--repo', repo, '--target', commit, '--draft',
            '--title', f'Live2dOnAndroid {args.version}', '--notes-file', str(notes))
    files = [args.directory / f'Live2dOnAndroid-{args.version}-release-arm64.apk',
        args.directory / 'SHA256SUMS.txt', args.directory / 'build-provenance.json']
    gh('release', 'upload', tag, '--repo', repo, '--clobber', *(str(path) for path in files))
    release = api(f'repos/{repo}/releases/tags/{tag}')
    if not release['draft']:
        raise ValueError('Release unexpectedly became public before verification')
    assets = {asset['name']: asset for asset in release['assets']}
    for path in files:
        asset = assets.get(path.name, {})
        digest = 'sha256:' + hashlib.sha256(path.read_bytes()).hexdigest()
        if asset.get('size') != path.stat().st_size or asset.get('digest') != digest:
            raise ValueError('Uploaded asset failed size/SHA256 verification: ' + path.name)
    gh('release', 'edit', tag, '--repo', repo, '--draft=false', '--latest', '--notes-file', str(notes))
    print(api(f'repos/{repo}/releases/tags/{tag}')['html_url'])


if __name__ == '__main__':
    main()
