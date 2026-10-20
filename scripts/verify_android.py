#!/usr/bin/env python3
from pathlib import Path
import json, xml.etree.ElementTree as ET
root = Path(__file__).resolve().parent.parent
android = root / 'frontend/android'
ns = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(android / 'app/src/main/AndroidManifest.xml').getroot()
app = manifest.find('application')
assert app.get(ns+'usesCleartextTraffic') == 'true'
assert app.get(ns+'networkSecurityConfig') == '@xml/network_security_config'
assert app.get(ns+'allowBackup') == 'false'
assert app.get(ns+'dataExtractionRules') == '@xml/data_extraction_rules'
permissions = {x.get(ns+'name') for x in manifest.findall('uses-permission')}
assert permissions == {'android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE'}
network = ET.parse(android/'app/src/main/res/xml/network_security_config.xml').getroot()
assert network.find('base-config').get('cleartextTrafficPermitted') == 'true'
assert network.find('domain-config').get('cleartextTrafficPermitted') == 'false'
assert {e.get('src') for e in network.findall('.//certificates')} == {'system'}
config = json.loads((root/'frontend/capacitor.config.json').read_text())
assert config['appName'] == 'STAK'
assert 'url' not in config.get('server', {})
assert config['server']['androidScheme'] == 'https'
assert config['android']['allowMixedContent'] is False
assert config['android']['adjustMarginsForEdgeToEdge'] == 'auto', 'Android 15 system bars must inset the WebView'
assert config['plugins']['CapacitorHttp']['enabled'] is False
main = (android/'app/src/main/java/app/dmmstudio/cinema/MainActivity.java').read_text()
for plugin in ['DMMVaultPlugin','DMMTorrentUploadPlugin']:
    assert f'registerPlugin({plugin}.class)' in main
    assert (android/f'app/src/main/java/app/dmmstudio/cinema/{plugin}.java').exists()
for xml in android.rglob('*.xml'): ET.parse(xml)
for element in app.iter():
    for attr, value in element.attrib.items():
        if value.startswith('@xml/'):
            assert (android / f'app/src/main/res/xml/{value[5:]}.xml').exists()
variables = (android / 'variables.gradle').read_text()
import re
minimum = int(re.search(r'minSdkVersion\s*=\s*(\d+)', variables)[1])
assert minimum >= 24, 'Cordova Android 13 requires minimum API 24'
print('Android manifest, permissions, XML resources, native plugins and Capacitor standalone configuration: PASS')
