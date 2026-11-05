"""Local operator commands; credentials stay in environment, never output."""
import json
import os
import sys
import urllib.request


def main():
    command = sys.argv[1] if len(sys.argv) > 1 else "status"
    if command == "plan":
        from server.organizer import OrganizedFilter
        from server.main import library_config
        from core.service import Config, WatchService
        import filter_server as legacy
        # No scheduler or add operation is started by the plan command.
        env = dict(os.environ)
        env["STAK_JELLYFIN_SCAN"] = "0"
        watcher = WatchService(Config.from_env(env))
        library = OrganizedFilter(library_config(os.environ))
        try:
            library.import_watcher_identities(watcher.store)
            catalog = library.catalog()
            groups = legacy.index_source(library.source)
            rows = []
            for job in library.get():
                if not job["approved"]:
                    continue
                try:
                    torrent = catalog.get(job["id"])
                    if not torrent:
                        raise ValueError("Approved torrent not present in account")
                    key, title, folder, links = library.plan(job, torrent, groups)
                    rows.append({"id": job["id"], "state": "ready", "title": title,
                                 "links": [str(p.relative_to(library.output)) for p, target in links]})
                except ValueError as error:
                    rows.append({"id": job["id"], "state": "needs_attention", "reason": str(error)})
            print(json.dumps({"layout": "organized", "readOnlyMedia": True, "items": rows}, indent=2))
        finally:
            library.db.close()
            watcher.store.close()
        return
    if command != "status":
        raise SystemExit("Usage: python -m server.admin [status|plan]")
    port = int(os.environ.get("STAK_BIND_PORT", "8055"))
    token = os.environ["STAK_WATCHER_TOKEN"]
    def get(path):
        req = urllib.request.Request(f"http://127.0.0.1:{port}/{path}", headers={"Authorization": "Bearer " + token})
        with urllib.request.urlopen(req, timeout=30) as result:
            return json.load(result)
    status = {"health": get("health"), "watching": get("v1/status"), "library": get("library/v1/status")}
    status["library"].pop("scope", None)
    print(json.dumps(status, indent=2))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        raise SystemExit("STAK server operation failed (" + type(error).__name__ + "); settings and media retained.") from None
