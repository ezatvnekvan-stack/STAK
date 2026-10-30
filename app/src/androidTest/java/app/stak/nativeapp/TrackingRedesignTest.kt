package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.StakTheme
import org.junit.*
import java.time.*
import java.io.File

@org.junit.Ignore("Interactive tracking retired in 1.3.4; replaced by PassiveTrackingUiTest")
class TrackingRedesignTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null;private lateinit var prefix:String
    private val movie=MediaItem("movie",name="Fixture Movie",runtime="120 min")
    private val series=MediaItem("show",imdbId="tt12345",type="series",name="Fixture Series",runtime="45 min")
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="tracking-redesign-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix).apply{journalPreferences=com.google.gson.Gson().toJson(JournalPreferences(view="history"))}}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}}
    private fun rows()=listOf(trackingEntity(movie,null,true))+(1..9).map{trackingEntity(series,Episode(1,it,"Episode $it",runtime="45 min"),true)}
    private fun screenshot(name:String){compose.waitForIdle();val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.absolutePath,"tracking-1.2.9");dir.mkdirs();File(dir,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun openStats(){
        if(compose.onAllNodesWithContentDescription("Back to Watching").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithContentDescription("Back to Watching").performClick()
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasContentDescription("View detailed stats"));compose.onNodeWithContentDescription("View detailed stats").performClick()
    }
    private fun openHistory(){
        if(compose.onAllNodesWithContentDescription("Back to Watching").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithContentDescription("Back to Watching").performClick()
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasText("View all"));compose.onNodeWithText("View all").performClick()
    }
    @Test fun historyGroupsEpisodesAndKeepsTheirIndividualEditActions(){
        var edited:JournalEntry?=null;val vm=TrackingViewModel()
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xFF08090B))){TrackingScreen({},vm,localEntries=rows(),continueWatching=listOf(movie.copy(progress=.5f)),onSetWatched={entry,_,_->edited=entry})}}}
        compose.onNodeWithText("History").assertIsDisplayed();compose.onNodeWithContentDescription("Back to Watching").assertIsDisplayed()
        compose.onNodeWithText("Season 1 · 9 episodes").assertIsDisplayed();compose.onNodeWithText("8.8h").assertDoesNotExist();compose.onNodeWithText("Continue watching").assertDoesNotExist()
        screenshot("tracking-history")
        compose.onNodeWithContentDescription("Expand episodes of Fixture Series").performClick()
        compose.onNodeWithContentDescription("Expand season 1 of Fixture Series").performClick()
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasContentDescription("Edit watched state for Fixture Series S1 E8"))
        compose.onNodeWithContentDescription("Edit watched state for Fixture Series S1 E8").performClick()
        compose.onNodeWithText("MARK UNWATCHED").performClick();Assert.assertEquals(8,edited?.media?.episode)
        screenshot("tracking-expanded")
    }
    @Test fun statsAndHistoryUseTheSameRuntimeAndHaveInteractiveWeekLabels(){
        val vm=TrackingViewModel()
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xFF08090B))){TrackingScreen({},vm,localEntries=rows())}}}
        compose.onNodeWithText("8.8h").assertDoesNotExist();openStats();compose.onNodeWithText("8.8h").assertExists()
        screenshot("tracking-stats-top");val monday=LocalDate.now().with(java.time.DayOfWeek.MONDAY)
        compose.onNodeWithContentDescription("Week of ${weekLabel(monday)}: 10 watched").performScrollTo().performClick()
        compose.onNodeWithText(activityRangeLabel(ActivitySlice(monday,monday.plusDays(6),10),false)).assertExists();compose.onNodeWithText("10 viewings").assertExists()
        compose.onNodeWithText("Monthly").performClick()
        compose.onNodeWithContentDescription("Month of ${java.time.YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy",java.util.Locale.ENGLISH))}: 10 watched").assertExists()
        screenshot("tracking-stats")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        openHistory();compose.onNodeWithText("Season 1 · 9 episodes").assertExists()
    }
    @Test fun closingSearchClearsItsHiddenFilter(){
        val vm=TrackingViewModel()
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xFF08090B))){TrackingScreen({},vm,localEntries=rows())}}}
        compose.onNodeWithContentDescription("Search history").performClick();compose.onNode(hasSetTextAction()).performTextInput("Movie")
        compose.onNodeWithText("Season 1 · 9 episodes").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close history search").performClick()
        compose.onNodeWithText("Season 1 · 9 episodes").assertExists();Assert.assertEquals("",vm.preferences.value.query)
    }
}
