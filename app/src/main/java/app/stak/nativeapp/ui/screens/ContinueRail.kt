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
import coil.compose.AsyncImage
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Composable internal fun ContinueRail(items:List<MediaItem>,
    artworkLoader:suspend(MediaItem)->ContinueArtwork={DirectCatalog().continueArtwork(it,::continueSeriesMetadata)},onOpenTitle:(MediaItem)->Unit){
    val scope=credentialScope(NativeRuntime.settings.tmdbKey.orEmpty())+jellyfinScope()
    val permits=remember{Semaphore(3)}
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("continue-rail")){
        val width=continueCardWidth(maxWidth.value).dp
        val height=width/1.6f
        LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            items(items,key={it.jellyfinId?:it.mediaKey}){item->
                val fallback=ContinueArtwork(item.continueBackground,item.logo)
                val art by produceState(fallback,item.type,item.mediaKey,scope,item.background,item.jellyfinSeriesId,item.jellyfinProviderKeys){
                    value=try{permits.withPermit{artworkLoader(item)}}catch(c:CancellationException){throw c}catch(_:Exception){fallback}
                }
                ContinueCard(item,art,width,height){onOpenTitle(item)}
            }
        }
    }
}

/** A read-only, account-scoped parent lookup, used only when public show identity is missing. */
private suspend fun continueSeriesMetadata(id:String):MediaItem? {
    val settings=NativeRuntime.settings
    if(!settings.jellyfinConnected)return null
    val scope=jellyfinScope(settings)
    val user=settings.jellyfinUserId.orEmpty()
    val server=DirectJellyfin.normalizeServer(settings.jellyfinUrl.orEmpty())
    val data=DirectJellyfin.sessionRequest("/Users/$user/Items/$id",expectedScope=scope)
    if(data.text("Id")!=id||data.text("Type")!="Series")return null
    return jellyfinMediaItem(data,server)
}

@Composable private fun ContinueCard(item:MediaItem,art:ContinueArtwork,width:androidx.compose.ui.unit.Dp,height:androidx.compose.ui.unit.Dp,onOpen:()->Unit){
    var menu by remember(item.mediaKey){mutableStateOf(false)}
    var logoFailed by remember(art.logo){mutableStateOf(false)}
    var artFailed by remember(art.image){mutableStateOf(false)}
    val press=rememberPressMotion()
    Column(Modifier.width(width).testTag("continue-card").semantics{contentDescription=item.name}
        .pressMotion(press)
        .combinedClickable(interactionSource=press.source,indication=LocalIndication.current,onClick=onOpen,onLongClick={menu=true})){
        Box(Modifier.fillMaxWidth().height(height).testTag("continue-image").clip(RoundedCornerShape(16.dp)).background(StakSurface)
            .border(1.dp,Color.White.copy(alpha=.14f),RoundedCornerShape(16.dp))){
        val fallback=item.continueBackground?.takeIf{it!=art.image}
        StakArtwork(art.image,null,Modifier.fillMaxSize(),fallbackModel=fallback,onLoadFailure={artFailed=true})
        if(!art.embeddedTitle||artFailed)Box(Modifier.align(Alignment.CenterStart)
            .padding(start=12.dp,end=18.dp,bottom=16.dp).fillMaxWidth(.8f)){
            if(art.logo!=null&&!logoFailed)AsyncImage(art.logo,null,Modifier.fillMaxWidth().height(38.dp),contentScale=ContentScale.Fit,alignment=Alignment.CenterStart,onError={logoFailed=true})
            else Text(item.name,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=18.sp,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
        if(!item.nextUp&&item.progress>0)Box(Modifier.align(Alignment.BottomStart).padding(12.dp)){
            StakProgress(item.progress,Modifier.width((width*.28f).coerceIn(60.dp,100.dp)).testTag("continue-progress"),height=3)
        }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal=2.dp,vertical=8.dp)){
            val episode=if(item.isSeries)listOfNotNull(item.season?.let{"S${it.toString().padStart(2,'0')}"},item.episode?.let{"E${it.toString().padStart(2,'0')}"}).joinToString(" · ")else ""
            val remaining=if(!item.nextUp&&item.durationMs>item.positionMs)"${((item.durationMs-item.positionMs+59999)/60000)} min left"else null
            Text(listOfNotNull(episode.takeIf{it.isNotBlank()},remaining).joinToString(" · ").ifBlank{if(item.isSeries)"Episode"else "Continue watching"},Modifier.testTag("continue-info"),fontFamily=SpaceMonoFont,fontSize=10.sp,lineHeight=15.sp,color=StakTextMuted,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
        PosterContextMenu(item,menu,{menu=false},onOpen)
    }
}
