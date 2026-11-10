@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.stak.nativeapp.ui.screens
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import com.skydoves.flexible.bottomsheet.material3.FlexibleBottomSheet
import com.skydoves.flexible.core.*
import dev.chrisbanes.haze.HazeState
import app.stak.nativeapp.ui.components.recordGlassSource

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable fun FindSourcesSheet(media:MediaItem,episodes:List<Episode>,viewModel:TitleViewModel,onDismiss:()->Unit,onOpenSettings:()->Unit,onOpenLibrary:()->Unit) {
    var qualityReport by remember{mutableStateOf<QualityReport?>(null)}
    val all by viewModel.sources.collectAsState();val loading by viewModel.sourceLoading.collectAsState();val error by viewModel.sourceError.collectAsState()
    val remaining by viewModel.remainingSources.collectAsState();val checkedCount by viewModel.cacheProgress.collectAsState()
    val cached by viewModel.cached.collectAsState();val checking by viewModel.cacheLoading.collectAsState();val cacheError by viewModel.cacheError.collectAsState()
    val season by viewModel.season.collectAsState();val episode by viewModel.episode.collectAsState();val packs by viewModel.packMode.collectAsState()
    val added by viewModel.added.collectAsState();val adding by viewModel.adding.collectAsState();val addError by viewModel.addError.collectAsState()
    val glyphSignals=LocalGlyphSignals.current
    var acknowledgedAdds by remember(media.mediaKey){mutableStateOf(added.toMap())}
    LaunchedEffect(added){
        if(added.any{(source,id)->acknowledgedAdds[source]!=id})glyphSignals?.added()
        acknowledgedAdds=added.toMap()
    }
    var filters by remember(media.mediaKey){mutableStateOf(SourceFilters())};var advanced by remember{mutableStateOf(false)};var showAll by remember(season){mutableStateOf(false)}
    var fileId by remember{mutableStateOf<String?>(null)};var files by remember{mutableStateOf<List<TorBoxFile>>(emptyList())};var fileBusy by remember{mutableStateOf(false)};var fileError by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope();val context=LocalContext.current
    val key=NativeRuntime.settings.torboxKey.orEmpty()
    val shown=remember(all,cached,filters,season,packs,showAll,media.isSeries){filteredSources(all,cached,filters,season,packs&&media.isSeries,showAll)}
    LaunchedEffect(media.mediaKey,season,episode?.episode,packs){viewModel.loadSources(media.type,media.mediaKey)}
    fun refresh(){viewModel.loadSources(media.type,media.mediaKey,force=true)}
    val sourceWarnings by viewModel.sourceWarnings.collectAsState()
    var manageSources by remember{mutableStateOf(false)}
    val activeAddons=remember(manageSources){(StreamAddonStore(NativeRuntime.settings).all().filter{it.enabled}.map{it.name}+TorznabStore(NativeRuntime.settings).all().filter{it.enabled}.map{it.name}).joinToString(" · ")}
    val sheetLayer=androidx.compose.ui.graphics.rememberGraphicsLayer()
    val sheetSource=remember(sheetLayer){app.stak.nativeapp.ui.components.GlassSource(sheetLayer,crossWindow=true,hazeState=HazeState())}
    val reduced=LocalReducedMotion.current
    val flexibleState=rememberFlexibleBottomSheetState(isModal=true,containSystemBars=true,skipSlightlyExpanded=true,flexibleSheetSize=FlexibleSheetSize(fullyExpanded=.94f,intermediatelyExpanded=.64f),animateSpec=if(reduced)snap() else spring(dampingRatio=.9f,stiffness=420f))
    LaunchedEffect(manageSources){if(manageSources)flexibleState.fullyExpand()}
    // The library's modal surface spans the window even at the medium anchor.
    // Give the list its actual visible viewport so accessibility/programmatic scrolling works too.
    val safeInsets=WindowInsets.systemBars.asPaddingValues()
    val panelHeight=(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp*(if(flexibleState.targetValue==FlexibleSheetValue.FullyExpanded).94f else .64f)-32.dp-safeInsets.calculateTopPadding()-safeInsets.calculateBottomPadding()).coerceAtLeast(120.dp)
    androidx.compose.runtime.CompositionLocalProvider(app.stak.nativeapp.ui.components.LocalGlassSource provides sheetSource) {
    FlexibleBottomSheet(onDismissRequest=onDismiss,sheetState=flexibleState,containerColor=Color(0xFF101010),contentColor=StakText,shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp),dragHandle={
        val expanded=flexibleState.targetValue==FlexibleSheetValue.FullyExpanded
        Row(Modifier.fillMaxWidth().height(32.dp).clickable{scope.launch{if(expanded)flexibleState.intermediatelyExpand() else flexibleState.fullyExpand()}},horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.width(36.dp).height(3.dp).clip(CircleShape).background(Color(0x55FFFFFF)))
            Spacer(Modifier.width(8.dp))
            Icon(Lucide.ChevronDown,if(expanded)"Collapse sources" else "Expand sources",tint=StakTextMuted,modifier=Modifier.size(14.dp).graphicsLayer{rotationZ=if(expanded)0f else 180f})
        }
    }) {
        app.stak.nativeapp.ui.components.SmoothDialogWindow()
        val sheetContentView=androidx.compose.ui.platform.LocalView.current
        if(manageSources)Box(Modifier.fillMaxWidth().height(panelHeight)){SourcesSettings(onBack={manageSources=false;refresh()})} else LazyColumn(Modifier.fillMaxWidth().height(panelHeight).recordGlassSource(sheetSource,sheetContentView).background(Color(0xFF101010)),contentPadding=PaddingValues(14.dp,6.dp,14.dp,32.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    AsyncImage(media.poster,null,contentScale=ContentScale.Crop,modifier=Modifier.width(34.dp).height(50.dp).clip(RoundedCornerShape(7.dp)).background(StakSurface))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(media.name,fontFamily=DotoFont,fontWeight=FontWeight.Black,fontSize=17.sp,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(listOfNotNull(media.year,if(media.isSeries)"Series" else "Movie",media.runtime).joinToString(" · "),fontFamily=SpaceMonoFont,fontSize=11.sp,lineHeight=15.sp,color=StakTextMuted)

                    }
                    Row {
                        SourceCircle(Lucide.X,"Close",onDismiss)
                        SourceCircle(Lucide.Refresh,"Refresh torrent sources",::refresh,!loading&&!checking)
                    }
                }
            }
            if(media.isSeries)item {
                val seasons=(viewModel.detail.value?.seasons.orEmpty()+episodes.map{it.season}+season).distinct().sorted()
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    SourceSelect("Season $season",seasons.map{it.toString() to "Season $it"},{viewModel.selectSeason(it.toInt())},Modifier.weight(1f),true)
                }
                Spacer(Modifier.height(6.dp))
                StakTabs(listOf("packs" to "Season packs","episode" to "Episode"),if(packs)"packs"else "episode",{viewModel.setPackMode(it=="packs")})
                if(!packs){Spacer(Modifier.height(8.dp));SourceSelect("E${episode?.episode ?: 1}",episodes.filter{it.season==season}.map{it.episode.toString() to "E${it.episode} · ${it.name}"},{number->episodes.firstOrNull{it.season==season&&it.episode.toString()==number}?.let{viewModel.selectEpisode(it)}},Modifier.fillMaxWidth(),true)}
            }
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    listOf("2160p" to "4K","1080p" to "1080P").forEach{(value,label)->SourceChip("$label ${all.count{it.resolution==value&&(!filters.cachedOnly||it.infoHash in cached)}}",filters.resolution==value,{filters=filters.copy(resolution=if(filters.resolution==value)"All" else value)},Lucide.Monitor)}
                    Spacer(Modifier.weight(1f));SourceCircle(Lucide.Radio,"Source providers",{manageSources=true});SourceCircle(Lucide.Sliders,"Filters and sorting",{advanced=true})
                }
                Spacer(Modifier.height(8.dp));Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    SourceChip("HDR",filters.hdr,{filters=filters.copy(hdr=!filters.hdr)},Lucide.Sun,Modifier.weight(1f))
                    SourceChip("ATMOS",filters.atmos,{filters=filters.copy(atmos=!filters.atmos)},Lucide.Volume,Modifier.weight(1f))
                    SourceChip("WEB-DL",filters.webdl,{filters=filters.copy(webdl=!filters.webdl)},Lucide.Globe,Modifier.weight(1.25f))
                    SourceChip("REMUX",filters.remux,{filters=filters.copy(remux=!filters.remux)},Lucide.Layers,Modifier.weight(1.2f))
                }
                Spacer(Modifier.height(8.dp));Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("${shown.size} ${if(filters.cachedOnly)"cached " else ""}releases",fontFamily=SpaceMonoFont,fontSize=11.sp,lineHeight=16.sp,color=StakTextMuted,modifier=Modifier.weight(1f))
                    SourceSelect(when(filters.sort){"size"->"Largest size";else->"Quality first"},listOf("best" to "Quality first","size" to "Largest size"),{filters=filters.copy(sort=it)})
                }
            }
            if(key.isBlank())item {Text("Set your TorBox API key in Settings to check cache and add torrents.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted);StakButton("Configure TorBox",{onDismiss();onOpenSettings()})}
            if(loading||checking)item {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){StakLottie(StakAnimation.Search,Modifier.size(30.dp),loop=true);Text(if(loading)"Searching torrent sources…" else "Checking TorBox cache…",fontFamily=SpaceMonoFont,fontSize=11.sp,lineHeight=16.sp,color=StakTextMuted)}}
            (error ?: cacheError)?.let{message->item {Text(message,fontFamily=HankenFont,fontSize=12.sp,color=Color(0xFFFFB86B));StakButton("Retry search",::refresh,enabled=!loading&&!checking)}}
            if(remaining>0&&!loading)item{StakButton("Check next ${minOf(8,remaining)} ${if(remaining==1)"release"else "releases"}",{viewModel.checkMoreSources()},enabled=!checking,compact=true);Text("$remaining ${if(remaining==1)"release needs"else "releases need"} metadata checks",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)}
            sourceWarnings.forEach{warning->item{Text(warning,fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=Color(0xFFFFB86B))}}
            addError?.let{message->item{Text(message,fontFamily=HankenFont,fontSize=12.sp,color=Color(0xFFFF7A80))}}
            if(!loading&&!checking&&shown.isEmpty())item {
                Text(if(packs&&media.isSeries&&!showAll)"No matching season pack" else if(remaining>0)"No cached matches yet" else if(filters.cachedOnly)"No cached releases" else "No releases match your filters",fontFamily=DotoFont,fontWeight=FontWeight.Bold,fontSize=21.sp,color=StakText)
                Text("Refresh cache checks, search individual episodes, or adjust the source filters.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
                StakButton("Clear source filters",{filters=SourceFilters(cachedOnly=filters.cachedOnly);showAll=true})
            }
            itemsIndexed(shown,key={index,source->source.sourceIdentity}){index,source->
                val torrentId=added[source.sourceIdentity]
                SourceReleaseCard(source,source.infoHash in cached,torrentId!=null,adding==source.sourceIdentity,onAdd={viewModel.addSource(source)},onFiles={
                    if(torrentId!=null){fileId=torrentId;files=emptyList();fileBusy=true;fileError=null;scope.launch{try{files=DirectServices.torrents(key).firstOrNull{it.id==torrentId}?.files.orEmpty()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){fileError="Could not load the added release. Open your TorBox library or retry."}finally{fileBusy=false}}}

                },canAdd=key.isNotBlank()&&source.canImport&&source.infoHash in cached,suggested=index==0&&filters.sort=="best",season=if(media.isSeries)season else null,episodeCount=episodes.count{it.season==season},onInspect={qualityReport=sourceQualityReport(source.copy(cached=source.infoHash in cached))})
            }
            if(media.isSeries&&packs)item {
                val hidden=filteredSources(all,cached,filters,season,false,true).count{!seasonPackMatches(it.name,season)}
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(if(showAll)"All matching releases" else "Show individual episodes ($hidden)",fontFamily=SpaceMonoFont,fontSize=11.sp,lineHeight=16.sp,color=StakTextMuted,modifier=Modifier.weight(1f))
                    Text(if(showAll)"Packs only" else "Show all",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakText,modifier=Modifier.clickable{showAll=!showAll}.padding(vertical=10.dp))
                }
            }
        }
    }
    qualityReport?.let{val comparisons=remember(all,cached){all.take(200).map{release->sourceQualityReport(release.copy(cached=release.infoHash in cached))}};MediaQualityDialog(it,{qualityReport=null},comparisons)}
    if(advanced)GlassAlertDialog(onDismissRequest={advanced=false},title={Text("Source filters")},text={Column{
        StakChip("Dolby Vision",filters.dv,{filters=filters.copy(dv=!filters.dv)})
        StakChip("HUN first",filters.preferHun,{filters=filters.copy(preferHun=!filters.preferHun)})
        Spacer(Modifier.height(8.dp));DiscoverySelect("Maximum source size",filters.maxGb?.toString().orEmpty(),listOf("" to "Any size")+listOf(5,10,20,50).map{it.toString() to "≤ $it GB"},{filters=filters.copy(maxGb=it.toIntOrNull())})
        Text("TorBox cached only",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakText)
        Text("Quality badges are inferred from release names. Cache badges require a successful TorBox check.",fontFamily=HankenFont,fontSize=12.sp)
    }},confirmButton={StakButton("Done",{advanced=false})},dismissButton={StakButton("Reset",{filters=SourceFilters()})})
    fileId?.let{id->GlassAlertDialog(onDismissRequest={if(!fileBusy)fileId=null},title={Text("Choose a file")},text={Column{
        if(fileBusy)Text("Preparing files…")
        fileError?.let{Text(it)}
        if(!fileBusy&&files.isEmpty())Text("TorBox is preparing this release. Check the library again shortly.")
        files.forEach{file->Text(file.name,fontFamily=HankenFont,fontSize=12.sp,color=StakText,modifier=Modifier.fillMaxWidth().padding(vertical=12.dp))}
    }},confirmButton={StakButton("Open TorBox library",{fileId=null;onDismiss();onOpenLibrary()})},dismissButton={StakButton("Close",{fileId=null},enabled=!fileBusy)})}
    }
}

