package app.stak.nativeapp.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.*

@Composable internal fun AddTorrentDialog(key:String,viewModel:TorBoxViewModel,onDismiss:()->Unit) {
    val busy by viewModel.actionBusy.collectAsState();val results by viewModel.addResults.collectAsState();val message by viewModel.actionMessage.collectAsState()
    var text by remember{mutableStateOf("")};var file by remember{mutableStateOf<TorrentUpload?>(null)};var fileBusy by remember{mutableStateOf(false)};var fileError by remember{mutableStateOf<String?>(null)}
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch {
        fileBusy=true;fileError=null
        try {
            file=withContext(Dispatchers.IO) {
                val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->if(cursor.moveToFirst())cursor.getString(0) else null} ?: "selected.torrent"
                val stream=context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Could not read this file.")
                readTorrentUpload(name,stream)
            }
        }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){fileError="Choose a readable .torrent file up to 5 MiB."}finally{fileBusy=false}
    }}
    LaunchedEffect(results){if(results.isNotEmpty()) {
        text=results.filter{!it.success&&!it.input.startsWith("file:")}.joinToString("\n"){it.input}
        if(results.any{it.input.startsWith("file:")&&it.success})file=null
        if(results.all{it.success})onDismiss()
    }}
    val uncertain=results.any{it.uncertain}
    GlassAlertDialog(onDismissRequest={if(!busy&&!fileBusy)onDismiss()},containerColor=StakSurface,title={Text("Add torrents",fontFamily=DotoFont)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("One magnet link or info hash per line (up to 50).",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
        StakTextField(text,{text=it},"magnet:?xt=urn:btih:…",singleLine=false,height=132,enabled=!busy&&!uncertain)
        StakButton(if(fileBusy)"Reading file…" else file?.name ?: "Choose .torrent file",{picker.launch(arrayOf("application/x-bittorrent","application/octet-stream","*/*"))},enabled=!busy&&!fileBusy&&!uncertain)
        if(file!=null)StakButton("Remove file",{file=null},enabled=!busy&&!fileBusy)
        Text("Torrent files: maximum 5 MiB.",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
        (fileError ?: message)?.let{Text(it,fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)}
        if(uncertain)StakButton("Refresh library",{viewModel.refresh(key);onDismiss()})
    }},confirmButton={StakButton(if(busy)"Adding…" else "Add to TorBox",{viewModel.add(key,text,file)},enabled=!busy&&!fileBusy&&!uncertain&&(text.isNotBlank()||file!=null))},dismissButton={StakButton("Close",onDismiss,enabled=!busy&&!fileBusy)})
}

@Composable internal fun ConfirmTorrentDelete(targets:List<TorBoxTorrent>,busy:Boolean,onConfirm:()->Unit,onDismiss:()->Unit) {
    GlassAlertDialog(onDismissRequest={if(!busy)onDismiss()},containerColor=StakSurface,title={Text("Delete ${targets.size} torrent${if(targets.size==1)"" else "s"}?",fontFamily=DotoFont)},text={Column {
        Text("These torrents will be permanently removed from your TorBox account.",fontFamily=HankenFont,fontSize=13.sp,color=StakTextMuted)
        Spacer(Modifier.height(12.dp));LazyColumn(Modifier.heightIn(max=220.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){items(targets,key={it.id}){Text(it.name,fontFamily=HankenFont,fontSize=12.sp,color=StakText)}}
    }},confirmButton={StakButton("Delete ${targets.size}",onConfirm,enabled=!busy,variant=StakButtonVariant.Destructive)},dismissButton={StakButton("Cancel",onDismiss,enabled=!busy)})
}
