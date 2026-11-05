#!/bin/sh
set -eu
umask 077
cd "$(dirname "$0")"
docker compose config --quiet
docker compose build stak-server
mkdir -p data/backups
stamp=$(date -u +%Y%m%dT%H%M%SZ)
# SQLite online backups include WAL pages; never copy an active .db alone.
docker compose run --rm --no-deps -T stak-server python -c '
import sqlite3, pathlib, datetime
root=pathlib.Path("/data"); stamp=datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
for name in ("stak-watch.db","filter.db"):
 p=root/name
 if not p.exists(): continue
 import os
 target=root/"backups"/(stamp+"-"+name)
 src=sqlite3.connect("file:"+str(p)+"?mode=ro",uri=True); dst=sqlite3.connect(str(target))
 src.backup(dst); assert dst.execute("pragma integrity_check").fetchone()[0]=="ok"; dst.close(); src.close()
 os.chmod(target,0o600)
'
docker compose up -d --force-recreate stak-server
attempt=0
while [ "$attempt" -lt 30 ]; do
  if docker compose exec -T stak-server python -m server.admin status; then exit 0; fi
  attempt=$((attempt+1)); sleep 2
done
echo 'Server did not become healthy. Data/configuration and backups retained.' >&2
exit 1
