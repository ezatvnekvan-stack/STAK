package app.stak.nativeapp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stak.nativeapp.data.canImport
import app.stak.nativeapp.data.sourceIdentity
import app.stak.nativeapp.data.Episode
import app.stak.nativeapp.data.MediaRepository
import app.stak.nativeapp.data.TitleDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TitleViewModel(private val repository: MediaRepository = MediaRepository(), private val detailsLoader: suspend (String, String) -> TitleDetails = repository::fastDetails, private val sourceLoader: (suspend (String, String, Episode?) -> List<app.stak.nativeapp.data.SourceRelease>)? = null, private val cacheLoader:suspend(List<String>,String)->Set<String> = app.stak.nativeapp.data.SourceRepository::cached, private val addLoader:suspend(app.stak.nativeapp.data.SourceRelease,String)->String = app.stak.nativeapp.data.SourceRepository::add,private val packSizeLoader:suspend(List<String>,String)->Map<String,Long> = app.stak.nativeapp.data.SourceRepository::torrentSizes,private val materialLoader:suspend(app.stak.nativeapp.data.SourceRelease,String)->app.stak.nativeapp.data.PreparedSource=app.stak.nativeapp.data.SourceMaterials::prepare,private val seasonLoader:suspend(String,Int)->List<Episode> = repository::seasonDetails,private val progressiveSearch:(suspend(app.stak.nativeapp.data.NabQuery,suspend(app.stak.nativeapp.data.AddonSearchResult)->Unit)->app.stak.nativeapp.data.AddonSearchResult)?=null) : ViewModel() {
    private val _detail = MutableStateFlow<TitleDetails?>(null)
    val detail: StateFlow<TitleDetails?> = _detail
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _remoteWatched=MutableStateFlow<Map<String,Boolean>>(emptyMap())
    val remoteWatched:StateFlow<Map<String,Boolean>> = _remoteWatched
    private var remoteJob:kotlinx.coroutines.Job?=null
    private val _season = MutableStateFlow(1)
    val season: StateFlow<Int> = _season
    private val _episode = MutableStateFlow<Episode?>(null)
    val episode: StateFlow<Episode?> = _episode

    private var ratingsJob:kotlinx.coroutines.Job?=null
    private var seasonGeneration=0
    private var seasonJob: kotlinx.coroutines.Job? = null
    private val loadedSeasons = mutableSetOf<Int>()
    private var preferredEpisode:Pair<Int,Int>?=null
    private val _seasonLoading = MutableStateFlow(false)
    val seasonLoading:StateFlow<Boolean> = _seasonLoading
    private val _seasonError = MutableStateFlow<String?>(null)
    val seasonError:StateFlow<String?> = _seasonError
    private var detailToken = 0
    private var detailJob: kotlinx.coroutines.Job? = null
    private var sourceToken = 0
    private var sourceJob: kotlinx.coroutines.Job? = null
    private var addedScope:String?=null
    private var requestedIdentity:String?=null
    fun ensureLoaded(type:String,id:String,source:app.stak.nativeapp.data.MediaItem?=null){
        val identity="$type:$id:${source?.jellyfinId.orEmpty()}"
        if(requestedIdentity!=identity)load(type,id,source)
    }
    fun load(type: String, id: String, source:app.stak.nativeapp.data.MediaItem?=null) {
        requestedIdentity="$type:$id:${source?.jellyfinId.orEmpty()}"
        preferredEpisode=source?.season?.let { season -> source.episode?.let { season to it } }
        val token = ++detailToken
        searchFinishedAt=0L;searchFingerprint=""
        seasonGeneration++;ratingsJob?.cancel();seasonJob?.cancel();loadedSeasons.clear();_seasonLoading.value=false;_seasonError.value=null
        remoteJob?.cancel();_remoteWatched.value=emptyMap()
        sourceToken++
        sourceJob?.cancel()
        _cacheLoading.value=false;_cacheError.value=null;_cached.value=emptySet()
        _sources.value = emptyList()
        _sourceError.value = null;_sourceWarnings.value=emptyList()
        _sourceLoading.value = false
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _detail.value = source?.let { TitleDetails(it, it.genres, logo=it.logo) }
            runCatching { detailsLoader(type, id) }
                .onSuccess {
                    if (token != detailToken) return@onSuccess
                    _detail.value = it.copy(item=it.item.copy(poster=it.item.poster ?: source?.poster,background=it.item.background ?: source?.background),logo=it.logo ?: source?.logo)
                    loadedSeasons.addAll(it.episodes.map { ep -> ep.season })
                    _season.value = source?.season?.takeIf{season->season in it.seasons} ?: it.seasons.firstOrNull() ?: 1
                    _episode.value = it.episodes.firstOrNull { ep -> preferredEpisode==(ep.season to ep.episode) } ?: it.episodes.firstOrNull { ep -> ep.season == _season.value }
                    if(it.item.isSeries && it.seasons.isNotEmpty() && _episode.value==null) selectSeason(_season.value)
                    ratingsJob=viewModelScope.launch {
                        try {
                            val enriched=repository.enrichRatings(it)
                            if(token==detailToken)_detail.value=_detail.value?.copy(imdbRating=enriched.imdbRating,rottenTomatoes=enriched.rottenTomatoes,metacritic=enriched.metacritic)
                        }catch(c:kotlinx.coroutines.CancellationException){throw c}catch(_:Exception){}
                    }
                    if(runCatching{app.stak.nativeapp.data.NativeRuntime.settings.jellyfinConnected}.getOrDefault(false)) {
                        val sourceId=if(source?.isSeries==true&&source.episode!=null)source.jellyfinSeriesId else source?.jellyfinId
                        val media=it.item.copy(jellyfinId=sourceId ?: it.item.jellyfinId,jellyfinSeriesId=source?.jellyfinSeriesId ?: it.item.jellyfinSeriesId)
                        remoteJob=viewModelScope.launch {try {val states=app.stak.nativeapp.data.JellyfinPlayback.watchedStates(media);if(token==detailToken)_remoteWatched.value=states}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}catch(_:Exception){}}
                    }
                }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; if (token == detailToken) _error.value = "Could not load this title. Check the metadata settings and retry." }
            if (token == detailToken) _loading.value = false
        }
    }

    private val _sources = MutableStateFlow<List<app.stak.nativeapp.data.SourceRelease>>(emptyList())
    val sources: StateFlow<List<app.stak.nativeapp.data.SourceRelease>> = _sources
    private val _sourceLoading = MutableStateFlow(false)
    val sourceLoading: StateFlow<Boolean> = _sourceLoading
    private val _sourceError = MutableStateFlow<String?>(null)
    val sourceError: StateFlow<String?> = _sourceError
    private val _sourceWarnings=MutableStateFlow<List<String>>(emptyList())
    val sourceWarnings:StateFlow<List<String>> = _sourceWarnings
    private val _cached=MutableStateFlow<Set<String>>(emptySet())
    val cached:StateFlow<Set<String>> = _cached
    private val _cacheLoading=MutableStateFlow(false)
    val cacheLoading:StateFlow<Boolean> = _cacheLoading
    private val _cacheError=MutableStateFlow<String?>(null)
    val cacheError:StateFlow<String?> = _cacheError
    private val _remainingSources=MutableStateFlow(0)
    val remainingSources:StateFlow<Int> = _remainingSources
    private val _cacheProgress=MutableStateFlow(0)
    val cacheProgress:StateFlow<Int> = _cacheProgress
    private var cacheSession:app.stak.nativeapp.data.CachedSourceSession?=null
    private var sourceAccount=""
    private var sessionKey=""
    private var providerWarnings=emptyList<String>()
    private var packSizes=emptyMap<String,Long>()
    private val cacheMemo=linkedMapOf<String,Pair<Long,Boolean>>()
    private suspend fun memoCheck(hashes:List<String>,key:String):Set<String>{
        val account=app.stak.nativeapp.data.credentialScope(key);val now=System.currentTimeMillis()
        cacheMemo.entries.removeAll{now-it.value.first>30000}
        val fresh=hashes.filter{"$account:$it" !in cacheMemo}
        if(fresh.isNotEmpty()){
            val result=cacheLoader(fresh,key)
            fresh.forEach{cacheMemo["$account:$it"]=System.currentTimeMillis() to (it in result)}
            while(cacheMemo.size>5000)cacheMemo.remove(cacheMemo.keys.first())
        }
        return hashes.filter{cacheMemo["$account:$it"]?.second==true}.toSet()
    }
    private val _packMode=MutableStateFlow(false)
    val packMode:StateFlow<Boolean> = _packMode
    private val _added=MutableStateFlow<Map<String,String>>(emptyMap())
    val added:StateFlow<Map<String,String>> = _added
    private val _adding=MutableStateFlow<String?>(null)
    val adding:StateFlow<String?> = _adding
    private val _addError=MutableStateFlow<String?>(null)
    val addError:StateFlow<String?> = _addError

    private fun publishSources(snapshot:app.stak.nativeapp.data.CachedSourceSnapshot,token:Int){
        if(token!=sourceToken)return
        if(sourceAccount!=app.stak.nativeapp.data.torboxScope()){
            _sources.value=emptyList();_cached.value=emptySet();_remainingSources.value=0
            throw kotlinx.coroutines.CancellationException("TorBox account changed")
        }
        _sources.value=snapshot.releases.map{it.copy(torrentSizeBytes=packSizes[it.infoHash]?:it.torrentSizeBytes)}
        _cached.value=snapshot.hashes;_remainingSources.value=snapshot.remaining;_cacheProgress.value=snapshot.checked
        _sourceWarnings.value=providerWarnings+if(snapshot.metadataFailures>0)listOf("${snapshot.metadataFailures} releases could not be verified from torrent metadata. They remain hidden.")else emptyList()
    }
    private suspend fun packSizes(token:Int,type:String){
        if(type!="series")return
        val hashes=_sources.value.filter{app.stak.nativeapp.data.seasonPackMatches(it.name,_season.value)&&it.infoHash !in packSizes}.mapNotNull{it.infoHash}.distinct()
        if(hashes.isEmpty())return
        val sizes=try{packSizeLoader(hashes,sessionKey)}catch(c:kotlinx.coroutines.CancellationException){throw c}catch(_:Exception){emptyMap()}
        if(token==sourceToken){packSizes=packSizes+sizes;cacheSession?.snapshot()?.let{publishSources(it,token)}}
    }
    private var searchFingerprint=""
    private var searchFinishedAt=0L
    fun loadSources(type:String,id:String,force:Boolean=false){
        val account=app.stak.nativeapp.data.torboxScope();val settings=app.stak.nativeapp.data.NativeRuntime.settings;val key=settings.torboxKey.orEmpty()
        val config=app.stak.nativeapp.data.credentialScope(com.google.gson.Gson().toJson(app.stak.nativeapp.data.StreamAddonStore(settings).all())+com.google.gson.Gson().toJson(app.stak.nativeapp.data.TorznabStore(settings).all()))
        val fingerprint="$account:$config:$type:$id:${_season.value}:${_episode.value?.episode}:${_packMode.value}"
        if(searchFingerprint!=fingerprint&&(_sourceLoading.value||_cacheLoading.value))invalidateSources()
        if(_sourceLoading.value||_cacheLoading.value)return
        if(!force&&searchFingerprint==fingerprint&&System.currentTimeMillis()-searchFinishedAt<30_000&&_cacheError.value==null&&_sourceError.value==null)return
        if(force)cacheMemo.clear()
        searchFingerprint=fingerprint;searchFinishedAt=0L
        if(addedScope!=account){_added.value=emptyMap();addedScope=account}
        val token=++sourceToken;sourceAccount=account;sessionKey=key
        sourceJob?.cancel()
        sourceJob=viewModelScope.launch{
            _sourceLoading.value=true;_cacheLoading.value=true;_sourceError.value=null;_cacheError.value=null
            _sources.value=emptyList();_cached.value=emptySet();_sourceWarnings.value=emptyList();providerWarnings=emptyList()
            _remainingSources.value=0;_cacheProgress.value=0;packSizes=emptyMap()
            if(key.isBlank()){_sourceError.value="Configure TorBox in Settings to find confirmed cached sources.";_sourceLoading.value=false;_cacheLoading.value=false;return@launch}
            val episode=if(type=="series"&&_packMode.value)_detail.value?.episodes?.firstOrNull{it.season==_season.value}else _episode.value
            val session=app.stak.nativeapp.data.CachedSourceSession(key,::memoCheck,materialLoader);cacheSession=session
            val batches=kotlinx.coroutines.channels.Channel<app.stak.nativeapp.data.AddonSearchResult>(16)
            val metadata=kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
            val verifier=launch{
                var budget=8
                for(signal in metadata){
                    if(budget<=0)continue
                    try{budget-=session.more(budget){publishSources(it,token)}}
                    catch(c:kotlinx.coroutines.CancellationException){throw c}
                    catch(_:Exception){budget=0;if(token==sourceToken){publishSources(session.snapshot(),token);_cacheError.value="Some releases could not be verified. Only confirmed releases are shown."}}
                }
            }
            val producer=launch{
                try{
                    val result=if(sourceLoader!=null)app.stak.nativeapp.data.AddonSearchResult(sourceLoader.invoke(type,id,episode))
                    else (progressiveSearch?:app.stak.nativeapp.data.SourceSearch::search)(app.stak.nativeapp.data.NabQuery(type,id,_detail.value?.item?.name,_detail.value?.item?.year,episode?.season,episode?.episode,_packMode.value)){batches.send(it)}
                    batches.send(result)
                }catch(c:kotlinx.coroutines.CancellationException){throw c}catch(e:Exception){batches.close(e)}finally{batches.close()}
            }
            try{
                for(batch in batches){
                    providerWarnings=(providerWarnings+batch.warnings).distinct().take(12)
                    session.accept(batch.releases);metadata.trySend(Unit)
                    session.known{publishSources(it,token)}
                }
                if(token==sourceToken)_sourceLoading.value=false
                metadata.close();verifier.join()
                packSizes(token,type)
                if(token==sourceToken&&_cacheError.value==null)searchFinishedAt=System.currentTimeMillis()
            }catch(c:kotlinx.coroutines.CancellationException){throw c}
            catch(_:Exception){if(token==sourceToken){publishSources(session.snapshot(),token);_cacheError.value="Source or TorBox cache verification could not finish. Only confirmed releases are shown. Retry search."}}
            finally{producer.cancel();metadata.cancel();verifier.cancel();if(token==sourceToken){_sourceLoading.value=false;_cacheLoading.value=false}}
        }
    }
    fun checkMoreSources(){
        if(_sourceLoading.value||_cacheLoading.value||_remainingSources.value==0)return
        val session=cacheSession?:return;val token=sourceToken
        _cacheLoading.value=true;_cacheError.value=null
        sourceJob=viewModelScope.launch{
            try{session.more{publishSources(it,token)};packSizes(token,_detail.value?.item?.type?:"movie")}
            catch(c:kotlinx.coroutines.CancellationException){throw c}
            catch(_:Exception){if(token==sourceToken){publishSources(session.snapshot(),token);_cacheError.value="Could not verify the next releases. Only confirmed releases are shown."}}
            finally{if(token==sourceToken)_cacheLoading.value=false}
        }
    }

    fun refreshWatched(media:app.stak.nativeapp.data.MediaItem){
        if(!app.stak.nativeapp.data.NativeRuntime.settings.jellyfinConnected)return
        val token=detailToken;val account=app.stak.nativeapp.data.jellyfinScope()
        remoteJob?.cancel();remoteJob=viewModelScope.launch{try{val values=app.stak.nativeapp.data.JellyfinPlayback.watchedStates(media);if(token==detailToken&&account==app.stak.nativeapp.data.jellyfinScope())_remoteWatched.value=values}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}catch(_:Exception){}}
    }
    fun selectSeason(season: Int) {
        invalidateSources()
        _season.value = season
        _episode.value = _detail.value?.episodes?.firstOrNull { it.season == season }
        val request=++seasonGeneration
        seasonJob?.cancel();_seasonLoading.value=false;_seasonError.value=null
        val current=_detail.value ?: return
        if(season in loadedSeasons || !current.item.isSeries) return
        val token=detailToken
        _seasonLoading.value=true
        seasonJob=viewModelScope.launch {
            try {
                val rows=seasonLoader(current.item.id,season)
                if(token==detailToken && request==seasonGeneration && _season.value==season){
                    loadedSeasons+=season
                    _detail.value=_detail.value?.let { it.copy(episodes=(it.episodes.filterNot { ep -> ep.season==season }+rows).sortedWith(compareBy({ep->ep.season},{ep->ep.episode}))) }
                    _episode.value=rows.firstOrNull{ep->preferredEpisode==(ep.season to ep.episode)} ?: rows.firstOrNull()
                }
            } catch(c:kotlinx.coroutines.CancellationException){throw c}
            catch(_:Exception){if(token==detailToken && request==seasonGeneration)_seasonError.value="This season could not load. Tap to retry."}
            finally{if(token==detailToken && request==seasonGeneration && _season.value==season)_seasonLoading.value=false}
        }
    }

    fun selectEpisode(episode: Episode) {
        invalidateSources()
        _season.value = episode.season
        _episode.value = episode
    }
    private fun invalidateSources() {
        searchFinishedAt=0L;searchFingerprint=""
        sourceToken++
        sourceJob?.cancel();cacheSession=null;_remainingSources.value=0;_cacheProgress.value=0
        _sources.value = emptyList()
        _sourceError.value = null;_sourceWarnings.value=emptyList()
        _sourceLoading.value = false
        _cacheLoading.value=false;_cacheError.value=null;_cached.value=emptySet()
    }
    fun setPackMode(value:Boolean) { if(value!=_packMode.value){invalidateSources();_packMode.value=value} }
    fun addSource(source:app.stak.nativeapp.data.SourceRelease) {
        if(!source.canImport||source.infoHash !in _cached.value||sourceAccount!=app.stak.nativeapp.data.torboxScope())return
        val hash=source.sourceIdentity
        if(_adding.value!=null||hash in _added.value)return
        _adding.value=hash;_addError.value=null
        val selectedMedia=_detail.value?.item;val selectedEpisode=_episode.value
        val accountScope=app.stak.nativeapp.data.torboxScope();val addKey=app.stak.nativeapp.data.NativeRuntime.settings.torboxKey.orEmpty()
        viewModelScope.launch {
            try { val id=addLoader(source,addKey);if(accountScope==app.stak.nativeapp.data.torboxScope()){
                    _added.value=_added.value+(hash to id)
                    if(!app.stak.nativeapp.data.LibraryApprovals.capture(id,app.stak.nativeapp.data.NativeRuntime.settings.torboxKey.orEmpty(),source.infoHash,source.name,selectedMedia))_addError.value="Added to TorBox, but library approval could not be saved. Approve this torrent from Library; do not add it again."
                    runCatching{app.stak.nativeapp.data.DeliveryTracking.record(id,app.stak.nativeapp.data.NativeRuntime.settings.torboxKey.orEmpty(),selectedMedia,selectedEpisode,source)}.onFailure{_addError.value="Added to TorBox, but the local delivery record could not be saved. Do not add it again."}
                } }
            catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
            catch(error:app.stak.nativeapp.data.NabFailure){_addError.value=error.message}
            catch(_:app.stak.nativeapp.data.UnconfirmedTorrentMutation){_addError.value="The request may have reached TorBox. Refresh your TorBox library before attempting to add it again."}
            catch(_:Exception){_addError.value="Could not add this release to TorBox. Check your key, account limits and connection."}
            finally{_adding.value=null}
        }
    }
}

