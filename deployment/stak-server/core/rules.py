"""Watch-rule model, validation and contradiction feedback.

A rule belongs to exactly one series and states which release attributes an
episode must have. Three strengths exist per attribute:

- mandatory: releases without the verified trait are removed ("filter out")
- preferred: releases without the trait stay allowed but rank lower
- any: no effect (rank neutral)

Cached-only is a hard, non-negotiable property enforced in the add pipeline.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

RESOLUTION_2160_ONLY = "2160_only"
RESOLUTION_1080_ONLY = "1080_only"
RESOLUTION_2160_PREFERRED = "2160_preferred"
RESOLUTION_MODES = (RESOLUTION_2160_ONLY, RESOLUTION_1080_ONLY, RESOLUTION_2160_PREFERRED)

MODE_MANDATORY = "mandatory"
MODE_PREFERRED = "preferred"
MODE_ANY = "any"
TRAIT_MODES = (MODE_MANDATORY, MODE_PREFERRED, MODE_ANY)

# Allowed release families a user may select. Unknown tiers can never satisfy
# a non-empty type restriction (unknown is not verified).
TYPE_WEBDL = "WEB-DL"
TYPE_WEBRIP = "WEBRip"
TYPE_BLURAY = "BluRay"
TYPE_REMUX = "Remux"
RELEASE_TYPES = (TYPE_WEBDL, TYPE_WEBRIP, TYPE_BLURAY, TYPE_REMUX)
_TYPE_TIER = {TYPE_REMUX: 5, TYPE_BLURAY: 4, TYPE_WEBDL: 3, TYPE_WEBRIP: 2}

LANG_ANY = "any"
LANG_HUN_MANDATORY = "hun_mandatory"
LANG_HUN_PREFERRED = "hun_preferred"
LANG_ORIGINAL = "original"
LANGUAGE_MODES = (LANG_ANY, LANG_HUN_MANDATORY, LANG_HUN_PREFERRED, LANG_ORIGINAL)

DEFAULT_CHECK_MINUTES = 45
MIN_CHECK_MINUTES = 15
MAX_CHECK_MINUTES = 720
DEFAULT_RESOLUTION_WAIT_HOURS = 24
MIN_WAIT_HOURS = 0
MAX_WAIT_HOURS = 14 * 24


@dataclass(frozen=True)
class SeriesIdentities:
    """Stable provider identities of a series. At least one must be set."""

    imdb: str | None = None
    tmdb: int | None = None
    tvdb: int | None = None
    jellyfin: str | None = None
    name: str = ""

    def key(self) -> str:
        """Stable, persistent series key used for dedup across restarts."""
        if self.tmdb:
            return f"tmdb:{self.tmdb}"
        if self.imdb:
            return f"imdb:{self.imdb.lower()}"
        if self.tvdb:
            return f"tvdb:{self.tvdb}"
        return f"jellyfin:{self.jellyfin}"


def _trait_mode(value: Any, default: str) -> str:
    if value is None:
        return default
    if value in TRAIT_MODES:
        return value
    raise ValueError(f"invalid trait mode: {value!r}")


@dataclass(frozen=True)
class WatchRule:
    """Everything the watcher needs for one series. Immutable by design."""

    rule_id: str
    series: SeriesIdentities
    enabled: bool = True
    paused: bool = False
    created_on: str | None = None  # ISO date when the rule was enabled (only-new window)

    # Episode scope
    watched_seasons: tuple[int, ...] = ()  # empty tuple => default: seasons 1+
    include_specials: bool = False  # specials default unwatched
    only_new_episodes: bool = False  # ignore episodes that already aired before enabling

    # Release conditions
    resolution_mode: str = RESOLUTION_2160_PREFERRED
    resolution_wait_hours: int = DEFAULT_RESOLUTION_WAIT_HOURS  # used by 2160_preferred only
    release_types: tuple[str, ...] = RELEASE_TYPES  # empty => no type restriction
    hdr_mode: str = MODE_ANY
    dolby_vision_mode: str = MODE_ANY
    atmos_mode: str = MODE_ANY
    language_mode: str = LANG_ANY
    max_episode_gb: float | None = None

    # Scheduling
    check_minutes: int = DEFAULT_CHECK_MINUTES

    # Requested state badge: distinguish locally saved vs server-confirmed
    confirmed: bool = False

    @staticmethod
    def resolution_mode_of(value: Any) -> str:
        if value in RESOLUTION_MODES:
            return value
        raise ValueError(f"invalid resolution mode: {value!r}")

    @staticmethod
    def language_mode_of(value: Any) -> str:
        if value in LANGUAGE_MODES:
            return value
        raise ValueError(f"invalid language mode: {value!r}")

    def season_allowed(self, season: int) -> bool:
        if season == 0:
            return self.include_specials
        if not self.watched_seasons:
            return True
        return season in self.watched_seasons


def validate_type_list(types: tuple[str, ...]) -> tuple[str, ...]:
    for value in types:
        if value not in RELEASE_TYPES:
            raise ValueError(f"invalid release type: {value!r}")
    seen: list[str] = []
    for value in RELEASE_TYPES:
        if value in types:
            seen.append(value)
    return tuple(seen)


def check_check_minutes(minutes: int) -> int:
    if not isinstance(minutes, int) or not MIN_CHECK_MINUTES <= minutes <= MAX_CHECK_MINUTES:
        raise ValueError(f"check_minutes must be {MIN_CHECK_MINUTES}..{MAX_CHECK_MINUTES}")
    return minutes


def check_wait_hours(hours: int) -> int:
    if not isinstance(hours, int) or not MIN_WAIT_HOURS <= hours <= MAX_WAIT_HOURS:
        raise ValueError(f"resolution_wait_hours must be {MIN_WAIT_HOURS}..{MAX_WAIT_HOURS}")
    return hours


@dataclass(frozen=True)
class RuleIssue:
    """A human-readable condition problem shown in the UI before saving."""

    code: str
    message: str


def warn_implausible(rule: WatchRule) -> tuple[RuleIssue, ...]:
    """Detect contradictory or practically impossible conditions.

    These are reported as clear feedback to the user; the rule may still be
    saved when the combination is merely very restrictive.
    """
    issues: list[RuleIssue] = []
    if rule.resolution_mode == RESOLUTION_2160_ONLY and rule.max_episode_gb is not None:
        if rule.max_episode_gb < 3.0:
            issues.append(RuleIssue(
                "size_cap_small_for_4k",
                "4K episodes are rarely smaller than 3 GB. With this size cap "
                "episodes may never match; raise the cap or allow 1080p fallback.",
            ))
    if rule.resolution_mode in (RESOLUTION_2160_ONLY, RESOLUTION_2160_PREFERRED) and \
            rule.release_types == (TYPE_WEBRIP,):
        issues.append(RuleIssue(
            "four_k_webrip",
            "Only WEBRip with 4K is an unusual combination; most 4K releases are "
            "WEB-DL or Remux, so waits may be long. Consider adding WEB-DL.",
        ))
    if rule.hdr_mode == MODE_MANDATORY and rule.dolby_vision_mode == MODE_MANDATORY:
        issues.append(RuleIssue(
            "hdr_and_dv",
            "Requiring both HDR and Dolby Vision rejects most releases, because "
            "a release usually tags one dynamic-range family, not both.",
        ))
    if rule.language_mode == LANG_HUN_MANDATORY and rule.atmos_mode == MODE_MANDATORY:
        issues.append(RuleIssue(
            "hun_atmos_rare",
            "Hungarian dub with Atmos is rare: Hungarian dubs ship in stereo, so "
            "episodes may wait a very long time.",
        ))
    if rule.atmos_mode == MODE_MANDATORY and rule.max_episode_gb is not None and rule.max_episode_gb < 2.0:
        issues.append(RuleIssue(
            "atmos_size",
            "Atmos material is larger than 2 GB per episode at any resolution in "
            "practice.",
        ))
    return tuple(issues)


def rule_from_dict(data: dict) -> WatchRule:
    """Parse a rule from JSON (API/backup shape). Unknown keys are ignored so
    adding optional fields stays compatible."""
    if not isinstance(data, dict):
        raise ValueError("rule must be an object")
    import math
    for flag in ('enabled','paused','includeSpecials','onlyNewEpisodes','confirmed'):
        if flag in data and not isinstance(data[flag],bool):raise ValueError('Invalid boolean setting: '+flag)
    if data.get('maxEpisodeGb') is not None:
        size=float(data['maxEpisodeGb'])
        if not math.isfinite(size) or not 0<size<=500:raise ValueError('Episode size must be greater than zero and at most 500 GB')
    seasons=data.get('watchedSeasons') or []
    if not isinstance(seasons,list) or len(seasons)>201 or any(isinstance(v,bool) or not isinstance(v,int) or not 0<=v<=200 for v in seasons):
        raise ValueError('Invalid season selection')
    rule_id = str(data.get("ruleId") or data.get("rule_id") or "")
    if not rule_id:
        raise ValueError("ruleId is required")
    series_raw = data.get("series")
    if not isinstance(series_raw, dict):
        raise ValueError("series is required")
    series = SeriesIdentities(
        imdb=(series_raw.get("imdb") or None),
        tmdb=(series_raw.get("tmdb") or None),
        tvdb=(series_raw.get("tvdb") or None),
        jellyfin=(series_raw.get("jellyfin") or None),
        name=str(series_raw.get("name") or ""),
    )
    if series.tmdb is not None:
        try:
            series = SeriesIdentities(imdb=series.imdb, tmdb=int(series.tmdb), tvdb=series.tvdb,
                                      jellyfin=series.jellyfin, name=series.name)
        except (TypeError, ValueError):
            raise ValueError(f"tmdb must be an integer: {series.tmdb!r}")
    if series.tvdb is not None:
        try:
            series = SeriesIdentities(imdb=series.imdb, tmdb=series.tmdb, tvdb=int(series.tvdb),
                                      jellyfin=series.jellyfin, name=series.name)
        except (TypeError, ValueError):
            raise ValueError(f"tvdb must be an integer: {series.tvdb!r}")
    if not (series.imdb or series.tmdb or series.tvdb or series.jellyfin):
        raise ValueError("series needs at least one provider identity")
    resolution = WatchRule.resolution_mode_of(data.get("resolutionMode") or RESOLUTION_2160_PREFERRED)
    if resolution == RESOLUTION_2160_ONLY:
        wait = 0
    else:
        wait = check_wait_hours(int(data.get("resolutionWaitHours") or DEFAULT_RESOLUTION_WAIT_HOURS))
    types = validate_type_list(tuple(data.get("releaseTypes") or RELEASE_TYPES))
    seasons = tuple(int(s) for s in (data.get("watchedSeasons") or []) if int(s) > 0)
    return WatchRule(
        rule_id=rule_id,
        series=series,
        enabled=bool(data.get("enabled", True)),
        paused=bool(data.get("paused", False)),
        created_on=str(data["createdOn"]) if data.get("createdOn") else None,
        watched_seasons=seasons,
        include_specials=bool(data.get("includeSpecials", False)),
        only_new_episodes=bool(data.get("onlyNewEpisodes", False)),
        resolution_mode=resolution,
        resolution_wait_hours=wait,
        release_types=types,
        hdr_mode=_trait_mode(data.get("hdrMode"), MODE_ANY),
        dolby_vision_mode=_trait_mode(data.get("dvMode"), MODE_ANY),
        atmos_mode=_trait_mode(data.get("atmosMode"), MODE_ANY),
        language_mode=WatchRule.language_mode_of(data.get("languageMode") or LANG_ANY),
        max_episode_gb=float(data["maxEpisodeGb"]) if data.get("maxEpisodeGb") is not None else None,
        check_minutes=check_check_minutes(int(data.get("checkMinutes") or DEFAULT_CHECK_MINUTES)),
        confirmed=bool(data.get("confirmed", False)),
    )


def rule_to_dict(rule: WatchRule) -> dict:
    return {
        "ruleId": rule.rule_id,
        "series": {
            "imdb": rule.series.imdb,
            "tmdb": rule.series.tmdb,
            "tvdb": rule.series.tvdb,
            "jellyfin": rule.series.jellyfin,
            "name": rule.series.name,
        },
        "enabled": rule.enabled,
        "paused": rule.paused,
        "createdOn": rule.created_on,
        "watchedSeasons": list(rule.watched_seasons),
        "includeSpecials": rule.include_specials,
        "onlyNewEpisodes": rule.only_new_episodes,
        "resolutionMode": rule.resolution_mode,
        "resolutionWaitHours": rule.resolution_wait_hours,
        "releaseTypes": list(rule.release_types),
        "hdrMode": rule.hdr_mode,
        "dvMode": rule.dolby_vision_mode,
        "atmosMode": rule.atmos_mode,
        "languageMode": rule.language_mode,
        "maxEpisodeGb": rule.max_episode_gb,
        "checkMinutes": rule.check_minutes,
        "confirmed": rule.confirmed,
    }
