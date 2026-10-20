package app.stak.nativeapp

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.*
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeSearchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: StakViewModel

    @After
    fun clearCredentials() {
        if (::vm.isInitialized) vm.repository.credentials.set("tmdb_key", "")
    }

    @Test
    fun inlineSearchLoadsBothTypesPaginatesAndClearsWithoutOpeningDiscover() {
        val paths = CopyOnWriteArrayList<String>()
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val path = request.url.encodedPath
                    paths.add(request.url.toString())
                    val root =
                        when {
                            path.endsWith("/search/multi") -> {
                                val page = request.url.queryParameter("page")?.toIntOrNull() ?: 1
                                JSONObject()
                                    .put("total_pages", 2)
                                    .put(
                                        "results",
                                        JSONArray(
                                            if (request.url.queryParameter("query") == "missing")
                                                emptyList<JSONObject>()
                                            else
                                                listOf(
                                                    JSONObject()
                                                        .put("id", page * 100 + 1)
                                                        .put("media_type", "movie")
                                                        .put("title", "Film result $page")
                                                        .put("release_date", "2026-01-01"),
                                                    JSONObject()
                                                        .put("id", page * 100 + 2)
                                                        .put("media_type", "tv")
                                                        .put("name", "Series result $page")
                                                        .put("first_air_date", "2026-01-02"),
                                                )
                                        ),
                                    )
                            }
                            path.endsWith("/external_ids") ->
                                JSONObject()
                                    .put(
                                        "imdb_id",
                                        "tt" + request.url.pathSegments[2].padStart(7, '0'),
                                    )
                            else -> JSONObject().put("results", JSONArray())
                        }
                    Response.Builder()
                        .request(request)
                        .code(200)
                        .message("TEST ONLY")
                        .protocol(Protocol.HTTP_1_1)
                        .body(root.toString().toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
        val app = compose.activity.application
        vm = StakViewModel(app, Repository(app, client))
        vm.repository.credentials.set("tmdb_key", "test-fixture-not-a-credential")
        runBlocking(Dispatchers.IO) {
            vm.repository.db.clearAllTables()
            vm.repository.metadataDirectory.listFiles()?.forEach { it.delete() }
        }
        compose.runOnUiThread {
            vm.appearance("reduced", true)
            compose.activity.setContent { StakApp(vm, "home") }
        }
        compose.onNodeWithContentDescription("Search").performTextInput("results")
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Film result 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Series result 1").assertExists()
        compose.onNodeWithText("Find your next watch").assertDoesNotExist()
        compose.onNodeWithText("LOAD MORE").performScrollTo().performClick()
        compose.waitUntil(10000) {
            paths.any { it.contains("/search/multi") && it.contains("page=2") }
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Film result 2").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Search").performScrollTo()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithContentDescription("Search").performTextInput("missing")
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("No results").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("No results").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search").assertTextEquals("")
    }
}
