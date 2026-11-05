"""Linux operator migration. No credentials are printed or uploaded.

prepare: inspect existing containers and create a private, non-running copy.
apply: final backups, stop old writers, start one server. Explicitly invoked.
rollback: retain new ledger records and return to the old two services.
"""
import argparse
import datetime
import json
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import sys
import time
import tarfile

ROOT = Path(__file__).resolve().parent


def operator_owner(path):
    """sudo is needed for old private state; new files belong to the login user."""
    if getattr(os, "geteuid", lambda: 1)() == 0 and os.getenv("SUDO_UID", "").isdigit() and os.getenv("SUDO_GID", "").isdigit():
        os.chown(path, int(os.environ["SUDO_UID"]), int(os.environ["SUDO_GID"]), follow_symlinks=False)


def docker(*args, capture=False):
    return subprocess.run(["docker", *args], cwd=ROOT, check=True,
                          stdout=subprocess.PIPE if capture else None,
                          text=True).stdout


def inspect(name):
    return json.loads(docker("inspect", name, capture=True))[0]


def env_of(container):
    return dict(x.split("=", 1) for x in container["Config"].get("Env", []) if "=" in x)


def host_path(container, path):
    target = Path(path)
    candidates = sorted(container["Mounts"], key=lambda x: len(x["Destination"]), reverse=True)
    for mount in candidates:
        base = Path(mount["Destination"])
        if target == base or base in target.parents:
            result = Path(mount["Source"]) / target.relative_to(base)
            if ".." in result.parts or not result.is_absolute():
                raise ValueError("Unsafe mounted state path")
            return result
    raise ValueError("The configured state path is not persistently mounted")


def private_write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as handle:
        handle.write(data)
    operator_owner(path)


def save_state(state):
    pending = ROOT / "migration.json.new"
    if pending.exists():
        pending.unlink()
    private_write(pending, json.dumps(state, indent=2))
    with pending.open("rb") as handle:
        os.fsync(handle.fileno())
    os.replace(pending, ROOT / "migration.json")


def backup_db(source, destination):
    source = Path(source)
    if not source.is_file() or destination.exists():
        raise ValueError("Expected an existing source and a new backup destination")
    destination.parent.mkdir(parents=True, exist_ok=True)
    src = sqlite3.connect("file:" + str(source) + "?mode=ro", uri=True, timeout=10)
    dst = sqlite3.connect(str(destination))
    deadline = time.monotonic() + 60
    def progress(*_):
        if time.monotonic() > deadline:
            raise TimeoutError("Database backup deadline exceeded")
    try:
        src.backup(dst, pages=256, progress=progress)
        if dst.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
            raise ValueError("Database backup verification failed")
    finally:
        dst.close()
        src.close()
    os.chmod(destination, 0o600)
    operator_owner(destination)


def snapshot_sources(state, folder):
    folder.mkdir(parents=True, exist_ok=True)
    os.chmod(folder, 0o700)
    operator_owner(folder)
    for key, name in (("watch_db", "stak-watch.db"), ("filter_db", "filter.db")):
        backup_db(state[key], folder / name)


