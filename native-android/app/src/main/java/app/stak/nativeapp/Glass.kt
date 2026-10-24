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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
/** Quiet translucent controls; reserve a reflection for the floating dock. */
@Composable
fun Modifier.liquidGlass(radius: Dp = 16.dp, highlighted: Boolean = false,
    strength: Float = .72f): Modifier {
    val dark = MaterialTheme.colorScheme.background.red < .5f
    val fill = if (highlighted) Accent.copy(alpha = .09f)
        else MaterialTheme.colorScheme.surface.copy(alpha = strength * .7f)
    val edge = if (highlighted) Accent.copy(alpha = .35f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) .12f else .14f)
    return clip(RoundedCornerShape(radius)).drawWithCache {
        val outline = RoundedCornerShape(radius).createOutline(size, layoutDirection, this)
        onDrawWithContent {
            drawOutline(outline, fill)
            drawContent()
            drawOutline(outline, edge, style = Stroke(1.dp.toPx()))
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
    val fill = if (highlighted && color == Color.Transparent) Accent.copy(alpha = .08f)
        else if (neutral) surface.copy(alpha = .88f) else color
    val rim = border ?: if (highlighted) BorderStroke(1.dp, Accent.copy(alpha = .35f))
        else if (neutral) BorderStroke(1.dp, Rule) else null
    if (onClick == null) Surface(modifier, shape, fill, contentColor,
        tonalElevation, shadowElevation, rim, content = content)
    else Surface(onClick, modifier, enabled, shape, fill, contentColor,
        tonalElevation, shadowElevation, rim, interactionSource, content)
}

@Composable
fun GlassIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.material3.IconButton(onClick, modifier.stakPressMotion(interaction),
        enabled = enabled, interactionSource = interaction, content = content)
}

@Composable
fun GlassTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.TextButton(onClick, modifier, enabled = enabled,
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = AccentInk), content = content)
}
@Composable
fun GlassAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null) {
    androidx.compose.material3.AlertDialog(onDismissRequest, confirmButton,

        dismissButton = dismissButton, title = title, text = text, containerColor = MaterialTheme.colorScheme.surface)
}

/** Low-contrast dock reflection; artwork cards keep the reference's quiet surfaces. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, radius: Dp = 20.dp, backdrop: Boolean = false,
    emphasized: Boolean = false, content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.red < .5f
    val base = if (dark) Color(0xff101010) else Color(0xfffafafa)
    val shape = RoundedCornerShape(radius)
    val ink = MaterialTheme.colorScheme.onSurface
    val material = if (backdrop) modifier.background(Brush.verticalGradient(listOf(
        base.copy(alpha = .96f), base.copy(alpha = .91f))), shape) else modifier
    Surface(material, shape = shape,
        color = if (backdrop) Color.Transparent else base.copy(alpha = .9f),
        border = BorderStroke(1.dp, ink.copy(alpha = if (emphasized) .32f else .12f)),
        content = content)
}
