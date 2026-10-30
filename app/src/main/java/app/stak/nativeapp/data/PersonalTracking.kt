package app.stak.nativeapp.data

import app.stak.nativeapp.data.db.TrackingEntity
import java.time.LocalDate
import java.util.UUID

data class WatchEvent(val id:String,val media:MediaItem,val watchedAt:Long?,val source:String,val scope:String,val createdAt:Long=System.currentTimeMillis()) {
    fun journal()=JournalEntry(media,watchedAt,true,source,createdAt,scope,eventId=id)
}
data class SeriesPlan(val media:MediaItem,val status:String="watching",val episodes:List<Episode> = emptyList(),val refreshedAt:Long=0,val completeMetadata:Boolean=false,val issue:String?=null)
data class PersonalRating(val key:String,val media:MediaItem,val value:Int,val updatedAt:Long=System.currentTimeMillis())
data class TrackerState(val version:Int=1,val events:List<WatchEvent> = emptyList(),val plans:List<SeriesPlan> = emptyList(),val ratings:List<PersonalRating> = emptyList(),val remote:List<JournalEntry> = emptyList(),val imported:List<String> = emptyList(),val suppressed:List<String> = emptyList(),val pins:List<WatchingPin> = emptyList())

fun watchIdentity(media:MediaItem)="${media.type}:${media.mediaKey}:${media.season?:0}:${media.episode?:0}"
fun matchingWatch(a:MediaItem,b:MediaItem):Boolean = a.type==b.type&&a.season==b.season&&a.episode==b.episode&&
    (a.mediaKey==b.mediaKey||(a.jellyfinId!=null&&a.jellyfinId==b.jellyfinId)||(a.isSeries&&journalSeriesIdentity(a)==journalSeriesIdentity(b)))
fun sameSeries(a:MediaItem,b:MediaItem)=a.isSeries&&b.isSeries&&(journalSeriesIdentity(a)==journalSeriesIdentity(b)||a.sameIdentity(b)||a.jellyfinSeriesId?.let{it==b.jellyfinSeriesId||it==b.jellyfinId}==true||b.jellyfinSeriesId?.let{it==a.jellyfinId}==true)
fun TrackerState.addManual(row:TrackingEntity,previous:TrackingEntity?):TrackerState {
    val entry=row.toJournal()
    var next=this
    if(row.played&&previous?.played!=true){
        val existing=events.any{matchingWatch(it.media,entry.media)&&(it.watchedAt==entry.watchedAt||(it.watchedAt!=null&&entry.watchedAt!=null&&kotlin.math.abs(it.watchedAt-entry.watchedAt)<60_000))}
        if(!existing)next=next.copy(events=events+WatchEvent(UUID.randomUUID().toString(),entry.media,entry.watchedAt,"manual",row.scope))
    }
    if(entry.media.isSeries&&entry.isEpisode&&next.plans.none{sameSeries(it.media,entry.media)})next=next.copy(plans=next.plans+SeriesPlan(journalSeriesTitle(entry.media)))
    return next
}
fun TrackerState.importRows(rows:List<TrackingEntity>,scope:String):TrackerState {
    var next=this
    rows.filter{it.scope==scope}.forEach{row->
        val id="legacy:${row.type}:${row.id}"
        if(id !in next.imported){
            val e=row.toJournal()
            val exists=next.events.any{matchingWatch(it.media,e.media)&&it.watchedAt==e.watchedAt}
            next=next.copy(imported=next.imported+id,events=if(row.played&&!exists)next.events+WatchEvent(id,e.media,e.watchedAt,"manual",scope)else next.events)
        }
    }
    return next
}
fun TrackerState.observeRemote(rows:List<JournalEntry>,scope:String):TrackerState {
    var next=this
    rows.filter{it.scope==scope||it.scope=="local"}.forEach{entry->
        if(!entry.played&&next.remote.none{matchingWatch(it.media,entry.media)}&&next.events.none{matchingWatch(it.media,entry.media)})return@forEach
        if(entry.played){
            val id="jellyfin:${credentialScope(entry.media.jellyfinId?:watchIdentity(entry.media))}:${entry.watchedAt?:"unknown"}"
            val echo=next.events.any{matchingWatch(it.media,entry.media)&&((it.watchedAt==null&&entry.watchedAt==null)||(it.watchedAt!=null&&entry.watchedAt!=null&&kotlin.math.abs(it.watchedAt-entry.watchedAt)<60_000))}
            if(id !in next.suppressed&&"watch:${watchIdentity(entry.media)}:${entry.watchedAt?:"unknown"}" !in next.suppressed&&next.events.none{it.id==id}&&!echo)next=next.copy(events=next.events+WatchEvent(id,entry.media,entry.watchedAt,"Jellyfin",scope))
        }
        next=next.copy(remote=next.remote.filterNot{matchingWatch(it.media,entry.media)}+entry.copy(scope=scope))
    }
    return next
}

data class SeriesProgress(val plan:SeriesPlan,val released:List<Episode>,val watched:Set<Pair<Int,Int>>,val next:Episode?,val upcoming:Episode?,val unknownDates:Int,val complete:Boolean,val status:String) {
    val watchedCount get()=released.count{it.season to it.episode in watched}
    val fraction get()=if(released.isEmpty())0f else watchedCount.toFloat()/released.size
}
fun seriesProgress(plan:SeriesPlan,states:List<JournalEntry>,today:LocalDate=LocalDate.now()):SeriesProgress {
    val episodes=plan.episodes.filter{it.season>0&&it.episode>0}.distinctBy{it.season to it.episode}.sortedWith(compareBy({it.season},{it.episode}))
    val watched=states.filter{it.isEpisode&&it.played&&sameSeries(it.media,plan.media)}.map{it.media.season!! to it.media.episode!!}.toSet()
    val released=episodes.filter{releaseDate(it.released)?.let{d->!d.isAfter(today)}==true}
    val unknown=episodes.count{releaseDate(it.released)==null}
    val completed=released.isNotEmpty()&&released.all{it.season to it.episode in watched}&&unknown==0&&plan.completeMetadata
    val status=when{plan.status in setOf("on_hold","dropped")->plan.status;completed->"completed";plan.status=="plan"&&watched.isEmpty()->"plan";else->"watching"}
    return SeriesProgress(plan,released,watched,released.firstOrNull{it.season to it.episode !in watched},episodes.firstOrNull{releaseDate(it.released)?.isAfter(today)==true},unknown,completed,status)
}
fun seriesStatusLabel(status:String)=when(status){"plan"->"Plan to watch";"on_hold"->"On hold";"dropped"->"Dropped";"completed"->"Completed";else->"Watching"}
