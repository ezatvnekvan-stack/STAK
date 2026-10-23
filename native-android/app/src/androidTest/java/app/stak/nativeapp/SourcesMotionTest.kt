package app.stak.nativeapp

import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.json.JSONArray
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourcesMotionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun findSourcesOpensAndReopensAModalWithNormalMotionEnabled() {
        exercise(Media("tt9912345", "movie", "Normal motion sources", poster = "https://example.invalid/poster.jpg"))
    }

    @Test fun tvSourcesSelectSeasonEpisodeAndReopenWithNormalMotionEnabled() {
        exercise(Media("tt9912346", "series", "Normal motion sources", poster = "https://example.invalid/poster.jpg",
            episodes = listOf(
                Episode("tt9912346:1:1", 1, 1, "First episode", "", "", ""),
                Episode("tt9912346:2:1", 2, 1, "First episode", "", "", ""),
                Episode("tt9912346:2:2", 2, 2, "Second episode", "", "", ""),
            )))
    }

    private fun exercise(title: Media) {
        val calls = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            calls.add(request.url.encodedPath)
            val body = when {
                request.url.encodedPath.contains("/meta/") -> JSONObject().put("meta", title.json().put("videos", JSONArray(title.episodes.map {
                    JSONObject().put("id", it.id).put("season", it.season).put("episode", it.number).put("name", it.name)
                }))).toString()
                title.type == "series" && request.url.encodedPath.contains("/stream/") -> {
                    val selected = Regex(":([0-9]+):([0-9]+)\\.json$").find(request.url.encodedPath)!!
                    val season = selected.groupValues[1].toInt()
                    val number = selected.groupValues[2].toInt()
                    JSONObject().put("streams", JSONArray(listOf(
                        JSONObject().put("name", "Test addon 1080p").put("title", "Normal.motion.sources.S%02d.Complete.1080p".format(season)).put("infoHash", "a".repeat(40)),
                        JSONObject().put("name", "Test addon 1080p").put("title", "Normal.motion.sources.S%02dE%02d.1080p".format(season, number)).put("infoHash", "b".repeat(40)),
                    ))).toString()
                }
                else -> """{"streams":[],"results":[],"metas":[]}"""
            }
            Response.Builder().request(request).code(200).message("TEST ONLY").protocol(Protocol.HTTP_1_1).body(body.toResponseBody()).build()
        }.build()
        val vm = StakViewModel(compose.activity.application, Repository(compose.activity.application, client))
        val oldReduced = vm.reduced.value
        val oldTmdb = vm.repository.credentials.get("tmdb_key")
        val oldTorrentio = vm.options.value["torrentio"] ?: "true"
        val animationScale = Settings.Global.getFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        fun scale(value: Float) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("settings put global animator_duration_scale $value").use {
                FileInputStream(it.fileDescriptor).use { input -> input.readBytes() }
            }
        }
        try {
            scale(1f)
            vm.appearance("reduced", false)
            vm.option("torrentio", "true")
            vm.repository.credentials.set("tmdb_key", "")
            vm.repository.metadataDirectory.listFiles()?.forEach { it.delete() }
            vm.repository.remember(title)
            compose.runOnUiThread { compose.activity.setContent { StakApp(vm, "title/${Uri.encode(title.key)}/motion-test") } }
            compose.onNodeWithText("FIND SOURCES").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("sources-results").fetchSemanticsNodes().isNotEmpty() }
            if (title.type == "movie") {
                compose.waitUntil(15000) { compose.onAllNodesWithText("No sources found.").fetchSemanticsNodes().isNotEmpty() }
                assertTrue(calls.any { it.endsWith("/stream/movie/tt9912345.json") })
            } else {
                compose.waitUntil(15000) { calls.any { it.endsWith("/stream/series/tt9912346:1:1.json") } }
                val packCount = "1 of 2 releases · tags and title matches are inferred from filenames."
                compose.onNodeWithTag("sources-results").performScrollToNode(hasText(packCount))
                compose.onNodeWithText(packCount).assertIsDisplayed()
                compose.onNodeWithTag("sources-results").performScrollToNode(hasContentDescription("Choose season"))
                compose.onNodeWithContentDescription("Choose season").performClick()
                compose.onNodeWithText("Season 2").performClick()
                compose.waitUntil(15000) { calls.any { it.endsWith("/stream/series/tt9912346:2:1.json") } }
                compose.onNodeWithText("EPISODE").performClick()
                compose.onNodeWithContentDescription("Search episode").performClick()
                compose.onNodeWithText("E2 · Second episode").performClick()
                compose.waitUntil(15000) { calls.any { it.endsWith("/stream/series/tt9912346:2:2.json") } }
                val episodeCount = "2 of 2 releases · tags and title matches are inferred from filenames."
                compose.onNodeWithTag("sources-results").performScrollToNode(hasText(episodeCount))
                compose.onNodeWithText(episodeCount).assertIsDisplayed()
            }
            assertFalse(vm.reduced.value)
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            saveEvidence("sources-${title.type}-normal-motion-${compose.activity.resources.configuration.screenWidthDp}.png", bitmap)
            bitmap.recycle()
            compose.onNodeWithTag("sources-results").performScrollToNode(hasContentDescription("Close dialog"))
            compose.onNodeWithContentDescription("Close dialog").performClick()
            compose.onNodeWithTag("sources-results").assertDoesNotExist()
            compose.onNodeWithText("FIND SOURCES").performClick()
            compose.onNodeWithTag("sources-results").assertIsDisplayed()
            compose.onNodeWithContentDescription("Close dialog").performClick()
        } finally {
            scale(animationScale)
            vm.appearance("reduced", oldReduced)
            vm.option("torrentio", oldTorrentio)
            vm.repository.credentials.set("tmdb_key", oldTmdb)
        }
    }
}
