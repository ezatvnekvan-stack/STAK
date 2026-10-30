package app.stak.nativeapp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stak.nativeapp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

class CalendarViewModel(private val repository:CalendarRepository=CalendarRepository(),private val loader:suspend(List<MediaItem>,LocalDate,LocalDate,Boolean)->ReleaseFeed={items,start,end,force->repository.load(items,start,end,force)}):ViewModel() {
    private val _feed=MutableStateFlow<ReleaseFeed?>(null);val feed:StateFlow<ReleaseFeed?> = _feed
    private val _loading=MutableStateFlow(false);val loading:StateFlow<Boolean> = _loading
    private val _error=MutableStateFlow<String?>(null);val error:StateFlow<String?> = _error
    private var generation=0;private var job:Job?=null
    fun refresh(items:List<MediaItem>,month:YearMonth,weekStart:Int,force:Boolean=false) {
        val token=++generation;job?.cancel();_feed.value=null;_error.value=null
        val cells=calendarCells(month,weekStart)
        job=viewModelScope.launch {
            _loading.value=true
            try{val result=loader(items,cells.first(),cells.last(),force);if(token==generation)_feed.value=result}
            catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){if(token==generation)_error.value="Calendar could not update. Configure TMDb in Metadata sources or retry."}
            finally{if(token==generation)_loading.value=false}
        }
    }
}
