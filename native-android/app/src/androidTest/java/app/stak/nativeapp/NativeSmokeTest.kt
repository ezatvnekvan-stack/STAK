package app.stak.nativeapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: StakViewModel

    @Before
    fun resetLocalData() {
        compose.runOnUiThread {
            vm = ViewModelProvider(compose.activity)[StakViewModel::class.java]
            vm.appearance("reduced", true)
            vm.option("theme", "dark")
        }
        listOf("tmdb_key", "torbox_token", "jellyfin_url", "jellyfin_token", "jellyfin_user")
            .forEach { vm.repository.credentials.set(it, "") }
        runBlocking(Dispatchers.IO) { vm.repository.db.clearAllTables() }
    }

    @Test
    fun originalNavigationAndSettingsBackStack() {
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("settings-appearance").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Reduce animations").performScrollTo().performClick()
        compose.onNodeWithText("All settings").performScrollTo().performClick()
        compose.onNodeWithTag("settings-metadata").assertIsDisplayed()
        compose.onNodeWithTag("nav-watchlist").performClick()
        compose.onNodeWithText("Your next watch starts here").assertIsDisplayed()
        compose.onNodeWithText("BROWSE DISCOVER").performScrollTo().performClick()
        compose.onNodeWithText("Find your next watch").assertIsDisplayed()
        compose.onNodeWithTag("nav-tracking").performClick()
        compose.onNodeWithText("CALENDAR").performClick()
        compose.onNodeWithText("Following").assertIsDisplayed()
        compose.onNodeWithTag("nav-library").performClick()
        compose.onNodeWithText("Your library awaits").assertIsDisplayed()
    }

    @Test
    fun episodeRailPersistsMarksAndSheetClosesWithSystemBack() {
        val title =
            Media(
                "jf:fixture-series",
                "series",
                "Test series",
                year = "2026",
                description = "Test-only title",
                episodes =
                    listOf(
                        Episode(
                            "fixture:1:1",
                            1,
                            1,
                            "First episode",
                            "2025-01-01T00:00:00Z",
                            "",
                            "Test-only episode",
                        ),
                        Episode(
                            "fixture:1:2",
                            1,
                            2,
                            "Second episode",
                            "2025-01-02T00:00:00Z",
                            "",
                            "",
                        ),
                    ),
            )
        vm.repository.remember(title)
        runBlocking(Dispatchers.IO) {
            vm.repository.dao.put(SavedMedia(title.key, "watchlist", title.json().toString()))
        }
        compose.onNodeWithTag("nav-watchlist").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText(title.name).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(title.name).performClick()
        compose.onNodeWithTag("title-scroll").performScrollToNode(hasText("1. First episode"))
        compose.onNodeWithText("1. First episode").performClick()
        compose.onNodeWithText("MARK WATCHED").performClick()
        compose.waitUntil(10000) {
            runBlocking(Dispatchers.IO) { vm.repository.dao.allEpisodes().size == 1 }
        }
        InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Test-only episode").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("title-scroll").performScrollToIndex(0)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText(title.name).assertIsDisplayed()
        compose.onNodeWithTag("nav-tracking").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText(title.name).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(title.name).assertExists()
        runBlocking(Dispatchers.IO) {
            val journal =
                vm.repository.dao.all().first {
                    it.mediaKey == title.key && it.collection == "tracking"
                }
            Assert.assertEquals(50.0, org.json.JSONObject(journal.json).getDouble("progress"), .01)
            Assert.assertFalse(org.json.JSONObject(journal.json).getBoolean("played"))
            vm.changeEpisodesWatched(title, title.episodes, false)
            Assert.assertTrue(vm.repository.dao.allEpisodes().isEmpty())
            Assert.assertFalse(
                vm.repository.dao.all().any {
                    it.mediaKey == title.key && it.collection == "tracking"
                }
            )
            Assert.assertTrue(
                vm.repository.dao.all().any {
                    it.mediaKey == title.key && it.collection == "watchlist"
                }
            )
        }
    }
}
