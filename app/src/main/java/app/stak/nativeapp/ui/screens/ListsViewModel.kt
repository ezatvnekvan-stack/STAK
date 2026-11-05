package app.stak.nativeapp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stak.nativeapp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class ListsState(
    val category:String="popular",val query:String="",val lists:List<PublicTraktList> = emptyList(),
    val saved:List<PublicTraktList> = emptyList(),val selected:PublicTraktList?=null,
    val titles:List<MediaItem> = emptyList(),val loading:Boolean=false,val error:String?=null,
    val page:Int=1,val pages:Int=1
)

class ListsViewModel(
    private val api:TraktLists=TraktLists(),
    private val readSaved:()->String?={NativeRuntime.settings.savedPublicLists},
    private val writeSaved:(String)->Unit={NativeRuntime.settings.savedPublicLists=it}
):ViewModel(){
    private val _state=MutableStateFlow(ListsState(saved=decodeSavedLists(readSaved())))
    val state:StateFlow<ListsState> = _state
    private var job:Job?=null
    private var generation=0
    private val artworkPermits=Semaphore(3)
    private val _previews=MutableStateFlow<Map<Long,List<MediaItem>>>(emptyMap())
    val previews:StateFlow<Map<Long,List<MediaItem>>> = _previews
    private val listCache=linkedMapOf<String,Pair<Long,PublicListPage>>()
    private val titleCache=linkedMapOf<String,Pair<Long,PublicListTitles>>()
    init{reload()}
    fun category(value:String){if(value==_state.value.category)return;_state.value=_state.value.copy(category=value);reload()}
    fun query(value:String){_state.value=_state.value.copy(query=value.take(160));reload(debounce=true)}
    fun open(list:PublicTraktList){_state.value=_state.value.copy(selected=list,titles=emptyList());reload()}
    fun back(){_state.value=_state.value.copy(selected=null,titles=emptyList());reload()}
    fun toggleSaved(list:PublicTraktList){
        val current=_state.value
        val saved=if(current.saved.any{it.id==list.id})current.saved.filterNot{it.id==list.id}else(listOf(list)+current.saved).take(200)
        writeSaved(encodeSavedLists(saved));_state.value=current.copy(saved=saved)
        if(current.category=="saved"&&current.selected==null)reload()
    }
    fun reload(more:Boolean=false,debounce:Boolean=false){
        if(more&&(_state.value.loading||_state.value.page>=_state.value.pages))return
        job?.cancel();val token=++generation
        val start=_state.value;val page=if(more)start.page+1 else 1
        _state.value=start.copy(loading=true,error=null,lists=if(more)start.lists else emptyList(),titles=if(more)start.titles else emptyList())
        job=viewModelScope.launch{
            try{
                if(debounce)delay(300)
                if(start.selected==null){
                    val result=if(start.category=="saved")PublicListPage(start.saved.filter{it.name.contains(start.query,true)||it.owner.contains(start.query,true)},1,1)else{
                        val key="${start.category}:${start.query}:$page"
                        listCache[key]?.takeIf{System.currentTimeMillis()-it.first<300_000}?.second?:api.browse(start.category,start.query,page).also{
                            listCache[key]=System.currentTimeMillis() to it
                            while(listCache.size>24)listCache.remove(listCache.keys.first())
                        }
                    }
                    if(token==generation)_state.value=_state.value.copy(lists=((if(more)start.lists else emptyList())+result.lists).distinctBy{it.id},page=page,pages=result.pages)
                }else{
                    val id=start.selected.id;val key="$id:$page"
                    val result=titleCache[key]?.takeIf{System.currentTimeMillis()-it.first<300_000}?.second?:api.titles(id,page).also{
                        titleCache[key]=System.currentTimeMillis() to it
                        while(titleCache.size>32)titleCache.remove(titleCache.keys.first())
                    }
                    if(token==generation)_state.value=_state.value.copy(titles=((if(more)start.titles else emptyList())+result.items).distinctBy{"${it.type}:${it.mediaKey}"},page=page,pages=result.pages)
                    // Publish text immediately. A slow image must not hold the page back.
                }
            }catch(c:CancellationException){throw c}
            catch(_:Exception){if(token==generation)_state.value=_state.value.copy(error=if(runCatching{NativeRuntime.settings.traktClientId.isNullOrBlank()}.getOrDefault(false))"Add your Trakt Client ID in Settings → Metadata & ratings."else "Lists could not load. Check your Trakt settings and connection, then retry.")}
            finally{if(token==generation)_state.value=_state.value.copy(loading=false)}
        }
    }
    suspend fun preview(list:PublicTraktList){
        if(_previews.value.containsKey(list.id))return
        try{
            val titles=artworkPermits.withPermit{api.titles(list.id).items.take(3)}
            val images=coroutineScope{titles.map{item->async{artworkPermits.withPermit{try{api.image(item)}catch(c:CancellationException){throw c}catch(_:Exception){item}}}}.awaitAll()}
            _previews.value=(_previews.value+ (list.id to images)).entries.toList().takeLast(40).associate{it.key to it.value}
        }catch(c:CancellationException){throw c}catch(_:Exception){/* Card text remains usable; opening retries normally. */}
    }
    suspend fun artwork(item:MediaItem):MediaItem=artworkPermits.withPermit{
        try{api.image(item)}catch(c:CancellationException){throw c}catch(_:Exception){item}
    }
}
