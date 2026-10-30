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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import coil.compose.AsyncImage
import java.time.LocalDate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import dev.chrisbanes.haze.HazeState
import java.util.Locale

@Composable internal fun ComingUpShelf(events:List<ReleaseEvent>,today:LocalDate,loading:Boolean,error:String?,retry:()->Unit,onOpen:(MediaItem)->Unit){
    Column(Modifier.fillMaxWidth().padding(bottom=26.dp)){
        StakSectionHeader("Coming up")
        Spacer(Modifier.height(12.dp))
        if(events.isNotEmpty()){
            BoxWithConstraints(Modifier.fillMaxWidth().testTag("coming-up-rail")){
                val cardWidth=(maxWidth-12.dp)/2
                LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    items(events,key={it.key},contentType={"upcoming-card"}){event->
                        Box(Modifier.width(cardWidth).testTag("coming-up-item-${event.key}")){
                            ComingUpCard(event,today,compact=true){onOpen(event.media)}
                        }
                    }
                }
            }
        }else if(loading)Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            repeat(2){ShimmerBox(Modifier.weight(1f).height(164.dp),corner=16)}
        }
        if(error!=null){Text(error,fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted);StakButton("Retry releases",retry,compact=true)}
    }
}
@Composable internal fun ComingUpCard(event:ReleaseEvent,today:LocalDate,compact:Boolean=false,onClick:()->Unit){
    val shape=RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth().height(if(compact)164.dp else 188.dp).clip(shape).background(StakSurface2).border(1.dp,StakBorder,shape).clickable(onClick=onClick)){
        val layer=rememberGraphicsLayer()
        val source=remember(layer){GlassSource(layer,hazeState=HazeState())}
        val view=LocalView.current
        // Only artwork is sampled. The badge and its sharp text stay outside this source.
        Box(Modifier.fillMaxSize().recordGlassSource(source,view)){
            AsyncImage(event.artwork ?: event.media.background ?: event.media.poster,null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xEF08090B),Color(0xA808090B),Color(0x4508090B)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xB308090B)))))
        }
        Column(Modifier.fillMaxSize().padding(if(compact)12.dp else 18.dp)){
            ComingUpCountdown(releaseCountdown(event.date,today),source,compact)
            Spacer(Modifier.weight(1f))
            var logoFailed by remember(event.media.logo){mutableStateOf(false)}
            if(!event.media.logo.isNullOrBlank()&&!logoFailed){
                AsyncImage(event.media.logo,event.media.name,contentScale=ContentScale.Fit,alignment=Alignment.CenterStart,onError={logoFailed=true},modifier=Modifier.width(if(compact)140.dp else 218.dp).height(if(compact)40.dp else 54.dp).testTag("coming-up-logo"))
            }else Text(event.media.name,fontFamily=HankenFont,fontWeight=FontWeight.Bold,fontSize=if(compact)18.sp else 23.sp,lineHeight=if(compact)22.sp else 26.sp,color=Color.White,maxLines=2,overflow=TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            val detail=if(event.season!=null&&event.episode!=null)"S${event.season.toString().padStart(2,'0')} E${event.episode.toString().padStart(2,'0')}"+event.title.takeIf{it.isNotBlank()}?.let{" · $it"}.orEmpty()else event.releaseLabel ?: "Release date"
            Text(detail,fontFamily=HankenFont,fontSize=if(compact)11.sp else 12.sp,lineHeight=if(compact)14.sp else 16.sp,color=Color(0xE6FFFFFF),maxLines=2,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable internal fun ComingUpCountdown(label:String,source:GlassSource,compact:Boolean=false){
    GlassSurface(Modifier.testTag("coming-up-countdown"),source=source){
        Row(Modifier.padding(horizontal=if(compact)8.dp else 10.dp,vertical=if(compact)6.dp else 7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
            Box(Modifier.size(5.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFE31C2F)).testTag("coming-up-red-dot"))
            Text(label.uppercase(Locale.ENGLISH),fontFamily=SpaceMonoFont,fontWeight=FontWeight.Bold,fontSize=if(compact)9.sp else 10.sp,lineHeight=13.sp,color=Color.White)
        }
    }
}
