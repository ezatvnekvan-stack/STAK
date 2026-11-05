"""Release-attribute extraction for the STAK watcher.

Faithful port of the semantics of the Android app's `parseSourceRelease` /
`sourceTier` / `bestQualityOrder` (app/src/main/java/app/stak/nativeapp/data/
SourceRepository.kt and SourceQuality.kt), adjusted for automation honesty:

- A trait is only ever TRUE when an explicit marker exists in the release text.
- Unknown traits stay unknown; the matching layer never treats them as verified.
- Hungarian SUBTITLE markers (HunSub / Sub-Hun variants) are explicitly excluded
  from Hungarian AUDIO detection, because filename markers alone cannot prove a
  dub, and subtitle releases are common (see the brief: never claim Hungarian
  audio or Atmos from an uncertain filename alone).
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field

UNKNOWN_TIER = 1
CAM_TIER = -1
SD_TIER = 1
HD_TIER = 2
WEBDL_TIER = 3
BLURAY_TIER = 4
REMUX_TIER = 5

RESOLUTION_2160 = 4
RESOLUTION_1080 = 3
RESOLUTION_720 = 2
RESOLUTION_480 = 1
RESOLUTION_NONE = 0

# Ported patterns (case-insensitive, byte-regular).
_DAMAGED = re.compile(r"(?i)\b(?:CAM|HDCAM|HDTS|TELESYNC|TELECINE|SCR|SCREENER|TS)\b")
_REMUX = re.compile(r"(?i)\bREMUX\b")
_DISC = re.compile(r"(?i)BLU[ -]?RAY|BDRIP|BDMV|BDREMUX")
_WEBDL = re.compile(r"(?i)WEB[ -]?DL|\bWEB\b")
_BROADCAST = re.compile(r"(?i)WEBRIP|HDTV")
_LOSSLESS = re.compile(r"(?i)TRUE[ .-]?HD|DTS[ .-]?(?:HD|X)|\bFLAC\b|\bPCM\b")
_HDR = re.compile(r"(?i)\bHDR(?:10(?:\+|Plus|P)|10\+|\+)?\b")
_DOLBY_VISION = re.compile(r"(?i)\b(?:DV|DOVI|Dolby[ .-]?Vision)\b")
_ATMOS = re.compile(r"(?i)\bAtmos\b")
_RESOLUTION_2160 = re.compile(r"(?i)2160p|\b4K\b")
_RESOLUTION_1080 = re.compile(r"(?i)1080p")
_RESOLUTION_720 = re.compile(r"(?i)720p")
_RESOLUTION_480 = re.compile(r"(?i)480p")
_SIZE = re.compile(r"(?i)([0-9]+(?:[.,][0-9]+)?)\s*(TB|TiB|GB|GiB|MB|MiB)")
_SEEDERS = re.compile(r"(?:👤|👥|[Ss]eeders?:?)\s*(\d+)")
_INDEXER = re.compile(r"(?:⚙️?|Tracker:|Indexer:)\s*([^\n|]+)")
_UNICODE_2160 = re.compile(r"(?i)(2160|1080|720|480)p")

# Language markers. Audio "HUN" must not match subtitle markers.
_LANG_HUN_AUDIO = re.compile(r"(?i)(?:\bHUN\b|Hungarian|Magyar|🇭🇺|HunDub|\bDUB[ .-]*HUN\b|\bHUN[ .-]*DUB\b)")
_LANG_HUN_SUB = re.compile(r"(?i)\bSubs?[ .-]*Hun\b|\bHun[ .-]*Subs?\b|\bSUB\.HUN\b|\bHUN\.SUB\b|\bHUNSUB\b|\bHUSUB\b")
_LANG_ENG = re.compile(r"(?i)(?:\bENG\b|English|🇬🇧|🇺🇸|\bEN\b)")
_LANG_MULTI = re.compile(r"(?i)\bMULTI\b")

_SIZE_FACTORS = {
    "TB": 1099511627776,
    "TIB": 1099511627776,
    "GB": 1073741824,
    "GIB": 1073741824,
    "MB": 1048576,
    "MIB": 1048576,
}


@dataclass(frozen=True)
class ReleaseTraits:
    """Parsed, verifiable attributes of one release candidate."""

    resolution_rank: int = RESOLUTION_NONE  # 4=2160p 3=1080p 2=720p 1=480p 0=unknown
    tier: int = UNKNOWN_TIER  # -1 damaged, 2 broadcast, 3 webdl, 4 disc, 5 remux
    clean: bool = True
    hdr: bool = False
    dolby_vision: bool = False
    atmos: bool = False
    lossless_audio: bool = False
    hungarian_audio: bool = False
    hungarian_subtitle: bool = False
    english_audio: bool = False
    multi_audio: bool = False
    size_bytes: int | None = None

    @property
    def resolution(self) -> str:
        return {
            RESOLUTION_2160: "2160p",
            RESOLUTION_1080: "1080p",
            RESOLUTION_720: "720p",
            RESOLUTION_480: "480p",
        }.get(self.resolution_rank, "Unknown")


def parse_traits(text: str, size_bytes: int | None = None) -> ReleaseTraits:
    """Parse release attributes from combined release text (name+description).

    `size_bytes` (optional explicit provider data, e.g. TorBox videoSize or
    behaviorHints.videoSize) overrides a text-derived size only when positive.
    """
    combined = text or ""
    clean = not bool(_DAMAGED.search(combined))
    tier = UNKNOWN_TIER
    if not clean:
        tier = CAM_TIER
    elif _REMUX.search(combined):
        tier = REMUX_TIER
    elif _DISC.search(combined):
        tier = BLURAY_TIER
    elif _WEBDL.search(combined):
        tier = WEBDL_TIER
    elif _BROADCAST.search(combined):
        tier = HD_TIER

    if _RESOLUTION_2160.search(combined):
        rank = RESOLUTION_2160
    elif _RESOLUTION_1080.search(combined):
        rank = RESOLUTION_1080
    elif _RESOLUTION_720.search(combined):
        rank = RESOLUTION_720
    elif _RESOLUTION_480.search(combined):
        rank = RESOLUTION_480
    else:
        rank = RESOLUTION_NONE

    size = size_bytes
    if size is None:
        match = _SIZE.search(combined)
        if match:
            size = round(float(match.group(1).replace(",", ".")) * _SIZE_FACTORS[match.group(2).upper()])

    hun_sub = bool(_LANG_HUN_SUB.search(combined))
    hun_audio = bool(_LANG_HUN_AUDIO.search(combined)) and not hun_sub
    return ReleaseTraits(
        resolution_rank=rank,
        tier=tier,
        clean=clean,
        hdr=bool(_HDR.search(combined)),
        dolby_vision=bool(_DOLBY_VISION.search(combined)),
        atmos=bool(_ATMOS.search(combined)),
        lossless_audio=bool(_LOSSLESS.search(combined)),
        hungarian_audio=hun_audio,
        hungarian_subtitle=hun_sub,
        english_audio=bool(_LANG_ENG.search(combined)),
        multi_audio=bool(_LANG_MULTI.search(combined)),
        size_bytes=size,
    )


def atmosphere_summary(traits: ReleaseTraits) -> list[str]:
    """Human-readable attribute summary; only verified traits are listed."""
    parts: list[str] = []
    if traits.resolution_rank:
        parts.append(traits.resolution)
    if traits.tier == REMUX_TIER:
        parts.append("Remux")
    elif traits.tier == BLURAY_TIER:
        parts.append("Blu-ray")
    elif traits.tier == WEBDL_TIER:
        parts.append("WEB-DL")
    if traits.dolby_vision:
        parts.append("Dolby Vision")
    elif traits.hdr:
        parts.append("HDR")
    if traits.lossless_audio and traits.atmos:
        parts.append("Lossless Atmos")
    elif traits.lossless_audio:
        parts.append("Lossless audio")
    elif traits.atmos:
        parts.append("Atmos")
    return parts
