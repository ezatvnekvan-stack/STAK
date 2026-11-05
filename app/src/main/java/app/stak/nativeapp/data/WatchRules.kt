package app.stak.nativeapp.data

import com.google.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest

// --- Watcher connection + rule model (mirrors stak-watch/core/rules.py) -------

/** Connection to a configured stak-watch watcher instance (server on Linux). */
data class WatcherConnection(val url:String="",val token:String="",val enabled:Boolean=false){
    override fun toString()="WatcherConnection(credentials hidden)"
    companion object{val NONE=WatcherConnection()}
}

/** Allowed values; each maps 1:1 onto the watcher's WatchRule validation. */
object WatchModes{
    const val RESOLUTION_2160_ONLY="2160_only"
    const val RESOLUTION_1080_ONLY="1080_only"
    const val RESOLUTION_2160_PREFERRED="2160_preferred"
    val RESOLUTION_MODES=setOf(RESOLUTION_2160_ONLY,RESOLUTION_1080_ONLY,RESOLUTION_2160_PREFERRED)
    const val MODE_MANDATORY="mandatory";const val MODE_PREFERRED="preferred";const val MODE_ANY="any"
    val TRAIT_MODES=setOf(MODE_MANDATORY,MODE_PREFERRED,MODE_ANY)
    const val RELEASE_WEBDL="WEB-DL";const val RELEASE_WEBRIP="WEBRip";const val RELEASE_BLURAY="BluRay";const val RELEASE_REMUX="Remux"
    val RELEASE_TYPES=setOf(RELEASE_WEBDL,RELEASE_WEBRIP,RELEASE_BLURAY,RELEASE_REMUX)
    const val LANG_ANY="any";const val LANG_HUN_MANDATORY="hun_mandatory";const val LANG_HUN_PREFERRED="hun_preferred";const val LANG_ORIGINAL="original"
    val LANGUAGE_MODES=setOf(LANG_ANY,LANG_HUN_MANDATORY,LANG_HUN_PREFERRED,LANG_ORIGINAL)
    const val MIN_CHECK_MINUTES=15;const val MAX_CHECK_MINUTES=720;const val DEFAULT_CHECK_MINUTES=45
    const val MAX_WAIT_HOURS=14*24
}

/**
 * One per-series auto-add rule. The series side only: film items never reach
 * this code path. State distinguishes locally stored ("saved") from confirmed
 * by the watcher ("confirmed") so the UI never claims server-side truth.
 */
data class WatchRuleModel(
    val ruleId:String,
    val seriesImdb:String?=null,
    val seriesTmdb:String?=null,
    val seriesTvdb:String?=null,
    val seriesJellyfin:String?=null,
    val seriesName:String="",
    val enabled:Boolean=false,
    val paused:Boolean=false,
    val createdOn:String=java.time.LocalDate.now().toString(),
    val watchedSeasons:List<Int> = emptyList(),
    val includeSpecials:Boolean=false,
    val onlyNewEpisodes:Boolean=false,
    val resolutionMode:String=WatchModes.RESOLUTION_2160_PREFERRED,
    val resolutionWaitHours:Int=24,
    val releaseTypes:List<String> = emptyList(),
    val hdrMode:String=WatchModes.MODE_ANY,
    val dvMode:String=WatchModes.MODE_ANY,
    val atmosMode:String=WatchModes.MODE_ANY,
    val languageMode:String=WatchModes.LANG_ANY,
    val maxEpisodeGb:Double?=null,
    val checkMinutes:Int=WatchModes.DEFAULT_CHECK_MINUTES,
    val state:String=STATE_SAVED_LOCAL,
    val at:Long=System.currentTimeMillis(),
    val pendingAction:String?=null,
    val poster:String?=null,
){
    companion object{
        /** Stored on the device; the watcher has not confirmed the save. */
        const val STATE_SAVED_LOCAL="saved_local"
        /** The watcher answered 2xx and the rule rows are being tracked. */
        const val STATE_SERVER_CONFIRMED="server_confirmed"
        val VALID_STATES=setOf(STATE_SAVED_LOCAL,STATE_SERVER_CONFIRMED)
    }
}

