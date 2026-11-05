"""Matching / ranking / identification of releases against rules."""

import time
from datetime import date

import pytest

from core import rules as R
from core.episode import EpisodeContext, parse_air_date, parse_episode_marker, is_season_pack, episode_is_released
from core.matching import Candidate, match_candidate, match_release_list, preference_key


def ctx(season=1, episode=1, air=(2026, 1, 1), enabled=True, rule_started=(2026, 1, 1)):
    return EpisodeContext(rule_id="r1", season=season, episode=episode,
                          air_date=date(*air), rule_started_on=date(*rule_started), enabled=enabled)


def rule(**changes):
    base = dict(
        rule_id="r1",
        series=R.SeriesIdentities(imdb="tt001"),
        resolution_mode=R.RESOLUTION_2160_PREFERRED,
        resolution_wait_hours=24,
    )
    base.update(changes)
    return R.WatchRule(**base)


def cand(name, size=None, h=None, provider="torrentio"):
    return Candidate(
        name=name.split(".")[0] if False else name,
        release_text=name,
        size_bytes=size,
        info_hash=h,
        source_identity=f"{provider}:{name}",
        provider=provider,
    )


NOW = time.time()


# --- identification ---------------------------------------------------------

def test_episode_marker_parsing():
    assert parse_episode_marker("Show.S01E02") == (1, 2)
    assert parse_episode_marker("Show.1x03") == (1, 3)
    assert parse_episode_marker("Show") is None


def test_season_pack_detection():
    assert is_season_pack("Show.S01.WEBRip", 1)
    assert is_season_pack("Show.Season.1", 1)
    assert not is_season_pack("Show.S01E05", 1)
    assert not is_season_pack("Show.S02", 1)      # different season
    assert is_season_pack("Show.S02 Pack", 1) is False


def test_auto_mode_never_takes_season_packs():
    """Every season pack gets rejected even when it would otherwise match."""
    c = cand("Show.S01.2160p.WEB-DL.HDR", h="a" * 40)
    verdict = match_candidate(c, rule(), ctx(), {c.info_hash}, now=NOW)
    assert not verdict.accepted
    assert "season pack" in verdict.reason
    wrong = cand("Show.S01E05.2160p.WEB-DL.HDR", h="b" * 40)
    verdict = match_candidate(wrong, rule(), ctx(season=1, episode=2), {wrong.info_hash}, now=NOW)
    assert not verdict.accepted
    assert "does not match" in verdict.reason
    unmarked = cand("Show.Complete.Collection", h="c" * 40)
    verdict = match_candidate(unmarked, rule(), ctx(), {unmarked.info_hash}, now=NOW)
    assert not verdict.accepted
    assert "no episode marker" in verdict.reason
    right = cand("Show.S01E02.2160p.WEB-DL.HDR", h="d" * 40)
    assert match_candidate(right, rule(), ctx(season=1, episode=2), {right.info_hash}, now=NOW).accepted


# --- released / scope -------------------------------------------------------

def test_not_released_rejected():
    c = cand("Show.S01E01.2160p.WEB-DL", h="b" * 40)
    rule_obj = rule(include_specials=False)
    assert not match_candidate(c, rule_obj, ctx(air=(2099, 1, 1)), {c.info_hash}, now=NOW).accepted
    assert match_candidate(c, rule_obj, ctx(air=(2020, 1, 1)), {c.info_hash}, now=NOW).accepted


def test_specials_default_unwatched():
    rule_obj = rule()
    c = cand("Show.S00E05.1080p.WEB-DL", h="c" * 40)
    exceptions = match_candidate(c, rule_obj, ctx(season=0, episode=5), {c.info_hash}, now=NOW)
    assert not exceptions.accepted
    rule_special = rule(include_specials=True)
    assert match_candidate(c, rule_special, ctx(season=0, episode=5), {c.info_hash}, now=NOW).accepted


