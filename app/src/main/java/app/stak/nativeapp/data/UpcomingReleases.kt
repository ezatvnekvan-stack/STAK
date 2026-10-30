package app.stak.nativeapp.data

import com.google.gson.JsonObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun upcomingMovieRelease(data:JsonObject,region:String,today:LocalDate,end:LocalDate):Pair<LocalDate,String>? {
    val candidates=data.objects("results").firstOrNull{it.text("iso_3166_1")==region}?.objects("release_dates").orEmpty().mapNotNull{row->
        val date=releaseDate(row.text("release_date")) ?: return@mapNotNull null
        val type=row.number("type")?.toInt() ?: return@mapNotNull null
        if(date.isBefore(today)||date.isAfter(end)||type !in listOf(2,3,4,5,6))return@mapNotNull null
        Triple(date,type,when(type){4->"Digital";5->"Physical";6->"TV premiere";else->"In cinemas"})
    }
    // Digital home releases are useful here, but never imply that TorBox or Jellyfin has them.
    val chosen=candidates.filter{it.second==4}.minByOrNull{it.first} ?: candidates.minByOrNull{it.first}
    return chosen?.let{it.first to it.third}
}
fun releaseCountdown(date:LocalDate,today:LocalDate):String=when(val days=ChronoUnit.DAYS.between(today,date)){
    0L->"Today";1L->"Tomorrow";in 2..Long.MAX_VALUE->"In $days days";else->"Released"
}
fun upcomingHomeEvents(feed:ReleaseFeed,today:LocalDate):List<ReleaseEvent> = feed.events
    .filter{!it.date.isBefore(today)&&!it.date.isAfter(today.plusDays(30))}
    .distinctBy{it.key}.sortedWith(compareBy<ReleaseEvent>{it.date}.thenBy{it.media.name}.thenBy{it.episode ?: 0})
    .distinctBy{"${it.media.type}:${it.media.mediaKey}"}.take(8)

/** Prefer an English title logo, then language-neutral artwork. Never use an unrelated language. */
fun upcomingLogo(summary:JsonObject,existing:String?=null):String? {
    if(!existing.isNullOrBlank())return existing
    val logos=summary.objectOrNull("images")?.objects("logos").orEmpty()
    val selected=logos.firstOrNull{it.text("iso_639_1")=="en"&&!it.text("file_path").isNullOrBlank()}
        ?: logos.firstOrNull{it.text("iso_639_1").isNullOrBlank()&&!it.text("file_path").isNullOrBlank()}
    return selected?.text("file_path")?.takeIf{it.startsWith("/")&&!it.startsWith("//")}?.let{"https://image.tmdb.org/t/p/w500$it"}
}