/** Lightweight, app-visible watcher state for one rule (from /v1/episodes). */
data class WatchRuleStatus(val ruleId:String,val seriesName:String,val paused:Boolean,val confirmed:Boolean,
                           val episodes:Int,val statuses:Map<String,Int>,val fetchedAt:Long)

/**
 * Persisted document (rules + watcher connection). Kept in SECRETS storage
 * exactly like the approvals: it joins `CloudSnapshot` via `backupSecrets()`,
 * so rules and pairing travel in the encrypted cloud backup only.
 */
data class WatcherDocument(val version:Int=1,val connection:WatcherConnection=WatcherConnection.NONE,
                           val rules:List<WatchRuleModel> = emptyList(),val statuses:List<WatchRuleStatus> = emptyList(),
                           val qualityDefault:WatchQualityProfile?=null)

/** Pure codec with strict guards: corrupted data is refused, never overwritten. */
object WatcherCodec{
    private const val MAX_RULES=200
    private val RULE_ID=Regex("^watch_(imdb|tmdb|tvdb|jf)_[A-Za-z0-9_-]{2,140}$")
    private val DATE=Regex("\\d{4}-\\d{2}-\\d{2}")

    private fun JsonObject.str(key:String):String?=get(key)?.takeIf{it.isJsonPrimitive}?.asString

    fun decode(raw:String?):WatcherDocument{
        if(raw.isNullOrBlank())return WatcherDocument()
        try{
            val root=JsonParser.parseString(raw)
            require(root.isJsonObject)
            val obj=root.asJsonObject
            require(obj.get("version")?.asInt==1)
            require(obj.get("rules")?.isJsonArray==true&&obj.get("connection")?.isJsonObject==true)
            val gson=Gson()
            val connection=gson.fromJson(obj.getAsJsonObject("connection"),WatcherConnection::class.java)
            require(connection.token.length<=4096&&connection.url.length<=500)
            require(obj.getAsJsonArray("rules").size()<=MAX_RULES)
            val rules=obj.getAsJsonArray("rules").map{it.asJsonObject}.map{rule->
                val decoded=gson.fromJson(rule,WatchRuleModel::class.java)
                require(decoded.ruleId.matches(RULE_ID))
                require(decoded.state in WatchRuleModel.VALID_STATES)
                require(decoded.seriesImdb==null||decoded.seriesImdb.matches(Regex("tt\\d{5,12}")))
                require(decoded.seriesTmdb==null||decoded.seriesTmdb.matches(Regex("\\d{1,12}")))
                atLeastOneId(decoded)
                require(decoded.resolutionMode in WatchModes.RESOLUTION_MODES)
                require(decoded.hdrMode in WatchModes.TRAIT_MODES&&decoded.dvMode in WatchModes.TRAIT_MODES&&decoded.atmosMode in WatchModes.TRAIT_MODES)
                require(decoded.languageMode in WatchModes.LANGUAGE_MODES)
                require(decoded.releaseTypes.all{it in WatchModes.RELEASE_TYPES})
                require(decoded.watchedSeasons.all{it in 0..200})
                require(decoded.checkMinutes in WatchModes.MIN_CHECK_MINUTES..WatchModes.MAX_CHECK_MINUTES)
                require(decoded.resolutionWaitHours in 0..WatchModes.MAX_WAIT_HOURS)
                require((decoded.maxEpisodeGb?:0.0) in 0.0..500.0)
                require(decoded.createdOn==null||decoded.createdOn.matches(DATE))
                decoded
            }
            require(rules.distinctBy{it.ruleId}.size==rules.size)
            val statuses=obj.getAsJsonArray("statuses")?.map{it.asJsonObject}.orEmpty().map{row->
                val counters=row.getAsJsonObject("statuses")
                require(counters!=null)
                WatchRuleStatus(
                    ruleId=row.str("ruleId")?:error("ruleId"),
                    seriesName=row.str("seriesName")?:"",
                    paused=row.get("paused")?.takeIf{it.isJsonPrimitive}?.asBoolean?:false,
                    confirmed=row.get("confirmed")?.takeIf{it.isJsonPrimitive}?.asBoolean?:false,
                    episodes=row.get("episodes")?.takeIf{it.isJsonPrimitive}?.asInt?:0,
                    statuses=counters.entrySet().associate{(k,v)->k to (v.takeIf{it.isJsonPrimitive}?.asInt?:0)},
                    fetchedAt=row.get("fetchedAt")?.takeIf{it.isJsonPrimitive}?.asLong?:0L,
                )
            }
            return WatcherDocument(1,connection,rules,statuses, obj.getAsJsonObject("qualityDefault")?.let{gson.fromJson(it,WatchQualityProfile::class.java)})
        }catch(e:Exception){throw IOException("Saved watcher rules could not be read. Existing rules were not changed.",e)}
    }