@Composable private fun SourceCircle(icon:ImageVector,label:String,onClick:()->Unit,enabled:Boolean=true) {
    IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(44.dp)) { Icon(icon,label,tint=if(enabled)StakText else StakTextMuted,modifier=Modifier.size(18.dp)) }
}

@Composable private fun SourceChip(label:String,selected:Boolean,onClick:()->Unit,icon:ImageVector?=null,modifier:Modifier=Modifier) {
    if(label in listOf("HDR","ATMOS","WEB-DL","REMUX")){
        Box(modifier.heightIn(min=44.dp).clip(RoundedCornerShape(9.dp)).then(if(selected)Modifier.border(1.dp,StakTextSoft,RoundedCornerShape(9.dp))else Modifier).semantics{this.selected=selected}.clickable(onClick=onClick).padding(horizontal=2.dp),contentAlignment=Alignment.Center){
            FormatBadge(if(label=="ATMOS")"Atmos"else label)
            if(selected)Box(Modifier.align(Alignment.BottomCenter).padding(bottom=2.dp).size(3.dp).background(StakAccent,CircleShape))
        }
    }else StakChip(label,selected,onClick,modifier,leading=if(icon==null)null else {{Icon(icon,null,tint=StakTextMuted,modifier=Modifier.size(14.dp))}})
}

