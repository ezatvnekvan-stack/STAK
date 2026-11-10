package app.stak.nativeapp.ui.screens

import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import coil.compose.AsyncImage
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun CalendarMonth(selected:LocalDate,weekStart:Int=1,counts:Map<LocalDate,List<ReleaseEvent>> = emptyMap(),artwork:Boolean=false,onSelect:(LocalDate)->Unit,onToday:(()->Unit)?=null) {
    val month=YearMonth.from(selected)
    val reduced=LocalReducedMotion.current
    Column(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            StakIconButton(onClick={onSelect(selected.minusMonths(1))}){Icon(Lucide.ChevronRight,"Previous month",modifier=Modifier.size(18.dp).rotate(180f),tint=StakText)}
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.ENGLISH)),fontFamily=DotoFont,fontWeight=FontWeight.Black,fontSize=20.sp,color=StakText,modifier=Modifier.weight(1f))
            onToday?.let{action->TextButton(onClick=action){Text("Today",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakText)}}
            StakIconButton(onClick={onSelect(selected.plusMonths(1))}){Icon(Lucide.ChevronRight,"Next month",modifier=Modifier.size(18.dp),tint=StakText)}
        }
        val days=if(weekStart==0)listOf("SUN","MON","TUE","WED","THU","FRI","SAT") else listOf("MON","TUE","WED","THU","FRI","SAT","SUN")
        Row {days.forEach{day->Box(Modifier.weight(1f).height(28.dp),contentAlignment=Alignment.Center){Text(day,fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)}}}
        androidx.compose.animation.AnimatedContent(month,modifier=Modifier.clipToBounds(),transitionSpec={
            val direction=if(targetState.isAfter(initialState))1 else -1
            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(if(reduced)100 else 180))+
                androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(if(reduced)0 else StakMotion.Standard,easing=StakMotion.Ease)){if(reduced)0 else direction*it/7}) togetherWith
                (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(if(reduced)100 else 130))+
                androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(if(reduced)0 else StakMotion.Standard,easing=StakMotion.Ease)){if(reduced)0 else -direction*it/7})
        },label="calendar-month"){visibleMonth->CalendarMonthGrid(visibleMonth,selected,weekStart,counts,artwork,onSelect)}
    }
}

@Composable private fun CalendarMonthGrid(month:YearMonth,selected:LocalDate,weekStart:Int,counts:Map<LocalDate,List<ReleaseEvent>>,artwork:Boolean,onSelect:(LocalDate)->Unit){
    val cells=remember(month,weekStart){calendarCells(month,weekStart)}
    val today=LocalDate.now();val reduced=LocalReducedMotion.current
    BoxWithConstraints(Modifier.fillMaxWidth()){
        val cellWidth=maxWidth/7
        val chosen=cells.indexOf(selected)
        val x=androidx.compose.animation.core.animateDpAsState(cellWidth*(chosen.coerceAtLeast(0)%7),if(reduced)androidx.compose.animation.core.snap()else androidx.compose.animation.core.tween(StakMotion.Quick,easing=StakMotion.Ease),label="calendar-day-x")
        val y=androidx.compose.animation.core.animateDpAsState(48.dp*(chosen.coerceAtLeast(0)/7),if(reduced)androidx.compose.animation.core.snap()else androidx.compose.animation.core.tween(StakMotion.Quick,easing=StakMotion.Ease),label="calendar-day-y")
        if(chosen>=0)Box(Modifier.offset{x.value.roundToPx().let{left->androidx.compose.ui.unit.IntOffset(left,y.value.roundToPx())}}.width(cellWidth).height(48.dp).padding(2.dp).border(1.dp,StakAccent.copy(alpha=.5f),RoundedCornerShape(8.dp)).background(StakAccent.copy(alpha=.12f),RoundedCornerShape(8.dp)))
        Column {
            cells.chunked(7).forEach{week->Row {
                week.forEach{date->
                    val events=counts[date].orEmpty();val active=date==selected
                    Box(Modifier.weight(1f).height(48.dp).padding(2.dp).clip(RoundedCornerShape(8.dp)).semantics{contentDescription="${date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy",Locale.ENGLISH))}, ${events.size} releases";this.selected=active}.clickable{onSelect(date)},contentAlignment=Alignment.Center){
                        if(artwork&&events.isNotEmpty()&&!active){StakArtwork(events.first().media.poster,null,Modifier.fillMaxSize());Box(Modifier.fillMaxSize().background(Color(0x99000000)))}
                        Text(date.dayOfMonth.toString(),fontFamily=SpaceMonoFont,fontWeight=if(active||date==today)FontWeight.Bold else FontWeight.Normal,fontSize=13.sp,color=if(active)StakText else if(date.month==month.month)StakTextSoft else StakTextFaint)
                        if(date==today)Box(Modifier.align(Alignment.TopCenter).padding(top=3.dp).size(3.dp).background(StakText,CircleShape))
                        if(events.isNotEmpty())Row(Modifier.align(Alignment.BottomCenter).padding(bottom=3.dp),horizontalArrangement=Arrangement.spacedBy(2.dp)){repeat(events.size.coerceAtMost(3)){Box(Modifier.size(3.dp).background(StakAccent,CircleShape))}}
                    }
                }
            }}
        }
    }
}