    private fun atLeastOneId(rule:WatchRuleModel){
        require(!rule.seriesImdb.isNullOrBlank()||!rule.seriesTmdb.isNullOrBlank()||
            !rule.seriesTvdb.isNullOrBlank()||!rule.seriesJellyfin.isNullOrBlank())
    }

    fun encode(document:WatcherDocument):String{
        require(document.rules.size<=MAX_RULES)
        require(document.rules.distinctBy{it.ruleId}.size==document.rules.size)
        val gson=Gson()
        val out=JsonObject().apply{
            addProperty("version",1)
            add("connection",gson.toJsonTree(document.connection))
            add("rules",JsonArray().apply{document.rules.forEach{add(gson.toJsonTree(it))}})
            add("statuses",JsonArray().apply{document.statuses.forEach{add(gson.toJsonTree(it))}})
            document.qualityDefault?.let{add("qualityDefault",gson.toJsonTree(it))}
        }
        return gson.toJson(out)
    }

    /** Exact payload the watcher's POST /v1/rules validates: ids + explicit
     *  auto-add consent. `confirm`/`confirmed` keep watchlisting alone from
     *  ever enabling automation. */
    fun payloadFor(rule:WatchRuleModel,consent:Boolean=false):JsonObject{
        require(consent){"Watchlisting alone must not enable automation "}
        return JsonObject().apply{
            addProperty("ruleId",rule.ruleId)
            add("series",JsonObject().apply{
                if(!rule.seriesImdb.isNullOrBlank())addProperty("imdb",rule.seriesImdb)
                if(!rule.seriesTmdb.isNullOrBlank())addProperty("tmdb",rule.seriesTmdb)
                if(!rule.seriesTvdb.isNullOrBlank())addProperty("tvdb",rule.seriesTvdb)
                if(!rule.seriesJellyfin.isNullOrBlank())addProperty("jellyfin",rule.seriesJellyfin)
                addProperty("name",rule.seriesName)})
            addProperty("enabled",rule.enabled)
            addProperty("paused",rule.paused)
            addProperty("createdOn",rule.createdOn)
            add("watchedSeasons",JsonArray().apply{rule.watchedSeasons.sorted().forEach{add(it)}})
            addProperty("includeSpecials",rule.includeSpecials)
            addProperty("onlyNewEpisodes",rule.onlyNewEpisodes)
            addProperty("resolutionMode",rule.resolutionMode)
            addProperty("resolutionWaitHours",rule.resolutionWaitHours)
            add("releaseTypes",JsonArray().apply{rule.releaseTypes.forEach{add(it)}})
            addProperty("hdrMode",rule.hdrMode)
            addProperty("dvMode",rule.dvMode)
            addProperty("atmosMode",rule.atmosMode)
            addProperty("languageMode",rule.languageMode)
            if(rule.maxEpisodeGb!=null)addProperty("maxEpisodeGb",rule.maxEpisodeGb)
            addProperty("checkMinutes",rule.checkMinutes)
            addProperty("confirm",true)
            addProperty("confirmed",true)
        }
    }
}

