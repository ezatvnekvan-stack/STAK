package app.stak.nativeapp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC, rowid DESC")
    fun observeAll(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist")
    suspend fun getAll(): List<WatchlistEntity>

    @Query("DELETE FROM watchlist")
    suspend fun clearForRestore()

    @Query("DELETE FROM watchlist WHERE imdbId = :id")
    suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchlistEntity)

    @Delete
    suspend fun delete(item: WatchlistEntity)
}
