#!/usr/bin/env python3
"""Compare verified APK public certificates; never read/export private keys."""
import json
from pathlib import Path
import re
import subprocess
import sys

signer, current_path, previous_path, output = sys.argv[1:5]
def certificates(path):
    result = subprocess.run([signer, 'verify', '--print-certs', str(path)], check=True, capture_output=True, text=True)
    digests = sorted(re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)', result.stdout))
    if not digests:
        raise RuntimeError('Verified APK returned no signer certificate digest')
    return [value.lower() for value in digests]

current = certificates(current_path)
previous = certificates(previous_path) if Path(previous_path).is_file() else None
compatible = current == previous if previous is not None else None
report = {
    'previous_release': sys.argv[5] if len(sys.argv)>5 else 'v2.1.0',
    'current_public_certificates_sha256': current,
    'previous_public_certificates_sha256': previous,
    'same_signing_certificate': compatible,
    'status': 'MATCH' if compatible is True else 'DIFFERENT_SIGNER' if compatible is False else 'UNVERIFIED',
    'installation_note': 'Certificate matches the compared release; normal Android package/version/device restrictions still apply.' if compatible is True else 'Do not uninstall or clear data to bypass a signing mismatch. Build with the original matching signing key for an in-place update. The CI debug APK is not confirmed as an in-place update.',
}
Path(output).parent.mkdir(parents=True, exist_ok=True)
Path(output).write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report))
