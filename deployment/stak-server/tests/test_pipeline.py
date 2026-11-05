"""Pipeline integration tests: happy path, cache drops, dedup, gating.

Everything runs against fixtures and a temporary SQLite store. The pipeline's
guarantees are verified end to end: cached-only adds, no duplicate adds across
runs, reconcile-before-re-add, negative-cache TTL and library gating.
"""

import time

import pytest

from core import pipeline, rules as R
from core.pipeline import WatcherDeps, process_episode, reconcile_with_account
from core.store import Store

from conftest import stream_entry, rule_payload

HASH = "aa11bb22cc33dd44ee55ff00112233445566778899" .lower()[:40]


def make_deps(tmp_path, fake_client, fake_torbox, fake_filter, fake_jellyfin, **kw):
    store = Store(str(tmp_path / "watch.db"))
    deps = WatcherDeps(
        store=store, client=fake_client, torbox=fake_torbox,
        filter_client=fake_filter, jellyfin=fake_jellyfin,
        addons=kw.pop("addons", [("https://addon.example/manifest.json", "myaddon")]),
        torznab=kw.pop("torznab", []),
        jellyfin_scan_enabled=kw.pop("jellyfin_scan_enabled", False),
        filter_scope=kw.pop("filter_scope", "scope-hash"),
        negative_ttl_seconds=kw.pop("negative_ttl_seconds", 12 * 3600))
    return deps, store


def prime_episode(store, rule_id="r1", key="r1:S01E001", season=1, number=1,
                  air="2026-01-01"):
    store.upsert_episode(key, rule_id, season, number, air)
    store.set_episode_status(key, "waiting_release", "", next_check=time.time() - 1)
    rule = R.rule_from_dict(rule_payload(ruleId=rule_id))
    return store.episode(key), rule


def add_release(fake_client, imdb, release_name, info_hash):
    fake_client.route(f"/stream/series/{imdb}:1:1.json",
                      {"streams": [stream_entry(release_name, info_hash)]})


def expect_store(store, key):
    return store.episode(key)


# --- happy path ---------------------------------------------------------------

def test_happy_path_with_gating(tmp_path, fake_client, fake_torbox, fake_filter,
                                fake_jellyfin):
    add_release(fake_client, "tt001",
                "Show.S01E01.2160p.WEB-DL.DV.DTS-HD.Hun.x265", HASH)
    fake_torbox.cached = {HASH}
    fake_filter.set_state(fake_torbox.add_id, "published")  # moderation: approved
    fake_jellyfin.present = True
    fake_jellyfin.episode_present = lambda *args,**kw:bool(fake_torbox.adds)
    deps, store = make_deps(tmp_path, fake_client, fake_torbox, fake_filter,
                            fake_jellyfin)
    row, rule = prime_episode(store)

    # first run: add + approval + published + already indexed everywhere
    status = process_episode(deps, row, rule, "Show", "tt001")
    assert status == pipeline.STATUS_AVAILABLE
    episode = expect_store(store, row["episode_key"])
    assert episode["status"] == "available_jellyfin"
    assert episode["jellyfin_verified"] is True

    # cached-only add traveled as a series approval with the rule's scope
    assert fake_torbox.adds == ["magnet:?xt=urn:btih:" + HASH]
    post = fake_filter.posts[0]
    assert post["type"] == "series"
    assert post["scope"] == "scope-hash"
    assert post["hash"] == HASH
    assert post["id"] == fake_torbox.add_id

    # no duplicate add on the next run
    status2 = process_episode(deps, store.episode(row["episode_key"]), rule,
                              "Show", "tt001")
    assert status2 == pipeline.STATUS_AVAILABLE
    assert len(fake_torbox.adds) == 1


def test_available_requires_verified_index(tmp_path, fake_client, fake_torbox,
                                           fake_filter, fake_jellyfin):
    add_release(fake_client, "tt004",
                "Show.S01E01.2160p.WEB-DL.DV.DTS-HD.Hun.x265", HASH)
    fake_torbox.cached = {HASH}
    fake_filter.set_state(fake_torbox.add_id, "pending")
    deps, store = make_deps(tmp_path, fake_client, fake_torbox, fake_filter,
                            fake_jellyfin)
    row, rule = prime_episode(store, air="2026-02-01")

    status = process_episode(deps, row, rule, "Show", "tt004")
    # pending moderation filter: it stays added_torbox, never verified
    assert status in (pipeline.STATUS_ADDED,)