/** Stable rule id for a series identity key; used verbatim as the watcher's rule_id. */
fun watchRuleIdFor(seriesKey:String):String{
    require(seriesKey.isNotBlank()&&seriesKey.length<=160)
    val hash=MessageDigest.getInstance("SHA-256").digest(seriesKey.toByteArray(Charsets.UTF_8))
        .joinToString(""){"%02x".format(it)}.take(12)
    val prefix=when{seriesKey.startsWith("tmdb:") ->"tmdb";seriesKey.startsWith("tvdb:") ->"tvdb";seriesKey.startsWith("jellyfin:") ->"jf";else ->"imdb"}
    val clean=seriesKey.substringAfter(':').replace(Regex("[^A-Za-z0-9_-]"),"")
    require(clean.isNotEmpty()){"Series key carries no usable id"}
    return "watch_${prefix}_${clean.take(40)}_$hash"
}

// --- client ------------------------------------------------------------------

class WatcherTokenError(message:String):IOException(message)
fun watchToken(raw:String):String{
    val value=raw.trim()
    if(value.length !in 16..4096||value.any{it.code !in 33..126})
        throw WatcherTokenError("Enter STAK_WATCHER_TOKEN from the server's .env, without spaces or line breaks.")
    return value
}

class WatcherFailure(message:String):IOException(message)

fun watchEndpoint(raw:String):okhttp3.HttpUrl{
    val u=try{raw.trim().toHttpUrl()}catch(_:IllegalArgumentException){throw WatcherFailure("Enter a valid watcher URL, including http:// or https://.")}
    if(u.username.isNotEmpty()||u.password.isNotEmpty()||u.query!=null||u.fragment!=null)throw WatcherFailure("Use a watcher endpoint without embedded credentials or query parameters.")
    val pieces=u.host.split('.').mapNotNull{it.toIntOrNull()}
    val local=u.host in setOf("localhost","::1")||pieces.size==4&&(pieces[0]in listOf(10,127)||pieces[0]==192&&pieces[1]==168||pieces[0]==172&&pieces[1]in 16..31||pieces[0]==100&&pieces[1]in 64..127)
    if(!u.isHttps&&!local)throw WatcherFailure("Use HTTPS or an existing private LAN/VPN address for the watcher.")
    return u.newBuilder().encodedPath(u.encodedPath.trimEnd('/')+"/").build()
}

fun watchRuleError(error:Exception):String{
    // JVM unit tests have no android.util.Log; never let logging break reporting.
    try{android.util.Log.w("STAKWatcher",error.javaClass.name+";"+error.stackTrace.take(12).joinToString(";"))}
    catch(_:Exception){}
    return when(error){
        is WatcherTokenError->error.message!!
        is WatcherFailure->error.message!!
        is java.net.SocketTimeoutException,is java.io.InterruptedIOException->"The watcher timed out. Check its address and connection. Rules remain saved locally."
        is java.net.UnknownHostException->"The watcher address could not be resolved. Check the hostname or private IP address."
        is java.net.ConnectException,is java.net.NoRouteToHostException->"Cannot reach the watcher. Check its address, port and home network or VPN."
        is javax.net.ssl.SSLException->"The watcher HTTPS certificate could not be verified. Check the server certificate."
        else->if(error.message.orEmpty().contains("CLEARTEXT",ignoreCase=true))"Android blocked the local HTTP connection. Use HTTPS on a trusted host or a private LAN address."
            else "The watcher rules could not sync. Rules remain saved locally."
    }
}

