@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.stak.nativeapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

data class SeriesCenterState(val identity:String="",val busy:Boolean=false,val snapshot:SeriesCenterSnapshot=SeriesCenterSnapshot(),val error:String?=null)
class SeriesCenterViewModel(private val loader:suspend(MediaItem)->SeriesCenterSnapshot={SeriesCenterRepository().load(it)}):ViewModel(){
    private val mutable=MutableStateFlow(SeriesCenterState());val state:StateFlow<SeriesCenterState> = mutable
    private var job:Job?=null
    fun load(account:String,media:MediaItem,force:Boolean=false){
        val identity="$account:${media.mediaKey}"
        if(!force&&mutable.value.identity==identity)return
        job?.cancel();mutable.value=SeriesCenterState(identity,busy=true)
        job=viewModelScope.launch{try{val result=loader(media);ensureActive();mutable.value=SeriesCenterState(identity,snapshot=result)}catch(c:CancellationException){throw c}catch(_:Exception){mutable.value=SeriesCenterState(identity,error="Series could not update. Retry to verify availability.")}}
    }
    fun clear(){job?.cancel();mutable.value=SeriesCenterState()}
}

@Composable fun SeriesCenterScreen(onLibrary:()->Unit,onTorBox:()->Unit,onSettings:()->Unit,
    onMediaCenter:()->Unit={},onOpenTitle:(MediaItem)->Unit={},knownSeries:List<MediaItem> = emptyList(),
    onSourceSettings:()->Unit=onSettings,onRefreshLibrary:()->Unit={},initialSeries:MediaItem?=null,onExit:()->Unit={},refreshEnabled:Boolean=true,
    libraryBusy:Boolean=false,libraryIssue:String?=null,
    model:SeriesCenterViewModel=androidx.lifecycle.viewmodel.compose.viewModel()){
    val revision by WatchRules.revision.collectAsState()
    val episodeRows by WatchRules.episodeRows.collectAsState()
    val busy by WatchRules.refreshing.collectAsState();val issue by WatchRules.issue.collectAsState()
    val doc=remember(revision){WatchRules.store.read()}
    val connected=NativeRuntime.settings.jellyfinConnected
    val account=(if(connected)jellyfinScope()else "disconnected")+":"+doc.connection.hashCode()
    val entries=remember(knownSeries,doc.rules,initialSeries){seriesCenterEntries(knownSeries+listOfNotNull(initialSeries),doc.rules)}
    var selected by rememberSaveable(account,initialSeries?.mediaKey){mutableStateOf(initialSeries?.let{request->entries.firstOrNull{sameSeries(it.media,request)}?.key})}
    val entry=entries.firstOrNull{it.key==selected}
    val state by model.state.collectAsState()
    val owner=LocalLifecycleOwner.current;val scope=rememberCoroutineScope()
    val paired=doc.connection.enabled&&doc.connection.url.isNotBlank()&&doc.connection.token.isNotBlank()
    var query by rememberSaveable{mutableStateOf("")};var filter by rememberSaveable{mutableStateOf("all")}
    var managing by remember(account,selected){mutableStateOf(false)}
    var editing by remember(account,selected){mutableStateOf(false)}
    var sourceEpisode by remember(account,selected){mutableStateOf<Episode?>(null)}
    LaunchedEffect(account,refreshEnabled){if(refreshEnabled)onRefreshLibrary()}
    LaunchedEffect(account,paired,refreshEnabled,owner){if(paired&&refreshEnabled)owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){while(true){WatchRules.refresh();delay(60000)}}}
    LaunchedEffect(account,entry?.key){if(entry!=null)model.load(account,entry.media)else model.clear()}
    fun back(){if(initialSeries!=null)onExit()else selected=null}
    BackHandler(entry!=null){back()}
    if(entry==null){
        LibraryRefresh(busy||libraryBusy,{onRefreshLibrary();scope.launch{if(paired)WatchRules.refresh()}},true){
            SeriesCenterList(entries,doc.statuses.associateBy{it.ruleId},query,filter,issue,connected,
                onQuery={query=it},onFilter={filter=it},onSelect={selected=it.key},onLibrary=onLibrary,onTorBox=onTorBox,onSettings=onSettings,onMediaCenter=onMediaCenter,libraryBusy=libraryBusy,libraryIssue=libraryIssue)
        }
    }else{
        val ready=state.identity=="$account:${entry.media.mediaKey}"
        val snapshot=if(ready)state.snapshot else SeriesCenterSnapshot()
        val rows=remember(snapshot,episodeRows[entry.rule?.ruleId]){seriesCenterEpisodes(snapshot,episodeRows[entry.rule?.ruleId].orEmpty())}
        SeriesCenterDetail(entry,rows,if(ready)state else SeriesCenterState(busy=true),issue,
            onBack={back()},onRefresh={model.load(account,entry.media,true);scope.launch{if(paired)WatchRules.refresh()}},
            onOpenTitle={onOpenTitle(entry.media)},onAutoAdd={if(entry.rule==null)editing=true else managing=true},
            onSources={sourceEpisode=it},onOpenEpisode={row->onOpenTitle((row.versions.firstOrNull()?:entry.media).copy(season=row.episode.season,episode=row.episode.episode))})
        if(managing&&entry.rule!=null)AutoAddDetails(entry.rule,doc.statuses.firstOrNull{it.ruleId==entry.rule.ruleId},episodeRows[entry.rule.ruleId].orEmpty(),issue,System.currentTimeMillis(),{managing=false},{managing=false;editing=true},{onOpenTitle(entry.media)})
        if(editing)AutoAddSheet(entry.media,rows.map{it.episode.season}.distinct().ifEmpty{listOf(1)},{editing=false},onSettings)
        sourceEpisode?.let{episode->
            val sourceModel:TitleViewModel=androidx.lifecycle.viewmodel.compose.viewModel(key="series-center-sources:${entry.media.mediaKey}")
            LaunchedEffect(episode){sourceModel.selectEpisode(episode);sourceModel.setPackMode(false)}
            // Wait for selection before the source sheet starts its request.
            val selectedEpisode by sourceModel.episode.collectAsState()
            if(selectedEpisode==episode)FindSourcesSheet(entry.media,rows.map{it.episode},sourceModel,{sourceEpisode=null},onSourceSettings,onTorBox)
        }
    }
}

