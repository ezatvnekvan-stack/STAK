import json
import os
import threading
import urllib.error
import urllib.request
from pathlib import Path

import pytest

import filter_server as legacy
from core import service
from core.rules import rule_from_dict
from server.main import InProcessLibrary, unified_handler
from server.organizer import OrganizedFilter, NeedsIdentity, episode_numbers
from conftest import rule_payload


@pytest.fixture
def organized(tmp_path):
    source = tmp_path / "source"
    source.mkdir()
    lib = OrganizedFilter({"source_root": str(source), "output_root": str(tmp_path / "library"),
        "target_root": "/media/zurg/__all__", "db": str(tmp_path / "filter.db"),
        "token": "fixture-token-" * 4, "torbox_key": "fixture-account", "layout": "organized"})
    lib.worker_alive = True
    yield lib
    lib.db.close()


def add_media(lib, release, names, torrent_id="1", h="a" * 40, kind="series", media=None):
    files = []
    for index, name in enumerate(names):
        path = lib.source / release / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"x" * (index + 11))
        files.append({"name": release + "/" + name, "size": index + 11})
    job = lib.approve({"id": torrent_id, "scope": lib.scope, "hash": h, "name": "A Show" if kind == "series" else "A Movie",
        "type": kind, "approved": True, "media": media or {"imdb": "tt0001234", "title": "A Show" if kind == "series" else "A Movie", "year": 2026}})
    torrent = {"id": torrent_id, "hash": h, "name": release, "download_finished": True, "files": files}
    return job, torrent


def test_episode_torrents_share_one_series_and_season(organized):
    lib = organized
    a, ta = add_media(lib, "release1", ["Show.S01E01.mkv"])
    b, tb = add_media(lib, "release2", ["Show.S01E02.mkv"], "2", "b" * 40)
    groups = legacy.index_source(lib.source)
    lib.publish(a, ta, groups)
    lib.publish(b, tb, groups)
    folders = [p for p in (lib.output / "shows").iterdir() if p.is_dir()]
    assert len(folders) == 1
    assert folders[0].name == "A Show (2026) [imdbid-tt0001234]"
    assert len(list((folders[0] / "Season 01").glob("*.mkv"))) == 2
    assert all(p.is_symlink() for p in folders[0].rglob("*.mkv"))


def test_bundle_separate_videos_are_individually_organized(organized):
    job, torrent = add_media(organized, "bundle", ["Show.S01E01.mkv", "Show.S01E02.mkv"])
    organized.publish(job, torrent, legacy.index_source(organized.source))
    links = list(organized.output.rglob("*.mkv"))
    assert len(links) == 2 and all(p.parent.name == "Season 01" for p in links)
    assert len({os.readlink(p) for p in links}) == 2


def test_combined_video_stays_one_link_with_multi_episode_name(organized):
    job, torrent = add_media(organized, "bundle", ["Show.S01E01-02.mkv"])
    organized.publish(job, torrent, legacy.index_source(organized.source))
    links = list(organized.output.rglob("*.mkv"))
    assert len(links) == 1 and "S01E01-E02" in links[0].name


def test_movies_use_identified_movie_folders(organized):
    job, torrent = add_media(organized, "Movie.2026.2160p", ["Movie.2026.2160p.mkv"], kind="movie", media={"tmdb": "12345", "title": "A Movie", "year": 2026})
    organized.publish(job, torrent, legacy.index_source(organized.source))
    link = next(organized.output.rglob("*.mkv"))
    assert link.parent.name == "A Movie (2026) [tmdbid-12345]"
    assert "movies" in link.parts


def test_uncertain_movie_with_multiple_videos_retains_existing_layout(organized):
    job, torrent = add_media(organized, "Movie", ["first.mkv", "second.mkv"], kind="movie")
    with pytest.raises(NeedsIdentity):
        organized.publish(job, torrent, legacy.index_source(organized.source))
    assert not list(organized.output.rglob("*.mkv"))


