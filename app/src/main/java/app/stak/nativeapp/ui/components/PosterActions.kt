package app.stak.nativeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.ui.theme.*

private val JellyfinMark=ImageVector.Builder("Jellyfin",24.dp,24.dp,64f,64f).apply {
    listOf("M28 8Q32 2 36 8L60 50Q64 58 55 58H9Q0 58 4 50Z","M30 25Q32 21 34 25L47 47H17Z","M32 34L38 44H26Z").forEach{path->addPath(PathParser().parsePathString(path).toNodes(),fill=null,stroke=SolidColor(Color.White),strokeLineWidth=3f,strokeLineJoin=StrokeJoin.Round,strokeLineCap=StrokeCap.Round)}
}.build()
@Composable fun JellyfinLogo(modifier:Modifier=Modifier,tint:Color=Color.White,description:String?="Available on Jellyfin") {Icon(JellyfinMark,description,tint=tint,modifier=modifier)}
@Composable fun JellyfinBadge(compact:Boolean=false,available:Boolean=true,connected:Boolean=true) {
    val foreground=if(available)Color.White else if(connected)Color(0xFFFF8585) else StakTextMuted
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        JellyfinLogo(Modifier.size(if(compact)15.dp else 17.dp),foreground,if(compact)"Available on Jellyfin" else null)
        if(!compact)Text(if(available)"In library" else if(connected)"Not in library" else "Jellyfin disconnected",fontFamily=HankenFont,fontWeight=FontWeight.Medium,fontSize=11.sp,lineHeight=16.sp,color=foreground)
    }
}

data class PosterActions(
    val isWatchlisted:(app.stak.nativeapp.data.MediaItem)->Boolean,
    val isWatched:(app.stak.nativeapp.data.MediaItem)->Boolean,
    val isInJellyfin:(app.stak.nativeapp.data.MediaItem)->Boolean,
    val toggleWatchlist:(app.stak.nativeapp.data.MediaItem)->Unit,
    val setWatched:(app.stak.nativeapp.data.MediaItem,Boolean)->Unit = {_,_->}
)
val LocalPosterActions=androidx.compose.runtime.staticCompositionLocalOf<PosterActions?>{null}
@Composable fun PosterContextMenu(item:app.stak.nativeapp.data.MediaItem,expanded:Boolean,onDismiss:()->Unit,onOpen:()->Unit) {
    if(!expanded)return
    val actions=LocalPosterActions.current
    GlassDialog(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){
                Text(item.name,Modifier.weight(1f),fontFamily=HankenFont,fontSize=14.sp,color=StakTextMuted,maxLines=2)
                androidx.compose.material3.IconButton(onClick=onDismiss){Icon(Lucide.X,"Close poster menu",tint=Color.White)}
            }
            Spacer(Modifier.height(8.dp))
            app.stak.nativeapp.ui.screens.JellyfinQualityButton(item)
            PosterMenuAction("View details",Lucide.Film){onDismiss();onOpen()}
            actions?.let{callbacks->
                val saved=callbacks.isWatchlisted(item);val watched=callbacks.isWatched(item)
                PosterMenuAction(if(saved)"Remove from watchlist" else "Add to watchlist",Lucide.Bookmark){onDismiss();callbacks.toggleWatchlist(item)}
            }
        }
    }
}
@Composable private fun PosterMenuAction(label:String,icon:ImageVector,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=14.dp,horizontal=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
        Icon(icon,null,tint=Color.White,modifier=Modifier.size(19.dp))
        Text(label,fontFamily=SpaceMonoFont,fontSize=12.sp,lineHeight=18.sp,color=StakText)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable fun PosterMenuAnchor(item:app.stak.nativeapp.data.MediaItem,onOpen:()->Unit,content:@Composable (Modifier)->Unit) {
    var expanded by androidx.compose.runtime.remember(item.mediaKey){androidx.compose.runtime.mutableStateOf(false)}
    Box {
        content(Modifier.combinedClickable(onClick=onOpen,onLongClick={expanded=true}))
        PosterContextMenu(item,expanded,{expanded=false},onOpen)
    }
}
