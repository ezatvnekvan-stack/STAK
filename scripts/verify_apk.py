#!/usr/bin/env python3
"""Inspect the compiled standalone APK; does not replace installation/device testing."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import sys
from urllib.parse import urlsplit
import zipfile

apk = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('frontend/android/app/build/outputs/apk/debug/app-debug.apk')
if not apk.is_file():
    raise SystemExit(f'Compiled APK missing: {apk}')
with zipfile.ZipFile(apk) as archive:
    if archive.testzip() is not None:
        raise SystemExit('APK CRC verification failed')
    names = set(archive.namelist())
    required = {'AndroidManifest.xml', 'classes.dex', 'assets/public/index.html', 'assets/capacitor.config.json'}
    missing = required - names
    if missing:
        raise SystemExit(f'APK entries missing: {sorted(missing)}')
    config = json.loads(archive.read('assets/capacitor.config.json'))
    if config.get('appName') != 'STAK':
        raise SystemExit('APK display name must be STAK')
    if config.get('appId') != 'app.dmmstudio.cinema' or config.get('server', {}).get('url'):
        raise SystemExit('APK package configuration is incorrect or depends on a hosted frontend')
    if config.get('android', {}).get('adjustMarginsForEdgeToEdge') != 'auto':
        raise SystemExit('APK must enable automatic Android 15 system-bar margins')
    html = archive.read('assets/public/index.html').decode('utf-8')
    if "frame-src https://www.youtube-nocookie.com https://www.youtube.com;" not in html:
        raise SystemExit('APK must restrict trailer frames to official YouTube hosts')
    if "script-src 'self' 'wasm-unsafe-eval';" not in html or "'unsafe-eval'" in html:
        raise SystemExit('APK must allow the bundled WASM without enabling JavaScript eval or remote scripts')
    for name, magic in [('rive.wasm', b'\x00asm'), ('rive_fallback.wasm', b'\x00asm'), ('theme-switch.riv', b'RIVE')]:
        asset = f'assets/public/animations/{name}'
        if asset not in names or not archive.read(asset).startswith(magic):
            raise SystemExit(f'Bundled Rive asset missing or invalid: {name}')
    notices = 'assets/public/open-source-notices.txt'
    if notices not in names or b'MIT License' not in archive.read(notices):
        raise SystemExit('APK must retain open-source UI license notices')
    scripts = []
    for value in re.findall(r'(?:src|href)=[\"\']([^\"\']+)', html):
        parsed = urlsplit(value)
        if parsed.scheme or parsed.netloc or not parsed.path:
            continue
        path = PurePosixPath(parsed.path.lstrip('/'))
        if '..' in path.parts or f'assets/public/{path}' not in names:
            raise SystemExit(f'APK web asset missing or unsafe: {path}')
        if str(path).endswith('.js'):
            scripts.append(str(path))
    if not scripts:
        raise SystemExit('APK does not contain a bundled React script')
    dex = b''.join(archive.read(name) for name in names if re.fullmatch(r'classes\d*\.dex', name))
    for plugin in (b'DMMVaultPlugin', b'DMMTorrentUploadPlugin'):
        if plugin not in dex:
            raise SystemExit(f'Native plugin missing from compiled dex: {plugin.decode()}')
print(json.dumps({'apk': str(apk), 'bytes': apk.stat().st_size, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'crc': 'PASS', 'standalone_web_assets': 'PASS', 'compiled_custom_plugins': 'PASS', 'youtube_only_trailer_frame_policy': 'PASS'}))
