package app.stak.nativeapp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stak.nativeapp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class JellyfinBrowserViewModel(
    private val catalogLoader:suspend()->JellyfinCatalog={JellyfinCollections.catalog()},
    private val itemLoader:suspend(String,suspend(List<MediaItem>,Int?)->Unit)->List<MediaItem> = {id,page->DirectJellyfin.library(NativeRuntime.settings,parentId=id,onPage=page)}
):ViewModel() {
    private val _selected=MutableStateFlow<JellyfinCollection?>(null);val selected:StateFlow<JellyfinCollection?> = _selected
    private val _catalog=MutableStateFlow<JellyfinCatalog?>(null);val catalog:StateFlow<JellyfinCatalog?> = _catalog
    private val _catalogBusy=MutableStateFlow(false);val catalogBusy:StateFlow<Boolean> = _catalogBusy
    private val _items=MutableStateFlow<List<MediaItem>>(emptyList());val items:StateFlow<List<MediaItem>> = _items
    private val _busy=MutableStateFlow(false);val busy:StateFlow<Boolean> = _busy
    private val _error=MutableStateFlow<String?>(null);val error:StateFlow<String?> = _error
    private var scope:String?=null;private var generation=0;private var catalogGeneration=0;private var itemJob:Job?=null;private var catalogJob:Job?=null
    fun account(value:String){if(value!=scope){scope=value;generation++;catalogGeneration++;itemJob?.cancel();catalogJob?.cancel();_selected.value=null;_items.value=emptyList();_catalog.value=null;_error.value=null;_busy.value=false;_catalogBusy.value=false}}
    fun loadCatalog(){
        if(_catalogBusy.value)return
        val token=++catalogGeneration;_catalogBusy.value=true
        catalogJob=viewModelScope.launch {try{val data=catalogLoader();if(token==catalogGeneration)_catalog.value=data}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){if(token==catalogGeneration)_catalog.value=JellyfinCatalog(emptyList(),emptyList(),listOf("Library catalog could not load. Retry."))}finally{if(token==catalogGeneration)_catalogBusy.value=false}}
    }
    fun select(value:JellyfinCollection?) {
        val changed=value?.id!=_selected.value?.id;val token=++generation;itemJob?.cancel();_selected.value=value;_error.value=null
        if(changed)_items.value=emptyList()
        if(value==null){_busy.value=false;return}
        itemJob=viewModelScope.launch {
            _busy.value=true
            try{val data=itemLoader(value.id){partial,_->withContext(Dispatchers.Main.immediate){if(token==generation)_items.value=partial}};if(token==generation)_items.value=data}
            catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){if(token==generation)_error.value="Could not finish loading this collection. Loaded titles are still available. Retry."}
            finally{if(token==generation)_busy.value=false}
        }
    }
}
