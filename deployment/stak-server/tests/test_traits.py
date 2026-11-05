"""Parsing traits from release text / names."""

import pytest

from core.traits import (
    ReleaseTraits,
    parse_traits,
    RESOLUTION_2160,
    RESOLUTION_1080,
    RESOLUTION_480,
    RESOLUTION_NONE,
    BLURAY_TIER,
    HD_TIER,
    REMUX_TIER,
    WEBDL_TIER,
    CAM_TIER,
)


def parse(name: str, size_bytes: int | None = None) -> ReleaseTraits:
    return parse_traits(name, size_bytes)


def test_resolution_ranks():
    assert parse("Show.S01E01.2160p.WEB-DL.HDR").resolution_rank == RESOLUTION_2160
    assert parse("Show.S01E01.4K.WEB-DL").resolution_rank == RESOLUTION_2160  # 4K alias
    assert parse("Show.S01E01.1080p.WEB-DL").resolution_rank == RESOLUTION_1080
    assert parse("Show.S01E01.480p.HDTV").resolution_rank == RESOLUTION_480
    assert parse("Show.S01E01").resolution_rank == RESOLUTION_NONE


def test_tiers_and_damaged():
    assert parse("Show.S01E01.2160p.Remux.HDR").tier == REMUX_TIER
    assert parse("Show.S01E01.1080p.BluRay.x264").tier == BLURAY_TIER
    assert parse("Show.S01E01.1080p.WEB-DL.DDP5.1").tier == WEBDL_TIER
    assert parse("Show.S01E01.1080p.WEBRip.x264").tier == HD_TIER
    assert parse("Show.S01E01.CAM.x264").tier == CAM_TIER
    assert parse("Show.S01E01.TS").tier == CAM_TIER
    assert not parse("Show.S01E01.CAM").clean
    assert parse("Show.S01E01.1080p.WEBRip").clean


def test_trait_flags():
    traits = parse("Show.S01E01.2160p.WEB-DL.DTS-HD.7.1.Atmos.HDR10Plus.DV")
    assert traits.hdr
    assert traits.dolby_vision
    assert traits.atmos
    assert traits.lossless_audio

    plain = parse("Show.S01E01.1080p.WEB-DL.DDP5.1")
    assert not plain.hdr and not plain.dolby_vision and not plain.atmos


def test_unknown_traits_stay_unknown():
    """No marker -> trait never counted as verified (brief rule)."""
    traits = parse("Show.S01E01.2160p.WEB-DL.x264")
    assert not traits.hdr
    assert not traits.dolby_vision
    assert not traits.atmos
    assert not traits.hungarian_audio
    assert not traits.lossless_audio


def test_hungarian_audio_and_subtitle_distinction():
    dub = parse("Show.S01E01.HUN.WEB-DL")
    assert dub.hungarian_audio
    assert not dub.hungarian_subtitle

    german_dub = parse("Show.S01E01.DUB.HUN.WEB-DL")
    assert german_dub.hungarian_audio

    magyar = parse("Show.S01E01.Magyar.WEB-DL")
    assert magyar.hungarian_audio

    subs_only = parse("Show.S01E01.1080p.WEB-DL.HunSub")
    assert not subs_only.hungarian_audio
    assert subs_only.hungarian_subtitle

    subs_only_2 = parse("Show.S01E01.1080p.WEB-DL.SUB.HUN")
    assert not subs_only_2.hungarian_audio
    assert subs_only_2.hungarian_subtitle


def test_sub_marker_does_not_leak_into_words():
    """HunSub only for subtitle words, not random prefixes like Hundstag."""
    traits = parse("Show.S01E01.Hundstag.WEB-DL")
    assert not traits.hungarian_audio


def test_multi_audio_detected():
    traits = parse("Show.S01E01.2160p.MULTI.DV.WEB-DL")
    assert traits.multi_audio
    assert not traits.hungarian_audio  # MULTI is unknown, never verified audio
    assert traits.dolby_vision


def test_english_marker():
    traits = parse("Show.S01E01.1080p.WEB-DL.English")
    assert traits.english_audio


def test_size_from_text_and_explicit_provider_size():
    assert parse("Show.S01E01 3.5 GB").size_bytes == int(3.5 * 1024 ** 3)
    assert parse("Show.S01E01 800MB").size_bytes == 800 * 1024 ** 2
    assert parse("Show.S01E01 2,5GB").size_bytes == int(2.5 * 1024 ** 3)
    # explicit provider size wins
    assert parse("Show.S01E01 800MB", size_bytes=12345).size_bytes == 12345
    assert parse("Show.S01E01", size_bytes=None).size_bytes is None
