package app.stak.nativeapp

import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DiscoveryModelTest {
    @Test fun preferencesRoundTripAndIgnoreUnknownFields() {
        val original = DiscoveryPreferences("series", "top", mapOf("genre" to "10765", "language" to "hu", "certification" to "TV-14"))
        assertEquals(original, DiscoveryPreferences.parse(original.json()))
        assertEquals(emptyMap<String, String>(), DiscoveryPreferences.parse("""{"filters":{"token":"secret","genre":123,"year_from":null}}""").filters)
        assertEquals(DiscoveryPreferences(), DiscoveryPreferences.parse("not json"))
        assertEquals("popular", DiscoveryPreferences.parse("""{"category":"invalid","type":"person"}""").category)
    }

    @Test fun mediaTypeChangeClearsOnlyTypeSpecificFilters() {
        val changed = DiscoveryPreferences(filters = mapOf("genre" to "28", "certification" to "PG", "provider" to "8", "language" to "ja", "year_from" to "2020")).forType("series")
        assertFalse(changed.filters.containsKey("genre"))
        assertFalse(changed.filters.containsKey("certification"))
        assertEquals("8", changed.filters["provider"])
        assertEquals("ja", changed.filters["language"])
        assertTrue(homeGenres("series").any { it.first == 10765 })
        assertFalse(homeGenres("series").any { it.first == 28 })
    }

    @Test fun invalidYearsHaveActionableErrors() {
        for (value in listOf("+2020", "02020", "2020.0", "1879", "2101", "abcd"))
            assertEquals("Use years between 1880 and 2100.", discoveryYearError(mapOf("year_from" to value)))
        assertEquals("Choose a start year before the end year.", discoveryYearError(mapOf("year_from" to "2024", "year_to" to "2020")))
        assertNull(discoveryYearError(mapOf("year_from" to "1880", "year_to" to "2100")))
        assertNull(discoveryYearError(mapOf("year_from" to "", "year_to" to "2020")))
    }

    @Test fun tvSortAndCertificationUseSupportedApiFields() {
        val filters = mapOf("sort" to "primary_release_date.desc", "certification" to "TV-14", "provider" to "8", "year_from" to "2020", "year_to" to "2024")
        val params = discoveryParams("series", "popular", 2, filters, LocalDate.of(2026, 10, 23))
        assertEquals("first_air_date.desc", params["sort_by"])
        assertEquals("2020-01-01", params["first_air_date.gte"])
        assertEquals("2024-12-31", params["first_air_date.lte"])
        assertEquals("8", params["with_watch_providers"])
        assertEquals("US", params["watch_region"])
        assertFalse(params.containsKey("certification"))
        assertEquals("name.asc", discoverySort("title.asc", "series"))
        assertEquals("title.asc", discoverySort("name.asc", "movie"))
        assertTrue(discoveryCertifications("series").contains("TV-MA"))
        assertFalse(discoveryCertifications("series").contains("PG-13"))
    }

    @Test fun invalidYearsNeverReachRequestConstruction() {
        assertThrows(IllegalArgumentException::class.java) {
            discoveryParams("movie", "popular", 1, mapOf("year_from" to "2025", "year_to" to "2020"), LocalDate.now())
        }
        assertEquals("PG-13", discoveryParams("movie", "popular", 1, mapOf("certification" to "PG-13"), LocalDate.now())["certification"])
    }

    @Test fun loadedPagesExcludeUnknownYearsAndMismatchedFilters() {
        val item = JSONObject("""{"genre_ids":[18,10765],"original_language":"hu","first_air_date":"2022-01-01"}""")
        val filters = mapOf("genre" to "10765", "language" to "hu", "year_from" to "2020", "year_to" to "2024")
        assertTrue(matchesDiscoveryFields(item, filters))
        assertFalse(matchesDiscoveryFields(item, filters + ("genre" to "28")))
        assertFalse(matchesDiscoveryFields(item, filters + ("language" to "en")))
        item.remove("first_air_date")
        assertFalse(matchesDiscoveryFields(item, filters))
    }

    @Test fun languageSearchKeepsSelectedLanguageAndSupportsCodes() {
        assertTrue(discoveryLanguages("hungar").any { it.first == "hu" })
        assertTrue(discoveryLanguages("zzzz", "hu").any { it.first == "hu" })
        assertTrue(discoveryLanguages("zzzz").isEmpty())
        assertTrue(discoveryLanguages("ja").any { it.first == "ja" })
    }

    @Test fun removingYearChipPreservesOtherAppliedFilters() {
        val filters = mapOf("genre" to "18", "year_from" to "2020", "year_to" to "2029", "language" to "hu")
        assertTrue(discoveryChips(filters, "movie").contains("year" to "2020–2029"))
        assertEquals(mapOf("genre" to "18", "language" to "hu"), removeDiscoveryFilter(filters, "year"))
        assertTrue(backupSettingAllowed("discover_state"))
        assertFalse(backupSettingAllowed("discover_token"))
    }
}
