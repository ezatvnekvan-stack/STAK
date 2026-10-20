package app.stak.nativeapp

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import org.json.JSONObject

const val GRAPHITE_TONE: Int = -9670536 // #6c7078, original artworkPalette.js fallback.

/** Direct translation of frontend/src/lib/artworkPalette.js:paletteFromPixels. */
fun paletteFromPixels(pixels: IntArray): Int {
    data class Bucket(var weight: Double = 0.0, val rgb: DoubleArray = DoubleArray(3))
    val buckets = linkedMapOf<Int, Bucket>()
    for (pixel in pixels) {
        val rgb = intArrayOf((pixel ushr 16) and 255, (pixel ushr 8) and 255, pixel and 255)
        val hi = rgb.max()
        val lo = rgb.min()
        if ((pixel ushr 24) < 200 || hi < 24 || lo > 235) continue
        val key = (rgb[0] / 32 shl 6) or (rgb[1] / 32 shl 3) or (rgb[2] / 32)
        val bucket = buckets.getOrPut(key) { Bucket() }
        val weight = 1.0 + (hi - lo) / 255.0
        bucket.weight += weight
        rgb.forEachIndexed { index, channel -> bucket.rgb[index] += channel * weight }
    }
    val winner = buckets.values.maxByOrNull { it.weight } ?: return GRAPHITE_TONE
    val rgb = winner.rgb.map { it / winner.weight }
    val mean = rgb.average()
    val muted = rgb.map { mean + (it - mean) * .65 }
    val scale = minOf(1.0, 145.0 / muted.max())
    val channels = muted.map { (it * scale).roundToInt().coerceIn(0, 255) }
    return (0xff shl 24) or (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
}

suspend fun Repository.artworkTone(context: Context, media: Media): Int {
    val source = media.background.ifBlank { media.poster }
    if (source.isBlank()) return GRAPHITE_TONE
    val uri = android.net.Uri.parse(source)
    if (uri.scheme !in listOf("https", "http") || uri.userInfo != null) return GRAPHITE_TONE
    return try {
        val result =
            publicMetadata(
                "artwork-palette:v2:${uri.scheme}://${uri.authority}${uri.path}",
                7 * 86_400_000L,
            ) {
                withTimeout(4000) {
                    val request =
                        ImageRequest.Builder(context).data(source).allowHardware(false).size(32, 24)
                    if (
                        media.jellyfinId.isNotBlank() &&
                            credentials.get("jellyfin_url").isNotBlank() &&
                            source.startsWith(credentials.get("jellyfin_url").trimEnd('/') + "/")
                    )
                        request.addHeader("X-Emby-Token", credentials.get("jellyfin_token"))
                    val image =
                        context.imageLoader.execute(request.build()) as? SuccessResult
                            ?: error("Artwork could not decode.")
                    val bitmap = image.drawable.toBitmap(32, 24, Bitmap.Config.ARGB_8888)
                    val pixels = IntArray(32 * 24)
                    bitmap.getPixels(pixels, 0, 32, 0, 0, 32, 24)
                    JSONObject().put("tone", paletteFromPixels(pixels))
                }
            }
        result.optInt("tone", GRAPHITE_TONE)
    } catch (e: CancellationException) {
        if (e is TimeoutCancellationException) GRAPHITE_TONE else throw e
    } catch (_: Exception) {
        GRAPHITE_TONE
    }
}
