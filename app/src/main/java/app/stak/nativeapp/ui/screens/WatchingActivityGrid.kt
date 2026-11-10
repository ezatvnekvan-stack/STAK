package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun WatchingActivityGrid(entries:List<JournalEntry>,today:LocalDate,onOpen:(MediaItem)->Unit,onDetails:()->Unit,modifier:Modifier=Modifier){
    var offset by rememberSaveable{mutableIntStateOf(0)}
    var selected by rememberSaveable{mutableStateOf<String?>(null)}
    val end=today.minusWeeks(offset.toLong()*12)
    val days=remember(entries,today,end){activityDays(entries,today,end)}
    val dateFormat=remember{DateTimeFormatter.ofPattern("MMM d, yy",Locale.ENGLISH)}
    val current=days.firstOrNull{it.date.toString()==selected}
    val all=days.flatMap{it.entries};val summary=remember(all,today){viewingInsights(all,today=today)}
    val reveal=rememberNothingReveal("activity-grid:$offset",duration=420)
    Column(modifier.fillMaxWidth().testTag("watching-activity").clip(RoundedCornerShape(16.dp)).border(1.dp,Color(0x30FFFFFF),RoundedCornerShape(16.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text("ACTIVITY",fontFamily=DotoFont,fontSize=18.sp,fontWeight=FontWeight.Bold,color=StakText,modifier=Modifier.weight(1f))
            TextButton(onClick=onDetails){Text("Details ›",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextSoft)}
        }
        Row(verticalAlignment=Alignment.CenterVertically){
            StakIconButton({offset++;selected=null}){Text("‹",color=StakText,fontSize=24.sp,modifier=Modifier.semantics{contentDescription="Previous 12 weeks"})}
            Text("${days.first().date.format(dateFormat)} – ${days.last().date.coerceAtMost(today).format(dateFormat)}",Modifier.weight(1f),fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
            StakIconButton({if(offset>0){offset--;selected=null}}){Text("›",color=if(offset>0)StakText else StakTextFaint,fontSize=24.sp,modifier=Modifier.semantics{contentDescription="Next 12 weeks"})}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)){
            Spacer(Modifier.weight(1f))
            days.chunked(7).forEachIndexed{i,w->Text(if(i==0||w.first().date.month!=days[(i-1)*7].date.month)w.first().date.format(DateTimeFormatter.ofPattern("MMM",Locale.ENGLISH))else "",Modifier.weight(1f),fontFamily=SpaceMonoFont,fontSize=8.sp,color=StakTextMuted,maxLines=1)}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                repeat(7){i->Box(Modifier.fillMaxWidth().aspectRatio(1f),contentAlignment=Alignment.Center){if(i in listOf(0,2,4))Text(listOf("M","T","W","T","F","S","S")[i],fontFamily=SpaceMonoFont,fontSize=9.sp,color=StakTextMuted)}}
            }
            days.chunked(7).forEach{week->Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                week.forEach{day->val future=day.date>today
                    val ink=when{future->Color.White.copy(alpha=.025f);day.count==0->Color.White.copy(alpha=.08f);day.count==1->StakAccent.copy(alpha=.3f);day.count<=3->StakAccent.copy(alpha=.6f);else->StakAccent}
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(3.dp)).background(ink.copy(alpha=ink.alpha*reveal.value)).then(if(day.date==today||day.date.toString()==selected)Modifier.border(1.dp,if(day.date.toString()==selected)StakAccent else StakText,RoundedCornerShape(3.dp))else Modifier).semantics{contentDescription="${day.date}: ${day.count} viewings";this.selected=day.date.toString()==selected}.clickable(enabled=!future){selected=day.date.toString()})
                }
            }}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){Text("Less",fontFamily=SpaceMonoFont,fontSize=9.sp,color=StakTextMuted);listOf(.08f,.3f,.6f,1f).forEach{a->Box(Modifier.size(9.dp).background(if(a==.08f)Color.White.copy(alpha=a)else StakAccent.copy(alpha=a),RoundedCornerShape(2.dp)))};Text("More",fontFamily=SpaceMonoFont,fontSize=9.sp,color=StakTextMuted)}
        val runtime=if(summary.knownRuntimeTitles==0&&summary.total>0)"—"else "${summary.knownMinutes/60}h ${summary.knownMinutes%60}m${if(summary.runtimeIncomplete)"+"else ""}"
        Text("${summary.movies} movies · ${summary.episodes} episodes · $runtime est.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextSoft)
        NothingExpand(current!=null){Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
            current?.let{day->
                Row(verticalAlignment=Alignment.CenterVertically){
                    StakIconButton({selected=day.date.minusDays(1).coerceAtLeast(days.first().date).toString()}){Text("‹",color=StakText,fontSize=22.sp,modifier=Modifier.semantics{contentDescription="Previous activity day"})}
                    Text(day.date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy",Locale.ENGLISH)),Modifier.weight(1f),fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakText)
                    StakIconButton({selected=day.date.plusDays(1).coerceAtMost(days.last().date).coerceAtMost(today).toString()}){Text("›",color=StakText,fontSize=22.sp,modifier=Modifier.semantics{contentDescription="Next activity day"})}
                }
                if(day.entries.isEmpty())Text("No recorded viewings",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
                day.entries.forEach{e->Text(e.media.name+(if(e.isEpisode)" · S${e.media.season} E${e.media.episode}"else ""),Modifier.fillMaxWidth().heightIn(min=44.dp).clickable{onOpen(e.media)}.padding(vertical=10.dp),fontFamily=HankenFont,fontSize=13.sp,color=StakText)}
            }
        }}
    }
}
