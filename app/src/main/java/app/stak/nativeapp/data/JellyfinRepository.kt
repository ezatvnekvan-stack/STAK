package app.stak.nativeapp.data

import app.stak.nativeapp.data.db.JellyfinItemDao
import app.stak.nativeapp.data.db.JellyfinItemEntity
import kotlinx.coroutines.flow.Flow

class JellyfinRepository(private val dao: JellyfinItemDao) {
    fun observe(): Flow<List<JellyfinItemEntity>> = dao.observeAll()
    suspend fun replaceAll(items: List<JellyfinItemEntity>) {
        dao.clear()
        dao.upsertAll(items)
    }
}