package app.stak.nativeapp.data

import com.google.gson.*
import kotlinx.coroutines.*
import java.io.IOException

data class PublicTraktList(val id:Long,val name:String,val owner:String,val count:Int=0,val description:String?=null)
data class PublicListPage(val lists:List<PublicTraktList>,val page:Int,val pages:Int)
data class PublicListTitles(val items:List<MediaItem>,val page:Int,val pages:Int)

internal fun parsePublicTraktLists(rows:JsonArray):List<PublicTraktList> = rows.mapNotNull { raw ->
    runCatching {
        val wrapper=raw.asJsonObject
        val list=wrapper.objectOrNull("list")?:return@runCatching null
        if(list.text("privacy")?.let{it!="public"}==true)return@runCatching null
        val id=list.objectOrNull("ids")?.text("trakt")?.toLongOrNull()?.takeIf{it>0}?:return@runCatching null
        val name=list.text("name")?.takeIf{it.isNotBlank()}?:return@runCatching null
        PublicTraktList(id,name.take(200),(wrapper.objectOrNull("user")?:list.objectOrNull("user"))?.text("name")
            ?: (wrapper.objectOrNull("user")?:list.objectOrNull("user"))?.text("username")?:"Trakt community",
            list.number("item_count")?.toInt()?.coerceAtLeast(0)?:0,list.text("description")?.take(1500))
    }.getOrNull()
}.distinctBy{it.id}

internal fun parsePublicListTitles(rows:JsonArray):List<MediaItem> = rows.mapNotNull { row ->
    val array=JsonArray().apply{add(row)}
    parseTraktItems(array,"movie").firstOrNull()?:parseTraktItems(array,"series").firstOrNull()
}.distinctBy{"${it.type}:${it.mediaKey}"}

/** Only public GET endpoints; does not access user tracking or enable acquisition. */
class TraktLists(
    private val key:()->String={NativeRuntime.settings.traktClientId.orEmpty()},
    private val request:(String,Map<String,String>,String)->TraktPage=::traktCatalogRequest,
    private val artwork:suspend(MediaItem)->MediaItem={TraktArtwork.load(it)}
) {
    private fun secret():String=key().trim().also {
        if(it.isEmpty())throw IOException("Add your Trakt Client ID in Settings → Metadata & ratings to browse lists.")
        require(it.length<=256&&it.none(Char::isWhitespace)){"Invalid Trakt Client ID."}
    }
    suspend fun browse(category:String,query:String="",page:Int=1):PublicListPage=withContext(Dispatchers.IO){
        require(category in setOf("popular","trending")&&page in 1..1000&&query.length<=160)
        val path=if(query.isBlank())"/lists/$category" else "/search/list"
        val result=request(path,mapOf("page" to "$page","limit" to "20")+if(query.isBlank())emptyMap()else mapOf("query" to query.trim()),secret())
        PublicListPage(parsePublicTraktLists(result.entries),page,result.pages.coerceAtLeast(page))
    }
    suspend fun titles(id:Long,page:Int=1):PublicListTitles=withContext(Dispatchers.IO){
        require(id>0&&page in 1..1000)
        val result=request("/lists/$id/items",mapOf("page" to "$page","limit" to "30","extended" to "full","sort_by" to "rank","sort_how" to "asc"),secret())
        PublicListTitles(parsePublicListTitles(result.entries),page,result.pages.coerceAtLeast(page))
    }
    suspend fun image(item:MediaItem):MediaItem=artwork(item)
}

internal fun decodeSavedLists(json:String?):List<PublicTraktList> = try {
    val rows=JsonParser.parseString(json?:"[]").asJsonArray
    rows.take(200).mapNotNull{raw->runCatching{
        val row=raw.asJsonObject
        PublicTraktList(row.text("id")!!.toLong().also{require(it>0)},row.text("name")!!.take(200),row.text("owner")?:"Trakt community",row.number("count")?.toInt()?.coerceAtLeast(0)?:0,row.text("description")?.take(1500))
    }.getOrNull()}.distinctBy{it.id}
}catch(_:Exception){emptyList()}
internal fun encodeSavedLists(lists:List<PublicTraktList>):String=Gson().toJson(lists.distinctBy{it.id}.take(200))