def test_watched_season_scopes():
    c_s2 = cand("Show.S02E01.1080p.WEB-DL", h="d" * 40)
    c_s1 = cand("Show.S01E01.1080p.WEB-DL", h="d1" * 20)
    rule_sel = rule(watched_seasons=(1,))
    assert not match_candidate(c_s2, rule_sel, ctx(season=2), {c_s2.info_hash}, now=NOW).accepted
    assert match_candidate(c_s1, rule_sel, ctx(season=1), {c_s1.info_hash}, now=NOW).accepted


# --- resolution modes -------------------------------------------------------

def test_strict_four_k_never_downgrades():
    c = cand("Show.S01E01.1080p.WEB-DL", h="e" * 40)
    rule_obj = rule(resolution_mode=R.RESOLUTION_2160_ONLY)
    verdict = match_candidate(c, rule_obj, ctx(), {c.info_hash}, now=NOW)
    assert not verdict.accepted
    assert "1080p" in verdict.reason or "4K" in verdict.reason or "Resolution" in verdict.reason


def test_2160_preferred_allows_after_wait_hours():
    c = cand("Show.S01E01.1080p.WEB-DL", h="f" * 40)
    air = (2026, 10, 30)
    rule24 = rule(resolution_mode=R.RESOLUTION_2160_PREFERRED, resolution_wait_hours=24)
    import calendar
    # air 2026-10-30 00:00 UTC; cutoff = air + 24 h => 2026-10-31 00:00 UTC
    cutoff = calendar.timegm((2026, 10, 31, 0, 0, 0, 0, 0, 0))
    before = match_candidate(c, rule24, ctx(air=air), {c.info_hash}, now=cutoff - 1)
    assert not before.accepted
    assert "4K" in before.reason or "1080p" in before.reason
    after = match_candidate(c, rule24, ctx(air=air), {c.info_hash}, now=cutoff + 1)
    assert after.accepted


def test_strict_four_k_ignores_wait():
    """Even with wait hours configured, 2160_only never accepts 1080p."""
    c = cand("Show.S01E01.1080p.WEB-DL", h="0" * 40)
    rule_obj = rule(resolution_mode=R.RESOLUTION_2160_ONLY, resolution_wait_hours=0)
    assert not match_candidate(c, rule_obj, ctx(air=(2020, 1, 1)), {c.info_hash}, now=NOW).accepted


# --- traits / unknown ------------------------------------------------------

def test_mandatory_unknown_traits_rejected():
    c = cand("Show.S01E01.2160p.WEB-DL")  # no HDR/DV/Atmos marker at all
    rule_obj = rule(hdr_mode=R.MODE_MANDATORY)
    verdict = match_candidate(c, rule_obj, ctx(), {c.info_hash}, now=NOW)
    assert not verdict.accepted
    assert "HDR" in verdict.reason or "not verified" in verdict.reason


def test_mandatory_hdr_satisfied():
    c = cand("Show.S01E01.2160p.WEB-DL.HDR", h="1" * 40)
    rule_obj = rule(hdr_mode=R.MODE_MANDATORY)
    assert match_candidate(c, rule_obj, ctx(), {c.info_hash}, now=NOW).accepted


def test_mandatory_dv_and_atmos():
    c = cand("Show.S01E01.2160p.WEB-DL.DV.Atmos", h="2" * 40)
    rule_obj = rule(dolby_vision_mode=R.MODE_MANDATORY, atmos_mode=R.MODE_MANDATORY)
    assert match_candidate(c, rule_obj, ctx(), {c.info_hash}, now=NOW).accepted
    rule_hdr_needed = rule(hdr_mode=R.MODE_MANDATORY)
    assert not match_candidate(c, rule_hdr_needed, ctx(), {c.info_hash}, now=NOW).accepted


