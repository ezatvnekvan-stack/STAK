package app.stak.nativeapp

import org.json.*
import org.junit.Assert.*
import org.junit.Test

class SourcePresentationTest {
    @Test
    fun seasonPacksMatchTheSelectedSeasonAndExcludeSingleEpisodes() {
        listOf(
            Triple("Show.S01.Complete", 1, true), Triple("Show.S01-S03", 2, true),
            Triple("Show.S01-S03", 4, false), Triple("Show.S02E01-E10", 1, false),
            Triple("Show.S02E01-E10", 2, true), Triple("Show.2x01-10", 2, true),
            Triple("Show.S01E01", 1, false), Triple("Show.Season.2", 2, true),
            Triple("Show.2.évad", 2, false),
        ).forEach { (release, season, expected) ->
            assertEquals("$release for season $season", expected,
                SourceStream("Torrentio", release, "a".repeat(40), null).isSeasonPackFor(season))
        }
    }

    @Test
    fun tagsPreserveNothingSourceFactsAndDistinguishHdr10Plus() {
        val tags = qualityTags("Film.2160p.HDR10+.DoVi.WEB-DL.HEVC.TrueHD.Atmos.HUN")
        assertTrue(
            tags.containsAll(
                listOf("4K", "HDR10+", "Dolby Vision", "WEB-DL", "HEVC", "TrueHD", "Atmos", "HU")
            )
        )
        assertFalse(qualityTags("Unknown release").contains("1080p"))
    }

    @Test
    fun wrongYearEpisodeAndExtrasStayFlaggedDespiteANameMatch() {
        val movie = Media("tt0133093", "movie", "The Matrix", year = "1999")
        val wrongYear =
            sourceMatch(
                SourceStream("Test addon", "The.Matrix.2026.1080p", "a".repeat(40), null),
                movie,
                null,
            )
        assertEquals("Check title match", wrongYear.label)
        val extra =
            sourceMatch(
                SourceStream("Test addon", "The.Matrix.1999.Trailer.1080p", "a".repeat(40), null),
                movie,
                null,
            )
        assertTrue(extra.reasons.any { it.contains("extra") })
        val episode =
            sourceMatch(
                SourceStream("Test addon", "Severance.S02E04.1080p", "a".repeat(40), null),
                Media("tt11280740", "series", "Severance"),
                Episode("", 2, 3, "", "", "", ""),
            )
        assertTrue(episode.reasons.any { it.contains("different episode") })
    }

    @Test
    fun titlesContainingYearsAreNotTruncatedToFalseConflicts() {
        val result =
            sourceMatch(
                SourceStream("Test addon", "Blade.Runner.2049.2017.2160p", "a".repeat(40), null),
                Media("tt1856101", "movie", "Blade Runner 2049", year = "2017"),
                null,
            )
        assertTrue(result.reasons.isEmpty())
        assertEquals("Filename matches · inferred", result.label)
    }

    @Test
    fun malformedCacheResponsesNeverBecomeNegativeAvailability() {
        val hash = "a".repeat(40)
        val root =
            JSONObject()
                .put("success", true)
                .put("data", JSONArray().put(JSONObject().put("hash", hash).put("size", 123)))
        val result = parseSourceCache(root, setOf(hash, "b".repeat(40)))
        assertEquals(setOf(hash), result.cached)
        assertEquals(setOf(hash, "b".repeat(40)), result.checked)
        assertEquals(123L, result.sizes[hash])
        assertThrows(IllegalStateException::class.java) {
            parseSourceCache(JSONObject().put("success", true), setOf(hash))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseSourceCache(
                JSONObject().put("success", true).put("data", JSONArray().put("invalid")),
                setOf(hash),
            )
        }
    }
}
