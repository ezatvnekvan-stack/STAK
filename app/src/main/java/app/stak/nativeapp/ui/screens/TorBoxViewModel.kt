package app.stak.nativeapp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stak.nativeapp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TorBoxViewModel(
    private val loader:suspend(String,suspend(List<TorBoxTorrent>,Int?)->Unit)->List<TorBoxTorrent> = {key,page->DirectServices.torrents(key,page)},
    private val artwork:TorrentMetadataResolver=TorrentMetadataResolver(),
    private val match:suspend(TorBoxTorrent)->MediaItem?={torrent->artwork.resolveTorrent(torrent)},
    private val mutations:TorBoxMutations=DirectTorBoxMutations(),
    private val keyProvider:()->String={NativeRuntime.settings.torboxKey.orEmpty()}
):ViewModel() {
    private val _selected=MutableStateFlow<Set<String>>(emptySet());val selected:StateFlow<Set<String>> = _selected
    private val _actionBusy=MutableStateFlow(false);val actionBusy:StateFlow<Boolean> = _actionBusy
    private val _actionMessage=MutableStateFlow<String?>(null);val actionMessage:StateFlow<String?> = _actionMessage
    private val _addResults=MutableStateFlow<List<TorrentAddResult>>(emptyList());val addResults:StateFlow<List<TorrentAddResult>> = _addResults
    private var mutationJob:Job?=null;private var operationGeneration=0;private var deleted=emptySet<String>()
    fun select(id:String){if(!_actionBusy.value&&_rows.value.any{it.torrent.id==id})_selected.value=if(id in _selected.value)_selected.value-id else _selected.value+id}
    fun selectVisible(ids:Set<String>){if(!_actionBusy.value){val known=ids.intersect(_rows.value.map{it.torrent.id}.toSet());_selected.value=if(known.all{it in _selected.value})_selected.value-known else _selected.value+known}}
    fun clearSelection(){if(!_actionBusy.value)_selected.value=emptySet()}
    fun clearAddResults(){if(!_actionBusy.value)_addResults.value=emptyList()}
    private fun accountIsCurrent(key:String)=key.isNotBlank()&&key==account&&key==keyProvider()
    fun control(key:String,ids:List<String>,operation:TorrentOperation) {
        if(_actionBusy.value||!accountIsCurrent(key))return
        val targets=ids.distinct().filter{id->_rows.value.any{it.torrent.id==id}}
        if(targets.isEmpty())return
        val token=++operationGeneration;_actionBusy.value=true;_actionMessage.value=null
        mutationJob=viewModelScope.launch {
            val results=mutableListOf<TorrentMutationResult>()
            try {
                for(id in targets) {
                    ensureActive();if(!accountIsCurrent(key)||token!=operationGeneration)break
                    try{mutations.control(key,id,operation);results+=TorrentMutationResult(id,true)}
                    catch(cancelled:CancellationException){throw cancelled}
                    catch(error:Exception){results+=TorrentMutationResult(id,false,if(error is UnconfirmedTorrentMutation)error.message else "TorBox rejected this operation. Check status and retry.",error is UnconfirmedTorrentMutation)}
                    if(results.lastOrNull()?.uncertain==true)break
                }
                if(token==operationGeneration&&accountIsCurrent(key)) {
                    val succeeded=results.filter{it.success}.map{it.id}.toSet()
                    _selected.value=(targets.toSet()-succeeded)
                    if(operation==TorrentOperation.Delete){deleted=deleted+succeeded;_rows.value=_rows.value.filter{it.torrent.id !in succeeded}}
                    _actionMessage.value="${succeeded.size}/${targets.size} ${operation.label.lowercase()} operations confirmed."+(if(results.any{it.uncertain})" Some outcomes are unknown. Refresh the library before retrying." else if(results.any{!it.success})" Failed items remain selected." else "")
                    if(succeeded.isNotEmpty())refresh(key)
                }
            } finally{if(token==operationGeneration)_actionBusy.value=false}
        }
    }
    fun add(key:String,text:String,file:TorrentUpload?) {
        if(_actionBusy.value||!accountIsCurrent(key))return
        val inputs=try{parseMagnetBatch(text)}catch(error:IllegalArgumentException){_actionMessage.value=error.message;return}
        try{file?.let{validateTorrentUpload(it)}}catch(error:IllegalArgumentException){_actionMessage.value=error.message;return}
        if(inputs.isEmpty()&&file==null)return
        val upload=file?.copy(bytes=file.bytes.copyOf());val token=++operationGeneration
        _actionBusy.value=true;_actionMessage.value=null;_addResults.value=emptyList()
        mutationJob=viewModelScope.launch {
            val results=mutableListOf<TorrentAddResult>()
            suspend fun submit(label:String,block:suspend()->String):Boolean {
                ensureActive();if(!accountIsCurrent(key)||token!=operationGeneration)return false
                try{results+=TorrentAddResult(label,true,block())}
                catch(cancelled:CancellationException){throw cancelled}
                catch(error:Exception){results+=TorrentAddResult(label,false,message=if(error is UnconfirmedTorrentMutation)error.message else "TorBox did not accept this torrent. Check account limits and torrent validity.",uncertain=error is UnconfirmedTorrentMutation)}
                return results.lastOrNull()?.uncertain!=true
            }
            try {
                var proceed=if(upload!=null)submit("file:${upload.name}"){mutations.upload(key,upload)} else true
                for(input in inputs) {
                    if(proceed)proceed=submit(input.original){mutations.addMagnet(key,input)}
                    else results+=TorrentAddResult(input.original,false,message="Not sent. Refresh the library before retrying.")
                }
                if(token==operationGeneration&&accountIsCurrent(key)) {
                    _addResults.value=results
                    _actionMessage.value="${results.count{it.success}}/${results.size} torrents added."+(if(results.any{it.uncertain})" Some outcomes are unknown. Refresh the library before retrying." else if(results.any{!it.success})" Failed entries were kept in the form." else "")
                    if(results.any{it.success})refresh(key)
                }
            }finally{if(token==operationGeneration)_actionBusy.value=false}
        }
    }
    private val _filters=MutableStateFlow(app.stak.nativeapp.data.LibraryFilters(sort="newest"))
    val filters:StateFlow<app.stak.nativeapp.data.LibraryFilters> = _filters
    fun setFilters(value:app.stak.nativeapp.data.LibraryFilters){_filters.value=value}
    private val _rows=MutableStateFlow<List<TorBoxLibraryEntry>>(emptyList());val rows:StateFlow<List<TorBoxLibraryEntry>> = _rows
    private val _loading=MutableStateFlow(false);val loading:StateFlow<Boolean> = _loading
    private val _matching=MutableStateFlow(false);val matching:StateFlow<Boolean> = _matching
    private val _error=MutableStateFlow<String?>(null);val error:StateFlow<String?> = _error
    private var account:String?=null;private var generation=0;private var job:Job?=null
    fun refresh(key:String) {
        val token=++generation;job?.cancel()
        if(account!=key){mutationJob?.cancel();operationGeneration++;_actionBusy.value=false;_selected.value=emptySet();_actionMessage.value=null;_addResults.value=emptyList();deleted=emptySet();_filters.value=app.stak.nativeapp.data.LibraryFilters(sort="newest");_rows.value=emptyList();account=key}
        _error.value=null;_matching.value=false;_loading.value=false
        if(key.isBlank())return
        val known=_rows.value.associate{it.torrent.id to it.metadata}
        job=viewModelScope.launch {
            _loading.value=true
            try {
                val loaded=loader(key){partial,_->withContext(Dispatchers.Main.immediate){if(token==generation)_rows.value=partial.filter{it.id !in deleted}.map{TorBoxLibraryEntry(it,known[it.id])}}}
                if(token==generation)_rows.value=loaded.filter{it.id !in deleted}.map{TorBoxLibraryEntry(it,known[it.id])}
            } catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){if(token==generation)_error.value="Could not finish loading TorBox. Loaded downloads are still available. Check the connection and retry."}
            finally{if(token==generation)_loading.value=false}
            if(token!=generation)return@launch
            _matching.value=true
            try {
                val pending=_rows.value.filter{it.metadata?.poster==null}
                coroutineScope {
                    // Three workers bound both network requests and coroutine count.
                    (0 until minOf(3,pending.size)).map{worker->launch {
                        var index=worker
                        while(index<pending.size) {
                            ensureActive();val entry=pending[index]
                            val metadata=try{match(entry.torrent)}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){null}
                            if(token==generation&&metadata!=null)_rows.value=_rows.value.map{if(it.torrent.id==entry.torrent.id)it.copy(metadata=metadata) else it}
                            index+=3
                        }
                    }}.joinAll()
                }
            } finally {if(token==generation)_matching.value=false}
        }
    }
}