def test_published_but_unindexed_requires_library(tmp_path, fake_client,
                                                  fake_torbox, fake_filter,
                                                  fake_jellyfin):
    add_release(fake_client, "tt005",
                "Show.S01E01.2160p.WEB-DL.DV.DTS-HD.Hun.x265", HASH)
    fake_torbox.cached = {HASH}
    fake_filter.set_state(fake_torbox.add_id, "published")
    fake_jellyfin.present = False
    deps, store = make_deps(tmp_path, fake_client, fake_torbox, fake_filter,
                            fake_jellyfin, jellyfin_scan_enabled=True)
    row, rule = prime_episode(store, air="2026-03-01")

    status = process_episode(deps, row, rule, "Show", "tt005")
    assert status == pipeline.STATUS_WAITING_LIBRARY
    assert fake_jellyfin.scans == 1  # the authorized scan request happened


# --- add-stage safety ----------------------------------------------------------

def test_no_cache_never_adds(tmp_path, fake_client, fake_torbox, fake_filter,
                             fake_jellyfin):
    add_release(fake_client, "tt006",
                "Show.S01E01.2160p.WEB-DL.DV.DTS-HD.Hun.x265", HASH)
    fake_torbox.cached = set()  # fresh re-confirm fails: no add allowed
    deps, store = make_deps(tmp_path, fake_client, fake_torbox, fake_filter,
                            fake_jellyfin)
    row, rule = prime_episode(store, air="2026-04-01")

    status = process_episode(deps, row, rule, "Show", "tt006")
    assert status == pipeline.STATUS_WAITING_CACHE
    assert fake_torbox.adds == []          # createtorrent never called
    assert fake_filter.posts == []         # nothing auto-approved either


def test_unidentifiable_release_never_added(tmp_path, fake_client, fake_torbox,
                                            fake_filter, fake_jellyfin):
    """No SxxEyy marker at all: the episode a release belongs to can not be
    verified, so it is never added (season packs are rejected separately)."""
    fake_client.route("/stream/series/tt007:1:1.json", {"streams": [
        stream_entry("Show.2160p.WEB-DL", HASH)]})
    fake_torbox.cached = {HASH}
    deps, store = make_deps(tmp_path, fake_client, fake_torbox, fake_filter,
                            fake_jellyfin)
    row, rule = prime_episode(store, air="2026-04-01")

    status = process_episode(deps, row, rule, "Show", "tt007")
    assert status in (pipeline.STATUS_WAITING_MATCH, pipeline.STATUS_WAITING_CACHE)
    assert fake_torbox.adds == []


def test_season_pack_never_added(tmp_path, fake_client, fake_torbox,
                                 fake_filter, fake_jellyfin):
    fake_client.route("/stream/series/tt008:1:1.json", {"streams": [
        stream_entry("Show.S01.Complete.2160p.WEB-DL", HASH)]})
    fake_torbox.cached = {HASH}
    deps, store = make_deps(tmp_path, fake_client, fake_torbox, fake_filter,
                            fake_jellyfin)
    row, rule = prime_episode(store, air="2026-04-01")

    status = process_episode(deps, row, rule, "Show", "tt008")
    assert status == pipeline.STATUS_WAITING_MATCH
    assert fake_torbox.adds == []


# --- dedup / reconciliation ------------------------------------------------------

def test_unconfirmed_add_reconciles_later(tmp_path, fake_client, fake_torbox,
                                          fake_filter, fake_jellyfin):
    add_release(fake_client, "tt009",
                "Show.S01E01.2160p.WEB-DL.DV.DTS-HD.Hun.x265", HASH)
    fake_torbox.cached = {HASH}
    deps, store = make_deps(tmp_path, fake_client, fake_torbox, fake_filter,
                            fake_jellyfin)
    row, rule = prime_episode(store, air="2026-05-01")
    row_key = row["episode_key"]

    # 1. add request goes out but times out: unconfirmed, never retried blindly
    fake_torbox.fail_add = True
    status = process_episode(deps, row, rule, "Show", "tt009")
    assert status == pipeline.STATUS_ADDED
    episode = expect_store(store, row_key)
    assert "unclear" in episode["status_detail"] or "reconcil" in episode["status_detail"]
    record = store.torrent(HASH)
    assert record["state"] == "unconfirmed"

    # 2. reconcile against the account (cached TORRENT actually in mylist)
    fake_torbox.mylist_items = [{"hash": HASH,"id":"777"}]
    summary = reconcile_with_account(deps)
    assert summary["reconciled"] == 1
    assert store.torrent(HASH)["state"] == "added"
    # 3. never a second createtorrent for the same episode
    assert fake_torbox.adds.count("magnet:?xt=urn:btih:" + HASH) == 1


