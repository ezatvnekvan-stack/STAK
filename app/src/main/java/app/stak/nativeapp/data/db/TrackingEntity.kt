package app.stak.nativeapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracking",primaryKeys=["id","scope"])
data class TrackingEntity(
    val id: String,
    val type: String,
    val name: String,
    val played: Boolean = false,
    val progress: Float = 0f,
    val lastPlayed: Long? = null,
    val mediaJson:String?=null,
    @androidx.room.ColumnInfo(defaultValue="'local'") val scope:String="local",
    @androidx.room.ColumnInfo(defaultValue="0") val updatedAt:Long=0
)