package app.stak.nativeapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun loadTrackingSeries(media:MediaItem):SeriesPlan {
    if(media.imdbId!=null||media.id.startsWith("tmdb:")){
        val details=MediaRepository().details("series",media.mediaKey)
        return SeriesPlan(journalSeriesTitle(details.item).copy(jellyfinId=media.jellyfinSeriesId?:media.jellyfinId,jellyfinSeriesId=media.jellyfinSeriesId),episodes=details.episodes,completeMetadata=details.notice?.contains("Season ")==false||details.notice==null,issue=details.notice)
    }
    require(NativeRuntime.settings.jellyfinConnected){"Connect a metadata source to load episodes."}
    val parent=media.jellyfinSeriesId?:media.jellyfinId?:media.id.takeIf{it.startsWith("jellyfin:")}?.substringAfter(':')?:error("Series identifier is unknown")
    require(parent.matches(Regex("[A-Za-z0-9-]+")))
    val scope=jellyfinScope()
    val episodes=loadLibraryPages(identity={it.season.toString()+":"+it.episode},fetch={offset,limit->
        val data=DirectJellyfin.sessionRequest("/Shows/$parent/Episodes",mapOf("UserId" to NativeRuntime.settings.jellyfinUserId.orEmpty(),"Fields" to "Overview","StartIndex" to "$offset","Limit" to "$limit","EnableUserData" to "true"),expectedScope=scope)
        require(data.get("Items")?.isJsonArray==true)
        LibraryPage(data.objects("Items").mapNotNull{row->val s=row.number("ParentIndexNumber")?.toInt();val e=row.number("IndexNumber")?.toInt();if(s==null||e==null)null else Episode(s,e,row.text("Name").orEmpty(),runtime=row.number("RunTimeTicks")?.toLong()?.div(600_000_000)?.let{"$it min"},released=row.text("PremiereDate"))},data.number("TotalRecordCount")?.toInt())
    })
    // Jellyfin can enumerate only episodes it knows about, not every produced episode.
    return SeriesPlan(journalSeriesTitle(media),episodes=episodes,completeMetadata=false,issue="Library episodes only")
}
