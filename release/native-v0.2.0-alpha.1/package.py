"""Publish one explicitly authorized preview from an immutable successful CI artifact.

No compilation or signing keys are involved. The APK remains byte-identical to CI.
"""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile

REPO = 'ezatvnekvan-stack/STAK'
SOURCE = '96002dc3ac0cb42739cfd6af74081028a96d9f3c'
RUN = 39550855542
ARTIFACT = 12323344291
RELEASE = 419202556
TAG = 'native-v0.2.0-alpha.1'
APK_SHA = 'd6918521754ae0e80250b8af6f8f4e877ddd4e5344dfca68abedf784aea66500'


def run(*args, output=None):
    return subprocess.run(args, check=True, stdout=output or subprocess.PIPE,
                          text=output is None).stdout


def api(path, *args):
    return json.loads(run('gh', 'api', f'repos/{REPO}/{path}', *args))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def arm64_alignment(apk):
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if not name.startswith('lib/arm64-v8a/') or not name.endswith('.so'):
                continue
            blob = archive.read(name)
            assert blob[:6] == b'\x7fELF\x02\x01', name
            offset = struct.unpack_from('<Q', blob, 32)[0]
            size, count = struct.unpack_from('<HH', blob, 54)
            aligns = [struct.unpack_from('<Q', blob, offset+i*size+48)[0]
                      for i in range(count)
                      if struct.unpack_from('<I', blob, offset+i*size)[0] == 1]
            assert aligns and all(value >= 16384 for value in aligns), name


