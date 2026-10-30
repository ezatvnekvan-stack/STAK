package app.stak.nativeapp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

/** Disposable emulator upgrade evidence only; never included as production media. */
class PassiveUpgradeFixtureTest {
    @Test fun seed()=runBlocking{
        val settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext);NativeRuntime.settings=settings
        settings.apply{jellyfinUrl="http://127.0.0.1:9";jellyfinToken="synthetic-upgrade-only";jellyfinUserId="passive-upgrade";reducedMotion=true;journalPreferences=com.google.gson.Gson().toJson(JournalPreferences(view="watching"));finishSetup()}
        val store=TrackerStore(DatabaseProvider.get(settings.applicationContext));val stamp=watchedOn(LocalDate.now())
        store.log("local",MediaItem("old134",name="Earlier manual movie"),stamp,"passive-upgrade-legacy")
        val movie=MediaItem("server134",name="Observed Jellyfin movie",jellyfinId="server-movie",watched=true,jellyfinUserDataKnown=true,runtime="90 min",lastPlayedAt=stamp)
        store.update(jellyfinScope()){it.copy(events=it.events.filterNot{e->e.media.jellyfinId=="server-movie"},remote=it.remote.filterNot{e->e.media.jellyfinId=="server-movie"})}
        store.remote(jellyfinScope(),listOf(JournalEntry(movie,stamp,true,"Jellyfin",System.currentTimeMillis(),jellyfinScope())))
        PlaybackStore().save(PlaybackRecord(movie,jellyfinScope(),0,0,true,123,jellyfinItemId="server-movie",pending=true,manual=true))
        val raw=settings.playbackJson!!
        SecureStorage(settings.applicationContext).putSecretChecked("playback_state",raw)
        assertTrue(settings.applicationContext.getSharedPreferences("passive_upgrade_fixture",0).edit().putString("pending_hash",credentialScope(raw)).commit())

    }
}
class PassiveUpgradeVerifyTest {
    @Test fun pendingAndHistorySurviveWithoutReplay()=runBlocking{
        val settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext);NativeRuntime.settings=settings
        val store=TrackerStore(DatabaseProvider.get(settings.applicationContext))
        assertTrue(store.read("local").events.any{it.id=="passive-upgrade-legacy"})
        assertTrue(store.read(jellyfinScope()).remote.any{it.media.jellyfinId=="server-movie"&&it.played})
        val raw=settings.playbackJson
        assertNotNull("The legacy encrypted record must be retained",raw)
        assertEquals(settings.applicationContext.getSharedPreferences("passive_upgrade_fixture",0).getString("pending_hash",null),credentialScope(raw!!))
        val record=PlaybackStore().all().single{it.jellyfinItemId=="server-movie"};assertTrue(record.pending);assertTrue(record.manual);assertEquals(123L,record.updatedAt)
        val newManual=store.read(jellyfinScope()).events.filter{it.source=="manual"};assertTrue(newManual.isEmpty())
    }
}