/** Blocking HTTP transport; callers run it on Dispatchers.IO. */
open class HttpWatcherTransport{
    private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(25,java.util.concurrent.TimeUnit.SECONDS).build()
    open fun call(connection:WatcherConnection,path:String,body:JsonObject?=null,method:String="GET"):JsonObject{
        val token=watchToken(connection.token)
        val base=watchEndpoint(connection.url)
        val url=base.resolve(path.trimStart('/'))?:throw WatcherFailure("Invalid watcher endpoint")
        require(url.host==base.host&&url.port==base.port&&url.encodedPath.startsWith(base.encodedPath))
        val req=Request.Builder().url(url).header("Authorization","Bearer $token")
            .header("Accept","application/json").header("Connection","close")
        when{
            body!=null->req.post(body.toString().toRequestBody("application/json".toMediaType()))
            method=="POST"->req.post(ByteArray(0).toRequestBody(null)) // pause/resume carry no payload
            method=="DELETE"->req.delete()
        }
        return client.newCall(req.build()).execute().use{response->
            val bodyText=response.body?.byteStream()?.use { input ->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){val n=input.read(buffer);if(n<0)break
                    if(out.size()+n>4*1024*1024)throw WatcherFailure("The watcher response exceeded the size limit.")
                    out.write(buffer,0,n)}
                out.toString("UTF-8")
            }.orEmpty()
            if(!response.isSuccessful){
                val serverError=try{JsonParser.parseString(bodyText).asJsonObject.str("error")}catch(_:Exception){null}
                throw WatcherFailure(when(response.code){
                    401,403->"The watcher rejected the token (HTTP ${response.code}). Check STAK_WATCHER_TOKEN."
                    404->"The watcher answered HTTP 404. Check the address, or the rule is no longer known by the watcher."
                    else->buildString{
                        append("The watcher returned HTTP ${response.code}.")
                        // Never echo response bodies: remote errors can contain credential URLs.
                        append(" Local rules stay pending until it answers.")
                    }
                })
            }
            if(bodyText.length>4*1024*1024)throw WatcherFailure("The watcher response exceeded the size limit.")
            try{JsonParser.parseString(bodyText).asJsonObject}
            catch(_:Exception){throw WatcherFailure("The endpoint did not return a valid watcher response. Check the address.")}
        }
    }
}
/** Typed operations over the watcher API; the transport stays injectable for tests. */
class WatcherClient(private val connection:WatcherConnection,
                    private val transport:HttpWatcherTransport=HttpWatcherTransport()){
    fun health():JsonObject=transport.call(connection,"health")
    fun get(path:String):JsonObject=transport.call(connection,path)
    fun saveRule(payload:JsonObject):JsonObject=transport.call(connection,"v1/rules",payload,method="POST")
    fun pauseRule(ruleId:String):JsonObject=transport.call(connection,"v1/rules/$ruleId/pause",method="POST")
    fun resumeRule(ruleId:String):JsonObject=transport.call(connection,"v1/rules/$ruleId/resume",method="POST")
    fun removeRule(ruleId:String):JsonObject=transport.call(connection,"v1/rules/$ruleId",method="DELETE")
    fun episodes(ruleId:String):JsonArray=transport.call(connection,"v1/episodes?rule_id=$ruleId").getAsJsonArray("items")?:JsonArray()
}

fun watcherApi(connection:WatcherConnection):WatcherClient=WatcherClient(connection)

// --- persistence ---------------------------------------------------------------

/** Injectable so offline tests can run the full facade flows without Android storage. */
interface WatchRulePersistence{
    fun read():WatcherDocument
    fun update(change:(WatcherDocument)->WatcherDocument):WatcherDocument
}

class WatchRuleStore(private val settings:SettingsStore=NativeRuntime.settings):WatchRulePersistence{
    companion object{private val lock=Any()}
    override fun read():WatcherDocument=synchronized(lock){WatcherCodec.decode(settings.watcherRulesJson)}
    override fun update(change:(WatcherDocument)->WatcherDocument):WatcherDocument=synchronized(lock){
        settings.saveWatcherRulesChecked(WatcherCodec.encode(change(read())))
        read()
    }
}