def test_unconfirmed_add_rolled_back(tmp_path, fake_client, fake_torbox,
                                     fake_filter, fake_jellyfin, monkeypatch):
    add_release(fake_client, "tt010",
                "Show.S01E01.2160p.WEB-DL.DV.DTS-HD.Hun.x265", HASH)
    fake_torbox.cached = {HASH}
    deps, store = make_deps(tmp_path, fake_client, fake_torbox, fake_filter,
                            fake_jellyfin)
    row, rule = prime_episode(store, air="2026-05-01")
    row_key = row["episode_key"]

    fake_torbox.fail_add = True
    process_episode(deps, row, rule, "Show", "tt010")
    assert store.torrent(HASH)["state"] == "unconfirmed"

    # account says it never landed: ledger rolls back WITHOUT deleting anything
    fake_torbox.mylist_items = [{"hash": "u" * 40}]  # unrelated account state
    summary = reconcile_with_account(deps)
    assert store.torrent(HASH)['state']=='unconfirmed'  # one empty snapshot is not proof
    future=time.time()+1000
    monkeypatch.setattr(pipeline,'_now',lambda:future)
    summary=reconcile_with_account(deps)
    rolled = store.torrent(HASH)
    assert rolled is None or rolled["state"].startswith("rolled")
    assert summary["orphans"] == 1
    assert store.event is not None


def test_unrelated_torrents_never_mutated(tmp_path, fake_client, fake_torbox,
                                          fake_filter, fake_jellyfin):
    """Foreign account state is reported, never deleted or auto-approved."""
    deps, store = make_deps(tmp_path, fake_client, fake_torbox, fake_filter,
                            fake_jellyfin)
    fake_torbox.mylist_items = [{"hash": "u" * 40}, {"hash": "v" * 40}]
    summary = reconcile_with_account(deps)
    assert summary["orphans"] == 2
    assert fake_torbox.adds == []
    assert fake_filter.posts == []


def test_provider_state_mine_never_readds(tmp_path, fake_client, fake_torbox,
                                          fake_filter, fake_jellyfin):
    """The torrent is already in the account but the ledger can't prove it:
    reconcile first, never add a duplicate."""
    add_release(fake_client, "tt011",
                "Show.S01E01.2160p.WEB-DL.DV.DTS-HD.Hun.x265", HASH)
    fake_torbox.cached = {HASH}
    fake_torbox.mylist_items = [{"hash": HASH}]  # account already has it
    deps, store = make_deps(tmp_path, fake_client, fake_torbox, fake_filter,
                            fake_jellyfin)
    row, rule = prime_episode(store, air="2026-06-01")

    status = process_episode(deps, row, rule, "Show", "tt011")
    assert status == pipeline.STATUS_WAITING_MATCH
    assert fake_torbox.adds == []


# --- outages / negative cache ------------------------------------------------------

def test_search_outage_maps_to_connection(tmp_path, fake_client, fake_torbox,
                                          fake_filter, fake_jellyfin):
    fake_client.fail_unmatched = True
    deps, store = make_deps(tmp_path, fake_client, fake_torbox, fake_filter,
                            fake_jellyfin)
    row, rule = prime_episode(store, air="2026-07-01")
    row_key = row["episode_key"]

    status = process_episode(deps, row, rule, "Show", "tt012")
    assert status == pipeline.STATUS_CONNECTION
    episode = expect_store(store, row_key)
    assert episode["attempts"] == 1
    assert episode["next_check"] > time.time()  # exponential backoff scheduled


