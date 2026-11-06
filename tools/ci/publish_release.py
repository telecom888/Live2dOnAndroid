"""Publish only a draft whose attached files match the signed CI artifacts."""
import argparse
import hashlib
import json
import os
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen


def api(path, method='GET', body=None, upload=None):
    token = os.environ.get('GH_TOKEN') or os.environ['GITHUB_TOKEN']
    host = 'https://uploads.github.com/' if upload is not None else 'https://api.github.com/'
    headers = {
        'Authorization': 'Bearer ' + token,
        'User-Agent': 'Live2dOnAndroid-release',
        'Accept': 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
    }
    data = None
    if upload is not None:
        data = upload.read_bytes()
        headers['Content-Type'] = 'application/octet-stream'
    elif body is not None:
        data = json.dumps(body).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    request = Request(host + path, data=data, headers=headers, method=method)
    with urlopen(request, timeout=180) as response:
        raw = response.read()
    return json.loads(raw) if raw else None


def release_for_tag(repo, tag):
    # Drafts can have no Git tag. Use the numeric release ID for all mutations.
    page = 1
    while True:
        releases = api(f'repos/{repo}/releases?per_page=100&page={page}')
        for release in releases:
            if release['tag_name'] == tag:
                return release
        if len(releases) < 100:
            return None
        page += 1


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
    # Never move a release tag, including an annotated tag.
    try:
        ref = api(f'repos/{repo}/git/ref/tags/{tag}')
    except HTTPError as error:
        if error.code != 404:
            raise
    else:
        obj = ref['object']
        while obj['type'] == 'tag':
            obj = api(f"repos/{repo}/git/tags/{obj['sha']}")['object']
        if obj['sha'] != commit:
            raise ValueError('Existing tag points to another commit')
    existing = release_for_tag(repo, tag)
    if existing is not None and not existing['draft']:
        raise ValueError('Release is already published; refusing to overwrite it')
    if existing is not None and existing['target_commitish'] != commit:
        raise ValueError('Draft release targets another commit')
    notes = Path('.github/release-notes.md').read_text(encoding='utf-8') + (
        f"\n\n构建来源：{provenance['workflow_run']}\n\n提交：`{commit}`\n"
    )
    if existing is None:
        # Keep the creation response: a list/tag lookup may not see a new draft yet.
        existing = api(f'repos/{repo}/releases', 'POST', {
            'tag_name': tag, 'target_commitish': commit, 'draft': True,
            'name': args.version, 'body': notes,
        })
    release_path = f"repos/{repo}/releases/{existing['id']}"
    files = [args.directory / f'Live2dOnAndroid-{args.version}-release-arm64.apk',
        args.directory / 'build-provenance.json']
    assets = {asset['name']: asset for asset in api(release_path)['assets']}
    for path in files:
        old = assets.get(path.name)
        if old is not None:
            api(f"repos/{repo}/releases/assets/{old['id']}", 'DELETE')
        api(release_path + '/assets?name=' + quote(path.name, safe=''), 'POST', upload=path)
    release = api(release_path)
    if not release['draft']:
        raise ValueError('Release unexpectedly became public before verification')
    assets = {asset['name']: asset for asset in release['assets']}
    for path in files:
        asset = assets.get(path.name, {})
        digest = 'sha256:' + hashlib.sha256(path.read_bytes()).hexdigest()
        if asset.get('size') != path.stat().st_size or asset.get('digest') != digest:
            raise ValueError('Uploaded asset failed size/SHA256 verification: ' + path.name)
    published = api(release_path, 'PATCH', {'draft': False, 'make_latest': 'true', 'body': notes, 'name': args.version})
    if published['draft']:
        raise ValueError('Release is still a draft')
    print(published['html_url'])


if __name__ == '__main__':
    main()
