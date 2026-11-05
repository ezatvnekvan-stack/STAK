package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.theme.*
import app.stak.nativeapp.ui.components.*
import androidx.compose.runtime.saveable.rememberSaveable
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun SimpleTrackingActivity(entries:List<JournalEntry>,period:String,today:LocalDate,onOpen:(MediaItem)->Unit){
    val bins=remember(entries,period,today){trackingTimeline(entries,period,today)}
    var selected by rememberSaveable(period,today.toString()){mutableIntStateOf(bins.indexOfLast{it.count>0}.coerceAtLeast(0))}
    val current=bins.getOrNull(selected)
    val dateFormat=remember(period){DateTimeFormatter.ofPattern(if(period=="all")"MMM yyyy"else "MMM d",Locale.ENGLISH)}
    Column(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        TrackingSection(if(period=="all")"LAST 12 MONTHS"else "DAILY ACTIVITY")
        Text("${bins.first().start.format(dateFormat)} – ${bins.last().end.format(dateFormat)}",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
        val peak=bins.maxOf{it.count}.coerceAtLeast(1)
        val reveal=rememberNothingReveal("activity:$period:${bins.map{it.count}}",duration=420)
        Row(Modifier.fillMaxWidth().height(96.dp),horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.Bottom){bins.forEachIndexed{i,bin->
            Box(Modifier.weight(1f).fillMaxHeight().clickable{selected=i}.semantics{contentDescription="${bin.start}: ${bin.count} viewings";this.selected=selected==i}){
                Canvas(Modifier.fillMaxSize()){
                    val step=4.dp.toPx();val dot=2.dp.toPx();val columns=(size.width/step).toInt().coerceAtLeast(1).coerceAtMost(5)
                    val rows=if(bin.count==0)1 else (2+18f*bin.count/peak).toInt()
                    repeat(rows){r->repeat(columns){c->
                    val shown=(reveal.value*(22f+bins.size*.4f)-i*.4f-r).coerceIn(0f,1f)
                    drawRect(if(bin.count==0)Color.White.copy(alpha=.16f)else if(i==selected)StakAccent else StakText.copy(alpha=.65f),Offset((size.width-columns*step)/2+c*step,size.height-dot-r*step),Size(dot,dot),alpha=shown)}}
                }
            }
        }}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            listOf(bins.first(),bins[bins.size/2],bins.last()).distinct().forEach{bin->
                Text(bin.start.format(DateTimeFormatter.ofPattern(if(period=="all")"MMM"else "MMM d",Locale.ENGLISH)),fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
            }
        }
        if(current!=null){
            HorizontalDivider(color=Color(0x20FFFFFF))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                StakIconButton({if(selected>0)selected--}){Icon(Lucide.ChevronRight,"Previous ${if(period=="all")"month"else "day"}",modifier=Modifier.size(18.dp).then(Modifier.graphicsLayer{rotationZ=180f}),tint=if(selected>0)StakText else StakTextFaint)}
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){
                    Text(current.start.format(DateTimeFormatter.ofPattern(if(period=="all")"MMMM yyyy"else "EEE, MMM d",Locale.ENGLISH)),fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=14.sp,color=StakText)
                    Text("${current.count} ${if(current.count==1)"viewing"else "viewings"}",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextSoft)
                }
                StakIconButton({if(selected<bins.lastIndex)selected++}){Icon(Lucide.ChevronRight,"Next ${if(period=="all")"month"else "day"}",modifier=Modifier.size(18.dp),tint=if(selected<bins.lastIndex)StakText else StakTextFaint)}
            }
            entries.filter{journalDay(it.watchedAt)?.let{d->!d.isBefore(current.start)&&!d.isAfter(current.end)}==true}.take(3).forEach{entry->
                Row(Modifier.fillMaxWidth().heightIn(min=44.dp).clickable{onOpen(entry.media)},verticalAlignment=Alignment.CenterVertically){
                    Text(entry.media.name,fontFamily=HankenFont,fontSize=13.sp,color=StakTextSoft,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(entry.isEpisode)Text("S${entry.media.season} E${entry.media.episode}",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
                }
            }
        }
    }
}
