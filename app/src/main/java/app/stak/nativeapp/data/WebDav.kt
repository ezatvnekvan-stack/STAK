package app.stak.nativeapp.data

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.io.IOException
import javax.xml.parsers.SAXParserFactory
import java.util.concurrent.TimeUnit

class DavHttpFailure(val status:Int):IOException(if(status in listOf(401,403))"WebDAV authentication denied. Check the saved credentials."else "WebDAV PROPFIND failed ($status). Check endpoint and network access.")

data class DavEntry(val url:String,val relativePath:String,val name:String,val directory:Boolean,val size:Long?,val etag:String?=null)
data class DavListing(val entries:List<DavEntry>,val truncated:Boolean=false,val latencyMs:Long=0)
fun davEndpoint(raw:String):HttpUrl {val u=raw.trim().toHttpUrl();require(u.scheme in setOf("http","https")&&u.username.isEmpty()&&u.password.isEmpty()&&u.query==null&&u.fragment==null){"Use an HTTP(S) WebDAV URL without embedded credentials."};return u.newBuilder().encodedPath(u.encodedPath.trimEnd('/')+"/").build()}
fun sameDavRoot(base:HttpUrl,url:HttpUrl)=base.scheme==url.scheme&&base.host==url.host&&base.port==url.port&&(url.encodedPath==base.encodedPath.trimEnd('/')||url.encodedPath.startsWith(base.encodedPath))&&url.username.isEmpty()&&url.password.isEmpty()&&url.query==null&&url.fragment==null
fun parseDavListing(xml:String,base:HttpUrl,current:HttpUrl,limit:Int=5000):DavListing {
    require(sameDavRoot(base,current));require(xml.length<=8*1024*1024){"Directory listing is too large. Open a narrower WebDAV folder."}
    require(!Regex("(?i)<!\\s*(DOCTYPE|ENTITY)").containsMatchIn(xml)){"Unsafe WebDAV XML declaration."}
    val result=mutableListOf<DavEntry>();var truncated=false;var validRoot=false
    val factory=SAXParserFactory.newInstance();factory.isNamespaceAware=true
    factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false)
    val parser=factory.newSAXParser().xmlReader
    parser.entityResolver=org.xml.sax.EntityResolver{_,_->throw SAXException("External XML entities are not allowed")}
    parser.contentHandler=object:DefaultHandler(){
        var inside=false;var tag="";var text=StringBuilder();var href="";var name="";var size:Long?=null;var dir=false;var etag:String?=null;var status="";var success=false;var savedName="";var savedSize:Long?=null;var savedDir=false;var savedEtag:String?=null
        override fun startElement(uri:String?,local:String?,q:String?,a:Attributes?){tag=local ?: q.orEmpty().substringAfter(':');text=StringBuilder();if(tag=="multistatus"&&uri=="DAV:")validRoot=true;if(tag=="response"){inside=true;href="";name="";size=null;dir=false;etag=null;status="";success=false};if(inside&&tag=="propstat"){savedName=name;savedSize=size;savedDir=dir;savedEtag=etag;name="";size=null;dir=false;etag=null;status=""};if(inside&&tag=="collection")dir=true}
        override fun characters(c:CharArray,start:Int,length:Int){if(text.length<32768)text.append(c,start,length)}
        override fun endElement(uri:String?,local:String?,q:String?){val t=local ?: q.orEmpty().substringAfter(':');val value=text.toString().trim();if(inside)when(t){"href"->href=value;"displayname"->name=value;"getcontentlength"->size=value.toLongOrNull()?.takeIf{it>=0};"getetag"->etag=value;"status"->{status=value;if(value.contains(" 200 "))success=true}}
            if(t=="propstat"){if(!status.contains(" 200 ")){name=savedName;size=savedSize;dir=savedDir;etag=savedEtag}else{name=name.ifBlank{savedName};size=size ?: savedSize;dir=dir||savedDir;etag=etag ?: savedEtag}}
            if(t=="response"){inside=false;val u=current.resolve(href);if(u!=null&&sameDavRoot(base,u)&&u.encodedPath.trimEnd('/')!=current.encodedPath.trimEnd('/')&&(status.isBlank()||success)){
                val rel=java.net.URLDecoder.decode(u.encodedPath.removePrefix(base.encodedPath).replace("+","%2B"),"UTF-8")
                if(rel.split('/').none{it==".."||it=="."||it.contains('\u0000')||it.contains('\\')})if(result.size<limit)result+=DavEntry(u.toString(),rel,name.ifBlank{rel.trimEnd('/').substringAfterLast('/')},dir,size,etag) else truncated=true
            }}
            text=StringBuilder()
        }
    }
    parser.parse(InputSource(StringReader(xml)));require(validRoot){"The endpoint did not return a WebDAV Multi-Status listing."}
    return DavListing(result.distinctBy{it.url},truncated)
}
class WebDavClient(private val client:OkHttpClient=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(25,TimeUnit.SECONDS).build()){
    suspend fun list(config:ZurgConnection,path:String?=null,depth:Int=1):DavListing=withContext(Dispatchers.IO){
        require(depth in 0..1);val base=davEndpoint(config.endpoint);val current=path?.toHttpUrl() ?: base;require(sameDavRoot(base,current)){"Folder is outside the configured WebDAV root."}
        val body="""<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:displayname/><d:resourcetype/><d:getcontentlength/><d:getetag/></d:prop></d:propfind>""".toRequestBody("application/xml; charset=utf-8".toMediaType())
        val request=Request.Builder().url(current).header("Depth",depth.toString()).method("PROPFIND",body)
        if(config.username.isNotBlank())request.header("Authorization",Credentials.basic(config.username,config.password,Charsets.UTF_8))
        val start=System.nanoTime();client.newCall(request.build()).execute().use{response->
            if(response.code in listOf(401,403))throw DavHttpFailure(response.code)
            if(response.code!=207)throw DavHttpFailure(response.code)
            val stream=response.body?.source() ?: throw IOException("Empty WebDAV response.");stream.request(8*1024*1024+1L);require(stream.buffer.size<=8*1024*1024){"Directory listing exceeds 8 MiB. Configure a narrower root."}
            parseDavListing(stream.buffer.readUtf8(),base,current).copy(latencyMs=(System.nanoTime()-start)/1_000_000)
        }
    }
}
fun mediaFile(name:String)=Regex("(?i)\\.(mkv|mp4|m4v|avi|mov|ts|m2ts|webm)$").containsMatchIn(name)
fun endpointReachability(raw:String):String=runCatching{val u=davEndpoint(raw);when{u.host=="localhost"||u.host.endsWith(".local")||u.host.startsWith("192.168.")||u.host.startsWith("10.")||u.host.startsWith("100.")->"Local/VPN address · access depends on your network";u.isHttps->"HTTPS endpoint · remote reachability must be checked";else->"HTTP endpoint · use a trusted network or VPN"}}.getOrDefault("Endpoint not configured")

data class DavHealth(val endpointScope:String,val successfulAt:Long,val latencyMs:Long)
fun davHealthScope(config:ZurgConnection)=credentialScope(config.endpoint+"|"+config.username+"|"+config.password)
fun lastDavHealth(settings:SettingsStore):DavHealth?=runCatching{settings.zurgHealthJson?.let{com.google.gson.Gson().fromJson(it,DavHealth::class.java)}?.takeIf{it.endpointScope==davHealthScope(readZurg(settings))}}.getOrNull()
