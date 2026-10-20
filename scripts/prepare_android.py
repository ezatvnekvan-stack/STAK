#!/usr/bin/env python3
"""Idempotently preserve LAN HTTP and backup protection after Capacitor sync."""
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parent.parent
assets = root / 'frontend/android/app/src/main/assets/public'
for path in assets.rglob('*'):
    if path.is_file() and (path.suffix == '.map' or path.name in {'sw.js', 'cordova.js', 'Dockerfile', 'nginx.conf'}):
        path.unlink()
manifest = root / 'frontend/android/app/src/main/AndroidManifest.xml'
if not manifest.exists(): raise SystemExit('Android project missing; restore the included project.')
namespace = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', namespace)
tree = ET.parse(manifest); app = tree.getroot().find('application')
if app is None: raise SystemExit('Application missing; no changes made.')
for key, value in {'usesCleartextTraffic':'true','networkSecurityConfig':'@xml/network_security_config','allowBackup':'false','fullBackupContent':'false','dataExtractionRules':'@xml/data_extraction_rules'}.items(): app.set('{'+namespace+'}'+key, value)
tree.write(manifest, encoding='utf-8', xml_declaration=True)
print('LAN HTTP config and backup protection applied. Public provider APIs use HTTPS.')
