package app.stak.nativeapp

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiscoveryDataTest {
    private lateinit var repository: Repository
    private var originalKey = ""
    private val requests = CopyOnWriteArrayList<HttpUrl>()
    private var failRatings = false

    @Before fun prepare() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests.add(request.url)
            val path = request.url.encodedPath
            var code = 200
            val body = when {
                path.endsWith("/watch/providers") -> JSONObject("""{"results":{"US":{"flatrate":[{"provider_id":${if (path.contains("/105/")) 9 else 8}}]}}}""")
                path.endsWith("/content_ratings") -> {
                    if (failRatings) code = 503
                    JSONObject("""{"results":[{"iso_3166_1":"US","rating":"TV-14"}]}""")
                }
                path.endsWith("/search/multi") -> {
                    val q = request.url.queryParameter("query").orEmpty()
                    val rows = if (q == "Blade Runner 2049") listOf(title(201, "Blade Runner 2049", "movie", "2017-01-01"))
                    else listOf(title(101, "B movie"), title(102, "A series", "tv"), title(103, "Wrong genre", genre = 35), title(104, "Wrong language", language = "de"), title(105, "Wrong provider"))
                    JSONObject().put("results", JSONArray(rows)).put("total_pages", 2)
                }
                path.contains("/trending/") -> JSONObject().put("results", JSONArray(listOf(title(401, "TV fixture", "tv", genre = 10765)))).put("total_pages", 2)
                path.contains("/discover/") -> JSONObject().put("results", JSONArray()).put("total_pages", 3)
                else -> JSONObject().put("results", JSONArray())
            }
            Response.Builder().request(request).code(code).message("TEST ONLY").protocol(Protocol.HTTP_1_1)
                .body(body.toString().toResponseBody()).build()
        }.build()
        repository = Repository(app, client)
        originalKey = repository.credentials.get("tmdb_key")
        repository.credentials.set("tmdb_key", "test-only-discovery")
        repository.metadataDirectory.listFiles()?.forEach { it.delete() }
    }

    @After fun cleanup() {
        if (::repository.isInitialized) {
            repository.credentials.set("tmdb_key", originalKey)
            repository.db.close()
        }
    }

    private fun title(id: Int, name: String, type: String = "movie", date: String = "2022-01-01", genre: Int = 18, language: String = "en") =
        JSONObject().put("id", id).put("media_type", type).put(if (type == "tv") "name" else "title", name)
            .put(if (type == "tv") "first_air_date" else "release_date", date)
            .put("genre_ids", JSONArray(listOf(genre))).put("original_language", language)

    @Test fun unifiedSearchFiltersBothTypesAndChecksRegionalAvailability() = runBlocking {
        val found = repository.discoveryPage("movie", query = "discovery parity", filters = mapOf("genre" to "18", "language" to "en", "provider" to "8", "year_from" to "2020", "sort" to "title.asc"))
        assertEquals(listOf("A series", "B movie"), found.items.map { it.name })
        assertEquals(listOf("series", "movie"), found.items.map { it.type })
        assertEquals(2, found.next)
        assertTrue(found.filteredPage)
        val search = requests.single { it.encodedPath.endsWith("/search/multi") }
        assertNull(search.queryParameter("with_genres"))
        assertEquals(3, requests.count { it.encodedPath.endsWith("/watch/providers") })
    }

    @Test fun emptyFilteredPagesStillOfferTheProvidersNextPage() = runBlocking {
        val found = repository.discoveryPage("series", page = 2, filters = mapOf("genre" to "10765", "year_from" to "2020", "sort" to "primary_release_date.desc"))
        assertTrue(found.items.isEmpty())
        assertEquals(3, found.next)
        val request = requests.single()
        assertEquals("/3/discover/tv", request.encodedPath)
        assertEquals("first_air_date.desc", request.queryParameter("sort_by"))
        assertEquals("2020-01-01", request.queryParameter("first_air_date.gte"))
        assertEquals("10765", request.queryParameter("with_genres"))
    }

    @Test fun tvRatingFailureIsReportedAndRetryKeepsTheFilters() = runBlocking {
        val filters = mapOf("genre" to "10765", "certification" to "TV-14")
        failRatings = true
        try {
            repository.discoveryPage("series", "trending", filters = filters)
            fail("A failed content-rating request must not look like an empty successful page")
        } catch (expected: Exception) { assertTrue(expected.message.orEmpty().isNotBlank()) }
        failRatings = false
        val found = repository.discoveryPage("series", "trending", filters = filters)
        assertEquals(listOf("TV fixture"), found.items.map { it.name })
        assertEquals(2, found.next)
        assertTrue(found.filteredPage)
        assertEquals(2, requests.count { it.encodedPath == "/3/tv/401/content_ratings" })
    }

    @Test fun numericTitleRemainsEligibleBeforeYearInterpretation() = runBlocking {
        val found = repository.discoveryPage("movie", query = "Blade Runner 2049")
        assertEquals(listOf("Blade Runner 2049"), found.items.map { it.name })
        assertEquals("2017", found.items.single().year)
        assertEquals(1, requests.count { it.encodedPath.endsWith("/search/multi") })
        assertEquals("Blade Runner 2049", requests.single().queryParameter("query"))
    }
}
