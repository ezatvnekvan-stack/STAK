package app.stak.nativeapp.data

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.xml.sax.*
import org.xml.sax.helpers.DefaultHandler
import java.io.*
import java.util.concurrent.TimeUnit
import javax.xml.parsers.SAXParserFactory

data class TorznabIndexer(val id:String=java.util.UUID.randomUUID().toString(),val name:String="Indexer",val url:String="",val apiKey:String="",val enabled:Boolean=true){override fun toString()="TorznabIndexer(credentials hidden)"}
fun normalizeTorznabIndexer(item:TorznabIndexer):TorznabIndexer {
    require(item.name.isNotBlank()&&item.name.length<=60&&item.url.length<=16384&&item.apiKey.length<=4096)
    val raw=item.url.trim().toHttpUrl();require(raw.username.isEmpty()&&raw.password.isEmpty()&&raw.fragment==null&&!raw.encodedPath.endsWith("manifest.json")){"Use the full Torznab API URL."}
    val builder=raw.newBuilder();var key=item.apiKey.trim()
    raw.queryParameterNames.filter{it.equals("apikey",true)}.forEach{if(key.isBlank())key=raw.queryParameter(it).orEmpty();builder.removeAllQueryParameters(it)}
    listOf("t","q","imdbid","tmdbid","tvdbid","season","ep","limit","offset","extended","cat").forEach{builder.removeAllQueryParameters(it)}
    if(raw.encodedPath.trimEnd('/').matches(Regex(".*/api/v1/torznab")))builder.encodedPath(raw.encodedPath.trimEnd('/')+"/api")
    require(key.none{it=='\r'||it=='\n'})
    return item.copy(name=item.name.trim(),url=builder.build().toString(),apiKey=key)
}
// Explicit JSON preserves the existing encrypted format without generic reflection in R8 builds.
object TorznabConfigJson {
    fun decode(raw:String):List<TorznabIndexer> {
        val root=JsonParser.parseString(raw)
        require(root.isJsonArray && root.asJsonArray.size()<=12)
        val result=root.asJsonArray.map { element ->
            val item=element.asJsonObject
            fun text(key:String):String { val value=requireNotNull(item.get(key));require(value.isJsonPrimitive&&value.asJsonPrimitive.isString);return value.asString }
            val enabled=item.get("enabled")?.let{require(it.isJsonPrimitive&&it.asJsonPrimitive.isBoolean);it.asBoolean} ?: true
            val id=text("id");require(id.isNotBlank()&&id.length<=128)
            normalizeTorznabIndexer(TorznabIndexer(id,text("name"),text("url"),item.get("apiKey")?.let{require(it.isJsonPrimitive&&it.asJsonPrimitive.isString);it.asString}.orEmpty(),enabled))
        }
        require(result.map{it.id}.distinct().size==result.size)
        return result
    }
    fun encode(items:List<TorznabIndexer>):String {
        require(items.size<=12&&items.map{it.id}.distinct().size==items.size)
        return JsonArray().apply { items.map(::normalizeTorznabIndexer).forEach { item ->
            require(item.id.isNotBlank()&&item.id.length<=128)
            add(JsonObject().apply { addProperty("id",item.id);addProperty("name",item.name);addProperty("url",item.url);addProperty("apiKey",item.apiKey);addProperty("enabled",item.enabled) })
        }}.toString()
    }
}
class TorznabStore(private val settings:SettingsStore){
    fun read():List<TorznabIndexer> = settings.torznabIndexersJson?.let(TorznabConfigJson::decode).orEmpty()
    fun all():List<TorznabIndexer> = runCatching{read()}.getOrDefault(emptyList())
    fun save(items:List<TorznabIndexer>){
        // Refuse to overwrite unreadable credentials with an apparently empty list.
        read()
        val json=TorznabConfigJson.encode(items)
        val expected=TorznabConfigJson.decode(json)
        settings.saveTorznabIndexersChecked(json)
        check(read()==expected){"Indexer storage verification failed."}
    }
}

