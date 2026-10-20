package app.stak.nativeapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: StakViewModel

    @Before fun resetLocalData() {
        compose.runOnUiThread { vm = ViewModelProvider(compose.activity)[StakViewModel::class.java]; vm.appearance("reduced", false) }
        runBlocking(Dispatchers.IO) { vm.repository.db.clearAllTables() }
    }
    @After fun recordFinalState() { runCatching { capture("last-screen") }; compose.onRoot().printToLog("STAK-NATIVE-TEST") }

    @Test fun nativeNavigationAndReducedMotionRemainUsable() {
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        capture("settings")
        compose.onAllNodes(isToggleable())[1].performClick()
        compose.onNodeWithTag("nav-watchlist").performClick()
        compose.onNodeWithText("Your Watchlist is empty. Open a title to add it.").assertIsDisplayed()
        compose.onNodeWithTag("nav-tracking").performClick()
        compose.onNodeWithText("Calendar").performClick()
        compose.onNodeWithText("Add series to Watchlist to follow their releases.").assertIsDisplayed()
        compose.onNodeWithTag("nav-library").performClick()
        compose.onNodeWithText("TorBox").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Connect TorBox in Settings.").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun posterNavigationWatchedUndoAndEpisodeSheet() {
        val fixture = Media("jf:fixture-series", "series", "Motion test series", year = "2026", description = "An isolated test title for exercising native transitions.", episodes = listOf(
            Episode("fixture:1:1", 1, 1, "First episode", "2026-01-01T00:00:00Z", "", "Episode sheet validation."),
            Episode("fixture:1:2", 1, 2, "Second episode", "2026-01-02T00:00:00Z", "", "")
        ))
        vm.repository.remember(fixture)
        runBlocking(Dispatchers.IO) { vm.repository.dao.put(SavedMedia(fixture.key, "watchlist", fixture.json().toString())) }
        compose.onNodeWithTag("nav-watchlist").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText(fixture.name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(fixture.name).performClick()
        compose.onNodeWithText("Watched").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Unwatch").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Watched").fetchSemanticsNodes().isNotEmpty() }
        capture("title-fixture")
        compose.onNodeWithTag("title-scroll").performScrollToNode(hasText("Seasons & episodes"))
        compose.onNodeWithText("Seasons & episodes").performClick()
        compose.onNodeWithText("0 / 2 episodes watched").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Mark episode watched")[0].performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("1 / 2 episodes watched").fetchSemanticsNodes().isNotEmpty() }
        capture("episodes-fixture")
        pressSystemBack()
        compose.waitUntil(10000) { compose.onAllNodesWithText("1 / 2 episodes watched").fetchSemanticsNodes().isEmpty() }
        compose.runOnUiThread { vm.appearance("reduced", true) }
        compose.onNodeWithTag("title-scroll").performScrollToNode(hasText("Seasons & episodes"))
        compose.onNodeWithText("Seasons & episodes").performClick()
        compose.onNodeWithText("First episode", substring = true).performTouchInput { click() }
        compose.onNodeWithText("1 / 2 episodes watched").assertIsDisplayed()
        pressSystemBack()
        compose.waitForIdle()
        pressSystemBack()
        compose.onNodeWithText(fixture.name).assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val folder = java.io.File(compose.activity.getExternalFilesDir(null), "evidence").apply { mkdirs() }
        java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        require(name.matches(Regex("[a-z-]+")))
        val command = "mkdir -p /sdcard/Download/stak-native-evidence && cp ${folder.absolutePath}/$name.png /sdcard/Download/stak-native-evidence/$name.png"
        android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    private fun pressSystemBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }
}
