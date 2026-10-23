package app.stak.nativeapp

import org.junit.Assert.*
import org.junit.Test

class SourceBadgeTest {
    @Test fun exposesOnlyTheRequestedBadgesAndGroupsHdrVariants() {
        assertEquals(SourceFeature.entries.toSet() - SourceFeature.FullHd,
            sourceFeatures(qualityTags("Title.2160p.HDR10+.WEB-DL.REMUX.TrueHD.Atmos.HEVC.AAC")))
        assertTrue(SourceFeature.Hdr in sourceFeatures(qualityTags("Title.1080p.Dolby.Vision")))
        assertTrue(SourceFeature.Hdr in sourceFeatures(qualityTags("Title.1080p.HDR10")))
    }

    @Test fun onlyFourKAndFullHdPassEvenWhenFiltersAreCleared() {
        listOf("720p", "480p", "8K", "unknown").forEach { value ->
            assertFalse(value, matchesSourceFeatures(sourceFeatures(qualityTags(value)), null, emptySet()))
        }
        assertTrue(matchesSourceFeatures(sourceFeatures(qualityTags("2160p")), null, emptySet()))
        assertTrue(matchesSourceFeatures(sourceFeatures(qualityTags("1080p")), null, emptySet()))
    }

    @Test fun resolutionAndFormatFiltersCombineWithoutChangingProviderFacts() {
        val features = sourceFeatures(qualityTags("Show.S01E01.2160p.HDR.WEB-DL.Atmos"))
        assertTrue(matchesSourceFeatures(features, SourceFeature.UltraHd, setOf(SourceFeature.Hdr, SourceFeature.Atmos)))
        assertFalse(matchesSourceFeatures(features, SourceFeature.FullHd, emptySet()))
        assertFalse(matchesSourceFeatures(features, null, setOf(SourceFeature.Remux)))
    }
}
