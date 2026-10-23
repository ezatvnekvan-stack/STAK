package app.stak.nativeapp

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SearchAliasTest {
    @Test fun localizedAndAlternativeTitlesRetainOriginalRanking() {
        val media = Media("tt1", "movie", "Spirited Away", aliases = listOf("千と千尋の神隠し", "Sen to Chihiro no kamikakushi"))
        assertEquals(-0.5, searchRelevance(media, "千と千尋の神隠し"), 0.0)
        assertTrue(searchRelevance(media, "Sen to Chihiro") < searchRelevance(Media("tt2", "movie", "Sen"), "Sen to Chihiro"))
    }

    @Test fun numericAliasIsAnExactTitleBeforeYearFiltering() {
        val media = Media("tt1", "movie", "Localized title", year = "2017", aliases = listOf("Blade Runner 2049"))
        assertTrue(matchesSearchTitle(media, "Blade Runner 2049"))
        assertEquals(-0.5, searchRelevance(media, "Blade Runner 2049"), 0.0)
        assertFalse(matchesSearchTitle(Media("tt2", "movie", "Blade Runner", year = "2049"), "Blade Runner 2049"))
    }

    @Test fun titleVariantsSurviveSerializationAndEnrichment() {
        val parsed = Media.fromJson(JSONObject("""{"id":"tt1","type":"movie","title":"Amélie","original_title":"Le Fabuleux Destin d'Amélie Poulain","aliases":["Amelie"],"alternative_titles":[{"title":"Wonderful Amelie"},"A French title",null]}"""))
        assertEquals("Amélie", parsed.name)
        assertTrue(parsed.aliases.contains("Wonderful Amelie"))
        assertTrue(parsed.aliases.contains("A French title"))
        val restored = Media.fromJson(parsed.json())
        assertEquals(parsed.aliases, restored.aliases)
        val enriched = restored.copy(aliases = listOf("New title")).enrichedBy(parsed)
        assertTrue(enriched.aliases.containsAll(parsed.aliases))
        assertTrue(enriched.aliases.contains("New title"))
    }

    @Test fun tmdbOriginalNamesAndNestedAlternativesArePreserved() {
        val media = tmdbMedia(JSONObject("""{"id":1,"name":"Localized series","original_name":"Original series","alternative_titles":{"results":[{"name":"Another name"}]}}"""), "series")
        assertTrue(media.aliases.contains("Original series"))
        assertTrue(media.aliases.contains("Another name"))
    }

    @Test fun releaseDateFallbackAndEmptyTitlesMatchReferenceScores() {
        val media = Media("tt1", "movie", "Dune", releaseDate = "2021-10-22")
        assertEquals(0.0, searchRelevance(media, "Dune 2021"), 0.0)
        assertEquals(1000.0, searchRelevance(Media("tt2", "movie", ""), "Dune"), 0.0)
    }
}
