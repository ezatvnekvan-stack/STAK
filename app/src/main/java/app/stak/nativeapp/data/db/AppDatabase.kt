package app.stak.nativeapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WatchlistEntity::class, TrackingEntity::class, JellyfinItemEntity::class, PreferenceEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun trackingDao(): TrackingDao
    abstract fun jellyfinItemDao(): JellyfinItemDao
    abstract fun preferenceDao(): PreferenceDao
}