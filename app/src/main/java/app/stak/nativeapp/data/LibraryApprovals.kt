package app.stak.nativeapp.data

import com.google.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID

data class LibraryApproval(val id:String,val scope:String,val hash:String?=null,val name:String="",val type:String="unknown",val approved:Boolean=true,val revision:String=UUID.randomUUID().toString(),val sentTo:String?=null,val state:String="saved",val note:String="",val at:Long=System.currentTimeMillis())
data class LibraryFilterConnection(val url:String="",val token:String="",val enabled:Boolean=false){override fun toString()="LibraryFilterConnection(credentials hidden)"}
data class ApprovalDocument(val version:Int=1,val connection:LibraryFilterConnection=LibraryFilterConnection(),val entries:List<LibraryApproval> = emptyList())

fun approvalHash(raw:String?):String? {
    val value=raw?.lowercase()?.removePrefix("urn:btmh:1220")?:return null
    return value.takeIf{it.matches(Regex("[a-f0-9]{40}|[a-f0-9]{64}"))}
}
/** Only controlled messages reach UI; networking exceptions can contain credentials. */
class LibraryFilterFailure(message:String):IOException(message)
fun filterToken(raw:String):String {
    val value=raw.trim().removePrefix("STAK_ACCESS_TOKEN=").trim()
    if(value.length !in 32..4096 || value.any{it.code !in 33..126})
        throw LibraryFilterFailure("Enter the filter access token from STAK_ACCESS_TOKEN, without spaces or line breaks.")
    return value
}
fun libraryFilterError(error:Exception):String {
    android.util.Log.w("STAKFilter",error.javaClass.name+";"+error.stackTrace.take(12).joinToString(";"))
    return when(error){
    is LibraryFilterFailure -> error.message!!
    is java.net.SocketTimeoutException,is java.io.InterruptedIOException -> "The library filter timed out. Check its address and connection. Approvals remain saved."
    is java.net.UnknownHostException -> "The filter address could not be resolved. Check the hostname or private IP address."
    is java.net.ConnectException,is java.net.NoRouteToHostException -> "Cannot connect to the filter. Check its address, port and home Wi-Fi or VPN."
    is javax.net.ssl.SSLException -> "The filter HTTPS certificate could not be verified. Check the server certificate."
    else -> if(error.message.orEmpty().contains("CLEARTEXT",ignoreCase=true)) "Android blocked the local HTTP connection. Update STAK or use a configured HTTPS endpoint."
        else "Library sync could not finish. Existing approvals remain saved. Retry the connection."
}
}
fun approvalEndpoint(raw:String):HttpUrl {
    val u=try{raw.trim().toHttpUrl()}catch(_:IllegalArgumentException){throw LibraryFilterFailure("Enter a valid filter URL, including http:// or https://.")};if(u.username.isNotEmpty()||u.password.isNotEmpty()||u.query!=null||u.fragment!=null)throw LibraryFilterFailure("Use an endpoint without embedded credentials or query parameters.")
    val pieces=u.host.split('.').mapNotNull{it.toIntOrNull()}
    val local=u.host in setOf("localhost","::1")||pieces.size==4&&(pieces[0]in listOf(10,127)||pieces[0]==192&&pieces[1]==168||pieces[0]==172&&pieces[1]in 16..31||pieces[0]==100&&pieces[1]in 64..127)
    if(!u.isHttps&&!local)throw LibraryFilterFailure("Use HTTPS or an existing private LAN/VPN address.")
    return u.newBuilder().encodedPath(u.encodedPath.trimEnd('/')+"/").build()
}
fun approvalConnectionId(c:LibraryFilterConnection)=credentialScope(c.url.trimEnd('/')+"|"+c.token)

