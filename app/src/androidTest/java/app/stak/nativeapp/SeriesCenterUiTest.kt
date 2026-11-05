package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import org.junit.*
import java.io.File
import java.time.LocalDate

class SeriesCenterUiTest{
 @get:Rule val compose=createComposeRule()
 private val show=MediaItem("fixture",imdbId="tt1234567",type="series",name="Series fixture",jellyfinId="series")
 private val rule=WatchRuleModel("rule",seriesImdb="tt1234567",seriesName="Auto-add fixture",state=WatchRuleModel.STATE_SERVER_CONFIRMED)
 @Composable private fun Frame(content:@Composable ()->Unit){StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(StakBackground).statusBarsPadding()){content()}}}}
 private fun capture(name:String){compose.waitForIdle();Thread.sleep(250);val inst=InstrumentationRegistry.getInstrumentation();val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:inst.targetContext.getExternalFilesDir(null)!!.path,"series-center").apply{mkdirs()};File(dir,"$name.png").outputStream().use{inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}}
 @Test fun libraryAndAutoAddShareOneListAndFiltersKeepSelectionWorking(){
  var selected:String?=null
  compose.setContent{Frame{var query by remember{mutableStateOf("")};var filter by remember{mutableStateOf("all")}
   SeriesCenterList(listOf(SeriesCenterEntry("library",show),SeriesCenterEntry("rule",show.copy(id="other",imdbId="tt9999999",jellyfinId=null,name="Auto-add fixture"),rule)),emptyMap(),query,filter,null,true,{query=it},{filter=it},{selected=it.key},{},{},{},{})
  }}
  compose.onNodeWithText("Series fixture").assertIsDisplayed();compose.onNodeWithText("Auto-add fixture").assertIsDisplayed();capture("list")
  compose.onNodeWithText("LIBRARY",substring=false).performClick();compose.onNodeWithText("Auto-add fixture").assertDoesNotExist()
  compose.onNodeWithText("Series fixture").performClick();Assert.assertEquals("library",selected)
 }
 @Test fun seasonSelectionAndEpisodeActionsAreReadOnlyCallbacks(){
  val episodes=listOf(Episode(1,1,"Available episode",released="2026-11-01"),Episode(1,2,"Missing episode",released="2026-11-01"),Episode(1,3,"Future episode",released="2099-01-01"),Episode(2,1,"Another season",released="2026-11-01"))
  val snapshot=SeriesCenterSnapshot(episodes,listOf(show.copy(season=1,episode=1,jellyfinVideoHeight=2160,jellyfinId="ep1")),true)
  val rows=seriesCenterEpisodes(snapshot,emptyList(),LocalDate.of(2026,11,4));var searched:Int?=null;var opened=false
  compose.setContent{Frame{SeriesCenterDetail(SeriesCenterEntry("show",show,rule),rows,SeriesCenterState(snapshot=snapshot),null,{},{},{},{},{searched=it.episode},{opened=true})}}
  compose.onNodeWithText("In Jellyfin",substring=false).assertIsDisplayed();compose.onNodeWithText("Missing",substring=false).assertIsDisplayed();capture("episodes")
  compose.onNodeWithText("Missing episode").performClick();compose.onNodeWithText("FIND SOURCES").performScrollTo().performClick();Assert.assertEquals(2,searched)
  compose.onNodeWithText("Available episode").performScrollTo().performClick();compose.onNodeWithText("OPEN EPISODE").performScrollTo().performClick();Assert.assertTrue(opened)
  compose.onNodeWithText("SEASON 01 ▾").performScrollTo().performClick();compose.onNodeWithText("Season 2").performClick();compose.onNodeWithText("Another season").assertIsDisplayed();compose.onNodeWithText("Missing episode").assertDoesNotExist()
 }
 @Test fun largerTextShowsUnverifiedStateInsteadOfMissing(){
  val snapshot=SeriesCenterSnapshot(listOf(Episode(1,1,"Availability unknown",released="2026-11-01")),notices=listOf("Jellyfin availability could not update. Retry to verify."))
  compose.setContent{Frame{val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,1.4f)){SeriesCenterDetail(SeriesCenterEntry("show",show),seriesCenterEpisodes(snapshot,emptyList()),SeriesCenterState(snapshot=snapshot),null,{},{},{},{},{},{})}}}
  compose.onNodeWithText("Library not verified").performScrollTo().assertIsDisplayed();compose.onNodeWithText("Missing",substring=false).assertDoesNotExist();capture("large-text-unverified")
 }
 @Test fun versionsCanBeChosenWithoutChangingWatchedState(){
  val snapshot=SeriesCenterSnapshot(listOf(Episode(1,1,"Two editions",released="2026-01-01")),listOf(show.copy(jellyfinId="4k",season=1,episode=1,jellyfinVideoHeight=2160),show.copy(jellyfinId="1080",season=1,episode=1,jellyfinVideoHeight=1080)),true)
  var chosen:String?=null
  compose.setContent{Frame{SeriesCenterDetail(SeriesCenterEntry("show",show),seriesCenterEpisodes(snapshot,emptyList()),SeriesCenterState(snapshot=snapshot),null,{},{},{},{},{},{chosen=it.versions.single().jellyfinId})}}
  compose.onNodeWithText("Two editions").performClick();compose.onNodeWithText("OPEN VERSION 2").performScrollTo().performClick();Assert.assertEquals("1080",chosen);capture("versions")
 }
}
