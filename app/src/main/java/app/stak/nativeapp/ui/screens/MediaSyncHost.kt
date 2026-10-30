package app.stak.nativeapp.ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.stak.nativeapp.data.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview

@OptIn(FlowPreview::class)
@Composable fun MediaSyncHost(library:JellyfinViewModel,tracking:TrackingViewModel,revision:Int){
    val owner=LocalLifecycleOwner.current;val context=LocalContext.current
    DisposableEffect(owner,revision){
        val observer=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_START->{MediaLiveSync.start(context)};Lifecycle.Event.ON_STOP->{MediaLiveSync.stop()};else->Unit}}
        owner.lifecycle.addObserver(observer)
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)){MediaLiveSync.start(context)}
        onDispose{owner.lifecycle.removeObserver(observer);MediaLiveSync.stop()}
    }
    LaunchedEffect(owner){owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
        var lastFull=0L;var lastHistory=0L;var lastResume=0L
        MediaLiveSync.signals.debounce(2500).collect{
            val event=MediaLiveSync.consume();val now=System.currentTimeMillis()
            if(event.reasons.all{it=="session"})return@collect
            val full=event.reasons.any{it in setOf("library","reconcile","restart","permissions")}||lastFull==0L||(event.ids.isEmpty()&&now-lastFull>30_000)
            if(full){if(!library.loading.value)library.refresh();if(!tracking.loading.value)tracking.refresh();lastFull=now;lastHistory=now;lastResume=now}
            else {
                if(now-lastResume>15_000||"watched" in event.reasons||"favorites" in event.reasons){library.refreshLive(event.ids);lastResume=now}
                if(("watched" in event.reasons||now-lastHistory>60_000)&&!tracking.loading.value){tracking.refresh();lastHistory=now}
            }
        }
    }}
}
