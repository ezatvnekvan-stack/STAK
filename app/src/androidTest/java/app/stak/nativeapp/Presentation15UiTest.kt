package app.stak.nativeapp

import androidx.compose.ui.test.*

import androidx.compose.ui.test.junit4.createComposeRule

import androidx.compose.foundation.layout.*

import androidx.compose.ui.Modifier

import androidx.compose.ui.unit.dp

import androidx.test.platform.app.InstrumentationRegistry

import app.stak.nativeapp.data.*

import app.stak.nativeapp.ui.screens.*

import app.stak.nativeapp.ui.theme.*

import org.junit.Test

import org.junit.Rule

import org.junit.Assert.*

import java.time.*

import java.io.File

class Presentation15UiTest{

 @get:Rule val compose=createComposeRule()

 private fun capture(name:String){compose.waitForIdle();val i=InstrumentationRegistry.getInstrumentation();val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:i.targetContext.getExternalFilesDir(null)!!.absolutePath,"presentation15");dir.mkdirs();i.uiAutomation.takeScreenshot().let{b->File(dir,"$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}}

 @Test fun cachedCardExpandsAndAddsOnlyByExplicitButton(){

  var adds=0

  val s=SourceRelease("VisionQuest.S01E01.2160p.WEB-DL.DV.HDR10+.Atmos",resolution="2160p",hdr=true,dolbyVision=true,atmos=true,webdl=true,sizeBytes=5500000000)

  compose.setContent{StakTheme{Box(Modifier.width(330.dp)){SourceReleaseCard(s,true,false,false,{adds++},{},true,true,1,8,{})}}}

  compose.onNodeWithText("DOLBY VISION").assertExists();compose.onNodeWithText("HDR10+").assertExists()

  compose.onNodeWithText("4K · WEB-DL").assertDoesNotExist()
  compose.onNodeWithText(s.name).performClick()

  compose.onNodeWithText("RELEASE DETAILS").assertExists();assertEquals(0,adds)

  capture("source-card")

  compose.onNodeWithContentDescription("Add cached release").performClick();assertEquals(1,adds)

 }

 @Test fun unverifiedCardCannotAdd(){

  compose.setContent{StakTheme{SourceReleaseCard(SourceRelease("Fixture"),false,false,false,{error("must not add")},{},false,false,null,0,{})}}

  compose.onNodeWithContentDescription("Add cached release").assertIsNotEnabled();compose.onNodeWithText("Cache unconfirmed").assertExists()

 }

 @Test fun activityDateSelectionAndDetails(){

  val today=LocalDate.of(2026,11,10);var opened=false

  val e=JournalEntry(MediaItem("fixture",type="movie",name="Fixture movie",runtime="120 min"),today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),source="Jellyfin")

  compose.setContent{StakTheme{WatchingActivityGrid(listOf(e),today,{}, {opened=true})}}

  compose.onNodeWithContentDescription("2026-11-10: 1 viewings").performClick();compose.onNodeWithText("Fixture movie").assertIsDisplayed()

  capture("activity-grid")

  compose.onNodeWithText("Details ›").performClick();assertTrue(opened)

 }

 @Test fun sourceCardRemainsUsableAtLargeFontScale(){
  compose.setContent{
   androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f,1.4f)){
    StakTheme{Box(Modifier.width(300.dp)){SourceReleaseCard(SourceRelease("A.long.release.name.2160p.DV.Atmos",resolution="2160p",dolbyVision=true,atmos=true),true,false,false,{},{},true,false,null,0,{})}}
   }
  }
  compose.onNodeWithContentDescription("Add cached release").assertIsDisplayed();compose.onNodeWithText("DOLBY VISION").assertExists()
  capture("source-large-text")
 }

}
