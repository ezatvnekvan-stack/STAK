package app.stak.nativeapp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stak.nativeapp.data.*
import app.stak.nativeapp.data.db.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class WatchlistViewModel(
    dao:WatchlistDao,
    private val repository:WatchlistRepository=WatchlistRepository(dao),
    private val cacheLoader:suspend(MediaItem,String)->Boolean={item,key->
        val imdb=DirectCatalog().imdbIdentity(item) ?: item.mediaKey.takeIf{it.matches(Regex("tt\\d+"))} ?: throw IllegalArgumentException("IMDb identity unavailable")
        val sources=DirectServices.sources(item.type,imdb,if(item.isSeries)1 else null,if(item.isSeries)1 else null)
        SourceRepository.cached(sources.mapNotNull{it.infoHash},key).isNotEmpty()
    },
    private val ratingLoader:suspend(MediaItem)->MediaItem={item->
        val imdb=DirectCatalog().imdbIdentity(item) ?: item.mediaKey.takeIf{it.matches(Regex("tt\\d+"))}
        if(imdb==null)item else item.copy(imdbId=imdb,rating=DirectRatings().get(imdb).imdb)
    }
):ViewModel() {
    val items:StateFlow<List<MediaItem>> = repository.observe().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _prefs=MutableStateFlow(runCatching{Gson().fromJson(NativeRuntime.settings.watchlistPreferences,WatchlistPreferences::class.java)}.getOrNull() ?: WatchlistPreferences())
    val preferences:StateFlow<WatchlistPreferences> = _prefs
    fun preferences(value:WatchlistPreferences){_prefs.value=value;NativeRuntime.settings.watchlistPreferences=Gson().toJson(value)}
    private val _busy=MutableStateFlow(false);val busy:StateFlow<Boolean> = _busy
    private val _error=MutableStateFlow<String?>(null);val error:StateFlow<String?> = _error
    private val _cached=MutableStateFlow<Set<String>>(emptySet());val cached:StateFlow<Set<String>> = _cached
    private val _checking=MutableStateFlow(false);val checking:StateFlow<Boolean> = _checking
    private val _cacheFailures=MutableStateFlow(0);val cacheFailures:StateFlow<Int> = _cacheFailures
    private val _ratingsLoading=MutableStateFlow(false);val ratingsLoading:StateFlow<Boolean> = _ratingsLoading
    private var cacheJob:Job?=null;private var cacheGeneration=0;private var ratingJob:Job?=null
    init{refresh()}
    fun refresh(){viewModelScope.launch{try{repository.refreshFromBackend();_error.value=null}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){_error.value="Could not read the saved watchlist. Retry."}}}
    fun seed(items:List<MediaItem>){viewModelScope.launch{repository.seed(items)}}
    fun toggle(item:MediaItem){if(_busy.value)return;_busy.value=true;viewModelScope.launch{try{repository.toggle(item);_error.value=null}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){_error.value="Could not update the watchlist. Retry."}finally{_busy.value=false}}}
    fun remove(item:MediaItem){if(_busy.value)return;_busy.value=true;viewModelScope.launch{try{repository.remove(item);_error.value=null}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){_error.value="Could not remove this title. Retry."}finally{_busy.value=false}}}
    fun checkCache(titles:List<MediaItem>,key:String,enabled:Boolean) {
        val token=++cacheGeneration;cacheJob?.cancel();_cached.value=emptySet();_cacheFailures.value=0;_checking.value=false
        if(!enabled||key.isBlank())return
        cacheJob=viewModelScope.launch {
            _checking.value=true;val permits=Semaphore(3)
            try {titles.map{item->async{permits.withPermit{try{if(cacheLoader(item,key)&&token==cacheGeneration)_cached.value=_cached.value+"${item.type}:${item.mediaKey}"}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){if(token==cacheGeneration)_cacheFailures.value++}}}}.awaitAll()}
            finally{if(token==cacheGeneration)_checking.value=false}
        }
    }
    fun loadRatings(titles:List<MediaItem>) {
        if(ratingJob?.isActive==true||NativeRuntime.settings.omdbKey.isNullOrBlank()||!NativeRuntime.settings.ratingsEnabled)return
        ratingJob=viewModelScope.launch {
            _ratingsLoading.value=true;var failed=false;val permits=Semaphore(3)
            try{titles.filter{it.rating==null}.map{item->async{permits.withPermit{try{repository.updateMetadata(ratingLoader(item))}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){failed=true}}}}.awaitAll();if(failed)_error.value="Some IMDb ratings could not load. Check OMDb settings or retry."}
            finally{_ratingsLoading.value=false}
        }
    }
}
