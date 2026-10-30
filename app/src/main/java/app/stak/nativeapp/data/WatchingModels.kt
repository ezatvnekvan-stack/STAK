package app.stak.nativeapp.data

data class WatchingPin(val media:MediaItem,val active:Boolean=true,val updatedAt:Long=System.currentTimeMillis())
data class WatchingRow(val media:MediaItem,val series:SeriesProgress?=null,val resume:MediaItem?=null,val pin:WatchingPin?=null,val episode:Episode?=null,val lastActivity:Long=0) {
    val key get()="${media.type}:${if(media.isSeries)journalSeriesIdentity(media)else media.mediaKey}"
}
fun sameWatchingTitle(a:MediaItem,b:MediaItem)=if(a.isSeries&&b.isSeries)sameSeries(a,b)else !a.isSeries&&!b.isSeries&&matchingWatch(a.copy(season=null,episode=null),b.copy(season=null,episode=null))

/** The watchlist alone does not imply that a title is being watched. Playback positions come only from Jellyfin. */
fun watchingRows(series:List<SeriesProgress>,states:List<JournalEntry>,resumes:List<MediaItem>,pins:List<WatchingPin>):List<WatchingRow>{
    val result=mutableListOf<WatchingRow>()
    val progress=resumes.filter{!it.watched&&it.positionMs>0}
    val seriesMedia=(series.map{it.plan.media}+progress.filter{it.isSeries}.map{journalSeriesTitle(it)}+pins.filter{it.active&&it.media.isSeries}.map{it.media}).fold(emptyList<MediaItem>()){all,m->if(all.any{sameWatchingTitle(it,m)})all else all+m}
    seriesMedia.forEach{media->
        val plan=series.firstOrNull{sameSeries(it.plan.media,media)}
        val pin=pins.firstOrNull{sameWatchingTitle(it.media,media)}
        val resume=progress.firstOrNull{it.isSeries&&sameSeries(it,media)}
        if(pin?.active==false||plan?.status in setOf("on_hold","dropped"))return@forEach
        if(plan?.complete==true&&resume==null)return@forEach
        val started=states.any{it.played&&it.isEpisode&&sameSeries(it.media,media)}
        val explicit=pin?.active==true||plan?.plan?.status=="watching"
        if(resume==null&&pin?.active!=true&&(plan?.complete==true||!started&&!explicit))return@forEach
        val episode=resume?.season?.let{s->resume.episode?.let{e->Episode(s,e,resume.name,runtime=resume.runtime)}}?:plan?.next
        result+=WatchingRow(plan?.plan?.media?:media,plan,resume,pin,episode,maxOf(pin?.updatedAt?:0,resume?.lastPlayedAt?:0,states.filter{it.played&&sameSeries(it.media,media)}.maxOfOrNull{it.watchedAt?:it.updatedAt}?:0))
    }
    val movies=(progress.filter{!it.isSeries}+pins.filter{it.active&&!it.media.isSeries}.map{it.media}).fold(emptyList<MediaItem>()){all,m->if(all.any{sameWatchingTitle(it,m)})all else all+m}
    movies.forEach{media->
        val pin=pins.firstOrNull{sameWatchingTitle(it.media,media)}
        val last=states.filter{!it.media.isSeries&&sameWatchingTitle(it.media,media)}.maxByOrNull{it.updatedAt}
        if(pin?.active==false||last?.played==true&&(pin==null||pin.updatedAt<(last.watchedAt?:last.updatedAt)))return@forEach
        val resume=progress.firstOrNull{!it.isSeries&&sameWatchingTitle(it,media)}
        result+=WatchingRow(resume?:media,resume=resume,pin=pin,lastActivity=maxOf(pin?.updatedAt?:0,resume?.lastPlayedAt?:0))
    }
    return result.sortedWith(compareByDescending<WatchingRow>{it.lastActivity}.thenBy{it.media.name})
}
fun movieResumeLabel(media:MediaItem?):String {
    if(media==null||media.positionMs<=0)return "Watching"
    if(media.durationMs<=0)return "In progress"
    val remaining=((media.durationMs-media.positionMs).coerceAtLeast(0)+59_999)/60_000
    return "$remaining min left · ${(media.positionMs.toDouble()/media.durationMs*100).toInt().coerceIn(0,100)}%"
}
