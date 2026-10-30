package app.stak.nativeapp.ui.screens

import androidx.lifecycle.ViewModel
import app.stak.nativeapp.data.*
import androidx.lifecycle.viewModelScope
import app.stak.nativeapp.data.MediaItem
import app.stak.nativeapp.data.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class JellyfinViewModel(private val repository: MediaRepository = MediaRepository(),private val libraryLoader:suspend(suspend(List<MediaItem>,Int?)->Unit)->List<MediaItem> = {page->repository.jellyfinLibrary(page)},private val resumeLoader:(suspend()->List<MediaItem>)? = null) : ViewModel() {
    private suspend fun continueFeed()=resumeLoader?.let{ContinueFeed(it())} ?: JellyfinPlayback.continueFeed()
    private var generation=0
    private var job:Job?=null
    private var activeScope:String?=null
    private val _filters=MutableStateFlow(app.stak.nativeapp.data.LibraryFilters(sort="name"))
    val filters:StateFlow<app.stak.nativeapp.data.LibraryFilters> = _filters
    fun setFilters(value:app.stak.nativeapp.data.LibraryFilters){_filters.value=value}
    private val _total=MutableStateFlow<Int?>(null)
    val total:StateFlow<Int?> = _total
    private val _resume=MutableStateFlow<List<MediaItem>>(emptyList())
    val resume:StateFlow<List<MediaItem>> = _resume
    private val _resumeLoading=MutableStateFlow(false)
    val resumeLoading:StateFlow<Boolean> = _resumeLoading
    private val _resumeError=MutableStateFlow<String?>(null)
    val resumeError:StateFlow<String?> = _resumeError
    private val _syncMessage=MutableStateFlow<String?>(null)
    val syncMessage:StateFlow<String?> = _syncMessage
    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init { refresh() }

    private var liveJob:Job?=null
    fun refreshLive(ids:Set<String>){
        if(!app.stak.nativeapp.data.NativeRuntime.settings.jellyfinConnected)return
        val scope=app.stak.nativeapp.data.jellyfinScope();val fetchedAt=System.currentTimeMillis()
        liveJob?.cancel();liveJob=viewModelScope.launch {
            try{
                if(ids.isNotEmpty()){
                    val data=app.stak.nativeapp.data.JellyfinIntelligence.items(mapOf("Ids" to ids.joinToString(","),"Fields" to "UserData,ProviderIds,MediaSources,MediaStreams,Genres,DateCreated","Limit" to "100"))
                    val observed=run{data.objects("Items").filter{it.text("Type") in setOf("Movie","Series","Episode")}.map{jellyfinMediaItem(it,DirectJellyfin.normalizeServer(NativeRuntime.settings.jellyfinUrl.orEmpty()))}}
                    if(scope==app.stak.nativeapp.data.jellyfinScope()){
                        app.stak.nativeapp.data.observeJellyfin(scope,observed,fetchedAt)
                        val replacements=observed.filter{it.episode==null}.associateBy{it.jellyfinId}
                        _items.value=_items.value.map{replacements[it.jellyfinId] ?: it}
                    }
                }
                val feed=continueFeed();val resumed=feed.items;if(scope==app.stak.nativeapp.data.jellyfinScope()){_resume.value=resumed;_resumeError.value=feed.issue;app.stak.nativeapp.data.observeJellyfin(scope,resumed,fetchedAt)}
            }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}catch(e:Exception){_resumeError.value=jellyfinFailureMessage(e)}
        }
    }
    fun refresh() {
        val token=++generation
        job?.cancel()
        val settings=app.stak.nativeapp.data.NativeRuntime.settings
        val scope=if(settings.jellyfinConnected)app.stak.nativeapp.data.jellyfinScope() else null
        if(scope!=activeScope){_filters.value=app.stak.nativeapp.data.LibraryFilters();_items.value=emptyList();_total.value=null;_resume.value=emptyList();_syncMessage.value=null;activeScope=scope}
        if(scope==null){_items.value=emptyList();_resume.value=emptyList();_loading.value=false;_resumeLoading.value=false;_error.value=null;_resumeError.value=null;return}
        job=viewModelScope.launch {
            launch {
                _resumeLoading.value=true;_resumeError.value=null
                try {val feed=continueFeed();val resumed=feed.items;if(token==generation){_resume.value=resumed;_resumeError.value=feed.issue}}
                catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
                catch(e:Exception){if(token==generation)_resumeError.value=jellyfinFailureMessage(e)}
                finally{if(token==generation)_resumeLoading.value=false}
            }
            _syncMessage.value=null
            launch {
            _loading.value = true
            _error.value = null
            val fetchedAt=System.currentTimeMillis()
            try { val loaded=libraryLoader{partial,total->kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate){if(token==generation){_items.value=partial;_total.value=total}}};if(token==generation){_items.value=loaded;app.stak.nativeapp.data.observeJellyfin(scope,loaded,fetchedAt)} }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (e: Exception) { if(token==generation)_error.value = jellyfinFailureMessage(e) }
            if(token==generation)_loading.value = false
            }
        }
    }
}
