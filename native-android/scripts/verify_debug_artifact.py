#!/usr/bin/env python3
"""Fail CI unless the debug APK and search regression reports are verifiable."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--variant', choices=['debug', 'preview'], default='debug')
variant = parser.parse_args().variant
apk = root / f'app/build/outputs/apk/{variant}/app-{variant}.apk'
sdk = Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ['ANDROID_HOME'])
tools = sdk / 'build-tools/35.0.0'

def run(*args):
    return subprocess.check_output([str(arg) for arg in args], text=True)

badging = run(tools / 'aapt', 'dump', 'badging', apk)
package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
if not package or package.groups() != ('app.stak.nativeapp', '6', '0.5.0-native-glass.1'):
    raise SystemExit('Unexpected application ID or version: ' + badging[:400])
debuggable = 'application-debuggable' in badging
if debuggable != (variant == 'debug'):
    raise SystemExit('Unexpected debuggable flag for ' + variant)
manifest = run(tools / 'aapt', 'dump', 'xmltree', apk, 'AndroidManifest.xml')
profileable = 'E: profileable' in manifest and 'android:shell' in manifest
if variant == 'preview':
    if not profileable or not (root / 'app/build/outputs/mapping/preview/mapping.txt').is_file():
        raise SystemExit('Preview must be profileable and have an R8 mapping')
signature = run(tools / 'apksigner', 'verify', '--verbose', '--print-certs', apk)
certificate = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)', signature)
if not certificate:
    raise SystemExit('APK signature verified but certificate digest is missing')
reports = sorted((root / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
if not reports:
    raise SystemExit('No native unit-test reports were produced')
tests = []
for report in reports:
    suite = ET.parse(report).getroot()
    tests.extend(suite.iter('testcase'))
    if int(suite.get('failures', 0)) or int(suite.get('errors', 0)):
        raise SystemExit('Native unit tests failed: ' + str(report))
regressions = [test for test in tests if test.get('classname') == 'app.stak.nativeapp.SearchAliasTest']
expected = {
    'localizedAndAlternativeTitlesRetainOriginalRanking',
    'numericAliasIsAnExactTitleBeforeYearFiltering',
    'titleVariantsSurviveSerializationAndEnrichment',
    'tmdbOriginalNamesAndNestedAlternativesArePreserved',
    'releaseDateFallbackAndEmptyTitlesMatchReferenceScores',
}
if {test.get('name') for test in regressions} != expected or len(regressions) != 5:
    raise SystemExit('All five search regression tests must execute')
if any(test.find('skipped') is not None or test.find('failure') is not None or test.find('error') is not None for test in regressions):
    raise SystemExit('A search regression was skipped or failed')
output = root / 'app/build/verification'
if variant == 'preview': output = output / variant
output.mkdir(parents=True, exist_ok=True)
(output / 'apk-badging.txt').write_text(badging)
(output / 'apk-signature.txt').write_text(signature)
(output / 'apk-manifest.txt').write_text(manifest)
summary = {
    'variant': variant, 'debuggable': debuggable, 'profileable': profileable, 'applicationId': package[1], 'versionCode': int(package[2]), 'versionName': package[3],
    'apkSha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
    'certificateSha256': certificate[1].lower(),
    'unitTests': len(tests), 'searchRegressions': len(regressions),
    'updateCompatibility': 'UNVERIFIED: compare the installed certificate before updating',
    'commit': os.environ.get('GITHUB_SHA', 'local'),
}
(output / 'apk-verification.json').write_text(json.dumps(summary, indent=2) + '\n')
(output / 'SHA256SUMS').write_text(summary['apkSha256'] + f'  app-{variant}.apk\n')
print(json.dumps(summary, indent=2))
