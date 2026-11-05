"""Rule model, validation and contradictory-condition feedback."""

import pytest

from core.rules import (
    WatchRule,
    SeriesIdentities,
    validate_type_list,
    rule_from_dict,
    rule_to_dict,
    warn_implausible,
    check_check_minutes,
    check_wait_hours,
    RESOLUTION_2160_ONLY,
    RESOLUTION_1080_ONLY,
    RESOLUTION_2160_PREFERRED,
)


def make_rule(**changes) -> WatchRule:
    base = dict(
        rule_id="r1",
        series=SeriesIdentities(imdb="tt001", name="Show"),
    )
    base.update(changes)
    return WatchRule(**base)


def test_season_scope_defaults():
    rule = make_rule()
    assert rule.season_allowed(1)
    assert rule.season_allowed(5)
    assert not rule.season_allowed(0)  # specials default unwatched
    rule = make_rule(include_specials=True)
    assert rule.season_allowed(0)
    rule = make_rule(watched_seasons=(2,))
    assert not rule.season_allowed(1)
    assert rule.season_allowed(2)


def test_invalid_modes_rejected():
    with pytest.raises(ValueError):
        WatchRule.resolution_mode_of("lower_q_bitrate")
    with pytest.raises(ValueError):
        WatchRule.language_mode_of("turkish_mandatory")
    with pytest.raises(ValueError):
        validate_type_list(("CAM",))
    with pytest.raises(ValueError):
        check_check_minutes(0)
    with pytest.raises(ValueError):
        check_wait_hours(-1)


def test_series_key_prefers_stable_provider_ids():
    assert SeriesIdentities(imdb="tt001", tmdb=77).key() == "tmdb:77"
    assert SeriesIdentities(imdb="tt001").key() == "imdb:tt001"
    assert SeriesIdentities(tvdb=9).key() == "tvdb:9"
    assert SeriesIdentities(jellyfin="abc").key() == "jellyfin:abc"


def test_rule_requires_provider_identity():
    with pytest.raises(ValueError):
        rule_from_dict({"ruleId": "r", "series": {"name": "Show"}})


def test_round_trip_dict():
    rule = make_rule(watched_seasons=(1, 2), hdr_mode="mandatory", language_mode="hun_preferred",
                     max_episode_gb=12.5, release_types=("WEB-DL", "Remux"), check_minutes=30)
    data = rule_to_dict(rule)
    again = rule_from_dict(data)
    assert again == rule


def test_unknown_dict_fields_ignored():
    data = {"ruleId": "r", "series": {"imdb": "tt001"}, "futureField": 123}
    rule = rule_from_dict(data)
    assert rule.rule_id == "r"


def test_resolution_wait_hours_validation():
    with pytest.raises(ValueError):
        rule_from_dict({"ruleId": "r", "series": {"imdb": "tt001"}, "resolutionWaitHours": 500})
    strict = rule_from_dict({"ruleId": "r", "series": {"imdb": "tt001"},
                             "resolutionMode": RESOLUTION_2160_ONLY, "resolutionWaitHours": 999})
    assert strict.resolution_wait_hours == 0  # strict 4K never waits for fallback


# --- contradiction / implausibility feedback --------------------------------

def test_four_k_with_tiny_size_cap_warns():
    rule = make_rule(resolution_mode=RESOLUTION_2160_ONLY, max_episode_gb=1.0)
    issues = warn_implausible(rule)
    assert any(i.code == "size_cap_small_for_4k" for i in issues)
    assert "3 GB" in issues[0].message or "1080p" in issues[0].message


def test_hdr_and_dv_both_mandatory_warns():
    rule = make_rule(hdr_mode="mandatory", dolby_vision_mode="mandatory")
    issues = warn_implausible(rule)
    assert any(i.code == "hdr_and_dv" for i in issues)
    assert all("Encode" not in i.message for i in issues)


def test_hun_atmos_rare_warns():
    rule = make_rule(language_mode="hun_mandatory", atmos_mode="mandatory")
    assert any(i.code == "hun_atmos_rare" for i in warn_implausible(rule))


def test_normal_rule_has_no_warnings():
    assert warn_implausible(make_rule()) == ()
    assert warn_implausible(make_rule(resolution_mode=RESOLUTION_2160_PREFERRED,
                                      max_episode_gb=15)) == ()
