package app.stak.nativeapp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class LibraryScanViewModel(private val gateway:LibraryScanGateway=DirectLibraryScanGateway):ViewModel(){
    private val mutable=MutableStateFlow(LibraryScanState());val state:StateFlow<LibraryScanState> = mutable
    private val checking=MutableStateFlow(false);val loading:StateFlow<Boolean> = checking
    private var account="";private var busy=false;private var generation=0
    fun account(value:String){if(value!=account){account=value;generation++;mutable.value=LibraryScanState();busy=false;checking.value=false}}
    fun refresh(){if(busy||account=="disconnected")return;val token=generation;busy=true;checking.value=true;viewModelScope.launch{try{
        val allowed=gateway.allowed()
        if(token!=generation)return@launch
        if(!allowed){mutable.value=LibraryScanState(allowed=false,message="Library scans require a Jellyfin administrator.");return@launch}
        val snapshot=gateway.snapshot();if(token==generation)mutable.value=reduceLibraryScan(mutable.value.copy(allowed=true),snapshot)
    }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){if(token==generation)mutable.value=mutable.value.copy(progress=null,message="Scan status unavailable. Check Jellyfin and refresh.") }finally{if(token==generation){busy=false;checking.value=false}}}}
    fun scan(){if(busy||state.value.active||account=="disconnected")return;val token=generation;busy=true;mutable.value=mutable.value.copy(phase="requesting",progress=null,message="Requesting library scan…");viewModelScope.launch{try{
        require(gateway.allowed()){ "Administrator permission required." };val before=gateway.snapshot();if(token!=generation)return@launch
        if(before.running){mutable.value=reduceLibraryScan(LibraryScanState(allowed=true),before);return@launch}
        gateway.request();if(token==generation)mutable.value=LibraryScanState(phase="queued",message="Waiting for Jellyfin to start the scan…",allowed=true,baseline=before.resultKey,requested=true)
    }catch(cancelled:CancellationException){throw cancelled}catch(error:Exception){if(token==generation)mutable.value=mutable.value.copy(phase="unknown",progress=null,message=if(error is IllegalArgumentException)"Administrator permission is required."else "Scan acceptance could not be confirmed. Refresh status before retrying.") }finally{if(token==generation){busy=false;checking.value=false}}}}
}
@Composable fun LibraryScanControl(connected:Boolean,onCompleted:()->Unit={},viewModel:LibraryScanViewModel=androidx.lifecycle.viewmodel.compose.viewModel(),leading:(@Composable RowScope.()->Unit)?=null){
    val state by viewModel.state.collectAsState();val checking by viewModel.loading.collectAsState();val lifecycle=LocalLifecycleOwner.current.lifecycle
    val account=if(connected)jellyfinScope()else "disconnected"
    val completed by rememberUpdatedState(onCompleted)
    LaunchedEffect(account,lifecycle){viewModel.account(account);if(connected)lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){viewModel.refresh();while(true){delay(if(viewModel.state.value.active)3000 else 30000);viewModel.refresh()}}}
    LaunchedEffect(state.phase){if(state.phase=="completed")completed()}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        leading?.invoke(this)
        StakButton(if(state.active)"Scanning library…"else "Scan library",viewModel::scan,modifier=Modifier.nothingSignal(state.phase),icon=if(state.phase=="completed")Lucide.Check else Lucide.Refresh,enabled=connected&&!state.active&&!checking&&state.allowed!=false,compact=true)
        }
        if(state.active){if(state.progress!=null)StakProgress(state.progress!!,height=4)else StakLoadingProgress();Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(state.message.orEmpty(),Modifier.weight(1f),fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted);state.progress?.let{Text("${(it*100).toInt()}%",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakText)}}}
        else if(state.message!=null){Row(verticalAlignment=Alignment.CenterVertically){Text(state.message.orEmpty(),Modifier.weight(1f),fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted);if(state.phase=="unknown")StakButton("Check status",viewModel::refresh,variant=StakButtonVariant.Ghost,compact=true)}}
    }
}
