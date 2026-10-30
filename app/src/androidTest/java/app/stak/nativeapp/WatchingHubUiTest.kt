package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.data.db.*
import app.stak.nativeapp.ui.components.LocalReducedMotion
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.*
import org.junit.*
import java.io.File
import java.time.*

@org.junit.Ignore("Interactive tracking retired in 1.3.4; replaced by PassiveTrackingUiTest")
class WatchingHubUiTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var db:AppDatabase;private lateinit var storage:TrackerStore;private lateinit var prefix:String;private var previous:SettingsStore?=null
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val today=LocalDate.now()
    private val show=MediaItem("hub-show",type="series",name="Started series")
    private val movie=MediaItem("hub-film",name="Manual movie")
    private fun plan(media:MediaItem)=SeriesPlan(media,"watching",listOf(Episode(1,1,"One",runtime="45 min",released=today.minusDays(2).toString()),Episode(1,2,"Two",runtime="45 min",released=today.minusDays(1).toString())),System.currentTimeMillis(),true)
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="hub132-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{reducedMotion=true};db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build();storage=TrackerStore(db)}
    @After fun cleanup(){db.close();previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
    private fun vm()=TrackingViewModel(historyLoader={emptyList()},episodeLoader={plan(it)}).also{it.attach(storage,"local")}
    @Composable private fun Frame(content:@Composable ()->Unit){StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(StakBackground)){content()}}}}
    private fun capture(name:String){compose.waitForIdle();val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:context.getExternalFilesDir(null)!!.path,"watching-1.3.3");dir.mkdirs();File(dir,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun onlyWatchingAndMonthlyCalendarArePrimaryViews(){
        val vm=vm();compose.setContent{Frame{TrackingScreen({},vm)}}
        compose.onNodeWithText("WATCHING").assertIsSelected();compose.onNodeWithText("CALENDAR").assertIsDisplayed()
        listOf("PROGRESS","HISTORY","STATS","Search tracked series").forEach{compose.onNodeWithText(it).assertDoesNotExist()}
        compose.onNodeWithText("CALENDAR").performClick();compose.onNodeWithContentDescription("Next month").assertIsDisplayed();compose.onNodeWithText("Today").assertExists();compose.onNodeWithText("Week").assertDoesNotExist();capture("monthly-calendar")
    }
    @Test fun mixedWatchingUsesActualMovieProgressAndExcludesUnstartedWatchlist()=runBlocking {
        storage.savePlan("local",plan(show));storage.pin("local",movie,true)
        val row=trackingEntity(show,Episode(1,1,"One"),true);storage.importLegacy(listOf(row))
        val unstarted=show.copy(id="future",name="Unstarted watchlist series")
        val resume=MediaItem("resume",name="Resumed film",positionMs=1_800_000,durationMs=5_400_000)
        val vm=vm();compose.setContent{Frame{TrackingScreen({},vm,localEntries=listOf(row),followed=listOf(unstarted),continueWatching=listOf(resume))}}
        compose.waitUntil(5000){vm.personalReady.value&&vm.plans.value.any{it.media.id==show.id}}
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasText("Next: S01 E02"));compose.onNodeWithText("Next: S01 E02").assertExists();compose.onNodeWithText("60 min left · 33%").assertExists();compose.onNodeWithText("Manual movie").assertExists()
        compose.onNodeWithContentDescription("Watching options for Unstarted watchlist series").assertDoesNotExist()
        compose.onNodeWithText("Next release",substring=true).assertDoesNotExist();compose.onNodeWithText("Next episode in",substring=true).assertDoesNotExist();capture("mixed-watching");Unit
    }
    @Test fun manuallyAddingAMovieDoesNotMarkItWatchedOrInventAResume()=runBlocking {
        val vm=vm();compose.setContent{Frame{TrackingScreen({},vm,followed=listOf(movie))}}
        compose.onNodeWithContentDescription("Add to Watching").performClick();compose.onNodeWithText("Manual movie").performClick()
        compose.waitUntil(5000){vm.personal.value.pins.any{it.media.id==movie.id&&it.active}}
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasContentDescription("Mark Manual movie watched"));compose.onNodeWithContentDescription("Mark Manual movie watched").assertIsDisplayed();Assert.assertTrue(db.trackingDao().getAll().isEmpty());Assert.assertEquals(movie.id,TrackerStore(db).read("local").pins.single().media.id)
        compose.onNodeWithText("0%").assertDoesNotExist();Unit
    }
    @Test fun manualMovieWatchedActionSavesHistoryAndLeavesWatching()=runBlocking {
        storage.pin("local",movie,true);val vm=vm();val repo=TrackingRepository(db.trackingDao(),storage)
        compose.setContent{Frame{val local by repo.observe().collectAsState(initial=emptyList());val scope=rememberCoroutineScope();TrackingScreen({},vm,localEntries=local,onSetWatched={e,date,played->scope.launch{repo.upsert(trackingEntity(e.media,null,played,date?.let(::watchedOn)?:System.currentTimeMillis()))}})}}
        compose.waitUntil(5000){vm.personal.value.pins.any{it.active}}
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasContentDescription("Mark Manual movie watched"));compose.onNodeWithContentDescription("Mark Manual movie watched").performClick()
        compose.waitUntil(5000){vm.personal.value.events.any{it.media.id==movie.id}&&vm.personal.value.pins.none{it.active}}
        Assert.assertTrue(db.trackingDao().getAll().single().played);compose.onNodeWithContentDescription("Watching options for Manual movie").assertDoesNotExist();Unit
    }
    @Test fun historyAndStatsOpenFromHubAndReturnWithoutExtraPrimaryTabs()=runBlocking {
        storage.importLegacy(listOf(trackingEntity(movie,null,true)))
        val vm=vm();compose.setContent{Frame{TrackingScreen({},vm)}}
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasText("View all"));compose.onNodeWithText("View all").performClick()
        compose.onNodeWithText("History").assertIsDisplayed();compose.onNodeWithText("WATCHING").assertDoesNotExist();compose.onNodeWithContentDescription("Back to Watching").performClick()
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasContentDescription("View detailed stats"));compose.onNodeWithContentDescription("View detailed stats").performClick()
        compose.onNodeWithText("Stats").assertIsDisplayed();compose.onNodeWithText("This month").assertExists();capture("nothing-stats")
        compose.onNodeWithContentDescription("Back to Watching").performClick();compose.onNodeWithContentDescription("Tracking content").performScrollToIndex(0);compose.onNodeWithText("WATCHING").assertIsSelected();Unit
    }
    @Test fun viewAllHistoryDoesNotInheritTheMonthlyStatsFilter()=runBlocking {
        val old=movie.copy(id="old-film",name="Older viewing")
        storage.importLegacy(listOf(trackingEntity(old,null,true,watchedOn(today.minusMonths(2))),trackingEntity(movie,null,true,watchedOn(today))))
        val vm=vm();compose.setContent{Frame{TrackingScreen({},vm)}}
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasContentDescription("View detailed stats"));compose.onNodeWithContentDescription("View detailed stats").performClick()
        Assert.assertEquals("this_month",vm.preferences.value.period);compose.onNodeWithContentDescription("Back to Watching").performClick()
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasText("View all"));compose.onNodeWithText("View all").performClick()
        compose.onNodeWithText("2 entries").assertExists();Assert.assertEquals("all",vm.preferences.value.period);Unit
    }
    @Test fun headerUsesThePageBackgroundAndScrollsWithContent(){
        val vm=vm();val backdrop=Color(0xFF203039)
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxWidth().height(400.dp).background(backdrop)){TrackingScreen({},vm)}}}}
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        // The old sticky header painted an opaque rectangle at this point.
        Assert.assertEquals(backdrop.toArgb(),bitmap.getPixel(bitmap.width-30,30))
        compose.onNodeWithText("MONTH IN REVIEW").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasText("View all"))
        compose.onNodeWithText("Tracking").assertIsNotDisplayed()
        capture("seamless-header")
    }
    @Test fun overviewKeepsRealCountsAndHistoryUsesHorizontalCards()=runBlocking {
        storage.importLegacy(listOf(trackingEntity(movie.copy(runtime="90 min"),null,true),trackingEntity(show,Episode(1,1,"One",runtime="45 min"),true)))
        val vm=vm();compose.setContent{Frame{TrackingScreen({},vm)}}
        compose.waitUntil(5000){vm.personal.value.events.size==2}
        compose.onNodeWithText("1 movie · 1 episode · 2h 15m est.").assertExists()
        capture("month-overview")
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasContentDescription("Recent viewing cards"))
        compose.onNodeWithContentDescription("Recent viewing cards").assertIsDisplayed()
        compose.onNodeWithText("WATCH HISTORY").assertIsDisplayed()
        capture("history-cards");Unit
    }

}
