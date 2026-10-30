package app.stak.nativeapp.data

import app.stak.nativeapp.data.db.TrackingDao
import app.stak.nativeapp.data.db.TrackingEntity
import kotlinx.coroutines.flow.Flow

class TrackingRepository(private val dao: TrackingDao,private val tracker:TrackerStore?=null) {
    fun observe(): Flow<List<TrackingEntity>> = dao.observeAll()
    suspend fun upsert(item: TrackingEntity) {if(tracker!=null)tracker.persistRows(listOf(item))else dao.upsert(item)}
    suspend fun upsertAll(items: List<TrackingEntity>) {if(tracker!=null)tracker.persistRows(items)else dao.upsertAll(items)}
    suspend fun delete(id: String) = dao.delete(id)
}
