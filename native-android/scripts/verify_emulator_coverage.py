#!/usr/bin/env python3
"""Require every source-declared UI test, without failures, skips or omissions."""
from pathlib import Path
import re
import xml.etree.ElementTree as E

root = Path(__file__).resolve().parents[2]
expected = set()
for source in (root / 'native-android/app/src/androidTest/java').rglob('*.kt'):
    text = source.read_text()
    cls = re.search(r'^class\s+(\w+)', text, re.M)
    if cls:
        expected.update((f'app.stak.nativeapp.{cls[1]}', name)
            for name in re.findall(r'@Test\s+fun\s+(\w+)\s*\(', text))
assert expected, 'No source-declared UI tests found'
motion = {t for t in expected if t[0].endswith('.NativeMotionTest')}
small_classes = {'OriginalReferenceTest', 'BetaUiTest', 'SourceFlowTest', 'SourcesMotionTest', 'GlassUiTest'}
small = {t for t in expected if t[0].rsplit('.', 1)[-1] in small_classes}
for variant in ('debug', 'preview'):
    executed = set()
    for phase, required in [('first-pass', expected - motion), ('second-pass', small), ('motion-pass', motion)]:
        reports = list((root / f'native-evidence/{variant}/{phase}/results').rglob('TEST-*.xml'))
        assert reports, f'{variant}/{phase}: missing reports'
        found = []
        for report in reports:
            suite = E.parse(report).getroot()
            assert all(not int(suite.get(key, 0)) for key in ('failures', 'errors', 'skipped')), str(report)
            for test in suite.iter('testcase'):
                assert all(test.find(tag) is None for tag in ('failure', 'error', 'skipped')), str(test.attrib)
                found.append((test.get('classname'), test.get('name')))
        assert len(found) == len(set(found)), f'{variant}/{phase}: duplicate cases'
        assert set(found) == required, f'{variant}/{phase}: missing {required - set(found)}, unexpected {set(found) - required}'
        executed.update(found)
        print(f'{variant}/{phase}: {len(found)} passed; 0 failures/errors/skips')
    assert executed == expected
    print(f'{variant}: all {len(expected)} source-declared UI tests executed')
