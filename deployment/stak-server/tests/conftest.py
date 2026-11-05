"""Shared fixtures: fake fetch client, TorBox, filter and Jellyfin doubles.

Nothing here touches the network or a real TorBox account: the suite runs
completely offline so the private account can never be mutated.
"""

from __future__ import annotations

import json

import pytest


# --- fetch client double -----------------------------------------------------

class FakeResponse:
    def __init__(self, status: int, body):
        if isinstance(body, bytes):
            self._body = body
        elif isinstance(body, (dict, list)):
            self._body = json.dumps(body).encode("utf-8")
        else:
            self._body = str(body).encode("utf-8")
        self.status = status

    def read(self) -> bytes:
        return self._body


class FakeClient:
    """Routes by URL substring + method; records every request."""

    def __init__(self):
        self.routes: list[tuple[str, str, int | None, dict | str, str]] = []
        #: set to make every unmatched request raise (outage simulation)
        self.fail_unmatched = False
        self.requests: list[dict] = []
        self.bodies: list[bytes | None] = []

    def route(self, url_part: str, payload, status: int = 200,
              method: str | None = None):
        """Route matching by URL substring. `method=None` means any method."""
        self.routes.append((url_part, url_part, status, payload, method or ""))

    def fetch(self, url: str, headers: dict | None = None, timeout: float = 30,
              data=None, method: str = "GET") -> FakeResponse:
        self.requests.append({"url": url, "method": method, "headers": dict(headers or {})})
        self.bodies.append(data if isinstance(data, bytes) else None)
        for url_part, _unused, status, payload, route_method in reversed(self.routes):
            if url_part in url and route_method in ("", method):
                return FakeResponse(status, payload)
        if self.fail_unmatched:
            raise TimeoutError(f"no route for {url}")
        raise AssertionError(f"unexpected request: {method} {url}")


# --- TorBox double -----------------------------------------------------------

class FakeTorBox:
    """Mirrors the real client's surface without touching the account."""

    def __init__(self, cached=(), mylist_items=(), add_id="777"):
        self.cached = {h.lower() for h in cached}
        self.mylist_items = list(mylist_items)
        self.adds: list[str] = []
        self.add_id = add_id
        self.fail_cache = False          # checkcached raises -> NetworkDown
        self.fail_add = False            # add raises UnconfirmedTorrentMutation
        self.refuse_adds = False         # server-side cached-only refusal
        self.fail_mylist = False

    def checkcached(self, hashes, list_files: bool = False):
        del list_files
        if self.fail_cache:
            raise TimeoutError("torbox unreachable")
        return {h.lower(): {"cached": True, "size": 4_000_000_000, "files": []}
                for h in hashes if h.lower() in self.cached}

    def cached_hashes(self, hashes):
        return {h for h, v in self.checkcached(hashes).items() if v.get("cached")}

    def mylist(self, bypass_cache: bool = True):
        if self.fail_mylist:
            raise TimeoutError("torbox mylist unreachable")
        return list(self.mylist_items)

    def add_cached_magnet(self, magnet_raw: str):
        self.adds.append(magnet_raw)
        if self.fail_add:
            from core.torbox import UnconfirmedTorrentMutation
            raise UnconfirmedTorrentMutation("createtorrent timed out")
        if self.refuse_adds:
            return {"added": False, "id": None, "detail": "not cached: refused add_only_if_cached"}
        return {"added": True, "id": self.add_id, "detail": "added (cached only)"}


# --- filter / Jellyfin doubles ------------------------------------------------

class FakeFilter:
    def __init__(self):
        self.posts: list[dict] = []
        self.states: dict[str, str] = {}
        self.fail = False

    def set_state(self, torbox_id: str, state: str) -> None:
        self.states[torbox_id] = state

    def post_approval(self, torbox_id, scope, info_hash, name, approved=True):
        if self.fail:
            raise TimeoutError("filter down")
        self.posts.append({"id": str(torbox_id), "scope": scope, "hash": info_hash,
                           "name": name, "type": "series", "approved": approved})
        self.states.setdefault(torbox_id, "pending")
        return {"ok": True}

    def approvals(self):
        if self.fail:
            raise TimeoutError("filter down")
        return {"items": [{"id": str(k), "state": v} for k, v in self.states.items()]}

    def approval_by_id(self, torbox_id):
        for row in self.approvals()["items"]:
            if row["id"] == str(torbox_id):
                return row
        return None


class FakeJellyfin:
    def __init__(self):
        self.present = False
        self.scans = 0
        self.fail = False
        self.series_base = "jf:123"

    def episode_present(self, series_id, season, episode, *args, **kwargs):
        if self.fail:
            raise TimeoutError("jellyfin down")
        return self.present

    def request_scan(self, series_id=None):
        self.scans += 1

    def find_series_base(self, provider_id=None, name=""):
        return self.series_base


# --- payload / stream helpers --------------------------------------------------

def stream_entry(release_name: str, info_hash: str | None = None,
                 size=8_000_000_000) -> dict:
    """A Stremio-style stream entry the way Torrentio-like addons return them."""
    import hashlib
    return {
        "name": "X",
        "title": release_name,
        "infoHash": info_hash or hashlib.sha1(release_name.encode()).hexdigest(),
        "behaviorHints": {"filename": f"{release_name}.mkv", "videoSize": size},
        "description": release_name,
    }


def series_meta(videos: list[dict]) -> dict:
    return {"meta": {"videos": videos}}


def rule_payload(**changes) -> dict:
    base = {
        "ruleId": "r1",
        "series": {"imdb": "tt001", "name": "Show"},
        "enabled": True,
        "paused": False,
        "confirm": True,
        "watchedSeasons": [1],
        "releaseTypes": ["WEB-DL"],
        "resolutionMode": "2160_preferred",
        "resolutionWaitHours": 24,
        "checkMinutes": 45,
        "confirmed": True,
    }
    base.update(changes)
    return base


def dep(deps, **changes):
    """Replace WatcherDeps fields after construction (tests swap doubles)."""
    for name, value in changes.items():
        setattr(deps, name, value)


@pytest.fixture(autouse=True)
def _clean_provider_doc_cache():
    """providers keeps a 300s document cache: clear it around every test."""
    from core import providers
    providers._addon_cache.clear()
    providers._addon_cache_times.clear()
    yield
    providers._addon_cache.clear()
    providers._addon_cache_times.clear()


@pytest.fixture
def fake_client():
    return FakeClient()


@pytest.fixture
def fake_torbox():
    return FakeTorBox()


@pytest.fixture
def fake_filter():
    return FakeFilter()


@pytest.fixture
def fake_jellyfin():
    return FakeJellyfin()