def test_contradictory_multi_language_gives_clear_feedback():
    """MULTI can never satisfy mandatory Hungarian, and earns a clear reason."""
    c = cand("Show.S01E01.2160p.MULTI.WEB-DL", h="3" * 40)
    rule_obj = rule(language_mode=R.LANG_HUN_MANDATORY)
    verdict = match_candidate(c, rule_obj, ctx(), {c.info_hash}, now=NOW)
    assert not verdict.accepted
    assert "Hungarian audio cannot be verified" in verdict.reason or "Hungarian" in verdict.reason


def test_hungarian_subtitle_is_not_hungarian_audio():
    c = cand("Show.S01E01.2160p.HunSub.WEB-DL", h="4" * 40)
    rule_obj = rule(language_mode=R.LANG_HUN_MANDATORY)
    verdict = match_candidate(c, rule_obj, ctx(), {c.info_hash}, now=NOW)
    assert not verdict.accepted


def test_hungarian_dub_satisfied():
    c = cand("Show.S01E01.2160p.HUN.DUB.WEB-DL", h="5" * 40)
    rule_obj = rule(language_mode=R.LANG_HUN_MANDATORY)
    assert match_candidate(c, rule_obj, ctx(), {c.info_hash}, now=NOW).accepted


def test_original_language_rejects_hungarian_dub():
    c = cand("Show.S01E01.2160p.HUN.WEB-DL", h="6" * 40)
    rule_obj = rule(language_mode=R.LANG_ORIGINAL)
    verdict = match_candidate(c, rule_obj, ctx(), {c.info_hash}, now=NOW)
    assert not verdict.accepted


def test_original_language_uses_english_or_default():
    eng = cand("Show.S01E01.2160p.WEB-DL.English", h="7" * 40)
    assert match_candidate(eng, rule(language_mode=R.LANG_ORIGINAL), ctx(), {eng.info_hash}, now=NOW).accepted
    plain = cand("Show.S01E01.2160p.WEB-DL", h="8" * 40)
    assert match_candidate(plain, rule(language_mode=R.LANG_ORIGINAL), ctx(), {plain.info_hash}, now=NOW).accepted
    multi = cand("Show.S01E01.2160p.MULTI.WEB-DL", h="9" * 40)
    assert not match_candidate(multi, rule(language_mode=R.LANG_ORIGINAL), ctx(), {multi.info_hash}, now=NOW).accepted


# --- type filter ------------------------------------------------------------

def test_release_type_restriction_and_unknown():
    rule_obj = rule(release_types=(R.TYPE_WEBDL,))
    good = cand("Show.S01E01.2160p.WEB-DL.HDR", h="a1" * 20)
    assert match_candidate(good, rule_obj, ctx(), {good.info_hash}, now=NOW).accepted
    other = cand("Show.S01E01.2160p.Remux.HDR", h="a2" * 20)
    verdict = match_candidate(other, rule_obj, ctx(), {other.info_hash}, now=NOW)
    assert not verdict.accepted
    unknown = cand("Show.S01E01.2160p.x264.HDR", h="a3" * 20)
    verdict = match_candidate(unknown, rule_obj, ctx(), {unknown.info_hash}, now=NOW)
    assert not verdict.accepted
    assert "unknown" in verdict.reason


def test_damaged_release_rejected():
    c = cand("Show.S01E01.2160p.HDTS", h="a4" * 20)
    verdict = match_candidate(c, rule(), ctx(), {c.info_hash}, now=NOW)
    assert "damage" in verdict.reason or "CAM" in verdict.reason or "TS" in verdict.reason


# --- size / cache -----------------------------------------------------------

def test_size_cap_and_unknown_size():
    rule_obj = rule(max_episode_gb=10)
    ok = cand("Show.S01E01.2160p.WEB-DL", size=int(9.5 * 1024 ** 3), h="b1" * 20)
    assert match_candidate(ok, rule_obj, ctx(), {ok.info_hash}, now=NOW).accepted
    big = cand("Show.S01E01.2160p.WEB-DL", size=int(11 * 1024 ** 3), h="b2" * 20)
    verdict = match_candidate(big, rule_obj, ctx(), {big.info_hash}, now=NOW)
    assert not verdict.accepted and "GB" in verdict.reason
    unknown = cand("Show.S01E01.2160p.WEB-DL", size=None, h="b3" * 20)
    verdict = match_candidate(unknown, rule_obj, ctx(), {unknown.info_hash}, now=NOW)
    assert not verdict.accepted and "size" in verdict.reason.lower()


