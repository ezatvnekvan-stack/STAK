@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable fun AutoAddOverviewScreen(onLibrary:()->Unit,onTorBox:()->Unit,onSettings:()->Unit,
                                      onMediaCenter:()->Unit={},onOpenTitle:(MediaItem)->Unit={},refreshEnabled:Boolean=true,
                                      knownSeries:List<MediaItem> = emptyList()) {
    val revision by WatchRules.revision.collectAsState()
    val rows by WatchRules.episodeRows.collectAsState()
    val issue by WatchRules.issue.collectAsState()
    val busy by WatchRules.refreshing.collectAsState()
    val doc=remember(revision){WatchRules.store.read()}
    val statuses=doc.statuses.associateBy{it.ruleId}
    val displayRules=remember(doc.rules,knownSeries){doc.rules.map{rule->
        val known=knownSeries.firstOrNull{it.isSeries&&(
            rule.seriesImdb!=null&&it.imdbId==rule.seriesImdb||
            rule.seriesJellyfin!=null&&(it.jellyfinSeriesId==rule.seriesJellyfin||it.jellyfinId==rule.seriesJellyfin))}
        rule.copy(poster=rule.poster?:known?.poster)
    }}
    val paired=doc.connection.enabled&&doc.connection.url.isNotBlank()&&doc.connection.token.isNotBlank()
    val scope=rememberCoroutineScope()
    val owner=LocalLifecycleOwner.current
    var filter by rememberSaveable{mutableStateOf("all")}
    var query by rememberSaveable{mutableStateOf("")}
    var selected by rememberSaveable{mutableStateOf<String?>(null)}
    var editing by remember{mutableStateOf<WatchRuleModel?>(null)}
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(owner,paired,refreshEnabled){
        if(paired&&refreshEnabled)owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
            while(true){WatchRules.refresh();now=System.currentTimeMillis();delay(60000)}
        }
    }
    val attention=doc.rules.count{watchNeedsAttention(it,statuses[it.ruleId])}
    val progress=doc.rules.count{watchInProgress(it,statuses[it.ruleId])}
    val shown=displayRules.filter { rule ->
        rule.seriesName.contains(query,true)&&when(filter){
            "progress"->watchInProgress(rule,statuses[rule.ruleId])
            "attention"->watchNeedsAttention(rule,statuses[rule.ruleId])||issue!=null
            else->true
        }
    }.sortedWith(compareByDescending<WatchRuleModel>{watchNeedsAttention(it,statuses[it.ruleId])}.thenBy{it.seriesName.lowercase()})
    LibraryRefresh(busy,{scope.launch{WatchRules.refresh()}},paired){
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,22.dp,16.dp,120.dp),verticalArrangement=Arrangement.spacedBy(0.dp)){
            item(key="header"){
                Column(Modifier.nothingReveal("autoadd-header")){
                    LibraryHeader(onMediaCenter){close->DropdownMenuItem(text={Text("STAK Server settings")},onClick={close();onSettings()})}
                    Spacer(Modifier.height(10.dp))
                    StakTabs(listOf("jellyfin" to "Jellyfin","torbox" to "TorBox","autoadd" to "Series"),"autoadd",{when(it){"jellyfin"->onLibrary();"torbox"->onTorBox()}},Modifier.fillMaxWidth())
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Text("AUTO-ADD",fontFamily=DotoFont,fontWeight=FontWeight.Bold,fontSize=22.sp,color=StakText,modifier=Modifier.weight(1f))
                        StakIconButton(onSettings){Icon(Lucide.Settings,"STAK Server settings",tint=StakTextMuted,modifier=Modifier.size(19.dp))}
                    }
                    Text("${doc.rules.size} series · $progress in progress · ${if(issue!=null)"Connection needs attention" else "$attention need attention"}",fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted)
                    Spacer(Modifier.height(12.dp))
                    if(!paired){
                        AutoAddMessage("Connect your watcher","Pair once to manage automatic cached-only additions.","Set up",onSettings)
                    }else if(issue!=null){
                        AutoAddMessage("Watcher could not update","Showing the last known state. Your server may still be running.","Connection settings",onSettings)
                    }else{
                        Text("Runs on STAK Server · Live status refreshes here",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
                    }
                    Spacer(Modifier.height(14.dp))
                    StakTextField(query,{query=it},"Search auto-added series",height=46)
                    Spacer(Modifier.height(10.dp))
                    StakSegmented(listOf("all" to "All","progress" to "In progress","attention" to "Needs attention"),filter,{filter=it},Modifier.fillMaxWidth(),fillWidth=true)
                    Spacer(Modifier.height(14.dp))
                }
            }
            if(shown.isEmpty())item(key="empty"){
                Column(Modifier.padding(vertical=28.dp).nothingReveal("autoadd-empty:$filter"),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Text(if(doc.rules.isEmpty())"Your series, automatically" else "Nothing here",fontFamily=DotoFont,fontSize=21.sp,color=StakText)
                    Text(if(doc.rules.isEmpty())"Open a series and choose Auto-add. You decide the quality; only cached releases can be added." else "No series match this search or filter.",fontFamily=HankenFont,fontSize=13.sp,lineHeight=20.sp,color=StakTextMuted)
                }
            }
            items(shown,key={it.ruleId}){rule->
                AutoAddSeriesRow(rule,statuses[rule.ruleId],rows[rule.ruleId].orEmpty(),now,{selected=rule.ruleId})
            }
        }
    }
    val current=displayRules.firstOrNull{it.ruleId==selected}
    if(current!=null&&editing==null)AutoAddDetails(current,statuses[current.ruleId],rows[current.ruleId].orEmpty(),issue,now,
        onDismiss={selected=null},onEdit={editing=current},onOpenTitle={onOpenTitle(ruleMedia(current))})
    editing?.let{rule->AutoAddSheet(ruleMedia(rule),rule.watchedSeasons.ifEmpty{listOf(1)},onDismiss={editing=null},onOpenSettings={editing=null;selected=null;onSettings()})}
}