// --- facade --------------------------------------------------------------------

private fun JsonObject.str(key:String):String?=get(key)?.takeIf{it.isJsonPrimitive}?.asString

/** Pure per-rule status built from a GET /v1/rules/{id} record + episode rows. */
internal fun watchStatusFromRows(rule:WatchRuleModel,remoteRule:JsonObject?,rows:JsonArray,now:Long=System.currentTimeMillis()):WatchRuleStatus{
    val counters=mutableMapOf<String,Int>();var total=0
    rows.forEach{row->
        val status=(row as? JsonObject)?.str("status")?:return@forEach
        total++;counters[status]=(counters[status]?:0)+1
    }
    val pausedOnServer=remoteRule?.get("paused")?.takeIf{it.isJsonPrimitive}?.asBoolean?:false
    return WatchRuleStatus(rule.ruleId,rule.seriesName,pausedOnServer,
        confirmed=remoteRule?.str("ruleId")==rule.ruleId,episodes=total,counters,now)
}

object WatchRules{
    val revision=MutableStateFlow(0L)
    /** Last sync problem (English, user-readable; never credentials). */
    val issue=MutableStateFlow<String?>(null)
    val store:WatchRulePersistence get() = WatchRuleStore()

    suspend fun document():WatcherDocument=withContext(Dispatchers.IO){store.read()}

    /** Series key used for rule identity (mirrors MediaItem.mediaKey for series). */
    fun seriesKeyOf(media:MediaItem):String?{
        if(!media.isSeries)return null
        return when{
            !media.imdbId.isNullOrBlank()->"imdb:${media.imdbId}"
            else->media.mediaKey.takeIf{it.isNotBlank()}
        }
    }

    fun ruleFor(key:String,doc:WatcherDocument=store.read()):WatchRuleModel?=doc.rules.firstOrNull{it.ruleId==key} ?: doc.rules.firstOrNull{it.ruleId==watchRuleIdFor(key)}
    fun statusFor(key:String,doc:WatcherDocument=store.read()):WatchRuleStatus?=doc.statuses.firstOrNull{it.ruleId==watchRuleIdFor(key)}

    data class SaveResult(val rule:WatchRuleModel,val issues:JsonArray,val confirmed:Boolean)

    /**
     * Persists the rule locally; when a watcher is paired the rule is pushed
     * WITH explicit consent (`confirmed=true` payload): a plain watchlist
     * action must not enable automation. Local state stays "saved_local"
     * until the watcher answers; then it becomes "server_confirmed".
     */
    suspend fun saveRule(rule:WatchRuleModel,consent:Boolean,
                         persistence:WatchRulePersistence=store,
                         clientFactory:(WatcherConnection)->WatcherClient=::watcherApi):SaveResult{
        operationLock.lock()
        try{
        require(rule.state in WatchRuleModel.VALID_STATES)
        val connection=persistence.read().connection
        val toSave=rule.copy(createdOn=rule.createdOn.ifBlank{java.time.LocalDate.now().toString()},
            resolutionWaitHours=watchCanonicalWait(rule),state=WatchRuleModel.STATE_SAVED_LOCAL,pendingAction="save")
        persistence.update{doc->
            doc.copy(rules=doc.rules.filterNot{it.ruleId==toSave.ruleId}+toSave,
                statuses=doc.statuses.filterNot{it.ruleId==toSave.ruleId})
        }
        var confirmed=false;var issues:JsonArray=JsonArray()
        if(connection.enabled&&!connection.url.isBlank()&&connection.token.isNotBlank()){
            val payload=WatcherCodec.payloadFor(toSave,consent)
            try{
                val response=withContext(Dispatchers.IO){
                    clientFactory(connection).saveRule(payload)
                }
                confirmed=true
                issue.value=null
                issues=response.getAsJsonArray("issues")?:JsonArray()
                markConfirmed(toSave.ruleId,persistence)
            }catch(c:CancellationException){throw c}
            catch(error:Exception){issue.value=watchRuleError(error)}
        }else issue.value=null
        revision.value++
        return SaveResult(toSave.copy(state=if(confirmed)WatchRuleModel.STATE_SERVER_CONFIRMED else toSave.state,pendingAction=if(confirmed)null else "save"),issues,confirmed)
        }finally{operationLock.unlock()}
    }

