package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun DetailActionRow(identity:String,saved:Boolean,busy:Boolean,trailer:Boolean,onSources:()->Unit,onTrailer:()->Unit,onWatchlist:()->Unit){
    val fontScale=androidx.compose.ui.platform.LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("detail-actions")){
        val narrow=maxWidth<352.dp||fontScale>1.15f
        val primaryWidth=if(narrow)minOf(216.dp,maxWidth-60.dp)else (maxWidth-176.dp).coerceAtMost(216.dp)
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
                DetailSourcesAction(identity,onSources,Modifier.width(primaryWidth))
                if(!narrow)StakButton("Trailer",onTrailer,Modifier.width(104.dp).height(48.dp).semantics{contentDescription="Trailer"},icon=Lucide.Play,enabled=trailer,compact=true)
                DetailIconAction(if(saved)Lucide.BookmarkFilled else Lucide.Bookmark,if(saved)"Remove from watchlist"else "Add to watchlist",!busy,saved,onWatchlist)
            }
            if(narrow)StakButton("Trailer",onTrailer,Modifier.height(48.dp).semantics{contentDescription="Trailer"},icon=Lucide.Play,enabled=trailer,compact=true)
        }
    }
}

@Composable private fun DetailIconAction(icon:ImageVector,label:String,enabled:Boolean,active:Boolean,onClick:()->Unit){
    val press=rememberPressMotion(enabled)
    Box(Modifier.size(48.dp).pressMotion(press).nothingSignal(active).clip(RoundedCornerShape(14.dp)).background(StakSurface.copy(alpha=.3f))
        .border(1.dp,if(active)StakText.copy(alpha=.5f)else StakBorderSoft,RoundedCornerShape(14.dp))
        .clickable(interactionSource=press.source,indication=LocalIndication.current,enabled=enabled,role=Role.Button,onClick=onClick)
        .semantics{contentDescription=label;stateDescription=if(active)"Saved"else if(!enabled)"Unavailable"else "Available"},contentAlignment=Alignment.Center){
        Icon(icon,null,tint=if(!enabled)StakTextFaint else if(active)StakAccent else StakText,modifier=Modifier.size(20.dp))
    }
}

@Composable internal fun DetailLibraryState(media:MediaItem,available:Boolean,connected:Boolean,watched:Boolean){
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth().heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(5.dp).background(if(available)StakTextSoft else StakTextFaint,RoundedCornerShape(5.dp)))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                Text(if(available)"In your library"else if(connected)"Not in library"else "Jellyfin not connected",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
                val quality=if(available)listOfNotNull(media.jellyfinVideoHeight?.let{if(it>=2160)"4K"else if(it>=1080)"1080p"else "${it}p"},media.jellyfinHdr?.takeIf{it.isNotBlank()}).joinToString(" · ")else ""
                if(quality.isNotBlank())Text(quality,fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextSoft,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            if(available)JellyfinQualityButton(media,available=true)
        }
        if(!media.isSeries&&(watched||media.progress>0)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Text(if(watched)"Watched on Jellyfin"else if(media.durationMs>media.positionMs)"${((media.durationMs-media.positionMs+59999)/60000)} min left"else "In progress",fontFamily=HankenFont,fontSize=12.sp,color=StakTextSoft)
                Text("${if(watched)100 else (media.progress*100).toInt()}%",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
            }
            StakProgress(if(watched)1f else media.progress,height=3)
        }
    }
}

@Composable internal fun DetailAutoAdd(rule:WatchRuleModel?,onOpen:()->Unit){
    val label=rule?.let{autoAddMode(it)}?:"Set up"
    Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clip(RoundedCornerShape(12.dp)).clickable(role=Role.Button,onClick=onOpen)
        .semantics{contentDescription="Auto-add settings";stateDescription=label}.nothingSignal(label).padding(horizontal=4.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Icon(Lucide.Radio,null,tint=if(rule==null)StakTextMuted else StakAccent,modifier=Modifier.size(18.dp))
        Text("Auto-add",fontFamily=HankenFont,fontSize=14.sp,color=StakText,modifier=Modifier.weight(1f))
        Text(label,fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextSoft,maxLines=1)
        Icon(Lucide.ChevronRight,null,tint=StakTextMuted,modifier=Modifier.size(16.dp))
    }
}

@Composable internal fun DetailSeasonSelector(seasons:List<Int>,selected:Int,episodes:List<Episode>,loading:Boolean,onSelect:(Int)->Unit){
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("EPISODES",fontFamily=DotoFont,fontSize=17.sp,fontWeight=FontWeight.Bold,color=StakText)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            DiscoverySelect("",selected.toString(),seasons.map{it.toString() to if(it==0)"Specials"else "Season $it"},{it.toIntOrNull()?.let(onSelect)},Modifier.widthIn(max=176.dp).weight(1f),accessibilityLabel="Choose season")
            Text(if(loading)"Loading…"else "${episodes.count{it.watched}} / ${episodes.size} watched",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
        }
    }
}

@Composable internal fun DetailEpisodeRow(episode:Episode,next:Boolean=false,onOpen:()->Unit){
    val resume=episode.progress>0&&!episode.watched
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if(resume||next)StakText.copy(alpha=.04f)else Color.Transparent)
        .clickable(role=Role.Button,onClick=onOpen).testTag("detail-episode-${episode.season}-${episode.episode}").padding(vertical=10.dp,horizontal=2.dp),
        horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.width(104.dp).height(64.dp).clip(RoundedCornerShape(9.dp))){
            StakArtwork(episode.image,null,Modifier.fillMaxSize())
            if(resume)StakProgress(episode.progress,Modifier.align(Alignment.BottomCenter),height=3)
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text("E${episode.episode.toString().padStart(2,'0')}${if(resume)" · IN PROGRESS"else if(next)" · NEXT UP"else ""}",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
            Text(episode.name,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=19.sp,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(listOfNotNull(episode.runtime,episode.released?.take(10)?.let{raw->runCatching{LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("MMM d, yyyy",Locale.ENGLISH))}.getOrDefault(raw)}).joinToString(" · "),fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        if(episode.watched)Icon(Lucide.Check,"Watched on Jellyfin",tint=StakTextSoft,modifier=Modifier.size(17.dp))
        else Icon(Lucide.ChevronRight,"Episode details",tint=StakTextFaint,modifier=Modifier.size(16.dp))
    }
    HorizontalDivider(color=StakBorderSoft.copy(alpha=.4f))
}
