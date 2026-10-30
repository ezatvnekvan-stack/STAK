package app.stak.nativeapp.data

import kotlinx.coroutines.*

data class JellyfinCollection(val id:String,val name:String,val kind:String,val count:Int?=null)
data class JellyfinCatalog(val libraries:List<JellyfinCollection>,val collections:List<JellyfinCollection>,val issues:List<String> = emptyList())

/** Provider IDs and server presentation keys are the only grouping evidence. */
fun groupJellyfinVersions(items:List<MediaItem>):List<MediaItem> {
    val parents=IntArray(items.size){it};val aliases=mutableMapOf<String,Int>()
    fun root(index:Int):Int {var i=index;while(parents[i]!=i){parents[i]=parents[parents[i]];i=parents[i]};return i}
    items.forEachIndexed{index,item->
        val ids=(item.jellyfinProviderKeys.orEmpty()+listOfNotNull(item.imdbId?.takeIf{it.matches(Regex("tt\\d+"))}?.let{"imdb:$it"},item.jellyfinPresentationKey?.takeIf{it.isNotBlank()}?.let{"presentation:$it"})+"item:${item.jellyfinId ?: item.id}").map{"${item.type}:$it"}
        ids.forEach{id->val previous=aliases[id];if(previous!=null)parents[root(index)]=root(previous) else aliases[id]=index}
    }
    return items.indices.groupBy{root(it)}.values.map{indices->
        val group=indices.map{items[it]}
        val representative=group.filter{!it.watched&&it.positionMs>0}.maxByOrNull{it.lastPlayedAt} ?: group.firstOrNull{it.watched} ?: group.first()
        representative.copy(jellyfinVersionIds=group.mapNotNull{it.jellyfinId}.distinct(),jellyfinProviderKeys=group.flatMap{it.jellyfinProviderKeys.orEmpty()}.distinct(),addedAt=group.maxOf{it.addedAt})
    }
}
object JellyfinCollections {
    suspend fun catalog():JellyfinCatalog=coroutineScope {
        val scope=jellyfinScope();val user=NativeRuntime.settings.jellyfinUserId.orEmpty()
        val libraries=async {
            try {
                val data=DirectJellyfin.sessionRequest("/Users/$user/Views",expectedScope=scope)
                require(data.get("Items")?.isJsonArray==true)
                data.objects("Items").filter{it.text("CollectionType") in listOf(null,"movies","tvshows","boxsets","mixed")}.map{JellyfinCollection(it.text("Id") ?: error("Missing library ID"),it.text("Name") ?: "Library","library")}
            }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){null}
        }
        val sets=async {
            try {loadLibraryPages(identity={it.id},fetch={offset,limit->
                val data=DirectJellyfin.sessionRequest("/Users/$user/Items",mapOf("IncludeItemTypes" to "BoxSet","Recursive" to "true","SortBy" to "SortName","Fields" to "ChildCount","EnableTotalRecordCount" to "true","StartIndex" to offset.toString(),"Limit" to limit.toString()),expectedScope=scope)
                require(data.get("Items")?.isJsonArray==true)
                LibraryPage(data.objects("Items").map{JellyfinCollection(it.text("Id") ?: error("Missing collection ID"),it.text("Name") ?: "Collection","collection",it.number("ChildCount")?.toInt())},data.number("TotalRecordCount")?.toInt())
            })}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){null}
        }
        val views=libraries.await();val collections=sets.await()
        JellyfinCatalog(views.orEmpty(),collections.orEmpty(),listOfNotNull(if(views==null)"Libraries could not load." else null,if(collections==null)"Collections could not load." else null))
    }
}
