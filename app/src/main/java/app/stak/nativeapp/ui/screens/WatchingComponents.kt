package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException

@Composable internal fun WatchingTitleRow(row:WatchingRow,onOpen:()->Unit,onMark:()->Unit,onMenu:()->Unit){
    Column{
        Row(Modifier.testTag("watching-title-${row.key}").fillMaxWidth().heightIn(min=84.dp).clickable(onClick=onOpen).padding(vertical=9.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
            LibraryArtwork(row.media.poster,row.media.name,Modifier.width(42.dp).height(63.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Text(row.media.name,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=15.sp,lineHeight=20.sp,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis)
                val subtitle=if(row.media.isSeries)row.episode?.let{"${if(row.resume!=null)"Resume"else "Next"}: S${it.season.toString().padStart(2,'0')} E${it.episode.toString().padStart(2,'0')}"}?:if(row.series?.released?.isEmpty()==true&&row.series.plan.episodes.isNotEmpty())"No released episodes"else "Choose an episode" else movieResumeLabel(row.resume)
                Text(subtitle,fontFamily=SpaceMonoFont,fontSize=11.sp,lineHeight=16.sp,color=StakTextSoft)
                val resume=row.resume
                if(!row.media.isSeries&&resume!=null&&resume.positionMs>0&&resume.durationMs>0)Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0x22FFFFFF))){Box(Modifier.fillMaxWidth((resume.positionMs.toFloat()/resume.durationMs).coerceIn(0f,1f)).fillMaxHeight().background(StakText))}
            }

            IconButton(onClick=onMenu,modifier=Modifier.size(36.dp)){Icon(Icons.Default.MoreVert,"Watching options for ${row.media.name}",tint=StakTextMuted,modifier=Modifier.size(17.dp))}
        }
        HorizontalDivider(color=Color(0x18FFFFFF))
    }
}
/** Shared cinematic artwork; missing art remains a quiet native surface. */
@Composable internal fun TrackingArtwork(media:MediaItem?,modifier:Modifier=Modifier){
    Box(modifier.background(Color(0xFF101216))){
        val images=remember(media?.background,media?.poster){listOfNotNull(media?.background,media?.poster).filter{it.isNotBlank()}.distinct()}
        var index by remember(images){mutableIntStateOf(0)}
        val image=images.getOrNull(index)
        if(image!=null)AsyncImage(image,null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize(),onError={if(index<images.size)index++})
        else LibraryArtwork(null,media?.name.orEmpty(),Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha=.52f),Color.Black.copy(alpha=.12f)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.48f)))))
    }
}
@Composable internal fun RecentlyWatchedCard(group:HistoryGroup,onOpen:()->Unit){
    val media=group.first.media
    Column(Modifier.width(166.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick=onOpen).semantics{contentDescription="Recent viewing: ${media.name}, ${group.subtitle}, ${historyDateLabel(journalDay(group.first.watchedAt))}"}){
        Box(Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(12.dp)).border(1.dp,Color(0x25FFFFFF),RoundedCornerShape(12.dp))){
            TrackingArtwork(media,Modifier.fillMaxSize())
            Text(group.subtitle,fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakText,modifier=Modifier.align(Alignment.BottomStart).padding(10.dp),maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Text(media.name,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=18.sp,color=StakText,modifier=Modifier.padding(top=8.dp),maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(historyDateLabel(journalDay(group.first.watchedAt)),fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted,modifier=Modifier.padding(top=3.dp,bottom=4.dp))
    }
}
private fun insightRuntime(insights:ViewingInsights):String=if(insights.knownRuntimeTitles==0&&insights.movies+insights.episodes>0)"—"else {
    val minutes=insights.knownMinutes
    (if(minutes>=60)"${minutes/60}h ${minutes%60}m"else "${minutes}m")+(if(insights.runtimeIncomplete)"+"else "")
}
@Composable internal fun WatchingMonthSummary(insights:ViewingInsights,artwork:MediaItem?=null,today:LocalDate=LocalDate.now(),onOpen:()->Unit){
    Box(Modifier.fillMaxWidth().heightIn(min=98.dp).clip(RoundedCornerShape(16.dp)).border(1.dp,Color(0x32FFFFFF),RoundedCornerShape(16.dp)).clickable(onClick=onOpen).semantics{contentDescription="View detailed stats"}){
        TrackingArtwork(artwork,Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text("MONTH IN REVIEW",fontFamily=DotoFont,fontWeight=FontWeight.Bold,fontSize=16.sp,color=StakText,modifier=Modifier.weight(1f))
                Icon(Lucide.ChevronRight,null,tint=StakTextSoft,modifier=Modifier.size(16.dp))
            }
            Text(today.format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.ENGLISH)),fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextSoft)
            Text("${insights.movies} ${if(insights.movies==1)"movie"else "movies"} · ${insights.episodes} ${if(insights.episodes==1)"episode"else "episodes"} · ${insightRuntime(insights)} est.",fontFamily=HankenFont,fontSize=13.sp,lineHeight=18.sp,color=StakText,maxLines=2)
        }
    }
}
@Composable internal fun WatchingHabits(entries:List<JournalEntry>,today:LocalDate,onOpen:()->Unit){
    val watches=remember(entries){individualWatches(entries)}
    val all=remember(watches,today){viewingInsights(watches,today=today)}
    val recent=remember(watches,today){filterJournal(watches,JournalPreferences(period="week"),today)}
    val week=remember(recent,today){viewingInsights(recent,today=today)}
    val counts=remember(watches,today){(6L downTo 0L).map{days->watches.count{journalDay(it.watchedAt)==today.minusDays(days)}}}
    Column{
        HabitRow("${all.streakDays}","day streak",counts,true,onOpen)
        HabitRow(insightRuntime(week),"Est. runtime · last 7 days",counts,false,onOpen)
    }
}
@Composable private fun HabitRow(value:String,label:String,counts:List<Int>,streak:Boolean,onOpen:()->Unit){
    Row(Modifier.fillMaxWidth().heightIn(min=66.dp).clickable(onClick=onOpen).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Box(Modifier.size(32.dp).border(1.dp,StakAccent.copy(alpha=.65f),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){
            Icon(if(streak)Lucide.Zap else Icons.Default.Schedule,null,tint=StakAccent,modifier=Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
            NothingNumber(value,style=androidx.compose.ui.text.TextStyle(fontFamily=DotoFont,fontWeight=FontWeight.Black,fontSize=25.sp,lineHeight=29.sp,color=StakText))
            Text(label,fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)
        }
        val reveal=rememberNothingReveal("habit:$streak:$counts",duration=420)
        Canvas(Modifier.width(74.dp).height(26.dp)){
            val step=size.width/7;val peak=counts.maxOrNull()?.coerceAtLeast(1)?:1
            counts.forEachIndexed{i,count->
                val rows=if(streak)5 else (1+4f*count/peak).toInt()
                repeat(rows){r->repeat(2){c->
                    val ink=if(count==0)Color.White.copy(alpha=.13f)else if(i==6)StakAccent else Color.White.copy(alpha=.75f)
                    val shown=(reveal.value*9-i*.35f-r).coerceIn(0f,1f)
                    drawRect(ink.copy(alpha=ink.alpha*shown),androidx.compose.ui.geometry.Offset(i*step+c*3.dp.toPx(),size.height-2.dp.toPx()-r*4.dp.toPx()),androidx.compose.ui.geometry.Size(2.dp.toPx(),2.dp.toPx()))
                }}
            }
        }
        Icon(Lucide.ChevronRight,null,tint=StakTextMuted,modifier=Modifier.size(16.dp))
    }
    HorizontalDivider(color=Color(0x20FFFFFF))
}

