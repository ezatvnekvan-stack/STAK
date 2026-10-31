package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: StakViewModel
    private val requests = CopyOnWriteArrayList<Request>()
    private val hash = "a".repeat(40)
    private val other = "b".repeat(40)
    private var added = false

    @After
    fun cleanup() {
        if (::vm.isInitialized) vm.repository.credentials.set("torbox_token", "")
    }

    private fun capture(theme: String) {
        compose.waitForIdle()
        val output =
            File(
                "/sdcard/Pictures/STAK-native-evidence/beta-sources-$theme-${compose.activity.resources.configuration.screenWidthDp}.png"
            )
        output.parentFile!!.mkdirs()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test
    fun searchTitleFiltersCacheAndAddsOneTorrentThenOpensTorBox() {
        val title = Media("tt0133093", "movie", "Pipeline fixture", year = "2026")
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    requests += request
                    val path = request.url.encodedPath
                    val root =
                        when {
                            path.endsWith("/user/me") ->
                                JSONObject()
                                    .put("success", true)
                                    .put("data", JSONObject().put("email", "test@example.invalid"))
                            path.endsWith("/torrents/checkcached") ->
                                JSONObject()
                                    .put("success", true)
                                    .put(
                                        "data",
                                        JSONArray()
                                            .put(
                                                JSONObject()
                                                    .put("hash", hash)
                                                    .put("size", 1000000000)
                                            ),
                                    )
                            path.endsWith("/torrents/createtorrent") -> {
                                added = true
                                JSONObject().put("success", true)
                            }
                            path.endsWith("/torrents/mylist") ->
                                JSONObject()
                                    .put("success", true)
                                    .put(
                                        "data",
                                        JSONArray(
                                            if (added)
                                                listOf(
                                                    JSONObject()
                                                        .put("id", 123)
                                                        .put("hash", hash)
                                                        .put("name", "Fixture torrent")
                                                        .put("progress", 1)
                                                        .put("download_finished", true)
                                                        .put("files", JSONArray())
                                                )
                                            else emptyList<JSONObject>()
                                        ),
                                    )
                            path.contains("/stream/") ->
                                JSONObject()
                                    .put(
                                        "streams",
                                        JSONArray()
                                            .put(
                                                JSONObject()
                                                    .put("name", "Test addon 4K")
                                                    .put(
                                                        "title",
                                                        "Pipeline.fixture.2026.2160p.WEB-DL.HEVC",
                                                    )
                                                    .put("infoHash", hash)
                                            )
                                            .put(
                                                JSONObject()
                                                    .put("name", "Test addon 1080p")
                                                    .put(
                                                        "title",
                                                        "Pipeline.fixture.2026.1080p.WEB-DL",
                                                    )
                                                    .put("infoHash", other)
                                            ),
                                    )
                            path.contains("/meta/movie/") -> JSONObject().put("meta", title.json())
                            path.contains("/catalog/movie/") ->
                                JSONObject().put("metas", JSONArray().put(title.json()))
                            else -> JSONObject().put("metas", JSONArray())
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
        vm.repository.credentials.set("torbox_token", "test-only-source-flow")
        vm.repository.credentials.set("tmdb_key", "")
        compose.runOnUiThread {
            vm.appearance("reduced", true)
            vm.option("torrentio", "true")
            compose.activity.setContent { StakApp(vm, "home") }
        }
        compose.onNodeWithContentDescription("Search").performTextInput("Pipeline fixture")
        compose.onNodeWithContentDescription("Search").performImeAction()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Pipeline fixture").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Pipeline fixture").performClick()
        compose.onNodeWithText("FIND SOURCES").performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("Cached on TorBox").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Cached sources only").performClick()
        compose
            .onNodeWithText("1 of 2 releases · tags and title matches are inferred from filenames.")
            .assertExists()
        compose.onNodeWithContentDescription("Source quality").performClick()
        compose.onNodeWithText("1080p").performClick()
        compose.onNodeWithText("No matching releases").assertExists()
        compose.onNodeWithText("CLEAR FILTERS").performClick()
        for (theme in listOf("dark", "light")) {
            compose.runOnUiThread { vm.option("theme", theme) }
            capture(theme)
        }
        compose.onNodeWithContentDescription("Cached sources only").performClick()
        compose.onNodeWithTag("sources-results").performScrollToNode(hasText("ADD TO TORBOX"))
        compose.onNodeWithText("ADD TO TORBOX").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("OPEN TORBOX").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, requests.count { it.url.encodedPath.endsWith("/torrents/createtorrent") })
        compose.onNodeWithText("OPEN TORBOX").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Fixture torrent").fetchSemanticsNodes().isNotEmpty()
        }
        val check = requests.first { it.url.encodedPath.endsWith("/torrents/checkcached") }
        assertEquals("Bearer test-only-source-flow", check.header("Authorization"))
        assertNull(check.url.queryParameter("token"))
    }
}
