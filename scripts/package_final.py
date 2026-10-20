#!/usr/bin/env python3
"""Package sources and verify archive integrity/extraction; no dependencies/secrets/caches."""
from pathlib import Path
import hashlib, json, tempfile, zipfile
root = Path(__file__).resolve().parent.parent
output = root.parent / 'DMM-Studio-Android-FINAL.zip'
excluded_dirs = {'node_modules', '.git', '.gradle', '__pycache__', '.cache', 'coverage', 'build', 'dist', '.idea'}
excluded_names = {'local.properties', '.DS_Store', 'Thumbs.db'}
excluded_suffixes = {'.pyc', '.jks', '.keystore', '.p12', '.pfx', '.pem'}
report = root / 'validation/archive-check.json'
manifest = root / 'SOURCE_MANIFEST.sha256'
def allowed(p):
    rel = p.relative_to(root)
    return not any(x in excluded_dirs for x in rel.parts) and p.name not in excluded_names and not p.name.startswith('.env') and p.suffix.lower() not in excluded_suffixes and not p.name.endswith('.zip')
def sources():
    return sorted(p for p in root.rglob('*') if p.is_file() and allowed(p))
def build():
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for p in sources(): z.write(p, root.name + '/' + p.relative_to(root).as_posix())
def verify():
    with zipfile.ZipFile(output) as z:
        assert z.testzip() is None, 'ZIP CRC failure'
        with tempfile.TemporaryDirectory(prefix='dmm-final-verify-') as temp:
            base = Path(temp)
            for n in z.namelist(): assert (base/n).resolve().is_relative_to(base.resolve()), 'Unsafe archive path'
            z.extractall(base)
            expected = sources()
            assert len(z.namelist()) == len(expected), 'Missing/extra archive entries'
            for p in expected:
                extracted = base / root.name / p.relative_to(root)
                assert extracted.is_file(), 'Missing source file'
                assert hashlib.sha256(extracted.read_bytes()).digest() == hashlib.sha256(p.read_bytes()).digest(), 'File changed during packaging'
            for rel in ['CHANGELOG.md','TEST_RESULTS.md','FEATURE_VERIFICATION.md','BUILD_ANDROID.md','SECURITY.md','frontend/package.json','frontend/src/App.js','frontend/capacitor.config.json','frontend/android/app/src/main/AndroidManifest.xml','frontend/android/app/src/main/java/app/dmmstudio/cinema/DMMVaultPlugin.java','frontend/android/app/src/main/java/app/dmmstudio/cinema/DMMTorrentUploadPlugin.java','scripts/tests/adapters.test.mjs']:
                assert (base/root.name/rel).is_file(), f'Required file missing: {rel}'
    return len(sources())
# Report avoids a circular final-archive checksum. Verify twice, including the final report.
report.parent.mkdir(parents=True, exist_ok=True)
report.write_text(json.dumps({'zip_crc':'pending','extraction':'pending'},indent=2)+'\n')
files = [p for p in sources() if p not in {manifest,report}]
manifest.write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(root).as_posix()}\n' for p in files))
build(); count = verify()
report.write_text(json.dumps({'archive_name':output.name,'zip_crc':'PASS','extraction':'PASS','all_packaged_files_byte_compared':'PASS','required_sources_and_documentation':'PASS','file_count':count,'excluded_dependencies_caches_env_secrets_signing_material':'PASS','compiled_apk_included':(root/'artifacts/DMM-Studio-debug.apk').is_file(),'manifest':'SOURCE_MANIFEST.sha256 covers files except itself and archive-check.json; no self-referential archive checksum.'},indent=2)+'\n')
build(); final_count = verify(); assert final_count == count
print(json.dumps({'archive':str(output),'bytes':output.stat().st_size,'files':final_count,'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'integrity':'PASS','extraction':'PASS'}))
