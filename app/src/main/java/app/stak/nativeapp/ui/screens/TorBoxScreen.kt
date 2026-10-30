@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable fun TorBoxScreen(
    onOpenTitle:(MediaItem)->Unit,onOpenLibrary:()->Unit,torboxKey:String,onOpenAccount:()->Unit,
    viewModel:TorBoxViewModel=androidx.lifecycle.viewmodel.compose.viewModel(),onMediaCenter:()->Unit={}
) {
    val approvalIssue by LibraryApprovals.issue.collectAsState();val approvalAccount by LibraryApprovals.issueAccount.collectAsState()
    val rows by viewModel.rows.collectAsState();val loading by viewModel.loading.collectAsState();val matching by viewModel.matching.collectAsState();val error by viewModel.error.collectAsState();val filters by viewModel.filters.collectAsState()
    var options by remember(torboxKey){mutableStateOf(false)};var selected by remember(torboxKey){mutableStateOf<String?>(null)}
    val picked by viewModel.selected.collectAsState();val actionBusy by viewModel.actionBusy.collectAsState();val actionMessage by viewModel.actionMessage.collectAsState()
    var selecting by remember(torboxKey){mutableStateOf(false)};var addOpen by remember(torboxKey){mutableStateOf(false)};var deleteTargets by remember(torboxKey){mutableStateOf<List<TorBoxTorrent>?>(null)}
    var rowActions by remember(torboxKey){mutableStateOf<TorBoxTorrent?>(null)}
    val metadataKey=NativeRuntime.settings.tmdbKey.orEmpty()
    LaunchedEffect(torboxKey,metadataKey){viewModel.refresh(torboxKey)}
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner,torboxKey) {
        var initial=true
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME){if(initial)initial=false else viewModel.refresh(torboxKey)}}
        owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}
    }
    val shown=remember(rows,filters){filterTorBoxLibrary(rows,filters)}
    val counts=remember(rows){rows.groupingBy{torBoxStatus(it.torrent)}.eachCount()}
    LibraryRefresh(loading,{viewModel.refresh(torboxKey)},torboxKey.isNotBlank()&&!actionBusy){
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,22.dp,16.dp,120.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) {
        item(key="controls",contentType="controls") {
            Column {
                LibraryHeader(onMediaCenter){close->
                    DropdownMenuItem(text={Text("Add torrent")},onClick={close();viewModel.clearAddResults();addOpen=true},enabled=torboxKey.isNotBlank()&&!actionBusy,modifier=Modifier.semantics{contentDescription="Add torrent"})
                    DropdownMenuItem(text={Text("Refresh torrents")},onClick={close();viewModel.refresh(torboxKey)},enabled=!actionBusy)
                    DropdownMenuItem(text={Text(if(selecting)"Cancel selection"else "Select torrents")},onClick={close();selecting=!selecting;viewModel.clearSelection()},enabled=torboxKey.isNotBlank()&&!actionBusy)
                }
                Spacer(Modifier.height(10.dp));StakTabs(listOf("jellyfin" to "Jellyfin","torbox" to "TorBox"),"torbox",{if(it=="jellyfin")onOpenLibrary()},Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                if(torboxKey.isBlank()) {
                    Text("Connect TorBox to manage your library.",fontFamily=HankenFont,fontSize=14.sp,color=StakTextMuted)
                    Spacer(Modifier.height(12.dp));StakButton("Add key",onOpenAccount,variant=StakButtonVariant.Primary,icon=Lucide.Key)
                } else {
                    LibrarySearch(filters.query,{viewModel.setFilters(filters.copy(query=it))},{options=true},torbox=true)
                    Spacer(Modifier.height(8.dp))
                    val statuses=buildList{add("all" to "All ${rows.size}");add("active" to "Active ${counts["active"] ?: 0}");add("done" to "Ready ${counts["done"] ?: 0}");if((counts["error"] ?: 0)>0||filters.status=="error")add("error" to "Error ${counts["error"] ?: 0}")}
                    StakTabs(statuses,filters.status,{viewModel.setFilters(filters.copy(status=it))},Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        Text("${shown.size} torrents · ${libraryBytes(shown.sumOf{it.torrent.size})}",Modifier.weight(1f),fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
                        DiscoverySelect("",filters.sort,listOf("newest" to "Newest","name" to "Name A–Z","size" to "Largest first"),{viewModel.setFilters(filters.copy(sort=it))},Modifier.width(142.dp),accessibilityLabel="Sort torrents")
                    }
                    if(selecting)Row(verticalAlignment=Alignment.CenterVertically){
                        StakButton("Cancel selection",{selecting=false;viewModel.clearSelection()},enabled=!actionBusy,variant=StakButtonVariant.Ghost,compact=true)
                        StakButton("Select visible",{viewModel.selectVisible(shown.map{it.torrent.id}.toSet())},enabled=!actionBusy&&shown.isNotEmpty(),compact=true)
                    }
                    if(picked.isNotEmpty()) {
                        Text("${picked.size} selected",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakText)
                        Row {TorrentOperation.entries.filter{it!=TorrentOperation.Delete}.forEach{operation->TextButton(onClick={viewModel.control(torboxKey,picked.toList(),operation)},enabled=!actionBusy){Text(operation.label,fontFamily=SpaceMonoFont,fontSize=9.sp,color=StakText)}}}
                        StakButton("Delete selected",{deleteTargets=rows.filter{it.torrent.id in picked}.map{it.torrent}},enabled=!actionBusy,variant=StakButtonVariant.Destructive)
                    }
                    if(actionBusy)Text("Applying TorBox operation…",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
                    if(approvalAccount==credentialScope("torbox:$torboxKey"))approvalIssue?.let{Text(it,fontFamily=HankenFont,fontSize=12.sp,color=StakTextSoft)}
                    actionMessage?.let{Text(it,fontFamily=HankenFont,fontSize=12.sp,lineHeight=17.sp,color=StakTextMuted)}
                    if(loading){Spacer(Modifier.height(8.dp));StakLoadingProgress(Modifier.fillMaxWidth());Text("Loading downloads…",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)}
                    if(matching)Text("Matching posters…",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
                    if(error!=null){Spacer(Modifier.height(8.dp));Text(error.orEmpty(),fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted);StakButton("Retry library",{viewModel.refresh(torboxKey)})}
                }
            }
        }
        if(torboxKey.isNotBlank()) {
            if(shown.isEmpty()&&!loading&&error==null)item {
                Column {
                    Text(if(rows.isEmpty())"Your TorBox library is empty" else "No matching torrents",fontFamily=DotoFont,fontWeight=FontWeight.Black,fontSize=20.sp,color=StakText)
                    if(rows.isNotEmpty()) {Text("Try another search or status filter.",fontFamily=HankenFont,fontSize=13.sp,color=StakTextMuted);StakButton("Clear filters",{viewModel.setFilters(LibraryFilters(sort="newest"))})}
                }
            }
            items(shown,key={it.torrent.id},contentType={"torrent"}){entry->Box(stakItemMotion()){TorrentLibraryCard(entry,selecting,entry.torrent.id in picked,{viewModel.select(entry.torrent.id)},{rowActions=entry.torrent},{selecting=true;if(entry.torrent.id !in picked)viewModel.select(entry.torrent.id)},rowActions?.id==entry.torrent.id,{rowActions=null},if(selecting&&entry.torrent.id in picked)picked.size else 1,{operation->
                val targets=if(selecting&&entry.torrent.id in picked)rows.filter{it.torrent.id in picked}.map{it.torrent} else listOf(entry.torrent)
                rowActions=null
                if(operation==TorrentOperation.Delete)deleteTargets=targets else viewModel.control(torboxKey,targets.map{it.id},operation)
            }){selected=entry.torrent.id}}}
        }
    }
    }
    if(addOpen)AddTorrentDialog(torboxKey,viewModel,{addOpen=false})
    deleteTargets?.let{targets->ConfirmTorrentDelete(targets,actionBusy,{viewModel.control(torboxKey,targets.map{it.id},TorrentOperation.Delete);deleteTargets=null},{deleteTargets=null})}
    if(options)LibraryFilterDialog(filters,{viewModel.setFilters(it)},{options=false},torbox=true)
    rows.firstOrNull{it.torrent.id==selected}?.let{entry->TorrentFilesDialog(entry,torboxKey,onOpenTitle,{selected=null})}
}

internal fun libraryBytes(bytes:Long):String=when {
    bytes>=1_099_511_627_776L->"%.2f TB".format(java.util.Locale.US,bytes/1_099_511_627_776.0)
    bytes>=1_073_741_824L->"%.2f GB".format(java.util.Locale.US,bytes/1_073_741_824.0)
    else->"%.1f MB".format(java.util.Locale.US,bytes/1_048_576.0)
}

@Composable private fun TorrentLibraryCard(entry:TorBoxLibraryEntry,selecting:Boolean,picked:Boolean,onToggle:()->Unit,onActions:()->Unit,onLongPress:()->Unit,menuExpanded:Boolean,onDismiss:()->Unit,selectionCount:Int,onOperation:(TorrentOperation)->Unit,onClick:()->Unit) {
    val torrent=entry.torrent;val status=torBoxStatus(torrent)
    Box {
    Column(Modifier.fillMaxWidth().testTag("torrent-row-${torrent.id}")){
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if(picked)Color.White.copy(alpha=.055f)else Color.Transparent).combinedClickable(onClick=if(selecting)onToggle else onClick,onLongClick=onLongPress).padding(vertical=12.dp,horizontal=2.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
        if(selecting)Checkbox(picked,{onToggle()},modifier=Modifier.size(28.dp))
        LibraryArtwork(entry.metadata?.poster,torrent.name,Modifier.width(42.dp).height(63.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(entry.metadata?.name ?: torrent.name,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=19.sp,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis)
            val quality=remember(torrent.name){inferredQuality(torrent.name)}
            val tags=listOfNotNull(quality.value("resolution"),quality.value("hdr")?.takeIf{it!="HDR (unspecified)"},libraryBytes(torrent.size)).joinToString(" · ")
            Text(tags,Modifier.semantics{contentDescription="Filename quality tags and TorBox reported size: $tags"},fontFamily=SpaceMonoFont,fontSize=9.sp,lineHeight=14.sp,color=StakTextMuted,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(status=="active")Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){StakProgress(torrent.progress,Modifier.weight(1f),height=2);Text("${(torrent.progress.coerceIn(0f,1f)*100).toInt()}%",fontFamily=SpaceMonoFont,fontSize=9.sp,color=StakTextMuted)}
            else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                Box(Modifier.size(4.dp).background(if(status=="done")Color(0xff10b981)else StakAccent,RoundedCornerShape(4.dp)))
                Text(if(status=="done")"Ready"else "Error",fontFamily=HankenFont,fontSize=10.sp,color=StakTextSoft)
            }
        }
        if(!selecting)StakIconButton(onActions){Icon(Icons.Default.MoreVert,"Torrent actions for ${torrent.name}",tint=StakTextMuted,modifier=Modifier.size(19.dp))}
    }
    HorizontalDivider(color=Color.White.copy(alpha=.08f))
    }
    GlassDropdownMenu(expanded=menuExpanded,onDismissRequest=onDismiss,containerColor=StakSurface) {
        if(selectionCount>1)DropdownMenuItem(text={Text("$selectionCount selected")},onClick={},enabled=false)
        TorrentOperation.entries.forEach{operation->DropdownMenuItem(text={Text(operation.label.uppercase(java.util.Locale.ENGLISH))},onClick={onOperation(operation)})}
        if(selecting)DropdownMenuItem(text={Text("View files")},onClick={onDismiss();onClick()})
    }
    }
}

@Composable private fun TorrentFilesDialog(entry:TorBoxLibraryEntry,key:String,onOpenTitle:(MediaItem)->Unit,onDismiss:()->Unit) {
    val torrent=entry.torrent;val clipboard=LocalClipboardManager.current;val scope=rememberCoroutineScope()
    var busy by remember(key,torrent.id){mutableStateOf<String?>(null)};var message by remember(key,torrent.id){mutableStateOf<String?>(null)}
    var fileQuery by remember(key,torrent.id){mutableStateOf("")};var artwork by remember(key,torrent.id){mutableStateOf<Map<String,MediaItem>>(emptyMap())}
    var quality by remember{mutableStateOf<QualityReport?>(null)}
    val resolver=remember{TorrentMetadataResolver()}
    LaunchedEffect(key,torrent.id) {
        if(entry.metadata==null)for(file in torrent.files) {
            if(file.name.matches(Regex("(?i).*\\.(mkv|mp4|avi|m4v|ts)$")))resolver.resolve(file.name)?.let{artwork=artwork+(file.id to it)}
        }
    }
    GlassAlertDialog(onDismissRequest=onDismiss,containerColor=StakSurface,title={Text(entry.metadata?.name ?: torrent.name,fontFamily=HankenFont,fontWeight=FontWeight.Bold,fontSize=18.sp,color=StakText,maxLines=3,overflow=TextOverflow.Ellipsis)},text={
        Column {
            Text(torrent.name,fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
            Spacer(Modifier.height(10.dp))
            entry.metadata?.let{media->StakButton("Title details",{onDismiss();onOpenTitle(media)},variant=StakButtonVariant.Ghost)}
            StakTextField(fileQuery,{fileQuery=it},"Search files")
            Spacer(Modifier.height(8.dp))
            message?.let{Text(it,fontFamily=HankenFont,fontSize=12.sp,lineHeight=17.sp,color=StakTextMuted)}
            StakButton("Include torrent in Jellyfin",{scope.launch{try{val saved=LibraryApprovals.capture(torrent.id,key,torrent.hash,torrent.name,entry.metadata);message=if(saved)"Approval saved for the whole torrent. Open Zurg → Library access to connect the server filter."else "Could not save library approval. TorBox content is unchanged."}catch(c:CancellationException){throw c}catch(_:Exception){message="Could not save library approval."}}},enabled=busy==null,compact=true)
            Text("Includes all media files in this torrent. Existing Jellyfin paths must use the filter.",fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)
            val files=torrent.files.filter{it.name.contains(fileQuery,true)}
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=360.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(files.isEmpty())item{Text(if(torrent.files.isEmpty())"No files are available yet." else "No matching files.",fontFamily=HankenFont,color=StakTextMuted)}
                items(files,key={it.id+":"+it.name}){file->
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Top) {
                        LibraryArtwork(artwork[file.id]?.poster ?: entry.metadata?.poster,file.name,Modifier.width(36.dp).height(54.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.name,fontFamily=HankenFont,fontSize=12.sp,lineHeight=17.sp,color=StakText)
                            StakButton("Media info",{quality=inferredQuality(file.name,file.size,"TorBox file API + filename")},variant=StakButtonVariant.Ghost,compact=true)
                            Text(libraryBytes(file.size),fontFamily=SpaceMonoFont,fontSize=9.sp,lineHeight=14.sp,color=StakTextMuted)
                            TextButton(onClick={busy=file.id;message=null;scope.launch{try{val url=DirectServices.download(key,torrent.id,file.id);clipboard.setText(AnnotatedString(url));message="Download link copied."}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){message="Could not get the file link. Retry when TorBox is ready."}finally{busy=null}}},enabled=busy==null&&torrent.finished&&file.id.isNotBlank()) {Text(if(busy==file.id)"Getting link…" else "Copy download link",fontFamily=SpaceMonoFont,fontSize=9.sp)}
                        }
                    }
                }
            }
        }
    },confirmButton={StakButton("Close",onDismiss)})
    quality?.let{MediaQualityDialog(it,{quality=null})}
}
