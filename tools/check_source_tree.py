"""Validate the tracked public source tree without printing credential values."""
from pathlib import Path
import re
import subprocess
import sys

root=Path(__file__).resolve().parents[1]
names=subprocess.check_output(['git','ls-files','-z'],cwd=root).decode().split('\0')
bad=[]
private_names={'cloud-config.env','local.properties','keystore.properties','secrets.properties','torznab.json','migration.json','migration.json.new','HANDOFF-MANIFEST.json'}
private_dirs={'node_modules','frontend','.gradle','.kotlin','.idea','build','signing','distribution','delivery','outputs','__pycache__','.pytest_cache','.freebuff'}
extensions={'.apk','.aab','.keystore','.jks','.p12','.pem','.key','.db','.sqlite','.sqlite3','.zip','.gz','.pyc','.log'}
patterns=[re.compile(rb'gh[pousr]_[A-Za-z0-9]{30,}'),re.compile(rb'github_pat_[A-Za-z0-9_]{35,}'),re.compile(rb'sb_secret_[A-Za-z0-9_-]{20,}'),re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----')]
for name in filter(None,names):
    path=Path(name)
    if path.parts[:3]==("deployment","stak-server","data") or path.name in private_names or set(path.parts)&private_dirs or path.suffix.lower() in extensions or (path.name.startswith('.env') and path.name!='.env.example'):
        bad.append((name,'private/generated path'));continue
    f=root/path
    if not f.is_file():continue
    raw=f.read_bytes()
    if any(pattern.search(raw) for pattern in patterns):bad.append((name,'credential/private-key pattern'))
    if path.suffix in {'.kt','.kts','.md','.sh','.ps1','.json','.yml','.yaml','.xml','.properties','.py','.sql'}:
        try:raw.decode('utf-8-sig')
        except UnicodeDecodeError:bad.append((name,'text is not UTF-8'))
        if b'\0' in raw:bad.append((name,'NUL bytes in source text'))
if bad:
    for name,reason in bad:print(f'{name}: {reason}')
    sys.exit(1)
print(f'Public source checks passed ({len(list(filter(None,names)))} tracked files).')
