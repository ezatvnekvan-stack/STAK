package app.stak.nativeapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.stak.nativeapp.data.SettingsStore
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiscoveryControlsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var settings: SettingsStore
    private var previous: String? = null
    @Before fun resetOnlyDiscoveryPreferences() {
        settings = SettingsStore(compose.activity)
        // This suite exercises browsing. First-run setup has its own isolated UI suite.
        if(compose.onAllNodesWithText("SET UP LATER").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithText("SET UP LATER").performClick()
        previous = settings.discoveryJson
        settings.discoveryJson = null
        compose.onNodeWithContentDescription("Discover").performClick()
    }
    @After fun restorePreferences() { settings.discoveryJson = previous }
    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasContentDescription(label)))

    @Test fun reversedYearRangeBlocksApplyAndCorrectedRangePersists() {
        compose.onNodeWithContentDescription("Discovery filters").performClick()
        compose.onNodeWithText("From year").performScrollTo()
        field("From year").performTextInput("2025")
        field("To year").performTextInput("2020")
        compose.onNodeWithText("SHOW RESULTS").assertIsNotEnabled()
        compose.onNodeWithText("Choose a start year before the end year.").assertExists()
        field("To year").performTextReplacement("2026")
        compose.onNodeWithText("SHOW RESULTS").assertIsEnabled().performClick()
        compose.onNodeWithText("2025–2026 ×").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Discover").performClick()
        compose.onNodeWithText("2025–2026 ×").assertExists()
    }

    @Test fun closingSheetDiscardsDraft() {
        compose.onNodeWithContentDescription("Discovery filters").performClick()
        compose.onNodeWithText("From year").performScrollTo()
        field("From year").performTextInput("1990")
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithContentDescription("Discovery filters").performClick()
        compose.onNodeWithText("From year").performScrollTo()
        field("From year").assertTextEquals("")
    }

    @Test fun servicePickerOpensSearchAndSavedRegion() {
        compose.onNodeWithContentDescription("Streaming shortcuts").performScrollToNode(hasContentDescription("All services"))
        compose.onNodeWithContentDescription("All services").performClick()
        compose.onNodeWithText("Streaming services").assertIsDisplayed()
        compose.onNodeWithText("Availability follows your saved region.").assertDoesNotExist()
        compose.onNodeWithText("Region").assertExists()
        compose.onNodeWithText("Find a service").assertExists()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Discover").assertIsDisplayed()
    }

    @Test fun homeSearchUsesItsOwnFilterDrawerAndClearReturnsToEmptySearch() {
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Dune")
        compose.onAllNodes(hasSetTextAction()).onFirst().performImeAction()
        compose.onNodeWithText("RESULTS: “Dune”",substring=true).assertExists()
        compose.onNodeWithContentDescription("Discovery filters").performClick()
        compose.onNodeWithText("Search movies and series together.").assertDoesNotExist()
        compose.onNodeWithText("A–Z").assertExists()
        compose.onNodeWithText("THIS YEAR").performScrollTo().performClick()
        compose.onNodeWithText("SHOW RESULTS").performScrollTo().performClick()
        val year=java.time.LocalDate.now().year
        compose.onNodeWithText("$year–$year ×").assertExists()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("Search movies and series together").assertDoesNotExist()
    }
    @Test fun reopenedSetupHasVisibleFooterWithoutNavigationOverlap() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Setup assistant"))
        compose.onNodeWithText("Setup assistant").performClick()
        compose.onNodeWithText("GET STARTED").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Discover").assertCountEquals(0)
        compose.onNodeWithText("SET UP LATER").performClick()
        compose.onNodeWithContentDescription("Discover").assertExists()
    }
}
