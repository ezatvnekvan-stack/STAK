@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.StakTheme
import org.junit.*
import java.io.File

class DetailsLayoutUiTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null
    private lateinit var prefix:String
    @Before fun setup(){
        previous=runCatching{NativeRuntime.settings}.getOrNull()
        prefix="details-layout-${java.util.UUID.randomUUID()}-"
        NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix)
        NativeRuntime.settings.hasOnboarded=true
    }
    @After fun cleanup(){
        previous?.let{NativeRuntime.settings=it}
        listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}
    }
    private fun capture(name:String){
        compose.waitForIdle()
        val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.absolutePath,"details-1.3.8")
        dir.mkdirs()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun dockNeverOverlapsContent(){
        val content=compose.onNodeWithTag("detail-content").fetchSemanticsNode().boundsInRoot
        val dock=compose.onNodeWithTag("detail-source-dock").fetchSemanticsNode().boundsInRoot
        Assert.assertTrue("Dock must reserve its own space",content.bottom<=dock.top+1f)
        compose.onNodeWithText("FIND SOURCES").assertIsDisplayed()
    }
    @Test fun movieProgressAndDockRemainUsableWhileScrolling(){
        val movie=MediaItem("layout-movie",name="Movie Details",overview="A film synopsis with enough text to expand. ".repeat(20),progress=.4f,positionMs=2400000,durationMs=6000000,year="2026",genres=listOf("Drama"))
        val vm=TitleViewModel(detailsLoader={_,_->TitleDetails(movie,crew=listOf(TitlePerson("one","Director One","Director"),TitlePerson("two","Writer Two","Writer"),TitlePerson("three","Producer Three","Producer")))},sourceLoader={_,_,_->emptyList()})
        var toggles=0
        compose.setContent{StakTheme{TitleScreen(movie,{},false,false,{toggles++},null,null,viewModel=vm)}}
        compose.onNodeWithText("WATCHLIST").performScrollTo().performClick()
        Assert.assertEquals(1,toggles)
        compose.onNodeWithText("60 min left").assertExists()
        compose.onNodeWithText("40%").assertExists()
        compose.onNodeWithText("MARK WATCHED").assertDoesNotExist()
        compose.onNodeWithText("MEDIA QUALITY").assertDoesNotExist()
        dockNeverOverlapsContent();capture("movie-actions")
        compose.onNodeWithText("Read more").performClick()
        compose.onNodeWithTag("detail-content").performScrollToNode(hasText("CREATORS"))
        compose.onNodeWithText("View all 3").performScrollTo().performClick()
        compose.onNodeWithText("Producer Three").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("detail-header").assertIsDisplayed()
        val header=compose.onNodeWithTag("detail-header").fetchSemanticsNode().boundsInRoot
        val content=compose.onNodeWithTag("detail-content").fetchSemanticsNode().boundsInRoot
        Assert.assertTrue("Header cannot cover the content",header.bottom<=content.top+1f)
        dockNeverOverlapsContent();capture("movie-creators-header")
    }
    @Test fun seriesProgressFollowsSelectedSeasonAndEpisodeSheetStaysReadOnly(){
        val show=MediaItem("layout-series",type="series",name="Series Details",overview="Series synopsis",year="2026")
        val episodes=listOf(Episode(1,1,"Pilot",overview="Pilot synopsis",runtime="52",watched=true),Episode(1,2,"Second chapter",runtime="48"),Episode(2,1,"New season",overview="New season synopsis",runtime="55"))
        val vm=TitleViewModel(detailsLoader={_,_->TitleDetails(show,episodes=episodes,seasons=listOf(1,2))},sourceLoader={_,_,_->emptyList()})
        compose.setContent{StakTheme{TitleScreen(show,{},false,false,{},null,null,viewModel=vm)}}
        compose.onNodeWithText("S01").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 / 2 watched").performScrollTo().assertIsDisplayed()
        val season=compose.onNodeWithText("S01").fetchSemanticsNode().boundsInRoot
        val progress=compose.onNodeWithText("1 / 2 watched").fetchSemanticsNode().boundsInRoot
        Assert.assertTrue("Season progress belongs below selector",progress.top>=season.bottom)
        dockNeverOverlapsContent();capture("series-episodes")
        compose.onNodeWithText("S02").performClick()
        compose.onNodeWithText("0 / 1 watched").assertExists()
        compose.onNodeWithText("New season").performScrollTo().performClick()
        compose.onNodeWithText("New season synopsis").assertExists()
        compose.onNodeWithText("MARK WATCHED").assertDoesNotExist()
        compose.onNodeWithText("MARK UNWATCHED").assertDoesNotExist()
        capture("series-episode-sheet")
    }
}
