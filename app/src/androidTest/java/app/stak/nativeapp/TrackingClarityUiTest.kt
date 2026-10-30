@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File
import java.time.*

@org.junit.Ignore("Interactive tracking retired in 1.3.4; replaced by PassiveTrackingUiTest")
class TrackingClarityUiTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null;private lateinit var prefix:String
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val today=LocalDate.of(2026,10,29)
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="clarity131-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{reducedMotion=true}}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
    @Composable private fun Frame(content:@Composable ()->Unit){StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(StakBackground)){content()}}}}
    private fun capture(name:String){compose.waitForIdle();val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:context.getExternalFilesDir(null)!!.path,"tracking-1.3.1");dir.mkdirs();File(dir,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun rows()=listOf(
        JournalEntry(MediaItem("older",name="Previous week movie"),LocalDate.of(2026,10,20).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),eventId="one"),
        JournalEntry(MediaItem("recent",name="Wednesday movie"),LocalDate.of(2026,10,28).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),eventId="two")
    )
    private fun openStats(){
        if(compose.onAllNodesWithContentDescription("Back to Watching").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithContentDescription("Back to Watching").performClick()
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasContentDescription("View detailed stats"));compose.onNodeWithContentDescription("View detailed stats").performClick()
    }
    private fun openHistory(){
        if(compose.onAllNodesWithContentDescription("Back to Watching").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithContentDescription("Back to Watching").performClick()
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasText("View all"));compose.onNodeWithText("View all").performClick()
    }
    @Test fun compactProgressKeepsNextEpisodeActionWithinOneHundredFortyTwoDp(){
        val show=MediaItem("compact",name="Fixture Series",type="series")
        val p=seriesProgress(SeriesPlan(show,"watching",listOf(Episode(1,1,"The opening",released="2026-10-28")),1,true),emptyList(),today)
        var marked=false
        compose.setContent{Frame{Column(Modifier.padding(16.dp)){StakPageHeader("Tracking");WatchingTitleRow(WatchingRow(show,series=p,episode=p.next),{},{marked=true},{})}}}
        val rect=compose.onNodeWithTag("watching-title-series:id:compact").getUnclippedBoundsInRoot()
        Assert.assertTrue("Progress must remain compact: ${rect.bottom-rect.top}",rect.bottom-rect.top<=94.dp)
        compose.onNodeWithContentDescription("Mark Fixture Series S1 E1 watched").performClick();Assert.assertTrue(marked);capture("compact-progress")
    }
    @Test fun weeklyDatesNavigateAndTappingADayOpensItsActualTitles(){
        val rows=rows();var opened:MediaItem?=null
        compose.setContent{Frame{Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())){TrackingActivity(trackingActivity(rows,false,"all",today),false,{},rows,{opened=it},today)}}}
        compose.onNodeWithText("Oct 26 – Nov 1, 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Wednesday, October 28, 2026: 1 viewings").performClick()
        compose.onNodeWithText("Wednesday movie").performScrollTo().performClick();Assert.assertEquals("recent",opened?.id);capture("weekly-day-detail")
        compose.onNodeWithContentDescription("Previous week").performScrollTo().performClick();compose.onNodeWithText("Oct 19 – Oct 25, 2026").assertExists()
        compose.onNodeWithContentDescription("Next week").performClick();compose.onNodeWithText("Oct 26 – Nov 1, 2026").assertExists()
    }
    @Test fun monthlyPillAndDayDropdownExposeAnExplicitDatedSelection(){
        val rows=rows();var monthly by mutableStateOf(false)
        compose.setContent{Frame{Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())){TrackingActivity(trackingActivity(rows,monthly,"all",today),monthly,{monthly=it},rows,{},today)}}}
        compose.onNodeWithText("Monthly").performClick();compose.onNodeWithText("Monthly").assertIsSelected()
        compose.onNodeWithContentDescription("Activity day").performScrollTo().performClick();compose.onNodeWithText("Wed, Oct 28 · 1").performClick()
        compose.onNodeWithText("Wednesday movie").performScrollTo().assertIsDisplayed();capture("monthly-day-detail")
    }
    @Test fun trackingHasNoPendingOrFetchErrorBannerAndPeriodIsAnOutlinedButton(){
        NativeRuntime.settings.apply{jellyfinUrl="http://127.0.0.1:1";jellyfinToken="synthetic";jellyfinUserId="user"}
        val vm=TrackingViewModel(historyLoader={throw java.io.IOException()},episodeLoader={SeriesPlan(it)})
        val pending="Some watched updates are waiting to sync. Retry when Jellyfin is available."
        compose.setContent{Frame{TrackingScreen({},vm,syncNotice=pending)}}
        compose.waitUntil(5000){vm.error.value!=null}
        compose.onNodeWithText(pending).assertDoesNotExist();compose.onNodeWithText("RETRY SYNC").assertDoesNotExist()
        openHistory();compose.onNodeWithText("RETRY ACTIVITY").assertDoesNotExist()
        compose.onNodeWithContentDescription("Tracking period").performClick();compose.onNodeWithText("Last 7 days").performClick();Assert.assertEquals("week",vm.preferences.value.period)
    }
    @Test fun homeKeepsResumeCardWithoutShowingPendingSyncText(){
        val vm=HomeViewModel(feedLoader={HomeFeed()})
        val pending="Some watched updates are waiting to sync. Retry when Jellyfin is available."
        val media=MediaItem("resume",name="Fixture resume",progress=.4f,positionMs=2400000,durationMs=6000000,jellyfinId="resume")
        compose.setContent{Frame{HomeScreen({_,_->},{},{},{},{},{},continueWatching=listOf(media),jellyfinConnected=true,continueError=pending,sharedScope=null,animatedScope=null,reducedMotion=true,viewModel=vm)}}
        compose.waitUntil(5000){vm.feed.value!=null}
        compose.onNodeWithText("Fixture resume").assertExists();compose.onNodeWithText(pending).assertDoesNotExist();compose.onNodeWithText("RETRY CONTINUE WATCHING").assertDoesNotExist();capture("quiet-home-resume")
    }
}
