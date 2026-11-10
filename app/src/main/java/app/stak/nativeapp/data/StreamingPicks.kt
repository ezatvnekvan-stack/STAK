package app.stak.nativeapp.data

import com.google.gson.JsonObject
import kotlinx.coroutines.*
import java.io.IOException
import java.time.LocalDate

/** Region-specific subscriptions/free streaming plus a past digital date, not theatrical-only picks. */
class StreamingPicks(
    private val key:()->String={NativeRuntime.settings.tmdbKey.orEmpty()},
    private val request:(String,Map<String,String>,String?)->JsonObject=::providerJson,
    private val today:()->LocalDate={LocalDate.now()}
){
    private fun get(path:String,params:Map<String,String> = emptyMap()):JsonObject{
        val token=key();require(token.isNotBlank()){"Streaming selections need TMDb metadata."}
        return request("https://api.themoviedb.org/3$path",if(token.contains('.'))params else params+("api_key" to token),if(token.contains('.'))token else null)
    }
    suspend fun load(type:String,region:String,provider:String=""):DiscoverResult=withContext(Dispatchers.IO){
        require(type in listOf("movie","series")&&region.matches(Regex("[A-Z]{2}")))
        val kind=if(type=="series")"tv"else "movie"
        val params=mutableMapOf("page" to "1","include_adult" to "false","sort_by" to "popularity.desc","watch_region" to region,"with_watch_monetization_types" to "flatrate|free|ads")
        if(provider.isNotBlank())params["with_watch_providers"]=provider
        if(type=="movie"){params["region"]=region;params["with_release_type"]="4";params["release_date.lte"]=today().toString()}
        else params["first_air_date.lte"]=today().toString()
        val candidates=get("/discover/$kind",params).objects("results").take(12)
        val eligible=mutableListOf<MediaItem>()
        var failed=false
        for(group in candidates.chunked(3)){
            val resolved=supervisorScope{group.map{row->async{
                try{
                    val id=row.text("id")?.takeIf{it.matches(Regex("\\d+"))}?:return@async null
                    val detail=get("/$kind/$id",mapOf("append_to_response" to if(type=="movie")"images,external_ids,release_dates"else "images,external_ids","include_image_language" to "en,null"))
                    if(detail.text("id")!=id)return@async null
                    val providers=get("/$kind/$id/watch/providers").objectOrNull("results")?.objectOrNull(region)
                    if(!streamingConfirmed(detail,providers,type,region,today(),provider))return@async null
                    val logos=detail.objectOrNull("images")?.objects("logos").orEmpty()
                    val logo=(logos.firstOrNull{it.text("iso_639_1")=="en"}?:logos.firstOrNull{it.text("iso_639_1")==null})?.text("file_path")
                    DirectCatalog.tmdbItem(detail,type).copy(imdbId=detail.objectOrNull("external_ids")?.text("imdb_id"),logo=logo?.takeIf{it.startsWith('/')&&!it.startsWith("//")}?.let{"https://image.tmdb.org/t/p/w500$it"})
                }catch(c:CancellationException){throw c}catch(_:Exception){failed=true;null}
            }}.awaitAll()}
            eligible+=resolved.filterNotNull()
            if(eligible.size>=3)break
        }
        if(eligible.isEmpty()&&failed)throw IOException("Streaming availability could not be verified.")
        DiscoverResult(items=eligible.take(3),source="tmdb-streaming",partialErrors=if(failed)listOf("Some streaming availability checks failed.")else emptyList())
    }
}

internal fun streamingConfirmed(detail:JsonObject,providers:JsonObject?,type:String,region:String,today:LocalDate,provider:String=""):Boolean{
    if(providers==null||listOf("flatrate","free","ads").none{providers.objects(it).any{p->(p.number("provider_id")?:0.0)>0}})return false
    if(provider.isNotBlank()&&listOf("flatrate","free","ads").none{bucket->providers.objects(bucket).any{it.text("provider_id") in provider.split('|')}})return false
    fun released(raw:String?):Boolean=raw?.take(10)?.let{runCatching{!LocalDate.parse(it).isAfter(today)}.getOrDefault(false)}?:false
    if(type=="series")return released(detail.text("first_air_date"))
    if(detail.text("status")!="Released")return false
    val dates=detail.objectOrNull("release_dates")?.objects("results")?.firstOrNull{it.text("iso_3166_1")==region}?.objects("release_dates").orEmpty()
    return dates.any{it.number("type")?.toInt()==4&&released(it.text("release_date"))}
}
