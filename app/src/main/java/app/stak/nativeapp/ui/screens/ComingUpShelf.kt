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
                val cardWidth=if(events.size==1)maxWidth else maxWidth*.86f
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
    val surface=StakSurface2
    Box(Modifier.fillMaxWidth().height(((if(compact)168 else 188)+extra*64).dp).clip(shape)
        .background(surface).border(1.dp,StakBorder,shape).clickable(onClick=onClick).testTag("coming-up-card")){
        StakArtwork(CinematicArtwork.original(event.artwork ?: event.media.background ?: event.media.poster),null,
            Modifier.fillMaxSize(),fallbackModel=CinematicArtwork.original(event.media.background ?: event.media.poster))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to surface,.23f to surface,.46f to surface.copy(alpha=.72f),.75f to surface.copy(alpha=.14f),1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent,.50f to Color.Black.copy(alpha=.08f),1f to Color.Black.copy(alpha=.72f))))
        Row(Modifier.fillMaxSize().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)){
            Column(Modifier.width((78+extra*42).dp).fillMaxHeight().testTag("coming-up-date"),verticalArrangement=Arrangement.Center){
                if(event.date==today)Box(Modifier.padding(bottom=9.dp).size(5.dp).background(StakAccent,RoundedCornerShape(3.dp)).testTag("coming-up-today-dot"))
                Text(if(event.date==today)"TODAY"else event.date.format(DateTimeFormatter.ofPattern("EEE",Locale.ENGLISH)).uppercase(Locale.ENGLISH),
                    fontFamily=DotoFont,fontSize=22.sp,lineHeight=27.sp,fontWeight=FontWeight.Black,color=StakText,maxLines=1)
                Spacer(Modifier.height(5.dp))
                Text(event.date.format(DateTimeFormatter.ofPattern("dd MMM",Locale.ENGLISH)).uppercase(Locale.ENGLISH),
                    fontFamily=SpaceMonoFont,fontSize=10.sp,lineHeight=15.sp,color=StakTextSoft)
            }
            Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.Bottom){
                var logoFailed by remember(event.media.logo){mutableStateOf(false)}
                if(!event.media.logo.isNullOrBlank()&&!logoFailed){
                    AsyncImage(CinematicArtwork.original(event.media.logo),event.media.name,contentScale=ContentScale.Fit,
                        alignment=Alignment.CenterStart,onError={logoFailed=true},modifier=Modifier.widthIn(max=160.dp).fillMaxWidth(.88f).height(32.dp).testTag("coming-up-logo"))
                }else Text(event.media.name,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=18.sp,lineHeight=22.sp,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis)
                Spacer(Modifier.height(9.dp))
                val episode=if(event.season!=null&&event.episode!=null)"S${event.season.toString().padStart(2,'0')} E${event.episode.toString().padStart(2,'0')}"else event.releaseLabel ?: "Release date"
                Text(episode,fontFamily=SpaceMonoFont,fontSize=10.sp,lineHeight=15.sp,color=StakTextSoft,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(event.season!=null&&event.episode!=null&&event.title.isNotBlank()){
                    Spacer(Modifier.height(3.dp))
                    Text(event.title,fontFamily=HankenFont,fontSize=12.sp,lineHeight=16.sp,color=StakTextSoft,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
}
