package app.stak.nativeapp.data

import java.time.*
data class InsightBucket(val label:String,val count:Int)
fun monthActivity(entries:List<JournalEntry>,today:LocalDate,zone:ZoneId=ZoneId.systemDefault()):List<InsightBucket>{
    val month=YearMonth.from(today);val dated=entries.filter{it.played}.mapNotNull{journalDay(it.watchedAt,zone)}
    return (5 downTo 0).map{back->val m=month.minusMonths(back.toLong());InsightBucket(m.toString(),dated.count{YearMonth.from(it)==m})}
}
fun libraryQuality(items:List<MediaItem>):List<InsightBucket> {
    val rows=items.filter{!it.isSeries}.distinctBy{it.jellyfinId ?: it.id}
    return rows.groupingBy{when{it.jellyfinVideoHeight==null->"Unknown";it.jellyfinVideoHeight>=2000->"2160p";it.jellyfinVideoHeight>=1000->"1080p";it.jellyfinVideoHeight>=700->"720p";else->"Other"}}.eachCount().entries.sortedByDescending{it.value}.map{InsightBucket(it.key,it.value)}
}
fun libraryGrowth(items:List<MediaItem>,today:LocalDate,zone:ZoneId=ZoneId.systemDefault()):List<InsightBucket>{
    val dated=items.distinctBy{it.jellyfinId ?: it.id}.mapNotNull{it.addedAt.takeIf{v->v>0}?.let{v->Instant.ofEpochMilli(v).atZone(zone).toLocalDate()}}
    return (5 downTo 0).map{back->val m=YearMonth.from(today).minusMonths(back.toLong());InsightBucket(m.toString(),dated.count{YearMonth.from(it)==m})}
}
fun completedAvailableSeries(items:List<MediaItem>)=items.filter{it.isSeries&&it.jellyfinUserDataKnown==true&&it.jellyfinUnplayedCount==0&&(it.jellyfinKnownItemCount ?: 0)>0}.distinctBy{it.jellyfinId ?: it.id}.size
