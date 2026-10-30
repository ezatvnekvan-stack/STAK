package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.LocalReducedMotion
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import org.junit.*
import java.io.File

@org.junit.Ignore("Interactive tracking retired in 1.3.4; replaced by PassiveTrackingUiTest")
class TrackingSimplificationTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null
    private lateinit var prefix:String
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val series=MediaItem("series129",type="series",name="Fixture Series",genres=listOf("Crime","Drama"))
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="tracking129-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{reducedMotion=true;journalPreferences=com.google.gson.Gson().toJson(JournalPreferences(view="history"))}}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
    private fun capture(name:String){compose.waitForIdle();val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: context.getExternalFilesDir(null)!!.path,"tracking-1.2.9");dir.mkdirs();File(dir,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun openStats(){
        if(compose.onAllNodesWithContentDescription("Back to Watching").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithContentDescription("Back to Watching").performClick()
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasContentDescription("View detailed stats"));compose.onNodeWithContentDescription("View detailed stats").performClick()
    }
    private fun openHistory(){
        if(compose.onAllNodesWithContentDescription("Back to Watching").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithContentDescription("Back to Watching").performClick()
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasText("View all"));compose.onNodeWithText("View all").performClick()
    }
    @Test fun fourSeasonsUseOneDailyRowAndKeepIndividualEditing(){
        val rows=(1..4).flatMap{s->(1..if(s==4)12 else 11).map{trackingEntity(series,Episode(s,it,"Episode $it"),true)}}
        var changed:JournalEntry?=null;val vm=TrackingViewModel(historyLoader={emptyList()})
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(StakBackground)){TrackingScreen({},vm,localEntries=rows,onSetWatched={entry,_,_->changed=entry})}}}}
        compose.onNodeWithText("4 seasons · 45 episodes").assertIsDisplayed()
        compose.onNodeWithText("manual").assertDoesNotExist()
        compose.onNodeWithText("Estimated runtime").assertDoesNotExist()
        capture("history-four-seasons-collapsed")
        compose.onNodeWithContentDescription("Expand episodes of Fixture Series").performClick()
        compose.onNodeWithContentDescription("Expand season 4 of Fixture Series").performClick()
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasContentDescription("Edit watched state for Fixture Series S4 E3"))
        compose.onNodeWithContentDescription("Edit watched state for Fixture Series S4 E3").performClick()
        compose.onNodeWithText("manual · Today").assertExists()
        compose.onNodeWithText("MARK UNWATCHED").performClick()
        Assert.assertEquals(4,changed?.media?.season);Assert.assertEquals(3,changed?.media?.episode)
        capture("history-four-seasons-expanded")
    }
    @Test fun unknownRuntimeIsExplicitAndTechnicalPanelsAreGone(){
        val rows=listOf(trackingEntity(series,Episode(1,1,"One"),true))
        val vm=TrackingViewModel(historyLoader={emptyList()})
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(StakBackground)){TrackingScreen({},vm,localEntries=rows)}}}}
        openStats()
        compose.onNodeWithText("Estimated runtime").assertIsDisplayed();compose.onAllNodesWithText("—").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Library additions").assertDoesNotExist();compose.onNodeWithText("Movie quality").assertDoesNotExist()
        compose.onNodeWithText("Monthly").performClick();capture("stats-compact-unknown-runtime")
        compose.onNodeWithContentDescription("About tracking").performClick()
        compose.onNodeWithText("Partial history").assertExists();compose.onNodeWithText("GOT IT").performScrollTo().performClick()
    }
}
