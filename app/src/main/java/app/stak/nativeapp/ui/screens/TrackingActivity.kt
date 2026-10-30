package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

@Composable internal fun TrackingActivity(slices:List<ActivitySlice>,monthly:Boolean,onMonthly:(Boolean)->Unit,entries:List<JournalEntry> = emptyList(),onOpen:(MediaItem)->Unit={},today:LocalDate=LocalDate.now()){
    var chosen by remember(slices,monthly){mutableIntStateOf(slices.indexOfLast{it.count>0}.coerceAtLeast(0))}
    val current=slices.getOrNull(chosen)
    var day by remember(current?.start,monthly){mutableStateOf<LocalDate?>(null)}
    fun periodName(slice:ActivitySlice)=if(monthly)activityRangeLabel(slice,true)else "Week ${slice.start.get(WeekFields.ISO.weekOfWeekBasedYear())}, ${slice.start.get(WeekFields.ISO.weekBasedYear())}"
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp,Color(0x28FFFFFF),RoundedCornerShape(16.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text("Activity",fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=17.sp,color=StakText,modifier=Modifier.weight(1f))
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                ActivityMode("Weekly",!monthly){onMonthly(false)}
                ActivityMode("Monthly",monthly){onMonthly(true)}
            }
        }
        if(current==null)Text("No dated activity",fontFamily=HankenFont,fontSize=13.sp,color=StakTextMuted)else{
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={chosen--},enabled=chosen>0,modifier=Modifier.size(44.dp)){Icon(Icons.Default.ChevronLeft,"Previous ${if(monthly)"month"else "week"}",tint=if(chosen>0)StakText else StakTextMuted.copy(alpha=.3f))}
                DiscoverySelect("",chosen.toString(),slices.mapIndexed{i,slice->i.toString() to if(monthly)periodName(slice)else "${periodName(slice)} · ${activityRangeLabel(slice,false)}"},{chosen=it.toInt()},Modifier.weight(1f),accessibilityLabel=if(monthly)"Activity month"else "Activity week",selectedText=periodName(current))
                IconButton(onClick={chosen++},enabled=chosen<slices.lastIndex,modifier=Modifier.size(44.dp)){Icon(Icons.Default.ChevronRight,"Next ${if(monthly)"month"else "week"}",tint=if(chosen<slices.lastIndex)StakText else StakTextMuted.copy(alpha=.3f))}
            }
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
                if(!monthly)Text(activityRangeLabel(current,monthly),fontFamily=HankenFont,fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=StakText)
                Text("${current.count} ${if(current.count==1)"viewing"else "viewings"}",fontFamily=HankenFont,fontSize=14.sp,color=StakTextSoft)
            }
            val peak=slices.maxOf{it.count}.coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(76.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.Bottom){slices.forEachIndexed{index,slice->
                Box(Modifier.weight(1f).fillMaxHeight().clickable{chosen=index}.semantics{contentDescription=if(monthly)"Month of ${activityLabel(slice,true)}: ${slice.count} watched"else "Week of ${weekLabel(slice.start)}: ${slice.count} watched";selected=index==chosen},contentAlignment=Alignment.BottomCenter){
                    NothingActivityColumn(slice.count,peak,index==chosen)
                }
            }}
            if(slices.size>1)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Text(if(monthly)slices.first().start.format(DateTimeFormatter.ofPattern("MMM",Locale.ENGLISH))else weekLabel(slices.first().start),fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
                Text(if(monthly)slices.last().start.format(DateTimeFormatter.ofPattern("MMM",Locale.ENGLISH))else weekLabel(slices.last().start),fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
            }
            val days=remember(current,entries,today){activityDays(current,entries,today=today)}
            if(!monthly){
                LazyRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    items(days,key={it.date.toString()}){item->
                        Column(Modifier.width(44.dp).clip(RoundedCornerShape(10.dp)).border(1.dp,if(day==item.date)StakText else Color(0x26FFFFFF),RoundedCornerShape(10.dp)).background(if(day==item.date)Color(0x14FFFFFF)else Color.Transparent).selectable(selected=day==item.date,enabled=!item.date.isAfter(today),role=Role.Button,onClick={day=if(day==item.date)null else item.date}).semantics{contentDescription="${item.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy",Locale.ENGLISH))}: ${item.count} viewings"}.padding(vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){
                            Text(item.date.format(DateTimeFormatter.ofPattern("EEE",Locale.ENGLISH)),fontFamily=HankenFont,fontSize=11.sp,color=StakTextSoft)
                            Text(item.date.dayOfMonth.toString(),fontFamily=HankenFont,fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=StakText)
                            Text(if(item.count==0)"—"else item.count.toString(),fontFamily=HankenFont,fontSize=12.sp,color=if(item.count>0)StakAccent else StakTextMuted)
                        }
                    }
                }
            }else{
                DiscoverySelect("",day?.toString().orEmpty(),listOf("" to "Choose a day")+days.filter{it.count>0}.map{it.date.toString() to "${it.date.format(DateTimeFormatter.ofPattern("EEE, MMM d",Locale.ENGLISH))} · ${it.count}"},{day=it.takeIf{it.isNotBlank()}?.let(LocalDate::parse)},Modifier.fillMaxWidth(),accessibilityLabel="Activity day")
            }
            day?.let{date->
                HorizontalDivider(color=Color(0x20FFFFFF))
                Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMM d",Locale.ENGLISH)),fontFamily=HankenFont,fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=StakText)
                val viewings=watchesOn(entries,date)
                if(viewings.isEmpty())Text("No viewings that day",fontFamily=HankenFont,fontSize=13.sp,color=StakTextMuted)
                viewings.take(5).forEach{entry->Row(Modifier.fillMaxWidth().heightIn(min=44.dp).clickable{onOpen(entry.media)},verticalAlignment=Alignment.CenterVertically){
                    Text(entry.media.name,fontFamily=HankenFont,fontSize=13.sp,color=StakTextSoft,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(entry.isEpisode)Text("S${entry.media.season} E${entry.media.episode}",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
                }}
                if(viewings.size>5)Text("+${viewings.size-5} more in History",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
            }
        }
    }
}
@Composable private fun ActivityMode(label:String,active:Boolean,onClick:()->Unit){
    Box(Modifier.heightIn(min=44.dp).clip(RoundedCornerShape(10.dp)).border(1.dp,if(active)Color(0xAAFFFFFF)else Color(0x32FFFFFF),RoundedCornerShape(10.dp)).selectable(selected=active,role=Role.Tab,onClick=onClick).padding(horizontal=10.dp),contentAlignment=Alignment.Center){Text(label,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=12.sp,color=if(active)StakText else StakTextMuted)}
}

/** Discrete monochrome marks, restrained red selection, no gradient or animated blur. */
@Composable private fun NothingActivityColumn(count:Int,peak:Int,chosen:Boolean){
    val tint=if(chosen)StakAccent else StakText.copy(alpha=.6f)
    Canvas(Modifier.widthIn(max=30.dp).fillMaxWidth().height(70.dp)){
        val step=4.dp.toPx();val dot=2.dp.toPx();val columns=(size.width/step).toInt().coerceAtLeast(1)
        val filled=if(count<=0)1 else (2+14f*count/peak.coerceAtLeast(1)).toInt()
        repeat(filled){row->repeat(columns){column->drawRect(if(count==0)Color.White.copy(alpha=.16f)else tint,topLeft=androidx.compose.ui.geometry.Offset((size.width-columns*step)/2+column*step,size.height-dot-row*step),size=androidx.compose.ui.geometry.Size(dot,dot))}}
    }
}