@Composable internal fun ReleaseCalendar(followed:List<MediaItem>,preferences:JournalPreferences,onPreferences:(JournalPreferences)->Unit,onOpenTitle:(MediaItem)->Unit,viewModel:CalendarViewModel) {
    val selected=runCatching{LocalDate.parse(preferences.date)}.getOrDefault(LocalDate.now());val month=YearMonth.from(selected)
    val feed by viewModel.feed.collectAsState();val loading by viewModel.loading.collectAsState();val error by viewModel.error.collectAsState()
    var filters by remember{mutableStateOf(false)};val key=NativeRuntime.settings.tmdbKey.orEmpty()
    val identities=followed.map{"${it.type}:${it.mediaKey}"}
    LaunchedEffect(identities,month,preferences.weekStart,key){viewModel.refresh(followed,month,preferences.weekStart)}
    val events=feed?.events.orEmpty().filter{preferences.calendarType=="all"||it.media.type==preferences.calendarType};val dates=events.groupBy{it.date};val day=dates[selected].orEmpty()
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={filters=true},modifier=Modifier.weight(1f)){Text("Following ▾",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)}
            IconButton(onClick={filters=true}){Icon(Lucide.Sliders,"Calendar filters",tint=StakText,modifier=Modifier.size(18.dp))}
        }
        CalendarMonth(selected,preferences.weekStart,dates,preferences.artwork,{onPreferences(preferences.copy(date=it.toString()))},{onPreferences(preferences.copy(date=LocalDate.now().toString()))})
        if(loading)Text("Loading schedule…",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
        Text((if(selected==LocalDate.now())"Today · " else "")+selected.format(DateTimeFormatter.ofPattern("EEE, MMM d",Locale.ENGLISH)),fontFamily=DotoFont,fontWeight=FontWeight.Black,fontSize=19.sp,color=StakText)
        if(feed!=null&&day.isEmpty())Text(if(followed.isEmpty())"Add movies or series to your watchlist to follow their releases." else "No releases for this day.",fontFamily=HankenFont,fontSize=13.sp,color=StakTextMuted)
        day.forEach{event->PosterMenuAnchor(event.media,{onOpenTitle(event.media.copy(season=event.season,episode=event.episode))}){posterGestures->Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(posterGestures).padding(vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            LibraryArtwork(event.media.poster,event.media.name,Modifier.width(42.dp).height(63.dp))
            Column(Modifier.weight(1f)) {Text(event.media.name,fontFamily=HankenFont,fontWeight=FontWeight.Bold,fontSize=14.sp,lineHeight=19.sp,color=StakText);Text(if(event.media.isSeries)"S${event.season} E${event.episode} · ${event.title}" else "Movie release",fontFamily=SpaceMonoFont,fontSize=10.sp,lineHeight=16.sp,color=StakTextMuted)}
        }}}
    }
    if(filters)GlassAlertDialog(onDismissRequest={filters=false},containerColor=StakSurface,title={Text("Calendar filters",fontFamily=DotoFont)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        DiscoverySelect("Releases",preferences.calendarType,listOf("all" to "All","series" to "Episodes","movie" to "Movies"),{onPreferences(preferences.copy(calendarType=it))})
        DiscoverySelect("Week starts",preferences.weekStart.toString(),listOf("1" to "Monday","0" to "Sunday"),{onPreferences(preferences.copy(weekStart=it.toInt()))})
        Row(verticalAlignment=Alignment.CenterVertically){Text("Artwork in calendar",modifier=Modifier.weight(1f));Switch(preferences.artwork,{onPreferences(preferences.copy(artwork=it))})}
        Text("Following uses your watchlist and tracked titles. Red dots mark releases; a white dot marks today. Release dates may change.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
    }},confirmButton={StakButton("Done",{filters=false})},dismissButton={StakButton("Reset",{onPreferences(preferences.copy(calendarType="all",weekStart=1,artwork=false))})})
}

