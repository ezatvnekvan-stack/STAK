package app.stak.nativeapp.data

import com.google.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class TraktPage(val entries:JsonArray,val pages:Int)
internal fun traktCatalogRequest(path:String,params:Map<String,String>,clientId:String):TraktPage {
    require(path.matches(Regex("/(movies|shows)/(trending|anticipated)")))
    val url=("https://api.trakt.tv"+path).toHttpUrl().newBuilder().apply{params.forEach{(k,v)->addQueryParameter(k,v)}}.build()
    val request=Request.Builder().url(url).header("Content-Type","application/json").header("User-Agent","STAK/1.2.3").header("trakt-api-key",clientId).header("trakt-api-version","2").build()
    // Never forward an app key to a redirect target; reuse the shared provider pool.
    providerClient.newBuilder().followRedirects(false).followSslRedirects(false).build().newCall(request).execute().use{response->
        if(!response.isSuccessful)throw IOException(when(response.code){401,403->"Trakt access denied. Check your Client ID in Metadata & ratings.";429->"Trakt rate limit reached. Please try again later.";else->"Trakt could not load (${response.code}). Please retry."})
        val source=response.body?.source() ?: throw IOException("Trakt returned an empty response.")
        source.request(2*1024*1024+1L);require(source.buffer.size<=2*1024*1024)
        val parsed=try{JsonParser.parseString(source.buffer.readUtf8())}catch(_:RuntimeException){throw IOException("Trakt returned invalid data.")}
        if(!parsed.isJsonArray)throw IOException("Trakt returned invalid data.")
        return TraktPage(parsed.asJsonArray,response.header("X-Pagination-Page-Count")?.toIntOrNull()?.coerceIn(1,1000) ?: params["page"]!!.toInt())
    }
}
fun parseTraktItems(raw:JsonArray,type:String):List<MediaItem> {
    val kind=if(type=="series")"show"else "movie"
    return raw.mapNotNull{entry->runCatching{
        val obj=entry.asJsonObject.objectOrNull(kind) ?: return@runCatching null
        val ids=obj.objectOrNull("ids") ?: return@runCatching null
        val imdb=ids.text("imdb")?.takeIf{it.matches(Regex("tt[0-9]+"))}
        val tmdb=ids.text("tmdb")?.takeIf{it.matches(Regex("[1-9][0-9]*"))}
        val id=tmdb?.let{"tmdb:$it"} ?: imdb ?: return@runCatching null
        val title=obj.text("title")?.takeIf{it.isNotBlank()} ?: return@runCatching null
        MediaItem(id,imdb,type,title,year=obj.text("year"),overview=obj.text("overview"),runtime=obj.number("runtime")?.toInt()?.takeIf{it>0}?.let{"$it min"},genres=obj.get("genres")?.takeIf{it.isJsonArray}?.asJsonArray?.mapNotNull{it.takeIf{it.isJsonPrimitive}?.asString}.orEmpty())
    }.getOrNull()}.distinctBy{"${it.type}:${it.mediaKey}"}
}
class TraktCatalog(
    private val key:()->String={NativeRuntime.settings.traktClientId.orEmpty()},
    private val request:(String,Map<String,String>,String)->TraktPage=::traktCatalogRequest,
    private val artwork:suspend(MediaItem)->MediaItem={TraktArtwork.load(it)}
) {
    private val cache=ConcurrentHashMap<String,Pair<Long,DiscoverResult>>()
    suspend fun list(type:String,category:String,page:Int=1,limit:Int=20):DiscoverResult=withContext(Dispatchers.IO){
        require(type in listOf("movie","series")&&category in listOf("trending","anticipated")&&page in 1..1000&&limit in 1..20)
        val secret=key().trim();if(secret.isBlank())throw IOException("Add your Trakt Client ID in Settings → Metadata & ratings.")
        require(secret.length<=256&&secret.none{it.isWhitespace()}){"Invalid Trakt Client ID."}
        val scope=credentialScope(secret);val cacheKey="$scope:$type:$category:$page:$limit"
        cache[cacheKey]?.takeIf{System.currentTimeMillis()-it.first<3_600_000}?.let{return@withContext it.second}
        val data=request("/${if(type=="series")"shows"else "movies"}/$category",mapOf("page" to "$page","limit" to "$limit","extended" to "full"),secret)
        val permits=Semaphore(3)
        val items=coroutineScope{parseTraktItems(data.entries,type).map{item->async{permits.withPermit{try{artwork(item)}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){item}}}}.awaitAll()}
        val result=DiscoverResult(items,page,data.pages.coerceAtLeast(page),"trakt",notice="Trakt · ${if(category=="anticipated")"Most anticipated"else "Trending"} ${if(type=="series")"series"else "movies"}")
        if(cache.size>=48)cache.clear();cache[cacheKey]=System.currentTimeMillis() to result;result
    }
}
private object TraktArtwork {
    private val cache=ConcurrentHashMap<String,Pair<Long,MediaItem>>()
    suspend fun load(item:MediaItem):MediaItem=withContext(Dispatchers.IO){
        val settings=NativeRuntime.settings;val key=settings.tmdbKey.orEmpty();val cacheKey="${credentialScope(key)}:${item.type}:${item.id}"
        cache[cacheKey]?.takeIf{System.currentTimeMillis()-it.first<86_400_000}?.let{return@withContext item.copy(poster=it.second.poster,background=it.second.background)}
        val image=if(key.isNotBlank()&&item.id.startsWith("tmdb:")){
            val data=providerJson("https://api.themoviedb.org/3/${if(item.isSeries)"tv"else "movie"}/${item.id.substringAfter(':')}",mapOf("language" to "en-US")+if(key.contains('.'))emptyMap()else mapOf("api_key" to key),key.takeIf{it.contains('.')})
            fun image(field:String)=data.text(field)?.let{"https://image.tmdb.org/t/p/w500$it"}
            item.copy(poster=image("poster_path"),background=image("backdrop_path"))
        } else if(item.imdbId!=null){
            val data=providerJson("https://v3-cinemeta.strem.io/meta/${item.type}/${item.imdbId}.json").objectOrNull("meta")
            item.copy(poster=data?.text("poster"),background=data?.text("background"))
        }else item
        if(cache.size>=256)cache.clear();cache[cacheKey]=System.currentTimeMillis() to image;image
    }
}
