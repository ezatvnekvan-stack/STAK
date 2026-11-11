package app.stak.nativeapp.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

data class LibraryPage<T>(val items:List<T>,val total:Int?=null)

/** Offset follows raw server rows, while presentation deduplicates by server ID. */
suspend fun <T> loadLibraryPages(
    pageSize:Int=200,
    identity:(T)->String,
    fetch:suspend (offset:Int,limit:Int)->LibraryPage<T>,
    onPage:suspend (items:List<T>,total:Int?)->Unit={_,_->}
):List<T> {
    require(pageSize>0)
    val result=linkedMapOf<String,T>();var offset=0
    while(true) {
        currentCoroutineContext().ensureActive()
        val page=fetch(offset,pageSize)
        val before=result.size
        page.items.forEach{result[identity(it)]=it}
        if(page.items.isNotEmpty()&&result.size==before)throw IOException("The server repeated a library page. Retry to finish loading.")
        offset+=page.items.size
        onPage(result.values.toList(),page.total)
        if(page.total!=null&&offset>=page.total)return result.values.toList()
        if(page.items.isEmpty()) {
            if(page.total!=null&&offset<page.total)throw IOException("The server returned an incomplete library. Retry to finish loading.")
            return result.values.toList()
        }
        if(page.total==null&&page.items.size<pageSize)return result.values.toList()
    }
}

data class LibraryFilters(val query:String="",val type:String="all",val status:String="all",val sort:String="name",val layout:String="grid")
internal fun librarySearchText(value:String)=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"),"").lowercase(java.util.Locale.ROOT)
fun filterJellyfinLibrary(items:List<MediaItem>,filters:LibraryFilters):List<MediaItem> {
    val query=librarySearchText(filters.query.trim())
    val shown=items.filter { item ->
        (filters.type=="all"||item.type==filters.type)&&when(filters.status){"watched"->item.watched;"unwatched"->!item.watched;"progress"->!item.watched&&item.progress>0;else->true}&&
        (query.isBlank()||listOf(item.name,item.year.orEmpty(),item.imdbId.orEmpty()).plus(item.aliases).any{librarySearchText(it).contains(query)})
    }
    val order=when(filters.sort){"newest"->compareByDescending<MediaItem>{it.addedAt};"year"->compareByDescending{it.year?.toIntOrNull() ?: 0};"name_desc"->compareByDescending{librarySearchText(it.name)};else->compareBy{librarySearchText(it.name)}}
    return shown.sortedWith(order.thenBy{librarySearchText(it.name)}.thenBy{it.jellyfinId ?: it.id})
}

data class TorBoxLibraryEntry(val torrent:TorBoxTorrent,val metadata:MediaItem?=null)
fun torBoxStatus(item:TorBoxTorrent):String=when {
    item.finished->"done"
    Regex("error|failed|missingfiles",RegexOption.IGNORE_CASE).containsMatchIn(item.state)->"error"
    else->"active"
}
fun filterTorBoxLibrary(items:List<TorBoxLibraryEntry>,filters:LibraryFilters):List<TorBoxLibraryEntry> {
    val query=librarySearchText(filters.query.trim())
    val shown=items.filter{row->(filters.status=="all"||torBoxStatus(row.torrent)==filters.status)&&
        (query.isBlank()||listOfNotNull(row.torrent.name,row.torrent.hash,row.metadata?.name).plus(row.torrent.files.map{it.name}).any{librarySearchText(it).contains(query)})}
    val order=when(filters.sort){"name"->compareBy<TorBoxLibraryEntry>{librarySearchText(it.torrent.name)};"size"->compareByDescending{it.torrent.size};else->compareByDescending{it.torrent.createdAt}}
    return shown.sortedWith(order.thenBy{it.torrent.id})
}
internal fun libraryTimestamp(raw:String?):Long=raw?.let { runCatching{java.time.Instant.parse(it).toEpochMilli()}.getOrNull() ?: runCatching{java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli()}.getOrNull() } ?: 0L
