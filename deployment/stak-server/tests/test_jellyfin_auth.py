"""Jellyfin 12-style loopback server rejects the deprecated token header."""
import json
import threading
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit, parse_qs

import pytest
import filter_server as module


@pytest.fixture
def modern_jellyfin(tmp_path):
    token = "synthetic-jellyfin-key"
    expected = 'MediaBrowser Token="' + token + '"'
    seen = []
    state = {"marker": "", "scans": 0}

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def dispatch(self):
            path = urlsplit(self.path).path
            seen.append((self.command, path, dict(self.headers)))
            if self.headers.get("Authorization") != expected or self.headers.get("X-Emby-Token"):
                self.send_response(401)
                self.end_headers()
                return
            if path == "/Library/VirtualFolders":
                body = [{"Locations": ["/media/movies", "/media/shows"]}]
            elif path == "/Environment/DirectoryContents":
                directory = parse_qs(urlsplit(self.path).query)["path"][0]
                body = [{"Name": state["marker"], "Path": directory + "/" + state["marker"], "Type": "File"}]
            elif path == "/Library/Refresh" and self.command == "POST":
                state["scans"] += 1
                self.send_response(204)
                self.end_headers()
                return
            else:
                self.send_response(404)
                self.end_headers()
                return
            raw = json.dumps(body).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)

        do_GET = dispatch
        do_POST = dispatch

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    worker = threading.Thread(target=server.serve_forever, daemon=True)
    worker.start()
    source = tmp_path / "source"
    source.mkdir()
    library = module.Filter({"source_root": str(source), "output_root": str(tmp_path / "library"),
        "target_root": "/media/zurg/__all__", "db": str(tmp_path / "filter.db"),
        "token": "fixture-server-token-" * 3, "torbox_key": "fixture-account",
        "jellyfin_url": f"http://127.0.0.1:{server.server_port}", "jellyfin_key": token,
        "jellyfin_movies": "/media/movies", "jellyfin_shows": "/media/shows", "jellyfin_zurg_base": "/media/zurg",
        "scan_on_change": True})
    state["marker"] = library.marker_name
    yield library, state, seen
    server.shutdown()
    server.server_close()
    worker.join(2)
    library.db.close()


def test_supported_auth_verifies_both_library_markers(modern_jellyfin):
    library, state, seen = modern_jellyfin
    assert library.jellyfin_verified() is True
    assert len(seen) == 3
    assert all(headers.get("Authorization", "").startswith("MediaBrowser Token=") for _, _, headers in seen)
    assert all("X-Emby-Token" not in headers for _, _, headers in seen)
    req = urllib.request.Request(library.c["jellyfin_url"] + "/Library/VirtualFolders", headers={"X-Emby-Token": library.c["jellyfin_key"]})
    with pytest.raises(urllib.error.HTTPError) as error:
        urllib.request.urlopen(req)
    assert error.value.code == 401


def test_publication_scan_also_uses_supported_auth(modern_jellyfin):
    library, state, seen = modern_jellyfin
    folder = library.source / "release"
    folder.mkdir()
    (folder / "movie.mkv").write_bytes(b"fixture")
    library.approve({"id": "1", "scope": library.scope, "hash": "a" * 40, "approved": True, "name": "Fixture movie", "type": "movie"})
    library.catalog = lambda: {"1": {"id": "1", "hash": "a" * 40, "download_finished": True,
        "files": [{"name": "release/movie.mkv", "size": len(b"fixture")} ]}}
    library.reconcile()
    assert library.get("1")["state"] == "published"
    assert state["scans"] == 1
    assert any(method == "POST" and path == "/Library/Refresh" for method, path, _ in seen)
    assert all("X-Emby-Token" not in headers for _, _, headers in seen)


@pytest.mark.parametrize("token", ["", 'bad"token', "bad\\token", "bad\nvalue", "bad value"])
def test_header_injection_and_invalid_token_format_are_refused(token):
    with pytest.raises(ValueError, match="Invalid Jellyfin access token format"):
        module.jellyfin_headers(token)
