package app.stak.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.screens.CalendarMonth
import app.stak.nativeapp.ui.screens.JournalSummary
import app.stak.nativeapp.ui.screens.SimpleTrackingActivity
import app.stak.nativeapp.data.*
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.io.File

class NothingMotionUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun dotGraphBuildsVisiblyWhileItsRealCountsRemainSelectable(){
        val today=LocalDate.of(2026,10,30)
        val entries=(0..6).flatMap{day->(0..day%3).map{index->JournalEntry(MediaItem("fixture-$day-$index",name="Film $day.$index",runtime="90"),today.minusDays(day.toLong()).atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),source="Jellyfin")}}
        compose.mainClock.autoAdvance=false
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides false){Column(Modifier.fillMaxSize().background(StakBackground).padding(20.dp)){
            JournalSummary(viewingInsights(entries,today=today));SimpleTrackingActivity(entries,"week",today,{})
        }}}}
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithContentDescription("2026-10-30: 1 viewings").assertExists()
        val first=compose.onRoot().captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(600)
        val last=compose.onRoot().captureToImage().asAndroidBitmap()
        assertFalse("The dot chart and counts should visibly build",first.sameAs(last))
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"motion-1.0.2");dir.mkdirs()
        listOf("stats-entering" to first,"stats-settled" to last).forEach{(name,bitmap)->File(dir,"$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
        compose.onNodeWithContentDescription("2026-10-28: 3 viewings").performClick();compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("3 viewings").assertIsDisplayed()
    }
    @Test fun countingKeepsTheFinalAccessibleValueAndReducedMotionStopsItImmediately(){
        var reduced by mutableStateOf(false)
        var amount:State<Float>?=null
        compose.mainClock.autoAdvance=false
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides reduced){
            amount=rememberNothingReveal("accessible-count",duration=420)
            NothingNumber("129.5h")
        }}}
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("129.5h").assertExists()
        compose.runOnIdle{assertTrue(amount!!.value<1f);reduced=true}
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle{assertEquals(1f,amount!!.value)}
        compose.onNodeWithContentDescription("129.5h").assertExists()
    }
    @Test fun reducedMotionLoadingAndSkeletonHaveNoRepeatingVisualMotion(){
        compose.mainClock.autoAdvance=false
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Column(Modifier.fillMaxWidth().background(Color.Black).padding(20.dp)){
            StakLoadingProgress();Spacer(Modifier.height(12.dp));ShimmerBox(Modifier.size(120.dp,160.dp))
        }}}}
        compose.mainClock.advanceTimeBy(64)
        val first=compose.onRoot().captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(2200)
        val second=compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue("Reduced motion must keep loaders and skeletons still",first.sameAs(second))
    }
    @Test fun rapidNavigationKeepsExactlyOneSelectedTabAndRespondsDuringMotion(){
        var current:Dest by mutableStateOf(Dest.Home)
        val tabs=listOf(NavTab(Dest.Home,"Home",Lucide.House,"home"),NavTab(Dest.Library,"Library",Lucide.Library,"library"),NavTab(Dest.Account,"Settings",Lucide.Settings,"settings"))
        compose.mainClock.autoAdvance=false
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides false){val layer=rememberGraphicsLayer();StakBottomBar(current,layer,tabs,{current=it})}}}
        compose.onNodeWithContentDescription("Settings").performClick();compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithContentDescription("Library").performClick();compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithContentDescription("Home").performClick();compose.mainClock.advanceTimeBy(1000)
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.onNode(hasContentDescription("Home") and isSelected()).assertExists()
        compose.runOnIdle{assertEquals(Dest.Home,current)}
    }
    @Test fun monthTransitionPreservesEndOfMonthAndDaySelection(){
        var selected by mutableStateOf(LocalDate.of(2026,1,31))
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides false){CalendarMonth(selected,onSelect={selected=it})}}}
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithText("February 2026").assertIsDisplayed()
        compose.runOnIdle{assertEquals(LocalDate.of(2026,2,28),selected)}
        compose.onNodeWithContentDescription("Wednesday, February 11, 2026, 0 releases").performClick()
        compose.onNodeWithContentDescription("Wednesday, February 11, 2026, 0 releases").assertIsSelected()
        compose.onNodeWithContentDescription("Previous month").performClick()
        compose.onNodeWithText("January 2026").assertIsDisplayed()
        compose.runOnIdle{assertEquals(LocalDate.of(2026,1,11),selected)}
    }
}
