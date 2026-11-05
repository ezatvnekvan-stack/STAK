package app.stak.nativeapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingDao {
    @Query("SELECT * FROM tracking ORDER BY lastPlayed DESC")
    fun observeAll(): Flow<List<TrackingEntity>>

    @Query("SELECT * FROM tracking")
    suspend fun getAll():List<TrackingEntity>

    @Query("DELETE FROM tracking")
    suspend fun clearForRestore()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TrackingEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TrackingEntity>)

    @Query("DELETE FROM tracking WHERE id = :id")
    suspend fun delete(id: String)
}
