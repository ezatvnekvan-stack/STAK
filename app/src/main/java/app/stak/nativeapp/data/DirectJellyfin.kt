package app.stak.nativeapp.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class JellyfinHttpFailure(val status:Int):IOException(if(status in listOf(401,403))"Jellyfin access denied. Reconnect or check user permissions." else "Jellyfin request failed ($status).")

object DirectJellyfin {
    private val auth: String get() = "MediaBrowser Client=\"STAK\", Device=\"Android\", DeviceId=\"${NativeRuntime.settings.jellyfinDeviceId}\", Version=\"1.3.0\""
    fun authorizationHeaders(token: String): Map<String, String> {
        require(token.isNotBlank() && token.length <= 4096 && token.none { it == '\r' || it == '\n' }) { "Reconnect Jellyfin: the saved credential is invalid." }
        val quoted = token.replace("\\", "\\\\").replace("\"", "\\\"")
        val authorization = "$auth, Token=\"$quoted\""
        return mapOf("Authorization" to authorization, "X-Emby-Authorization" to authorization, "X-Emby-Token" to token)
    }
    internal val client = OkHttpClient.Builder().followRedirects(false).connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()

    fun normalizeServer(raw: String): String {
        val url = raw.trim().trimEnd('/').toHttpUrl()
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "Enter a server URL without credentials or query parameters." }
        return url.toString().trimEnd('/')
    }

    private fun requestValue(server: String, path: String, token: String? = null, body: JsonObject? = null, params: Map<String, String> = emptyMap(), method: String = "GET"): com.google.gson.JsonElement = try {
        require(jellyfinTrackingMutationAllowed(path,if(body!=null)"POST"else method)){"Playback state is read-only in STAK."}
        performRequestValue(server,path,token,body,params,method)
    } catch(error:JellyfinHttpFailure) {
        if(error.status!=404)throw error
        val route=legacyJellyfinRoute(path) ?: throw error
        performRequestValue(server,route.first,token,body,params+mapOf("UserId" to route.second),method)
    }
    private fun performRequestValue(server: String, path: String, token: String? = null, body: JsonObject? = null, params: Map<String, String> = emptyMap(), method: String = "GET"): com.google.gson.JsonElement {
        require(jellyfinTrackingMutationAllowed(path,if(body!=null)"POST"else method)){"Playback state is read-only in STAK."}
        val url = (normalizeServer(server) + path).toHttpUrl().newBuilder()
        jellyfinQuery(params).forEach { (key, value) -> url.addQueryParameter(key, value) }
        val builder = Request.Builder().url(url.build()).header("Authorization", auth)
        if (token != null) authorizationHeaders(token).forEach { (name, value) -> builder.header(name, value) }
        if (body != null) builder.post(body.toString().toRequestBody("application/json".toMediaType()))
        else if (method != "GET") builder.method(method, if (method == "POST") "".toRequestBody(null) else null)
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw JellyfinHttpFailure(response.code)
            val source=response.body?.source()
            source?.request(8*1024*1024+1L)
            if(source!=null&&source.buffer.size>8*1024*1024)throw IOException("Jellyfin response exceeds the supported page size.")
            val content = source?.buffer?.readUtf8().orEmpty()
            if (content.isBlank()) return JsonObject()
            return try { JsonParser.parseString(content) } catch (_: RuntimeException) { throw IOException("Jellyfin returned an invalid response.") }
        }
    }
    private fun request(server: String, path: String, token: String? = null, body: JsonObject? = null, params: Map<String,String> = emptyMap()): JsonObject =
        requestValue(server, path, token, body, params).let { if (!it.isJsonObject) throw IOException("Jellyfin returned an invalid response."); it.asJsonObject }
    internal suspend fun sessionValue(path:String,params:Map<String,String> = emptyMap(),body:JsonObject?=null,method:String="GET",expectedScope:String?=null):com.google.gson.JsonElement {
        val snapshot=withContext(Dispatchers.Main.immediate){val settings=NativeRuntime.settings;require(settings.jellyfinConnected){"Connect Jellyfin in Settings first."};if(expectedScope!=null)require(expectedScope==jellyfinScope(settings)){"Jellyfin account changed. Retry sync."};settings.jellyfinUrl.orEmpty() to settings.jellyfinToken.orEmpty()}
        val result=withContext(Dispatchers.IO){requestValue(snapshot.first,path,snapshot.second,body,params,method)}
        if(expectedScope!=null)require(expectedScope==jellyfinScope()){ "Jellyfin account changed. Discarded the old response." }
        return result
    }
    internal suspend fun sessionRequest(path:String,params:Map<String,String> = emptyMap(),body:JsonObject?=null,method:String="GET",expectedScope:String?=null):JsonObject {
        val value=sessionValue(path,params,body,method,expectedScope)
        if(!value.isJsonObject)throw IOException("Jellyfin returned an invalid response.")
        return value.asJsonObject
    }
    internal suspend fun compatible(oldPath:String,newPath:String,params:Map<String,String> = emptyMap(),method:String="GET",expectedScope:String=jellyfinScope()):com.google.gson.JsonElement = try{sessionValue(oldPath,params,method=method,expectedScope=expectedScope)}catch(error:JellyfinHttpFailure){if(error.status!=404)throw error;sessionValue(newPath,params,method=method,expectedScope=expectedScope)}

    suspend fun probeLogin(server: String, username: String, password: String): JellyfinProbe = withContext(Dispatchers.IO) {
        require(username.isNotBlank()) { "Enter your Jellyfin username." }
        val data = request(server, "/Users/AuthenticateByName", body = JsonObject().apply { addProperty("Username", username); addProperty("Pw", password) })
        val token = data.text("AccessToken")?.takeIf { it.isNotBlank() } ?: throw IOException("Jellyfin returned invalid login data.")
        val user = data.getAsJsonObject("User") ?: throw IOException("Jellyfin returned invalid login data.")
        val id = user.text("Id")?.takeIf { it.isNotBlank() } ?: throw IOException("Jellyfin returned invalid login data.")
        JellyfinProbe(token, listOf(JellyfinUser(id, user.text("Name") ?: "Jellyfin user")))
    }
    suspend fun probeKey(server: String, key: String): JellyfinProbe = withContext(Dispatchers.IO) {
        require(key.isNotBlank()) { "Enter your Jellyfin API key." }
        val info = request(server, "/System/Info/Public", key)
        val users = requestValue(server, "/Users", key)
        if (!users.isJsonArray) throw IOException("Jellyfin returned an invalid user list.")
        JellyfinProbe(key, users.asJsonArray.map { value -> val u = value.asJsonObject; JellyfinUser(u.text("Id")?.takeIf { it.isNotBlank() } ?: throw IOException("Jellyfin user has no identifier."), u.text("Name") ?: "Jellyfin user") }, info.text("ServerName") ?: "Jellyfin", info.text("Version").orEmpty())
    }
    suspend fun verifyUser(server: String, token: String, id: String): Unit = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[A-Za-z0-9-]+"))) { "Invalid Jellyfin user." }
        if (request(server, "/Users/$id", token).text("Id") != id) throw IOException("Jellyfin returned a different user. Reconnect and choose your user again.")
    }

    suspend fun signIn(server: String, username: String, password: String): Pair<String, String> = withContext(Dispatchers.IO) {
        require(username.isNotBlank()) { "Enter your Jellyfin username." }
        val body = JsonObject().apply { addProperty("Username", username); addProperty("Pw", password) }
        val response = request(server, "/Users/AuthenticateByName", body = body)
        val token = response.text("AccessToken") ?: throw IOException("Jellyfin did not return an access token.")
        val user = response.getAsJsonObject("User")?.text("Id") ?: throw IOException("Jellyfin did not return a user.")
        token to user
    }

    suspend fun library(settings: SettingsStore,parentId:String?=null,onPage:suspend(List<MediaItem>,Int?)->Unit={_,_->}):List<MediaItem> {
        val snapshot=withContext(Dispatchers.Main.immediate) {
            if(!settings.jellyfinConnected)null else Triple(settings.jellyfinToken.orEmpty(),settings.jellyfinUserId.orEmpty(),normalizeServer(settings.jellyfinUrl.orEmpty()))
        } ?: return emptyList()
        if(parentId!=null)require(parentId.matches(Regex("[A-Za-z0-9-]+")))
        require(snapshot.second.matches(Regex("[A-Za-z0-9-]+")))
        return withContext(Dispatchers.IO) {
            loadLibraryPages(identity={it.jellyfinId ?: it.id},fetch={offset,limit->
                val data=request(snapshot.third,"/Users/${snapshot.second}/Items",snapshot.first,params=mapOf("Recursive" to "true","IncludeItemTypes" to "Movie,Series","Fields" to "Overview,ProviderIds,UserData,RunTimeTicks,DateCreated,Genres,OriginalTitle,PresentationUniqueKey,MediaSources,MediaStreams","EnableUserData" to "true","SortBy" to "SortName,ProductionYear","SortOrder" to "Ascending","StartIndex" to offset.toString(),"Limit" to limit.toString())+(if(parentId!=null)mapOf("ParentId" to parentId) else emptyMap()))
                if(data.get("Items")?.isJsonArray!=true)throw IOException("Jellyfin returned an invalid library.")
                LibraryPage(data.objects("Items").map{jellyfinMediaItem(it,snapshot.third)},data.number("TotalRecordCount")?.toInt()?.takeIf{it>=0})
            },onPage=onPage)
        }
    }
}
