@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.data.db.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.StakTheme
import com.google.gson.Gson
import kotlinx.coroutines.*
import org.junit.*
import java.io.File
import java.time.*
import java.util.Locale

@org.junit.Ignore("Interactive tracking retired in 1.3.4; replaced by PassiveTrackingUiTest")
class JournalCalendarExperienceTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null;private lateinit var prefix:String;private lateinit var db:AppDatabase;private lateinit var locale:Locale
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="journal-ui-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix).apply{journalPreferences=com.google.gson.Gson().toJson(JournalPreferences(view="history"))};db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build();locale=Locale.getDefault()}
    @After fun cleanup(){db.close();Locale.setDefault(locale);previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}}
    private fun capture(name:String){compose.waitForIdle();val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.absolutePath,"preview-8");dir.mkdirs();File(dir,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    private val movie=MediaItem("tmdb:1",name="Fixture Movie",year="2026",runtime="120 min")
    private val series=MediaItem("tmdb:2",type="series",name="Fixture Series",year="2025",runtime="45 min")
    private fun openStats(){
        if(compose.onAllNodesWithContentDescription("Back to Watching").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithContentDescription("Back to Watching").performClick()
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasContentDescription("View detailed stats"));compose.onNodeWithContentDescription("View detailed stats").performClick()
    }
    private fun openHistory(){
        if(compose.onAllNodesWithContentDescription("Back to Watching").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithContentDescription("Back to Watching").performClick()
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasText("View all"));compose.onNodeWithText("View all").performClick()
    }
    @Test fun calendarStaysEnglishWithAHungarianDeviceLocaleAndOpensTheActualEpisode(){
        Locale.setDefault(Locale.forLanguageTag("hu-HU"));NativeRuntime.settings.journalPreferences=Gson().toJson(JournalPreferences(view="calendar",date="2026-10-26"))
        val event=ReleaseEvent(series,LocalDate.of(2026,10,26),"The episode",2,3);var opened:MediaItem?=null
        val calendar=CalendarViewModel(loader={_,_,_,_->ReleaseFeed(listOf(event),emptyList(),1)})
        val vm=TrackingViewModel(historyLoader={emptyList()})
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xff090909))){TrackingScreen({opened=it},vm,followed=listOf(series),calendarViewModel=calendar)}}}
        compose.onNodeWithText("October 2026").assertExists();compose.onNodeWithText("MON").assertExists();compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithText("S2 E3 · The episode").performScrollTo().assertIsDisplayed();capture("calendar-english")
        compose.onNodeWithText("Fixture Series").performClick();Assert.assertEquals(2,opened?.season);Assert.assertEquals(3,opened?.episode)
    }
    @Test fun changingMonthsTodayAndCalendarFiltersUseRealSelectedDates(){
        NativeRuntime.settings.journalPreferences=Gson().toJson(JournalPreferences(view="calendar",date="2026-10-26"))
        val calendar=CalendarViewModel(loader={_,_,_,_->ReleaseFeed(emptyList(),emptyList(),0)});val vm=TrackingViewModel()
        compose.setContent{StakTheme{TrackingScreen({},vm,calendarViewModel=calendar)}}
        compose.onNodeWithContentDescription("Next month").performClick();compose.onNodeWithText("November 2026").assertExists()
        compose.onNodeWithText("Today").performClick();Assert.assertEquals(LocalDate.now().toString(),vm.preferences.value.date)
        compose.onNodeWithContentDescription("Calendar filters").performClick();compose.onNodeWithText("All").performClick();compose.onNodeWithText("Movies").performClick()
        compose.onNodeWithText("Monday").performClick();compose.onNodeWithText("Sunday").performClick();compose.onNodeWithText("DONE").performClick()
        Assert.assertEquals("movie",vm.preferences.value.calendarType);Assert.assertEquals(0,vm.preferences.value.weekStart)
        compose.onNodeWithText("SUN").assertExists()
    }
    @Test fun calendarFailureShowsRetryRatherThanInventedReleases(){
        NativeRuntime.settings.journalPreferences=Gson().toJson(JournalPreferences(view="calendar",date="2026-10-26"))
        val calendar=CalendarViewModel(loader={_,_,_,_->throw java.io.IOException("synthetic")})
        compose.setContent{TrackingScreen({},TrackingViewModel(),followed=listOf(series),calendarViewModel=calendar)}
        compose.onNodeWithText("RETRY CALENDAR").performScrollTo().assertIsDisplayed();compose.onNodeWithText("Fixture Series").assertDoesNotExist()
    }
    @Test fun activityCountsEpisodesSeparatelyAndSupportsDateEditingAndUndo(){
        val now=System.currentTimeMillis();val rows=listOf(trackingEntity(movie,null,true,now),trackingEntity(series,Episode(1,1,"One"),true,now),trackingEntity(series,Episode(1,2,"Two"),true,now))
        val vm=TrackingViewModel();var date:LocalDate?=null;var changed:JournalEntry?=null;var played=true
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xff090909))){TrackingScreen({},vm,localEntries=rows,onSetWatched={entry,value,state->changed=entry;date=value;played=state})}}}
        openStats()
        compose.onNodeWithText("Episodes").assertExists();compose.onNodeWithText("2").assertExists();compose.onNodeWithText("3.5h").assertExists()
        openHistory()
        capture("tracking-activity")
        compose.onNodeWithContentDescription("Edit watched state for Fixture Movie").performScrollTo().performClick();compose.onNodeWithText("EDIT WATCHED DATE").performClick();compose.onNodeWithText("Watched date").assertExists();compose.onNodeWithText("SAVE DATE").performClick()
        Assert.assertEquals(movie.mediaKey,changed?.media?.mediaKey);Assert.assertEquals(LocalDate.now(),date);Assert.assertTrue(played)
        compose.onNodeWithContentDescription("Edit watched state for Fixture Movie").performScrollTo().performClick();compose.onNodeWithText("MARK UNWATCHED").performClick();Assert.assertFalse(played)
    }
    @Test fun watchlistFiltersRemovalAndPreferencesPersistWithoutAKey()=runBlocking {
        val repo=WatchlistRepository(db.watchlistDao());repo.toggle(movie);repo.toggle(series)
        val vm=WatchlistViewModel(db.watchlistDao(),repo)
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xff090909))){androidx.compose.runtime.CompositionLocalProvider(app.stak.nativeapp.ui.components.LocalPosterActions provides app.stak.nativeapp.ui.components.PosterActions({true},{false},{false},{vm.toggle(it)},{_,_->})){WatchlistScreen({_,_->},{},vm,null,null,watchedIds=setOf(movie.mediaKey))}}}}
        compose.onNodeWithText("Fixture Movie").assertExists();capture("watchlist")
        compose.onNodeWithContentDescription("Watchlist filters").performClick();compose.onNodeWithText("Hide watched").performClick();compose.onNodeWithText("DONE").performClick()
        compose.onNodeWithText("Fixture Movie").assertDoesNotExist();compose.onNodeWithText("Fixture Series").assertExists()
        compose.onNodeWithContentDescription("Remove Fixture Series").assertDoesNotExist();compose.onNodeWithText("Fixture Series").performTouchInput{longClick()};compose.onNodeWithText("Remove from watchlist").performClick()
        compose.waitUntil(5000){vm.items.value.size==1}
        Assert.assertTrue(Gson().fromJson(NativeRuntime.settings.watchlistPreferences,WatchlistPreferences::class.java).hideWatched)
    }
    @Test fun cacheOnlyUsesConfirmedResultsAndExcludesFailedChecks()=runBlocking {
        NativeRuntime.settings.torboxKey="synthetic-only";val repo=WatchlistRepository(db.watchlistDao());repo.toggle(movie);repo.toggle(series)
        var checks=0
        val vm=WatchlistViewModel(db.watchlistDao(),repo,cacheLoader={item,_->checks++;if(item.isSeries)throw java.io.IOException("synthetic") else true})
        compose.setContent{WatchlistScreen({_,_->},{},vm,null,null)}
        compose.waitUntil(5000){vm.items.value.size==2};Assert.assertEquals(0,checks)
        compose.runOnIdle{vm.preferences(vm.preferences.value.copy(cachedOnly=true))}
        compose.waitUntil(5000){!vm.checking.value&&vm.cacheFailures.value==1}
        compose.onNodeWithText("Fixture Movie").assertExists();compose.onNodeWithText("Fixture Series").assertDoesNotExist();compose.onNodeWithText("RETRY CACHE CHECKS").assertExists();Unit
    }
}
