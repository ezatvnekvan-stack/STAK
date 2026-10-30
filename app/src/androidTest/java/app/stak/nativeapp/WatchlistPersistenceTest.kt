package app.stak.nativeapp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.MediaItem
import app.stak.nativeapp.data.WatchlistRepository
import app.stak.nativeapp.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchlistPersistenceTest {
    @Test fun saveAndRemoveOperateWithoutBackendAndPreserveOtherItems() = runBlocking {
        // Separate in-memory database: never clear the user's saved collection.
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val repository = WatchlistRepository(db.watchlistDao())
            val first = MediaItem("tt1234567", "tt1234567", name = "First")
            val second = MediaItem("tt7654321", "tt7654321", name = "Second")
            assertTrue(db.watchlistDao().getAll().isEmpty())
            val legacy = MediaItem("tmdb:42", name = "Legacy title")
            val resolved = legacy.copy(imdbId = "tt1234567")
            assertTrue(repository.toggle(legacy))
            assertFalse(repository.toggle(resolved))
            assertTrue(db.watchlistDao().getAll().isEmpty())
            assertTrue(repository.toggle(first))
            assertTrue(repository.toggle(second))
            assertEquals(2, db.watchlistDao().getAll().size)
            assertFalse(repository.toggle(first))
            assertEquals("Second", db.watchlistDao().getAll().single().name)
            repository.remove(second)
            assertTrue(db.watchlistDao().getAll().isEmpty())
        } finally { db.close() }
    }
}
