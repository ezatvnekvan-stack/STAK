package app.stak.nativeapp.data

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object JellyfinJournal {
    suspend fun load():List<JournalEntry> = coroutineScope {
        val settings=NativeRuntime.settings
        if(!settings.jellyfinConnected)return@coroutineScope emptyList()
        val scope=jellyfinScope();val server=DirectJellyfin.normalizeServer(settings.jellyfinUrl.orEmpty());val user=settings.jellyfinUserId.orEmpty()
        val rows=loadLibraryPages(identity={it.text("Id").orEmpty()},fetch={offset,limit->
            val data=DirectJellyfin.sessionRequest("/Users/$user/Items",mapOf("Recursive" to "true","IncludeItemTypes" to "Movie,Episode","IsPlayed" to "true","EnableUserData" to "true","SortBy" to "DatePlayed","SortOrder" to "Descending","Fields" to "ProviderIds,UserData,RunTimeTicks,Overview,Genres","StartIndex" to offset.toString(),"Limit" to limit.toString()),expectedScope=scope)
            require(data.get("Items")?.isJsonArray==true){"Invalid Jellyfin history."}
            LibraryPage(data.objects("Items"),data.number("TotalRecordCount")?.toInt())
        })
        val parents=mutableMapOf<String,MediaItem>();val permits=Semaphore(3)
        rows.mapNotNull{it.text("SeriesId")}.distinct().map{id->async{permits.withPermit {
            try {val data=DirectJellyfin.sessionRequest("/Users/$user/Items/$id",expectedScope=scope);id to jellyfinMediaItem(data,server)}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){null}
        }}}.awaitAll().filterNotNull().forEach{(id,item)->parents[id]=item}
        if(scope!=jellyfinScope())return@coroutineScope emptyList()
        val result=rows.map{row->
            val base=jellyfinMediaItem(row,server);val parent=base.jellyfinSeriesId?.let{parents[it]}
            val item=if(parent==null)base else base.copy(imdbId=parent.imdbId,name=parent.name,poster=parent.poster,genres=base.genres.ifEmpty{parent.genres})
            JournalEntry(item,libraryTimestamp(row.objectOrNull("UserData")?.text("LastPlayedDate")).takeIf{it>0},true,"Jellyfin",scope=scope)
        }
        runCatching{IntelligenceStore.get(settings).observe(scope,result.map{it.media})}
        result
    }
}
