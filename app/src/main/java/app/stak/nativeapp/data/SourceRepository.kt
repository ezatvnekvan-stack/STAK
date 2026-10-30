package app.stak.nativeapp.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class SourceFilters(val resolution:String="All",val hdr:Boolean=false,val dv:Boolean=false,val atmos:Boolean=false,val remux:Boolean=false,val webdl:Boolean=false,val maxGb:Int?=null,val preferHun:Boolean=false,val sort:String="best",val cachedOnly:Boolean=true)
fun parseSourceRelease(stream:JsonObject,allowTorrentLink:Boolean=false):SourceRelease? {
    val hash=stream.text("infoHash")?.takeIf { it.matches(Regex("[a-fA-F0-9]{40}")) }?.lowercase()
        ?: stream.text("url")?.takeIf{it.startsWith("magnet:?",true)}?.let{runCatching{parseMagnetInput(it).identity.takeIf{hash->hash.matches(Regex("[a-f0-9]{40}"))}}.getOrNull()}
    val url=stream.text("url")?.takeIf { it.startsWith("https://")||(allowTorrentLink&&it.startsWith("http://")) }
    val magnet=stream.text("url")?.takeIf{it.startsWith("magnet:?",true)&&runCatching{parseMagnetInput(it)}.isSuccess}
    if(hash==null && url==null&&magnet==null)return null
    val raw=stream.text("description") ?: stream.text("title") ?: stream.objectOrNull("behaviorHints")?.text("filename") ?: stream.text("name") ?: "Release"
    val combined=raw+" "+stream.text("name").orEmpty()+" "+stream.objectOrNull("behaviorHints")?.text("filename").orEmpty()
    val hints=stream.objectOrNull("behaviorHints")
    val size=hints?.number("videoSize")?.toLong() ?: Regex("(?i)([0-9]+(?:[.,][0-9]+)?)\\s*(TB|TiB|GB|GiB|MB|MiB)").find(combined)?.let { match -> (match.groupValues[1].replace(',','.').toDouble() * when(match.groupValues[2].uppercase()){ "TB","TIB"->1099511627776.0;"GB","GIB"->1073741824.0;else->1048576.0 }).toLong() }
    fun tag(pattern:String)=Regex(pattern,RegexOption.IGNORE_CASE).containsMatchIn(combined)
    val languages=listOf("HUN" to "(?:\\bHUN\\b|Hungarian|Magyar|🇭🇺)","ENG" to "(?:\\bENG\\b|English|🇬🇧|🇺🇸)").filter{tag(it.second)}.map{it.first}
    return SourceRelease(magnetUrl=magnet,releaseText=raw+" "+hints?.text("filename").orEmpty()+" "+stream.text("name").orEmpty(),name=raw.lineSequence().firstOrNull().orEmpty(),resolution=Regex("(?i)(2160|1080|720|480)p").find(combined)?.value?.lowercase() ?: if(tag("\\b4K\\b"))"2160p" else "Unknown",indexer=Regex("(?:⚙️?|Tracker:|Indexer:)\\s*([^\\n|]+)").find(raw)?.groupValues?.get(1)?.trim()?.takeIf{it.isNotEmpty()} ?: "Torrentio",infoHash=hash,streamUrl=url,fileIndex=stream.number("fileIdx")?.toInt(),sizeBytes=size,seeders=Regex("(?:👤|👥|[Ss]eeders?:?)\\s*(\\d+)").find(combined)?.groupValues?.get(1)?.toIntOrNull() ?: 0,hdr=tag("\\bHDR(?:10\\+?)?\\b"),dolbyVision=tag("\\b(?:DV|DOVI|Dolby[ .-]?Vision)\\b"),atmos=tag("\\bAtmos\\b"),remux=tag("\\bREMUX\\b"),webdl=tag("\\bWEB[ .-]?DL\\b"),languages=languages)
}
fun seasonPackMatches(name:String,season:Int):Boolean {
    if(Regex("(?i)\\bS\\d{1,2}E\\d{1,3}\\b|\\b\\d{1,2}x\\d{2}\\b").containsMatchIn(name))return false
    return Regex("(?i)\\bS0*$season\\b|\\bSeason[ ._-]*0*$season\\b").containsMatchIn(name.replace('.', ' ').replace('_',' '))
}
fun filteredSources(items:List<SourceRelease>,cached:Set<String>,filters:SourceFilters,season:Int?=null,packs:Boolean=false,showAll:Boolean=false):List<SourceRelease> {
    val selected=items.filter { s -> (!filters.cachedOnly || s.infoHash in cached) && (filters.resolution=="All" || s.resolution==filters.resolution) && (!filters.hdr||s.hdr) && (!filters.dv||s.dolbyVision) && (!filters.atmos||s.atmos) && (!filters.remux||s.remux) && (!filters.webdl||s.webdl) && (filters.maxGb==null||(if(season!=null&&seasonPackMatches(s.name,season))s.torrentSizeBytes else s.sizeBytes)?.let{it<=filters.maxGb*1073741824L}==true) && (!packs||showAll||season==null||seasonPackMatches(s.name,season)) }
    return when(filters.sort) {
        "seeders"->selected.sortedByDescending{it.seeders ?: -1}
        "size"->selected.sortedByDescending{if(season!=null&&seasonPackMatches(it.name,season))it.torrentSizeBytes ?: -1 else it.sizeBytes ?: -1}
        else->bestQualityOrder(selected,cached,filters.preferHun)
    }
}
object SourceRepository {
    suspend fun cached(hashes:List<String>,key:String):Set<String> = withContext(Dispatchers.IO) {
        require(key.isNotBlank()){ "Set your TorBox API key in Settings to check cache." }
        val result=mutableSetOf<String>()
        hashes.filter{it.matches(Regex("[a-fA-F0-9]{40}"))}.map{it.lowercase()}.distinct().chunked(80).forEach { group ->
            val response=providerJson("https://api.torbox.app/v1/api/torrents/checkcached",mapOf("hash" to group.joinToString(","),"format" to "list","list_files" to "false"),key)
            if(response.get("success")?.asBoolean!=true)throw IOException("TorBox cache verification failed.")
            result+=confirmedHashes(response.get("data"),group.toSet())
        };result
    }
    suspend fun torrentSizes(hashes:List<String>,key:String):Map<String,Long> = withContext(Dispatchers.IO) {
        require(key.isNotBlank())
        val sizes=mutableMapOf<String,Long>()
        hashes.filter{it.matches(Regex("[a-fA-F0-9]{40}"))}.map{it.lowercase()}.distinct().chunked(80).forEach{group->
            val response=providerJson("https://api.torbox.app/v1/api/torrents/checkcached",mapOf("hash" to group.joinToString(","),"format" to "object","list_files" to "true"),key)
            require(response.get("success")?.asBoolean==true)
            sizes+=confirmedTorrentSizes(response.get("data"),group.toSet())
        }
        sizes
    }
    internal fun confirmedTorrentSizes(data:com.google.gson.JsonElement?,requested:Set<String>):Map<String,Long> {
        if(data==null||!data.isJsonObject)return emptyMap()
        return data.asJsonObject.entrySet().mapNotNull{(hash,value)->
            if(hash.lowercase() !in requested||!value.isJsonObject)return@mapNotNull null
            val item=value.asJsonObject
            if(item.get("cached")?.takeIf{it.isJsonPrimitive}?.asBoolean==false)return@mapNotNull null
            val explicit=(item.number("size") ?: item.number("total_size"))?.takeIf{it.isFinite()&&it>0&&it<Long.MAX_VALUE.toDouble()}?.toLong()
            val files=item.get("files")?.takeIf{it.isJsonArray}?.asJsonArray
            val sum=if(files!=null&&files.size()>0)runCatching{var total=0L;for(file in files){val size=file.takeIf{it.isJsonObject}?.asJsonObject?.number("size")?.takeIf{it.isFinite()&&it>=0&&it<Long.MAX_VALUE.toDouble()}?.toLong() ?: error("Incomplete sizes");total=Math.addExact(total,size)};total.takeIf{it>0}}.getOrNull() else null
            (explicit ?: sum)?.let{hash.lowercase() to it}
        }.toMap()
    }
    internal fun confirmedHashes(data:com.google.gson.JsonElement?,requested:Set<String>):Set<String> {
        if(data==null || (!data.isJsonArray&&!data.isJsonObject))throw IOException("TorBox returned invalid cache data.")
        return if(data.isJsonArray)data.asJsonArray.mapNotNull{item->if(item.isJsonPrimitive)item.asString else if(item.isJsonObject && item.asJsonObject.get("cached")?.takeIf{it.isJsonPrimitive}?.asBoolean!=false)item.asJsonObject.text("hash") else null}.map{it.lowercase()}.filter{it in requested}.toSet()
        else data.asJsonObject.entrySet().filter{it.key.lowercase() in requested && !it.value.isJsonNull && ((it.value.isJsonObject&&it.value.asJsonObject.get("cached")?.takeIf{value->value.isJsonPrimitive}?.asBoolean!=false) || (it.value.isJsonArray&&it.value.asJsonArray.size()>0) || (it.value.isJsonPrimitive&&it.value.asString=="true"))}.map{it.key.lowercase()}.toSet()
    }
    suspend fun add(source:SourceRelease,key:String):String=addCachedSource(source,key)
}

