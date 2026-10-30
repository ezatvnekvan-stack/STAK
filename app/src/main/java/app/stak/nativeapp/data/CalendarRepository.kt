package app.stak.nativeapp.data

import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.*
import java.util.concurrent.ConcurrentHashMap

data class ReleaseEvent(val media:MediaItem,val date:LocalDate,val title:String,val season:Int?=null,val episode:Int?=null,val artwork:String?=null,val releaseLabel:String?=null) {
    val key:String get()="${media.type}:${media.mediaKey}:${season ?: 0}:${episode ?: 0}"
}
data class ReleaseFeed(val events:List<ReleaseEvent>,val issues:List<String>,val checked:Int)
fun calendarCells(month:YearMonth,weekStart:Int=1):List<LocalDate> {
    val first=month.atDay(1);val start=if(weekStart==0)7 else 1
    val offset=(first.dayOfWeek.value-start+7)%7
    return (0L..41L).map{first.minusDays(offset.toLong()).plusDays(it)}
}
internal fun releaseDate(raw:String?):LocalDate?=raw?.take(10)?.let{runCatching{LocalDate.parse(it)}.getOrNull()}
internal fun calendarSeasons(summary:JsonObject,start:LocalDate,end:LocalDate):Pair<List<Int>,Boolean> {
    val seasons=summary.objects("seasons").filter{(it.number("season_number") ?: 0.0)>0&&releaseDate(it.text("air_date"))!=null}.sortedBy{it.text("air_date")}
    val picked=seasons.filterIndexed{index,s->!releaseDate(s.text("air_date"))!!.isAfter(end)&&(index==seasons.lastIndex||releaseDate(seasons[index+1].text("air_date"))!!.isAfter(start))}.mapNotNull{it.number("season_number")?.toInt()}.toMutableList()
    listOf("next_episode_to_air","last_episode_to_air").mapNotNull{summary.objectOrNull(it)}.forEach{ep->val date=releaseDate(ep.text("air_date"));val number=ep.number("season_number")?.toInt();if(number!=null&&number>0&&(seasons.isEmpty()||date!=null&&!date.isBefore(start)&&!date.isAfter(end)))picked+=number}
    val unique=picked.distinct();return unique.take(4) to (unique.size>4)
}
class CalendarRepository(
    private val key:()->String={NativeRuntime.settings.tmdbKey.orEmpty()},
    private val request:(String,Map<String,String>,String?)->JsonObject=::providerJson
) {
    private val cache=ConcurrentHashMap<String,Pair<Long,JsonObject>>()
    private fun fetch(path:String,params:Map<String,String> = emptyMap(),force:Boolean=false):JsonObject {
        val secret=key();require(secret.isNotBlank()){ "Configure TMDb in Metadata sources to load the release calendar." }
        val scope=credentialScope(secret);val cacheKey="$scope:$path:$params"
        if(!force)cache[cacheKey]?.takeIf{System.currentTimeMillis()-it.first<300_000}?.let{return it.second}
        val data=request("https://api.themoviedb.org/3$path",params+mapOf("language" to "en-US")+if(secret.contains('.'))emptyMap() else mapOf("api_key" to secret),secret.takeIf{it.contains('.')})
        if(cache.size>256)cache.clear();cache[cacheKey]=System.currentTimeMillis() to data;return data
    }
    /** Home checks the next scheduled episode, not every historical season. Dates are schedules, not availability. */
    suspend fun upcoming(followed:List<MediaItem>,today:LocalDate,region:String="US",force:Boolean=false):ReleaseFeed=withContext(Dispatchers.IO){
        if(followed.isEmpty())return@withContext ReleaseFeed(emptyList(),emptyList(),0)
        require(key().isNotBlank()){ "Configure TMDb in Metadata & ratings to load Coming up." }
        require(region.matches(Regex("[A-Z]{2}")))
        val end=today.plusDays(30);val permits=Semaphore(3)
        val unique=followed.distinctBy{"${it.type}:${it.mediaKey}"}
        val results=unique.map{item->async{permits.withPermit{
            try{
                val kind=if(item.isSeries)"tv"else "movie"
                val id=item.id.takeIf{it.matches(Regex("tmdb:[0-9]+"))}?.substringAfter(':') ?: (item.imdbId ?: item.mediaKey).takeIf{it.matches(Regex("tt[0-9]+"))}?.let{fetch("/find/$it",mapOf("external_source" to "imdb_id"),force).objects(if(item.isSeries)"tv_results"else "movie_results").firstOrNull()?.text("id")}
                    ?: throw IllegalArgumentException("Title identity unavailable")
                require(id.matches(Regex("[0-9]+")))
                val data=fetch("/$kind/$id",mapOf("append_to_response" to "images","include_image_language" to "en,null"),force)
                val media=item.copy(logo=upcomingLogo(data,item.logo),poster=item.poster ?: data.text("poster_path")?.let{"https://image.tmdb.org/t/p/w500$it"},background=item.background ?: data.text("backdrop_path")?.let{"https://image.tmdb.org/t/p/w780$it"})
                val event=if(item.isSeries){
                    val next=data.objectOrNull("next_episode_to_air") ?: data.objects("seasons").filter{(it.number("season_number") ?: 0.0)>0}.filter{releaseDate(it.text("air_date"))?.let{date->!date.isBefore(today)&&!date.isAfter(end)}==true}.minByOrNull{it.text("air_date").orEmpty()}?.number("season_number")?.toInt()?.let{season->fetch("/tv/$id/season/$season",force=force).objects("episodes").filter{releaseDate(it.text("air_date"))?.let{date->!date.isBefore(today)&&!date.isAfter(end)}==true}.minByOrNull{it.number("episode_number") ?: Double.MAX_VALUE}}
                    val date=releaseDate(next?.text("air_date"));val season=next?.number("season_number")?.toInt();val episode=next?.number("episode_number")?.toInt()
                    if(date!=null&&season!=null&&season>=0&&episode!=null&&episode>0&&!date.isBefore(today)&&!date.isAfter(end))ReleaseEvent(media,date,next.text("name").orEmpty(),season,episode,next.text("still_path")?.let{"https://image.tmdb.org/t/p/w780$it"},"Episode")else null
                }else{
                    val releases=fetch("/movie/$id/release_dates",force=force)
                    upcomingMovieRelease(releases,region,today,end)?.let{(date,label)->ReleaseEvent(media,date,item.name,releaseLabel="$region · $label")}
                        ?: releaseDate(data.text("release_date"))?.takeIf{!it.isBefore(today)&&!it.isAfter(end)}?.let{ReleaseEvent(media,it,item.name,releaseLabel="Release date")}
                }
                event to null
            }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){null to "${item.name}: release date could not load."}
        }}}.awaitAll()
        ReleaseFeed(results.mapNotNull{it.first}.sortedWith(compareBy<ReleaseEvent>{it.date}.thenBy{it.media.name}),results.mapNotNull{it.second},unique.size)
    }
    suspend fun load(followed:List<MediaItem>,start:LocalDate,end:LocalDate,force:Boolean=false):ReleaseFeed=withContext(Dispatchers.IO) {
        require(!start.isAfter(end)&&java.time.temporal.ChronoUnit.DAYS.between(start,end)<=62)
        if(followed.isEmpty())return@withContext ReleaseFeed(emptyList(),emptyList(),0)
        require(key().isNotBlank()){ "Configure TMDb in Metadata sources to load the release calendar." }
        val permits=Semaphore(3)
        val results=followed.distinctBy{"${it.type}:${it.mediaKey}"}.map{item->async{permits.withPermit{
            val events=mutableListOf<ReleaseEvent>();val issues=mutableListOf<String>()
            try {
                val kind=if(item.isSeries)"tv" else "movie"
                val id=item.id.takeIf{it.matches(Regex("tmdb:\\d+"))}?.substringAfter(':') ?: item.imdbId?.takeIf{it.matches(Regex("tt\\d+"))}?.let{imdb->fetch("/find/$imdb",mapOf("external_source" to "imdb_id"),force).objects(if(item.isSeries)"tv_results" else "movie_results").firstOrNull()?.text("id")}
                    ?: item.mediaKey.takeIf{it.matches(Regex("tt\\d+"))}?.let{imdb->fetch("/find/$imdb",mapOf("external_source" to "imdb_id"),force).objects(if(item.isSeries)"tv_results" else "movie_results").firstOrNull()?.text("id")}
                    ?: throw IllegalArgumentException("Title identity unavailable")
                require(id.matches(Regex("\\d+")))
                val summary=fetch("/$kind/$id",force=force)
                val media=item.copy(poster=item.poster ?: summary.text("poster_path")?.let{"https://image.tmdb.org/t/p/w500$it"})
                val candidates=mutableListOf<JsonObject>()
                if(item.isSeries) {
                    listOf("next_episode_to_air","last_episode_to_air").mapNotNullTo(candidates){summary.objectOrNull(it)}
                    val (seasons,limited)=calendarSeasons(summary,start,end)
                    if(limited)issues+="${item.name}: season coverage was limited for this month."
                    for(season in seasons)try {candidates+=fetch("/tv/$id/season/$season",force=force).objects("episodes")}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){issues+="${item.name}: a season could not be checked."}
                    if(seasons.isEmpty()&&candidates.isEmpty()&&summary.objects("seasons").any{(it.number("season_number") ?: 0.0)>0&&releaseDate(it.text("air_date"))==null})issues+="${item.name}: episode dates have not been announced."
                } else candidates+=JsonObject().apply{addProperty("air_date",summary.text("release_date"));addProperty("name",summary.text("title"))}
                for(candidate in candidates) {
                    val date=releaseDate(candidate.text("air_date")) ?: continue
                    if(date.isBefore(start)||date.isAfter(end))continue
                    val season=candidate.number("season_number")?.toInt();val episode=candidate.number("episode_number")?.toInt()
                    if(item.isSeries&&(season==null||episode==null))continue
                    events+=ReleaseEvent(media,date,candidate.text("name") ?: item.name,season,episode,candidate.text("still_path")?.let{"https://image.tmdb.org/t/p/w500$it"})
                }
            } catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){issues+="${item.name}: release metadata could not load. Retry or check TMDb settings."}
            events to issues
        }}}.awaitAll()
        ReleaseFeed(results.flatMap{it.first}.associateBy{it.key}.values.sortedWith(compareBy<ReleaseEvent>{it.date}.thenBy{it.media.name}.thenBy{it.season ?: 0}.thenBy{it.episode ?: 0}),results.flatMap{it.second},followed.size)
    }
}
