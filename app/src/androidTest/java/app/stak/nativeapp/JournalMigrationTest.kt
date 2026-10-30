package app.stak.nativeapp

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.data.db.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*

class JournalMigrationTest {
    @Test fun previewSevenDatabaseUpgradePreservesSavedTitlesEpisodesAndExistingPreferences()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val name="migration-journal-${java.util.UUID.randomUUID()}.db"
        val legacy=context.openOrCreateDatabase(name,0,null)
        try {
            legacy.execSQL("CREATE TABLE watchlist (imdbId TEXT NOT NULL PRIMARY KEY,type TEXT NOT NULL,name TEXT NOT NULL,year TEXT,poster TEXT,progress REAL NOT NULL)")
            legacy.execSQL("CREATE TABLE tracking (id TEXT NOT NULL PRIMARY KEY,type TEXT NOT NULL,name TEXT NOT NULL,played INTEGER NOT NULL,progress REAL NOT NULL,lastPlayed INTEGER)")
            legacy.execSQL("CREATE TABLE jellyfin_items (id TEXT NOT NULL PRIMARY KEY,name TEXT NOT NULL,type TEXT NOT NULL,year INTEGER,played INTEGER NOT NULL,playedPercentage REAL NOT NULL,posterUrl TEXT)")
            legacy.execSQL("CREATE TABLE preferences (`key` TEXT NOT NULL PRIMARY KEY,value TEXT NOT NULL)")
            legacy.execSQL("INSERT INTO watchlist VALUES ('tt1234567','movie','Saved film','2020','https://example.invalid/poster',0)")
            legacy.execSQL("INSERT INTO tracking VALUES ('tt7654321:2:3','episode','Saved series · S2 E3',1,1,12345)")
            legacy.execSQL("INSERT INTO preferences VALUES ('existing','preserved')")
            legacy.version=3
        } finally {legacy.close()}
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(LIBRARY_JOURNAL_MIGRATION).build()
        try {
            val saved=db.watchlistDao().getAll().single();Assert.assertEquals("Saved film",saved.name);Assert.assertTrue(saved.addedAt>0)
            val tracked=db.trackingDao().getAll().single();Assert.assertEquals("local",tracked.scope);Assert.assertEquals(12345L,tracked.lastPlayed);Assert.assertEquals(2,tracked.toJournal().media.season);Assert.assertEquals(3,tracked.toJournal().media.episode)
            db.openHelper.readableDatabase.query("SELECT value FROM preferences WHERE `key`='existing'").use{Assert.assertTrue(it.moveToFirst());Assert.assertEquals("preserved",it.getString(0))}
            // Distinct TMDb namespaces and distinct Jellyfin users must coexist after migration.
            val repo=WatchlistRepository(db.watchlistDao());repo.toggle(MediaItem("tmdb:42",type="movie",name="Movie"));repo.toggle(MediaItem("tmdb:42",type="series",name="Series"))
            Assert.assertEquals(3,db.watchlistDao().getAll().size)
            db.trackingDao().upsert(tracked.copy(scope="another-account",played=false));Assert.assertEquals(2,db.trackingDao().getAll().size)
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun savedMetadataAndDateAddedSurviveUpdatesAndRemovalUsesActualStoredIdentity()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
        try {
            val repo=WatchlistRepository(db.watchlistDao());val original=MediaItem("tmdb:42",name="Film",poster="https://example.invalid/poster",addedAt=123)
            repo.toggle(original);repo.updateMetadata(original.copy(imdbId="tt1234567",rating=8.5))
            val item=repo.observe().first().single();Assert.assertEquals(123L,item.addedAt);Assert.assertEquals(8.5,item.rating!!,.001);Assert.assertEquals("tt1234567",item.imdbId)
            repo.remove(item);Assert.assertTrue(db.watchlistDao().getAll().isEmpty())
        } finally {db.close()}
    }
}