    private fun markConfirmed(ruleId:String,persistence:WatchRulePersistence=store)=persistence.update{doc->
        doc.copy(rules=doc.rules.map{if(it.ruleId==ruleId)it.copy(state=WatchRuleModel.STATE_SERVER_CONFIRMED,pendingAction=null) else it})
    }

    // Desired operations remain visible until the server acknowledges them. Refresh
    // only reconciles reads; restoring a backup must never replay a mutation.
    val episodeRows=MutableStateFlow<Map<String,List<WatchEpisode>>>(emptyMap())
    val refreshing=MutableStateFlow(false)
    val lastContact=MutableStateFlow(0L)
    private val operationLock=kotlinx.coroutines.sync.Mutex()

    suspend fun turnOff(key:String,persistence:WatchRulePersistence=store,
                        clientFactory:(WatcherConnection)->WatcherClient=::watcherApi){
        operationLock.lock()
        try{
            val doc=persistence.read();val rule=ruleFor(key,doc)?:return
            persistence.update{d->d.copy(rules=d.rules.map{if(it.ruleId==rule.ruleId)it.copy(pendingAction="off",state=WatchRuleModel.STATE_SAVED_LOCAL) else it})}
            revision.value++
            try{
                requirePaired(doc.connection)
                withContext(Dispatchers.IO){clientFactory(doc.connection).removeRule(rule.ruleId)}
                removeLocal(rule.ruleId,persistence);issue.value=null
            }catch(c:CancellationException){throw c}
            catch(error:Exception){issue.value=watchRuleError(error)}
        }finally{revision.value++;operationLock.unlock()}
    }

    suspend fun setPaused(key:String,paused:Boolean,persistence:WatchRulePersistence=store,
                          clientFactory:(WatcherConnection)->WatcherClient=::watcherApi){
        operationLock.lock()
        try{
            val doc=persistence.read();val rule=ruleFor(key,doc)?:return
            val pending=if(rule.pendingAction=="save")"save" else if(paused)"pause" else "resume"
            persistence.update{d->d.copy(rules=d.rules.map{if(it.ruleId==rule.ruleId)it.copy(paused=paused,pendingAction=pending,state=WatchRuleModel.STATE_SAVED_LOCAL) else it})}
            revision.value++
            try{
                requirePaired(doc.connection)
                withContext(Dispatchers.IO){if(paused)clientFactory(doc.connection).pauseRule(rule.ruleId) else clientFactory(doc.connection).resumeRule(rule.ruleId)}
                if(pending!="save")markConfirmed(rule.ruleId,persistence)
                issue.value=null
            }catch(c:CancellationException){throw c}
            catch(error:Exception){issue.value=watchRuleError(error)}
        }finally{revision.value++;operationLock.unlock()}
    }

    private fun requirePaired(c:WatcherConnection){
        if(!c.enabled||c.url.isBlank()||c.token.isBlank())throw WatcherFailure("Pair your watcher to confirm this change. It has not been applied on the server.")
    }
    private fun removeLocal(id:String,persistence:WatchRulePersistence){
        persistence.update{it.copy(rules=it.rules.filterNot{r->r.ruleId==id},statuses=it.statuses.filterNot{s->s.ruleId==id})}
        episodeRows.value=episodeRows.value-id
    }

