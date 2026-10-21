package app.stak.nativeapp

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.security.MessageDigest

/** Matches the existing native hash-list and indexer namespaces; no key is stored in Room. */
fun accountScope(token: String): String =
    if (token.isBlank()) "local"
    else
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)).joinToString(
            ""
        ) {
            "%02x".format(it)
        }

/** A captured account, so suspended operations cannot write into a replacement account. */
class ScopedStakDao(private val dao: StakDao, val scope: String) {
    fun observe() = dao.observe(scope)

    fun episodes() = dao.episodes(scope)

    suspend fun all() = dao.all(scope)

    suspend fun allEpisodes() = dao.allEpisodes(scope)

    suspend fun put(item: SavedMedia) = dao.put(item.copy(accountScope = scope))

    suspend fun merge(items: List<SavedMedia>) =
        dao.merge(items.map { it.copy(accountScope = scope) })

    suspend fun remove(key: String, collection: String) = dao.remove(scope, key, collection)

    suspend fun putEpisode(item: WatchedEpisode) = dao.putEpisode(item.copy(accountScope = scope))

    suspend fun removeEpisode(key: String, season: Int, episode: Int) =
        dao.removeEpisode(scope, key, season, episode)
}

/** Preserve v1 rows under the saved account at upgrade, or the local profile with no key. */
fun accountMigration(legacyOwner: String) =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE saved_media_v2 (mediaKey TEXT NOT NULL, collection TEXT NOT NULL, json TEXT NOT NULL, updatedAt INTEGER NOT NULL, accountScope TEXT NOT NULL, PRIMARY KEY(accountScope, mediaKey, collection))"
            )
            db.execSQL(
                "INSERT INTO saved_media_v2 SELECT mediaKey, collection, json, updatedAt, ? FROM saved_media",
                arrayOf(legacyOwner),
            )
            db.execSQL("DROP TABLE saved_media")
            db.execSQL("ALTER TABLE saved_media_v2 RENAME TO saved_media")
            db.execSQL(
                "CREATE TABLE watched_episodes_v2 (mediaKey TEXT NOT NULL, season INTEGER NOT NULL, episode INTEGER NOT NULL, watchedAt INTEGER NOT NULL, accountScope TEXT NOT NULL, PRIMARY KEY(accountScope, mediaKey, season, episode))"
            )
            db.execSQL(
                "INSERT INTO watched_episodes_v2 SELECT mediaKey, season, episode, watchedAt, ? FROM watched_episodes",
                arrayOf(legacyOwner),
            )
            db.execSQL("DROP TABLE watched_episodes")
            db.execSQL("ALTER TABLE watched_episodes_v2 RENAME TO watched_episodes")
        }
    }

fun openStakDatabase(context: Context, legacyOwner: String): StakDatabase =
    Room.databaseBuilder(context, StakDatabase::class.java, "stak-native.db")
        .addMigrations(accountMigration(legacyOwner))
        .build()
