@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.stak.nativeapp.ui.screens
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import com.skydoves.flexible.bottomsheet.material3.FlexibleBottomSheet
import com.skydoves.flexible.core.*
import dev.chrisbanes.haze.HazeState

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Compact series-tracking popup (app-style GlassDialog, not a full sheet).
 * Pairing lives in Settings -> Auto-add; this popup only edits the rule.
 */
@Composable fun AutoAddSheet(media:MediaItem,seasons:List<Int>,onDismiss:()->Unit,onOpenSettings:()->Unit={}) {
    val key=WatchRules.seriesKeyOf(media)
    val revision by WatchRules.revision.collectAsState()
    val issue by WatchRules.issue.collectAsState()
    val scope=rememberCoroutineScope()

    val existing=remember(revision,key){key?.let{WatchRules.ruleFor(it)}}
    val status=remember(revision,key){key?.let{WatchRules.statusFor(it)}}
    val connection=remember(revision,key){WatchRules.store.read().connection}
    val paired=remember(revision,key){connection.enabled&&connection.url.isNotBlank()&&connection.token.isNotBlank()}

    val defaults=remember(key){WatchRules.store.read().qualityDefault ?: WatchQualityProfile()}
    var mode by remember(key){mutableStateOf(when{existing==null->0;existing.paused->1;else->0})}
    var watched by remember(key){mutableStateOf(existing?.watchedSeasons?.toSet() ?: emptySet<Int>())}
    var includeSpecials by remember(key){mutableStateOf(existing?.includeSpecials ?: false)}
    var onlyNew by remember(key){mutableStateOf(existing?.onlyNewEpisodes ?: true)}
    var resolutionMode by remember(key){mutableStateOf(existing?.resolutionMode ?: defaults.resolution)}
    var waitHours by remember(key){mutableStateOf(existing?.resolutionWaitHours ?: defaults.waitHours)}
    var releaseTypes by remember(key){mutableStateOf(existing?.releaseTypes?.toSet() ?: defaults.releases.toSet())}
    var hdrMode by remember(key){mutableStateOf(existing?.hdrMode ?: defaults.hdr)}
    var dvMode by remember(key){mutableStateOf(existing?.dvMode ?: defaults.dv)}
    var atmosMode by remember(key){mutableStateOf(existing?.atmosMode ?: defaults.atmos)}
    var languageMode by remember(key){mutableStateOf(existing?.languageMode ?: defaults.language)}
    var maxGb by remember(key){mutableStateOf(existing?.maxEpisodeGb ?: defaults.maxGb)}
    var checkMinutes by remember(key){mutableStateOf(existing?.checkMinutes ?: defaults.checkMinutes)}
    var moreOpen by remember(key){mutableStateOf(false)}

    var savingRule by remember{mutableStateOf(false)}
    var saveNotice by remember{mutableStateOf<String?>(null)}
    var confirmOff by remember{mutableStateOf(false)}

    LaunchedEffect(media.mediaKey,paired){if(paired&&!savingRule){WatchRules.refresh()}}

    GlassDialog(onDismiss=onDismiss){
        Column(Modifier.fillMaxWidth().heightIn(max=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp*.88f)
            .verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {

            Text("AUTO-ADD",fontFamily=DotoFont,fontWeight=FontWeight.Bold,fontSize=20.sp,color=StakText)
            Text("${media.name} · Series only · TorBox cached-only",fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)

            // --- state ------------------------------------------------------
            AutoAddBadge(autoAddMode(existing), existing!=null&&existing.state!=WatchRuleModel.STATE_SERVER_CONFIRMED)
            status?.let{s->
                val ready=s.statuses["available_jellyfin"]?:0
                Text("${s.episodes} episodes · $ready available in Jellyfin · Updated ${watchRelative(s.fetchedAt)}",
                    fontFamily=HankenFont,fontSize=11.sp,lineHeight=17.sp,color=StakTextMuted)
            }
            if(paired){
                Text("STAK Server paired · Manage in Settings → STAK Server",
                    fontFamily=SpaceMonoFont,fontSize=11.sp,lineHeight=14.sp,color=StakTextMuted)
            } else {
                Text("Connect STAK Server in Settings to enable Auto-add.",
                    fontFamily=HankenFont,fontSize=12.sp,lineHeight=17.sp,color=Color(0xFFFFB86B))
                Spacer(Modifier.height(4.dp))
                StakButton("Open settings",{onOpenSettings()},compact=true)
            }
            issue?.let{Text(it,fontFamily=HankenFont,fontSize=12.sp,lineHeight=17.sp,color=Color(0xFFFFB86B))}

            if(key!=null) {
                // --- mode ---------------------------------------------------
                AutoSectionLabel("Mode")
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    StakChip("ON",mode==0,{mode=0})
                    StakChip("PAUSED",mode==1,{if(existing!=null)mode=1})
                    StakChip("OFF",mode==2,{if(existing!=null)mode=2})
                }
                Text(if(mode==2)"Turning off needs watcher confirmation. Existing media and history stay."
                    else if(mode==1)"Pause automatic additions. Existing media stays in your library."
                    else if(existing==null)"Enable automatic cached-only additions with these settings."
                    else "Your watcher adds matching cached episodes, even when this app is closed.",
                    fontFamily=HankenFont,fontSize=11.sp,lineHeight=16.sp,color=StakTextMuted,modifier=Modifier.padding(top=5.dp))

                if(mode!=2) {
                    AutoSectionLabel("Episodes")
                    RuleSelect(if(onlyNew)"Only new episodes" else "Include earlier missing episodes",
                        listOf("new" to "Only new episodes","include-missing" to "Include earlier missing episodes")){onlyNew=it=="new"}
                    Spacer(Modifier.height(6.dp))
                    RuleSelect(resolutionLabel(resolutionMode),
                        listOf(
                            WatchModes.RESOLUTION_2160_ONLY to "2160p only (4K)",
                            WatchModes.RESOLUTION_2160_PREFERRED to "2160p preferred",
                            WatchModes.RESOLUTION_1080_ONLY to "1080p only")){resolutionMode=it;if(it==WatchModes.RESOLUTION_2160_PREFERRED&&waitHours<=0)waitHours=24}
                    if(resolutionMode==WatchModes.RESOLUTION_2160_PREFERRED) {
                        Spacer(Modifier.height(6.dp))
                        RuleSelect("Wait for 4K up to $waitHours h",
                            listOf(6 to "6 hours",12 to "12 hours",24 to "24 hours",48 to "2 days",72 to "3 days",168 to "1 week").map{(v,l)->v.toString() to l}){
                            waitHours=it.toInt()
                        }
                    }

                    AutoSectionLabel("Release types")
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        WatchModes.RELEASE_TYPES.forEach{type->StakChip(type,type in releaseTypes,{
                            releaseTypes=if(type in releaseTypes)releaseTypes-type else releaseTypes+type})}
                    }

                    // compact "more options" (watched seasons, HDR, language, size, check freq)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp,Color.White.copy(alpha=.26f),RoundedCornerShape(10.dp))
                        .clickable{moreOpen=!moreOpen}.padding(horizontal=14.dp,vertical=10.dp),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Text(if(moreOpen)"Fewer options" else "More options",fontFamily=HankenFont,fontSize=12.sp,color=StakText,modifier=Modifier.weight(1f))
                        Icon(Lucide.ChevronDown,null,tint=StakTextMuted,modifier=Modifier.size(13.dp).graphicsLayer{rotationZ=if(moreOpen)0f else 180f})
                    }
                    NothingExpand(moreOpen){
                      Column {
                        Spacer(Modifier.height(8.dp))
                        AutoSectionLabel("Seasons to search")
                        StakChip("All seasons, including future",watched.isEmpty(),{watched=emptySet()})
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            (seasons.filter{it>0}+listOf(0)).distinct().sorted().forEach{season->
                                val label=if(season==0)"Specials" else "S${season.toString().padStart(2,'0')}"
                                val selected=if(season==0)includeSpecials else watched.isEmpty()||season in watched
                                StakChip(label,selected,{
                                    if(season==0)includeSpecials=!includeSpecials
                                    else watched=if(season in watched)watched-season else watched+season
                                })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        AutoSectionLabel("Quality and limits")
                        Text("HDR",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
                        RuleSelect(hdrModeLabel(hdrMode),traitOptions){hdrMode=it}
                        Spacer(Modifier.height(6.dp))
                        Text("Dolby Vision",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
                        RuleSelect(traitLabel("Dolby Vision",dvMode),traitOptions){dvMode=it}
                        Spacer(Modifier.height(6.dp))
                        Text("Atmos",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
                        RuleSelect(traitLabel("Atmos",atmosMode),traitOptions){atmosMode=it}
                        Spacer(Modifier.height(6.dp))
                        RuleSelect(languageLabel(languageMode),
                            listOf(
                                WatchModes.LANG_ANY to "Any language",
                                WatchModes.LANG_ORIGINAL to "Original language only",
                                WatchModes.LANG_HUN_PREFERRED to "Hungarian preferred",
                                WatchModes.LANG_HUN_MANDATORY to "Hungarian mandatory")){languageMode=it}
                        Spacer(Modifier.height(6.dp))
                        RuleSelect(if(maxGb==null)"Any episode size" else "Up to %.0f GB per episode".format(maxGb),
                            listOf("" to "Any size")+listOf(5,10,20,50).map{it.toString() to "≤ $it GB"}){maxGb=it.toDoubleOrNull()}
                        Spacer(Modifier.height(6.dp))
                        RuleSelect("Check every $checkMinutes minutes",
                            listOf(15,30,45,60,90,120,240,720).map{it.toString() to "$it min"}){checkMinutes=it.toInt()}
                    }

                      }
                    // --- save ---------------------------------------------------
                    Spacer(Modifier.height(4.dp))
                    Text("${resolutionLabel(resolutionMode)} · ${releaseTypes.joinToString(" · ")} · ${languageLabel(languageMode)}",fontFamily=HankenFont,fontSize=11.sp,lineHeight=17.sp,color=StakTextMuted)
                    val busy=savingRule
                    StakButton(if(busy)"Saving…" else if(existing!=null)"Save changes" else "Enable Auto-add",{scope.launch{
                        savingRule=true;saveNotice=null
                        try{
                            val saved=WatchRules.saveRule(buildRule(key,media,existing,mode,watched.toList().sorted(),includeSpecials,
                                onlyNew,resolutionMode,waitHours,releaseTypes.toList().intersect(WatchModes.RELEASE_TYPES).toList().sorted(),
                                hdrMode,dvMode,atmosMode,languageMode,maxGb,checkMinutes),consent=true)
                            saveNotice=if(saved.confirmed)"Saved on the watcher."
                                else "Saved on this device. "+(WatchRules.issue.value ?: "The watcher has not confirmed this rule yet.")
                            if(saved.issues.size()>0)saveNotice=saveNotice+" Check the selected filters: the watcher reported a restrictive combination."
                        }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
                        catch(error:Throwable){
                            val failure=(error as? Exception) ?: Exception(error)
                            saveNotice="Could not save. "+watchRuleError(failure)
                        }
                        finally{savingRule=false}
                    }},enabled=!busy&&releaseTypes.isNotEmpty())
                    saveNotice?.let{Text(it,fontFamily=HankenFont,fontSize=12.sp,lineHeight=17.sp,
                        color=if(it.startsWith("Saved on the watcher"))StakText else Color(0xFFFFB86B),modifier=Modifier.padding(top=6.dp))}
                        ?: Text(if(existing==null)"Nothing is sent to TorBox unless a release is cached."
                            else "Changes sync to the paired watcher.",
                            fontFamily=HankenFont,fontSize=11.sp,lineHeight=15.sp,color=StakTextMuted,modifier=Modifier.padding(top=6.dp))
                    StakButton("Use quality as default",{scope.launch{
                        WatchRules.saveDefault(WatchQualityProfile(resolutionMode,waitHours,releaseTypes.toList(),hdrMode,dvMode,atmosMode,languageMode,maxGb,checkMinutes))
                        saveNotice="Quality defaults saved. Existing series were not changed."
                    }},compact=true,enabled=!busy&&releaseTypes.isNotEmpty(),variant=StakButtonVariant.Ghost)
                } else {
                    saveNotice?.let{Text(it,fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)}
                    StakButton("Turn off Auto-add",{
                        if(existing!=null)confirmOff=true
                        else{scope.launch{WatchRules.turnOff(key)}}
                    })
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
            StakButton("Close",{onDismiss()},compact=true,variant=StakButtonVariant.Ghost)
        }
    }
    if(confirmOff)GlassAlertDialog(onDismissRequest={confirmOff=false},title={Text("Turn off Auto-add?")},
        text={Column{Text("Stop Auto-add for \"${media.name}\"? The watcher pauses its episodes and keeps history; anything already sent to TorBox stays as-is.",fontFamily=HankenFont,fontSize=13.sp)}},
        confirmButton={StakButton(if(savingRule)"Stopping…" else "Turn it off",{scope.launch{
            savingRule=true
            try{key?.let{WatchRules.turnOff(it)};saveNotice=WatchRules.issue.value?:"Auto-add stopped.";confirmOff=false}
            finally{savingRule=false}
        }},enabled=!savingRule)},
        dismissButton={StakButton("Keep enabled",{confirmOff=false},enabled=!savingRule)})
}

private fun buildRule(key:String,media:MediaItem,existing:WatchRuleModel?,mode:Int,watchedSeasons:List<Int>,includeSpecials:Boolean,
                      onlyNew:Boolean,resolutionMode:String,waitHours:Int,releaseTypes:List<String>,hdrMode:String,dvMode:String,
                      atmosMode:String,languageMode:String,maxGb:Double?,checkMinutes:Int):WatchRuleModel=WatchRuleModel(
    ruleId=watchRuleIdFor(key),
    seriesImdb=media.imdbId?.takeIf{it.isNotBlank()},
    seriesTmdb=media.mediaKey.takeIf{it.startsWith("tmdb:")}?.substringAfter(':')?.takeIf{it.all(Char::isDigit)},
    seriesTvdb=null,
    seriesJellyfin=media.jellyfinSeriesId?.takeIf{it.isNotBlank()},
    seriesName=media.name,
    enabled=true,
    paused=mode==1,
    createdOn=existing?.createdOn ?: java.time.LocalDate.now().toString(),
    watchedSeasons=watchedSeasons,
    includeSpecials=includeSpecials,
    onlyNewEpisodes=onlyNew,
    resolutionMode=resolutionMode,
    resolutionWaitHours=waitHours,
    releaseTypes=releaseTypes,
    hdrMode=hdrMode,dvMode=dvMode,atmosMode=atmosMode,
    languageMode=languageMode,
    maxEpisodeGb=maxGb,
    checkMinutes=checkMinutes.coerceIn(WatchModes.MIN_CHECK_MINUTES..WatchModes.MAX_CHECK_MINUTES),
    state=existing?.state ?: WatchRuleModel.STATE_SAVED_LOCAL,at=System.currentTimeMillis(),poster=media.poster ?: existing?.poster)

private val traitOptions=listOf(
    WatchModes.MODE_ANY to "Any (no preference)",
    WatchModes.MODE_PREFERRED to "Preferred",
    WatchModes.MODE_MANDATORY to "Mandatory")
private fun resolutionLabel(mode:String)=when(mode){
    WatchModes.RESOLUTION_2160_ONLY->"2160p only (4K)"
    WatchModes.RESOLUTION_1080_ONLY->"1080p only"
    else->"2160p preferred (falls back)"
}
private fun traitLabel(mode:String,value:String)=when(value){
    WatchModes.MODE_MANDATORY->"$mode mandatory"
    WatchModes.MODE_PREFERRED->"$mode preferred"
    else->"$mode: any"
}
private fun hdrModeLabel(mode:String)=traitLabel("HDR",mode)
private fun languageLabel(mode:String)=when(mode){
    WatchModes.LANG_ANY->"Any language"
    WatchModes.LANG_ORIGINAL->"Original only"
    WatchModes.LANG_HUN_PREFERRED->"Hungarian preferred"
    else->"Hungarian mandatory"
}

@Composable private fun AutoTag(label:String)=Text(label,fontFamily=SpaceMonoFont,fontSize=11.sp,lineHeight=13.sp,
    color=StakTextMuted,modifier=Modifier.border(1.dp,Color(0x24FFFFFF),RoundedCornerShape(7.dp)).padding(horizontal=7.dp,vertical=3.dp))

@Composable private fun AutoSectionLabel(label:String)=Text(label,fontFamily=SpaceMonoFont,fontSize=11.sp,lineHeight=14.sp,
    color=StakTextMuted,modifier=Modifier.padding(bottom=6.dp))

@Composable private fun RuleSelect(label:String,options:List<Pair<String,String>>,modifier:Modifier=Modifier,onSelect:(String)->Unit) {
    var open by remember{mutableStateOf(false)}
    Box(modifier.fillMaxWidth()) {
        Row(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp,Color.White.copy(alpha=.30f),RoundedCornerShape(10.dp))
            .clickable{open=true}.padding(horizontal=14.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(label,fontFamily=HankenFont,fontSize=13.sp,color=StakText,modifier=Modifier.weight(1f))
            Icon(Lucide.ChevronDown,null,tint=StakTextMuted,modifier=Modifier.size(13.dp))
        }
        GlassDropdownMenu(expanded=open,onDismissRequest={open=false}){options.forEach{(value,text)->
            androidx.compose.material3.DropdownMenuItem(text={Text(text,fontFamily=HankenFont,fontSize=13.sp,color=StakText)},
                onClick={open=false;onSelect(value)})}
        }
    }
}
