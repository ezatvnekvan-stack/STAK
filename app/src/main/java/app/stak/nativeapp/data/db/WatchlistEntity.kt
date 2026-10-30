package app.stak.nativeapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist",primaryKeys=["imdbId","type"])
data class WatchlistEntity(
    val imdbId: String,
    val type: String,
    val name: String,
    val year: String?,
    val poster: String?,
    val progress: Float = 0f,
    @androidx.room.ColumnInfo(defaultValue="0") val addedAt:Long=0,
    val mediaJson:String?=null
)