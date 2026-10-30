package app.stak.nativeapp.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.TimeUnit

const val MAX_TORRENT_BYTES=5*1024*1024
data class TorrentUpload(val name:String,val bytes:ByteArray)
data class MagnetInput(val original:String,val magnet:String,val identity:String)
fun parseMagnetInput(raw:String):MagnetInput {
    val value=raw.trim();require(value.isNotEmpty()&&value.length<=32768){"Enter a magnet link or info hash (up to 32 KiB)."}
    fun hash(text:String):String? {
        if(text.matches(Regex("[a-fA-F0-9]{40}")))return text.lowercase(Locale.ROOT)
        if(text.matches(Regex("[A-Za-z2-7]{32}"))) {
            var buffer=0;var bits=0;val output=java.io.ByteArrayOutputStream()
            text.uppercase(Locale.ROOT).forEach{char->buffer=(buffer shl 5) or "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(char);bits+=5;if(bits>=8){bits-=8;output.write((buffer shr bits) and 255)}}
            return output.toByteArray().joinToString(""){"%02x".format(it)}
        };return null
    }
    hash(value)?.let{return MagnetInput(value,"magnet:?xt=urn:btih:$it",it)}
    val uri=runCatching{URI(value)}.getOrNull()
    require(uri?.scheme.equals("magnet",true)&&value.startsWith("magnet:?",true)&&uri?.rawFragment==null){"Use a magnet link, a 40-character hex hash or a 32-character base32 hash."}
    val topics=value.substringAfter('?').split('&').filter{it.substringBefore('=').equals("xt",true)}.map{URLDecoder.decode(it.substringAfter('='),"UTF-8")}
    val identity=topics.firstNotNullOfOrNull{topic->when{topic.startsWith("urn:btih:",true)->hash(topic.substringAfterLast(':'));topic.matches(Regex("(?i)urn:btmh:1220[a-f0-9]{64}"))->topic.lowercase(Locale.ROOT);else->null}}
    require(identity!=null){"The magnet link has no supported torrent info hash."}
    require(value.none{it=='\r'||it=='\n'}){"Use one magnet link per line."}
    return MagnetInput(value,value,identity)
}
fun parseMagnetBatch(text:String):List<MagnetInput> {
    val lines=text.lineSequence().map{it.trim()}.filter{it.isNotEmpty()}.toList()
    require(lines.size<=50){"Add at most 50 magnet links or hashes at a time."}
    return lines.map{parseMagnetInput(it)}.distinctBy{it.identity}
}
fun validateTorrentUpload(file:TorrentUpload) {
    require(file.name.endsWith(".torrent",true)&&file.name.length<=255&&file.name.none{it=='\r'||it=='\n'||it=='/'||it=='\\'}){"Choose a .torrent file."}
    require(file.bytes.isNotEmpty()&&file.bytes.size<=MAX_TORRENT_BYTES){"Torrent files must be between 1 byte and 5 MiB."}
    require(file.bytes.first()=='d'.code.toByte()&&file.bytes.last()=='e'.code.toByte()){"This file does not look like torrent metadata."}
}
enum class TorrentOperation(val wire:String,val label:String){Pause("pause","Pause"),Resume("resume","Resume"),Reannounce("reannounce","Reannounce"),Delete("delete","Delete")}
data class TorrentMutationResult(val id:String,val success:Boolean,val message:String?=null,val uncertain:Boolean=false)
data class TorrentAddResult(val input:String,val success:Boolean,val torrentId:String?=null,val message:String?=null,val uncertain:Boolean=false)
class UnconfirmedTorrentMutation:IOException("The request outcome could not be confirmed. Refresh your library before retrying.")
interface TorBoxMutations {
    suspend fun addMagnet(key:String,input:MagnetInput):String
    suspend fun upload(key:String,file:TorrentUpload):String
    suspend fun control(key:String,id:String,operation:TorrentOperation)
}
/** Fixed production origin; test transports can point only to loopback HTTP. No automatic POST retries. */
class DirectTorBoxMutations(
    private val baseUrl:String="https://api.torbox.app/v1/api",
    private val client:OkHttpClient=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).callTimeout(45,TimeUnit.SECONDS).build()
):TorBoxMutations {
    init{require(baseUrl=="https://api.torbox.app/v1/api"||baseUrl.matches(Regex("http://127\\.0\\.0\\.1:\\d+/v1/api")))}
    private suspend fun post(key:String,path:String,body:RequestBody,cachedOnly:Boolean=false):JsonObject=withContext(Dispatchers.IO) {
        require(key.isNotBlank()&&key.length<=4096&&key.none{it=='\r'||it=='\n'}){"Configure TorBox in Settings first."}
        val once=object:RequestBody(){override fun contentType()=body.contentType();override fun contentLength()=body.contentLength();override fun isOneShot()=true;override fun writeTo(sink:BufferedSink)=body.writeTo(sink)}
        val request=Request.Builder().url(baseUrl+path).header("Authorization","Bearer $key").post(once).build()
        try {client.newCall(request).execute().use{response->
            if(response.code==408||response.code>=500)throw UnconfirmedTorrentMutation()
            if(!response.isSuccessful){if(cachedOnly)throw NabFailure("TorBox refused the cached-only addition (HTTP ${response.code}). Refresh sources and check account limits.");throw IOException("TorBox rejected the request (HTTP ${response.code}).")}
            val data=runCatching{JsonParser.parseString(response.body?.string()).asJsonObject}.getOrElse{throw UnconfirmedTorrentMutation()}
            val status=data.get("success")
            if(status?.isJsonPrimitive!=true||!status.asJsonPrimitive.isBoolean)throw UnconfirmedTorrentMutation()
            if(!status.asBoolean){if(cachedOnly)throw NabFailure("TorBox could not add this cached release. Cache availability or account limits may have changed.");throw IOException("TorBox did not accept the operation. Check account limits and torrent status.")}
            data
        }}catch(error:IOException){if(error is UnconfirmedTorrentMutation||error.message?.startsWith("TorBox ")==true)throw error;throw UnconfirmedTorrentMutation()}
    }
    private fun addedId(data:JsonObject):String=data.objectOrNull("data")?.let{it.text("torrent_id") ?: it.text("id")}?.takeIf{it.matches(Regex("\\d+"))} ?: throw UnconfirmedTorrentMutation()
    override suspend fun addMagnet(key:String,input:MagnetInput):String=addMagnetInternal(key,input,false)
    suspend fun addMagnetCached(key:String,input:MagnetInput):String=addMagnetInternal(key,input,true)
    private suspend fun addMagnetInternal(key:String,input:MagnetInput,cachedOnly:Boolean):String {
        val checked=parseMagnetInput(input.magnet)
        val id=addedId(post(key,"/torrents/createtorrent",MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("magnet",checked.magnet).apply{if(cachedOnly)addFormDataPart("add_only_if_cached","true")}.build(),cachedOnly))
        if(baseUrl=="https://api.torbox.app/v1/api")LibraryApprovals.capture(id,key,checked.identity,"Torrent $id")
        runCatching{DeliveryTracking.record(id,key,source=SourceRelease(checked.original,infoHash=checked.identity))}
        return id
    }
    override suspend fun upload(key:String,file:TorrentUpload):String=uploadInternal(key,file,false)
    suspend fun uploadCached(key:String,file:TorrentUpload):String=uploadInternal(key,file,true)
    private suspend fun uploadInternal(key:String,file:TorrentUpload,cachedOnly:Boolean):String {
        validateTorrentUpload(file)
        val id=addedId(post(key,"/torrents/createtorrent",MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file",file.name,file.bytes.toRequestBody("application/x-bittorrent".toMediaType())).apply{if(cachedOnly)addFormDataPart("add_only_if_cached","true")}.build(),cachedOnly))
        if(baseUrl=="https://api.torbox.app/v1/api")LibraryApprovals.capture(id,key,name=file.name)
        runCatching{DeliveryTracking.record(id,key,source=SourceRelease(file.name))}
        return id
    }
    override suspend fun control(key:String,id:String,operation:TorrentOperation) {
        val numeric=id.toLongOrNull();require(id.matches(Regex("\\d+"))&&numeric!=null&&numeric>=0){"Invalid TorBox torrent identifier."}
        val body=JsonObject().apply{addProperty("torrent_id",numeric);addProperty("operation",operation.wire);addProperty("all",false)}
        post(key,"/torrents/controltorrent",body.toString().toRequestBody("application/json".toMediaType()))
    }
}

fun readTorrentUpload(name:String,stream:java.io.InputStream):TorrentUpload=stream.use{input->
    val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(32768)
    while(true){val count=input.read(buffer);if(count<0)break;require(output.size()+count<=MAX_TORRENT_BYTES){"Torrent files must not exceed 5 MiB."};output.write(buffer,0,count)}
    TorrentUpload(name,output.toByteArray()).also{validateTorrentUpload(it)}
}