@Composable private fun SourceSelect(label:String,options:List<Pair<String,String>>,onSelect:(String)->Unit,modifier:Modifier=Modifier,outlined:Boolean=false) {
    var open by remember{mutableStateOf(false)}
    Box(modifier) {
        Row(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp,Color.White.copy(alpha=.30f),RoundedCornerShape(10.dp)).then(if(outlined)Modifier.fillMaxWidth() else Modifier).clickable{open=true}.padding(horizontal=if(outlined)14.dp else 4.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(label,fontFamily=HankenFont,fontWeight=if(outlined)FontWeight.Bold else FontWeight.Normal,fontSize=if(outlined)14.sp else 12.sp,color=StakText,modifier=if(outlined)Modifier.weight(1f) else Modifier)
            Icon(Lucide.ChevronDown,null,tint=StakTextMuted,modifier=Modifier.size(13.dp))
        }
        GlassDropdownMenu(expanded=open,onDismissRequest={open=false}){options.forEach{(value,text)->DropdownMenuItem(text={Text(text)},onClick={open=false;onSelect(value)})}}
    }
}

@Composable private fun SourceTag(label:String) {
    Text(label,fontFamily=SpaceMonoFont,fontSize=11.sp,lineHeight=13.sp,color=StakTextMuted,modifier=Modifier.border(1.dp,Color(0x24FFFFFF),RoundedCornerShape(7.dp)).padding(horizontal=7.dp,vertical=4.dp))
}

