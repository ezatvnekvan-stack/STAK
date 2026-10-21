#!/usr/bin/env python3
"""Expose original/native differences; never certify parity from a similarity score."""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageEnhance, ImageDraw, ImageStat

parser = argparse.ArgumentParser()
parser.add_argument('--native', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
reference = Path(__file__).parent / 'reference'
args.output.mkdir(parents=True, exist_ok=True)
rows = []
for original in sorted(reference.glob('*.png')):
    candidates = list(args.native.rglob(original.name))
    row = {'screen': original.name, 'status': 'native capture missing'}
    if candidates:
        a = Image.open(original).convert('RGB')
        b = Image.open(candidates[0]).convert('RGB')
        row.update(originalSize=list(a.size), nativeSize=list(b.size))
        if a.size != b.size:
            row['status'] = 'viewport mismatch; comparison invalid'
        else:
            delta = ImageChops.difference(a, b)
            row['meanAbsoluteChannelDifference'] = round(sum(ImageStat.Stat(delta).mean) / 3, 3)
            row['status'] = 'identical pixels' if delta.getbbox() is None else 'differences require review'
            canvas = Image.new('RGB', (a.width * 3, a.height + 30), '#222222')
            draw = ImageDraw.Draw(canvas)
            for x, label, picture in [(0, 'Original React reference', a), (a.width, 'Native Compose', b), (a.width * 2, 'Absolute difference (4x)', ImageEnhance.Brightness(delta).enhance(4))]:
                draw.text((x + 8, 8), label, fill='white')
                canvas.paste(picture, (x, 30))
            canvas.save(args.output / original.name)
    rows.append(row)
result = {'status': 'UNVERIFIED: this report does not establish visual or functional parity', 'screens': rows}
(args.output / 'report.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps({'referenceScreens': len(rows), 'captured': sum('nativeSize' in r for r in rows), 'viewportMismatches': sum(r['status'].startswith('viewport mismatch') for r in rows), 'visuallyVerified': False}))
