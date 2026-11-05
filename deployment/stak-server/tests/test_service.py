"""Service tests: config parsing, validation gate, scheduler, HTTP API.

The HTTP server runs on an ephemeral localhost port inside the test process;
no external network or live service is involved.
"""

import json
import threading
import time
import urllib.request
import urllib.error

import pytest

import core.service as service_module
from core import pipeline
from core.service import Config, WatchService

from conftest import rule_payload

TOKEN = "test-token-123"


def stub_loader(monkeypatch):
    """Cinemeta is replaced by a deterministic fixture loader."""
    monkeypatch.setattr(
        service_module, "cinemeta_episode_loader",
        lambda client: lambda series: [
            {"season": 1, "episode": 1, "air_date": "2026-01-01"},
            {"season": 1, "episode": 2, "air_date": "2026-01-08"},
        ])


@pytest.fixture
def service(tmp_path, monkeypatch, fake_client, fake_torbox, fake_filter,
            fake_jellyfin):
    config = Config(token=TOKEN, db_path=str(tmp_path / "watch.db"),
                    addons=[("https://addon.example/manifest.json", "myaddon")])
    svc = WatchService(config)
    svc.deps.client = fake_client
    svc.deps.torbox = fake_torbox
    svc.deps.filter = fake_filter
    svc.deps.jellyfin = fake_jellyfin
    svc.deps.verify_filter = False  # this fixture injects clients without credentials; handshake has separate coverage
    stub_loader(monkeypatch)
    return svc


# --- config ---------------------------------------------------------------------

def test_config_requires_token():
    with pytest.raises(ValueError):
        Config.from_env({})


def test_config_addon_parsing():
    addons = Config.parse_addons(
        "Torrentio|https://torrentio.example/manifest.json,"
        "http://x.io/manifest.json")
    assert addons == [("https://torrentio.example/manifest.json/", "Torrentio"),
                      ("http://x.io/manifest.json/", "x.io")]
    with pytest.raises(ValueError):
        Config.parse_addons("Bad|https://x.example/streams.json")


def test_config_torznab_file(tmp_path):
    (tmp_path / "torznab.json").write_text(json.dumps([
        {"id": "idx1", "url": "https://a.example/api", "apiKey": "K"},
        {"id": "off", "url": "https://b.example/api", "enabled": False},
    ]), encoding="utf-8")
    indexers = Config.parse_torznab(str(tmp_path / "torznab.json"))
    assert len(indexers) == 2
    assert indexers[0]["enabled"] is True
    assert indexers[1]["enabled"] is False


# --- rule lifecycle ---------------------------------------------------------------

def test_save_rule_validates_and_syncs(service):
    out = service.save_rule(rule_payload())
    assert out["episodes"] == 0
    assert out["metadata"]["state"] == "queued"
    service.run_metadata_jobs()
    episodes = service.store.episodes_for_rule("r1")
    keys = {row["episode_key"] for row in episodes}
    # stable identity incl. season/episode numbering
    assert keys == {"r1:S01E001", "r1:S01E002"}


def test_save_rule_rejects_invalid(service):
    with pytest.raises(ValueError):
        service.save_rule(rule_payload(resolutionMode="nonsense"))


def test_save_rule_rejects_contradictions(service):
    # 4K-only with an impossible size cap: clear user feedback instead of silence
    out = service.save_rule(rule_payload(
        resolutionMode="2160_only", maxEpisodeGb=1.0))
    assert any(i["code"] == "size_cap_small_for_4k" for i in out["issues"])


def test_paused_rule_does_not_sync(service):
    service.save_rule(rule_payload(paused=True))
    assert service.store.episodes_for_rule("r1") == []


def test_take_down_rule_pauses_episodes(service):
    service.save_rule(rule_payload())
    service.run_metadata_jobs()
    assert service.store.episodes_for_rule("r1")
    service.store.delete_rule("r1")
    rows = service.store.episodes_for_rule("r1")
    assert rows and all(row["status"] == "paused" for row in rows)


