package app.stak.nativeapp.data

import java.time.LocalDate
import kotlinx.coroutines.*

data class SeriesCenterEntry(val key:String,val media:MediaItem,val rule:WatchRuleModel?=null)
fun seriesCenterEntries(library:List<MediaItem>,rules:List<WatchRuleModel>):List<SeriesCenterEntry>{
    val result=mutableListOf<SeriesCenterEntry>()
    library.filter{it.isSeries}.forEach{raw->
        val media=journalSeriesTitle(raw).let{it.copy(imdbId=it.imdbId?:it.id.takeIf{value->value.matches(Regex("tt\\d+"))})}
        if(result.none{sameSeries(it.media,media)})result+=SeriesCenterEntry("series:${journalSeriesIdentity(media)}",media)
    }
    rules.forEach{rule->
        val media=MediaItem(rule.seriesImdb?:rule.seriesTmdb?.let{"tmdb:$it"}?:"jellyfin:${rule.seriesJellyfin?:rule.ruleId}",imdbId=rule.seriesImdb,type="series",name=rule.seriesName,poster=rule.poster,jellyfinId=rule.seriesJellyfin,jellyfinSeriesId=rule.seriesJellyfin)
        val index=result.indexOfFirst{sameSeries(it.media,media)||rule.seriesTmdb?.let{tmdb->"tmdb:$tmdb" in it.media.jellyfinProviderKeys.orEmpty()}==true}
        if(index>=0){val old=result[index];result[index]=old.copy(media=old.media.copy(imdbId=old.media.imdbId?:rule.seriesImdb,poster=old.media.poster?:rule.poster),rule=rule)}
        else result+=SeriesCenterEntry("rule:${rule.ruleId}",media,rule)
    }
    return result.sortedBy{it.media.name.lowercase()}
}

data class SeriesCenterSnapshot(val metadata:List<Episode> = emptyList(),val library:List<MediaItem> = emptyList(),val libraryVerified:Boolean=false,val notices:List<String> = emptyList())
data class SeriesCenterEpisode(val episode:Episode,val versions:List<MediaItem>,val automation:WatchEpisode?,val status:String,val label:String){
    val available:Boolean get()=versions.isNotEmpty()
    val quality:String get()=versions.mapNotNull{item->listOfNotNull(item.jellyfinVersionLabel?:item.jellyfinVideoHeight?.let{if(it>=1800)"4K"else "${it}p"},item.jellyfinHdr?.takeUnless{it.equals("SDR",true)}).distinct().joinToString(" · ").takeIf{it.isNotBlank()}}.distinct().joinToString(" / ")
}
fun seriesCenterEpisodes(snapshot:SeriesCenterSnapshot,watcher:List<WatchEpisode>,today:LocalDate=LocalDate.now()):List<SeriesCenterEpisode>{
    val metadata=snapshot.metadata.associateBy{it.season to it.episode}
    val library=snapshot.library.filter{it.season!=null&&it.episode!=null}.groupBy{it.season!! to it.episode!!}
    val automation=watcher.associateBy{it.season to it.number}
    return (metadata.keys+library.keys+automation.keys).sortedWith(compareBy<Pair<Int,Int>>{it.first}.thenBy{it.second}).map{key->
        val versions=library[key].orEmpty();val watch=automation[key]
        val episode=metadata[key]?:Episode(key.first,key.second,versions.firstOrNull()?.releaseInfo?.substringAfter(" · ")?:"Episode ${key.second}",released=watch?.airDate)
        val date=runCatching{LocalDate.parse((episode.released?:watch?.airDate).orEmpty().take(10))}.getOrNull()
        val status=when{
            versions.isNotEmpty()->"available"
            date?.isAfter(today)==true->"unreleased"
            watch!=null&&watch.status!="available_jellyfin"->watch.status
            !snapshot.libraryVerified->if(watch?.status=="available_jellyfin")"reported_available"else "unknown"
            date!=null->"missing"
            else->"unknown_release"
        }
        val label=when(status){"available"->"In Jellyfin";"unreleased"->"Not released";"reported_available"->"Reported in Jellyfin";"unknown"->"Library not verified";"missing"->"Missing";"unknown_release"->"Release date unknown";else->watchStatusLabel(status)}
        SeriesCenterEpisode(episode,versions,watch,status,label)
    }
}

/** Reads only. A partial library response never becomes proof that episodes are missing. */
class SeriesCenterRepository{
    suspend fun load(media:MediaItem):SeriesCenterSnapshot=coroutineScope{
        val connected=NativeRuntime.settings.jellyfinConnected
        val account=if(connected)jellyfinScope()else "disconnected"
        val metadata=async{try{loadTrackingSeries(media).let{it.episodes to it.issue}}catch(c:CancellationException){throw c}catch(_:Exception){emptyList<Episode>() to "Episode metadata could not load."}}
        val library=async{
            if(!connected)Triple(emptyList<MediaItem>(),false,"Connect Jellyfin to verify availability.")
            else try{
                val parent=JellyfinPlayback.resolve(media)
                val server=DirectJellyfin.normalizeServer(NativeRuntime.settings.jellyfinUrl.orEmpty())
                val user=NativeRuntime.settings.jellyfinUserId.orEmpty()
                val rows=if(parent==null){
                    require((media.imdbId?:media.mediaKey).matches(Regex("tt\\d+"))){"Series identity is not known."}
                    emptyList()
                }else loadLibraryPages(identity={it.jellyfinId.orEmpty()},fetch={offset,limit->
                    val data=DirectJellyfin.sessionRequest("/Shows/$parent/Episodes",mapOf("UserId" to user,"Fields" to "ProviderIds,Overview,MediaSources,MediaStreams","EnableUserData" to "true","StartIndex" to "$offset","Limit" to "$limit"),expectedScope=account)
                    require(data.get("Items")?.isJsonArray==true)
                    LibraryPage(data.objects("Items").map{jellyfinMediaItem(it,server)},data.number("TotalRecordCount")?.toInt())
                })
                require(account==jellyfinScope())
                Triple(rows,true,null)
            }catch(c:CancellationException){throw c}catch(_:Exception){Triple(emptyList<MediaItem>(),false,"Jellyfin availability could not update. Retry to verify.")}
        }
        val meta=metadata.await();val local=library.await()
        if(connected)require(account==jellyfinScope()){"Jellyfin account changed."}
        SeriesCenterSnapshot(meta.first,local.first,local.second,listOfNotNull(meta.second,local.third))
    }
}
