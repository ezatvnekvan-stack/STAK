package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import dev.chrisbanes.haze.*

/** Chrome occupies layout space, so no content or season control can sit underneath. */
@Composable internal fun DetailHeader(title:String,haze:HazeState,onBack:()->Unit){
    Column(Modifier.fillMaxWidth().hazeChild(haze,stakHazeStyle()).background(Color.Black.copy(alpha=.22f)).testTag("detail-header")){
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
            StakIconButton(onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back to browsing",tint=StakText,modifier=Modifier.size(20.dp))}
            Text(title,Modifier.weight(1f).padding(start=4.dp,end=16.dp),fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=14.sp,color=StakText,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha=.07f)))
    }
}

@Composable internal fun DetailSourceDock(haze:HazeState,onSources:()->Unit){
    Column(Modifier.fillMaxWidth().hazeChild(haze,stakHazeStyle()).background(Color.Black.copy(alpha=.22f)).testTag("detail-source-dock")){
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha=.09f)))
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=16.dp,vertical=10.dp)){
            val press=rememberPressMotion()
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).pressMotion(press).clip(RoundedCornerShape(12.dp)).border(1.dp,Color.White.copy(alpha=.48f),RoundedCornerShape(12.dp)).clickable(interactionSource=press.source,indication=androidx.compose.foundation.LocalIndication.current,onClick=onSources).padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Icon(Lucide.Plus,null,tint=StakAccent,modifier=Modifier.size(18.dp))
                Text("FIND SOURCES",fontFamily=SpaceMonoFont,fontWeight=FontWeight.Bold,fontSize=11.sp,color=StakText,modifier=Modifier.weight(1f))
                Icon(Lucide.ChevronRight,null,tint=StakTextMuted,modifier=Modifier.size(15.dp))
            }
        }
    }
}
