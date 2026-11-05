@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import org.junit.*
import java.io.File

/** Isolated device fixtures: no live watcher, TorBox, Jellyfin or owner data. */
class AutoAddOverviewUiTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null
    private lateinit var prefix:String
    private fun rule(id:String,name:String)=WatchRuleModel(watchRuleIdFor("imdb:$id"),seriesImdb=id,seriesName=name,enabled=true,
        releaseTypes=listOf("WEB-DL"),resolutionMode=WatchModes.RESOLUTION_2160_ONLY,state=WatchRuleModel.STATE_SERVER_CONFIRMED)
    @Before fun setup(){
        previous=runCatching{NativeRuntime.settings}.getOrNull()
        prefix="autoadd-ux-${java.util.UUID.randomUUID()}-"
        NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix)
        WatchRules.issue.value=null;WatchRules.episodeRows.value=emptyMap()
    }
    @After fun cleanup(){
        previous?.let{NativeRuntime.settings=it}
        WatchRules.episodeRows.value=emptyMap();WatchRules.issue.value=null
        listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}
    }
    private fun seed(){
        val a=rule("tt1234567","Carrie");val b=rule("tt2345678","Common Side Effects");val c=rule("tt3456789","Future Series").copy(paused=true)
        WatchRules.store.update{WatcherDocument(connection=WatcherConnection("http://127.0.0.1:1/","synthetic-token-123456789",true),rules=listOf(a,b,c),statuses=listOf(
            WatchRuleStatus(a.ruleId,a.seriesName,false,true,1,mapOf("waiting_torbox_cache" to 1),System.currentTimeMillis()),
            WatchRuleStatus(b.ruleId,b.seriesName,false,true,1,mapOf("available_jellyfin" to 1),System.currentTimeMillis()))) }
        WatchRules.episodeRows.value=mapOf(
            a.ruleId to listOf(WatchEpisode(1,4,"waiting_torbox_cache","",System.currentTimeMillis(),System.currentTimeMillis()+1800000,"2026-10-30")),
            b.ruleId to listOf(WatchEpisode(1,10,"available_jellyfin","",System.currentTimeMillis(),0,"2026-10-29")))
        WatchRules.revision.value++
    }
    private fun capture(name:String){
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(400)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: instrumentation.targetContext.getExternalFilesDir(null)!!.absolutePath,"autoadd-ux");dir.mkdirs()
        instrumentation.uiAutomation.takeScreenshot().let{bitmap->File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
    }
    @Test fun overviewFiltersAndEpisodeDetailsAreReadable(){
        seed()
        compose.setContent{StakTheme{AutoAddOverviewScreen({},{},{},refreshEnabled=false)}}
        compose.onNodeWithText("Carrie").assertIsDisplayed()
        compose.onNodeWithText("S01E04 · Waiting for TorBox cache").assertIsDisplayed()
        capture("overview")
        compose.onNodeWithText("IN PROGRESS").performClick()
        compose.onNodeWithText("Common Side Effects").assertDoesNotExist()
        compose.onNodeWithText("Carrie").performClick()
        compose.onNodeWithText("S01E04").performClick()
        compose.onAllNodesWithText("A release must be cached before it can be added.").onLast().assertIsDisplayed()
        compose.onNodeWithText("Last check: Just now").assertExists()
        capture("episode-details")
    }
    @Test fun detailsButtonReflectsConfirmedAndPendingRules(){
        val r=rule("tt1234567","Carrie").copy(state=WatchRuleModel.STATE_SAVED_LOCAL,pendingAction="off")
        WatchRules.store.update{it.copy(rules=listOf(r))};WatchRules.revision.value++
        val show=MediaItem("tt1234567",imdbId="tt1234567",type="series",name="Carrie",overview="Fixture synopsis",year="2026")
        val vm=TitleViewModel(detailsLoader={_,_->TitleDetails(show,seasons=listOf(1))},sourceLoader={_,_,_->emptyList()})
        compose.setContent{StakTheme{TitleScreen(show,{},false,false,{},null,null,viewModel=vm)}}
        compose.onNodeWithText("Stop pending").performScrollTo().assertIsDisplayed()
        capture("details-badge")
        compose.runOnIdle {
            WatchRules.store.update{it.copy(rules=listOf(r.copy(state=WatchRuleModel.STATE_SERVER_CONFIRMED,pendingAction=null)))}
            WatchRules.revision.value++
        }
        compose.onNodeWithText("Active").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Stop pending").assertDoesNotExist()
    }
    @Test fun ruleEditorShowsAdvancedAudioAndDoesNotHideRequiredChoices(){
        val r=rule("tt1234567","Carrie").copy(atmosMode=WatchModes.MODE_MANDATORY,dvMode=WatchModes.MODE_PREFERRED)
        WatchRules.store.update{it.copy(rules=listOf(r))};WatchRules.revision.value++
        val show=MediaItem("tt1234567",imdbId="tt1234567",type="series",name="Carrie")
        compose.setContent{StakTheme{AutoAddSheet(show,listOf(1),{})}}
        compose.onNodeWithText("More options").performScrollTo().performClick()
        compose.onNodeWithText("Atmos mandatory").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dolby Vision preferred").performScrollTo().assertIsDisplayed()
        capture("quality-options")
    }
}
