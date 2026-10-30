package app.stak.nativeapp.data

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class ActivitySlice(val start:LocalDate,val end:LocalDate,val count:Int){val key:String get()=start.toString()}
data class SeriesActivity(val media:MediaItem,val episodes:Int)

fun individualWatches(entries:List<JournalEntry>)=entries.filter{it.played&&(!it.media.isSeries||it.isEpisode)}.distinctBy{it.key}
fun topWatchedSeries(entries:List<JournalEntry>):List<SeriesActivity> = individualWatches(entries).filter{it.isEpisode}
    .groupBy{journalSeriesIdentity(it.media)}.values.map{rows->SeriesActivity(rows.first().media,rows.size)}
    .sortedWith(compareByDescending<SeriesActivity>{it.episodes}.thenBy{it.media.name}).take(5)
fun trackingGenres(entries:List<JournalEntry>):List<GenreShare> = individualWatches(entries).flatMap{it.media.genres.map(String::trim).filter(String::isNotBlank).distinct()}
    .groupingBy{it}.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String,Int>>{it.value}.thenBy{it.key}).take(5).map{GenreShare(it.key,it.value)}

fun trackingActivity(entries:List<JournalEntry>,monthly:Boolean,period:String,today:LocalDate=LocalDate.now(),zone:ZoneId=ZoneId.systemDefault()):List<ActivitySlice>{
    val dates=individualWatches(entries).mapNotNull{journalDay(it.watchedAt,zone)}.filter{!it.isAfter(today)&&when(period){"week"->!it.isBefore(today.minusDays(6));"month"->!it.isBefore(today.minusDays(29));"this_month"->!it.isBefore(today.withDayOfMonth(1));else->true}}
    if(dates.isEmpty())return emptyList()
    val end=if(period=="all")dates.max()else today
    val selectedStart=when(period){"week"->today.minusDays(6);"month"->today.minusDays(29);"this_month"->today.withDayOfMonth(1);else->null}
    val buckets=if(monthly){
        val month=YearMonth.from(end)
        (11L downTo 0L).map{back->val m=month.minusMonths(back);ActivitySlice(m.atDay(1),m.atEndOfMonth(),0)}
    }else{
        val monday=end.with(DayOfWeek.MONDAY)
        (11L downTo 0L).map{back->val start=monday.minusWeeks(back);ActivitySlice(start,start.plusDays(6),0)}
    }
    val counted=buckets.filter{selectedStart==null||!it.end.isBefore(selectedStart)}.map{slice->slice.copy(count=dates.count{date->!date.isBefore(slice.start)&&!date.isAfter(slice.end)})}
    val first=counted.indexOfFirst{it.count>0}
    return if(first<0)emptyList()else counted.drop(first)
}
fun datedWeeklyAverage(entries:List<JournalEntry>,period:String,today:LocalDate=LocalDate.now(),zone:ZoneId=ZoneId.systemDefault()):Double?{
    val dates=individualWatches(entries).mapNotNull{journalDay(it.watchedAt,zone)}.filter{!it.isAfter(today)&&when(period){"week"->!it.isBefore(today.minusDays(6));"month"->!it.isBefore(today.minusDays(29));"this_month"->!it.isBefore(today.withDayOfMonth(1));else->true}}
    if(dates.isEmpty())return null
    val start=when(period){"week"->today.minusDays(6);"month"->today.minusDays(29);"this_month"->today.withDayOfMonth(1);else->dates.min()}
    val weeks=((ChronoUnit.DAYS.between(start,today)+1)/7.0).coerceAtLeast(1.0)
    return dates.size/weeks
}
fun activityLabel(slice:ActivitySlice,monthly:Boolean):String=if(monthly)slice.start.format(DateTimeFormatter.ofPattern("MMM yyyy",Locale.ENGLISH))else "${weekLabel(slice.start)}–${weekLabel(slice.end)}"

/** Navigate to the known parent series, not an episode with its selection merely cleared. */
fun journalSeriesTitle(media:MediaItem):MediaItem {
    val parent=media.jellyfinSeriesId?.takeIf{it.isNotBlank()}
    return media.copy(id=if(parent!=null&&media.imdbId.isNullOrBlank())"jellyfin:$parent"else media.id,
        jellyfinId=parent?:media.jellyfinId,season=null,episode=null)
}
