package app.stak.nativeapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JellyfinItemDao {
    @Query("SELECT * FROM jellyfin_items ORDER BY name")
    fun observeAll(): Flow<List<JellyfinItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<JellyfinItemEntity>)

    @Query("DELETE FROM jellyfin_items")
    suspend fun clear()
}