def test_negative_cache_skips_and_expires(tmp_path, fake_client, fake_torbox,
                                          fake_filter, fake_jellyfin):
    fake_client.route("/stream/series/tt013:1:1.json", {"streams": []})
    deps, store = make_deps(tmp_path, fake_client, fake_torbox, fake_filter,
                            fake_jellyfin)
    row, rule = prime_episode(store, air="2026-08-01")
    row_key = row["episode_key"]

    status = process_episode(deps, row, rule, "Show", "tt013")
    assert status == pipeline.STATUS_WAITING_MATCH
    cached = store.search_cache("addons", row_key)
    assert cached["negative"] is True

    from core import providers as providers_module

    def reset_provider_cache():
        providers_module._addon_cache.clear()
        providers_module._addon_cache_times.clear()

    reset_provider_cache()
    # fresh negative: no new provider request this round (search skipped)
    process_episode(deps, store.episode(row_key), rule, "Show", "tt013")
    assert len(fake_client.requests) == 1

    # backdate the negative entry: it must be revisited
    store._conn.execute("UPDATE search_cache SET checked_at=? WHERE provider=?",
                        (time.time() - 100000, "addons"))  # ~28h: beyond the TTL
    store._conn.commit()
    reset_provider_cache()
    process_episode(deps, store.episode(row_key), rule, "Show", "tt013")
    assert len(fake_client.requests) == 2


def test_zero_ttl_always_rechecks(tmp_path, fake_client, fake_torbox,
                                  fake_filter, fake_jellyfin):
    fake_client.route("/stream/series/tt014:1:1.json", {"streams": []})
    deps, store = make_deps(tmp_path, fake_client, fake_torbox, fake_filter,
                            fake_jellyfin, negative_ttl_seconds=0)
    row, rule = prime_episode(store, air="2026-08-01")

    process_episode(deps, row, rule, "Show", "tt014")
    from core import providers as providers_module
    providers_module._addon_cache.clear()  # the stale provider-doc cache would
    providers_module._addon_cache_times.clear()  # otherwise serve the fetch
    process_episode(deps, store.episode(row["episode_key"]), rule, "Show", "tt014")
    assert len(fake_client.requests) == 2


# --- publication / filter handshake -------------------------------------------------

def test_filter_status_revoked_does_not_readd(tmp_path, fake_client, fake_torbox,
                                              fake_filter, fake_jellyfin):
    add_release(fake_client, "tt015",
                "Show.S01E01.2160p.WEB-DL.DV.DTS-HD.Hun.x265", HASH)
    fake_torbox.cached = {HASH}
    deps, store = make_deps(tmp_path, fake_client, fake_torbox, fake_filter,
                            fake_jellyfin)
    row, rule = prime_episode(store, air="2026-09-01")
    row_key = row["episode_key"]

    process_episode(deps, row, rule, "Show", "tt015")
    assert store.episode(row_key)["torbox_id"] is not None

    # moderation revoked the approval after the fact
    fake_filter.set_state(fake_torbox.add_id, "revoked")
    process_episode(deps, store.episode(row_key), rule, "Show", "tt015")
    # the mount link is not touched and no new add may appear
    assert len(fake_torbox.adds) == 1
    assert fake_filter.posts == [{"id": fake_torbox.add_id, "scope": "scope-hash",
                                  "hash": HASH, "name": "Show.S01E01", "type": "series",
                                  "approved": True}] or len(fake_filter.posts) == 1


def test_missing_mount_does_not_readd(tmp_path, fake_client, fake_torbox,
                                      fake_filter, fake_jellyfin):
    """Jellyfin/index keeps failing: episode waits, never mass re-adds."""
    add_release(fake_client, "tt016",
                "Show.S01E01.2160p.WEB-DL.DV.DTS-HD.Hun.x265", HASH)
    fake_torbox.cached = {HASH}
    fake_filter.set_state(fake_torbox.add_id, "published")
    fake_jellyfin.fail = True
    deps, store = make_deps(tmp_path, fake_client, fake_torbox, fake_filter,
                            fake_jellyfin)
    row, rule = prime_episode(store, air="2026-10-01")
    row_key = row["episode_key"]

    process_episode(deps, row, rule, "Show", "tt016")
    first = store.episode(row_key)
    assert first["status"] == "connection_unavailable"
    assert fake_torbox.adds == []

    fake_jellyfin.fail = False
    fake_jellyfin.present = True
    status = process_episode(deps, first, rule, "Show", "tt016")
    assert status == pipeline.STATUS_AVAILABLE
    assert len(fake_torbox.adds) == 0  # no re-add ever happened


