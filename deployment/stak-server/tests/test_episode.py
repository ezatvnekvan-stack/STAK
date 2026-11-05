"""Air-date / release-window logic."""

from datetime import date

from core.episode import EpisodeContext, episode_is_released, episode_wanted, parse_air_date, fallback_offered_at
from core.rules import SeriesIdentities, WatchRule


def ctx(air=None, season=1, episode=1, started=(2026, 1, 1)):
    return EpisodeContext(rule_id="r", season=season, episode=episode,
                          air_date=air, rule_started_on=date(*started))


def rule(**changes):
    base = dict(rule_id="r", series=SeriesIdentities(imdb="tt001"))
    base.update(changes)
    return WatchRule(**base)


def test_air_date_parsing_prefix_only():
    assert parse_air_date("2026-10-31T02:00:00Z") == date(2026, 10, 31)
    assert parse_air_date("2026-10-31") == date(2026, 10, 31)
    assert parse_air_date("") is None
    assert parse_air_date(None) is None
    assert parse_air_date("garbage") is None


def test_unknown_air_date_means_not_released():
    assert not episode_is_released(ctx(air=None))


def test_unknown_air_date_never_becomes_released():
    """No guessing: an episode without a known air date never passes release check."""
    assert not episode_is_released(ctx(air=None), today=date(2100, 1, 1))


def test_released_by_calendar_date():
    today = date(2026, 10, 31)
    assert episode_is_released(ctx(air=date(2026, 10, 30)), today)
    assert episode_is_released(ctx(air=date(2026, 10, 31)), today)  # airing today counts as released
    assert not episode_is_released(ctx(air=date(2026, 11, 1)), today)


def test_only_new_episodes_window():
    rule_obj = rule(only_new_episodes=True)
    today = date(2026, 12, 1)
    started = (2026, 6, 1)
    # Episode aired before enabling: skipped
    old = ctx(air=date(2026, 6, 1), started=started)
    assert not episode_wanted(rule_obj, old)
    # Episode aired after enabling: wanted
    new = ctx(air=date(2026, 7, 15), started=started)
    assert episode_wanted(rule_obj, new)
    assert not episode_is_released(new, date(2026, 6, 2))  # not yet aired at that moment
    assert episode_is_released(new, date(2026, 7, 16))


def test_include_missing_mode_counts_old_episodes():
    rule_new_only = rule(only_new_episodes=True)
    rule_all = rule()
    old = ctx(air=date(2025, 12, 3), started=(2026, 6, 1))
    assert not episode_wanted(rule_new_only, old)
    assert episode_wanted(rule_all, old)


def test_paused_rules_watched_nothing():
    rule_paused = rule(paused=True)
    assert not episode_wanted(rule_paused, ctx(air=date(2026, 10, 1)))


def test_fallback_offered_at():
    context = ctx(air=date(2026, 10, 30))
    stamp = fallback_offered_at(context, 24)
    import calendar
    assert stamp == calendar.timegm((2026, 10, 31, 0, 0, 0, 0, 0, 0))