def main(output, prepare_only=False, existing_input=None):
    work = Path(output).resolve()
    incoming = Path(existing_input).resolve() if existing_input else work/'input'
    stage = work/'output'
    incoming.mkdir(parents=True, exist_ok=True)
    stage.mkdir(parents=True, exist_ok=True)
    provenance = api(f'actions/runs/{RUN}')
    assert provenance['conclusion'] == 'success'
    assert provenance['head_sha'] == SOURCE
    assert provenance['head_repository']['full_name'] == REPO
    artifact = api(f'actions/artifacts/{ARTIFACT}')
    assert not artifact['expired'] and artifact['name'] == 'STAK-native-validation'
    assert artifact['workflow_run']['id'] == RUN
    draft = api(f'releases/{RELEASE}')
    assert draft['draft'] and draft['prerelease']
    assert draft['tag_name'] == TAG and draft['target_commitish'] == SOURCE
    if not existing_input:
        run('gh', 'run', 'download', str(RUN), '--repo', REPO,
            '--name', 'STAK-native-validation', '--dir', str(incoming))

    report = json.loads((incoming/'native-comparisons/report.json').read_text())
    assert len(report['screens']) == 88
    assert all(item.get('nativeSize') == item.get('originalSize') for item in report['screens'])
    suites = [ET.parse(p).getroot() for p in
              (incoming/'native-evidence/first-pass/results').rglob('TEST-*.xml')]
    assert sum(int(s.attrib['tests']) for s in suites) == 10
    assert all(int(s.attrib['failures']) == int(s.attrib['errors']) == 0 for s in suites)
    video = incoming/'native-evidence/STAK-native-evidence/native-motion.mp4'
    assert video.stat().st_size > 0
    original = incoming/'native-android/app/build/outputs/apk/debug/app-debug.apk'
    assert sha(original) == APK_SHA
    apk = stage/'STAK-Native-0.2.0-alpha.1.apk'
    shutil.copyfile(original, apk)
    assert sha(apk) == APK_SHA
    sdk = Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
    cert = run(str(sdk/'apksigner'), 'verify', '--print-certs', str(apk))
    (stage/'APK_CERTIFICATE.txt').write_text(cert)
    identity = run(str(sdk/'aapt'), 'dump', 'badging', str(apk))
    assert "name='app.stak.nativeapp' versionCode='2' versionName='0.2.0-native-alpha.1'" in identity
    (stage/'APK_IDENTITY.txt').write_text(identity)
    run(str(sdk/'zipalign'), '-c', '-P', '16', '4', str(apk))
    arm64_alignment(apk)
    with zipfile.ZipFile(apk) as z:
        assert not any('provider-fixture' in name for name in z.namelist())
        entries = len(z.namelist())
    (stage/'APK_PAYLOAD_VERIFICATION.json').write_text(json.dumps({
        'sourceCommit': SOURCE, 'runId': RUN, 'artifactId': ARTIFACT,
        'sourceSHA256': APK_SHA, 'releaseSHA256': sha(apk),
        'identicalPayloadEntries': entries,
        'status': 'The complete APK is byte-identical to the validated CI artifact.'
    }, indent=2)+'\n')
    notes = Path(__file__).resolve().parent
    for name in ['RELEASE_NOTES.md', 'VALIDATION.md']:
        shutil.copyfile(notes/name, stage/name)
    shutil.copyfile(video, stage/'STAK-Native-0.2.0-motion.mp4')
    with (stage/'STAK-Native-0.2.0-source.zip').open('wb') as stream:
        run('git', 'archive', '--format=zip', SOURCE, 'native-android',
            '.github/workflows/native-android.yml', 'scripts/capture-original-reference.cjs', output=stream)
    epoch = int(run('git', 'show', '-s', '--format=%ct', SOURCE).strip())
    zip_time = datetime.datetime.fromtimestamp(epoch, datetime.timezone.utc).timetuple()[:6]
    with zipfile.ZipFile(stage/'STAK-Native-0.2.0-evidence.zip', 'w', zipfile.ZIP_DEFLATED) as z:
        def add(name, data):
            entry = zipfile.ZipInfo(str(name), date_time=zip_time)
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            z.writestr(entry, data)
        for p in sorted((incoming/'native-evidence').rglob('*')):
            if p.is_file():
                add(p.relative_to(incoming), p.read_bytes())
        add('comparison-report.json', (incoming/'native-comparisons/report.json').read_bytes())
        add('reference-manifest.json', (incoming/'native-android/parity/reference-manifest.json').read_bytes())
        for p in sorted((incoming/'native-android/app/build/reports').rglob('*')):
            if p.is_file() and ('tests' in p.parts or p.name.startswith('lint-results')):
                add(p.relative_to(incoming), p.read_bytes())
        add('TEST_ONLY_FIXTURES.txt',
                   'Provider data and artwork are test fixtures, not live account or device-performance evidence.\n')
    files = sorted(p for p in stage.iterdir() if p.is_file() and p.name != 'SHA256SUMS.txt')
    (stage/'SHA256SUMS.txt').write_text(''.join(f'{sha(p)}  {p.name}\n' for p in files))
    files = sorted(stage.iterdir())
    assert len(files) == 10
    if prepare_only:
        print('Prepared 10 verified release files:', stage)
        return
    draft = api(f'releases/{RELEASE}')
    assert draft['draft'] and draft['tag_name'] == TAG and draft['target_commitish'] == SOURCE
    existing = {item['name']: item for item in draft['assets']}
    assert set(existing).issubset({p.name for p in files})
    for p in files:
        if p.name in existing:
            assert existing[p.name]['digest'] == 'sha256:'+sha(p), p.name
    pending = [str(p) for p in files if p.name not in existing]
    if pending:
        run('gh', 'release', 'upload', TAG, '--repo', REPO, *pending)
    uploaded = api(f'releases/{RELEASE}')
    assets = {item['name']: item for item in uploaded['assets']}
    assert len(assets) == len(files)
    for p in files:
        assert assets[p.name]['size'] == p.stat().st_size
        assert assets[p.name]['digest'] == 'sha256:'+sha(p), p.name
    patch = work/'publish.json'
    patch.write_text(json.dumps({'draft': False, 'prerelease': True, 'make_latest': 'false'}))
    published = api(f'releases/{RELEASE}', '--method', 'PATCH', '--input', str(patch))
    assert not published['draft'] and published['prerelease']
    print('Published verified native preview:', published['html_url'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True)
    parser.add_argument('--prepare-only', action='store_true')
    parser.add_argument('--input', help='Existing downloaded evidence; allowed only with --prepare-only')
    args = parser.parse_args()
    if args.input and not args.prepare_only:
        parser.error('--input requires --prepare-only')
    main(args.output, args.prepare_only, args.input)
