package app.stak.nativeapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import coil.request.SuccessResult
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloat
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

/** Small quantized histogram; never scans full artwork or runs in a draw callback. */
fun artworkTint(pixels:IntArray):Int {
    val bins=mutableMapOf<Int,Double>()
    for(pixel in pixels){
        if((pixel ushr 24)<160)continue
        val r=(pixel shr 16)and 255;val g=(pixel shr 8)and 255;val b=pixel and 255
        val max=maxOf(r,g,b);val min=minOf(r,g,b);val saturation=if(max==0)0.0 else (max-min).toDouble()/max
        val luminance=(r*.2126+g*.7152+b*.0722)/255
        if(luminance<.09||luminance>.88||saturation<.16)continue
        val key=((r/32)shl 6)or((g/32)shl 3)or(b/32)
        bins[key]=(bins[key] ?: 0.0)+.3+saturation
    }
    val key=bins.maxByOrNull{it.value}?.key ?: return 0xFF566273.toInt()
    return (0xFF000000.toInt() or ((((key shr 6)and 7)*32+16)shl 16) or ((((key shr 3)and 7)*32+16)shl 8) or ((key and 7)*32+16))
}
private val paletteCache=object:LinkedHashMap<String,Int>(48,.75f,true){override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,Int>?)=size>48}
@Composable fun rememberArtworkTint(artwork:String?):State<Color> {
    val context=LocalContext.current
    val target by produceState(Color(0xFF566273),artwork){
        value=Color(0xFF566273)
        if(artwork.isNullOrBlank())return@produceState
        synchronized(paletteCache){paletteCache[artwork]}?.let{value=Color(it);return@produceState}
        try{
            val result=coil.Coil.imageLoader(context).execute(ImageRequest.Builder(context).data(artwork).size(64,64).allowHardware(false).build())
            val bitmap=((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return@produceState
            val tint=withContext(Dispatchers.Default){
                val small=android.graphics.Bitmap.createScaledBitmap(bitmap,64,64,true)
                val pixels=IntArray(4096);small.getPixels(pixels,0,64,0,0,64,64)
                val chosen=artworkTint(pixels)
                if(small!==bitmap)small.recycle()
                chosen
            }
            synchronized(paletteCache){paletteCache[artwork]=tint};value=Color(tint)
        }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}catch(_:Exception){}
    }
    return animateColorAsState(target,if(LocalReducedMotion.current)snap() else tween(StakMotion.Atmosphere,easing=StakMotion.Ease),label="artwork-atmosphere")
}
fun atmosphereBase(tint:Color)=cinemaPalette(tint).background
val LocalTitleSurface=staticCompositionLocalOf{Color(0xFF101010)}
@Composable fun ArtworkAtmosphere(tint:State<Color>,modifier:Modifier=Modifier,paused:()->Boolean={false}){
    CinematicBackground(tint,modifier,paused,intensity=1f)
}