class ApprovalStore(private val settings:SettingsStore=NativeRuntime.settings){
    companion object{private val lock=Any()}
    fun read():ApprovalDocument=synchronized(lock){
        val raw=settings.libraryApprovalsJson?:return@synchronized ApprovalDocument()
        try{val root=JsonParser.parseString(raw).asJsonObject;require(root.get("version")?.asInt==1&&root.get("entries")?.isJsonArray==true&&root.get("connection")?.isJsonObject==true)
            Gson().fromJson(root,ApprovalDocument::class.java).also{data->require(data.entries.size<=10000);data.entries.forEach{require(it.id.matches(Regex("\\d{1,20}"))&&it.scope.matches(Regex("[a-f0-9]{64}"))&&it.type in setOf("movie","series","unknown"));require(it.hash==null||approvalHash(it.hash)==it.hash)}}
        }catch(e:Exception){throw IOException("Saved library approvals could not be read. Existing data was not overwritten.",e)}
    }
    fun update(change:(ApprovalDocument)->ApprovalDocument):ApprovalDocument=synchronized(lock){val old=read();val next=change(old);require(next.entries.size<=10000){"Approval limit reached."};if(next!=old)settings.saveLibraryApprovalsChecked(Gson().toJson(next));next}
    fun approve(id:String,key:String,hash:String?=null,name:String="",type:String="unknown"):LibraryApproval {
        require(id.matches(Regex("\\d{1,20}"))&&key.isNotBlank());val account=credentialScope("torbox:$key");val cleanHash=approvalHash(hash)
        val next=update{doc->val old=doc.entries.firstOrNull{it.scope==account&&it.id==id};val row=LibraryApproval(id,account,cleanHash?:old?.hash,name.take(160).ifBlank{old?.name?:"Torrent $id"},type.takeIf{it in setOf("movie","series")}?:old?.type?:"unknown",revision=UUID.randomUUID().toString(),at=old?.at?:System.currentTimeMillis())
            doc.copy(entries=doc.entries.filterNot{it.scope==account&&it.id==id}+row)}
        return next.entries.last()
    }
    fun importRecorded(account:String,records:List<DeliveryRecord>)=update{doc->
        val ids=doc.entries.filter{it.scope==account}.map{it.id}.toSet()
        val added=records.filter{it.torboxAccount==account&&it.torboxId.matches(Regex("\\d{1,20}"))&&it.torboxId !in ids}.distinctBy{it.torboxId}.map{record->
            LibraryApproval(record.torboxId,account,approvalHash(record.hash),(record.media?.name?:record.releaseName).take(160).ifBlank{"Torrent ${record.torboxId}"},record.media?.type?.takeIf{it in setOf("movie","series")}?:"unknown",at=record.at)
        }
        doc.copy(entries=doc.entries+added)
    }
    fun decide(row:LibraryApproval,approved:Boolean,type:String=row.type)=update{doc->doc.copy(entries=doc.entries.map{if(it.scope==row.scope&&it.id==row.id)it.copy(approved=approved,type=type,revision=UUID.randomUUID().toString(),sentTo=null,state="saved",note="")else it})}
    fun acknowledge(row:LibraryApproval,connection:String,state:String,note:String)=update{doc->doc.copy(entries=doc.entries.map{if(it.scope==row.scope&&it.id==row.id&&it.revision==row.revision)it.copy(sentTo=connection,state=state,note=note.take(160))else it})}
}

