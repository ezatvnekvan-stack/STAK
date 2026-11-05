@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

@Composable internal fun LibraryHeader(onZurg:()->Unit,actions:@Composable (()->Unit)->Unit){
    var menu by remember{mutableStateOf(false)}
    StakPageHeader("Library",trailing={
        Row {
            StakIconButton(onZurg){Icon(Lucide.MonitorPlay,"Zurg browser",tint=StakText,modifier=Modifier.size(19.dp))}
            Box {
                StakIconButton({menu=true}){Icon(Icons.Default.MoreVert,"Library actions",tint=StakText,modifier=Modifier.size(21.dp))}
                GlassDropdownMenu(menu,{menu=false}){actions{menu=false}}
            }
        }
    })
}

@Composable internal fun LibraryChoice(label:String,connected:Boolean,onClick:()->Unit,modifier:Modifier=Modifier){
    val shape=RoundedCornerShape(11.dp)
    Row(modifier.heightIn(min=44.dp).clip(shape).background(Color.White.copy(alpha=.025f)).border(1.dp,Color.White.copy(alpha=.3f),shape).clickable(onClick=onClick).semantics{contentDescription="Choose library";stateDescription=if(connected)"Connected"else "Not connected";role=Role.Button}.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Box(Modifier.size(5.dp).background(if(connected)Color(0xff10b981)else StakTextMuted,RoundedCornerShape(5.dp)))
        Text(label,Modifier.weight(1f),fontFamily=HankenFont,fontSize=12.sp,color=StakText,maxLines=1,overflow=TextOverflow.Ellipsis)
        Icon(Lucide.ChevronDown,null,tint=StakTextMuted,modifier=Modifier.size(15.dp))
    }
}

/** Existing loaders remain authoritative; dragging never requests a server scan. */
@Composable internal fun LibraryRefresh(refreshing:Boolean,onRefresh:()->Unit,enabled:Boolean=true,content:@Composable BoxScope.()->Unit){
    val state=rememberPullToRefreshState()
    PullToRefreshBox(isRefreshing=refreshing,onRefresh={if(enabled)onRefresh()},state=state,modifier=Modifier.fillMaxSize(),indicator={
        if(enabled&&(refreshing||state.distanceFraction>0f))Column(Modifier.align(Alignment.TopCenter).padding(top=8.dp).width(110.dp).clip(RoundedCornerShape(10.dp)).background(StakSurface).border(1.dp,Color.White.copy(alpha=.18f),RoundedCornerShape(10.dp)).padding(10.dp).testTag("library-refresh-indicator"),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text(if(refreshing)"REFRESHING"else "PULL TO REFRESH",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakText)
            if(refreshing)StakLoadingProgress()else StakProgress(state.distanceFraction.coerceIn(0f,1f),height=2)
        }
    },content=content)
}
