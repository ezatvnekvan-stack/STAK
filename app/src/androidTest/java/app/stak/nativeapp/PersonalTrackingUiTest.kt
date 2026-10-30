package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
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
import java.time.LocalDate

@org.junit.Ignore("Interactive tracking retired in 1.3.4; replaced by PassiveTrackingUiTest")
class PersonalTrackingUiTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null;private lateinit var prefix:String;private lateinit var db:AppDatabase;private lateinit var storage:TrackerStore
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val show=MediaItem("show130",type="series",name="Fixture Series",genres=listOf("Drama","Crime"))
    private val movie=MediaItem("movie130",name="Fixture Movie",runtime="90 min",genres=listOf("Drama"))
    private val today=LocalDate.now()
    private fun plan()=SeriesPlan(show,episodes=listOf(Episode(1,1,"Opening",runtime="45 min",released=today.minusDays(2).toString()),Episode(1,2,"Second",runtime="45 min",released=today.minusDays(1).toString()),Episode(1,3,"Next",runtime="45 min",released=today.plusDays(3).toString())),refreshedAt=System.currentTimeMillis(),completeMetadata=true)
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="personal130-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{reducedMotion=true};db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build();storage=TrackerStore(db)}
    @After fun cleanup(){db.close();previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
    private fun capture(name:String){compose.waitForIdle();val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:context.getExternalFilesDir(null)!!.path,"tracking-1.3.0");dir.mkdirs();File(dir,"$name.png").outputStream().use{compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun vm()=TrackingViewModel(historyLoader={emptyList()},episodeLoader={plan()}).also{it.attach(storage,"local")}
    @Composable private fun Frame(vm:TrackingViewModel,rows:List<TrackingEntity> = emptyList(),onSet:(JournalEntry,LocalDate?,Boolean)->Unit={_,_,_->}){StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(StakBackground)){TrackingScreen({},vm,localEntries=rows,followed=listOf(show),onSetWatched=onSet)}}}}
    private fun openStats(){
        if(compose.onAllNodesWithContentDescription("Back to Watching").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithContentDescription("Back to Watching").performClick()
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasContentDescription("View detailed stats"));compose.onNodeWithContentDescription("View detailed stats").performClick()
    }
    private fun openHistory(){
        if(compose.onAllNodesWithContentDescription("Back to Watching").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithContentDescription("Back to Watching").performClick()
        compose.onNodeWithContentDescription("Tracking content").performScrollToNode(hasText("View all"));compose.onNodeWithText("View all").performClick()
    }
    @Test fun progressDefaultsToNextActualUnwatchedEpisodeAndStatusPersists()=runBlocking {
        storage.savePlan("local",plan());val rows=listOf(trackingEntity(show,Episode(1,1,"Opening"),true));storage.importLegacy(rows)
        val vm=vm();var changed:JournalEntry?=null
        compose.setContent{Frame(vm,rows){entry,_,_->changed=entry}}
        compose.onNodeWithText("WATCHING").assertIsSelected();compose.onNodeWithText("Next: S01 E02").assertIsDisplayed();capture("01-progress")
        compose.onNodeWithContentDescription("Mark Fixture Series S1 E2 watched").performClick();Assert.assertEquals(2,changed?.media?.episode)
        compose.onNodeWithContentDescription("Watching options for Fixture Series").performClick();compose.onNodeWithText("On hold").performClick()
        compose.waitUntil(5000){vm.personal.value.plans.any{it.status=="on_hold"}}
        Assert.assertEquals("on_hold",TrackerStore(db).read("local").plans.single().status);capture("02-progress-on-hold");Unit
    }
    @Test fun seasonSheetUsesKnownEpisodesAndDisablesFutureQuickWatched()=runBlocking {
        storage.savePlan("local",plan());val vm=vm()
        compose.setContent{Frame(vm)}
        compose.onNodeWithText("Fixture Series").performClick()
        compose.onNodeWithContentDescription("Mark watched S1 E3").assertIsNotEnabled();compose.onNodeWithContentDescription("Mark watched S1 E2").assertIsEnabled();capture("03-seasons");Unit
    }
    @Test fun historyShowsRewatchesStatsCountThemAndDeletingEntryKeepsState()=runBlocking {
        val row=trackingEntity(movie,null,true,watchedOn(today.minusDays(1)))
        TrackingRepository(db.trackingDao(),storage).upsert(row);storage.log("local",movie,watchedOn(today),"rewatch")
        val vm=vm();compose.setContent{Frame(vm,listOf(row))}
        openHistory();compose.onNodeWithText("2 entries").assertExists();capture("04-history-rewatch")
        compose.onAllNodesWithContentDescription("Edit watched state for Fixture Movie")[0].performClick()
        compose.onNodeWithText("REMOVE HISTORY ENTRY").performClick();compose.onNodeWithText("REMOVE ENTRY").performClick()
        compose.waitUntil(5000){vm.personal.value.events.size==1};Assert.assertTrue(db.trackingDao().getAll().single().played)
        openStats();compose.onNodeWithText("1.5h").assertExists();capture("05-stats");Unit
    }
    @Test fun ratingIsPersonalAndPersistsWithoutChangingWatchedState()=runBlocking {
        storage.savePlan("local",plan());val vm=vm();compose.setContent{Frame(vm)}
        compose.onNodeWithContentDescription("Watching options for Fixture Series").performClick();compose.onNodeWithText("YOUR RATING").performClick();compose.onNodeWithText("8").performClick()
        compose.waitUntil(5000){vm.personal.value.ratings.any{it.value==8}}
        Assert.assertTrue(db.trackingDao().getAll().isEmpty());Assert.assertEquals(8,TrackerStore(db).read("local").ratings.single().value);Unit
    }
    @Test fun removingLastHistoryEventDoesNotResurrectLegacyState()=runBlocking {
        val row=trackingEntity(movie,null,true);storage.importLegacy(listOf(row));val vm=vm();compose.setContent{Frame(vm,listOf(row))}
        openHistory();compose.onNodeWithContentDescription("Edit watched state for Fixture Movie").performClick();compose.onNodeWithText("REMOVE HISTORY ENTRY").performClick();compose.onNodeWithText("REMOVE ENTRY").performClick()
        compose.waitUntil(5000){vm.personal.value.events.isEmpty()};compose.onNodeWithText("0 entries").assertExists();Unit
    }
    @Test fun watchAgainUsesDatePickerAndCreatesASeparateViewing()=runBlocking {
        val row=trackingEntity(movie,null,true,watchedOn(today.minusDays(1)));storage.importLegacy(listOf(row));val vm=vm();var date:LocalDate?=null
        compose.setContent{Frame(vm,listOf(row)){_,value,_->date=value}}
        openHistory();compose.onNodeWithContentDescription("Edit watched state for Fixture Movie").performClick();compose.onNodeWithText("WATCH AGAIN").performClick()
        compose.onNodeWithText("SAVE DATE").performScrollTo().performClick()
        compose.waitUntil(5000){vm.personal.value.events.size==2&&date==today}
        openStats();compose.onNodeWithText("3.0h").assertExists();Unit
    }
    @Test fun cachedHistoryRemainsVisibleWhenJellyfinFails()=runBlocking {
        NativeRuntime.settings.apply{jellyfinUrl="http://127.0.0.1:1";jellyfinToken="synthetic";jellyfinUserId="user-a"}
        val active=jellyfinScope();storage.remote(active,listOf(JournalEntry(movie,watchedOn(today),source="Jellyfin",scope=active)))
        val vm=TrackingViewModel(historyLoader={throw java.io.IOException("sensitive URL must not be shown")},episodeLoader={plan()});vm.attach(storage,active)
        compose.setContent{Frame(vm)};openHistory()
        compose.waitUntil(5000){vm.error.value!=null&&vm.personal.value.events.isNotEmpty()}
        compose.onNodeWithText("Fixture Movie").assertIsDisplayed();compose.onNodeWithText("Jellyfin could not update.",substring=true).assertDoesNotExist();capture("06-offline-history");Unit
    }
}
