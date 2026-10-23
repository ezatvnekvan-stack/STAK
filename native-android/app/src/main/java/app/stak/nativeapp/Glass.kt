@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)
package app.stak.nativeapp

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import dev.chrisbanes.haze.*

val LocalGlassBackdrop = staticCompositionLocalOf<HazeState?> { null }

/** One downsampled backdrop for floating chrome; scrolling cards use a cheap glass tint. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, radius: Dp = 20.dp, backdrop: Boolean = false,
    emphasized: Boolean = false, content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.red < .5f
    val base = if (dark) Color(0xff101318) else Color(0xfff7f8fa)
    val haze = LocalGlassBackdrop.current
    val style = remember(dark) { HazeStyle(backgroundColor = if (dark) Color.Black else Color.White,
        tints = listOf(HazeTint(base.copy(alpha = if (dark) .55f else .65f))), blurRadius = 22.dp,
        noiseFactor = .015f, fallbackTint = HazeTint(base.copy(alpha = .94f))) }
    val shape = RoundedCornerShape(radius)
    val glass = if (backdrop && haze != null) Modifier.hazeEffect(haze, style) { inputScale = HazeInputScale.Fixed(.5f) } else Modifier
    Surface(modifier.clip(shape).then(glass).background(Brush.linearGradient(listOf(
        Color.White.copy(alpha = if (dark) .075f else .48f), Color.Transparent, Color(0xffa9c8e8).copy(alpha = if (dark) .05f else .12f)))),
        shape = shape, color = if (backdrop) Color.Transparent else base.copy(alpha = if (dark) .66f else .7f),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(
            Color.White.copy(alpha = if (emphasized) .55f else if (dark) .25f else .9f),
            MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), Color.White.copy(alpha = if (dark) .12f else .6f))))) {
        content()
    }
}

/** Window compositor blur is shared by sheets instead of adding blur passes to every card. */
@Composable
fun GlassSheetBlur() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (Build.VERSION.SDK_INT >= 31 && window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = 20 }
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= 31 && window != null) window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
    }
}
