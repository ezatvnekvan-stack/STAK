package app.stak.nativeapp.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collect
import kotlin.math.*

class CinematicMotion {
    var scrolling by mutableStateOf(false);private set
    var dialogs by mutableIntStateOf(0)
    private var lastScroll=0L
    val busy:Boolean get()=scrolling||dialogs>0
    fun scrolled(){lastScroll=SystemClock.uptimeMillis();if(!scrolling)scrolling=true}
    fun settle(){if(SystemClock.uptimeMillis()-lastScroll>140)scrolling=false}
    val connection=object:NestedScrollConnection{
        override fun onPostScroll(consumed:Offset,available:Offset,source:NestedScrollSource):Offset{if(consumed!=Offset.Zero)scrolled();return Offset.Zero}
    }
}
val LocalCinematicMotion=staticCompositionLocalOf<CinematicMotion?>{null}
val LocalCinematicPalette=staticCompositionLocalOf<MutableState<String?>?>{null}
val LocalCinematicHome=staticCompositionLocalOf{false}
val LocalCinematicEnabled=staticCompositionLocalOf{true}
@Composable fun rememberCinematicMotion():CinematicMotion {
    val state=remember{CinematicMotion()}
    LaunchedEffect(state){snapshotFlow{state.scrolling}.filter{it}.collect{while(state.scrolling){delay(160);state.settle()}}}
    return state
}
@Composable fun PauseCinematicMotion(){
    val state=LocalCinematicMotion.current
    DisposableEffect(state){state?.let{it.dialogs++};onDispose{state?.let{it.dialogs--}}}
}
/** One reusable soft texture, tinted and translated on the GPU; no per-frame blur. */
private val softLight:ImageBitmap by lazy {
    val bitmap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888)
    val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    val stops=FloatArray(24){it/23f}
    val colors=IntArray(24){i->android.graphics.Color.argb((255*(exp(-5f*stops[i]*stops[i])-exp(-5f))/(1-exp(-5f))).toInt().coerceIn(0,255),255,255,255)}
    paint.shader=RadialGradient(128f,128f,128f,colors,stops,Shader.TileMode.CLAMP)
    Canvas(bitmap).drawRect(0f,0f,256f,256f,paint)
    bitmap.asImageBitmap()
}
private class LightPhase(var value:Float=.22f)
@Composable fun CinematicBackground(tint:State<Color>,modifier:Modifier=Modifier,paused:()->Boolean={false},intensity:Float=1f){
    val memory=remember{LightPhase()}
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val global=LocalCinematicMotion.current
    val enabled=LocalCinematicEnabled.current
    val moving=enabled&&!LocalReducedMotion.current&&lifecycle.isAtLeast(Lifecycle.State.RESUMED)&&!paused()&&global?.busy!=true
    val phase=if(moving){val offset=remember{StartOffset((memory.value*32000).toInt(),StartOffsetType.FastForward)};rememberInfiniteTransition(label="cinematic-light").animateFloat(0f,1f,infiniteRepeatable(tween(32000,easing=LinearEasing),RepeatMode.Restart,offset),label="light-drift")}else rememberUpdatedState(memory.value)
    Box(modifier.fillMaxSize().graphicsLayer().drawWithCache{
        val base=cinemaPalette(tint.value).background
        val image=softLight
        val color=lerp(tint.value,Color.White,.25f)
        val first=ColorFilter.tint(color,BlendMode.Modulate)
        val second=ColorFilter.tint(lerp(color,Color(0xFF766BBA),.48f),BlendMode.Modulate)
        val third=ColorFilter.tint(lerp(color,Color(0xFFB28E79),.30f),BlendMode.Modulate)
        val radius=maxOf(size.width*1.35f,size.height*.82f).toInt().coerceAtLeast(1)
        val mask=Brush.radialGradient(listOf(Color.Black.copy(alpha=.50f),Color.Transparent),center=Offset(size.width*.5f,size.height*.48f),radius=size.maxDimension*.56f)
        onDrawBehind{
            val p=phase.value;memory.value=p
            val angle=p*(2f*PI.toFloat());val drift=sin(angle);val rise=cos(angle)
            drawRect(base)
            fun light(x:Float,y:Float,alpha:Float,filter:ColorFilter){drawImage(image,dstOffset=IntOffset((x-radius/2).roundToInt(),(y-radius/2).roundToInt()),dstSize=IntSize(radius,radius),alpha=alpha*intensity*(if(enabled)1f else 0f),colorFilter=filter)}
            light(size.width*(-.10f+.14f*drift),size.height*(.22f+.12f*rise),.40f,first)
            light(size.width*(1.08f-.12f*rise),size.height*(.45f+.13f*drift),.32f,second)
            light(size.width*(.65f+.10f*rise),size.height*(1.03f-.10f*drift),.24f,third)
            drawRect(mask)
        }
    })
}
