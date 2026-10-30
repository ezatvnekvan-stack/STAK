package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import app.stak.nativeapp.data.MediaItem
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

@Composable fun TitleManagementPanel(media:MediaItem,inLibrary:Boolean,connected:Boolean,saved:Boolean,watchlistBusy:Boolean,trailer:Boolean,watched:Boolean,episodeLabel:String?,season:Int,seasonWatched:Boolean,watchedCount:Int,totalCount:Int,canMark:Boolean,syncNotice:String?,onTrailer:()->Unit,onWatchlist:()->Unit,onWatched:()->Unit,onSeason:()->Unit,showSeasonProgress:Boolean=true){
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            StakButton("Trailer",onTrailer,Modifier.weight(1f),icon=Lucide.Play,enabled=trailer,compact=true)
            StakButton(if(saved)"Saved"else "Watchlist",onWatchlist,Modifier.weight(1f),icon=Lucide.Bookmark,enabled=!watchlistBusy,compact=true)
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            JellyfinBadge(available=inLibrary,connected=connected)
            if(inLibrary)JellyfinQualityButton(media,available=true)
        }
        if(media.isSeries&&totalCount>0&&showSeasonProgress){
            TitleSeasonProgress(season,watchedCount,totalCount)
        }else if(!media.isSeries&&(watched||media.progress>0)){
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(if(watched)"Watched"else if(media.durationMs>media.positionMs&&media.positionMs>0)"${((media.durationMs-media.positionMs+59999)/60000)} min left"else "In progress",fontFamily=HankenFont,fontSize=12.sp,color=StakText);Text("${(if(watched)100 else (media.progress*100).toInt())}%",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)}
                StakProgress(if(watched)1f else media.progress,height=4)
            }
        }
        syncNotice?.let{Text(it,fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)}
    }
}

@Composable internal fun TitleSeasonProgress(season:Int,watchedCount:Int,totalCount:Int,showSeasonLabel:Boolean=true){
    if(totalCount<=0)return
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Text(if(showSeasonLabel)"Season ${season.toString().padStart(2,'0')}"else "$totalCount episodes",fontFamily=HankenFont,fontSize=12.sp,color=StakTextSoft)
            Text("$watchedCount / $totalCount watched",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
        }
        StakProgress(watchedCount.toFloat()/totalCount,height=3)
    }
}
