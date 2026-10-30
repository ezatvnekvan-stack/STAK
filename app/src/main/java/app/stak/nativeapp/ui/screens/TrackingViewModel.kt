package app.stak.nativeapp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stak.nativeapp.data.*
import app.stak.nativeapp.data.db.TrackingEntity
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class TrackingViewModel(private val historyLoader:suspend()->List<JournalEntry> = {JellyfinJournal.load()},private val episodeLoader:suspend(MediaItem)->SeriesPlan = ::loadTrackingSeries):ViewModel() {
    private val _remote=MutableStateFlow<List<JournalEntry>>(emptyList());val remote:StateFlow<List<JournalEntry>> = _remote
    private val _loading=MutableStateFlow(false);val loading:StateFlow<Boolean> = _loading
    private val _error=MutableStateFlow<String?>(null);val error:StateFlow<String?> = _error
    private val _personalReady=MutableStateFlow(false);val personalReady:StateFlow<Boolean> = _personalReady
    private val _personal=MutableStateFlow(TrackerState());val personal:StateFlow<TrackerState> = _personal
    private val _plans=MutableStateFlow<List<SeriesPlan>>(emptyList());val plans:StateFlow<List<SeriesPlan>> = _plans
    private val _operation=MutableStateFlow<String?>(null);val operation:StateFlow<String?> = _operation
    private val _prefs=MutableStateFlow(runCatching{Gson().fromJson(NativeRuntime.settings.journalPreferences,JournalPreferences::class.java)}.getOrNull() ?: JournalPreferences())
    val preferences:StateFlow<JournalPreferences> = _prefs
    init {val old=_prefs.value;if(old.trackerVersion<2||runCatching{NativeRuntime.settings.journalPreferences?.let{!com.google.gson.JsonParser.parseString(it).asJsonObject.has("trackerVersion")}==true}.getOrDefault(false)){val next=old.copy(view=if(old.view=="calendar")"calendar"else "watching",trackerVersion=2);_prefs.value=next;NativeRuntime.settings.journalPreferences=Gson().toJson(next)}}
    fun preferences(value:JournalPreferences){_prefs.value=value;NativeRuntime.settings.journalPreferences=Gson().toJson(value)}
    private var scope:String?=null;private var generation=0;private var job:Job?=null
    private var store:TrackerStore?=null;private var storageJob:Job?=null;private var attachedScope:String?=null
    private var catalogJob:Job?=null;private var candidates=emptyList<MediaItem>();private val loadingSeries=mutableSetOf<String>()
    fun attach(storage:TrackerStore,active:String){
        if(attachedScope==active&&store===storage)return
        store=storage;attachedScope=active;storageJob?.cancel();_personalReady.value=false;_personal.value=TrackerState();_plans.value=emptyList()
        storageJob=viewModelScope.launch{try{storage.observe(active).collect{_personal.value=it;_personalReady.value=true;rebuildPlans()}}catch(c:CancellationException){throw c}catch(_:Exception){_operation.value="Saved tracking could not be read. Existing data was not overwritten."}}
    }
    fun importLegacy(rows:List<TrackingEntity>){val storage=store?:return;viewModelScope.launch{try{storage.importLegacy(rows)}catch(c:CancellationException){throw c}catch(_:Exception){_operation.value="Could not import saved history. Your records are kept."}}}
    fun progressCandidates(followed:List<MediaItem>,entries:List<JournalEntry>,library:List<MediaItem>){
        candidates=(followed.filter{it.isSeries}+entries.filter{it.media.isSeries&&it.played}.map{journalSeriesTitle(it.media)}+library.filter{it.isSeries&&(it.watched||it.nextUp||it.positionMs>0)}).fold(emptyList()){all,item->if(all.any{sameSeries(it,item)})all else all+item}
        rebuildPlans()
        catalogJob?.cancel();catalogJob=viewModelScope.launch{val slots=Semaphore(2);_plans.value.filter{(it.refreshedAt==0L||System.currentTimeMillis()-it.refreshedAt>21_600_000)&&it.status !in setOf("dropped","on_hold")}.take(20).map{plan->async{slots.withPermit{loadSeries(plan.media)}}}.awaitAll()}
    }
    private fun rebuildPlans(){
        val saved=_personal.value.plans
        _plans.value=(saved+candidates.filter{m->saved.none{sameSeries(it.media,m)}}.map{SeriesPlan(journalSeriesTitle(it),status="plan")}).sortedBy{it.media.name.lowercase()}
    }
    suspend fun loadSeries(media:MediaItem,force:Boolean=false){
        val id=journalSeriesIdentity(media);if(!loadingSeries.add(id))return
        val active=attachedScope?:"local";val previous=_plans.value.firstOrNull{sameSeries(it.media,media)}?:SeriesPlan(media)
        try{
            val metadata=episodeLoader(media)
            val latestStatus=store?.read(active)?.plans?.firstOrNull{sameSeries(it.media,media)}?.status?:previous.status
            val result=metadata.copy(status=latestStatus,refreshedAt=System.currentTimeMillis())
            if(attachedScope!=null&&attachedScope!=active)return
            store?.savePlan(active,result)?:run{_plans.value=_plans.value.filterNot{sameSeries(it.media,media)}+result}
        }catch(c:CancellationException){throw c}catch(_:Exception){
            val latestStatus=store?.read(active)?.plans?.firstOrNull{sameSeries(it.media,media)}?.status?:previous.status
            val failed=previous.copy(status=latestStatus,issue="Episodes could not update. Retry metadata.",refreshedAt=System.currentTimeMillis())
            if(attachedScope==null||attachedScope==active)store?.savePlan(active,failed)?:run{_plans.value=_plans.value.filterNot{sameSeries(it.media,media)}+failed}
        }finally{loadingSeries.remove(id)}
    }
    fun retrySeries(media:MediaItem){viewModelScope.launch{loadSeries(media,true)}}
    fun status(media:MediaItem,value:String) = Unit
    fun startWatching(media:MediaItem) = Unit
    fun stopWatching(media:MediaItem) = Unit
    fun markMovie(row:WatchingRow,date:java.time.LocalDate,wasWatched:Boolean,onDone:()->Unit)= Unit
    fun rate(media:MediaItem,value:Int?){mutate{storage,active->storage.rate(active,media,value)}}
    fun logAgain(media:MediaItem,date:java.time.LocalDate,onDone:()->Unit={}) = Unit
    fun editEvent(entry:JournalEntry,date:java.time.LocalDate)= Unit
    fun deleteEvent(entry:JournalEntry)= Unit
    fun clearOperation(){_operation.value=null}
    private fun mutate(action:suspend(TrackerStore,String)->Unit){val storage=store?:return;val active=attachedScope?:"local";viewModelScope.launch{try{action(storage,active)}catch(c:CancellationException){throw c}catch(_:Exception){_operation.value="Could not save this tracking change. Retry."}}}
    fun refresh() {
        val active=if(NativeRuntime.settings.jellyfinConnected)jellyfinScope() else "local"
        val token=++generation;job?.cancel();if(active!=scope){_remote.value=emptyList();scope=active}
        if(active=="local"){_loading.value=false;_error.value=null;return}
        job=viewModelScope.launch {
            _loading.value=true;_error.value=null
            try{
                val storage=store;val saved=storage?.read(active)
                if(token==generation&&_remote.value.isEmpty())_remote.value=saved?.remote.orEmpty()
                val fetchedAt=System.currentTimeMillis();val data=historyLoader()
                if(token!=generation||active!=jellyfinScope())return@launch
                // A missing watched result is not proof of an unwatched/deleted item. Verify known missing IDs explicitly, in small batches.
                val pending=emptyList<PlaybackRecord>()
                val missing=saved?.remote.orEmpty().filter{old->old.played&&old.media.jellyfinId!=null&&fetchedAt-old.updatedAt>180_000&&data.none{matchingWatch(it.media,old.media)}&&pending.none{matchingWatch(it.media,old.media)}}.sortedBy{it.updatedAt}.take(20)
                val verified=supervisorScope {
                    val slots=Semaphore(2)
                    missing.map{old->async{slots.withPermit {
                        try {
                            val item=DirectJellyfin.sessionRequest("/Users/${NativeRuntime.settings.jellyfinUserId}/Items/${old.media.jellyfinId}",expectedScope=active)
                            val media=jellyfinMediaItem(item,DirectJellyfin.normalizeServer(NativeRuntime.settings.jellyfinUrl.orEmpty()))
                            old.copy(media=media.copy(imdbId=media.imdbId?:old.media.imdbId),played=media.watched,watchedAt=media.lastPlayedAt.takeIf{it>0},updatedAt=fetchedAt)
                        }catch(c:CancellationException){throw c}catch(_:Exception){old.copy(updatedAt=fetchedAt)}
                    }}}.awaitAll()
                }
                val observed=data.map{it.copy(updatedAt=fetchedAt)}+verified
                if(token!=generation||active!=jellyfinScope())return@launch
                if(storage!=null){storage.remote(active,observed);observeJellyfin(active,observed.map{it.media.copy(watched=it.played)},fetchedAt)}
                if(token==generation)_remote.value=storage?.read(active)?.remote?:data
            }
            catch(cancelled:CancellationException){throw cancelled}
            catch(e:Exception){if(token==generation)_error.value=jellyfinFailureMessage(e)}
            finally{if(token==generation)_loading.value=false}
        }
    }
}
