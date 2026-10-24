package app.stak.nativeapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
/** Cached glass paint, without a render target or blur pass for each control. */
@Composable
fun Modifier.liquidGlass(radius: Dp = 16.dp, highlighted: Boolean = false,
    strength: Float = .72f): Modifier = liquidGlassShape(RoundedCornerShape(radius), highlighted, strength)

@Composable
private fun Modifier.liquidGlassShape(shape: Shape, highlighted: Boolean = false,
    strength: Float = .72f): Modifier {
    val dark = MaterialTheme.colorScheme.background.red < .5f
    val tint = if (highlighted) Accent else if (dark) Color(0xff18212b) else Color(0xfff6f9ff)
    val edge = if (highlighted) AccentInk else if (dark) Color.White else Color(0xff64748b)
    return clip(shape).drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val fill = Brush.linearGradient(listOf(
            tint.copy(alpha = strength * if (highlighted) .42f else .9f),
            tint.copy(alpha = strength * .5f)))
        val shine = Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) .13f else .7f),
            Color.White.copy(alpha = .015f), Color.Transparent), end = Offset(size.width * .65f, size.height))
        val rim = Brush.linearGradient(listOf(edge.copy(alpha = if (highlighted) .65f else .32f),
            edge.copy(alpha = .06f), edge.copy(alpha = .2f)))
        val stroke = 1.dp.toPx()
        onDrawWithContent {
            drawOutline(outline, fill)
            drawOutline(outline, shine)
            drawContent()
            drawOutline(outline, rim, style = Stroke(stroke * 2))
        }
    }
}

/** Shared treatment for cards, dialogs and secondary controls across every screen. */
@Composable
fun LiquidSurface(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(16.dp),
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = androidx.compose.material3.contentColorFor(color),
    border: BorderStroke? = null, onClick: (() -> Unit)? = null, enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null, tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp, highlighted: Boolean = false, content: @Composable () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    val neutral = color == surface || color == surface.copy(alpha = .74f) || color == surface.copy(alpha = .94f)
    val translucent = neutral || color == Color.Transparent
    val base = if (translucent) modifier else modifier.background(color, shape)
    val paint = base.liquidGlassShape(shape, highlighted = highlighted || color == Accent,
        strength = if (translucent) .72f else 0f)
    if (onClick == null) Surface(paint, shape, Color.Transparent, contentColor,
        tonalElevation, shadowElevation, border, content = content)
    else Surface(onClick, paint, enabled, shape, Color.Transparent, contentColor,
        tonalElevation, shadowElevation, border, interactionSource, content)
}

@Composable
fun GlassIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.material3.IconButton(onClick, modifier.stakPressMotion(interaction).liquidGlass(100.dp),
        enabled = enabled, interactionSource = interaction, content = content)
}

@Composable
fun GlassTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.TextButton(onClick, modifier.liquidGlass(14.dp, highlighted = enabled), enabled = enabled,
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = AccentInk), content = content)
}
@Composable
fun GlassAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null) {
    androidx.compose.material3.AlertDialog(onDismissRequest, confirmButton,
        modifier = Modifier.liquidGlass(28.dp, strength = .95f),
        dismissButton = dismissButton, title = title, text = text, containerColor = Color.Transparent)
}

/** Cached frosted chrome; no live capture/render effect beneath scrolling content. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, radius: Dp = 20.dp, backdrop: Boolean = false,
    emphasized: Boolean = false, content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.red < .5f
    val base = if (dark) Color(0xff101318) else Color(0xfff7f8fa)
    val shape = RoundedCornerShape(radius)
    val chrome = if (backdrop) Modifier.background(base.copy(alpha = .92f), shape) else Modifier
    Surface(modifier.clip(shape).then(chrome).liquidGlass(radius, emphasized, if (backdrop) .35f else .72f),
        shape = shape, color = Color.Transparent) {
        content()
    }
}
