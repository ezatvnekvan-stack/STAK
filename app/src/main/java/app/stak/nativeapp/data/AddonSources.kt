package app.stak.nativeapp.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class StreamAddon(val id:String=java.util.UUID.randomUUID().toString(),val name:String="Addon",val manifestUrl:String="",val enabled:Boolean=true)
data class AddonSearchResult(val releases:List<SourceRelease>,val warnings:List<String> = emptyList())
fun normalizeAddonManifest(raw:String):String {
    val url=raw.trim().replace(Regex("^stremio://",RegexOption.IGNORE_CASE),"https://").toHttpUrl()
    require(url.isHttps&&url.username.isEmpty()&&url.password.isEmpty()&&url.fragment==null&&url.query==null&&url.encodedPath.endsWith("/manifest.json")&&url.toString().length<16384){"Use the addon's full HTTPS manifest.json URL, without a query or fragment."}
    return url.toString()
}
class StreamAddonStore(private val settings:SettingsStore) {
    fun all():List<StreamAddon> {
        val raw=settings.streamAddonsJson ?: return listOf(StreamAddon("torrentio","Torrentio","https://torrentio.strem.fun/manifest.json"))
        return runCatching{Gson().fromJson<List<StreamAddon>>(raw,object:TypeToken<List<StreamAddon>>(){}.type).orEmpty().filter{it.id.isNotBlank()&&it.name.isNotBlank()&&runCatching{normalizeAddonManifest(it.manifestUrl)}.isSuccess}.distinctBy{it.id}.take(12)}.getOrDefault(emptyList())
    }
    fun save(items:List<StreamAddon>){require(items.size<=12);val normalized=items.map{require(it.name.isNotBlank()&&it.name.length<=60);it.copy(manifestUrl=normalizeAddonManifest(it.manifestUrl))};settings.streamAddonsJson=Gson().toJson(normalized)}
}
class AddonSourceClient(
    private val addons:()->List<StreamAddon>,
    private val request:suspend(String)->JsonObject={url->withContext(Dispatchers.IO){
        client.newCall(Request.Builder().url(url).build()).execute().use{response->
            if(!response.isSuccessful)throw IOException("Addon request failed.")
            val body=response.body ?: throw IOException("Empty addon response.")
            require(body.contentLength()<=8*1024*1024){"Addon response is too large."}
            val input=body.source();input.request(8*1024*1024+1L);require(input.buffer.size<=8*1024*1024){"Addon response is too large."};val text=input.buffer.readUtf8()
            JsonParser.parseString(text).asJsonObject
        }
    }}
) {
    companion object{private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(30,TimeUnit.SECONDS).build()}
    suspend fun search(type:String,imdb:String,season:Int?=null,episode:Int?=null):AddonSearchResult=supervisorScope {
        require(type in listOf("movie","series")&&imdb.matches(Regex("tt\\d+"))){"Resolve this title's IMDb identity first."}
        val identity=if(type=="series"){require(season!=null&&episode!=null);"$imdb:$season:$episode"}else imdb
        val enabled=addons().filter{it.enabled}.take(12)
        require(enabled.isNotEmpty()){"Enable a source in Settings → Search sources."}
        val results=enabled.chunked(4).flatMap{group->group.map{addon->async(Dispatchers.Default){
            try {
                val manifest=normalizeAddonManifest(addon.manifestUrl)
                val base=manifest.removeSuffix("/manifest.json")
                val data=request("$base/stream/$type/$identity.json")
                val streams=data.get("streams")?.takeIf{it.isJsonArray}?.asJsonArray ?: throw IOException("Invalid stream response")
                val parsed=streams.take(1500).mapNotNull{value->if(!value.isJsonObject)null else runCatching{parseSourceRelease(value.asJsonObject)?.let{release->release.copy(addonName=addon.name,indexer=if(release.indexer=="Torrentio")addon.name else release.indexer)}}.getOrNull()}
                parsed to if(parsed.isNotEmpty()&&parsed.none{it.canImport})"${addon.name}: playback-only links returned. Enable torrent results in this addon to add them to TorBox." else null
            }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){emptyList<SourceRelease>() to "${addon.name}: search failed; check the addon configuration."}
        }}.awaitAll()}
        if(results.all{it.second!=null&&it.first.isEmpty()})throw IOException("All enabled addons failed. Check their configuration and connection.")
        val candidates=results.flatMap{it.first}.distinctBy{it.infoHash?.let{hash->"$hash:${it.fileIndex}"} ?: "${it.addonName}:${it.name}:${it.streamUrl}"}
        AddonSearchResult(candidates.take(5000),results.mapNotNull{it.second})
    }
}
