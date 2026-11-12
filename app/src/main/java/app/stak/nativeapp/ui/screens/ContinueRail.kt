@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Composable internal fun ContinueRail(items:List<MediaItem>,
    artworkLoader:suspend(MediaItem)->ContinueArtwork={DirectCatalog().continueArtwork(it)},onOpenTitle:(MediaItem)->Unit){
    val scope=credentialScope(NativeRuntime.settings.tmdbKey.orEmpty())+jellyfinScope()
    val permits=remember{Semaphore(3)}
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("continue-rail")){
        val width=(maxWidth*.74f).coerceIn(minOf(220.dp,maxWidth*.84f),276.dp)
        val height=(148f+(androidx.compose.ui.platform.LocalDensity.current.fontScale-1f).coerceIn(0f,1f)*36f).dp
        LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            items(items,key={it.jellyfinId?:it.mediaKey}){item->
                val fallback=ContinueArtwork(item.continueBackground)
                val art by produceState(fallback,item.type,item.mediaKey,scope,item.background,item.jellyfinSeriesId,item.jellyfinProviderKeys){
                    value=try{permits.withPermit{artworkLoader(item)}}catch(c:CancellationException){throw c}catch(_:Exception){fallback}
                }
                ContinueCard(item,art,width,height){onOpenTitle(item)}
            }
        }
    }
}

@Composable private fun ContinueCard(item:MediaItem,art:ContinueArtwork,width:androidx.compose.ui.unit.Dp,height:androidx.compose.ui.unit.Dp,onOpen:()->Unit){
    var menu by remember(item.mediaKey){mutableStateOf(false)}
    val press=rememberPressMotion()
    Box(Modifier.width(width).height(height).testTag("continue-card").semantics{contentDescription=item.name}
        .pressMotion(press).clip(RoundedCornerShape(16.dp)).background(StakSurface)
        .border(1.dp,Color.White.copy(alpha=.18f),RoundedCornerShape(16.dp))
        .combinedClickable(interactionSource=press.source,indication=LocalIndication.current,onClick=onOpen,onLongClick={menu=true})){
        StakArtwork(art.image,null,Modifier.fillMaxSize().testTag("continue-image"),fallbackModel=item.continueImageFallback)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent,.35f to Color.Black.copy(alpha=.04f),.67f to Color.Black.copy(alpha=.40f),1f to Color.Black.copy(alpha=.88f))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
            Text(item.name,Modifier.fillMaxWidth(.76f).widthIn(max=184.dp).testTag("continue-wordmark"),
                fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=15.sp,lineHeight=18.sp,
                letterSpacing=.3.sp,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text(continueInformation(item),Modifier.weight(1f).testTag("continue-info"),fontFamily=SpaceMonoFont,fontSize=10.sp,lineHeight=14.sp,color=StakTextSoft,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            if(!item.nextUp)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                StakProgress(item.progress,Modifier.width((width*.28f).coerceIn(60.dp,100.dp)).testTag("continue-progress"),height=3)
                Spacer(Modifier.weight(1f))
                Text("${(item.progress.coerceIn(0f,1f)*100).toInt()}%",fontFamily=SpaceMonoFont,fontSize=9.sp,color=StakTextSoft)
            }
            else Spacer(Modifier.height(3.dp))
        }
        PosterContextMenu(item,menu,{menu=false},onOpen)
    }
}

internal fun continueInformation(item:MediaItem):String {
    val episode=if(item.isSeries)listOfNotNull(item.season?.let{"S${it.toString().padStart(2,'0')}"},item.episode?.let{"E${it.toString().padStart(2,'0')}"}).joinToString(" ")else ""
    val left=if(!item.nextUp&&item.durationMs>0)"${((item.durationMs-item.positionMs).coerceAtLeast(0)+59999)/60000} min left"else ""
    return listOf(if(item.nextUp)"NEXT UP"else "IN PROGRESS",episode,left).filter{it.isNotBlank()}.joinToString(" · ")
}
