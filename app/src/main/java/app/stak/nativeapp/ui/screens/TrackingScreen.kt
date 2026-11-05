package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.stak.nativeapp.data.*
import app.stak.nativeapp.data.db.TrackingEntity
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import java.time.*

@Composable fun TrackingScreen(
    onOpenTitle:(MediaItem)->Unit,viewModel:TrackingViewModel,localEntries:List<TrackingEntity> = emptyList(),
    followed:List<MediaItem> = emptyList(),knownLibrary:List<MediaItem> = emptyList(),continueWatching:List<MediaItem> = emptyList(),onOpenAccount:()->Unit={},
    onSetWatched:(JournalEntry,LocalDate?,Boolean)->Unit={_,_,_->},calendarViewModel:CalendarViewModel=androidx.lifecycle.viewmodel.compose.viewModel(),
    onPlanToWatch:(MediaItem)->Unit={},syncNotice:String?=null,onRetrySync:()->Unit={}
){
    val remote by viewModel.remote.collectAsState();val personal by viewModel.personal.collectAsState();val plans by viewModel.plans.collectAsState();val prefs by viewModel.preferences.collectAsState()
    val account=if(NativeRuntime.settings.jellyfinConnected)jellyfinScope()else "local"
    var today by remember{mutableStateOf(LocalDate.now())};val owner=LocalLifecycleOwner.current
    DisposableEffect(owner){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)today=LocalDate.now()};owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}}
    LaunchedEffect(today){val now=ZonedDateTime.now();delay(Duration.between(now,now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis().coerceAtLeast(1000));today=LocalDate.now()}
    LaunchedEffect(account){viewModel.refresh()}
    val libraryObservedAt=remember(knownLibrary){System.currentTimeMillis()}
    val resumeObservedAt=remember(continueWatching){System.currentTimeMillis()}
    val states=remember(remote,personal.remote,account,knownLibrary,continueWatching){serverTrackingStates(account,remote+personal.remote+serverTrackingStates(account,emptyList(),continueWatching,resumeObservedAt)+serverTrackingStates(account,emptyList(),knownLibrary,libraryObservedAt))}
    val entries=remember(personal.events,states,account){serverViewingHistory(account,personal.events,states)}
    val archive=remember(personal.events){personal.events.filter{!it.source.equals("Jellyfin",true)}.map{it.journal()}.sortedByDescending{it.watchedAt?:Long.MIN_VALUE}}
    LaunchedEffect(states,knownLibrary,continueWatching){viewModel.progressCandidates(emptyList(),states,knownLibrary+continueWatching)}
    val progress=remember(plans,states,today){plans.map{seriesProgress(it.copy(status="watching"),states,today)}}
    val watching=remember(progress,states,continueWatching){automaticWatchingRows(progress,states,continueWatching)}
    val trackedCalendar=remember(followed,watching){(followed+watching.map{it.media}).distinctBy{"${it.type}:${it.mediaKey}"}}
    var panel by rememberSaveable{mutableStateOf(prefs.view.takeIf{it in setOf("history","stats")})}
    var earlier by rememberSaveable{mutableStateOf(false)}
    var more by remember{mutableStateOf(false)};var info by remember{mutableStateOf(false)}
    var search by rememberSaveable{mutableStateOf(false)};var query by rememberSaveable{mutableStateOf("")};var type by rememberSaveable{mutableStateOf("all")}
    var historySearch by rememberSaveable{mutableStateOf(false)}
    var details by remember{mutableStateOf<JournalEntry?>(null)};var title by remember{mutableStateOf<MediaItem?>(null)};var episodes by remember{mutableStateOf<MediaItem?>(null)}
    val primary=if(prefs.view=="calendar")"calendar"else "watching";val tab=panel?:primary
    val hubScroll=rememberLazyListState();val historyScroll=rememberLazyListState();val statsScroll=rememberLazyListState();val calendarScroll=rememberLazyListState()
    fun history(old:Boolean=false){earlier=old;historySearch=false;viewModel.preferences(prefs.copy(period="all",type="all",query=""));panel="history"}
    fun stats(period:String="this_month"){viewModel.preferences(prefs.copy(period=period));panel="stats"}
    androidx.activity.compose.PredictiveBackHandler(enabled=panel!=null){events->events.collect{};panel=null}
    val month=remember(entries,today){viewingInsights(individualWatches(filterJournal(entries,JournalPreferences(period="this_month"),today)),today=today)}
    val recent=remember(entries){historyDays(entries).flatMap{it.groups}.take(10)}
    val source=if(panel=="history"&&earlier)archive else entries
    val period=if(panel=="stats"&&prefs.period !in setOf("all","week","this_month"))"this_month"else prefs.period
    val selected=remember(source,prefs,period,today){filterJournal(source,prefs.copy(period=period,type=if(prefs.type=="series")"episode"else prefs.type),today)}
    val watches=remember(entries,period,today){individualWatches(filterJournal(entries,JournalPreferences(period=period),today))}
    val insights=remember(watches,today){viewingInsights(watches,today=today)}
    LazyColumn(state=when(tab){"stats"->statsScroll;"history"->historyScroll;"calendar"->calendarScroll;else->hubScroll},modifier=Modifier.fillMaxSize().semantics{contentDescription="Tracking content"},contentPadding=PaddingValues(16.dp,20.dp,16.dp,128.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item(key="header"){
            Column{
                Row(Modifier.fillMaxWidth().padding(bottom=12.dp),verticalAlignment=Alignment.CenterVertically){
                    if(panel!=null)IconButton(onClick={panel=null}){Icon(Lucide.ChevronRight,"Back to Watching",tint=StakText,modifier=Modifier.size(20.dp).rotate(180f))}
                    StakPageHeader(if(panel=="history"&&earlier)"Earlier records"else when(panel){"history"->"History";"stats"->"Stats";else->"Tracking"},modifier=Modifier.weight(1f))
                    if(panel==null&&primary=="watching"){
                        IconButton(onClick={search=!search;if(!search)query=""}){Icon(if(search)Lucide.X else Lucide.Search,if(search)"Close Watching search"else "Search Watching",tint=StakTextMuted,modifier=Modifier.size(20.dp))}
                        Box{
                            IconButton(onClick={more=true}){Icon(Lucide.Sliders,"Tracking options",tint=StakTextMuted,modifier=Modifier.size(20.dp))}
                            GlassDropdownMenu(more,{more=false}){
                                DropdownMenuItem(text={Text("History")},onClick={more=false;history()})
                                DropdownMenuItem(text={Text("Stats")},onClick={more=false;stats()})
                                if(archive.isNotEmpty())DropdownMenuItem(text={Text("Earlier records")},onClick={more=false;history(true)})
                                DropdownMenuItem(text={Text("Refresh")},onClick={more=false;onRetrySync();viewModel.refresh()})
                                DropdownMenuItem(text={Text("About tracking")},onClick={more=false;info=true})
                            }
                        }
                    }else{
                        IconButton(onClick={info=true}){Icon(Icons.Default.Info,"About tracking",tint=StakTextMuted,modifier=Modifier.size(19.dp))}
                        if(panel==null)IconButton(onClick={calendarViewModel.refresh(trackedCalendar,YearMonth.from(runCatching{LocalDate.parse(prefs.date)}.getOrDefault(today)),prefs.weekStart,true)}){Icon(Lucide.Refresh,"Refresh release calendar",tint=StakText,modifier=Modifier.size(18.dp))}
                    }
                }
                if(panel==null)StakTabs(listOf("watching" to "Watching","calendar" to "Calendar"),primary,{viewModel.preferences(prefs.copy(view=it))})
            }
        }
        if(tab=="watching"){
            item(key="controls"){
                TrackingSection("IN PROGRESS")
                if(search){StakTextField(query,{query=it},"Search Watching",height=44);Spacer(Modifier.height(8.dp))}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("all" to "All","movie" to "Movies","series" to "Series").forEach{(value,label)->StakChip(label,type==value,{type=value})}}
            }
            val visible=watching.filter{(type=="all"||it.media.type==type)&&librarySearchText(it.media.name).contains(librarySearchText(query))}
            if(visible.isEmpty())item{
                Text(if(watching.isEmpty())"Nothing in progress"else "No matching titles",fontFamily=HankenFont,fontSize=14.sp,color=StakTextSoft,modifier=Modifier.padding(top=8.dp))
                if(watching.isEmpty())Text("Jellyfin playback appears here.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted,modifier=Modifier.padding(top=5.dp,bottom=10.dp))
                if(account=="local")StakButton("Connect Jellyfin",onOpenAccount)
            }
            items(visible,key={"watching:${it.key}"}){row->WatchingTitleRow(row,onOpen={if(row.media.isSeries&&row.series!=null)episodes=row.media else onOpenTitle(row.media)},onMark={},onMenu={title=row.media})}
            item(key="recent-heading"){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TrackingSection("WATCH HISTORY");Spacer(Modifier.weight(1f));TextButton(onClick={history()}){Text("View all",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextSoft)}}}
            if(recent.isEmpty())item{Text("No Jellyfin history yet.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)}
            else item(key="recent-rail"){LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.semantics{contentDescription="Recent viewing cards"}){items(recent,key={it.id}){group->RecentlyWatchedCard(group){if(group.first.isEpisode)history()else onOpenTitle(group.first.media)}}}}
            item(key="month"){WatchingMonthSummary(month,recent.firstOrNull()?.first?.media,today){stats()}}
            item(key="habits"){WatchingHabits(entries,today){stats("week")}}
        }else if(tab=="calendar")item{ReleaseCalendar(trackedCalendar,prefs,{viewModel.preferences(it)},onOpenTitle,calendarViewModel)}
        else{
            item(key="period"){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){DiscoverySelect("",period,listOf("week" to "Last 7 days","this_month" to "This month","all" to "All time"),{viewModel.preferences(prefs.copy(period=it))},Modifier.width(165.dp),accessibilityLabel="Tracking period")}}
            if(tab=="stats"){
                item(key="summary"){JournalSummary(insights)}
                item(key="activity"){SimpleTrackingActivity(watches,period,today,onOpenTitle)}
                item(key="streak"){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Current streak",fontFamily=HankenFont,fontSize=13.sp,color=StakTextMuted);Text(viewingInsights(individualWatches(entries),today=today).streakDays.let{"$it ${if(it==1)"day"else "days"}"},fontFamily=SpaceMonoFont,fontSize=12.sp,color=StakText)}}
                val top=topWatchedSeries(watches).take(3)
                if(top.isNotEmpty()){item{TrackingSection("TOP SERIES")};items(top,key={"top:${journalSeriesIdentity(it.media)}"}){TopSeriesRow(it,onOpenTitle)}}
            }else{
                item(key="history-controls"){
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${selected.size} ${if(selected.size==1)"entry"else "entries"}",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted,modifier=Modifier.weight(1f));IconButton(onClick={historySearch=!historySearch;if(!historySearch)viewModel.preferences(prefs.copy(query=""))}){Icon(Lucide.Search,"Search history",tint=StakTextMuted,modifier=Modifier.size(20.dp))}}
                    if(historySearch)StakTextField(prefs.query,{viewModel.preferences(prefs.copy(query=it))},"Search activity")
                    StakSegmented(listOf("all" to "All","movie" to "Movies","episode" to "Episodes"),if(prefs.type=="series")"episode"else prefs.type,{viewModel.preferences(prefs.copy(type=it))})
                }
                if(selected.isEmpty())item{Text("No viewings in this selection.",fontFamily=HankenFont,fontSize=13.sp,color=StakTextMuted)}
                historyDays(selected).forEach{day->item(key="day:${day.date}"){Text(historyDateLabel(day.date,today),fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextSoft,modifier=Modifier.padding(top=10.dp))};items(day.groups,key={it.id}){group->HistoryCard(group,onOpenTitle,{details=it},Modifier)}}
            }
        }
    }
    episodes?.let{media->progress.firstOrNull{sameSeries(it.plan.media,media)}?.let{p->ProgressEpisodesDialog(p,today,{episodes=null},{episodes=null;onOpenTitle(media)},{_,_->},{})}}
    title?.let{media->GlassAlertDialog({title=null},title={Text(media.name,fontFamily=HankenFont)},text={Column{JellyfinQualityButton(media);StakButton("View title",{title=null;onOpenTitle(media)})}},confirmButton={StakButton("Close",{title=null})})}
    details?.let{entry->GlassAlertDialog({details=null},title={Text(entry.media.name,fontFamily=HankenFont)},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text("${entry.source} · ${historyDateLabel(journalDay(entry.watchedAt),today)}",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextSoft);StakButton("View title",{details=null;onOpenTitle(entry.media)})}},confirmButton={StakButton("Close",{details=null})})}
    if(info)GlassAlertDialog({info=false},title={Text("About tracking",fontFamily=DotoFont)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        TrackingNote("Jellyfin","Playback position and watched state come from your Jellyfin user. STAK never changes them. Refreshes and reconnection update the local view.")
        TrackingNote("History","Jellyfin may not provide every viewing or rewatch. Earlier manually recorded entries are preserved separately under Earlier records and do not affect current progress or statistics.")
        TrackingNote("Runtime","Estimated runtime sums known media lengths, not time spent watching. + means partial data; — means unavailable. Charts use dated entries in your device’s time zone.")
    }},confirmButton={StakButton("Got it",{info=false})})
}