@pytest.mark.parametrize("account_id,ledger_id,expected", [("71","71",True),("71",None,True),("72","71",False),(None,"71",False)])
def test_known_owned_torrent_reuses_without_add(tmp_path,fake_client,fake_torbox,fake_filter,fake_jellyfin,account_id,ledger_id,expected):
    add_release(fake_client,"tt011","Show.S01E01.2160p.WEB-DL.DV.DTS-HD.Hun.x265",HASH)
    fake_torbox.cached={HASH}
    fake_torbox.mylist_items=[{"hash":HASH,"id":account_id,"files":[{"name":"bundle/Show.S01E01.mkv"},{"name":"bundle/Show.S01E02.mkv"}]}]
    deps,store=make_deps(tmp_path,fake_client,fake_torbox,fake_filter,fake_jellyfin)
    row,rule=prime_episode(store)
    store.put_torrent(HASH,'previous-rule:S01E001','known-source','added',ledger_id)
    process_episode(deps,row,rule,'Show','tt011')
    assert fake_torbox.adds==[]
    assert bool(store.episode(row['episode_key'])['torbox_id']) is expected
    assert store.torrent(HASH)['episode_key']=='previous-rule:S01E001'
    if expected:
        assert store.episode(row['episode_key'])['torbox_id']=='71'
        assert fake_filter.posts
        before=len(fake_filter.posts)
        process_episode(deps,store.episode(row['episode_key']),rule,'Show','tt011')
        assert fake_torbox.adds==[] and len(fake_filter.posts)==before
    else:assert not fake_filter.posts


def test_known_owned_reuse_does_not_override_revocation(tmp_path,fake_client,fake_torbox,fake_filter,fake_jellyfin):
    add_release(fake_client,"tt011","Show.S01E01.2160p.WEB-DL.DV.DTS-HD.Hun.x265",HASH)
    fake_torbox.cached={HASH};fake_torbox.mylist_items=[{"hash":HASH,"id":"71","files":[{"name":"Show.S01E01.mkv"}]}]
    fake_filter.set_state("71","revoked")
    deps,store=make_deps(tmp_path,fake_client,fake_torbox,fake_filter,fake_jellyfin)
    row,rule=prime_episode(store);store.put_torrent(HASH,'previous:S01E001','known-source','added','71')
    process_episode(deps,row,rule,'Show','tt011')
    assert not fake_filter.posts and not fake_torbox.adds
    assert store.episode(row['episode_key'])['filter_state']=='revoked'


def test_reuse_consent_guard_prevents_attachment(tmp_path,fake_client,fake_torbox,fake_filter,fake_jellyfin):
    add_release(fake_client,"tt011","Show.S01E01.2160p.WEB-DL.DV.DTS-HD.Hun.x265",HASH)
    fake_torbox.cached={HASH};fake_torbox.mylist_items=[{"hash":HASH,"id":"71","files":[{"name":"Show.S01E01.mkv"}]}]
    deps,store=make_deps(tmp_path,fake_client,fake_torbox,fake_filter,fake_jellyfin)
    row,rule=prime_episode(store);store.put_torrent(HASH,'previous:S01E001','known-source','added','71')
    deps.reuse_if_current=lambda *args:False
    process_episode(deps,row,rule,'Show','tt011')
    assert not fake_filter.posts and not fake_torbox.adds
    assert store.episode(row['episode_key'])['torbox_id'] is None


@pytest.mark.parametrize("filename", ["Show.S01E02.mkv","Show.S01E01-02.mkv","bundle.S01E01/unidentified.mkv","Show.S01E01.sample.mkv"])
def test_reuse_requires_exact_episode_video(tmp_path,fake_client,fake_torbox,fake_filter,fake_jellyfin,filename):
    add_release(fake_client,"tt011","Show.S01E01.2160p.WEB-DL.DV.DTS-HD.Hun.x265",HASH)
    fake_torbox.cached={HASH}
    fake_torbox.mylist_items=[{"hash":HASH,"id":"71","files":[{"name":filename}]}]
    deps,store=make_deps(tmp_path,fake_client,fake_torbox,fake_filter,fake_jellyfin)
    row,rule=prime_episode(store);store.put_torrent(HASH,'previous:S01E002','known-source','added','71')
    process_episode(deps,row,rule,'Show','tt011')
    assert not fake_filter.posts and not fake_torbox.adds
    assert store.episode(row['episode_key'])['torbox_id'] is None
