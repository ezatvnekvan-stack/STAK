"""Ordinary approved media gets tidy folders without a manual identity step."""
import re
import pytest

import filter_server as legacy
from server.organizer import identity, episode_numbers, is_extra
from server.titles import release_title, title_year, media_kind
from test_unified import organized, add_media


def without_id(library, release, names, torrent_id="1", h="a" * 40, name=None, kind="series"):
    job, torrent = add_media(library, release, names, torrent_id, h, kind, media={"title": ""})
    job = library.approve(dict(job, name=name or release, type=kind))
    assert not job.get("media")
    return job, torrent


@pytest.mark.parametrize("release,title", [
    ("Common.Side.Effects.S01.MULTi.1080p.AMZN.WEB-DL.DDP5.1.H.264-P2P", "Common Side Effects"),
    ("www.UIndex.org    -    Yaga S01E01 The Burning Man 2160p HMAX WEB-DL DD 5 1 H 265-playWEB", "Yaga"),
    ("www.UIndex.org    -    Nocturne 2026 S01E02 For Such a Time as This 2160p ATVP WEB-DL", "Nocturne"),
    ("IT.Welcome.to.Derry.S01.UHD.BluRay.2160p.TrueHD.Atmos.7.1.DV.HEVC.REMUX-FraMeSToR", "IT Welcome to Derry"),
    ("Carrie.S01.2160p.AMZN.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-FLUX", "Carrie"),
    ("Blade.Runner.2049.2017.2160p.WEB-DL.mkv", "Blade Runner 2049"),
])
def test_owner_release_names_get_clean_titles(release, title):
    assert release_title(release)[0] == title


@pytest.mark.parametrize("release,filename,title", [
    ("Common.Side.Effects.S01.MULTi.1080p.AMZN.WEB-DL", "Common.Side.Effects.S01E01.mkv", "Common Side Effects"),
    ("www.UIndex.org - Yaga S01E01 2160p WEB-DL", "Yaga.S01E01.mkv", "Yaga"),
    ("IT.Welcome.to.Derry.S01.2160p.REMUX", "IT.Welcome.to.Derry.S01E01.mkv", "IT Welcome to Derry"),
    ("Carrie.S01.2160p.AMZN.WEB-DL", "Carrie_S01_E01_2160p.mkv", "Carrie"),
])
def test_legacy_series_reorganizes_without_identify(organized, release, filename, title):
    job, torrent = without_id(organized, release, [filename])
    # Simulate the owner's already-published release-folder layout.
    organized.c["layout"] = "legacy"
    organized.publish(job, torrent, legacy.index_source(organized.source))
    organized.c["layout"] = "organized"
    organized.publish(organized.get("1"), torrent, legacy.index_source(organized.source))
    links = list(organized.output.rglob("*.mkv"))
    assert len(links) == 1
    assert links[0].parent.name == "Season 01"
    assert links[0].parent.parent.name == title
    assert organized.get("1")["state"] == "published"
    assert not any(k in organized.get("1").get("media", {}) for k in ("imdb", "tmdb", "tvdb"))


def test_separate_torrents_with_same_series_share_one_season(organized):
    a, ta = without_id(organized, "www.UIndex.org - Yaga S01E01 2160p", ["Yaga.S01E01.mkv"])
    b, tb = without_id(organized, "www.UIndex.org - Yaga S01E02 2160p", ["Yaga.S01E02.mkv"], "2", "b" * 40)
    for job, torrent in ((a, ta), (b, tb)):
        organized.publish(job, torrent, legacy.index_source(organized.source))
    assert len(list((organized.output / "shows").glob("*/Season 01"))) == 1
    assert len(list(organized.output.rglob("*.mkv"))) == 2


def test_one_known_release_year_is_shared_with_same_title(monkeypatch, organized):
    a, ta = without_id(organized, "Nocturne.S01E01.2160p", ["Nocturne.S01E01.mkv"], name="Nocturne")
    b, tb = without_id(organized, "Nocturne.2026.S01E02.2160p", ["Nocturne.S01E02.mkv"], "2", "b" * 40, name="Nocturne")
    monkeypatch.setattr(legacy.Filter, "catalog", lambda _: {"1": ta, "2": tb})
    organized.reconcile()
    folders = list((organized.output / "shows").glob("*/Season 01"))
    assert len(folders) == 1 and folders[0].parent.name == "Nocturne (2026)"


def test_conflicting_remake_years_are_not_propagated(monkeypatch, organized):
    a, ta = without_id(organized, "Show.2020.S01E01.2160p", ["Show.S01E01.mkv"], name="Show")
    b, tb = without_id(organized, "Show.2026.S01E02.2160p", ["Show.S01E02.mkv"], "2", "b" * 40, name="Show")
    monkeypatch.setattr(legacy.Filter, "catalog", lambda _: {"1": ta, "2": tb})
    organized.catalog()
    assert ("series", "show") not in organized._year_hints


@pytest.mark.parametrize("name,parents,expected", [
    ("Show_S01_E02_2160p.mkv", [], (1, [2])),
    ("Show.S01.E02.mkv", [], (1, [2])),
    ("Episode 02.mkv", ["Show", "Season 01"], (1, [2])),
    ("02 - Episode title.mkv", ["Show.S01.COMPLETE"], (1, [2])),
])
def test_explicit_episode_numbering_variants(name, parents, expected):
    assert episode_numbers(name, parents) == expected


def test_explicit_conflicting_season_folders_are_not_guessed():
    with pytest.raises(ValueError):
        episode_numbers("Episode 02.mkv", ["Show.S01", "Season 02"])


def test_unknown_category_is_detected_for_ordinary_series(organized):
    job, torrent = without_id(organized, "Show.S01E01.2160p", ["Show.S01E01.mkv"], kind="unknown")
    organized.publish(job, torrent, legacy.index_source(organized.source))
    assert organized.get("1")["type"] == "series"
    assert next(organized.output.rglob("*.mkv")).parent.name == "Season 01"


def test_movie_without_provider_or_year_still_gets_clean_folder(organized):
    job, torrent = without_id(organized, "A.Movie.1080p.WEB-DL", ["A.Movie.1080p.mkv"], kind="unknown")
    organized.publish(job, torrent, legacy.index_source(organized.source))
    assert organized.get("1")["type"] == "movie"
    assert next(organized.output.rglob("*.mkv")).parent.name == "A Movie"


def test_numeric_movie_title_keeps_its_real_release_year():
    key, title, folder = identity({"type": "movie", "name": "Blade Runner 2049"}, {"name": "Blade.Runner.2049.2017.2160p.mkv"})
    assert title == "Blade Runner 2049" and folder == "Blade Runner 2049 (2017)"


def test_4k_resolution_is_never_mistaken_for_a_year():
    assert title_year({"type": "series", "name": "Nocturne"}, {"name": "Nocturne.S01E01.2160p.WEB-DL"}) == ("Nocturne", None)


def test_real_series_called_extras_is_not_discarded():
    from pathlib import Path
    assert not is_extra(Path("Extras.S01.1080p/Extras.S01E01.mkv"))
    assert is_extra(Path("Show.S01/sample.mkv"))
    assert is_extra(Path("Show.S01/extras/behind-scenes.mkv"))