def test_subtitles_are_linked_and_samples_are_excluded(organized):
    job, torrent = add_media(organized, "release", ["Show.S01E01.mkv", "sample.mkv"])
    (organized.source / "release" / "Show.S01E01.eng.srt").write_text("fixture")
    organized.publish(job, torrent, legacy.index_source(organized.source))
    assert len(list(organized.output.rglob("*.mkv"))) == 1
    subtitle = next(organized.output.rglob("*.srt"))
    assert subtitle.is_symlink() and subtitle.name.endswith(".eng.srt")


def test_legacy_layout_is_migrated_only_after_complete_plan(organized):
    lib = organized
    job, torrent = add_media(lib, "release", ["Show.S01E01.mkv"])
    lib.c["layout"] = "legacy"
    lib.publish(job, torrent, legacy.index_source(lib.source))
    old = next(lib.output.rglob("*.mkv"))
    lib.c["layout"] = "organized"
    lib.publish(lib.get("1"), torrent, legacy.index_source(lib.source))
    assert not old.is_symlink()
    assert len(list(lib.output.rglob("*.mkv"))) == 1
    assert (lib.source / "release" / "Show.S01E01.mkv").exists()


def test_series_title_without_provider_id_is_organized_without_inventing_identity(organized):
    job, torrent = add_media(organized, "release", ["Show.S01E01.mkv"])
    job = organized.approve(dict(job, media={"title": "Unknown"}))
    organized.publish(job, torrent, legacy.index_source(organized.source))
    link = next(organized.output.rglob("*.mkv"))
    assert link.parent.name == "Season 01"
    assert link.parent.parent.name == "Unknown"
    assert "imdb" not in organized.get("1")["media"]


def test_stable_identity_does_not_split_after_display_name_change(organized):
    job, torrent = add_media(organized, "release", ["Show.S01E01.mkv"])
    organized.publish(job, torrent, legacy.index_source(organized.source))
    changed = organized.approve(dict(job, media={"imdb": "tt0001234", "title": "Another display title"}))
    organized.publish(changed, torrent, legacy.index_source(organized.source))
    assert len(list((organized.output / "shows").glob("*/Season 01"))) == 1
    assert len(list(organized.output.rglob("*.mkv"))) == 1


def test_inflight_job_does_not_undo_revoke(organized):
    job, torrent = add_media(organized, "release", ["Show.S01E01.mkv"])
    organized.approve(dict(job, approved=False))
    assert organized.publish(job, torrent, legacy.index_source(organized.source)) is False
    assert not list(organized.output.rglob("*.mkv"))


def test_regular_file_collision_is_never_overwritten(organized):
    job, torrent = add_media(organized, "release", ["Show.S01E01.mkv"])
    planned = organized.plan(job, torrent, legacy.index_source(organized.source))[3]
    destination = planned[0][0]
    destination.parent.mkdir(parents=True)
    destination.write_bytes(b"personal")
    with pytest.raises(ValueError):
        organized.publish(job, torrent, legacy.index_source(organized.source))
    assert destination.read_bytes() == b"personal"


def test_metadata_survives_older_app_edit(organized):
    job, torrent = add_media(organized, "release", ["Show.S01E01.mkv"])
    body = dict(job)
    body.pop("media")
    edited = organized.approve(body)
    assert edited["media"]["imdb"] == "tt0001234"


def test_interrupted_layout_journal_recovers_complete_new_layout(organized, monkeypatch):
    lib = organized
    job, torrent = add_media(lib, "release", ["Show.S01E01.mkv"])
    lib.c["layout"] = "legacy"
    lib.publish(job, torrent, legacy.index_source(lib.source))
    old = next(lib.output.rglob("*.mkv"))
    lib.c["layout"] = "organized"
    original = lib.unlink_owned
    monkeypatch.setattr(lib, "unlink_owned", lambda _: (_ for _ in ()).throw(OSError("interrupted")))
    with pytest.raises(OSError):
        lib.publish(lib.get("1"), torrent, legacy.index_source(lib.source))
    assert old.is_symlink() and len(list(lib.output.rglob("*.mkv"))) == 2
    monkeypatch.setattr(lib, "unlink_owned", original)
    lib.recover_layout()
    assert not old.is_symlink() and len(list(lib.output.rglob("*.mkv"))) == 1
    assert lib.db.execute("select count(*) from layout_journal").fetchone()[0] == 0


