package app.stak.nativeapp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stak.nativeapp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class LiveSearchViewModel(private val loader:suspend(String)->List<MediaItem> = {q->MediaRepository().discover("all","popular",q,1).items}):ViewModel(){
    private val _query=MutableStateFlow("");val query:StateFlow<String> = _query
    private val _items=MutableStateFlow<List<MediaItem>>(emptyList());val items:StateFlow<List<MediaItem>> = _items
    private val _loading=MutableStateFlow(false);val loading:StateFlow<Boolean> = _loading
    private val _error=MutableStateFlow<String?>(null);val error:StateFlow<String?> = _error
    private var known=emptyList<MediaItem>();private var remote=emptyList<MediaItem>();private var job:Job?=null;private var generation=0
    private val cache=linkedMapOf<String,List<MediaItem>>()
    fun knownTitles(items:List<MediaItem>){known=items;publish()}
    private fun publish(){
        val local=rankedSearch(known,_query.value);val merged=mutableListOf<MediaItem>()
        for(item in local+rankedSearch(remote,_query.value,true)){
            val keys=jellyfinIdentityKeys(item)
            if(merged.none{it.sameIdentity(item)||jellyfinIdentityKeys(it).any{key->key in keys}})merged+=item
        }
        _items.value=rankedSearch(merged,_query.value,true).take(60)
    }
    fun setQuery(value:String,immediate:Boolean=false){
        _query.value=value.take(160);val token=++generation;job?.cancel();remote=emptyList();_error.value=null;publish()
        val q=providerSearchQuery(_query.value)
        if(normalizedSearch(q).length<2){_loading.value=false;return}
        val key=normalizedSearch(q)
        cache[key]?.let{remote=it;_loading.value=false;publish();return}
        _loading.value=true
        job=viewModelScope.launch{
            try{
                if(!immediate)delay(250)
                val result=loader(q)
                if(token==generation){remote=result;cache[key]=result;if(cache.size>32)cache.remove(cache.keys.first());publish()}
            }catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){if(token==generation)_error.value="Search could not connect. Showing matching saved titles."}
            finally{if(token==generation)_loading.value=false}
        }
    }
    fun retry(){cache.remove(normalizedSearch(providerSearchQuery(_query.value)));setQuery(_query.value,true)}
}
