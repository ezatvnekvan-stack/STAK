"""Episode identity, released-window logic and matching of rule -> episode."""

from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import date, datetime, timezone


@dataclass(frozen=True)
class EpisodeIdentity:
    """Stable, persistent identity of a single episode of one series."""

    rule_id: str
    season: int
    episode: int

    @property
    def key(self) -> str:
        return f"{self.rule_id}:S{self.season:02d}E{self.episode:03d}"


# Port of SourceRepository.seasonPackMatches, extended quickly enough to also
# catch "S xx E yy" and "1x01" episode markers inside packs.
_EPISODE_MARKER = re.compile(r"(?i)\bS\d{1,2}E\d{1,3}\b|\b\d{1,2}x\d{2}\b")
_CHANNEL = re.compile(r"[. _]")


def parse_episode_marker(name: str) -> tuple[int, int] | None:
    """Extract (season, episode) from a release name, or None."""
    match = re.search(r"(?i)\bS(\d{1,2})E(\d{1,3})\b", name)
    if match:
        return int(match.group(1)), int(match.group(2))
    match = re.search(r"(?i)\b(\d{1,2})x(\d{2})\b", name)
    if match:
        return int(match.group(1)), int(match.group(2))
    return None


def is_season_pack(name: str, season: int) -> bool:
    """True when a release name is a season pack for the season (never
    a specific episode). Uncertain pack names also return True."""
    flat = _CHANNEL.sub(" ", name)
    if _EPISODE_MARKER.search(name):
        return False
    return bool(re.search(rf"(?i)\bS0*{season}\b|\bSeason[ ._-]*0*{season}\b", flat))


def parse_air_date(raw: str | None) -> date | None:
    """Parse an ISO air/air_date prefix into a date. No timezone games: the
    app treats air dates as plain calendar dates (TMDb/Cinemeta give dates)."""
    if not raw:
        return None
    head = raw[:10]
    try:
        return date.fromisoformat(head)
    except ValueError:
        return None


@dataclass(frozen=True)
class EpisodeContext:
    rule_id: str
    season: int
    episode: int
    air_date: date | None
    rule_started_on: date  # when only_new_episodes applies
    enabled: bool = True


def episode_is_released(context: EpisodeContext, today: date | None = None) -> bool:
    """Step 1 of the add pipeline: the episode aired and matches the rule."""
    if context.air_date is None:
        return False  # unknown air date is not "released" (no guessing)
    return context.air_date <= (today or datetime.now(timezone.utc).date())


def episode_wanted(rule, context: EpisodeContext) -> bool:
    """Rule settings decide whether this season/episode is watched at all.

    Specials default to unwatched unless include_specials is set.
    only_new_episodes skips episodes that aired on or before the rule start
    date (they are 'already missing before enabling').
    """
    if not context.enabled or not rule.enabled or rule.paused:
        return False
    if not rule.season_allowed(context.season):
        return False
    if rule.only_new_episodes and context.air_date is not None and context.air_date <= context.rule_started_on:
        return False
    return True


def fallback_offered_at(context: EpisodeContext, wait_hours: int) -> datetime:
    """When a release may first fall back to lower resolution (2160_preferred)."""
    air = context.air_date or date(1970, 1, 1)
    return datetime(air.year, air.month, air.day, tzinfo=timezone.utc) \
        .timestamp() + wait_hours * 3600
