package app.stak.nativeapp.ui.screens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun ActivityMonthHeader(month:YearMonth,months:List<YearMonth>,summary:ViewingInsights,onMonth:(YearMonth)->Unit,onStats:()->Unit,modifier:Modifier=Modifier){
    var choose by remember{mutableStateOf(false)}
    val format=remember{DateTimeFormatter.ofPattern("MMMM yyyy",Locale.ENGLISH)}
    val runtime=if(summary.knownRuntimeTitles==0&&summary.total>0)"—"else "${summary.knownMinutes/60}h ${summary.knownMinutes%60}m${if(summary.runtimeIncomplete)"+"else ""}"
    Column(modifier.fillMaxWidth().testTag("activity-diary-summary"),verticalArrangement=Arrangement.spacedBy(4.dp)){
        Box{
            TextButton(onClick={choose=true}){Text(month.format(format).uppercase(Locale.ENGLISH)+"  ▾",fontFamily=DotoFont,fontWeight=FontWeight.Bold,fontSize=19.sp,color=StakText)}
            GlassDropdownMenu(choose,{choose=false},modifier=Modifier.heightIn(max=320.dp)){
                months.forEach{m->DropdownMenuItem(text={Text(m.format(format),color=if(m==month)StakText else StakTextMuted)},onClick={choose=false;onMonth(m)})}
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(onClick=onStats).semantics{contentDescription="View statistics for ${month.format(format)}"}.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Text("${summary.movies} movies · ${summary.episodes} episodes · $runtime est.",Modifier.weight(1f),fontFamily=HankenFont,fontSize=13.sp,lineHeight=19.sp,color=StakTextSoft)
            Icon(Lucide.ChevronRight,null,tint=StakTextMuted,modifier=Modifier.size(17.dp))
        }
        HorizontalDivider(color=StakText.copy(alpha=.1f))
    }
}
@Composable internal fun DiaryHistoryRow(group:HistoryGroup,onOpen:(MediaItem)->Unit,modifier:Modifier=Modifier){
    var expanded by rememberSaveable(group.id){mutableStateOf(false)}
    val media=group.first.media
    Column(modifier.fillMaxWidth().testTag("diary-entry")){
        Row(Modifier.fillMaxWidth().heightIn(min=88.dp).clickable{if(group.groupedEpisodes)expanded=!expanded else onOpen(media)}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            LibraryArtwork(media.poster?:media.background,media.name,Modifier.width(44.dp).height(66.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Text(media.name,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=21.sp,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis)
                val subtitle=when{group.groupedEpisodes->"${group.entries.size} episodes";group.first.isEpisode->"S%02d E%02d".format(media.season,media.episode);else->"Movie"+runtimeMinutes(media)?.let{" · $it min"}.orEmpty()}
                Text(subtitle,fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
            }
            if(group.groupedEpisodes)Icon(if(expanded)Lucide.ChevronDown else Lucide.ChevronRight,if(expanded)"Collapse episodes"else "Expand episodes",tint=StakTextMuted,modifier=Modifier.size(18.dp))
        }
        NothingExpand(expanded){Column(Modifier.padding(start=56.dp)){
            group.entries.forEach{entry->Text("S%02d E%02d".format(entry.media.season,entry.media.episode),Modifier.fillMaxWidth().heightIn(min=44.dp).clickable{onOpen(entry.media)}.padding(vertical=12.dp),fontFamily=SpaceMonoFont,fontSize=12.sp,color=StakTextSoft)}
            TextButton(onClick={onOpen(journalSeriesTitle(media))}){Text("View series",color=StakTextMuted,fontFamily=HankenFont)}
        }}
        HorizontalDivider(color=StakText.copy(alpha=.08f))
    }
}
