package app.stak.nativeapp.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** Shared cinematic light; blur utility retained for existing regression coverage. */
@Composable
fun AmbientBackground() {
    val palette=LocalCinematicPalette.current
    val tint=rememberArtworkTint(palette?.value)
    CinematicBackground(tint,intensity=if(LocalCinematicHome.current)1f else .48f)
}

/**
 * Downsamples, blurs and keeps the small bitmap: it is drawn stretched to the
 * full size, so the upscale finishes the smoothing for free.
 */
private fun softenAmbient(source: Bitmap, radiusPx: Float): Bitmap {
    val factor = 3
    val w = (source.width / factor).coerceAtLeast(1)
    val h = (source.height / factor).coerceAtLeast(1)
    val small = if (w == source.width && h == source.height) source
    else Bitmap.createScaledBitmap(source, w, h, true)
    val pixels = IntArray(w * h)
    small.getPixels(pixels, 0, w, 0, 0, w, h)
    boxBlurArgb(pixels, w, h, (radiusPx / factor).toInt().coerceAtLeast(1))
    small.setPixels(pixels, 0, w, 0, 0, w, h)
    if (small !== source) source.recycle()
    return small
}

/** Separable box blur, three passes, straight ARGB channels. */
internal fun boxBlurArgb(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 3) {
    if (radius <= 0 || width <= 0 || height <= 0 || pixels.size < width * height) return
    val scratch = IntArray(width * height)
    repeat(passes) {
        blurLines(pixels, scratch, width, height, radius, horizontal = true)
        blurLines(scratch, pixels, width, height, radius, horizontal = false)
    }
}

private fun blurLines(
    src: IntArray,
    dst: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean
) {
    val div = 2 * radius + 1
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    for (line in 0 until lines) {
        var sa = 0
        var sr = 0
        var sg = 0
        var sb = 0
        fun index(position: Int) =
            if (horizontal) line * width + position.coerceIn(0, length - 1)
            else position.coerceIn(0, length - 1) * width + line
        for (offset in -radius..radius) {
            val color = src[index(offset)]
            sa += (color ushr 24) and 255
            sr += (color shr 16) and 255
            sg += (color shr 8) and 255
            sb += color and 255
        }
        for (position in 0 until length) {
            dst[index(position)] =
                (((sa / div) and 255) shl 24) or
                    (((sr / div) and 255) shl 16) or
                    (((sg / div) and 255) shl 8) or
                    ((sb / div) and 255)
            val leaving = src[index(position - radius)]
            val entering = src[index(position + radius + 1)]
            sa += ((entering ushr 24) and 255) - ((leaving ushr 24) and 255)
            sr += ((entering shr 16) and 255) - ((leaving shr 16) and 255)
            sg += ((entering shr 8) and 255) - ((leaving shr 8) and 255)
            sb += (entering and 255) - (leaving and 255)
        }
    }
}


