package app.stak.nativeapp

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiscoveryUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: StakViewModel
    private val requests = CopyOnWriteArrayList<HttpUrl>()
    private var oldState = ""
    private var oldKey = ""
    @Volatile private var failFiltered = false

    @Before fun setup() {
        val app = compose.activity.application
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests.add(request.url)
            val failure = failFiltered && request.url.queryParameter("primary_release_date.gte") == "2020-01-01"
            Response.Builder().request(request).code(if (failure) 503 else 200).message("TEST ONLY")
                .protocol(Protocol.HTTP_1_1).body("""{"results":[],"total_pages":1}""".toResponseBody()).build()
        }.build()
        vm = StakViewModel(app, Repository(app, client))
        oldState = vm.options.value["discover_state"].orEmpty()
        oldKey = vm.repository.credentials.get("tmdb_key")
        vm.option("discover_state", "")
        vm.repository.credentials.set("tmdb_key", "test-only-discovery-ui")
        vm.appearance("reduced", true)
        runBlocking(Dispatchers.IO) { vm.repository.metadataDirectory.listFiles()?.forEach { it.delete() } }
        compose.runOnUiThread { compose.activity.setContent { StakApp(vm, "discover") } }
    }

    @After fun cleanup() {
        if (::vm.isInitialized) {
            vm.option("discover_state", oldState)
            vm.repository.credentials.set("tmdb_key", oldKey)
        }
    }

    private fun openFilters() = compose.onNodeWithText("FILTERS", useUnmergedTree = true).performScrollTo().performClick()
    private fun value(label: String, text: String) = compose.onNodeWithContentDescription(label).assert(
        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text))
    )

    @Test fun draftSurvivesDismissalAndInvalidYearsCannotApply() {
        openFilters()
        compose.onNodeWithContentDescription("From year").performScrollTo().performTextReplacement("+2020")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Use years between 1880 and 2100.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("SHOW RESULTS").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("From year").performScrollTo().performTextReplacement("2020")
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        assertFalse(requests.any { it.queryParameter("primary_release_date.gte") == "2020-01-01" })
        openFilters()
        value("From year", "2020")
        compose.onNodeWithText("SHOW RESULTS").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10000) { requests.any { it.queryParameter("primary_release_date.gte") == "2020-01-01" } }
        compose.waitUntil(10000) { DiscoveryPreferences.parse(vm.options.value["discover_state"]).filters["year_from"] == "2020" }
        val persisted = vm.repository.preferences.getString("discover_state", "")
        assertEquals("2020", DiscoveryPreferences.parse(persisted).filters["year_from"])
        compose.onNodeWithContentDescription("Remove 2020–… filter").performScrollTo().performClick()
        compose.waitUntil(10000) { DiscoveryPreferences.parse(vm.options.value["discover_state"]).filters["year_from"].isNullOrBlank() }
    }

    @Test fun seriesFiltersUseTvGenresAndAgeRatingsAndSearchLanguages() {
        compose.onNodeWithText("SERIES", substring = false).performClick()
        openFilters()
        compose.onNodeWithContentDescription("Genre").performScrollTo().performClick()
        compose.onNodeWithText("Action & Adventure", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("US age rating").performScrollTo().performClick()
        compose.onNodeWithText("TV-14", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Find a language").performScrollTo().performTextInput("Hungarian")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithContentDescription("Original language").performScrollTo().performClick()
        compose.onNodeWithText("Hungarian · HU", useUnmergedTree = true).performClick()
        compose.onNodeWithText("SHOW RESULTS").performScrollTo().performClick()
        compose.waitUntil(10000) { requests.any { it.encodedPath == "/3/discover/tv" && it.queryParameter("with_genres") == "10759" } }
        val sent = requests.last { it.encodedPath == "/3/discover/tv" }
        assertEquals("hu", sent.queryParameter("with_original_language"))
        assertNull(sent.queryParameter("certification"))
        compose.waitUntil(10000) { DiscoveryPreferences.parse(vm.options.value["discover_state"]).filters["certification"] == "TV-14" }
        compose.onNodeWithText("MOVIES", substring = false).performScrollTo().performClick()
        compose.waitUntil(10000) {
            val saved = DiscoveryPreferences.parse(vm.options.value["discover_state"])
            saved.type == "movie" && saved.filters["genre"].isNullOrBlank() && saved.filters["certification"].isNullOrBlank()
        }
        assertEquals("hu", DiscoveryPreferences.parse(vm.options.value["discover_state"]).filters["language"])
    }

    @Test fun retryRetainsTheAppliedFilters() {
        failFiltered = true
        openFilters()
        compose.onNodeWithContentDescription("From year").performScrollTo().performTextReplacement("2020")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("SHOW RESULTS").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("RETRY").fetchSemanticsNodes().isNotEmpty() }
        val before = requests.count { it.queryParameter("primary_release_date.gte") == "2020-01-01" }
        failFiltered = false
        compose.onNodeWithText("RETRY").performScrollTo().performClick()
        compose.waitUntil(10000) { requests.count { it.queryParameter("primary_release_date.gte") == "2020-01-01" } > before }
        compose.onNodeWithContentDescription("Remove 2020–… filter").assertExists()
        compose.onNodeWithText("No matching titles in the loaded results. Try fewer filters or load the next page.").performScrollTo().assertIsDisplayed()
    }
}
