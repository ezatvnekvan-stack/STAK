package app.stak.nativeapp

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountSafetyTest {
    private val context
        get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun capturedDaoAndJellyfinSessionsStayWithTheirAccount() = runBlocking {
        val repository = Repository(context)
        val original = repository.credentials.get("torbox_token")
        withContext(Dispatchers.IO) { repository.db.clearAllTables() }
        try {
            repository.credentials.set("torbox_token", "test-only-safety-account-a")
            val a = repository.dao
            repository.credentials.set("jellyfin_token", "test-only-server-a")
            val media = Media("tt11280740", "series", "Account A title")
            a.put(SavedMedia(media.key, "watchlist", media.json().toString(), 1234))
            a.putEpisode(WatchedEpisode(media.key, 1, 1, 4321))
            repository.credentials.set("torbox_token", "test-only-safety-account-b")
            val b = repository.dao
            assertTrue(b.all().isEmpty())
            assertTrue(b.allEpisodes().isEmpty())
            assertEquals("", repository.credentials.get("jellyfin_token"))
            repository.credentials.set("jellyfin_token", "test-only-server-b")
            // Simulate an operation started in A that completes after switching to B.
            a.putEpisode(WatchedEpisode(media.key, 1, 2, 5555))
            assertTrue(b.allEpisodes().isEmpty())
            repository.credentials.set("torbox_token", "test-only-safety-account-a")
            assertEquals("test-only-server-a", repository.credentials.get("jellyfin_token"))
            assertEquals(1234L, repository.dao.all().single().updatedAt)
            assertEquals(
                setOf(4321L, 5555L),
                repository.dao.allEpisodes().map { it.watchedAt }.toSet(),
            )
            repository.credentials.set("torbox_token", "")
            assertTrue(repository.dao.all().none { it.mediaKey == media.key })
        } finally {
            withContext(Dispatchers.IO) { repository.db.clearAllTables() }
            repository.credentials.set("torbox_token", original)
            repository.db.close()
        }
    }

    @Test
    fun v1MigrationPreservesRowsOnlyForItsUpgradeOwner() =
        runBlocking(Dispatchers.IO) {
            val name = "test-account-migration-${System.nanoTime()}.db"
            val media = Media("tt11280740", "series", "Preserved title")
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
                old.execSQL(
                    "CREATE TABLE saved_media (mediaKey TEXT NOT NULL, collection TEXT NOT NULL, json TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(mediaKey, collection))"
                )
                old.execSQL(
                    "CREATE TABLE watched_episodes (mediaKey TEXT NOT NULL, season INTEGER NOT NULL, episode INTEGER NOT NULL, watchedAt INTEGER NOT NULL, PRIMARY KEY(mediaKey, season, episode))"
                )
                old.execSQL(
                    "INSERT INTO saved_media VALUES (?, ?, ?, ?)",
                    arrayOf<Any>(media.key, "watchlist", media.json().toString(), 1234),
                )
                old.execSQL(
                    "INSERT INTO watched_episodes VALUES (?, ?, ?, ?)",
                    arrayOf<Any>(media.key, 2, 3, 5678),
                )
                old.version = 1
            }
            val owner = accountScope("test-only-upgrade-owner")
            val db =
                Room.databaseBuilder(context, StakDatabase::class.java, name)
                    .addMigrations(accountMigration(owner))
                    .build()
            try {
                val a = ScopedStakDao(db.dao(), owner)
                assertEquals(1234L, a.all().single().updatedAt)
                assertEquals(5678L, a.allEpisodes().single().watchedAt)
                assertTrue(ScopedStakDao(db.dao(), "local").all().isEmpty())
                assertTrue(
                    ScopedStakDao(db.dao(), accountScope("test-only-other-owner"))
                        .allEpisodes()
                        .isEmpty()
                )
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }

    @Test
    fun encryptedRestoreKeepsExistingDatesAndNeverWritesToAnotherAccount() = runBlocking {
        val repository = Repository(context)
        val original = repository.credentials.get("torbox_token")
        withContext(Dispatchers.IO) { repository.db.clearAllTables() }
        repository.credentials.set("torbox_token", "test-only-restore-owner")
        val vm = StakViewModel(context, repository)
        try {
            val media = Media("tt11280740", "series", "Restored title")
            repository.dao.put(SavedMedia(media.key, "watchlist", media.json().toString(), 100))
            repository.dao.putEpisode(WatchedEpisode(media.key, 1, 1, 200))
            val source = repository.createBackupArchive(false)
            val encrypted =
                withContext(Dispatchers.Default) {
                    EncryptedBackup.encrypt(source, "test-only long password".toCharArray())
                }
            val archive =
                parseBackupArchive(
                    withContext(Dispatchers.Default) {
                        EncryptedBackup.decrypt(encrypted, "test-only long password".toCharArray())
                    }
                )
            repository.dao.putEpisode(WatchedEpisode(media.key, 1, 1, 300))
            vm.restoreBackup(archive, repository.accountScope)
            assertEquals(300L, repository.dao.allEpisodes().single().watchedAt)
            assertEquals(100L, repository.dao.all().single().updatedAt)
            val owner = repository.accountScope
            repository.credentials.set("torbox_token", "test-only-restore-other")
            assertThrows(IllegalStateException::class.java) {
                runBlocking { vm.restoreBackup(archive, owner) }
            }
            assertTrue(repository.dao.all().isEmpty())
            assertFalse(JSONObject(source).has("connections"))
            assertFalse(source.contains("test-only-restore-owner"))
        } finally {
            withContext(Dispatchers.IO) { repository.db.clearAllTables() }
            repository.credentials.set("torbox_token", original)
            repository.db.close()
        }
    }
}
