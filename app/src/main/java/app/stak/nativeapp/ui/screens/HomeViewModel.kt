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

class HomeViewModel(private val repository: MediaRepository = MediaRepository(),private val feedLoader:suspend()->HomeFeed = repository::home, private val calendar:CalendarRepository=CalendarRepository(), private val releaseLoader:suspend(List<MediaItem>,LocalDate,Boolean)->ReleaseFeed = {items,date,force->calendar.upcoming(items,date,force=force)}) : ViewModel() {
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
    init { refresh() }

    fun refresh() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching { feedLoader() }
                .onSuccess { _feed.value = it }
                .onFailure { _error.value = it.message ?: "Could not load Home" }
            _loading.value = false
        }
    }
}
