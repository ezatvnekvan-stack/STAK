package app.stak.nativeapp.data

import app.stak.nativeapp.data.db.TrackingEntity
import com.google.gson.Gson
import java.time.*
import java.time.temporal.ChronoUnit

data class JournalEntry(val media:MediaItem,val watchedAt:Long?,val played:Boolean=true,val source:String="manual",val updatedAt:Long=0,val scope:String="local",val eventId:String?=null) {
    val key:String get()=eventId ?: "${media.type}:${media.mediaKey}:${media.season ?: 0}:${media.episode ?: 0}"
    val isEpisode:Boolean get()=media.isSeries&&media.season!=null&&media.episode!=null
}
data class JournalPreferences(val view:String="watching",val period:String="all",val type:String="all",val query:String="",val date:String=LocalDate.now().toString(),val calendarType:String="all",val weekStart:Int=1,val artwork:Boolean=false,val trackerVersion:Int=2)
data class WatchlistPreferences(val query:String="",val type:String="all",val sort:String="added",val layout:String="grid",val hideWatched:Boolean=false,val cachedOnly:Boolean=false)
fun trackingEntity(media:MediaItem,episode:Episode?,played:Boolean,watchedAt:Long=System.currentTimeMillis(),scope:String="local"):TrackingEntity {
    val target=media.copy(season=episode?.season ?: media.season,episode=episode?.episode ?: media.episode,runtime=episode?.runtime ?: media.runtime,watched=played,progress=if(played)1f else 0f)
    val ep=target.season!=null&&target.episode!=null
    return TrackingEntity(if(ep)"${media.mediaKey}:${target.season}:${target.episode}" else media.mediaKey,if(ep)"episode" else media.type,if(ep)"${media.name} · S${target.season} E${target.episode}" else media.name,played,if(played)1f else 0f,if(played)watchedAt else null,Gson().toJson(target),scope,System.currentTimeMillis())
}
fun TrackingEntity.toJournal():JournalEntry {
    val coordinates=if(type=="episode")Regex("^(.*):(\\d+):(\\d+)$").matchEntire(id) else null
    val identity=coordinates?.groupValues?.get(1) ?: id
    val fallback=MediaItem(identity,identity.takeIf{it.matches(Regex("tt\\d+"))},if(type=="episode")"series" else type,name.substringBefore(" · S"),season=coordinates?.groupValues?.get(2)?.toIntOrNull(),episode=coordinates?.groupValues?.get(3)?.toIntOrNull())
    val media=runCatching{mediaJson?.let{Gson().fromJson(it,MediaItem::class.java)}}.getOrNull() ?: fallback
    return JournalEntry(media.copy(watched=played,progress=progress),lastPlayed,played,"manual",updatedAt,scope)
}
fun mergeJournal(local:List<TrackingEntity>,remote:List<JournalEntry>,scope:String):List<JournalEntry> {
    val result=remote.filter{it.scope=="local"||it.scope==scope}.associateBy{it.key}.toMutableMap()
    local.filter{it.scope=="local"||it.scope==scope}.map{it.toJournal()}.forEach{entry->
        val existing=result[entry.key] ?: result.values.firstOrNull{entry.media.jellyfinId!=null&&it.media.jellyfinId==entry.media.jellyfinId}
        if(existing==null||entry.updatedAt>=(existing.watchedAt ?: 0)) {
            existing?.let{result.remove(it.key)}
            result[entry.key]=if(existing==null)entry else entry.copy(media=entry.media.copy(poster=entry.media.poster ?: existing.media.poster,background=entry.media.background ?: existing.media.background,runtime=entry.media.runtime ?: existing.media.runtime,durationMs=entry.media.durationMs.takeIf{it>0} ?: existing.media.durationMs,jellyfinId=entry.media.jellyfinId ?: existing.media.jellyfinId,jellyfinSeriesId=entry.media.jellyfinSeriesId ?: existing.media.jellyfinSeriesId))
        }
    }
    return result.values.filter{it.played}.sortedWith(compareByDescending<JournalEntry>{it.watchedAt ?: Long.MIN_VALUE}.thenBy{it.key})
}
fun journalDay(timestamp:Long?,zone:ZoneId=ZoneId.systemDefault()):LocalDate?=timestamp?.let{Instant.ofEpochMilli(it).atZone(zone).toLocalDate()}
fun filterJournal(items:List<JournalEntry>,preferences:JournalPreferences,today:LocalDate=LocalDate.now(),zone:ZoneId=ZoneId.systemDefault()):List<JournalEntry> {
    val start=when(preferences.period){"week"->today.minusDays(6);"month"->today.minusDays(29);"this_month"->today.withDayOfMonth(1);else->null}
    return items.filter{entry->(preferences.type=="all"||if(preferences.type=="episode")entry.isEpisode else entry.media.type==preferences.type)&&
        (preferences.query.isBlank()||librarySearchText(entry.media.name).contains(librarySearchText(preferences.query)))&&
        (start==null||journalDay(entry.watchedAt,zone)?.let{!it.isBefore(start)&&!it.isAfter(today)}==true)}
}
fun runtimeMinutes(item:MediaItem):Long?=item.durationMs.takeIf{it>0}?.div(60_000) ?: item.runtime?.let{raw->
    Regex("(?i)(\\d+)\\s*h").find(raw)?.groupValues?.get(1)?.toLong()?.let{hours->hours*60+(Regex("(?i)(\\d+)\\s*m").find(raw)?.groupValues?.get(1)?.toLong() ?: 0)} ?: Regex("(?i)(\\d+)\\s*m").find(raw)?.groupValues?.get(1)?.toLong()
}
fun watchedOn(date:LocalDate,today:LocalDate=LocalDate.now(),zone:ZoneId=ZoneId.systemDefault(),now:Instant=Instant.now()):Long {
    require(!date.isAfter(today)){"Choose today or an earlier date."}
    return if(date==today)now.toEpochMilli() else date.atTime(12,0).atZone(zone).toInstant().toEpochMilli()
}
fun filterSavedTitles(items:List<MediaItem>,prefs:WatchlistPreferences,watched:Set<String>,cached:Set<String>):List<MediaItem> {
    val query=librarySearchText(prefs.query.trim())
    val filtered=items.filter{(prefs.type=="all"||it.type==prefs.type)&&(!prefs.hideWatched||it.mediaKey !in watched)&&(!prefs.cachedOnly||"${it.type}:${it.mediaKey}" in cached)&&(query.isBlank()||librarySearchText(it.name).contains(query)||it.mediaKey.contains(query,true))}
    val order=when(prefs.sort){"year"->compareByDescending<MediaItem>{it.year?.toIntOrNull() ?: 0};"rating"->compareByDescending{it.rating ?: -1.0};"name"->compareBy{librarySearchText(it.name)};else->compareByDescending{it.addedAt}}
    return filtered.sortedWith(order.thenBy{librarySearchText(it.name)}.thenBy{it.mediaKey})
}
