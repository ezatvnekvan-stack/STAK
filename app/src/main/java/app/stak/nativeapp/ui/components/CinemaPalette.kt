package app.stak.nativeapp.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.dynamicColorScheme
import com.materialkolor.PaletteStyle

data class CinemaPalette(val scheme:ColorScheme,val background:Color,val surface:Color,val glow:Color)
private val cinemaPalettes=object:LinkedHashMap<Int,CinemaPalette>(48,.75f,true){override fun removeEldestEntry(eldest:MutableMap.MutableEntry<Int,CinemaPalette>?)=size>48}
/** Quantized seeds prevent palette generation on every animation frame. No artwork pixels or network calls here. */
fun cinemaPalette(seed:Color):CinemaPalette {
    val key=seed.toArgb() or 0xFF000000.toInt()
    val quantized=key and 0xFFF0F0F0.toInt()
    return synchronized(cinemaPalettes){cinemaPalettes.getOrPut(quantized){
        val source=Color(quantized)
        val scheme=dynamicColorScheme(seedColor=source,isDark=true,isAmoled=false,style=PaletteStyle.TonalSpot,contrastLevel=.25)
        CinemaPalette(scheme,lerp(Color(0xFF060709),scheme.surface,.42f),scheme.surfaceContainerLow,lerp(source,scheme.primary,.22f))
    }}
}
@Composable fun rememberCinemaPalette(seed:Color):CinemaPalette=remember(seed.toArgb() and 0xFFF0F0F0.toInt()){cinemaPalette(seed)}