/** Pure UI: no settings, repositories or watcher operations are read during rendering. */
@Composable internal fun SeriesCenterList(entries:List<SeriesCenterEntry>,statuses:Map<String,WatchRuleStatus>,query:String,filter:String,issue:String?,connected:Boolean,
    onQuery:(String)->Unit,onFilter:(String)->Unit,onSelect:(SeriesCenterEntry)->Unit,onLibrary:()->Unit,onTorBox:()->Unit,onSettings:()->Unit,onMediaCenter:()->Unit,libraryBusy:Boolean=false,libraryIssue:String?=null){
    val shown=remember(entries,query,filter,statuses){entries.filter{entry->entry.media.name.contains(query,true)&&when(filter){"autoadd"->entry.rule!=null;"library"->entry.media.jellyfinId!=null;"attention"->entry.rule?.let{watchNeedsAttention(it,statuses[it.ruleId])}==true;else->true}}}
    LazyColumn(Modifier.fillMaxSize().testTag("series-center-list"),contentPadding=PaddingValues(16.dp,22.dp,16.dp,120.dp)){
        item(key="header"){
            LibraryHeader(onMediaCenter){close->DropdownMenuItem(text={Text("STAK Server settings")},onClick={close();onSettings()})}
            Spacer(Modifier.height(10.dp));StakTabs(listOf("jellyfin" to "Jellyfin","torbox" to "TorBox","autoadd" to "Following"),"autoadd",{when(it){"jellyfin"->onLibrary();"torbox"->onTorBox()}},Modifier.fillMaxWidth())
            Spacer(Modifier.height(22.dp));Text("SERIES CENTER",fontFamily=DotoFont,fontSize=22.sp,fontWeight=FontWeight.Bold,color=StakText)
            Text("${entries.size} series · ${entries.count{it.rule!=null}} with Auto-add",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted,modifier=Modifier.padding(top=8.dp,bottom=16.dp))
            StakTextField(query,onQuery,"Search series",height=46)
            Spacer(Modifier.height(12.dp));StakSegmented(listOf("all" to "All","library" to "Library","autoadd" to "Auto-add","attention" to "Attention"),filter,onFilter,Modifier.fillMaxWidth(),fillWidth=true)
            if(issue!=null)Text("Auto-add status could not refresh. Showing the last known state.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted,modifier=Modifier.padding(top=12.dp))
            if(libraryBusy)StakLoadingProgress(Modifier.fillMaxWidth().padding(top=12.dp))
            libraryIssue?.let{Text(it,fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted,modifier=Modifier.padding(top=12.dp))}
            if(!connected)Text("Connect Jellyfin to check which episodes are available.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted,modifier=Modifier.padding(top=12.dp))
            Spacer(Modifier.height(12.dp))
        }
        if(shown.isEmpty()&&!libraryBusy)item{Column(Modifier.padding(vertical=28.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text(if(entries.isEmpty())"Your series, in one place"else "No matching series",fontFamily=DotoFont,fontSize=20.sp,color=StakText)
            Text(if(entries.isEmpty())"Library series and series with Auto-add appear here. Open a title to explore its episodes."else "Try another search or filter.",fontFamily=HankenFont,fontSize=13.sp,lineHeight=20.sp,color=StakTextMuted)
        }}
        items(shown,key={it.key}){entry->SeriesCenterRow(entry,statuses[entry.rule?.ruleId],{onSelect(entry)})}
    }
}

@Composable private fun SeriesCenterRow(entry:SeriesCenterEntry,status:WatchRuleStatus?,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().testTag("series-row:${entry.key}").clickable(onClick=onClick).padding(vertical=15.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
        LibraryArtwork(entry.media.poster,entry.media.name,Modifier.width(48.dp).height(72.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text(entry.media.name,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=16.sp,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(if(entry.media.jellyfinId!=null)"In Jellyfin"else "Not yet verified in library",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
            entry.rule?.let{Text("Auto-add · ${autoAddMode(it)}",fontFamily=SpaceMonoFont,fontSize=11.sp,color=if(watchNeedsAttention(it,status))StakAccent else StakTextSoft);Text(watchQuality(it),fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted,maxLines=1,overflow=TextOverflow.Ellipsis)
                status?.statuses?.let{counts->
                    val available=counts["available_jellyfin"]?:0;val upcoming=counts["waiting_release"]?:0
                    val pending=counts.filterKeys{key->key !in setOf("available_jellyfin","waiting_release")}.values.sum()
                    val summary=listOfNotNull(available.takeIf{it>0}?.let{"$it reported available"},upcoming.takeIf{it>0}?.let{"$it unreleased"},pending.takeIf{it>0}?.let{"$it pending"}).joinToString(" · ")
                    if(summary.isNotBlank())Text(summary,fontFamily=HankenFont,fontSize=11.sp,lineHeight=16.sp,color=StakTextMuted)
                }
            }
        }
        Icon(Lucide.ChevronRight,"Open series",tint=StakTextMuted,modifier=Modifier.size(18.dp))
    }
    HorizontalDivider(color=StakBorderSoft)
}

@Composable internal fun SeriesCenterDetail(entry:SeriesCenterEntry,rows:List<SeriesCenterEpisode>,state:SeriesCenterState,watcherIssue:String?,onBack:()->Unit,onRefresh:()->Unit,onOpenTitle:()->Unit,onAutoAdd:()->Unit,onSources:(Episode)->Unit,onOpenEpisode:(SeriesCenterEpisode)->Unit){
    val seasons=rows.map{it.episode.season}.distinct()
    var season by rememberSaveable(entry.key){mutableIntStateOf(1)}
    var seasonMenu by remember{mutableStateOf(false)}
    var expanded by rememberSaveable(entry.key,season){mutableStateOf<Int?>(null)}
    val active=season.takeIf{it in seasons}?:seasons.firstOrNull()?:1
    val visible=rows.filter{it.episode.season==active}
    LazyColumn(Modifier.fillMaxSize().testTag("series-center-detail"),contentPadding=PaddingValues(16.dp,16.dp,16.dp,120.dp)){
        item(key="header"){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){StakIconButton(onBack){Icon(Lucide.ChevronRight,"Back to series",tint=StakText,modifier=Modifier.rotate(180f))};Text("SERIES CENTER",fontFamily=DotoFont,fontSize=18.sp,color=StakText,modifier=Modifier.weight(1f));StakIconButton(onRefresh){Icon(Lucide.Refresh,"Refresh series",tint=StakTextMuted)}}
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){
                LibraryArtwork(entry.media.poster,entry.media.name,Modifier.width(64.dp).height(96.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(entry.media.name,fontFamily=HankenFont,fontWeight=FontWeight.Bold,fontSize=22.sp,lineHeight=26.sp,color=StakText)
                    Text(if(state.busy)"Checking episodes…"else if(!state.snapshot.libraryVerified)"Library availability not verified"else "${rows.count{it.available}} in Jellyfin · ${rows.count{it.status=="unreleased"}} unreleased",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
                    Text("Auto-add · ${autoAddMode(entry.rule)}",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextSoft)
                }
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(vertical=16.dp)){
                StakButton("View title",onOpenTitle,compact=true)
                StakButton(if(entry.rule==null)"Set up Auto-add"else "Manage Auto-add",onAutoAdd,compact=true)
            }
            if(state.busy)StakLoadingProgress(Modifier.fillMaxWidth())
            val notices=listOfNotNull(state.error)+state.snapshot.notices+listOfNotNull(watcherIssue?.let{"Auto-add status may be out of date."})
            notices.distinct().forEach{Text(it,fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted,modifier=Modifier.padding(bottom=8.dp))}
            if(state.error!=null||state.snapshot.notices.isNotEmpty())StakButton("Retry availability",onRefresh,compact=true)
            Spacer(Modifier.height(12.dp))
            if(seasons.isNotEmpty())Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Box{StakButton(if(active==0)"Specials ▾"else "Season ${active.toString().padStart(2,'0')} ▾",{seasonMenu=true},compact=true);DropdownMenu(seasonMenu,{seasonMenu=false}){seasons.forEach{number->DropdownMenuItem(text={Text(if(number==0)"Specials"else "Season $number")},onClick={season=number;seasonMenu=false})}}}
                Spacer(Modifier.weight(1f));Text("${visible.size} ${if(visible.size==1)"episode"else "episodes"}",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
            }
            if(rows.isEmpty()&&!state.busy)Text("No episode information is available yet. Retry or open the title for metadata.",fontFamily=HankenFont,fontSize=13.sp,lineHeight=20.sp,color=StakTextMuted,modifier=Modifier.padding(vertical=20.dp))
        }
        items(visible,key={"${entry.key}:${it.episode.season}:${it.episode.episode}"}){row->
            SeriesCenterEpisodeRow(row,expanded==row.episode.episode,{expanded=if(expanded==row.episode.episode)null else row.episode.episode},{onSources(row.episode)},{onOpenEpisode(row)},canSearch=entry.media.imdbId!=null||entry.media.mediaKey.startsWith("tt")||entry.media.mediaKey.startsWith("tmdb:"),onVersion={version->onOpenEpisode(row.copy(versions=listOf(version)))})
        }
    }
}

@Composable private fun SeriesCenterEpisodeRow(row:SeriesCenterEpisode,expanded:Boolean,onToggle:()->Unit,onSources:()->Unit,onOpen:()->Unit,canSearch:Boolean=true,onVersion:(MediaItem)->Unit={}){
    val episode=row.episode
    Column(Modifier.fillMaxWidth().testTag("center-episode:${episode.season}:${episode.episode}")){
        Row(Modifier.fillMaxWidth().clickable(onClick=onToggle).padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Text("E${episode.episode.toString().padStart(2,'0')}",fontFamily=SpaceMonoFont,fontSize=12.sp,color=StakTextMuted)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Text(episode.name,fontFamily=HankenFont,fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(row.label,fontFamily=HankenFont,fontSize=12.sp,color=if(row.status=="connection_unavailable")StakAccent else StakTextSoft)
                if(row.quality.isNotBlank())Text(row.quality,fontFamily=SpaceMonoFont,fontSize=10.sp,lineHeight=15.sp,color=StakTextMuted,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            Icon(Lucide.ChevronDown,"Episode options",tint=StakTextMuted,modifier=Modifier.size(16.dp).rotate(if(expanded)180f else 0f))
        }
        NothingExpand(expanded){Column(Modifier.padding(start=38.dp,bottom=14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
            episode.released?.let{Text("Release · ${it.take(10)}",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)}
            if(!canSearch)Text("Open the title to resolve its metadata identity before searching sources.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
            if(row.versions.size>1)row.versions.forEachIndexed{index,version->StakButton("Open version ${index+1}", {onVersion(version)},compact=true)}
            row.automation?.takeUnless{row.available}?.let{Text(watchEpisodeReason(it),fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted)}
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                if(row.available)StakButton("Open episode",onOpen,compact=true)
                if(row.status!="unreleased"&&canSearch)StakButton("Find sources",onSources,compact=true)
            }
        }}
        HorizontalDivider(color=StakBorderSoft)
    }
}
