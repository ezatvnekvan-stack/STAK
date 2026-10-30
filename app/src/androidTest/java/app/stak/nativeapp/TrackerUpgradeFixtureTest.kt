package app.stak.nativeapp

import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDate

/** Explicitly invoked only on the disposable emulator for optimized in-place-upgrade verification. */
class TrackerUpgradeFixtureTest {
    @Test fun seedLegacyRowAndPersonalDataForOptimizedUpgrade()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=DatabaseProvider.get(context);val storage=TrackerStore(db)
        val movie=MediaItem("tt999991300",imdbId="tt999991300",name="Tracking upgrade movie",runtime="90 min",genres=listOf("Drama"))
        val show=MediaItem("tt999991301",imdbId="tt999991301",type="series",name="Tracking upgrade series",genres=listOf("Drama"))
        val row=trackingEntity(movie,null,true,watchedOn(LocalDate.now().minusDays(1)))
        db.trackingDao().upsert(row)
        // Import will happen in the app after installation. These are already-created personal records.
        storage.log("local",movie,watchedOn(LocalDate.now()),"optimized-rewatch-130")
        storage.savePlan("local",SeriesPlan(show,"on_hold",listOf(Episode(1,1,"Known first episode",runtime="45 min",released=LocalDate.now().minusDays(1).toString())),System.currentTimeMillis(),true))
        storage.rate("local",show,8)
        SettingsStore(context).apply{finishSetup();journalPreferences=com.google.gson.Gson().toJson(JournalPreferences(view="progress"))}
        Unit
    }
}