/** Recheck at confirmation and enforce the provider-side cutoff; never fall back to uncached. */
suspend fun addCachedSource(source:SourceRelease,key:String,
    prepare:suspend(SourceRelease,String)->PreparedSource=SourceMaterials::prepare,
    check:suspend(List<String>,String)->Set<String> = SourceRepository::cached,
    torrentAdd:suspend(String,TorrentUpload)->String={k,f->DirectTorBoxMutations().uploadCached(k,f)},
    magnetAdd:suspend(String,MagnetInput)->String={k,m->DirectTorBoxMutations().addMagnetCached(k,m)},
    currentKey:()->String={NativeRuntime.settings.torboxKey.orEmpty()}
):String {
    if(key.isBlank()||key!=currentKey())throw NabFailure("TorBox account changed. Refresh sources.")
    val expected=source.infoHash?.lowercase()?.takeIf{it.matches(Regex("[a-f0-9]{40}"))}?:throw NabFailure("This release has not passed cache verification.")
    val ready=prepare(source,key)
    if(ready.source.infoHash!=expected)throw NabFailure("Torrent metadata changed. Refresh sources.")
    if(expected !in check(listOf(expected),key))throw NabFailure("This release is no longer confirmed cached on TorBox. Refresh sources.")
    kotlinx.coroutines.currentCoroutineContext().ensureActive()
    if(key!=currentKey())throw NabFailure("TorBox account changed. Refresh sources.")
    return when(val material=ready.material){
        is NabMaterial.Torrent->torrentAdd(key,material.upload)
        is NabMaterial.Magnet->magnetAdd(key,material.input)
    }
}