def prepare():
    if (ROOT / ".env").exists() or (ROOT / "migration.json").exists():
        raise ValueError("Migration already prepared; existing configuration was not overwritten")
    watch, library = inspect("stak-watch"), inspect("stak-library-filter")
    we, le = env_of(watch), env_of(library)
    if we.get("STAK_TORBOX_KEY") != le.get("TORBOX_API_KEY"):
        raise ValueError("Existing watcher and filter use different TorBox accounts")
    if len(we.get("STAK_WATCHER_TOKEN", "")) < 32:
        raise ValueError("The existing watcher token is missing or too short")
    source = host_path(library, le.get("SOURCE_ROOT", "/source"))
    output = host_path(library, le.get("OUTPUT_ROOT", "/library"))
    if not source.is_dir() or not output.is_dir():
        raise ValueError("Existing Zurg mount or dedicated library is unavailable")
    state = {"watch_db": str(host_path(watch, we.get("STAK_DB_PATH", "/data/stak-watch.db"))),
             "filter_db": str(host_path(library, le.get("STATE_DB", "/state/filter.db"))),
             "old_restart": {"stak-watch": watch["HostConfig"]["RestartPolicy"]["Name"],
                             "stak-library-filter": library["HostConfig"]["RestartPolicy"]["Name"]},
             "output": str(output), "applied": False}
    (ROOT / "data").mkdir(exist_ok=True)
    operator_owner(ROOT / "data")
    snapshot_sources(state, ROOT / "data" / "prepared")
    for name in ("stak-watch.db", "filter.db"):
        shutil.copy2(ROOT / "data" / "prepared" / name, ROOT / "data" / name)
    keep = ("STAK_WATCHER_TOKEN", "STAK_TORBOX_KEY", "STAK_ADDONS", "STAK_JELLYFIN_URL",
            "STAK_JELLYFIN_TOKEN", "STAK_JELLYFIN_USER", "STAK_BIND_HOST", "STAK_BIND_PORT",
            "STAK_NEGATIVE_TTL_HOURS", "STAK_TICK_SECONDS")
    new = {k: we[k] for k in keep if we.get(k) is not None}
    binding = library["HostConfig"].get("PortBindings", {}).get("8787/tcp") or [{"HostIp": "127.0.0.1"}]
    new.update({"ZURG_SOURCE_DIR": str(source), "STAK_LIBRARY_DIR": str(output),
                "STAK_JELLYFIN_SCAN": "1", "STAK_LAYOUT": "organized",
                "JELLYFIN_ZURG_ROOT": le.get("JELLYFIN_ZURG_ROOT", "/media/zurg/__all__"),
                "STAK_LEGACY_FILTER_PORT": "8787", "STAK_LEGACY_FILTER_BIND": binding[0].get("HostIp") or "127.0.0.1",
                "STAK_LEGACY_FILTER_TOKEN": le["STAK_ACCESS_TOKEN"]})
    new.setdefault("STAK_JELLYFIN_URL", le.get("JELLYFIN_URL", ""))
    new.setdefault("STAK_JELLYFIN_TOKEN", le.get("JELLYFIN_API_KEY", ""))
    lines = []
    for key, value in new.items():
        if "\n" in value or "\r" in value:
            raise ValueError("Configuration contains unexpected multiline values")
        lines.append(key + "='" + value.replace("'", "\\'") + "'")
    torznab = we.get("STAK_TORZNAB_FILE", "")
    if torznab:
        raw = host_path(watch, torznab).read_text()
        if not isinstance(json.loads(raw), list):
            raise ValueError("Invalid existing Torznab configuration")
    else:
        raw = "[]\n"
    private_write(ROOT / ".env", "\n".join(lines) + "\n")
    private_write(ROOT / "torznab.json", raw)
    private_write(ROOT / "migration.json", json.dumps(state, indent=2))
    docker("compose", "config", "--quiet")
    docker("compose", "build", "stak-server")
    print("Prepared private settings and verified database copies. Old services are still running.")
    print("Preview: docker compose run --rm --no-deps -T stak-server python -m server.admin plan")
    print("After reviewing the plan and backing up Jellyfin configuration: sudo python3 migrate.py apply")


