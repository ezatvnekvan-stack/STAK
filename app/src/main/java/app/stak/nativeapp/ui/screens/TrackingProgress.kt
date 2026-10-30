package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import java.time.LocalDate

fun progressEpisode(media:MediaItem,ep:Episode,played:Boolean=false)=JournalEntry(media.copy(season=ep.season,episode=ep.episode,runtime=ep.runtime?:media.runtime,jellyfinSeriesId=media.jellyfinSeriesId?:media.jellyfinId,jellyfinId=null),null,played)

@Composable internal fun PersonalRatingDialog(media:MediaItem,value:Int?,onSave:(Int?)->Unit,onDismiss:()->Unit){
    GlassAlertDialog(onDismiss,title={Text("Your rating",fontFamily=HankenFont)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(media.name,fontFamily=HankenFont,color=StakTextSoft)
        (0..1).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){(1..5).forEach{col->val n=row*5+col
            Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(10.dp)).border(1.dp,if(n==value)StakText else Color(0x33FFFFFF),RoundedCornerShape(10.dp)).clickable{onSave(n)},contentAlignment=Alignment.Center){Text(n.toString(),fontFamily=SpaceMonoFont,color=StakText,fontSize=14.sp)}
        }}}
        if(value!=null)StakButton("Remove rating",{onSave(null)})
    }},confirmButton={StakButton("Close",onDismiss)})
}
@Composable internal fun ProgressEpisodesDialog(progress:SeriesProgress,today:LocalDate,onDismiss:()->Unit,onOpen:()->Unit,onSet:(JournalEntry,Boolean)->Unit,onMenu:(JournalEntry)->Unit){
    val plan=progress.plan
    val seasons=plan.episodes.map{it.season}.filter{it>0}.distinct().sorted()
    var selected by rememberSaveable(journalSeriesIdentity(plan.media)){mutableIntStateOf(progress.next?.season?:seasons.firstOrNull()?:1)}
    GlassAlertDialog(onDismiss,title={Text(plan.media.name,fontFamily=HankenFont)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(seasons.isNotEmpty())DiscoverySelect("",selected.toString(),seasons.map{it.toString() to "Season $it"},{selected=it.toInt()},Modifier.fillMaxWidth(),accessibilityLabel="Season")
        if(plan.episodes.isEmpty())Text("Episode data unavailable.",fontFamily=HankenFont,color=StakTextMuted)
        plan.episodes.filter{it.season==selected}.sortedBy{it.episode}.forEach{ep->
            val played=ep.season to ep.episode in progress.watched
            val date=ep.released?.take(10)?.let{runCatching{LocalDate.parse(it)}.getOrNull()}
            val eligible=played||date?.isAfter(today)==false
            Row(Modifier.fillMaxWidth().heightIn(min=58.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text("S${ep.season} E${ep.episode} · ${ep.name}",fontFamily=HankenFont,fontSize=13.sp,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(if(played)"Watched"else if(date==null)"Release date unknown"else if(date.isAfter(today))"Releases $date"else "Unwatched",fontFamily=HankenFont,fontSize=10.sp,color=StakTextMuted)
                }
                if(played)Icon(Lucide.Check,"Watched on Jellyfin",tint=StakTextMuted,modifier=Modifier.size(18.dp))
            }
            HorizontalDivider(color=Color(0x13FFFFFF))
        }
        StakButton("View title",onOpen)
    }},confirmButton={StakButton("Done",onDismiss)})
}
