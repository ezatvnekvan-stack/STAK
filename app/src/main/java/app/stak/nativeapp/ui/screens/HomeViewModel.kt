package app.stak.nativeapp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stak.nativeapp.data.HomeFeed
import app.stak.nativeapp.data.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.*
import app.stak.nativeapp.data.*
import java.time.LocalDate

class HomeViewModel(private val repository: MediaRepository = MediaRepository(),private val feedLoader:(suspend()->HomeFeed)? = null, private val calendar:CalendarRepository=CalendarRepository(), private val releaseLoader:suspend(List<MediaItem>,LocalDate,Boolean)->ReleaseFeed = {items,date,force->calendar.upcoming(items,date,force=force)},private val brandLoader:suspend(MediaItem)->MediaItem={repository.branding(it)}) : ViewModel() {
    private val _feed = MutableStateFlow<HomeFeed?>(null)
    val feed: StateFlow<HomeFeed?> = _feed
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _comingUp=MutableStateFlow<ReleaseFeed?>(null);val comingUp:StateFlow<ReleaseFeed?> = _comingUp
    private val _comingLoading=MutableStateFlow(false);val comingLoading:StateFlow<Boolean> = _comingLoading
    private val _comingError=MutableStateFlow<String?>(null);val comingError:StateFlow<String?> = _comingError
    private var comingJob:Job?=null;private var comingGeneration=0
    fun refreshUpcoming(items:List<MediaItem>,today:LocalDate,force:Boolean=false){
        val token=++comingGeneration;comingJob?.cancel();_comingUp.value=null;_comingError.value=null;_comingLoading.value=false
        if(items.isEmpty())return
        comingJob=viewModelScope.launch{_comingLoading.value=true;try{val result=releaseLoader(items,today,force);if(token==comingGeneration)_comingUp.value=result}
        catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){if(token==comingGeneration)_comingError.value="Coming up could not load. Check TMDb in Metadata & ratings."}
        finally{if(token==comingGeneration)_comingLoading.value=false}}
    }


    private var generation=0
    private var feedDay=LocalDate.now()
    fun refreshDay(day:LocalDate){if(day!=feedDay&&!_loading.value){feedDay=day;refresh()}}
    private var brandingJob:Job?=null
    init { refresh() }
    fun refresh() {
        if (_loading.value) return
        val token=++generation;brandingJob?.cancel()
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching { feedLoader?.invoke() ?: repository.home { partial -> _feed.value=partial } }
                .onSuccess { feed->
                    _feed.value=feed
                    val row=feed.rows.firstOrNull{it.id=="trending_movie"}
                    val first=row?.items?.firstOrNull()
                    if(first!=null&&first.logo==null)brandingJob=viewModelScope.launch{
                        val branded=try{brandLoader(first)}catch(c:CancellationException){throw c}catch(_:Exception){first}
                        if(token==generation)_feed.value=_feed.value?.let{current->current.copy(rows=current.rows.map{r->if(r.id==row?.id&&r.items.firstOrNull()?.mediaKey==first.mediaKey)r.copy(items=listOf(branded)+r.items.drop(1))else r})}
                    }
                }
                .onFailure { _error.value = it.message ?: "Could not load Home" }
            _loading.value = false
        }
    }
}
