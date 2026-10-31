package app.stak.nativeapp

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IndexerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: StakViewModel

    @After
    fun cleanup() {
        if (::vm.isInitialized) {
            vm.repository.credentials.set("torbox_token", "test-only-indexer-owner")
            vm.indexers.read().forEach { vm.indexers.remove(it.id) }
            vm.repository.credentials.set("torbox_token", "")
            vm.option("torrentio", "true")
        }
    }

    @Test
    fun sourceControlsPersistEncryptedScopedIndexersAndAggregatePartialResults() {
        val requests = java.util.concurrent.CopyOnWriteArrayList<Request>()
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    requests += request
                    val text =
                        when {
                            request.url.queryParameter("t") == "caps" -> "<caps><searching/></caps>"
                            request.url.encodedPath.endsWith("/api") ->
                                """<rss xmlns:torznab="http://torznab.com/schemas/2015/feed"><channel><item><title>Test episode</title><torznab:attr name="infohash" value="${"a".repeat(40)}"/><torznab:attr name="seeders" value="8"/></item></channel></rss>"""
                            request.url.encodedPath.endsWith("manifest.json") ->
                                """{"id":"test-only","name":"Test addon"}"""
                            request.url.host == "broken.example.invalid" -> "invalid json"
                            request.url.encodedPath.contains("/stream/") ->
                                """{"streams":[{"name":"Test addon 1080p","title":"Test episode","infoHash":"${"b".repeat(40)}","fileIdx":2}]}"""
                            else -> "{}"
                        }
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("TEST ONLY")
                        .body(text.toResponseBody())
                        .build()
                }
                .build()
        val app = compose.activity.application
        vm = StakViewModel(app, Repository(app, client))
        vm.repository.credentials.set("torbox_token", "test-only-indexer-owner")
        vm.indexers.reload()
        vm.indexers.read().forEach { vm.indexers.remove(it.id) }
        compose.runOnUiThread {
            vm.appearance("reduced", true)
            vm.option("torrentio", "false")
            compose.activity.setContent { StakApp(vm, "settings?section=sources") }
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("indexer-form").fetchSemanticsNodes().isNotEmpty()
        }
        compose
            .onNodeWithContentDescription("Source name")
            .performScrollTo()
            .performTextReplacement("Test Prowlarr")
        compose
            .onNodeWithContentDescription("Endpoint URL")
            .performScrollTo()
            .performTextReplacement("https://indexer.example.invalid/1/api")
        compose
            .onNodeWithContentDescription("Indexer API key")
            .performScrollTo()
            .performTextReplacement("test-only-indexer-secret")
        compose.onNodeWithTag("indexer-test-button").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithText("Connected: Torznab capabilities received")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag("indexer-add-button").performScrollTo().performClick()
        compose.waitUntil(5000) { vm.indexers.items.value.size == 1 }
        compose.onNodeWithTag("indexer-row-0-toggle").performScrollTo().performClick()
        compose.waitUntil(5000) { !vm.indexers.read().first().enabled }
        compose.onNodeWithTag("indexer-row-0-toggle").performClick()
        val addon =
            Indexer(
                name = "Test addon",
                kind = "stremio",
                url = "https://addon.example.invalid/configured/manifest.json",
            )
        val broken =
            Indexer(
                name = "Broken addon",
                kind = "stremio",
                url = "https://broken.example.invalid/manifest.json",
            )
        vm.indexers.add(addon)
        vm.indexers.add(broken)
        val result = runBlocking {
            vm.repository.searchSources(
                Media("tt0903747", "series", "Test", imdbId = "tt0903747"),
                Episode("tt0903747:1:2", 1, 2, "Test", "", "", ""),
            )
        }
        Assert.assertEquals(2, result.streams.size)
        Assert.assertEquals(1, result.errors.size)
        Assert.assertTrue(result.errors.single().startsWith("Broken addon:"))
        Assert.assertTrue(
            requests.any { it.url.encodedPath == "/configured/stream/series/tt0903747:1:2.json" }
        )
        Assert.assertTrue(
            requests.any {
                it.url.queryParameter("t") == "tvsearch" &&
                    it.url.queryParameter("ep") == "2" &&
                    it.url.queryParameter("apikey") == "test-only-indexer-secret"
            }
        )
        Assert.assertTrue(
            requests
                .filter { it.url.host.endsWith("example.invalid") }
                .all { it.header("Authorization") == null && it.header("X-Emby-Token") == null }
        )
        vm.repository.credentials.set("torbox_token", "test-only-different-indexer-owner")
        Assert.assertTrue(vm.indexers.read().isEmpty())
        vm.repository.credentials.set("torbox_token", "test-only-indexer-owner")
        Assert.assertEquals(3, vm.indexers.read().size)
    }
}
