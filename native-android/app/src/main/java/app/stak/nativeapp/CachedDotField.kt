package app.stak.nativeapp

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Rasterize a repeated tile once, off the UI thread; scrolling draws one cached texture. */
@Composable
fun CachedDotField(color: Color, opacity: Float, modifier: Modifier = Modifier) {
    var dimensions by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    val image by produceState<ImageBitmap?>(null, dimensions, density, color, opacity) {
        if (dimensions.width <= 0 || dimensions.height <= 0 || opacity == 0f) { value = null; return@produceState }
        value = withContext(Dispatchers.Default) {
            val step = (20 * density).toInt().coerceAtLeast(1)
            val tile = Bitmap.createBitmap(step, step, Bitmap.Config.ARGB_8888)
            val ink = color.copy(alpha = opacity).toArgb()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(step / 2f, step / 2f, density, intArrayOf(ink, ink, android.graphics.Color.TRANSPARENT),
                    floatArrayOf(0f, .7f, 1f), Shader.TileMode.CLAMP)
            }
            android.graphics.Canvas(tile).drawRect(0f, 0f, step.toFloat(), step.toFloat(), paint)
            val result = Bitmap.createBitmap(dimensions.width, dimensions.height, Bitmap.Config.ARGB_8888)
            paint.shader = ComposeShader(BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT),
                LinearGradient(0f, 0f, 0f, dimensions.height.toFloat(),
                    intArrayOf(0x99ffffff.toInt(), android.graphics.Color.WHITE, 0xaaffffff.toInt()),
                    floatArrayOf(0f, .3f, 1f), Shader.TileMode.CLAMP), PorterDuff.Mode.DST_IN)
            android.graphics.Canvas(result).drawRect(0f, 0f, dimensions.width.toFloat(), dimensions.height.toFloat(), paint)
            result.asImageBitmap()
        }
    }
    Canvas(modifier.onSizeChanged { dimensions = it }) { image?.let { drawImage(it) } }
}