def apply():
    state = json.loads((ROOT / "migration.json").read_text())
    if state["applied"]:
        raise ValueError("Already migrated; use update.sh for subsequent updates")
    # A second preparation is not used as a final snapshot: final copies happen
    # only after the old writers stop, so no pending adds or approvals are lost.
    docker("compose", "build", "stak-server")
    # Paths may receive new Jellyfin item ids. Preserve the complete old server
    # configuration for recovery; do not write watched state through its API.
    jellyfin = inspect("jellyfin")
    config_root = host_path(jellyfin, "/config").resolve()
    if len(config_root.parts) < 3 or not config_root.is_dir():
        raise ValueError("Cannot safely locate Jellyfin configuration for backup")
    if config_root == ROOT or config_root in ROOT.parents:
        raise ValueError("Install STAK Server outside the Jellyfin configuration directory")
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    config_backup = ROOT / "data" / "backups" / (stamp + "-jellyfin-config.tar.gz")
    partial_backup = config_backup.with_suffix(config_backup.suffix + ".partial")
    config_backup.parent.mkdir(parents=True, exist_ok=True)
    os.chmod(config_backup.parent, 0o700)
    operator_owner(config_backup.parent)
    was_running = jellyfin["State"]["Running"]
    print("Backing up Jellyfin configuration (including users and watched data); playback pauses briefly.", flush=True)
    if was_running:
        docker("stop", "-t", "60", "jellyfin")
    try:
        fd = os.open(partial_backup, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "wb") as handle:
            with tarfile.open(fileobj=handle, mode="w:gz", dereference=False) as archive:
                archive.add(config_root, arcname="config")
        with tarfile.open(partial_backup) as archive:
            archive.getmembers()
        os.replace(partial_backup, config_backup)
        operator_owner(config_backup)
        state["jellyfin_backup"] = str(config_backup)
        save_state(state)
    finally:
        if was_running:
            docker("start", "jellyfin")
    docker("stop", "-t", "60", "stak-watch", "stak-library-filter")
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    try:
        folder = ROOT / "data" / "backups" / stamp
        snapshot_sources(state, folder)
        for name in ("stak-watch.db", "filter.db"):
            dst = ROOT / "data" / name
            # Only newly prepared private copies in this exact directory are
            # replaced. Never truncate a live database or leave a stale WAL.
            for suffix in ("-wal", "-shm"):
                sidecar = Path(str(dst) + suffix)
                if sidecar.exists():
                    sidecar.unlink()
            shutil.copy2(folder / name, dst)
            if name == "stak-watch.db":
                with sqlite3.connect(dst) as connection:
                    if connection.execute("SELECT name FROM sqlite_master WHERE name='meta'").fetchone():
                        connection.execute("DELETE FROM meta WHERE key='organized_layout_v1'")
        docker("compose", "up", "-d", "stak-server")
        healthy = False
        for _ in range(30):
            result = subprocess.run(["docker", "compose", "exec", "-T", "stak-server", "python", "-c",
                "import os,json,urllib.request; r=json.load(urllib.request.urlopen('http://127.0.0.1:'+os.getenv('STAK_BIND_PORT','8055')+'/health',timeout=5)); assert r['ok']"],
                cwd=ROOT, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if result.returncode == 0:
                healthy = True
                break
            time.sleep(2)
        if not healthy:
            raise RuntimeError("Unified server did not become healthy; backups retained. Use rollback.")
        docker("update", "--restart=no", "stak-watch", "stak-library-filter")
        state["applied"] = True
        save_state(state)
        print("One STAK Server is running. Existing app addresses and tokens remain compatible.")
        print("Check: docker compose exec -T stak-server python -m server.admin status")
    except Exception:
        # Do not blindly restart old writers if the new service might have
        # already added content. Rollback merges the latest verified databases.
        print("Cutover stopped. Backups and old containers retained; run sudo python3 migrate.py rollback.", file=sys.stderr)
        raise


def rollback():
    state = json.loads((ROOT / "migration.json").read_text())
    docker("compose", "stop", "-t", "60", "stak-server")
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    # Preserve post-cutover reservations and approvals; never restore an older
    # ledger that could cause the same torrent to be added again.
    for key, name in (("watch_db", "stak-watch.db"), ("filter_db", "filter.db")):
        src = ROOT / "data" / name
        dst = Path(state[key])
        snapshot = ROOT / "data" / "backups" / (stamp + "-rollback-" + name)
        backup_db(src, snapshot)
        for suffix in ("-wal", "-shm"):
            sidecar = Path(str(dst) + suffix)
            if sidecar.exists():
                sidecar.unlink()
        shutil.copy2(snapshot, dst)
    for name, policy in state["old_restart"].items():
        docker("update", "--restart=" + (policy or "no"), name)
    docker("start", "stak-library-filter", "stak-watch")
    state["applied"] = False
    save_state(state)
    print("Old services restored with the latest ledger. Their filter will restore its legacy link layout.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("prepare", "apply", "rollback"))
    args = parser.parse_args()
    if os.geteuid() != 0:
        raise SystemExit("Use sudo python3 migrate.py " + args.action + " to access the existing private state. New settings remain owned by your login user.")
    try:
        {"prepare": prepare, "apply": apply, "rollback": rollback}[args.action]()
    except Exception as error:
        reason = ": " + str(error) if type(error) is ValueError else ""
        raise SystemExit("Migration failed (" + type(error).__name__ + ")" + reason + ". Existing media retained.") from None
