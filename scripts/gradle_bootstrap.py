#!/usr/bin/env python3
"""Download official Gradle distribution with SHA-256 verification; no vendored JAR."""
from pathlib import Path
import hashlib, os, subprocess, sys, tempfile, urllib.request, zipfile
version = '8.11.1'
base = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'dmm-distributions'
runner = base / f'gradle-{version}' / 'bin' / ('gradle.bat' if os.name == 'nt' else 'gradle')
if not runner.exists():
    base.mkdir(parents=True, exist_ok=True)
    url = f'https://services.gradle.org/distributions/gradle-{version}-bin.zip'
    expected = urllib.request.urlopen(url + '.sha256', timeout=30).read().decode().strip().split()[0]
    with tempfile.TemporaryDirectory(dir=base) as temp:
        archive = Path(temp) / 'gradle.zip'
        with urllib.request.urlopen(url, timeout=60) as response, archive.open('wb') as target:
            import shutil
            shutil.copyfileobj(response, target)
        if hashlib.sha256(archive.read_bytes()).hexdigest() != expected:
            raise SystemExit('Gradle checksum mismatch; download rejected.')
        with zipfile.ZipFile(archive) as z:
            for name in z.namelist():
                if not (base / name).resolve().is_relative_to(base.resolve()): raise SystemExit('Unsafe archive entry')
            z.extractall(base)
    runner.chmod(0o755)
raise SystemExit(subprocess.call([str(runner), *sys.argv[1:]]))