class LibraryFilterClient(private val connection:LibraryFilterConnection,private val client:OkHttpClient=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).callTimeout(25,java.util.concurrent.TimeUnit.SECONDS).build()){
    private suspend fun call(path:String,body:JsonObject?=null):JsonObject=withContext(Dispatchers.IO){
        val token=filterToken(connection.token)
        val base=approvalEndpoint(connection.url);val url=base.resolve(path)?:throw IOException("Invalid filter endpoint")
        require(url.scheme==base.scheme&&url.host==base.host&&url.port==base.port&&url.encodedPath.startsWith(base.encodedPath))
        // The v1 Python helper closes HTTP/1.0 responses without a close header.
        // Do not reuse that socket for approval POSTs or enable mutation retries.
        val req=Request.Builder().url(url).header("Authorization","Bearer $token").header("Accept","application/json").header("Connection","close")
        if(body!=null)req.post(body.toString().toRequestBody("application/json".toMediaType()))
        client.newCall(req.build()).execute().use{response->
            if(!response.isSuccessful)throw LibraryFilterFailure(when(response.code){
                401,403->"The filter rejected the access token (HTTP ${response.code}). Use STAK_ACCESS_TOKEN from the running filter."
                404->"The filter API was not found (HTTP 404). Enter its base URL without /v1/status."
                else->"The filter returned HTTP ${response.code}. Approvals remain saved; check the filter service."
            })
            val source=response.body?.source()?:throw LibraryFilterFailure("The filter returned an empty response. Check the endpoint.");source.request(4*1024*1024+1L);if(source.buffer.size>4*1024*1024)throw LibraryFilterFailure("The filter response exceeded the size limit.")
            try{JsonParser.parseString(source.buffer.readUtf8()).asJsonObject}catch(_:Exception){throw LibraryFilterFailure("The endpoint did not return a valid filter response. Check the address.")}
        }
    }
    suspend fun status(scope:String):JsonObject=call("v1/status").also{if(it.text("protocol")!="stak-library-filter/1")throw LibraryFilterFailure("This endpoint is not a supported STAK library filter.");if(it.text("scope")!=scope)throw LibraryFilterFailure("The filter uses a different TorBox API key. Match the app and filter TorBox settings.")}
    suspend fun send(row:LibraryApproval)=call("v1/approvals",JsonObject().apply{addProperty("id",row.id);addProperty("scope",row.scope);row.hash?.let{addProperty("hash",it)};addProperty("name",row.name);addProperty("type",row.type);addProperty("approved",row.approved)})
    suspend fun entries()=call("v1/approvals").objects("items")
}

object LibraryApprovals {
    private val lock=Mutex();private val tasks=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val revision=MutableStateFlow(0L);val issue=MutableStateFlow<String?>(null);val issueAccount=MutableStateFlow("")
    suspend fun capture(id:String,key:String,hash:String?=null,name:String="",media:MediaItem?=null)=withContext(Dispatchers.IO){
        issueAccount.value=credentialScope("torbox:$key")
        try{ApprovalStore().approve(id,key,hash,media?.name?:name,media?.type?:"unknown");issue.value=null;revision.value++;tasks.launch{try{sync()}catch(c:CancellationException){throw c}catch(e:Exception){issue.value=libraryFilterError(e)}};true}
        catch(c:CancellationException){throw c}
        catch(_:Exception){issue.value="Added to TorBox, but library approval could not be saved. Approve it from the TorBox file details; do not add it again.";false}
    }
    suspend fun sync():JsonObject?=lock.withLock{
        withContext(Dispatchers.IO){
            val settings=NativeRuntime.settings;val store=ApprovalStore(settings);val account=torboxScope(settings)
            val connection=store.read().connection
            if(!connection.enabled)return@withContext null
            // Confirm the connection before optional legacy-data work. A malformed old journal
            // must never prevent existing explicit approvals from reaching the filter.
            val client=LibraryFilterClient(connection);val status=client.status(account);val identity=approvalConnectionId(connection)
            val legacy=try{IntelligenceStore.get(settings).read().deliveries}catch(c:CancellationException){throw c}catch(_:Exception){null}
            if(legacy!=null)store.importRecorded(account,legacy)else status.addProperty("stak_import_warning",true)
            val saved=store.read()
            for(row in saved.entries.filter{it.scope==account&&it.sentTo!=identity}){
                if(torboxScope()!=account||approvalConnectionId(store.read().connection)!=identity)throw CancellationException("Library account or endpoint changed")
                val response=client.send(row);store.acknowledge(row,identity,response.text("state")?:"pending",response.text("note").orEmpty())
            }
            val updates=client.entries()
            for(row in store.read().entries.filter{it.scope==account&&it.sentTo==identity}){
                updates.firstOrNull{it.text("id")==row.id}?.let{remote->store.acknowledge(row,identity,remote.text("state")?:"pending",remote.text("note").orEmpty())}
            }
            issue.value=null;revision.value++;status
        }
    }
}
