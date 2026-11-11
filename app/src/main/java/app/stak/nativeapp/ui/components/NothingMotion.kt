package app.stak.nativeapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.animation.*

/** One entrance per saved screen/item identity, never a repeating scroll effect. */
@Composable fun rememberNothingReveal(identity:String,order:Int=0,duration:Int=StakMotion.Standard):State<Float> {
    val reduced=LocalReducedMotion.current
    var seen by rememberSaveable(identity){mutableStateOf(false)}
    val progress=remember(identity){Animatable(if(seen||reduced)1f else 0f)}
    LaunchedEffect(identity,reduced){
        if(seen||reduced)progress.snapTo(1f)
        else {seen=true;progress.animateTo(1f,tween(duration,order.coerceIn(0,4)*32,StakMotion.Ease))}
    }
    return progress.asState()
}

@Composable fun Modifier.nothingReveal(identity:String,order:Int=0):Modifier {
    val amount=rememberNothingReveal(identity,order)
    return graphicsLayer{alpha=amount.value;translationY=(1f-amount.value)*10f*density}
}

/** Feedback starts only on a value change, never on loading/restoring an existing state. */
@Composable fun Modifier.nothingSignal(value:Any):Modifier {
    val reduced=LocalReducedMotion.current
    val pulse=remember{Animatable(0f)}
    var previous by remember{mutableStateOf(value)}
    LaunchedEffect(value,reduced){
        val changed=previous!=value;previous=value
        if(reduced)pulse.snapTo(0f)
        else if(changed){pulse.snapTo(1f);pulse.animateTo(0f,tween(300,easing=StakMotion.Ease))}
    }
    return graphicsLayer{scaleX=1f+.035f*pulse.value;scaleY=scaleX}
}

/** Expose the real value to accessibility while only the visible glyphs count up. */
@Composable fun NothingNumber(value:String,modifier:Modifier=Modifier,style:TextStyle=TextStyle.Default) {
    val number=remember(value){Regex("^(\\d+)(\\.\\d+)?(.*)$").matchEntire(value)}
    val reveal=rememberNothingReveal("number:$value",duration=420)
    val text=if(number==null||reveal.value>=1f)value else {
        val target=(number.groupValues[1]+number.groupValues[2]).toDoubleOrNull()?:0.0
        val digits=number.groupValues[2].drop(1).length
        (if(digits==0)(target*reveal.value).roundToInt().toString() else String.format(Locale.ENGLISH,"%.$digits"+"f",target*reveal.value))+number.groupValues[3]
    }
    Text(text,modifier.clearAndSetSemantics{contentDescription=value},style=style,maxLines=1)
}

/** Coil skips the crossfade for memory-cache hits; no per-poster animation coroutine. */
@Composable fun StakArtwork(model:Any?,contentDescription:String?,modifier:Modifier=Modifier,contentScale:ContentScale=ContentScale.Crop,onSuccess:()->Unit={},alignment:androidx.compose.ui.Alignment=androidx.compose.ui.Alignment.Center,fallbackModel:Any?=null,onLoadFailure:()->Unit={}) {
    val context=LocalContext.current;val reduced=LocalReducedMotion.current
    var useFallback by remember(model,fallbackModel){mutableStateOf(false)}
    val active=if(useFallback)fallbackModel else model
    val request=remember(active,reduced,context){ImageRequest.Builder(context).data(active).crossfade(if(reduced)0 else StakMotion.Quick).build()}
    var failed by remember(model,fallbackModel){mutableStateOf(false)}
    Box(modifier){
        if(model==null||failed)Box(Modifier.fillMaxSize().background(app.stak.nativeapp.ui.theme.StakSurface),contentAlignment=androidx.compose.ui.Alignment.Center){
            androidx.compose.material3.Icon(app.stak.nativeapp.ui.theme.Lucide.Film,null,tint=app.stak.nativeapp.ui.theme.StakTextFaint,modifier=Modifier.size(22.dp))
        }
        // Dispose the failed painter before mounting its replacement: late events from
        // the outgoing request must not mark the fallback request as failed.
        key(active){
            AsyncImage(request,contentDescription,modifier=Modifier.fillMaxSize(),contentScale=contentScale,alignment=alignment,onError={
                onLoadFailure()
                if(active==model&&fallbackModel!=null&&fallbackModel!=model)useFallback=true else failed=true
            },onSuccess={failed=false;onSuccess()})
        }
    }
}

@Composable fun nothingLoopEnabled():Boolean {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return !LocalReducedMotion.current&&lifecycle.isAtLeast(Lifecycle.State.RESUMED)&&LocalCinematicMotion.current?.busy!=true
}

@Composable fun NothingExpand(visible:Boolean,content:@Composable ()->Unit){
    val reduced=LocalReducedMotion.current
    AnimatedVisibility(visible,
        enter=fadeIn(tween(120))+expandVertically(tween(if(reduced)0 else StakMotion.Standard,easing=StakMotion.Ease)),
        exit=fadeOut(tween(100))+shrinkVertically(tween(if(reduced)0 else StakMotion.Quick,easing=StakMotion.Ease))){content()}
}
