package app.stak.nativeapp

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class GlassUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun continueFeedLoadsNextUpAndKeepsResumeResultsWhenNextUpFails() = runBlocking {
        compose.runOnUiThread { compose.activity.setContent {} }
        var failNext = false
        val calls = java.util.concurrent.CopyOnWriteArrayList<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); calls += request
            val next = request.url.encodedPath.endsWith("/Shows/NextUp")
            val item = if (next) JSONObject().put("Id", "episode1").put("Type", "Episode")
                .put("SeriesName", "Next-up show").put("ParentIndexNumber", 1).put("IndexNumber", 4)
                else JSONObject().put("Id", "movie1").put("Type", "Movie").put("Name", "Resumed movie")
                    .put("RunTimeTicks", 100 * 600_000_000L).put("UserData", JSONObject().put("PlaybackPositionTicks", 41 * 600_000_000L))
            Response.Builder().request(request).code(if (next && failNext) 500 else 200).message("FIXTURE")
                .protocol(Protocol.HTTP_1_1).body(JSONObject().put("Items", JSONArray().put(item)).toString().toResponseBody()).build()
        }.build()
        val repository = Repository(compose.activity.application, client)
        val keys = listOf("jellyfin_url", "jellyfin_token", "jellyfin_user")
        val before = keys.associateWith { repository.credentials.get(it) }
        try {
            repository.credentials.set("jellyfin_url", "https://fixture.invalid/jellyfin")
            repository.credentials.set("jellyfin_token", "test-only-glass-feed")
            repository.credentials.set("jellyfin_user", "fixture-user")
            val feed = repository.continueFeed()
            assertEquals(2, feed.items.size)
            assertEquals(4, feed.items.last().episodeNumber)
            assertEquals("Next-up show", feed.items.last().seriesName)
            assertEquals(59L, continueRemainingMinutes(feed.items.first()))
            assertTrue(calls.any { it.url.encodedPath.endsWith("/Items/Resume") })
            assertEquals("fixture-user", calls.first { it.url.encodedPath.endsWith("/Shows/NextUp") }.url.queryParameter("UserId"))
            assertTrue(calls.all { it.header("X-Emby-Token") == "test-only-glass-feed" })
            failNext = true
            val partial = repository.continueFeed()
            assertEquals(1, partial.items.size)
            assertTrue(partial.warning.isNotBlank())
        } finally { before.forEach { (key, value) -> repository.credentials.set(key, value) } }
    }
    @Test fun posterLongPressOpensAnAnchoredMenuWithWorkingWatchlistAction() {
        val app = compose.activity.application
        val client = OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request())
            .code(200).message("FIXTURE").protocol(Protocol.HTTP_1_1).body("{}".toResponseBody()).build() }.build()
        val vm = StakViewModel(app, Repository(app, client))
        val title = Media("tt7654321", "movie", "Popup fixture", year = "2026")
        compose.runOnUiThread { compose.activity.setContent {
            MaterialTheme { CompositionLocalProvider(LocalStakViewModel provides vm) {
                LaunchedEffect(vm) { vm.saved.collect {} }
                Column { PosterCard(title, "popup-test", Modifier.width(120.dp), onOpen = { _, _ -> }) }
            } }
        } }
        compose.onNodeWithText(title.name).performTouchInput { longClick() }
        compose.onNodeWithTag("poster-actions").assertIsDisplayed()
        val bounds = compose.onNodeWithTag("poster-actions").fetchSemanticsNode().boundsInRoot
        assertTrue("Poster actions must stay compact", bounds.height < compose.activity.resources.displayMetrics.heightPixels * .5f)
        compose.onNodeWithText("Add to watchlist").performClick()
        compose.waitUntil(10000) { vm.saved.value.any { it.mediaKey == title.key && it.collection == "watchlist" } }
        compose.onNodeWithTag("poster-actions").assertDoesNotExist()
        runBlocking { vm.repository.dao.remove(title.key, "watchlist") }
    }
    @Test fun continueWatchingUsesLandscapeResumeAndNextUpCards() {
        val resume = Media("tt7654322", "movie", "Resume fixture", runtime = "100m", progress = 41f, playbackTicks = 41 * 600_000_000L)
        val next = Media("jf:nextfixture", "series", "Episode", seriesName = "Next-up fixture", episodeSeason = 1, episodeNumber = 4)
        compose.runOnUiThread { compose.activity.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(16.dp)) {
                ContinueWatchingRail(listOf(resume, next)) { _, _ -> }
            }
        } } }
        compose.onNodeWithText("RESUME").assertIsDisplayed()
        compose.onNodeWithText("NEXT UP").assertIsDisplayed()
        compose.onNodeWithText("59 min left").assertIsDisplayed()
        compose.onNodeWithText("S1 E4").assertIsDisplayed()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        saveEvidence("continue-watching-reference.png", bitmap); bitmap.recycle()
    }
}
