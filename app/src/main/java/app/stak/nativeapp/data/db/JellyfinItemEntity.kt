package app.stak.nativeapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jellyfin_items")
data class JellyfinItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val year: Int? = null,
    val played: Boolean = false,
    val playedPercentage: Float = 0f,
    val posterUrl: String? = null
)