package app.stak.nativeapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PreferenceDao {
    @Query("SELECT value FROM preferences WHERE key = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM preferences WHERE key = :key")
    fun observe(key:String):kotlinx.coroutines.flow.Flow<String?>

    @Query("DELETE FROM preferences WHERE key = :key")
    suspend fun delete(key:String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: PreferenceEntity)
}