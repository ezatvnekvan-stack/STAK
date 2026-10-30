package app.stak.nativeapp.data

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun observeJellyfin(scope:String,items:List<MediaItem>,fetchedAt:Long){
    val settings=NativeRuntime.settings
    if(!settings.jellyfinConnected||scope!=jellyfinScope(settings))return
    IntelligenceStore.get(settings).observe(scope,items)
    withContext(Dispatchers.IO){
        val database=DatabaseProvider.get(settings.applicationContext)
        val dao=database.trackingDao()
        val observed=items.filter{it.jellyfinUserDataKnown==true&&(!it.isSeries||it.episode!=null)}
        TrackerStore(database).remote(scope,observed.map{JournalEntry(it,it.lastPlayedAt.takeIf{n->n>0},it.watched,"Jellyfin",fetchedAt,scope)})
        val gson=Gson()
        val updates=dao.getAll().filter{it.scope==scope&&it.updatedAt<=fetchedAt}.mapNotNull{row->
            val media=runCatching{row.mediaJson?.let{gson.fromJson(it,MediaItem::class.java)}}.getOrNull() ?: return@mapNotNull null
            val remote=items.firstOrNull{media.jellyfinId!=null&&it.jellyfinId==media.jellyfinId} ?: items.filter{media.imdbId!=null&&it.imdbId==media.imdbId&&it.season==media.season&&it.episode==media.episode}.singleOrNull() ?: return@mapNotNull null
            if(remote.jellyfinUserDataKnown!=true)return@mapNotNull null
            if(row.played==remote.watched&&kotlin.math.abs(row.progress-remote.progress)<.001f)return@mapNotNull null
            row.copy(played=remote.watched,progress=remote.progress,lastPlayed=remote.lastPlayedAt.takeIf{it>0&&remote.watched},updatedAt=System.currentTimeMillis(),mediaJson=gson.toJson(media.copy(watched=remote.watched,progress=remote.progress,jellyfinId=remote.jellyfinId,positionMs=remote.positionMs,durationMs=remote.durationMs)))
        }
        if(scope==jellyfinScope(settings)&&updates.isNotEmpty())dao.upsertAll(updates)
    }
    MediaLiveSync.success(scope)
}