@pytest.mark.parametrize("name,expected", [("Show.S01E01.mkv", (1, [1])), ("Show.S01E01-02.mkv", (1, [1, 2])), ("Show.S01E01E02.mkv", (1, [1, 2])), ("Show.1x01-02.mkv", (1, [1, 2])), ("Show.S00E01.mkv", (0, [1]))])
def test_episode_file_patterns(name, expected):
    assert episode_numbers(name) == expected


def test_episode_parent_folder_does_not_supply_guessed_number():
    with pytest.raises(NeedsIdentity):
        episode_numbers("Show.S01E01/unidentified.mkv")


def test_inprocess_approval_passes_verified_series_identity(organized):
    adapter = InProcessLibrary(organized)
    rule = rule_from_dict(rule_payload())
    result = adapter.post_series_approval("77", organized.scope, "a" * 40, "Release", rule.series)
    assert result["media"]["imdb"] == "tt001"
    assert result["type"] == "series"


def test_single_api_and_old_filter_port_contract(organized, tmp_path):
    token = "fixture-server-token-123456789012345"
    svc = service.WatchService(service.Config(token=token, db_path=str(tmp_path / "watch.db")))
    server = service.WatchHTTPServer(("127.0.0.1", 0), svc)
    server.RequestHandlerClass = unified_handler(svc, organized, "old-filter-token")
    server.service_actions = lambda: None
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    base = f"http://127.0.0.1:{server.server_port}"
    try:
        req = urllib.request.Request(base + "/library/v1/approvals", headers={"Authorization": "Bearer " + token})
        assert json.load(urllib.request.urlopen(req))["items"] == []
        req = urllib.request.Request(base + "/library/v1/status", headers={"Authorization": "Bearer old-filter-token"})
        assert json.load(urllib.request.urlopen(req))["scope"] == organized.scope
        with pytest.raises(urllib.error.HTTPError) as error:
            urllib.request.urlopen(base + "/library/v1/approvals")
        assert error.value.code == 401
        req = urllib.request.Request(base + "/v1/status", headers={"Authorization": "Bearer old-filter-token"})
        with pytest.raises(urllib.error.HTTPError) as error:
            urllib.request.urlopen(req)
        assert error.value.code == 401
    finally:
        server.shutdown()
        server.server_close()
        svc.store.close()


def test_cached_add_to_organized_library_round_trip(organized, tmp_path, fake_client, fake_torbox, fake_filter, fake_jellyfin):
    from test_pipeline import make_deps, prime_episode, add_release
    from core import pipeline
    filename = "Show.S01E01.2160p.WEB-DL.DV.DTS-HD.Hun.x265.mkv"
    path = organized.source / "release" / filename
    path.parent.mkdir()
    path.write_bytes(b"fixture-only")
    h = "a" * 40
    add_release(fake_client, "tt001", filename, h)
    fake_torbox.cached = {h}
    deps, store = make_deps(tmp_path, fake_client, fake_torbox, fake_filter, fake_jellyfin, filter_scope=organized.scope)
    deps.filter = InProcessLibrary(organized)
    deps.verify_filter = True
    fake_jellyfin.episode_present = lambda *args: bool(organized.get("777") and organized.get("777")["state"] == "published")
    row, rule = prime_episode(store)
    assert pipeline.process_episode(deps, row, rule, "Show", "tt001") == pipeline.STATUS_ADDED
    job = organized.get("777")
    assert job["media"]["imdb"] == "tt001"
    organized.catalog = lambda: {"777": {"id": "777", "hash": h, "download_finished": True,
        "name": "release", "files": [{"name": "release/" + filename, "size": len(b"fixture-only")} ]}}
    organized.reconcile()
    assert organized.get("777")["state"] == "published"
    assert pipeline.process_episode(deps, store.episode(row["episode_key"]), rule, "Show", "tt001") == pipeline.STATUS_AVAILABLE
    assert len(fake_torbox.adds) == 1
    assert len(list(organized.output.rglob("*.mkv"))) == 1
    store.close()