private fun ruleMedia(rule:WatchRuleModel)=MediaItem(id=rule.seriesImdb?:rule.seriesTmdb?.let{"tmdb:$it"}?:rule.seriesJellyfin.orEmpty(),
    imdbId=rule.seriesImdb,type="series",name=rule.seriesName,poster=rule.poster,jellyfinSeriesId=rule.seriesJellyfin)

@Composable private fun AutoAddMessage(title:String,body:String,action:String,onClick:()->Unit){
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha=.035f)).padding(14.dp)){
        Text(title,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=13.sp,color=StakText)
        Text(body,fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted)
        StakButton(action,onClick,compact=true,variant=StakButtonVariant.Ghost)
    }
}

@Composable internal fun AutoAddBadge(label:String,attention:Boolean=false){
    Row(Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp,if(attention)StakAccent.copy(alpha=.6f) else Color.White.copy(alpha=.22f),RoundedCornerShape(8.dp))
        .nothingSignal(label).padding(horizontal=8.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
        Box(Modifier.size(4.dp).background(if(attention)StakAccent else StakText,RoundedCornerShape(2.dp)))
        Text(label,fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakText)
    }
}

@Composable private fun AutoAddSeriesRow(rule:WatchRuleModel,status:WatchRuleStatus?,episodes:List<WatchEpisode>,now:Long,onClick:()->Unit){
    val featured=watchFeatured(episodes)
    Column(Modifier.fillMaxWidth().nothingReveal("autoadd:${rule.ruleId}")){
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick=onClick).padding(vertical=12.dp,horizontal=2.dp),
            horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top){
            LibraryArtwork(rule.poster,rule.seriesName,Modifier.width(48.dp).height(72.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(rule.seriesName,Modifier.weight(1f),fontFamily=HankenFont,fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis)
                    AutoAddBadge(autoAddMode(rule),watchNeedsAttention(rule,status))
                }
                Text(watchQuality(rule),fontFamily=SpaceMonoFont,fontSize=11.sp,lineHeight=15.sp,color=StakTextMuted,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(when{rule.pendingAction=="off"->"Stop requested · awaiting confirmation";rule.state!=WatchRuleModel.STATE_SERVER_CONFIRMED->"Changes have not been confirmed";rule.paused->"Automatic additions are paused";featured!=null->"${featured.label} · ${watchStatusLabel(featured.status)}";else->"Waiting for the first episode update"},
                    fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakText)
                if(featured!=null&&rule.pendingAction==null&&!rule.paused&&featured.status in setOf("waiting_release_match","connection_unavailable"))Text(watchEpisodeReason(featured),fontFamily=HankenFont,fontSize=11.sp,lineHeight=17.sp,color=StakTextMuted,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text("Updated: ${watchRelative(status?.fetchedAt?:0L,now)}",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
            }
            Icon(Lucide.ChevronRight,null,tint=StakTextMuted,modifier=Modifier.padding(top=4.dp).size(16.dp))
        }
        HorizontalDivider(color=Color.White.copy(alpha=.09f))
    }
}

@Composable internal fun AutoAddDetails(rule:WatchRuleModel,status:WatchRuleStatus?,rows:List<WatchEpisode>,issue:String?,now:Long,
                                     onDismiss:()->Unit,onEdit:()->Unit,onOpenTitle:()->Unit){
    val scope=rememberCoroutineScope()
    var busy by remember{mutableStateOf(false)}
    var stop by remember{mutableStateOf(false)}
    var notice by remember{mutableStateOf<String?>(null)}
    var selectedEpisode by rememberSaveable(rule.ruleId){mutableStateOf<String?>(null)}
    GlassDialog(onDismiss=onDismiss){
        Column(Modifier.fillMaxWidth().heightIn(max=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp*.78f).verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(rule.seriesName,fontFamily=DotoFont,fontSize=22.sp,color=StakText)
            AutoAddBadge(autoAddMode(rule),watchNeedsAttention(rule,status))
            Text(watchQuality(rule),fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted)
            Text("Updated: ${watchRelative(status?.fetchedAt?:0,now)}",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
            if(issue!=null)Text("Connection unavailable. These are the last known episode states.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                StakButton("Settings",onEdit,compact=true,enabled=!busy)
                StakButton(if(rule.paused)"Resume" else "Pause",{scope.launch{busy=true;try{WatchRules.setPaused(rule.ruleId,!rule.paused);notice=WatchRules.issue.value?:"Change confirmed by watcher."}finally{busy=false}}},compact=true,enabled=!busy&&rule.pendingAction!="off")
                StakButton("Refresh status",{scope.launch{busy=true;try{WatchRules.refresh()}finally{busy=false}}},compact=true,enabled=!busy)
                StakButton("View title",onOpenTitle,compact=true,variant=StakButtonVariant.Ghost)
            }
            notice?.let{Text(it,fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted)}
            if(busy)StakLoadingProgress()
            Text("EPISODES",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
            if(rows.isEmpty())Text("Episode details appear after a successful status update.",fontFamily=HankenFont,fontSize=13.sp,color=StakTextMuted)
            rows.forEach{episode->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable{selectedEpisode=if(selectedEpisode==episode.label)null else episode.label}.padding(vertical=10.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        Text(episode.label,fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakText)
                        Text(watchStatusLabel(episode.status),Modifier.weight(1f),fontFamily=HankenFont,fontSize=12.sp,color=StakText)
                        Icon(Lucide.ChevronDown,"Episode details",tint=StakTextMuted,modifier=Modifier.size(14.dp))
                    }
                    NothingExpand(selectedEpisode==episode.label){Column(Modifier.padding(top=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text(watchEpisodeReason(episode),fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted)
                        AutoAddStages(episode.status)
                        Text("Last check: ${watchRelative(episode.lastCheck,now)}",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
                        if(episode.nextCheck>0)Text("Next check: ${watchRelative(episode.nextCheck,now)}",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
                    }}
                }
                HorizontalDivider(color=Color.White.copy(alpha=.08f))
            }
            StakButton(if(rule.pendingAction=="off")"Retry stop request" else "Turn off Auto-add",{stop=true},compact=true,enabled=!busy,variant=StakButtonVariant.Destructive)
            Text("Turning off keeps existing TorBox items and watcher history.",fontFamily=HankenFont,fontSize=11.sp,lineHeight=16.sp,color=StakTextMuted)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){StakButton("Close",onDismiss,compact=true,variant=StakButtonVariant.Ghost)}
    }
    if(stop)GlassAlertDialog(onDismissRequest={if(!busy)stop=false},title={Text("Turn off Auto-add?")},
        text={Text("Stop automatic additions for “${rule.seriesName}”? Existing media and history will remain.")},
        confirmButton={StakButton(if(busy)"Stopping…" else "Turn off",{scope.launch{busy=true;try{WatchRules.turnOff(rule.ruleId);notice=WatchRules.issue.value?:"Auto-add stopped.";stop=false}finally{busy=false}}},enabled=!busy)},
        dismissButton={StakButton("Keep enabled",{stop=false},enabled=!busy)})
}

@Composable private fun AutoAddStages(status:String){
    val stage=when(status){"available_jellyfin"->3;"waiting_library"->1;"added_torbox"->1;"waiting_torbox_cache"->0;else->-1}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
        listOf("Release","TorBox","Filter","Jellyfin").forEachIndexed{i,label->
            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){
                Box(Modifier.size(6.dp).nothingSignal(stage).background(if(i<=stage&&status!="connection_unavailable")StakText else Color.White.copy(alpha=.16f),RoundedCornerShape(2.dp)))
                Text(label,fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
            }
        }
    }
}
