package app.stak.nativeapp.data

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DirectCatalogTest {
    private fun json(raw: String) = JsonParser.parseString(raw).asJsonObject

    @Test fun heroImdbIdentityUsesTypedExternalIdsInsteadOfTmdbScore()=runBlocking {
        val paths=mutableListOf<String>()
        val catalog=DirectCatalog({"synthetic-key"}){url,_,_->paths+=url;json("""{"imdb_id":"tt1234567"}""")}
        assertEquals("tt1234567",catalog.imdbIdentity(MediaItem("tmdb:42",name="Movie",tmdbRating=9.9)))
        assertEquals("tt1234567",catalog.imdbIdentity(MediaItem("tmdb:43",type="series",name="Series")))
        assertTrue(paths[0].contains("/movie/42/external_ids"));assertTrue(paths[1].contains("/tv/43/external_ids"))
    }
    @Test fun existingImdbIdentityDoesNotMakeAnotherProviderRequest()=runBlocking {
        val catalog=DirectCatalog({"synthetic-key"}){_,_,_->throw AssertionError("Unnecessary provider request")}
        assertEquals("tt1234567",catalog.imdbIdentity(MediaItem("tmdb:42","tt1234567",name="Movie")))
        assertNull(catalog.imdbIdentity(MediaItem("jellyfin:private",name="Private")))
    }
    @Test fun emptyCatalogIsNotReplacedBySamples() = runBlocking {
        val catalog = DirectCatalog({ "" }) { url, _, token ->
            assertTrue(url.startsWith("https://v3-cinemeta.strem.io/catalog/movie/top"))
            assertNull(token)
            json("""{"metas":[]}""")
        }
        assertTrue(catalog.discover("movie", "popular", 1).items.isEmpty())
    }

    @Test fun failedCatalogReportsFailure() = runBlocking {
        val catalog = DirectCatalog({ "" }) { _, _, _ -> throw IOException("unavailable") }
        try { catalog.discover("movie", "popular", 1); fail("Expected failure") } catch (_: IOException) { }
    }

    @Test fun searchBothKindsCanReturnLegitimateEmptyResults() = runBlocking {
        val urls = mutableListOf<String>()
        val catalog = DirectCatalog({ "" }) { url, _, _ -> urls.add(url); json("""{"metas":[]}""") }
        assertTrue(catalog.search("not a title").items.isEmpty())
        assertEquals(2, urls.size)
        assertTrue(urls.any { it.contains("/series/") })
        assertTrue(urls.all { it.contains("not%20a%20title") })
    }

    @Test fun searchFailureIsNotAnEmptySuccess() = runBlocking {
        val catalog = DirectCatalog({ "" }) { _, _, _ -> throw IOException("down") }
        try { catalog.search("Dune"); fail("Expected failure") } catch (_: IOException) { }
    }

    @Test fun cinemetaDetailsDoNotInventRatingsProgressOrSources() = runBlocking {
        val catalog = DirectCatalog({ "" }) { _, _, _ -> json("""{"meta":{"id":"tt1234567","name":"A title","imdbRating":"9.0","genres":["Drama"],"videos":[]}}""") }
        val detail = catalog.details("movie", "tt1234567")
        assertEquals("A title", detail.item.name)
        assertNull(detail.imdbRating)
        assertNull(detail.item.rating)
        assertEquals(0f, detail.item.progress)
        assertTrue(detail.sources.isEmpty())
        assertTrue(detail.gallery.isEmpty())
        assertTrue(detail.seasons.isEmpty())
    }

    @Test fun malformedDetailsDoNotOpenAnUnrelatedMovie() = runBlocking {
        val catalog = DirectCatalog({ "" }) { _, _, _ -> json("{}") }
        try { catalog.details("movie", "tt1234567"); fail("Expected failure") } catch (_: IOException) { }
    }

    @Test fun tmdbBearerIsOnlySentToTmdb() = runBlocking {
        val catalog = DirectCatalog({ "test.bearer.token" }) { url, params, token ->
            assertTrue(url.startsWith("https://api.themoviedb.org/3/"))
            assertEquals("test.bearer.token", token)
            assertFalse(params.containsKey("api_key"))
            json("""{"results":[],"total_pages":1}""")
        }
        assertTrue(catalog.discover("series", "trending", 1).items.isEmpty())
    }

    @Test fun tmdbIdentityPreservesKindAndDoesNotBecomeFakeImdbId() {
        val item = DirectCatalog.tmdbItem(json("""{"id":42,"name":"Example","first_air_date":"2026-01-01","vote_average":7.5}"""), "series")
        assertEquals("tmdb:42", item.mediaKey)
        assertNull(item.imdbId)
        assertTrue(item.isSeries)
        assertEquals("2026", item.year)
        assertNull(item.rating)
        assertEquals(7.5, item.tmdbRating!!, .001)
    }

    @Test fun homePreferencesPreserveStoredOrderAndDisabledRows() {
        val rows = HomeSections.read("""[{"id":"top_series","on":true},{"id":"trending_movie","on":false}]""")
        assertEquals("top_series", rows.first().id)
        assertFalse(rows[1].enabled)
        assertEquals(rows.size, rows.map { it.id }.distinct().size)
    }

    @Test fun corruptHomePreferencesRecoverWithoutRemovingNavigation() {
        assertEquals(HomeSections.defaults(), HomeSections.read("invalid"))
    }
}