def test_identity_enrichment_cannot_reapprove_revoked_job(organized):
    job, torrent = add_media(organized, "release", ["Show.S01E01.mkv"])
    organized.approve(dict(job, approved=False))
    updated = organized.identify({"id": "1", "scope": organized.scope, "hash": "a" * 40,
                                  "media": {"imdb": "tt0001234", "title": "Verified title"}})
    assert updated["approved"] is False
    with pytest.raises(ValueError):
        organized.identify({"id": "1", "scope": organized.scope, "hash": "b" * 40, "media": {"title": "Wrong"}})


def test_movie_title_year_is_not_mistaken_for_release_year():
    from server.organizer import identity
    result = identity({"type": "movie", "name": "2001 A Space Odyssey"}, {"name": "2001.A.Space.Odyssey.1968.2160p.mkv"})
    assert result[2] == "2001 A Space Odyssey (1968)"


def test_unicode_titles_stay_within_filesystem_component_limits(organized):
    job, torrent = add_media(organized, "release", ["Show.S01E01.mkv"], media={"imdb": "tt0001234", "title": "長" * 160})
    organized.publish(job, torrent, legacy.index_source(organized.source))
    link = next(organized.output.rglob("*.mkv"))
    assert len(link.name.encode()) <= 255 and len(link.parent.parent.name.encode()) <= 255


def test_organizer_never_reads_video_contents(organized, monkeypatch):
    job, torrent = add_media(organized, "release", ["Show.S01E01.mkv"])
    original = Path.open
    def guard(path, *args, **kwargs):
        if path.suffix.lower() in legacy.VIDEO:
            raise AssertionError("Video bytes must never be opened")
        return original(path, *args, **kwargs)
    monkeypatch.setattr(Path, "open", guard)
    organized.publish(job, torrent, legacy.index_source(organized.source))
    assert organized.get("1")["state"] == "published"


def test_layout_change_invalidates_only_derived_library_state_once(tmp_path):
    import time
    from core.store import Store
    from server.main import reset_layout_verification
    store = Store(str(tmp_path / "watch.db"))
    store.upsert_episode("r1:S01E001", "r1", 1, 1, "2026-01-01")
    store.put_torrent("a" * 40, "r1:S01E001", "fixture", "added", "777")
    store.set_episode_torrent("r1:S01E001", "a" * 40, "fixture", "777", time.time())
    store.set_series_jellyfin("r1:S01E001", "old-library-id")
    store.set_jellyfin_verified("r1:S01E001", True)
    reset_layout_verification(store)
    row = store.episode("r1:S01E001")
    assert row["series_jellyfin"] is None and not row["jellyfin_verified"]
    assert row["torbox_id"] == "777" and row["torrent_hash"] == "a" * 40
    assert store.torrent("a" * 40)["state"] == "added"
    store.set_jellyfin_verified("r1:S01E001", True)
    reset_layout_verification(store)
    assert store.episode("r1:S01E001")["jellyfin_verified"] is True
    store.close()


def test_existing_owned_bundle_identity_backfill_preserves_unrelated_approvals(organized, tmp_path):
    import time
    from core.store import Store
    store = Store(str(tmp_path / "watch.db"))
    store.put_rule("r1", rule_payload())
    store.put_torrent("a" * 40, "r1:S01E001", "fixture", "added", "1")
    for number in (1, 2):
        key = f"r1:S01E{number:03d}"
        store.upsert_episode(key, "r1", 1, number, "2026-01-01")
        store.set_episode_torrent(key, "a" * 40, "fixture", "1", time.time())
    for identity_, h in (("1", "a" * 40), ("2", "b" * 40)):
        organized.approve({"id": identity_, "scope": organized.scope, "hash": h, "name": "Old title", "type": "series", "approved": True})
    organized.import_watcher_identities(store)
    assert organized.get("1")["media"]["imdb"] == "tt001"
    assert "media" not in organized.get("2")
    organized.import_watcher_identities(store)
    assert len(organized.get()) == 2
    store.close()