def test_run_pending_processes_episode(service, fake_client, fake_torbox,
                                       monkeypatch):
    from core import providers
    providers._addon_cache.clear()  # isolation from other tests' documents
    providers._addon_cache_times.clear()
    fake_client.route("/stream/series/tt001:1:1.json", {"streams": [{
        "name": "X", "title": "Show.S01E01.2160p.WEB-DL.DV.DTS-HD",
        "infoHash": "1" * 40,
        "behaviorHints": {"filename": "s01e01.mkv", "videoSize": 4_000_000_000}}]})
    fake_torbox.cached = {"1" * 40}
    service.save_rule(rule_payload())
    service.run_metadata_jobs()
    service.store.set_episode_status("r1:S01E001", "waiting_release", "",
                                     next_check=time.time() - 1)
    processed = service.run_pending()
    assert processed >= 1
    episode = service.store.episode("r1:S01E001")
    assert episode["status"] in (pipeline.STATUS_ADDED,)


# --- HTTP API -----------------------------------------------------------------------

def http(method: str, url: str, token=TOKEN, body=None):
    request = urllib.request.Request(url, method=method)
    if token:
        request.add_header("Authorization", "Bearer " + token)
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, data, timeout=10) as response:
            return response.status, json.loads(response.read().decode())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read().decode() or "{}")


@pytest.fixture
def server(service):
    # keep the background scheduler quiet: no scheduler thread in this test
    from core.service import _handler_class
    httpd = service_module.ThreadingHTTPServer(("127.0.0.1", 0),
                                               _handler_class(service))
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    yield f"http://127.0.0.1:{httpd.server_address[1]}", service, httpd
    httpd.shutdown()
    httpd.server_close()


def test_health_needs_no_token(server):
    base, _svc, _h = server
    status, body = http("GET", base + "/health", token="")
    assert status == 200 and body["ok"] is True


def test_endpoints_require_correct_token(server):
    base, _svc, _h = server
    assert http("GET", base + "/v1/rules", token="wrong")[0] == 401
    assert http("POST", base + "/v1/rules", token="wrong", body={})[0] == 401


def test_automation_must_be_confirmed(server):
    base, svc, _h = server
    billing_only = rule_payload()
    billing_only["confirmed"] = False  # watchlist save only: no auto-add consent
    billing_only.pop("confirm", None)
    status, body = http("POST", base + "/v1/rules", body=billing_only)
    assert status == 400
    assert "confirmed" in body["error"]
    assert svc.store.rules() == []


def test_rule_crud_over_http(server):
    base, svc, _h = server
    status, body = http("POST", base + "/v1/rules", body=rule_payload())
    assert status == 201
    assert body["episodes"] == 0
    assert body["metadata"]["state"] == "queued"
    svc.run_metadata_jobs()
    assert body["rule"]["ruleId"] == "r1"

    status, body = http("GET", base + "/v1/rules/r1")
    assert status == 200 and body["ruleId"] == "r1"
    assert http("GET", base + "/v1/rules/nope")[0] == 404

    status, body = http("POST", base + "/v1/rules/r1/pause", body=None)
    assert status == 200
    assert all(row["status"] == "paused"
               for row in svc.store.episodes_for_rule("r1"))

    status, _ = http("POST", base + "/v1/rules/r1/resume", body=None)
    assert status == 200

    status, body = http("DELETE", base + "/v1/rules/r1")
    assert status == 200 and body["paused"] is True
    assert svc.rule_payload("r1") is None  # rule gone, episode history kept


def test_status_overview_counts(server):
    base, svc, _h = server
    http("POST", base + "/v1/rules", body=rule_payload())
    svc.run_metadata_jobs()
    status, body = http("GET", base + "/v1/status")
    assert status == 200
    rule_row = body["rules"][0]
    assert rule_row["episodes"] == 2
    assert rule_row["statuses"]


def test_reconcile_without_torbox_credentials(server):
    base, _svc, _h = server
    status, body = http("POST", base + "/v1/reconcile")
    assert status == 202 and body['queued'] is True
    assert 'note' in _svc.safe_reconcile()
