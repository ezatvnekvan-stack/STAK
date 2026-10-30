package app.stak.nativeapp

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.data.db.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*

class PersonalTrackerStoreTest {
    private lateinit var db:AppDatabase;private lateinit var store:TrackerStore
    private val movie=MediaItem("tt0000001",imdbId="tt0000001",name="Fixture Movie",runtime="90 min")
    @Before fun setup(){db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build();store=TrackerStore(db)}
    @After fun cleanup(){db.close()}
    @Test fun existingRowsImportWithoutDuplicatingAcrossRestarts()=runBlocking {
        val row=trackingEntity(movie,null,true,1000,"a");db.trackingDao().upsert(row)
        store.importLegacy(listOf(row));TrackerStore(db).importLegacy(listOf(row))
        Assert.assertEquals(1,store.read("a").events.size);Assert.assertEquals(row,db.trackingDao().getAll().single());Unit
    }
    @Test fun unwatchAndLibraryRemovalDoNotDeletePersonalHistory()=runBlocking {
        val repo=TrackingRepository(db.trackingDao(),store);val row=trackingEntity(movie,null,true,1000,"a")
        repo.upsert(row);repo.upsert(row.copy(played=false,lastPlayed=null));db.trackingDao().delete(row.id)
        Assert.assertEquals(1,store.read("a").events.size);Assert.assertTrue(db.trackingDao().getAll().isEmpty());Unit
    }
    @Test fun editingAndRemovingOneRewatchLeavesOtherEventsAndWatchedState()=runBlocking {
        val repo=TrackingRepository(db.trackingDao(),store);repo.upsert(trackingEntity(movie,null,true,1000,"a"));store.log("a",movie,200000,"rewatch")
        val event=store.read("a").events.first{it.id=="rewatch"};store.edit(event,300000)
        Assert.assertEquals(300000L,store.read("a").events.first{it.id=="rewatch"}.watchedAt)
        store.remove(event);Assert.assertEquals(1,store.read("a").events.size);Assert.assertTrue(db.trackingDao().getAll().single().played);Unit
    }
    @Test fun deletedRemoteEventDoesNotReappearOnSnapshotOrRestart()=runBlocking {
        val row=JournalEntry(movie,1000,source="Jellyfin",scope="a");store.remote("a",listOf(row));val event=store.read("a").events.single()
        store.remove(event);TrackerStore(db).remote("a",listOf(row));Assert.assertTrue(store.read("a").events.isEmpty());Unit
    }
    @Test fun plansRatingsAndViewingEventsAreIsolatedByAccount()=runBlocking {
        val show=movie.copy(type="series");store.savePlan("a",SeriesPlan(show,"dropped"));store.rate("a",movie,8);store.log("a",movie,1000,"a1")
        Assert.assertTrue(store.observe("b").first().events.isEmpty());Assert.assertTrue(store.read("b").plans.isEmpty());Assert.assertTrue(store.read("b").ratings.isEmpty())
        Assert.assertEquals("dropped",TrackerStore(db).read("a").plans.single().status);Assert.assertEquals(8,TrackerStore(db).read("a").ratings.single().value);Unit
    }
    @Test fun concurrentActionsDoNotOverwriteEvents()=runBlocking {
        coroutineScope{(1..20).map{i->async{store.log("a",movie,i*1000L,"event$i")}}.awaitAll()}
        Assert.assertEquals(20,store.read("a").events.size);Unit
    }
    @Test fun unreadableDocumentIsNotSilentlyReplaced()=runBlocking {
        val key="personal-tracker-v1:a";db.preferenceDao().set(PreferenceEntity(key,"{broken"))
        try{store.log("a",movie,1000);Assert.fail("Expected storage error")}catch(_:java.io.IOException){}
        Assert.assertEquals("{broken",db.preferenceDao().get(key));Unit
    }
    @Test fun editingManualDateDoesNotReimportOldServerEcho()=runBlocking {
        val remote=JournalEntry(movie.copy(jellyfinId="actual-file"),1000,source="Jellyfin",scope="a")
        store.log("a",movie,1000,"manual-event");store.remote("a",listOf(remote));val event=store.read("a").events.single()
        store.edit(event,200000);store.remote("a",listOf(remote))
        Assert.assertEquals(1,store.read("a").events.size);Assert.assertEquals(200000L,store.read("a").events.single().watchedAt);Unit
    }
    @Test fun preHubDocumentsWithoutPinsUpgradeWithoutLosingEvents()=runBlocking {
        val state=TrackerState(events=listOf(WatchEvent("old",movie,1000,"manual","a")))
        val json=com.google.gson.Gson().toJsonTree(state).asJsonObject;json.remove("pins")
        db.preferenceDao().set(PreferenceEntity("personal-tracker-v1:a",json.toString()))
        Assert.assertTrue(store.read("a").pins.isEmpty());store.pin("a",movie,true)
        Assert.assertEquals("old",TrackerStore(db).read("a").events.single().id);Assert.assertTrue(TrackerStore(db).read("a").pins.single().active);Unit
    }
    @Test fun watchingPinsRemainAccountScopedAndRemovableWithoutUnwatching()=runBlocking {
        val repo=TrackingRepository(db.trackingDao(),store);repo.upsert(trackingEntity(movie,null,true,1000,"a"));store.pin("a",movie,true);store.pin("a",movie,false)
        Assert.assertFalse(store.read("a").pins.single().active);Assert.assertTrue(store.read("b").pins.isEmpty());Assert.assertTrue(db.trackingDao().getAll().single().played);Assert.assertEquals(1,store.read("a").events.size);Unit
    }
    @Test fun lateMetadataDoesNotOverwriteAUserStatusChange()=runBlocking {
        val oldSettings=runCatching{NativeRuntime.settings}.getOrNull()
        val prefix="metadata132-${java.util.UUID.randomUUID()}-"
        NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix)
        try {
            val show=movie.copy(type="series");store.savePlan("local",SeriesPlan(show,"watching"))
            val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            val vm=app.stak.nativeapp.ui.screens.TrackingViewModel(historyLoader={emptyList()},episodeLoader={started.complete(Unit);release.await();SeriesPlan(it,episodes=listOf(Episode(1,1,"One")),completeMetadata=true)})
            vm.attach(store,"local");vm.retrySeries(show);withTimeout(5000){started.await()}
            store.savePlan("local",SeriesPlan(show,"dropped"));release.complete(Unit)
            val saved=withTimeout(5000){store.observe("local").first{it.plans.any{p->p.refreshedAt>0}}}
            Assert.assertEquals("dropped",saved.plans.single().status)
        }finally{oldSettings?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}}
        Unit
    }
}
