package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun LibraryAccessDialog(onDismiss:()->Unit){
    val revision by LibraryApprovals.revision.collectAsState();val scope=rememberCoroutineScope()
    var saved by remember{mutableStateOf<ApprovalDocument?>(null)};var error by remember{mutableStateOf<String?>(null)}
    var endpoint by remember{mutableStateOf("")};var token by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    var query by remember{mutableStateOf("")}
    var identifying by remember{mutableStateOf<LibraryApproval?>(null)}
    var status by remember{mutableStateOf<com.google.gson.JsonObject?>(null)};var remove by remember{mutableStateOf<LibraryApproval?>(null)}
    LaunchedEffect(revision){try{saved=withContext(Dispatchers.IO){ApprovalStore().read()};if(endpoint.isBlank()){endpoint=saved!!.connection.url;token=saved!!.connection.token}}catch(_:Exception){error="Saved approvals could not be read. Existing data was not overwritten."}}
    val owner=LocalLifecycleOwner.current
    val latestBusy by rememberUpdatedState(busy)
    val latestConnection by rememberUpdatedState(saved?.connection)
    LaunchedEffect(owner,saved?.connection){
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
            while(true){
                if(!latestBusy&&latestConnection?.enabled==true){
                    try{status=LibraryApprovals.sync()}
                    catch(c:CancellationException){throw c}
                    catch(e:Exception){error=libraryFilterError(e)}
                }
                delay(30000)
            }
        }
    }
    fun work(action:suspend()->Unit){if(busy)return;busy=true;error=null;scope.launch(Dispatchers.Main.immediate){try{action()}catch(c:CancellationException){throw c}catch(e:Exception){error=libraryFilterError(e)}finally{busy=false;LibraryApprovals.revision.value++}}}
    if(identifying==null)GlassAlertDialog(onDismissRequest=onDismiss,title={Text("Library organization",fontFamily=DotoFont,fontWeight=FontWeight.Bold,fontSize=20.sp)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        error?.let{Text(it,fontFamily=HankenFont,fontSize=12.sp,color=StakTextSoft)}
        Text("Only STAK-approved torrents",fontFamily=HankenFont,fontSize=14.sp,color=StakText)
        Text("Jellyfin must use the filter’s Movies and Shows folders. Saving approvals alone does not filter your existing library.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
        StakTextField(endpoint,{endpoint=it},"STAK Server / library URL")
        StakTextField(token,{token=it},"Library organization token",secret=true)
        StakButton(if(busy)"Checking…"else "Connect and sync",{work{
            approvalEndpoint(endpoint)
            val cleanToken=filterToken(token)
            withContext(Dispatchers.IO){ApprovalStore().update{it.copy(connection=LibraryFilterConnection(endpoint.trim().trimEnd('/'),cleanToken,true))}}
            status=LibraryApprovals.sync();token=cleanToken
        }},enabled=!busy&&saved!=null)
        status?.let{remote->
            Text("Connected to STAK Server",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextSoft)
            if(remote.get("stak_import_warning")?.asBoolean==true)Text("Earlier additions could not be imported. Existing approvals are synced.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
            Text(if(remote.get("jellyfin_paths_verified")?.asBoolean==true)"Jellyfin paths verified"else "Jellyfin folder setup required",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextSoft)
            if(remote.get("source_available")?.asBoolean!=true)Text("Zurg mount unavailable",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
        }
        if(saved?.connection?.enabled==true)StakButton("Disconnect library connection",{work{withContext(Dispatchers.IO){ApprovalStore().update{it.copy(connection=it.connection.copy(enabled=false))}};status=null}},enabled=!busy,variant=StakButtonVariant.Ghost,compact=true)
        val entries=saved?.entries.orEmpty().filter{it.scope==torboxScope()}.sortedByDescending{it.at}
        Text("${entries.count{it.approved}} approved",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextSoft)
        if(entries.size>10)StakTextField(query,{query=it},"Search approvals",height=44)
        entries.filter{it.name.contains(query,true)||it.id==query||it.hash?.contains(query,true)==true}.take(100).forEach{row->Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(row.name.ifBlank{"Torrent ${row.id}"},fontFamily=HankenFont,fontSize=13.sp,color=StakText)
            Text(if(!row.approved&&row.state=="revoked")"Removed from filtered library"else if(row.sentTo!=approvalConnectionId(saved!!.connection))"Saved · waiting for filter sync"else when(row.state){"published"->if(status?.get("layout")?.asString=="organized")"Published · organized"else "Published · whole torrent";"processing"->"Processing in TorBox";"needs_type"->"Choose media type";"duplicate"->"Already included";"revoked"->"Removed from filtered library";else->row.note.ifBlank{"Waiting for source mapping"}},fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(2.dp)){
                if(status?.get("server_protocol")?.asString=="stak-server/1"&&row.hash!=null&&row.type!="unknown")TextButton(onClick={identifying=row},enabled=!busy){Text("Identify",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextSoft)}
                if(row.approved){listOf("movie" to "Movies","series" to "Series").forEach{(value,label)->TextButton(onClick={work{withContext(Dispatchers.IO){ApprovalStore().decide(row,true,value)};status=LibraryApprovals.sync()}},enabled=!busy){Text(if(row.type==value)"• $label"else label,fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextSoft)}};TextButton(onClick={remove=row},enabled=!busy){Text("Remove",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)}}
                else TextButton(onClick={work{withContext(Dispatchers.IO){ApprovalStore().decide(row,true)};status=LibraryApprovals.sync()}},enabled=!busy){Text("Approve again",color=StakTextSoft)}
            }
            HorizontalDivider(color=androidx.compose.ui.graphics.Color(0x20FFFFFF))
        }}
        if(entries.size>100)Text("Showing up to 100 approvals. Search to find earlier entries.",fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)
    }},confirmButton={StakButton("Close",onDismiss,enabled=!busy)})
    remove?.let{row->GlassAlertDialog({remove=null},title={Text("Remove library approval?")},text={Text("Only the filter’s links are removed after sync. TorBox content and viewing history are retained.",fontFamily=HankenFont,fontSize=13.sp,color=StakTextSoft)},confirmButton={StakButton("Remove approval",{remove=null;work{withContext(Dispatchers.IO){ApprovalStore().decide(row,false)};status=LibraryApprovals.sync()}})},dismissButton={StakButton("Cancel",{remove=null})})}
    identifying?.let{row->
        var title by remember(row.id){mutableStateOf(row.media?.title?:row.name)}
        var imdb by remember(row.id){mutableStateOf(row.media?.imdb.orEmpty())}
        var year by remember(row.id){mutableStateOf(row.media?.year?.toString().orEmpty())}
        val valid=title.isNotBlank()&&imdb.trim().matches(Regex("tt\\d{3,12}"))&&(year.isBlank()||year.toIntOrNull()?.let{it in 1870..2200}==true)
        GlassAlertDialog({identifying=null},title={Text("Identify title",fontFamily=DotoFont,fontWeight=FontWeight.Bold)},text={
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Use the exact IMDb ID for this ${if(row.type=="series")"series" else "movie"}. Existing approval stays unchanged.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextSoft)
                StakTextField(title,{title=it},"Title",height=44)
                StakTextField(imdb,{imdb=it},"IMDb ID · tt1234567",height=44)
                StakTextField(year,{year=it},"Year · optional",height=44,keyboardType=androidx.compose.ui.text.input.KeyboardType.Number)
            }
        },confirmButton={StakButton("Save identity",{
            val media=ApprovalMediaIdentity(title.trim().take(160),imdb.trim(),year=year.toIntOrNull())
            identifying=null
            work{
                withContext(Dispatchers.IO){ApprovalStore().update{doc->doc.copy(entries=doc.entries.map{current->
                    if(current.scope==row.scope&&current.id==row.id&&current.hash==row.hash)current.copy(media=media) else current
                })}}
                status=LibraryApprovals.sync()
            }
        },enabled=valid&&!busy)},dismissButton={StakButton("Cancel",{identifying=null})})
    }

}
