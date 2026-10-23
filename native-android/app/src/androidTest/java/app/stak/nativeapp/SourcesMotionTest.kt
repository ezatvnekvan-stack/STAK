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
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourcesMotionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun findSourcesOpensAndReopensAModalWithNormalMotionEnabled() {
        val calls = CopyOnWriteArrayList<String>()
        val title = Media("tt9912345", "movie", "Normal motion sources", poster = "https://example.invalid/poster.jpg")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            calls.add(request.url.encodedPath)
            val body = if (request.url.encodedPath.contains("/meta/")) JSONObject().put("meta", title.json()).toString()
                else """{"streams":[],"results":[],"metas":[]}"""
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
            compose.waitUntil(15000) { compose.onAllNodesWithText("No sources found.").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(calls.any { it.endsWith("/stream/movie/tt9912345.json") })
            assertFalse(vm.reduced.value)
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            saveEvidence("sources-normal-motion-${compose.activity.resources.configuration.screenWidthDp}.png", bitmap)
            bitmap.recycle()
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
