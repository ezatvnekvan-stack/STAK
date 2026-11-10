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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

/** Primary action lives with the title, never in a fixed bottom strip. */
@Composable internal fun DetailSourcesAction(identity:String,onSources:()->Unit,modifier:Modifier=Modifier){
    val reduced=LocalReducedMotion.current
    val sweep=androidx.compose.runtime.remember(identity){androidx.compose.animation.core.Animatable(1f)}
    var seen by androidx.compose.runtime.saveable.rememberSaveable(identity){androidx.compose.runtime.mutableStateOf(false)}
    androidx.compose.runtime.LaunchedEffect(identity,reduced){
        if(!seen&&!reduced){seen=true;sweep.snapTo(0f);sweep.animateTo(1f,androidx.compose.animation.core.tween(900,easing=StakMotion.Ease))}
        else {seen=true;sweep.snapTo(1f)}
    }
    val press=rememberPressMotion()
    val shape=RoundedCornerShape(14.dp)
    Box(modifier.widthIn(min=176.dp).heightIn(min=48.dp).pressMotion(press).clip(shape)
        .background(Color(0x99141618)).border(1.dp,Color.White.copy(alpha=.28f),shape)
        .clickable(interactionSource=press.source,indication=androidx.compose.foundation.LocalIndication.current,role=androidx.compose.ui.semantics.Role.Button,onClick=onSources)
        .testTag("detail-source-button"),contentAlignment=Alignment.CenterStart){
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()){
            val p=sweep.value
            if(p>0f&&p<1f){
                val x=(size.width*1.8f)*p-size.width*.4f
                drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent,Color.White.copy(alpha=.08f),Color.Transparent),startX=x-size.width*.22f,endX=x+size.width*.22f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp,Alignment.CenterHorizontally)){
            Icon(Lucide.Plus,null,tint=StakAccent.copy(alpha=.8f),modifier=Modifier.size(16.dp))
            Text("FIND SOURCES",fontFamily=SpaceMonoFont,fontWeight=FontWeight.Bold,fontSize=11.sp,color=StakText)
        }
    }
}
