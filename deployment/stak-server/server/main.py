"""One container/process for the existing watcher API and library organizer."""
import hmac
import json
import os
import signal
import threading
import time
from http.server import ThreadingHTTPServer
from urllib.parse import urlsplit

from core import service as watcher
from core.library import filter_scope_hash
from server import VERSION, PROTOCOL
from server.organizer import OrganizedFilter, clean_media


class InProcessLibrary:
    def __init__(self, library):
        self.library = library

    def status(self):
        # Pre-add guards need source/account readiness, not a remote Jellyfin scan.
        return {"protocol": "stak-library-filter/1", "scope": self.library.scope,
                "source_available": self.library.source.is_dir()}

    def approvals(self):
        return {"items": self.library.get()}

    def approval_by_id(self, torrent_id):
        return self.library.get(str(torrent_id))

    def post_approval(self, torrent_id, scope, info_hash, name, approved=True):
        return self.library.approve({"id": str(torrent_id), "scope": scope, "hash": info_hash,
                                     "name": name, "approved": approved, "type": "series"})

    def post_series_approval(self, torrent_id, scope, info_hash, name, series):
        media = {key: getattr(series, key) for key in ("imdb", "tmdb", "tvdb") if getattr(series, key, None)}
        media["title"] = series.name
        return self.library.approve({"id": str(torrent_id), "scope": scope, "hash": info_hash,
                                     "name": name, "approved": True, "type": "series", "media": media})


def library_config(env):
    return {"source_root": env.get("SOURCE_ROOT", "/source"), "output_root": env.get("OUTPUT_ROOT", "/library"),
            "target_root": env.get("JELLYFIN_ZURG_ROOT", "/media/zurg/__all__"),
            "db": env.get("STATE_DB", "/data/filter.db"), "token": env["STAK_WATCHER_TOKEN"],
            "torbox_key": env["STAK_TORBOX_KEY"], "layout": env.get("STAK_LAYOUT", "organized"),
            "interval": max(30, int(env.get("CHECK_SECONDS", "60"))),
            "jellyfin_url": env.get("STAK_JELLYFIN_URL", ""), "jellyfin_key": env.get("STAK_JELLYFIN_TOKEN", ""),
            "jellyfin_movies": "/media/movies", "jellyfin_shows": "/media/shows", "jellyfin_zurg_base": "/media/zurg",
            "scan_on_change": env.get("STAK_JELLYFIN_SCAN", "1") == "1"}


def reset_layout_verification(store):
    """Invalidate derived library ids once, preserving acquisitions and history."""
    with store._lock, store._conn:
        if store.get_meta("organized_layout_v1") == "ready":
            return
        store._conn.execute("UPDATE episodes SET series_jellyfin=NULL,jellyfin_verified=0,status='added_torbox',"
            "status_detail='Library layout changed; verifying index',next_check=? "
            "WHERE torbox_id IS NOT NULL AND torrent_hash IS NOT NULL AND status!='paused'", (time.time(),))
        store._conn.execute("INSERT OR REPLACE INTO meta(key,value) VALUES('organized_layout_v1','ready')")


def unified_handler(service, library, legacy_token="", library_only=False):
    base = watcher._handler_class(service)

    class Handler(base):
        def library_authorized(self):
            supplied = self.headers.get("Authorization", "")
            return any(hmac.compare_digest(supplied, "Bearer " + token)
                       for token in (service.config.token, legacy_token) if token)

        def _dispatch(self, method):
            path = urlsplit(self.path).path
            if path == "/health" and method == "GET":
                healthy = service.healthy() and bool(getattr(library, "worker_alive", False)) and time.monotonic() - library.heartbeat < 600
                return self._send(200 if healthy else 503, {"ok": healthy, "version": VERSION, "protocol": PROTOCOL,
                    "libraryBase": "library/", "layout": library.c.get("layout", "organized")})
            is_library = library_only or path.startswith("/library/")
            if is_library:
                if not self.library_authorized():
                    return self._send(401, {"error": "Authentication required"})
                route = path if library_only else path.removeprefix("/library")
                try:
                    if method == "GET" and route == "/v1/status":
                        return self._send(200, library.status())
                    if method == "GET" and route == "/v1/approvals":
                        return self._send(200, {"items": library.get()})
                    if method == "POST" and route == "/v1/approvals":
                        return self._send(200, library.approve(self._read_json()))
                    if method == "POST" and route == "/v1/identity":
                        return self._send(200, library.identify(self._read_json()))
                    return self._send(404, {"error": "Unknown operation"})
                except (ValueError, TypeError, KeyError):
                    return self._send(400, {"error": "Invalid approval or media identity; existing links retained"})
                except Exception:
                    return self._send(503, {"error": "Library storage unavailable; retry later"})
            return super()._dispatch(method)
    return Handler


