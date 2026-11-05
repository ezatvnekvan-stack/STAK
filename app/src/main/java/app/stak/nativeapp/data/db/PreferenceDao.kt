package app.stak.nativeapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences ORDER BY key")
    suspend fun getAllForBackup():List<PreferenceEntity>

    @Query("SELECT * FROM preferences ORDER BY key")
    fun observeForBackup():kotlinx.coroutines.flow.Flow<List<PreferenceEntity>>

    @Query("DELETE FROM preferences")
    suspend fun clearForRestore()
    @Query("SELECT value FROM preferences WHERE key = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM preferences WHERE key = :key")
    fun observe(key:String):kotlinx.coroutines.flow.Flow<String?>

    @Query("DELETE FROM preferences WHERE key = :key")
    suspend fun delete(key:String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: PreferenceEntity)
}