@Composable internal fun SourceReleaseCard(source:SourceRelease,cached:Boolean,added:Boolean,adding:Boolean,onAdd:()->Unit,onFiles:()->Unit,canAdd:Boolean,suggested:Boolean,season:Int?,episodeCount:Int,onInspect:()->Unit) {
    var expanded by remember(source.sourceIdentity){mutableStateOf(false)}
    val pack=season!=null&&seasonPackMatches(source.name,season)
    val bytes=if(pack)source.torrentSizeBytes else source.sizeBytes
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF151515)).border(1.dp,if(suggested)Color(0xFF797979)else Color(0x2AFFFFFF),RoundedCornerShape(14.dp)).clickable{expanded=!expanded}.padding(13.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Text(sourceQualityHeading(source),Modifier.weight(1f),fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=16.sp,color=StakText)
            Text(bytes?.let{"%.2f GB".format(java.util.Locale.US,it/1073741824.0)}?:"Size unknown",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
            releaseBadges(source).forEach{FormatBadge(it)}
        }
        Text(source.name.replace('_',' '),fontFamily=HankenFont,fontSize=12.sp,lineHeight=17.sp,color=StakTextSoft,maxLines=2,overflow=TextOverflow.Ellipsis)
        if(pack)Text("Season $season pack",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
        if(suggested)Text("Recommended",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
        NothingExpand(expanded){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(source.name,fontFamily=SpaceMonoFont,fontSize=11.sp,lineHeight=17.sp,color=StakText)
            source.indexer?.let{Text(it,fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)}
            StakButton("Release details",onInspect,compact=true)
        }}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Icon(Lucide.Zap,null,tint=if(cached)Color(0xFFB9D3C6)else StakTextMuted,modifier=Modifier.size(14.dp))
            Text(if(cached)"Cached on TorBox"else "Cache unconfirmed",Modifier.weight(1f),fontFamily=SpaceMonoFont,fontSize=10.sp,lineHeight=15.sp,color=if(cached)Color(0xFFB9D3C6)else StakTextMuted)
            if(adding)StakLottie(StakAnimation.Search,Modifier.size(24.dp),loop=true)
            StakButton(if(added)"Added · Files"else if(adding)"Adding…"else "Add",if(added)onFiles else onAdd,compact=true,enabled=added||(!adding&&canAdd),icon=if(added)Lucide.Check else Lucide.Plus)
        }
    }
}
