package app.stak.nativeapp

import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.ImageLoader
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.*
import org.junit.*
import org.junit.runner.RunWith

/** A fixture-only motion recording. The frame clock is advanced at a visible cadence. */
@androidx.test.filters.SdkSuppress(minSdkVersion = 30)
@RunWith(AndroidJUnit4::class)
class NativeMotionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val automation
        get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
            String(it.readBytes())
        }

    private fun settle() {
        repeat(28) {
            compose.mainClock.advanceTimeByFrame()
            Thread.sleep(16)
        }
    }

    @Test
    fun recordPosterNavigationAndReducedMotionWithRealNativeControls() {
        val picture =
            InstrumentationRegistry.getInstrumentation()
                .context
                .assets
                .open("provider-fixture.png")
                .readBytes()
        val title =
            Media(
                "tt0133093",
                "movie",
                "Motion study",
                poster = "https://image.tmdb.org/t/p/w500/test.jpg",
                background = "https://image.tmdb.org/t/p/w1280/test.jpg",
                year = "2026",
                description = "Test-only native motion fixture",
                genres = listOf("Drama"),
            )
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    Response.Builder()
                        .request(request)
                        .code(200)
                        .message("TEST ONLY")
                        .protocol(Protocol.HTTP_1_1)
                        .body(
                            if (request.url.host == "image.tmdb.org")
                                picture.toResponseBody("image/png".toMediaType())
                            else
                                JSONObject()
                                    .put("meta", title.json())
                                    .put("metas", JSONArray())
                                    .put("results", JSONArray())
                                    .toString()
                                    .toResponseBody("application/json".toMediaType())
                        )
                        .build()
                }
                .build()
        val app = compose.activity.application
        val vm = StakViewModel(app, Repository(app, client))
        listOf("torbox_token", "tmdb_key", "jellyfin_url", "jellyfin_token", "jellyfin_user")
            .forEach { vm.repository.credentials.set(it, "") }
        runBlocking(Dispatchers.IO) {
            vm.repository.db.clearAllTables()
            vm.repository.dao.put(SavedMedia(title.key, "watchlist", title.json().toString()))
        }
        vm.repository.remember(title)
        Coil.setImageLoader(ImageLoader.Builder(app).okHttpClient(client).build())
        shell("settings put global animator_duration_scale 1")
        compose.runOnUiThread {
            vm.appearance("reduced", false)
            vm.option("theme", "dark")
            compose.activity.setContent { StakApp(vm, "watchlist") }
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithText(title.name).fetchSemanticsNodes().isNotEmpty()
        }
        shell("mkdir -p /sdcard/Pictures/STAK-native-evidence")
        val path = "/sdcard/Pictures/STAK-native-evidence/native-motion.mp4"
        val recorder =
            automation.executeShellCommand("screenrecord --time-limit 20 --bit-rate 2000000 $path")
        try {
            Thread.sleep(700)
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText(title.name).performClick()
            settle()
            compose.onNodeWithTag("title-scroll").assertExists()
            compose.onNodeWithContentDescription("Back").performClick()
            settle()
            compose.onNodeWithTag("nav-watchlist").assertIsSelected()
            compose.onNodeWithTag("nav-settings").performClick()
            settle()
            compose.onNodeWithTag("nav-settings").assertIsSelected()
            compose.onNodeWithTag("settings-sources").performClick()
            settle()
            compose.onNodeWithContentDescription("Torrentio").performClick()
            settle()
            compose.onNodeWithContentDescription("Torrentio").performClick()
            settle()
            compose.onNodeWithTag("nav-tracking").performClick()
            settle()
            compose.onNodeWithText("CALENDAR").performClick()
            settle()
            compose.onNodeWithText("Following").assertExists()
            compose.runOnUiThread { vm.appearance("reduced", true) }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("nav-library").performClick()
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("nav-library").assertIsSelected()
            compose.onNodeWithText("Your library awaits").assertExists()
        } finally {
            compose.mainClock.autoAdvance = true
            shell("pkill -INT screenrecord")
            Thread.sleep(700)
            recorder.close()
            runBlocking(Dispatchers.IO) { vm.repository.db.clearAllTables() }
            vm.option("torrentio", "true")
        }
        Assert.assertTrue(
            "Native motion recording must be retained",
            (shell("stat -c %s $path").trim().toLongOrNull() ?: 0) > 0,
        )
    }
}