data class NabSearch(val available:Boolean=false,val params:Set<String> = emptySet())
data class NabCaps(val movie:NabSearch=NabSearch(),val tv:NabSearch=NabSearch(),val generic:NabSearch=NabSearch(),val limit:Int=100)
data class NabQuery(val type:String,val imdb:String,val title:String?=null,val year:String?=null,val season:Int?=null,val episode:Int?=null,val pack:Boolean=false,val tmdb:String?=null,val tvdb:String?=null)
data class NabItem(val title:String,val guid:String,val magnet:String?,val hash:String?,val torrentUrl:String?,val size:Long?,val seeders:Int=0,val imdb:String?=null)
data class NabPage(val items:List<NabItem>,val total:Int?=null,val offset:Int=0,val nzbItems:Int=0)
class NabFailure(message:String):IOException(message)
private fun parseNabXml(xml:String,handler:DefaultHandler){
    require(xml.length<=8*1024*1024&&!Regex("(?i)<!\\s*(DOCTYPE|ENTITY)").containsMatchIn(xml)){"Invalid indexer XML."}
    val factory=SAXParserFactory.newInstance();factory.isNamespaceAware=true;factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false)
    val reader=factory.newSAXParser().xmlReader;reader.entityResolver=EntityResolver{_,_->throw SAXException("External entities are disabled")};reader.contentHandler=handler;reader.parse(InputSource(StringReader(xml)))
}
fun parseNabCaps(xml:String):NabCaps {
    var valid=false;var depth=0;var caps=NabCaps();var error=false
    parseNabXml(xml,object:DefaultHandler(){override fun startElement(uri:String?,local:String?,q:String?,attrs:Attributes){require(++depth<64);val tag=local ?: q.orEmpty().substringAfter(':');if(tag=="caps")valid=true;if(tag=="error")error=true
        fun search()=NabSearch(attrs.getValue("available").equals("yes",true),attrs.getValue("supportedParams")?.split(',')?.map{it.trim().lowercase()}?.filter{it.isNotEmpty()}?.toSet() ?: setOf("q"))
        caps=when(tag){"movie-search"->caps.copy(movie=search());"tv-search"->caps.copy(tv=search());"search"->caps.copy(generic=search());"limits"->caps.copy(limit=(attrs.getValue("max")?.toIntOrNull() ?: 100).coerceIn(1,200));else->caps}
    };override fun endElement(uri:String?,local:String?,q:String?){depth--}})
    if(error)throw NabFailure("Indexer authentication or API request was rejected.");require(valid){"This URL did not return Torznab capabilities."};return caps
}
fun nabQueryParameters(caps:NabCaps,query:NabQuery):Map<String,String>{
    require(query.imdb.matches(Regex("tt\\d+"))&&query.type in setOf("movie","series"))
    val specialized=if(query.type=="movie")caps.movie else caps.tv;val action=if(query.type=="movie")"movie"else "tvsearch"
    val common=linkedMapOf("extended" to "1","limit" to caps.limit.toString())
    if(specialized.available){
        val ids=listOf("imdbid" to query.imdb.removePrefix("tt"),"tmdbid" to query.tmdb,"tvdbid" to query.tvdb)
        val id=ids.firstOrNull{it.first in specialized.params&&it.second!=null}
        val supportsEpisode=query.type!="series"||((query.season==null||"season" in specialized.params)&&(query.pack||query.episode==null||"ep" in specialized.params))
        if(id!=null&&supportsEpisode){common["t"]=action;common[id.first]=id.second!!}
        else if("q" in specialized.params&&!query.title.isNullOrBlank()){common["t"]=action;common["q"]=query.title+if(query.type=="series"&&(!supportsEpisode))" S${query.season?.toString()?.padStart(2,'0').orEmpty()}${if(query.pack)""else "E${query.episode?.toString()?.padStart(2,'0').orEmpty()}"}"else ""}
        if(common.containsKey("t")){
            if(query.type=="series"){if("season" in specialized.params)query.season?.let{common["season"]=it.toString()};if(!query.pack&&"ep" in specialized.params)query.episode?.let{common["ep"]=it.toString()}}
            if("year" in specialized.params)query.year?.takeIf{it.matches(Regex("\\d{4}"))}?.let{common["year"]=it};return common
        }
    }
    if(caps.generic.available&&"q" in caps.generic.params&&!query.title.isNullOrBlank()){
        common["t"]="search";common["q"]=query.title+if(query.type=="series")" S${query.season?.toString()?.padStart(2,'0').orEmpty()}${if(query.pack)""else "E${query.episode?.toString()?.padStart(2,'0').orEmpty()}"}"else query.year?.takeIf{it.matches(Regex("\\d{4}"))}?.let{" $it"}.orEmpty();return common
    }
    throw NabFailure("Indexer does not support this title's IDs or an appropriate title search.")
}
fun parseNabPage(xml:String):NabPage {
    val items=mutableListOf<NabItem>();var rss=false;var error=false;var total:Int?=null;var offset=0;var depth=0;var nzbItems=0
    parseNabXml(xml,object:DefaultHandler(){var inside=false;var title="";var guid="";var link="";var enclosure="";var mime="";var size:Long?=null;var text=StringBuilder();val attrs=mutableMapOf<String,String>()
        override fun startElement(uri:String?,local:String?,q:String?,a:Attributes){require(++depth<64);val tag=local ?: q.orEmpty().substringAfter(':');text=StringBuilder();if(tag=="rss")rss=true;if(tag=="error")error=true
            if(tag=="response"){total=a.getValue("total")?.toIntOrNull();offset=a.getValue("offset")?.toIntOrNull() ?: 0}
            if(tag=="item"){inside=true;title="";guid="";link="";enclosure="";mime="";size=null;attrs.clear()}
            if(inside&&tag=="enclosure"){enclosure=a.getValue("url").orEmpty();mime=a.getValue("type").orEmpty().lowercase();size=a.getValue("length")?.toLongOrNull()?.takeIf{it>0}}
            if(inside&&tag=="attr"){val key=a.getValue("name")?.lowercase();val value=a.getValue("value");if(key!=null&&value!=null&&attrs.size<80)attrs[key]=value.take(16384)}
        }
        override fun characters(c:CharArray,start:Int,length:Int){require(text.length+length<=32768);text.append(c,start,length)}
        override fun endElement(uri:String?,local:String?,q:String?){val tag=local ?: q.orEmpty().substringAfter(':');val value=text.toString().trim();if(inside)when(tag){"title"->title=value.take(1000);"guid"->guid=value;"link"->link=value;"size"->size=value.toLongOrNull()?.takeIf{it>0}}
            if(tag=="item"){inside=false;val magnet=listOfNotNull(attrs["magneturl"],link,enclosure).firstOrNull{it.startsWith("magnet:?",true)&&runCatching{parseMagnetInput(it)}.isSuccess}
                val hash=attrs["infohash"]?.takeIf{it.matches(Regex("[a-fA-F0-9]{40}"))}?.lowercase() ?: magnet?.let{runCatching{parseMagnetInput(it).identity.takeIf{h->h.matches(Regex("[a-f0-9]{40}"))}}.getOrNull()}
                val download=listOf(enclosure,link).firstOrNull{it.startsWith("http://",true)||it.startsWith("https://",true)}?.takeIf{mime in setOf("application/x-bittorrent","application/octet-stream")||runCatching{it.toHttpUrl().encodedPath.endsWith(".torrent",true)}.getOrDefault(false)}
                if(mime in setOf("application/x-nzb","application/nzb"))nzbItems++
                if(title.isNotBlank()&&(hash!=null||magnet!=null||download!=null)&&items.size<1500)items+=NabItem(title,guid.ifBlank{hash ?: download ?: magnet.orEmpty()},magnet,hash,download,attrs["size"]?.toLongOrNull()?.takeIf{it>0} ?: size,attrs["seeders"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,attrs["imdb"] ?: attrs["imdbid"])
            };depth--;text=StringBuilder()
        }
    })
    if(error)throw NabFailure("Indexer authentication or search was rejected.");require(rss){"Indexer did not return a Torznab RSS feed."};return NabPage(items,total,offset,nzbItems)
}
fun nabRelease(item:NabItem,indexer:TorznabIndexer):SourceRelease {
    val raw=JsonObject().apply{addProperty("title",item.title);addProperty("name",indexer.name);item.hash?.let{addProperty("infoHash",it)};addProperty("url",item.magnet ?: item.torrentUrl);add("behaviorHints",JsonObject().apply{item.size?.let{addProperty("videoSize",it)}})}
    val parsed=parseSourceRelease(raw,allowTorrentLink=true) ?: throw NabFailure("Unsupported torrent result.")
    return parsed.copy(streamUrl=null,name=item.title,indexer=indexer.name,addonName=indexer.name,sizeBytes=item.size,torrentSizeBytes=item.size,seeders=item.seeders,torznabIndexerId=indexer.id,torrentFileUrl=item.torrentUrl,magnetUrl=item.magnet,sourceToken=credentialScope(indexer.id+":"+item.guid))
}
sealed interface NabMaterial {data class Torrent(val upload:TorrentUpload):NabMaterial;data class Magnet(val input:MagnetInput):NabMaterial}
class TorznabClient(private val client:OkHttpClient=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(15,TimeUnit.SECONDS).readTimeout(55,TimeUnit.SECONDS).callTimeout(60,TimeUnit.SECONDS).build()){
    suspend fun xml(item:TorznabIndexer,params:Map<String,String>):String=withContext(Dispatchers.IO){
        val normalized=normalizeTorznabIndexer(item);val builder=normalized.url.toHttpUrl().newBuilder();params.forEach{(k,v)->builder.setQueryParameter(k,v)};if(normalized.apiKey.isNotBlank())builder.setQueryParameter("apikey",normalized.apiKey)
        client.newCall(Request.Builder().url(builder.build()).build()).execute().use{response->if(!response.isSuccessful)throw NabFailure(if(response.code in listOf(401,403))"Indexer authentication denied."else "Indexer request failed (${response.code}).");val body=response.body ?: throw NabFailure("Empty indexer response.");val input=body.source();input.request(8*1024*1024+1L);require(input.buffer.size<=8*1024*1024);input.buffer.readUtf8()}
    }
    suspend fun test(item:TorznabIndexer)=parseNabCaps(xml(item,mapOf("t" to "caps")))
    suspend fun search(item:TorznabIndexer,query:NabQuery,onPage:suspend(List<SourceRelease>)->Unit={}):List<SourceRelease>{
        val caps=test(item);val params=nabQueryParameters(caps,query);val output=mutableListOf<SourceRelease>();val seen=mutableSetOf<String>();var offset=0
        repeat(3){
            val page=parseNabPage(xml(item,params+mapOf("offset" to offset.toString())))
            if(page.items.isEmpty()&&page.nzbItems>0)throw NabFailure("This feed returns Usenet/NZB results. Use the torrent Torznab endpoint.")
            val fresh=page.items.filter{seen.add(it.guid)}
            val releases=fresh.filter{it.imdb==null||it.imdb.removePrefix("tt")==query.imdb.removePrefix("tt")}.mapNotNull{runCatching{nabRelease(it,item)}.getOrNull()};output+=releases;onPage(releases)
            offset+=page.items.size
            if(fresh.isEmpty()||page.total==null||offset>=page.total||page.items.size<caps.limit)return output
        };return output
    }
    suspend fun torrent(item:TorznabIndexer,source:SourceRelease):NabMaterial=withContext(Dispatchers.IO){
        var url=source.torrentFileUrl?.toHttpUrl() ?: throw NabFailure("No torrent metadata link was returned.")
        val normalized=normalizeTorznabIndexer(item);val endpoint=normalized.url.toHttpUrl()
        if(url.scheme==endpoint.scheme&&url.host==endpoint.host&&url.port==endpoint.port&&url.queryParameterNames.none{it.equals("apikey",true)}&&normalized.apiKey.isNotBlank())url=url.newBuilder().addQueryParameter("apikey",normalized.apiKey).build()
        repeat(4){
            require(url.username.isEmpty()&&url.password.isEmpty())
            client.newBuilder().callTimeout(20,TimeUnit.SECONDS).retryOnConnectionFailure(false).build().newCall(Request.Builder().url(url).header("Connection","close").build()).execute().use{response->
                if(response.code in 300..399){val location=response.header("Location") ?: throw NabFailure("Indexer redirect was missing a target.");if(location.startsWith("magnet:?",true))return@withContext NabMaterial.Magnet(parseMagnetInput(location));val next=url.resolve(location) ?: throw NabFailure("Invalid torrent redirect.");require(!(url.isHttps&&!next.isHttps));url=next}
                else{if(!response.isSuccessful)throw NabFailure("Torrent metadata request failed (${response.code}).");val body=response.body ?: throw NabFailure("Empty torrent metadata.");val mime=body.contentType()?.let{"${it.type}/${it.subtype}"};require(mime in setOf("application/x-bittorrent","application/octet-stream")){"The indexer did not return torrent metadata."};require(body.contentLength()<=MAX_TORRENT_BYTES);val upload=readTorrentUpload("release.torrent",body.byteStream());return@withContext NabMaterial.Torrent(upload)}
            }
        };throw NabFailure("Too many torrent metadata redirects.")
    }
}
val SourceRelease.sourceIdentity:String get()=infoHash ?: magnetUrl?.let{runCatching{parseMagnetInput(it).identity}.getOrNull()} ?: "torznab:${torznabIndexerId.orEmpty()}:${sourceToken ?: credentialScope(torrentFileUrl.orEmpty()+name)}"
val SourceRelease.canImport:Boolean get()=infoHash!=null||magnetUrl?.let{runCatching{parseMagnetInput(it)}.isSuccess}==true||(!torznabIndexerId.isNullOrBlank()&&!torrentFileUrl.isNullOrBlank())
