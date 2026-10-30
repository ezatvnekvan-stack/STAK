package app.stak.nativeapp.data

import app.stak.nativeapp.data.db.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WatchlistRepository(private val dao:WatchlistDao,private val remote:MediaRepository=MediaRepository()) {
    private val writes=Mutex()
    fun observe():Flow<List<MediaItem>> = dao.observeAll().map{rows->rows.map{it.toMedia()}}
    // Local Room is authoritative. There is no watchlist backend in the native app.
    suspend fun refreshFromBackend(){withContext(Dispatchers.IO){dao.getAll()}}
    suspend fun seed(items:List<MediaItem>)=writes.withLock{withContext(Dispatchers.IO){if(dao.getAll().isEmpty())items.forEach{dao.upsert(it.toEntity())}}}
    suspend fun toggle(item:MediaItem):Boolean=writes.withLock{withContext(Dispatchers.IO){
        val current=dao.getAll().firstOrNull{it.toMedia().sameIdentity(item)}
        if(current!=null){dao.delete(current);false}else{dao.upsert(item.toEntity());true}
    }}
    suspend fun remove(item:MediaItem)=writes.withLock{withContext(Dispatchers.IO){dao.getAll().filter{it.toMedia().sameIdentity(item)}.forEach{dao.delete(it)}}}
    suspend fun updateMetadata(item:MediaItem)=withContext(Dispatchers.IO){
        val current=dao.getAll().firstOrNull{it.toMedia().sameIdentity(item)} ?: return@withContext
        dao.upsert(item.toEntity().copy(imdbId=current.imdbId,addedAt=current.addedAt))
    }
}
fun WatchlistEntity.toMedia():MediaItem {
    val saved=runCatching{mediaJson?.let{Gson().fromJson(it,MediaItem::class.java)}}.getOrNull()
    return (saved ?: MediaItem(id=imdbId,imdbId=imdbId.takeIf{it.matches(Regex("tt\\d+"))},type=type,name=name,year=year,poster=poster,background=poster,releaseInfo=year,progress=progress)).copy(addedAt=addedAt)
}
fun MediaItem.toEntity():WatchlistEntity=WatchlistEntity(mediaKey,type,name,year,poster,progress,addedAt.takeIf{it>0} ?: System.currentTimeMillis(),Gson().toJson(this))
