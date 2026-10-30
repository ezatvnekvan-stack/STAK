package app.stak.nativeapp.data

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ActivityDay(val date:LocalDate,val count:Int)
fun activityRangeLabel(slice:ActivitySlice,monthly:Boolean):String {
    fun LocalDate.formatEnglish(pattern:String)=format(DateTimeFormatter.ofPattern(pattern,Locale.ENGLISH))
    return if(monthly)slice.start.formatEnglish("MMMM yyyy")else if(slice.start.year!=slice.end.year)"${slice.start.formatEnglish("MMM d, yyyy")} – ${slice.end.formatEnglish("MMM d, yyyy")}"else "${slice.start.formatEnglish("MMM d")} – ${slice.end.formatEnglish("MMM d, yyyy")}"
}
fun activityDays(slice:ActivitySlice,entries:List<JournalEntry>,zone:ZoneId=ZoneId.systemDefault(),today:LocalDate=LocalDate.now(zone)):List<ActivityDay>{
    val dated=individualWatches(entries).mapNotNull{journalDay(it.watchedAt,zone)}.filter{!it.isAfter(today)}.groupingBy{it}.eachCount()
    return (0L..java.time.temporal.ChronoUnit.DAYS.between(slice.start,slice.end)).map{offset->val date=slice.start.plusDays(offset);ActivityDay(date,dated[date]?:0)}
}
fun watchesOn(entries:List<JournalEntry>,day:LocalDate,zone:ZoneId=ZoneId.systemDefault())=individualWatches(entries).filter{journalDay(it.watchedAt,zone)==day}.sortedByDescending{it.watchedAt}