def test_cached_only_mandatory():
    rule_obj = rule()
    uncached = cand("Show.S01E01.2160p.WEB-DL.HDR", h="c1" * 20)
    verdict = match_candidate(uncached, rule_obj, ctx(), set(), now=NOW)
    assert not verdict.accepted
    assert "cached" in verdict.reason.lower() or "TorBox" in verdict.reason
    cached = cand("Show.S01E01.2160p.WEB-DL.HDR", h="c2" * 20)
    assert match_candidate(cached, rule_obj, ctx(), {cached.info_hash}, now=NOW).accepted


# --- ranking ----------------------------------------------------------------

def test_ranking_prefers_stronger_quality_and_preferred_traits():
    """Resolution preference stays the primary rule (like the app), and
    preferred trait contracts rank among otherwise-equal candidates."""
    smart_1080 = cand("Show.S01E01.1080p.WEB-DL.DV", h="d1" * 20)
    plain_2160 = cand("Show.S01E01.2160p.WEB-DL", h="d2" * 20)
    rule_obj = rule(resolution_mode=R.RESOLUTION_2160_PREFERRED, dolby_vision_mode=R.MODE_PREFERRED)
    accepted, _ = match_release_list([plain_2160, smart_1080], rule_obj, ctx(), {smart_1080.info_hash, plain_2160.info_hash}, now=NOW)
    assert accepted[0] is plain_2160  # 2160 preferred beats1080 DV

    dv_2160 = cand("Show.S01E01.2160p.WEB-DL.DV", h="d3" * 20)
    accepted, _ = match_release_list([plain_2160, dv_2160], rule_obj, ctx(), {plain_2160.info_hash, dv_2160.info_hash}, now=NOW)
    assert accepted[0] is dv_2160  # same resolution: preferred DV ranks first
    rule_plain = rule(resolution_mode=R.RESOLUTION_2160_PREFERRED)
    accepted, _ = match_release_list([plain_2160, smart_1080], rule_plain, ctx(), {smart_1080.info_hash, plain_2160.info_hash}, now=NOW)
    assert accepted[0] is plain_2160


def test_unknown_size_ranks_after_known_size():
    known = cand("Show.S01E01.2160p.WEB-DL", size=int(4 * 1024 ** 3), h="e1" * 20)
    empty = cand("Show.S01E01.2160p.WEB-DL", size=None, h="e2" * 20)
    rule_obj = rule()
    accepted, _ = match_release_list([empty, known], rule_obj, ctx(), {known.info_hash, empty.info_hash}, now=NOW)
    assert accepted[0] is known


def test_hun_preferred_ranking():
    hun = cand("Show.S01E01.2160p.HUN.WEB-DL", h="f1" * 20)
    plain = cand("Show.S01E01.2160p.WEB-DL", h="f2" * 20)
    rule_obj = rule(language_mode=R.LANG_HUN_PREFERRED)
    accepted, _ = match_release_list([plain, hun], rule_obj, ctx(), {hun.info_hash, plain.info_hash}, now=NOW)
    assert accepted[0] is hun


def test_all_rejected_list_gives_reasons():
    rule_obj = rule(resolution_mode=R.RESOLUTION_2160_ONLY)
    low = cand("Show.S01E01.1080p.WEB-DL", h="0" * 40)
    accepted, rejected = match_release_list([low], rule_obj, ctx(), {low.info_hash}, now=NOW)
    assert not accepted
    assert len(rejected) == 1 and rejected[0][1]
