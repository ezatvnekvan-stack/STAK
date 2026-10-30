package app.stak.nativeapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

@Composable fun MediaCenterScreen(onBack:()->Unit,connections:ConnectionSettingsController,viewModel:MediaCenterViewModel=androidx.lifecycle.viewmodel.compose.viewModel()){
    ZurgBrowserScreen(onBack,viewModel)
}
@Composable fun ZurgBrowserScreen(onBack:()->Unit,vm:MediaCenterViewModel){
    val busy by vm.busy.collectAsState();val error by vm.error.collectAsState();val message by vm.message.collectAsState()
    val listing by vm.dav.collectAsState();val path by vm.davPath.collectAsState();val torrent by vm.davTorrent.collectAsState()
    var access by remember{mutableStateOf(false)}
    var configure by remember{mutableStateOf(false)};var query by remember{mutableStateOf("")};var selected by remember{mutableStateOf<DavEntry?>(null)}
    var remove by remember{mutableStateOf<TorBoxTorrent?>(null)};var quality by remember{mutableStateOf<QualityReport?>(null)}
    val config=readZurg(vm.settings);val clipboard=LocalClipboardManager.current;val scroll=rememberLazyListState()
    LaunchedEffect(config.endpoint){if(config.endpoint.isNotBlank()&&listing==null&&!busy)vm.openDav()}
    LaunchedEffect(path){query="";scroll.scrollToItem(0)}
    BackHandler(enabled=path!=null&&path!=config.endpoint){vm.davParent()}
    val shown=remember(listing,query){listing?.entries.orEmpty().filter{it.name.contains(query,true)}.sortedWith(compareByDescending<DavEntry>{it.directory}.thenBy{it.name.lowercase()})}
    LazyColumn(Modifier.fillMaxSize(),state=scroll,contentPadding=PaddingValues(16.dp,20.dp,16.dp,36.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item(key="header"){Row(verticalAlignment=Alignment.CenterVertically){StakIconButton(onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back",tint=StakText,modifier=Modifier.size(19.dp))};Text("Zurg",Modifier.weight(1f),fontFamily=DotoFont,fontSize=25.sp,color=StakText);IconButton(onClick={configure=true},enabled=!busy){Icon(Lucide.Settings,"Zurg connection",tint=StakText,modifier=Modifier.size(19.dp))};IconButton(onClick={vm.openDav(path)},enabled=!busy&&config.endpoint.isNotBlank()){Icon(Lucide.Refresh,"Refresh Zurg",tint=StakText,modifier=Modifier.size(18.dp))}}}
        item(key="library-access"){StakButton("Library access",{access=true},compact=true)}
        if(config.endpoint.isBlank())item{Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Connect Zurg to browse your files.",fontFamily=HankenFont,fontSize=14.sp,color=StakTextMuted);StakButton("Connect Zurg",{configure=true})}}
        else {
            item{Row(verticalAlignment=Alignment.CenterVertically){StakButton("Parent",vm::davParent,enabled=!busy&&path!=config.endpoint,compact=true,variant=StakButtonVariant.Ghost);Text(path?.removePrefix(config.endpoint)?.ifBlank{"/"} ?: "/",Modifier.weight(1f),fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted,maxLines=2,overflow=TextOverflow.Ellipsis)};StakTextField(query,{query=it},"Search this folder")}
            if(busy)item{StakLoadingProgress()}
            error?.let{item{Text(it,fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted);StakButton("Retry",{vm.openDav(path)},compact=true)}}
            message?.let{item{Text(it,fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)}}
            item{Text("${shown.size} items${if(listing?.truncated==true)" · listing limit reached"else ""}",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)}
            items(shown,key={it.url},contentType={it.directory}){entry->Row(Modifier.fillMaxWidth().clickable(enabled=!busy){if(entry.directory)vm.openDav(entry.url,true)else{selected=entry;vm.resetDavTorrent()}}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Icon(if(entry.directory)Lucide.Library else Lucide.Film,null,tint=StakTextMuted,modifier=Modifier.size(21.dp));Column(Modifier.weight(1f)){Text(entry.name,fontFamily=HankenFont,fontSize=14.sp,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis);Text(if(entry.directory)"Folder"else entry.size?.let{libraryBytes(it)} ?: "Size unavailable",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)};Icon(Lucide.ChevronRight,null,tint=StakTextMuted,modifier=Modifier.size(15.dp))}}
        }
    }
    if(access)LibraryAccessDialog{access=false}
    if(configure)ZurgConnectionDialog(config,busy,{configure=false}){value->vm.connectDav(value);configure=false}
    selected?.let{entry->GlassAlertDialog(onDismissRequest={selected=null},title={Text(entry.name,fontFamily=HankenFont,fontSize=16.sp,color=StakText)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(entry.relativePath,fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted);Text(entry.size?.let{libraryBytes(it)} ?: "Size unavailable",fontFamily=HankenFont,fontSize=13.sp,color=StakText)
        StakButton("Copy path",{clipboard.setText(AnnotatedString(entry.relativePath))},variant=StakButtonVariant.Ghost,compact=true)
        if(mediaFile(entry.name))StakButton("Media info",{selected=null;quality=inferredQuality(entry.name,entry.size,"WebDAV file listing")},variant=StakButtonVariant.Ghost,compact=true)
        if(torrent==null)StakButton("Manage in TorBox",{vm.findDavTorrent(entry)},enabled=!busy&&!vm.settings.torboxKey.isNullOrBlank(),compact=true)
        torrent?.let{owner->Text(owner.name,fontFamily=HankenFont,fontSize=13.sp,color=StakText);Text("${owner.files.size} files · ${libraryBytes(owner.size)}",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted);StakButton("Remove from TorBox",{remove=owner},variant=StakButtonVariant.Destructive,enabled=!busy,compact=true)}
        if(busy)StakLoadingProgress();error?.let{Text(it,fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)};message?.let{Text(it,fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)}
    }},confirmButton={StakButton("Close",{selected=null})})}
    remove?.let{owner->GlassAlertDialog(onDismissRequest={remove=null},title={Text("Remove torrent?")},text={Text("Remove ${owner.name} and all ${owner.files.size} files from TorBox? This affects the whole torrent, including other episodes in a pack.",fontFamily=HankenFont,fontSize=13.sp,color=StakText)},confirmButton={StakButton("Remove",{remove=null;selected=null;vm.removeDavTorrent(owner)},variant=StakButtonVariant.Destructive)},dismissButton={StakButton("Cancel",{remove=null})})}
    quality?.let{MediaQualityDialog(it,{quality=null})}
}
@Composable private fun ZurgConnectionDialog(initial:ZurgConnection,busy:Boolean,dismiss:()->Unit,save:(ZurgConnection)->Unit){
    var value by remember{mutableStateOf(initial)}
    GlassAlertDialog(onDismissRequest=dismiss,title={Text("Zurg connection",fontFamily=DotoFont,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold,fontSize=18.sp,color=StakText)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        StakTextField(value.endpoint,{value=value.copy(endpoint=it)},"WebDAV URL")
        StakTextField(value.username,{value=value.copy(username=it)},"Username (optional)")
        StakTextField(value.password,{value=value.copy(password=it)},"Password",secret=true)
    }},confirmButton={StakButton("Save",{save(value)},enabled=!busy&&value.endpoint.isNotBlank())},dismissButton={StakButton("Cancel",dismiss)})
}
