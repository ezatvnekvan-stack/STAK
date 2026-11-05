import importlib.util
import json
import os
import sqlite3
from pathlib import Path

import pytest

spec = importlib.util.spec_from_file_location("migration", Path(__file__).parents[1] / "migrate.py")
migration = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migration)


def test_online_backup_includes_wal_and_keeps_source(tmp_path):
    src = tmp_path / "original.db"
    connection = sqlite3.connect(src)
    connection.execute("pragma journal_mode=wal")
    connection.execute("create table items(id integer)")
    connection.execute("insert into items values(17)")
    connection.commit()
    destination = tmp_path / "snapshot.db"
    migration.backup_db(src, destination)
    backup = sqlite3.connect(destination)
    assert backup.execute("select * from items").fetchall() == [(17,)]
    assert connection.execute("select * from items").fetchall() == [(17,)]
    assert destination.stat().st_mode & 0o077 == 0
    backup.close()
    connection.close()


def test_host_path_uses_most_specific_persistent_mount():
    container = {"Mounts": [{"Destination": "/data", "Source": "/srv/watch"},
                            {"Destination": "/data/private", "Source": "/srv/private"}]}
    assert migration.host_path(container, "/data/private/watch.db") == Path("/srv/private/watch.db")
    with pytest.raises(ValueError):
        migration.host_path(container, "/unmounted/db")
    with pytest.raises(ValueError):
        migration.host_path(container, "/data/../secret")


def test_private_settings_never_overwrite_existing_file(tmp_path):
    path = tmp_path / ".env"
    migration.private_write(path, "fixture-only")
    with pytest.raises(FileExistsError):
        migration.private_write(path, "replacement")
    assert path.read_text() == "fixture-only"
    assert path.stat().st_mode & 0o077 == 0


def test_snapshot_rejects_existing_destination(tmp_path):
    source = tmp_path / "source.db"
    sqlite3.connect(source).close()
    destination = tmp_path / "destination.db"
    destination.write_text("preserve")
    with pytest.raises(ValueError):
        migration.backup_db(source, destination)
    assert destination.read_text() == "preserve"


def test_prepare_apply_and_rollback_preserve_latest_database_rows(tmp_path, monkeypatch, capsys):
    from types import SimpleNamespace
    root = tmp_path / "server"
    root.mkdir()
    old_watch = tmp_path / "old-watch"
    old_filter = tmp_path / "old-filter"
    old_watch.mkdir()
    old_filter.mkdir()
    source = tmp_path / "zurg"
    output = tmp_path / "library"
    config = tmp_path / "jellyfin-config"
    for path in (source, output, config):
        path.mkdir()
    (config / "users.txt").write_text("private-fixture-user-data")
    for path in (old_watch / "stak-watch.db", old_filter / "filter.db"):
        with sqlite3.connect(path) as conn:
            conn.execute("create table ledger(value integer)")
            conn.execute("insert into ledger values(1)")
    (old_watch / "torznab.json").write_text("[]")
    key = "private-fixture-torbox-key"
    token = "private-fixture-token-1234567890123456789"
    watch = {"Config": {"Env": ["STAK_TORBOX_KEY=" + key, "STAK_WATCHER_TOKEN=" + token,
        "STAK_DB_PATH=/data/stak-watch.db", "STAK_TORZNAB_FILE=/config/torznab.json"]},
        "HostConfig": {"RestartPolicy": {"Name": "always"}}, "Mounts": [
        {"Destination": "/data", "Source": str(old_watch)},
        {"Destination": "/config/torznab.json", "Source": str(old_watch / "torznab.json")} ]}
    library = {"Config": {"Env": ["TORBOX_API_KEY=" + key, "STAK_ACCESS_TOKEN=" + token]},
        "HostConfig": {"RestartPolicy": {"Name": "unless-stopped"}, "PortBindings": {}},
        "Mounts": [{"Destination": "/source", "Source": str(source)},
                   {"Destination": "/library", "Source": str(output)},
                   {"Destination": "/state", "Source": str(old_filter)}]}
    jellyfin = {"State": {"Running": True}, "Mounts": [{"Destination": "/config", "Source": str(config)}]}
    containers = {"stak-watch": watch, "stak-library-filter": library, "jellyfin": jellyfin}
    calls = []
    monkeypatch.setattr(migration, "ROOT", root)
    monkeypatch.setattr(migration, "inspect", lambda name: containers[name])
    monkeypatch.setattr(migration, "docker", lambda *args, **kw: calls.append(args))
    monkeypatch.setattr(migration.subprocess, "run", lambda *args, **kw: SimpleNamespace(returncode=0))
    migration.prepare()
    assert not any(call[0] in ("stop", "start", "update") for call in calls)
    assert token not in capsys.readouterr().out
    assert (root / ".env").stat().st_mode & 0o077 == 0
    with sqlite3.connect(old_watch / "stak-watch.db") as conn:
        conn.execute("insert into ledger values(2)")
    migration.apply()
    with sqlite3.connect(root / "data" / "stak-watch.db") as conn:
        assert conn.execute("select value from ledger").fetchall() == [(1,), (2,)]
        conn.execute("insert into ledger values(3)")
    assert json.loads((root / "migration.json").read_text())["applied"] is True
    backup = Path(json.loads((root / "migration.json").read_text())["jellyfin_backup"])
    assert backup.is_file() and backup.stat().st_mode & 0o077 == 0
    migration.rollback()
    with sqlite3.connect(old_watch / "stak-watch.db") as conn:
        assert conn.execute("select value from ledger").fetchall() == [(1,), (2,), (3,)]
    assert (source / "unrelated-video.mkv").exists() is False
    assert ("start", "stak-library-filter", "stak-watch") in calls
