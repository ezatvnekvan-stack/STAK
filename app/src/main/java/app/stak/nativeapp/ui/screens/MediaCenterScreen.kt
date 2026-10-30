package app.stak.nativeapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import com.google.gson.JsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun stamp(time:Long?)=time?.takeIf{it>0}?.let{Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, HH:mm",Locale.ENGLISH))} ?: "Not recorded"
@Composable private fun DetailLine(label:String,value:String){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(label,Modifier.weight(.4f),fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted);Text(value,Modifier.weight(.6f),fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakText)}}
@Composable private fun SectionName(text:String){Text(text,fontFamily=DotoFont,fontWeight=FontWeight.Bold,fontSize=17.sp,color=StakText)}
@Composable internal fun LegacyMediaCenterScreen(onBack:()->Unit,connections:ConnectionSettingsController,viewModel:MediaCenterViewModel=androidx.lifecycle.viewmodel.compose.viewModel()){
    val vm=viewModel;var page by rememberSaveable{mutableStateOf("home")}
    val busy by vm.busy.collectAsState();val error by vm.error.collectAsState();val message by vm.message.collectAsState()
    val dashboard by vm.dashboard.collectAsState();val journal by vm.journal.collectAsState();val live by MediaLiveSync.state.collectAsState()
    val rows by vm.rows.collectAsState();val total by vm.total.collectAsState();val folderName by vm.folderName.collectAsState()
    val latency by vm.davLatency.collectAsState();val listing by vm.dav.collectAsState();val davPath by vm.davPath.collectAsState();val checked by vm.davCheckedAt.collectAsState();val report by vm.quality.collectAsState()
    var fileReport by remember{mutableStateOf<QualityReport?>(null)};var scanConfirm by remember{mutableStateOf(false)};var clearConfirm by remember{mutableStateOf(false)}
    LaunchedEffect(jellyfinScope()){vm.resetAccount()}
    BackHandler(enabled=page!="home"){page="home";vm.clearMessage()}
    LaunchedEffect(page){vm.clearMessage();when(page){"server"->if(vm.settings.jellyfinConnected)vm.refreshServer();"libraries"->vm.browse();"favorites"->vm.favorites();"recent"->vm.recent();"files"->if(readZurg().endpoint.isNotBlank())vm.openDav()}}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(page,lifecycle){if(page=="server")lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(true){delay(30000);if(vm.settings.jellyfinConnected&&!vm.busy.value)vm.refreshServer()}}}
    if(page=="connect"){ProviderSettings("jellyfin",connections,{page="server"},backLabel="Media center");return}
    val account=if(vm.settings.jellyfinConnected)jellyfinScope()else torboxScope()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,18.dp,16.dp,40.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically){StakIconButton({if(page=="home")onBack()else{page="home";vm.clearMessage()}}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back",tint=StakText,modifier=Modifier.size(20.dp))};StakPageHeader(when(page){"server"->"Jellyfin server";"zurg"->"Zurg center";"files"->"WebDAV files";"deliveries"->"Library status";"device"->"Playback device";"journal"->"Observed activity";"libraries"->"Server libraries";"favorites"->"Jellyfin favorites";"recent"->"Recently added";else->"Media center"})}}
        if(busy)item{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){StakLottie(StakAnimation.Search,Modifier.size(26.dp),loop=true);Text("Checking…",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)}}
        error?.let{item{Text(it,fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=Color(0xFFFF9A9F))}}
        message?.let{item{Text(it,fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted)}}
        when(page){
            "home"->{
                item{ServiceRow("Jellyfin server",live.status,Lucide.MonitorPlay){page="server"}}
                item{ServiceRow("Zurg control center",if(readZurg().endpoint.isBlank())"Configure WebDAV"else "WebDAV · files · independent endpoint",Lucide.Database){page="zurg"}}
                item{ServiceRow("Library delivery status","TorBox → Zurg → Jellyfin",Lucide.Library){page="deliveries"}}
                item{ServiceRow("Playback device",readPlaybackDevice().name,Lucide.Sliders){page="device"}}
                item{ServiceRow("Observed activity","Local journal · privacy controls",Lucide.Tracking){page="journal"}}
            }
            "server"->{
                item{DetailLine("Connection",live.status);Spacer(Modifier.height(8.dp));DetailLine("Endpoint",endpointReachability(vm.settings.jellyfinUrl.orEmpty()));Spacer(Modifier.height(8.dp));DetailLine("Last reconciliation",stamp(journal.lastSync[jellyfinScope()]));Spacer(Modifier.height(12.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){StakButton("Connection",{page="connect"},enabled=!busy);StakButton("Refresh",vm::refreshServer,enabled=!busy)}}
                if(!vm.settings.jellyfinConnected)item{Text("Connect a Jellyfin user to inspect this server.",fontFamily=HankenFont,fontSize=13.sp,color=StakTextMuted)}
                dashboard?.let{server->
                    item{DetailLine("Server",server.name);Spacer(Modifier.height(8.dp));DetailLine("Version",server.version.ifBlank{"Not reported"});Spacer(Modifier.height(8.dp));DetailLine("Library scan",server.scanState);Spacer(Modifier.height(12.dp));StakButton("Request library scan",{scanConfirm=true},enabled=server.admin&&!busy)}
                    server.issues.forEach{issue->item{Text(issue,fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)}}
                    item{Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){StakButton("Libraries",{page="libraries"},compact=true);StakButton("Favorites",{page="favorites"},compact=true);StakButton("Recent",{page="recent"},compact=true)}}
                    item{SectionName("Sessions")}
                    if(server.sessions.isEmpty())item{Text("No accessible sessions were reported.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)}
                    items(server.sessions,key={it.id}){session->SessionControls(session,busy){command,value->vm.command(session,command,value)}}
                }
            }
            "libraries","favorites","recent"->{
                if(page=="libraries")item{Row(verticalAlignment=Alignment.CenterVertically){Text(folderName,Modifier.weight(1f),fontFamily=HankenFont,fontSize=14.sp,color=StakText);StakButton("Parent",vm::parent,compact=true,enabled=!busy)}}
                items(rows,key={it.text("Id").orEmpty()}){row->
                    val isFolder=row.text("Type") in setOf("Series","Season","BoxSet","CollectionFolder","Folder")
                    Column(Modifier.fillMaxWidth().background(Color(0x50111418),androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Row(Modifier.fillMaxWidth().clickable{if(isFolder){page="libraries";vm.browse(row.text("Id"),row.text("Name").orEmpty(),true)}else row.text("Id")?.let{vm.inspect(it)}},verticalAlignment=Alignment.CenterVertically){Icon(if(isFolder)Lucide.Library else Lucide.Film,null,tint=StakTextMuted,modifier=Modifier.size(20.dp));Spacer(Modifier.width(10.dp));Column{Text(row.text("Name") ?: "Unnamed item",fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=14.sp,color=StakText);Text(listOfNotNull(row.text("Type"),row.text("ProductionYear")).joinToString(" · "),fontFamily=SpaceMonoFont,fontSize=9.sp,color=StakTextMuted)}}
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                            if(!isFolder)StakButton("Media info",{row.text("Id")?.let{vm.inspect(it)}},compact=true,enabled=!busy)
                            StakButton(if(row.objectOrNull("UserData")?.get("IsFavorite")?.asBoolean==true)"Unfavorite"else "Favorite",{vm.favorite(row)},compact=true,enabled=!busy,variant=StakButtonVariant.Ghost)
                        }
                    }
                }
                if(page=="libraries"&&total!=null&&rows.size<total!!)item{StakButton("Load more",vm::more,Modifier.fillMaxWidth(),enabled=!busy)}
                if(rows.isEmpty()&&!busy)item{Text("No items were returned for this user.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)}
            }
            "zurg"->{
                item{ZurgSettingsForm(readZurg(),busy,vm::saveZurg)}
                item{DetailLine("Last successful WebDAV check",stamp(checked));Spacer(Modifier.height(8.dp));DetailLine("Response latency",latency?.let{"$it ms"} ?: "Not measured");Spacer(Modifier.height(8.dp));DetailLine("Linux mount","Unverified — WebDAV does not prove mount health")}
                item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){StakButton("Test WebDAV",vm::checkDav,enabled=!busy&&readZurg().endpoint.isNotBlank());StakButton("Browse files",{page="files"},enabled=!busy&&readZurg().endpoint.isNotBlank())}}
                item{StakButton("Restart Zurg",{},enabled=false);Text("Process logs, restarts and mount controls are unavailable: no documented trusted control API/helper is configured. WebDAV needs no helper.",fontFamily=HankenFont,fontSize=11.sp,lineHeight=17.sp,color=StakTextMuted)}
            }
            "files"->{
                item{Column{Text(davPath?.substringAfter(readZurg().endpoint)?.ifBlank{"/"} ?: "/",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){StakButton("Parent",vm::davParent,compact=true,enabled=!busy);StakButton("Refresh",{vm.openDav(davPath)},compact=true,enabled=!busy)}}}
                item{DavFileList(listing,busy,onFolder={vm.openDav(it.url,true)},onFile={fileReport=inferredQuality(it.name,it.size,"WebDAV file listing; no media bytes were downloaded")})}
            }
            "deliveries"->{
                item{Text("Checks are user-initiated. TorBox cache, WebDAV visibility and Jellyfin indexing are separate stages.",fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted)}
                val records=journal.deliveries.filter{it.torboxAccount==torboxScope()}
                if(records.isEmpty())item{Text("Add a release through Find sources to track its route into the library.",fontFamily=HankenFont,fontSize=13.sp,color=StakTextMuted)}
                items(records,key={it.id}){record->DeliveryCard(record,busy,{vm.checkDelivery(record)},{vm.checkDelivery(record,true)})}
            }
            "device"->item{PlaybackDeviceForm(readPlaybackDevice(),busy,vm::device)}
            "journal"->{
                item{Row(verticalAlignment=Alignment.CenterVertically){Text("Record future activity on this device",Modifier.weight(1f),fontFamily=HankenFont,fontSize=13.sp,color=StakText);Switch(journal.recording,{vm.recording(it)},enabled=!busy)};Text("Observed since ${stamp(journal.startedAt)}. Repeated snapshots are deduplicated; progress gains are not treated as watch time.",fontFamily=HankenFont,fontSize=11.sp,lineHeight=17.sp,color=StakTextMuted);StakButton("Clear this account's journal",{clearConfirm=true},enabled=!busy,variant=StakButtonVariant.Ghost)}
                items(journal.events.filter{it.scope==account}.sortedByDescending{it.at},key={it.id}){event->Column(Modifier.padding(vertical=8.dp)){Text(event.title,fontFamily=HankenFont,fontSize=14.sp,color=StakText);Text("${event.kind} · ${stamp(event.at)}",fontFamily=SpaceMonoFont,fontSize=9.sp,color=StakTextMuted);Text(event.source+if(event.detail.isBlank())""else " · ${event.detail}",fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)}}
            }
        }
    }
    report?.let{MediaQualityDialog(it,{vm.quality.value=null})};fileReport?.let{MediaQualityDialog(it,{fileReport=null})}
    if(scanConfirm)GlassAlertDialog(onDismissRequest={scanConfirm=false},title={Text("Scan Jellyfin libraries?")},text={Text("The configured Jellyfin server will scan its libraries. This does not download media or prove indexing has completed.")},confirmButton={StakButton("Request scan",{scanConfirm=false;vm.scan()})},dismissButton={StakButton("Cancel",{scanConfirm=false})})
    if(clearConfirm)GlassAlertDialog(onDismissRequest={clearConfirm=false},title={Text("Clear observed activity?")},text={Text("Only this account's local observation journal is cleared. Jellyfin history and your watchlist stay in place.")},confirmButton={StakButton("Clear journal",{clearConfirm=false;vm.clearJournal()})},dismissButton={StakButton("Cancel",{clearConfirm=false})})
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun SessionControls(session:ServerSession,busy:Boolean,send:(String,Long?)->Unit){
    var seek by remember(session.id,session.positionMs){mutableFloatStateOf(if(session.durationMs>0)session.positionMs.toFloat()/session.durationMs else 0f)}
    var volume by remember(session.id,session.volume){mutableFloatStateOf((session.volume ?: 0).toFloat())}
    Column(Modifier.fillMaxWidth().background(Color(0x50111418),androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(session.title,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=15.sp,color=StakText)
        Text("${session.device} · ${session.client}",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
        Text(session.playMethod ?: session.reason,fontFamily=HankenFont,fontSize=10.sp,color=StakTextMuted)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){StakButton(if(session.paused)"Play" else "Pause",{send("PlayPause",null)},enabled=!busy&&session.canControl,compact=true);StakButton("Stop",{send("Stop",null)},enabled=!busy&&session.canControl,compact=true);StakButton("Previous",{send("PreviousTrack",null)},enabled=!busy&&session.canQueue,compact=true);StakButton("Next",{send("NextTrack",null)},enabled=!busy&&session.canQueue,compact=true)}
        Text("${session.positionMs/60000} / ${session.durationMs/60000} min",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
        Slider(seek,{seek=it},enabled=session.canSeek&&!busy,onValueChangeFinished={send("Seek",(seek*session.durationMs).toLong())})
        Text(if(session.canVolume)"Volume ${volume.toInt()}%" else "Volume control unavailable",fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)
        Slider(volume,{volume=it},valueRange=0f..100f,enabled=session.canVolume&&!busy,onValueChangeFinished={send("SetVolume",volume.toLong())})
        if(!session.canControl)Text(session.reason,fontFamily=HankenFont,fontSize=11.sp,lineHeight=16.sp,color=StakTextMuted)
    }
}
@Composable private fun ZurgSettingsForm(initial:ZurgConnection,busy:Boolean,save:(ZurgConnection)->Unit){
    var value by remember(initial.endpoint,initial.username){mutableStateOf(initial)}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        StakTextField(value.endpoint,{value=value.copy(endpoint=it)},"Zurg WebDAV URL, e.g. https://…/dav/")
        Text(endpointReachability(value.endpoint),fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)
        StakTextField(value.username,{value=value.copy(username=it)},"WebDAV username (optional)")
        StakTextField(value.password,{value=value.copy(password=it)},"WebDAV password",secret=true)
        StakTextField(value.libraryId,{value=value.copy(libraryId=it)},"Jellyfin library ID (optional)")
        StakTextField(value.mountPath,{value=value.copy(mountPath=it)},"Jellyfin Linux mount path (optional)")
        Text("Jellyfin and Zurg may use different hosts. Saving associates this configuration with the currently connected Jellyfin user.",fontFamily=HankenFont,fontSize=11.sp,lineHeight=17.sp,color=StakTextMuted)
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(value.scanWhenVisible,{value=value.copy(scanWhenVisible=it)});Text("Request a scan when a tracked file appears (admin permission required)",fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)}
        StakButton("Save Zurg",{save(value)},enabled=!busy&&value.endpoint.isNotBlank())
    }
}
@Composable private fun DavFileList(listing:DavListing?,busy:Boolean,onFolder:(DavEntry)->Unit,onFile:(DavEntry)->Unit){
    var query by remember{mutableStateOf("")};var mediaOnly by remember{mutableStateOf(true)};var count by remember(listing){mutableIntStateOf(100)}
    val entries=remember(listing,query,mediaOnly){listing?.entries.orEmpty().filter{(!mediaOnly||it.directory||mediaFile(it.name))&&it.name.contains(query,true)}.sortedWith(compareByDescending<DavEntry>{it.directory}.thenBy{it.name.lowercase()})}
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        StakTextField(query,{query=it},"Search this loaded directory")
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(mediaOnly,{mediaOnly=it});Text("Media files",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)}
        Text("${entries.size} loaded entries${if(listing?.truncated==true)" · listing limit reached"else ""}",fontFamily=SpaceMonoFont,fontSize=9.sp,color=StakTextMuted)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=520.dp)){items(entries.take(count),key={it.url}){entry->Row(Modifier.fillMaxWidth().clickable(enabled=!busy){if(entry.directory)onFolder(entry)else onFile(entry)}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(entry.directory)Lucide.Library else Lucide.Film,null,tint=StakTextMuted,modifier=Modifier.size(18.dp));Spacer(Modifier.width(10.dp));Column{Text(entry.name,fontFamily=HankenFont,fontSize=13.sp,color=StakText);Text(if(entry.directory)"Folder"else entry.size?.let{libraryBytes(it)} ?: "Size unavailable",fontFamily=SpaceMonoFont,fontSize=9.sp,color=StakTextMuted)}}}
        }
        if(entries.size>count)StakButton("Show more",{count+=100})
    }
}
@Composable private fun DeliveryCard(record:DeliveryRecord,busy:Boolean,check:()->Unit,scan:()->Unit){
    val labels=mapOf("added" to "Added to TorBox","processing" to "Processing","torbox" to "Available in TorBox","zurg" to "Visible in Zurg","awaiting" to "Awaiting Jellyfin scan","scan" to "Scan requested","jellyfin" to "Available in Jellyfin","failed" to "Failed")
    val steps=listOf("added","processing","torbox","zurg","awaiting","jellyfin");val index=when(record.phase){"scan"->4;else->steps.indexOf(record.phase)}
    Column(Modifier.fillMaxWidth().background(Color(0x50111418),androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(record.media?.name ?: record.releaseName.ifBlank{"TorBox ${record.torboxId}"},fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=14.sp,color=StakText)
        Text(labels[record.phase] ?: "Unverified",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakText)
        StakProgress(if(index>=0)(index+1)/6f else 0f)
        Text(record.note,fontFamily=HankenFont,fontSize=11.sp,lineHeight=17.sp,color=StakTextMuted)
        Text("TorBox #${record.torboxId} · checked ${stamp(record.updatedAt)}",fontFamily=SpaceMonoFont,fontSize=9.sp,color=StakTextMuted)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){StakButton("Check status",check,enabled=!busy,compact=true);StakButton("Request scan",scan,enabled=!busy&&record.zurgPath!=null&&record.phase!="jellyfin",variant=StakButtonVariant.Ghost,compact=true)}
    }
}
@Composable private fun PlaybackDeviceForm(initial:PlaybackDevice,busy:Boolean,save:(PlaybackDevice)->Unit){
    var value by remember{mutableStateOf(initial)};val options=listOf("Unknown" to "Unknown","Supported" to "Supported","Unsupported" to "Unsupported")
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Text("Compatibility hints",Modifier.weight(1f),fontFamily=HankenFont,color=StakText);Switch(value.enabled,{value=value.copy(enabled=it)})}
        StakTextField(value.name,{value=value.copy(name=it)},"Playback setup name")
        Text("Set capabilities for your actual Apple TV, TV and audio setup. Unknown is kept until you configure it.",fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted)
        DiscoverySelect("HDR10",value.hdr10,options,{value=value.copy(hdr10=it)})
        DiscoverySelect("HDR10+",value.hdr10Plus,options,{value=value.copy(hdr10Plus=it)})
        DiscoverySelect("Dolby Vision",value.dolbyVision,options,{value=value.copy(dolbyVision=it)})
        DiscoverySelect("HEVC / H.265",value.hevc,options,{value=value.copy(hevc=it)})
        DiscoverySelect("AV1",value.av1,options,{value=value.copy(av1=it)})
        DiscoverySelect("Dolby Atmos",value.atmos,options,{value=value.copy(atmos=it)})
        DiscoverySelect("DTS audio",value.dts,options,{value=value.copy(dts=it)})
        StakButton("Save device",{save(value)},enabled=!busy&&value.name.isNotBlank())
    }
}
