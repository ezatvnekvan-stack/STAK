"""Matching and ranking of release candidates against a watch rule.

Determines (1) which releases satisfy the rule's mandatory conditions and
(2) the preference order among those that do. Every rejection has an honest,
user-visible reason. Uncertain attributes are never treated as verified.

Cached-only is enforced here at the candidate level: a release candidate
without a verified TorBox-cached info hash is rejected with a clear reason
(the actual hash re-check happens again right before adding, per pipeline).
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field

from . import episode as episode_mod
from . import rules as rules_mod
from .traits import ReleaseTraits, parse_traits, CAM_TIER, BLURAY_TIER, REMUX_TIER, WEBDL_TIER, HD_TIER as WEBRIP_TIER

RES_2160 = 4
RES_1080 = 3


@dataclass(frozen=True)
class Candidate:
    """One searchable release candidate (already provider-verified fields)."""

    name: str                       # display/first line of release text
    release_text: str               # full combined text used for parsing
    size_bytes: int | None = None   # provider-reported per-video size
    info_hash: str | None = None
    source_identity: str = ""       # provider unique identity (dedup)
    provider: str = ""              # e.g. "torrentio", "torznab:<id>"
    traits: ReleaseTraits | None = None
    magnet: str | None = field(default=None,repr=False,compare=False)
    torrent_url: str | None = field(default=None,repr=False,compare=False)
    torrent_payload: bytes | None = field(default=None,repr=False,compare=False)

    def resolved(self) -> ReleaseTraits:
        return self.traits or parse_traits(self.release_text, self.size_bytes)


@dataclass(frozen=True)
class Verdict:
    """Accept() | Reject(reason): matching outcome for one candidate."""

    accepted: bool
    reason: str = ""

    @classmethod
    def accept(cls) -> "Verdict":
        return cls(True, "")

    @classmethod
    def reject(cls, reason: str) -> "Verdict":
        return cls(False, reason)


def _type_matched(traits: ReleaseTraits, types: tuple[str, ...]) -> str | None:
    """Return the matching release-type tag, or a reject reason."""
    if not types:
        return None  # no restriction
    if traits.tier == CAM_TIER or traits.tier == 1:
        return "release type unknown; unknown traits are never treated as verified"
    tier_to_type = {REMUX_TIER: rules_mod.TYPE_REMUX, BLURAY_TIER: rules_mod.TYPE_BLURAY,
                    WEBDL_TIER: rules_mod.TYPE_WEBDL, WEBRIP_TIER: rules_mod.TYPE_WEBRIP}
    release_type = tier_to_type.get(traits.tier)
    if release_type in types:
        return None
    return f"release type {release_type or 'unknown'} not in allowed set"


def _resolution_ceiling(traits: ReleaseTraits, rule, episode: episode_mod.EpisodeContext,                        now: float) -> str | None:
    """Check the resolution mode, including the fallback windowing."""
    mode = rule.resolution_mode
    upper, _lower = RES_2160, RES_1080
    if mode == rules_mod.RESOLUTION_2160_ONLY:
        if traits.resolution_rank != RES_2160:
            return f"resolution {traits.resolution.lower()} does not satisfy 4K-only"
        return None
    if mode == rules_mod.RESOLUTION_1080_ONLY:
        if traits.resolution_rank != RES_1080:
            return f"resolution {traits.resolution.lower()} does not satisfy 1080p-only"
        return None
    # 2160_preferred: 2160p always allowed; 1080p allowed after the wait window.
    if traits.resolution_rank == RES_2160:
        return None
    if traits.resolution_rank == RES_1080:
        if not episode_mod.episode_is_released(episode):
            return "not released yet"
        cutoff = episode_mod.fallback_offered_at(episode, rule.resolution_wait_hours)
        if now < cutoff:
            return "waiting for 4K-only window to pass before accepting 1080p"
        return None
    return f"resolution {traits.resolution.lower()} not allowed"


def _size_ok(traits: ReleaseTraits, rule) -> str | None:
    if rule.max_episode_gb is None:
        return None
    if traits.size_bytes is None:
        return "size unknown; per-episode size could not be verified"
    cap = int(rule.max_episode_gb * 1024 ** 3)
    if traits.size_bytes > cap:
        gb = traits.size_bytes / 1024 ** 3
        return f"size {gb:.2f} GB exceeds the {rule.max_episode_gb:.2f} GB cap"
    return None


def _trait_check(traits: ReleaseTraits, rule) -> str | None:
    """HDR / Dolby Vision / Atmos mandatory modes. Unknown == not verified."""
    if rule.hdr_mode == rules_mod.MODE_MANDATORY and not traits.hdr:
        return "HDR not verified on this release"
    if rule.dolby_vision_mode == rules_mod.MODE_MANDATORY and not traits.dolby_vision:
        return "Dolby Vision not verified on this release"
    if rule.atmos_mode == rules_mod.MODE_MANDATORY and not traits.atmos:
        return "Atmos not verified on this release"
    return None


def _language_check(traits: ReleaseTraits, rule) -> str | None:
    """Hungarian/original language policy.

    Hungarian can only be satisfied by an explicit AUDIO marker; MULTI is
    honest 'unknown' and can never satisfy a mandatory Hungarian condition.
    Original language can only be satisfied when no Hungarian audio marker
    exists AND the release also does not carry unprovable multi-audio flags;
    MULTI stays 'unknown' and therefore rejects under mandatory original.
    """
    mode = rule.language_mode
    if mode == rules_mod.LANG_ANY:
        return None
    if mode in (rules_mod.LANG_HUN_MANDATORY, rules_mod.LANG_HUN_PREFERRED):
        if traits.hungarian_audio:
            return None
        if traits.hungarian_subtitle:
            return "Hungarian subtitles found, but Hungarian audio is not verified"
        if traits.multi_audio:
            return "multi-audio release: Hungarian audio cannot be verified from the release"
        if mode == rules_mod.LANG_HUN_MANDATORY:
            return "Hungarian audio not verified on this release"
    else:  # LANG_ORIGINAL
        if traits.hungarian_audio:
            return "release has Hungarian audio instead of original"
        if traits.multi_audio:
            return "multi-audio release: original language cannot be verified from the release"
    return None


def _cache_check(candidate: Candidate, cached: set[str]) -> str | None:
    """Cached-only is mandatory and never optional."""
    if candidate.info_hash is None or candidate.info_hash.lower() not in cached:
        return "not confirmed TorBox-cached"
    return None


def identify(candidate: Candidate, episode: episode_mod.EpisodeContext) -> str | None:
    """Confirm the release actually contains THE searched episode.

    Episode-by-episode mode only: season packs are never auto-added, and an
    episode marker naming a different episode is rejected. A name without any
    episode marker is not provably the searched episode either.
    """
    name = candidate.release_text
    marker = episode_mod.parse_episode_marker(name)
    if marker is not None:
        if marker != (episode.season, episode.episode):
            return (f"release marker S{marker[0]:02d}E{marker[1]:02d} does not match "
                    f"the searched episode S{episode.season:02d}E{episode.episode:02d}")
        return None
    if episode_mod.is_season_pack(name, episode.season):
        return ("season pack detected; uncertain whether it contains the searched "
                "episode, so it is never auto-added")
    return "release has no episode marker; cannot prove it contains this episode"


def match_candidate(candidate: Candidate, rule, context: episode_mod.EpisodeContext,
                    cached_hashes: set[str], now: float | None = None) -> Verdict:
    """Full mandatory-condition match for one candidate."""
    traits = candidate.resolved()
    now = time.time() if now is None else now
    reasons = []
    if traits.tier == CAM_TIER:
        return Verdict.reject("damaged release (CAM/TS/SCR) rejected")
    if not episode_mod.episode_wanted(rule, context):
        return Verdict.reject("episode is not watched by the rule (season scope/paused/not released yet)")
    if not episode_mod.episode_is_released(context):
        return Verdict.reject("not released yet")
    blocker = identify(candidate, context) \
        or _resolution_ceiling(traits, rule, context, now) \
        or _type_matched(traits, rule.release_types) \
        or _trait_check(traits, rule) \
        or _language_check(traits, rule) \
        or _size_ok(traits, rule) \
        or _cache_check(candidate, cached_hashes)
    if blocker:
        return Verdict.reject(blocker)
    return Verdict.accept()


def preference_key(candidate: Candidate, rule) -> tuple:
    """Descending sort key among accepted candidates.

    Layers the app's quality order with rule preferences (preferred traits rank
    first among otherwise equal candidates):
      clean > resolution > tier > dynamic range (DV>HDR) > lossless > atmos >
      preference trades (preferred traits present) > Hungarian
    """
    traits = candidate.resolved()
    dynamic = 2 if traits.dolby_vision else (1 if traits.hdr else 0)
    preferred_bonus = (
        (1 if rule.hdr_mode == rules_mod.MODE_PREFERRED and traits.hdr else 0) * 4
        + (1 if rule.dolby_vision_mode == rules_mod.MODE_PREFERRED and traits.dolby_vision else 0) * 4
        + (1 if rule.atmos_mode == rules_mod.MODE_PREFERRED and traits.atmos else 0) * 2
    )
    hungarian = 0
    if rule.language_mode == rules_mod.LANG_HUN_PREFERRED:
        hungarian = 3 if traits.hungarian_audio else (2 if traits.multi_audio else 0)
    elif rule.language_mode == rules_mod.LANG_ORIGINAL:
        hungarian = 1 if traits.english_audio else 0
    return (
        1 if traits.clean else 0,
        traits.resolution_rank,
        traits.tier,
        dynamic,
        1 if traits.lossless_audio else 0,
        1 if traits.atmos else 0,
        preferred_bonus,
        hungarian,
        traits.size_bytes or 0,
    )


def match_release_list(candidates: list[Candidate], rule, context: episode_mod.EpisodeContext,
                       cached_hashes: set[str], now: float | None = None
                       ) -> tuple[list[Candidate], list[tuple[Candidate, str]]]:
    """Filter + rank: returns (accepted_sorted, rejected_with_reasons)."""
    accepted: list[Candidate] = []
    rejected: list[tuple[Candidate, str]] = []
    now = time.time() if now is None else now
    for candidate in candidates:
        verdict = match_candidate(candidate, rule, context, cached_hashes, now)
        if verdict.accepted:
            accepted.append(candidate)
        else:
            rejected.append((candidate, verdict.reason))
    accepted.sort(key=lambda c: preference_key(c, rule), reverse=True)
    return accepted, rejected