def main():
    env = dict(os.environ)
    env["STAK_FILTER_URL"] = "http://127.0.0.1:8055/library"
    env["STAK_FILTER_TOKEN"] = env.get("STAK_WATCHER_TOKEN", "")
    # The organizer owns scan scheduling. Watcher remains read-only to Jellyfin.
    env["STAK_JELLYFIN_SCAN"] = "0"
    config = watcher.Config.from_env(env)
    from pathlib import Path
    import fcntl
    Path(config.db_path).parent.mkdir(parents=True, exist_ok=True)
    guard = open(config.db_path + ".instance.lock", "a")
    try:
        fcntl.flock(guard, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        raise SystemExit("Another STAK server already owns this database") from None
    service = watcher.WatchService(config)
    library = OrganizedFilter(library_config(os.environ))
    if library.scope != filter_scope_hash(config.torbox_key):
        raise ValueError("Library and watcher accounts differ")
    library.recover_layout()
    library.import_watcher_identities(service.store)
    if library.c.get("layout") == "organized":
        reset_layout_verification(service.store)
    adapter = InProcessLibrary(library)
    service.filter_client = adapter
    service.deps.filter = adapter
    original_reconcile = service.safe_reconcile
    def reconcile():
        result = original_reconcile()
        library.import_watcher_identities(service.store)
        return result
    service.safe_reconcile = reconcile
    service.start()
    stop = threading.Event()

    def organize():
        library.worker_alive = True
        library.heartbeat = time.monotonic()
        try:
            while not stop.is_set():
                library.wake.clear()
                library.recover_layout()
                library.reconcile()
                library.heartbeat = time.monotonic()
                library.wake.wait(library.c["interval"])
        finally:
            library.worker_alive = False
    worker = threading.Thread(target=organize, name="stak-library", daemon=True)
    worker.start()
    server = watcher.WatchHTTPServer((config.bind_host, config.bind_port), service)
    server.RequestHandlerClass = unified_handler(service, library, os.getenv("STAK_LEGACY_FILTER_TOKEN", ""))
    def supervise():
        if not stop.is_set() and (not service.healthy() or not worker.is_alive() or time.monotonic() - library.heartbeat > 600):
            raise RuntimeError("Worker stopped")
    server.service_actions = supervise
    compatibility = None
    if os.getenv("STAK_LEGACY_FILTER_PORT", ""):
        compatibility = watcher.WatchHTTPServer((os.getenv("STAK_LEGACY_FILTER_BIND", "127.0.0.1"), int(os.environ["STAK_LEGACY_FILTER_PORT"])), service)
        compatibility.RequestHandlerClass = unified_handler(service, library, os.getenv("STAK_LEGACY_FILTER_TOKEN", ""), True)
        threading.Thread(target=compatibility.serve_forever, daemon=True).start()
    def shutdown(*_):
        stop.set()
        library.wake.set()
        service._stop.set()
        service._wake.set()
        threading.Thread(target=server.shutdown, daemon=True).start()
    signal.signal(signal.SIGTERM, shutdown)
    signal.signal(signal.SIGINT, shutdown)
    try:
        server.serve_forever(poll_interval=.5)
    finally:
        stop.set()
        library.wake.set()
        if compatibility:
            compatibility.shutdown()
            compatibility.server_close()
        server.server_close()
        service.stop()
        worker.join(40)
        if worker.is_alive():
            # Do not close databases still used by a worker; process exit releases them.
            raise RuntimeError("Library worker did not stop")
        library.db.close()
        service.store.close()
        guard.close()


if __name__ == "__main__":
    main()