    suspend fun refresh(persistence:WatchRulePersistence=store,
                        clientFactory:(WatcherConnection)->WatcherClient=::watcherApi):WatcherDocument=withContext(Dispatchers.IO){
        operationLock.lock();refreshing.value=true
        try{
            val doc=persistence.read();val connection=doc.connection
            if(!connection.enabled||connection.url.isBlank()||connection.token.isBlank())return@withContext doc
            val client=clientFactory(connection)
            var failure:String?=null
            for(rule in doc.rules){
                try{
                    val record=client.get("v1/rules/${rule.ruleId}")
                    val remote=record.getAsJsonObject("rule")?:record.takeIf{it.str("ruleId")==rule.ruleId}
                    val rows=client.episodes(rule.ruleId)
                    val status=watchStatusFromRows(rule,remote,rows)
                    val matches=remote!=null&&watchRuleMatches(rule,remote)
                    when{
                        rule.pendingAction=="off"&&status.confirmed&&status.paused->removeLocal(rule.ruleId,persistence)
                        rule.pendingAction!="off"&&matches->markConfirmed(rule.ruleId,persistence)
                        else->persistence.update{d->d.copy(rules=d.rules.map{if(it.ruleId==rule.ruleId)it.copy(state=WatchRuleModel.STATE_SAVED_LOCAL) else it})}
                    }
                    if(persistence.read().rules.any{it.ruleId==rule.ruleId}){
                        upsertStatus(status,persistence)
                        episodeRows.value=episodeRows.value+(rule.ruleId to watchEpisodes(rows))
                    }
                    lastContact.value=System.currentTimeMillis()
                }catch(c:CancellationException){throw c}
                catch(error:Exception){failure=watchRuleError(error)}
            }
            issue.value=failure
        }catch(c:CancellationException){throw c}
        catch(error:Exception){issue.value=watchRuleError(error)}
        finally{refreshing.value=false;revision.value++;operationLock.unlock()}
        persistence.read()
    }

    private fun upsertStatus(status:WatchRuleStatus,persistence:WatchRulePersistence)=persistence.update{doc->
        doc.copy(statuses=(doc.statuses.filterNot{it.ruleId==status.ruleId}+status).sortedBy{it.ruleId})
    }

    suspend fun saveDefault(profile:WatchQualityProfile,persistence:WatchRulePersistence=store){
        withContext(Dispatchers.IO){persistence.update{it.copy(qualityDefault=profile)}};revision.value++
    }

    /** Stores pairing for the stak-watch watcher (URL + token, validated). */
    suspend fun saveConnection(url:String,token:String,enabled:Boolean=true,persistence:WatchRulePersistence=store):String{
        operationLock.lock()
        try{
        val trimmedToken=watchToken(token)
        val endpoint=watchEndpoint(url).toString()
        persistence.update{doc->
            val changed=doc.connection.url!=endpoint||doc.connection.token!=trimmedToken
            doc.copy(connection=WatcherConnection(endpoint,trimmedToken,enabled),
                rules=if(changed)doc.rules.map{it.copy(state=WatchRuleModel.STATE_SAVED_LOCAL)} else doc.rules,
                statuses=if(changed)emptyList() else doc.statuses)
        }
        episodeRows.value=emptyMap();lastContact.value=0
        issue.value=null
        revision.value++
        return endpoint
        }finally{operationLock.unlock()}
    }

    /** Pairing probe: does this endpoint really answer like a stak-watch watcher? */
    suspend fun probe(connection:WatcherConnection,clientFactory:(WatcherConnection)->WatcherClient=::watcherApi):String=withContext(Dispatchers.IO){
        val client=clientFactory(connection)
        val health=client.health()
        val ok=health.get("ok")?.takeIf{it.isJsonPrimitive}?.asBoolean==true
        if(!ok)throw WatcherFailure("This endpoint did not answer like a stak-watch watcher.")
        client.get("v1/status") // health is public; this verifies the actual bearer token.
        val version=health.str("version").orEmpty().ifBlank{"?"}
        "Watcher v$version paired"
    }
}
