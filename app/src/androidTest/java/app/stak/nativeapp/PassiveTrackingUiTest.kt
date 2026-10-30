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
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File
import java.time.LocalDate

class PassiveTrackingUiTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var db:AppDatabase;private lateinit var store:TrackerStore;private lateinit var prefix:String;private var previous:SettingsStore?=null
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val today=LocalDate.now()
    private val movie=MediaItem("passive-movie",name="Server movie",jellyfinId="movie",jellyfinUserDataKnown=true,positionMs=1_800_000,durationMs=5_400_000,progress=1f/3)
    private val show=MediaItem("passive-series",name="Server series",type="series",jellyfinId="series",jellyfinUserDataKnown=true)
    private val scope get()=jellyfinScope()
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="passive134-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{reducedMotion=true;jellyfinUrl="http://127.0.0.1:9";jellyfinUserId="fixture-user";jellyfinToken="test-only"};db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build();store=TrackerStore(db)}
    @After fun cleanup(){db.close();previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
    private fun vm()=TrackingViewModel(historyLoader={emptyList()},episodeLoader={SeriesPlan(it,episodes=listOf(Episode(1,1,"One",released=today.minusDays(2).toString()),Episode(1,2,"Two",released=today.minusDays(1).toString())),completeMetadata=true)}).also{it.attach(store,scope)}
    @Composable private fun Frame(content:@Composable ()->Unit){StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(Color(0xFF18232B))){content()}}}}
    private fun capture(name:String){compose.waitForIdle();val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:context.getExternalFilesDir(null)!!.path,"passive-1.3.4");dir.mkdirs();File(dir,"$name.png").outputStream().use{compose.onAllNodes(isRoot())[compose.onAllNodes(isRoot()).fetchSemanticsNodes().lastIndex].captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun absentWrites(){listOf("MARK WATCHED","MARK UNWATCHED","WATCH AGAIN","EDIT WATCHED DATE","LOG VIEWING","REMOVE HISTORY ENTRY","Add to Watching").forEach{compose.onAllNodes(hasText(it,ignoreCase=true)or hasContentDescription(it,ignoreCase=true)).assertCountEquals(0)}}
    @Test fun movieDetailsKeepTrailerWatchlistAndProgressWithoutWatchedActions(){var writes=0;compose.setContent{Frame{TitleManagementPanel(movie,true,true,false,false,true,false,null,1,false,0,0,true,null,{},{},{writes++},{writes++})}};compose.onNodeWithText("33%").assertIsDisplayed();compose.onNodeWithText("TRAILER").assertIsDisplayed();compose.onNodeWithText("WATCHLIST").assertIsDisplayed();absentWrites();Assert.assertEquals(0,writes);capture("movie-details")}
    @Test fun seriesDetailsDisplayCompletionWithoutEpisodeOrSeasonMutation(){var writes=0;compose.setContent{Frame{TitleManagementPanel(show,true,true,false,false,true,true,"S01 E01",1,true,2,2,true,null,{},{},{writes++},{writes++})}};compose.onNodeWithText("2 / 2 watched").assertIsDisplayed();compose.onNodeWithText("UNWATCH SEASON").assertDoesNotExist();compose.onNodeWithText("UNWATCH S01 E01").assertDoesNotExist();absentWrites();Assert.assertEquals(0,writes);capture("series-details")}
    @Test fun episodeSheetIsReadOnlyAndKeepsSourceSearch(){var writes=0;compose.setContent{Frame{EpisodeDetailsSheet(show,Episode(1,2,"Two",watched=true),{}, {writes++},{})}};compose.onNodeWithText("Watched on Jellyfin").assertIsDisplayed();absentWrites();Assert.assertEquals(0,writes);capture("episode-details")}
    @Test fun posterMenuHasNoWatchedMutation(){var writes=0;compose.setContent{Frame{CompositionLocalProvider(LocalPosterActions provides PosterActions({false},{false},{true},{},{_,_->writes++})){PosterContextMenu(movie,true,{},{})}}};compose.onNodeWithText("View details").assertIsDisplayed();compose.onNodeWithText("Add to watchlist").assertIsDisplayed();absentWrites();Assert.assertEquals(0,writes)}
    @Test fun automaticWatchingIgnoresManualPinsAndCannotMarkFromRows()=runBlocking{
        store.pin(scope,MediaItem("manual",name="Old manual pin"),true);val vm=vm()
        compose.setContent{Frame{TrackingScreen({},vm,continueWatching=listOf(movie))}}
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasText("60 min left · 33%"));compose.onNodeWithText("60 min left · 33%").assertIsDisplayed();compose.onNodeWithText("Old manual pin").assertDoesNotExist();compose.onNodeWithContentDescription("Mark Server movie watched").assertDoesNotExist();absentWrites();capture("automatic-watching");Unit
    }
    @Test fun currentServerCompletionRemovesAStaleResumeWithoutManualWrite(){val vm=vm();var library by mutableStateOf(emptyList<MediaItem>());compose.setContent{Frame{TrackingScreen({},vm,knownLibrary=library,continueWatching=listOf(movie))}};compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasText("60 min left · 33%"));compose.runOnIdle{library=listOf(movie.copy(watched=true,positionMs=0,progress=1f))};compose.waitForIdle();compose.onNodeWithContentDescription("Watching options for Server movie").assertDoesNotExist();Assert.assertTrue(runBlocking{db.trackingDao().getAll().isEmpty()})}
    @Test fun historyStatsAndArchiveAreSeparateAndReadOnly()=runBlocking{
        store.log(scope,MediaItem("old",name="Earlier manual movie"),watchedOn(today));store.remote(scope,listOf(JournalEntry(movie.copy(watched=true),watchedOn(today),true,"Jellyfin",System.currentTimeMillis(),scope)))
        val vm=vm();compose.setContent{Frame{TrackingScreen({},vm)}};compose.waitUntil(5000){vm.personal.value.events.size>=2}
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasText("View all"));compose.onNodeWithText("View all").performClick();compose.onNodeWithText("1 entry").assertExists();compose.onNodeWithText("Earlier manual movie").assertDoesNotExist();absentWrites();capture("history")
        compose.onNodeWithContentDescription("Back to Watching").performClick();compose.onNodeWithContentDescription("Tracking content").performScrollToIndex(0);compose.onNodeWithContentDescription("View detailed stats").performClick();compose.onNodeWithText("Stats").assertIsDisplayed();compose.onNodeWithText("ACTIVITY").assertExists();compose.onNodeWithText("Weekly").assertDoesNotExist();compose.onNodeWithText("Monthly").assertDoesNotExist();compose.onNodeWithContentDescription("Activity week").assertDoesNotExist();capture("simple-stats")
        compose.onNodeWithContentDescription("Back to Watching").performClick();compose.onNodeWithContentDescription("Tracking content").performScrollToIndex(0);compose.onNodeWithContentDescription("Tracking options").performClick();compose.onNodeWithText("Earlier records").performClick();compose.onNodeWithText("Earlier manual movie").assertExists();absentWrites();capture("archive");Unit
    }
    @Test fun calendarRemainsMonthlyAndSeamless(){val vm=vm();compose.setContent{Frame{TrackingScreen({},vm)}};compose.onNodeWithText("CALENDAR").performClick();compose.onNodeWithContentDescription("Next month").assertIsDisplayed();compose.onNodeWithText("Today").assertExists();compose.onNodeWithText("Week").assertDoesNotExist();absentWrites();capture("calendar")}
}
