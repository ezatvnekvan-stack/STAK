package app.stak.nativeapp.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

/** Only an acknowledged add morphs into a check; uncertain/error outcomes stay unconfirmed. */
@Composable internal fun SourceAcquireButton(added:Boolean,adding:Boolean,canAdd:Boolean,onAdd:()->Unit,onFiles:()->Unit){
    val enabled=added||(!adding&&canAdd)
    val reduced=LocalReducedMotion.current
    val progress=animateFloatAsState(if(added)1f else 0f,if(reduced)snap()else tween(380,easing=StakMotion.Ease),label="source-add-check")
    val press=rememberPressMotion(enabled)
    Box(Modifier.size(48.dp).pressMotion(press).alpha(if(enabled||adding)1f else .35f)
        .clip(RoundedCornerShape(11.dp)).border(1.dp,Color.White.copy(alpha=.4f),RoundedCornerShape(11.dp))
        .clickable(enabled=enabled,interactionSource=press.source,indication=LocalIndication.current,role=Role.Button,onClick=if(added)onFiles else onAdd)
        .semantics{
            contentDescription=if(added)"Open added release files"else if(adding)"Adding release"else "Add cached release"
            stateDescription=if(added)"Added"else if(adding)"Adding"else "Not added"
        },contentAlignment=Alignment.Center){
        if(adding&&!added)StakLottie(StakAnimation.Search,Modifier.size(22.dp),loop=true)
        else Canvas(Modifier.size(22.dp)){
            val p=progress.value
            fun morph(a:Offset,b:Offset)=a+(b-a)*p
            fun xy(x:Float,y:Float)=Offset(x*size.width,y*size.height)
            drawLine(StakText,morph(xy(.2f,.5f),xy(.18f,.53f)),morph(xy(.8f,.5f),xy(.43f,.76f)),strokeWidth=1.8.dp.toPx(),cap=StrokeCap.Round)
            drawLine(StakText,morph(xy(.5f,.2f),xy(.43f,.76f)),morph(xy(.5f,.8f),xy(.83f,.25f)),strokeWidth=1.8.dp.toPx(),cap=StrokeCap.Round)
        }
    }
}
