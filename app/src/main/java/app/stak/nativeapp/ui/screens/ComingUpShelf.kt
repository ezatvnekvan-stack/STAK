package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun ComingUpShelf(events:List<ReleaseEvent>,today:LocalDate,loading:Boolean,error:String?,retry:()->Unit,onOpen:(MediaItem)->Unit){
    Column(Modifier.fillMaxWidth().padding(bottom=26.dp)){
        StakSectionHeader("Coming up")
        Spacer(Modifier.height(12.dp))
        if(events.isNotEmpty()){
            BoxWithConstraints(Modifier.fillMaxWidth().testTag("coming-up-rail")){
                val cardWidth=(maxWidth*.74f).coerceIn(minOf(220.dp,maxWidth*.84f),276.dp)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    items(events,key={it.key},contentType={"upcoming-card"}){event->
                        Box(Modifier.width(cardWidth).testTag("coming-up-item-${event.key}")){
                            ComingUpCard(event,today,compact=true){onOpen(event.media)}
                        }
                    }
                }
            }
        }else if(loading)ShimmerBox(Modifier.fillMaxWidth().height(168.dp),corner=16)
        if(error!=null){Text(error,fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted);StakButton("Retry releases",retry,compact=true)}
    }
}

@Composable internal fun ComingUpCard(event:ReleaseEvent,today:LocalDate,compact:Boolean=false,onClick:()->Unit){
    val shape=RoundedCornerShape(16.dp)
    val extra=(LocalDensity.current.fontScale-1f).coerceIn(0f,1f)
    Column(Modifier.fillMaxWidth()){
        Row(Modifier.fillMaxWidth().testTag("coming-up-date"),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Box(Modifier.size(8.dp).background(if(event.date==today)StakAccent else StakTextMuted,RoundedCornerShape(4.dp))
                .then(if(event.date==today)Modifier.testTag("coming-up-today-dot")else Modifier))
            Text(if(event.date==today)"TODAY"else event.date.format(DateTimeFormatter.ofPattern("EEE",Locale.ENGLISH)).uppercase(Locale.ENGLISH),
                fontFamily=SpaceMonoFont,fontSize=10.sp,lineHeight=15.sp,fontWeight=FontWeight.Bold,color=StakText)
            Text("·",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
            Text(event.date.format(DateTimeFormatter.ofPattern("dd MMM",Locale.ENGLISH)).uppercase(Locale.ENGLISH),
                fontFamily=SpaceMonoFont,fontSize=10.sp,lineHeight=15.sp,color=StakTextSoft)
        }
        Row(Modifier.fillMaxWidth().height(((if(compact)148 else 160)+extra*48).dp)){
            Box(Modifier.width(16.dp).fillMaxHeight()){
                Box(Modifier.padding(start=3.5.dp).width(1.dp).fillMaxHeight().background(StakBorder))
            }
            Box(Modifier.weight(1f).fillMaxHeight().padding(top=8.dp).clip(shape)
                .background(StakSurface2).border(1.dp,StakBorder,shape).clickable(onClick=onClick).testTag("coming-up-card")){
                StakArtwork(CinematicArtwork.original(event.artwork ?: event.media.background ?: event.media.poster),null,
                    Modifier.fillMaxSize(),fallbackModel=CinematicArtwork.original(event.media.background ?: event.media.poster))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent,
                    .35f to Color.Transparent,.65f to Color.Black.copy(alpha=.36f),1f to Color.Black.copy(alpha=.88f))))
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                    var logoFailed by remember(event.media.logo){mutableStateOf(false)}
                    if(!event.media.logo.isNullOrBlank()&&!logoFailed){
                        AsyncImage(CinematicArtwork.original(event.media.logo),event.media.name,contentScale=ContentScale.Fit,
                            alignment=Alignment.CenterStart,onError={logoFailed=true},modifier=Modifier.widthIn(max=156.dp).fillMaxWidth(.74f).height(26.dp).testTag("coming-up-logo"))
                    }else Text(event.media.name,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=15.sp,lineHeight=19.sp,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis)
                    val episode=if(event.season!=null&&event.episode!=null)"S${event.season.toString().padStart(2,'0')} E${event.episode.toString().padStart(2,'0')}"else event.releaseLabel ?: "Release date"
                    Text(episode,fontFamily=SpaceMonoFont,fontSize=10.sp,lineHeight=14.sp,color=StakTextSoft,maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(event.season!=null&&event.episode!=null&&event.title.isNotBlank()){
                        Text(event.title,fontFamily=HankenFont,fontSize=12.sp,lineHeight=16.sp,color=StakTextSoft,maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
