package app.stak.nativeapp.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object DirectServices {
    private fun tmdb(path: String, params: Map<String, String> = emptyMap()): JsonObject {
        val key = NativeRuntime.settings.tmdbKey.orEmpty()
        require(key.isNotBlank()) { "Configure your TMDb API key or read access token in Metadata sources." }
        return providerJson("https://api.themoviedb.org/3$path", if (key.contains('.')) params else params + ("api_key" to key), if (key.contains('.')) key else null)
    }
    suspend fun directors(query: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val response = tmdb(if (query.isBlank()) "/person/popular" else "/search/person", if (query.isBlank()) emptyMap() else mapOf("query" to query, "include_adult" to "false"))
        response.getAsJsonArray("results")?.map { it.asJsonObject }?.filter { it.text("known_for_department") == "Directing" }?.mapNotNull { person -> person.text("id")?.let { it to person.text("name").orEmpty() } }.orEmpty()
    }
    suspend fun filmography(id: String): List<MediaItem> = withContext(Dispatchers.IO) {
        require(id.matches(Regex("\\d+"))) { "Invalid person identifier." }
        val response = tmdb("/person/$id/combined_credits")
        (response.objects("cast") + response.objects("crew").filter { it.text("job") in listOf("Director", "Writer", "Screenplay", "Creator") }).filter { it.text("media_type") in listOf("tv", "movie") }.map { DirectCatalog.tmdbItem(it, if (it.text("media_type") == "tv") "series" else "movie") }.distinctBy { "${it.type}:${it.mediaKey}" }
    }

    private fun requireKey(key: String) {
        require(key.isNotBlank() && key.length <= 4096 && !key.contains('\r') && !key.contains('\n')) { "Configure your TorBox API key in Settings." }
    }

    private fun torbox(path: String, key: String, params: Map<String, String> = emptyMap()): JsonElement {
        requireKey(key)
        val response = providerJson("https://api.torbox.app/v1/api$path", params, key)
        if (response.get("success")?.asBoolean != true || !response.has("data")) throw IOException("TorBox returned an invalid response. Please retry.")
        return response.get("data")
    }

    suspend fun account(key: String): String = withContext(Dispatchers.IO) {
        val data = torbox("/user/me", key, mapOf("settings" to "false")).asJsonObject
        data.text("name") ?: "TorBox"
    }

    suspend fun torrents(key: String,onPage:suspend(List<TorBoxTorrent>,Int?)->Unit={_,_->}):List<TorBoxTorrent> = withContext(Dispatchers.IO) {
        loadLibraryPages(identity={it.id},fetch={offset,limit->
            val data=torbox("/torrents/mylist",key,mapOf("bypass_cache" to "true","offset" to offset.toString(),"limit" to limit.toString()))
            val list=if(data.isJsonArray)data.asJsonArray else if(data.isJsonObject)data.asJsonObject.get("torrents")?.takeIf{it.isJsonArray}?.asJsonArray else null
            if(list==null)throw IOException("TorBox returned an invalid library.")
            LibraryPage(list.map{parseTorBoxTorrent(it.asJsonObject)})
        },onPage=onPage)
    }

    suspend fun download(key: String, torrentId: String, fileId: String): String = withContext(Dispatchers.IO) {
        val data = torbox("/torrents/requestdl", key, mapOf("token" to key, "torrent_id" to torrentId, "file_id" to fileId, "zip_link" to "false"))
        val url = if (data.isJsonPrimitive) data.asString else data.asJsonObject.text("url")
        if (url == null || !url.startsWith("https://")) throw IOException("TorBox did not return a valid download link.")
        url
    }

    suspend fun sources(type: String, imdbId: String, season: Int? = null, episode: Int? = null): List<SourceRelease> = withContext(Dispatchers.IO) {
        AddonSourceClient(addons={StreamAddonStore(NativeRuntime.settings).all()}).search(type,imdbId,season,episode).releases
    }
}

data class TorBoxFile(val id: String, val name: String, val size: Long)
data class TorBoxTorrent(val id: String, val name: String, val size: Long, val progress: Float, val finished: Boolean, val state: String, val files: List<TorBoxFile>,val hash:String?=null,val createdAt:Long=0)


internal fun parseTorBoxTorrent(item:JsonObject):TorBoxTorrent {
    val id=item.text("id") ?: throw IOException("TorBox download has no identifier.")
    return TorBoxTorrent(id,item.text("name") ?: "Download $id",(item.number("size") ?: item.number("total_size") ?: 0.0).toLong().coerceAtLeast(0),(item.number("progress") ?: 0.0).toFloat().let{if(it.isFinite())it.coerceIn(0f,1f) else 0f},item.get("download_finished")?.takeIf{it.isJsonPrimitive}?.asBoolean==true,item.text("download_state") ?: "Unknown",item.get("files")?.takeIf{it.isJsonArray}?.asJsonArray?.map{value->val file=value.asJsonObject;TorBoxFile(file.text("id").orEmpty(),file.text("name") ?: file.text("short_name") ?: "File",file.number("size")?.toLong()?.coerceAtLeast(0) ?: 0L)}.orEmpty(),item.text("hash"),libraryTimestamp(item.text("created_at")))
}
