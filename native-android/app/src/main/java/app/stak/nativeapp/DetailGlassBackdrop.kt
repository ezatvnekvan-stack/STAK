package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Sliding-window blur: linear in pixel count, with clamped edges and retained alpha. */
fun blurGlassPixels(input: IntArray, width: Int, height: Int, radius: Int): IntArray {
    require(width > 0 && height > 0 && input.size == width * height && radius >= 0)
    if (radius == 0) return input.copyOf()
    val divisor = radius * 2 + 1
    fun pass(source: IntArray, horizontal: Boolean): IntArray {
        val result = IntArray(source.size)
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        fun index(line: Int, position: Int) = if (horizontal) line * width + position else position * width + line
        repeat(lines) { line ->
            var a = 0; var r = 0; var g = 0; var b = 0
            fun add(pixel: Int, sign: Int) {
                a += (pixel ushr 24) * sign; r += (pixel ushr 16 and 255) * sign
                g += (pixel ushr 8 and 255) * sign; b += (pixel and 255) * sign
            }
            for (offset in -radius..radius) add(source[index(line, offset.coerceIn(0, length - 1))], 1)
            repeat(length) { position ->
                result[index(line, position)] = (a / divisor shl 24) or (r / divisor shl 16) or (g / divisor shl 8) or (b / divisor)
                add(source[index(line, (position - radius).coerceIn(0, length - 1))], -1)
                add(source[index(line, (position + radius + 1).coerceIn(0, length - 1))], 1)
            }
        }
        return result
    }
    var pixels = input
    repeat(3) { pixels = pass(pass(pixels, true), false) }
    return pixels
}

private object DetailBlur : Transformation {
    override val cacheKey = "stak-detail-glass-blur-v1"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.Default) {
        val pixels = IntArray(input.width * input.height)
        input.getPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
        Bitmap.createBitmap(blurGlassPixels(pixels, input.width, input.height, 8), input.width, input.height, Bitmap.Config.ARGB_8888)
    }
}

/** Decode and blur a small image once in Coil's cache; scrolling samples a fixed texture. */
@Composable
fun DetailGlassBackdrop(media: Media) {
    val context = LocalContext.current
    val repository = LocalStakViewModel.current?.repository
    val url = media.background.ifBlank { media.poster }
    val request = remember(context, url, repository) {
        ImageRequest.Builder(context).data(url).size(192, 256).allowHardware(false)
            .transformations(DetailBlur).crossfade(false).apply {
                val address = repository?.credentials?.get("jellyfin_url").orEmpty()
                if (media.jellyfinId.isNotBlank() && url.toHttpUrlOrNull()?.let { allowsPrivateArtworkHeader(it, address) } == true)
                    addHeader("X-Emby-Token", repository?.credentials?.get("jellyfin_token").orEmpty())
            }.build()
    }
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize().testTag("detail-glass-backdrop")) {
        AsyncImage(request, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .55f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            background.copy(alpha = .12f), background.copy(alpha = .5f), background.copy(alpha = .9f)))))
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Accent.copy(alpha = .08f), Color.Transparent))))
    }
}
