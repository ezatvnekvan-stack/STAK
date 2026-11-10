package app.stak.nativeapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.stak.nativeapp.data.BackgroundStyle
import app.stak.nativeapp.ui.theme.*

enum class GlyphLayout { Home, Detail, General }
class GlyphSignals {
    var success by mutableIntStateOf(0)
        private set
    fun added() { success++ }
}
val LocalBackgroundStyle = staticCompositionLocalOf { BackgroundStyle.Current }
val LocalGlyphLayout = staticCompositionLocalOf { GlyphLayout.General }
val LocalGlyphRoute = staticCompositionLocalOf<Any> { "initial" }
val LocalGlyphSignals = staticCompositionLocalOf<GlyphSignals?> { null }

/** No timer, bitmap, blur or hit target. Animation invalidates only this drawing. */
@Composable fun GlyphBackground(modifier: Modifier = Modifier, paused: () -> Boolean = { false }) {
    val layout = LocalGlyphLayout.current
    val route = LocalGlyphRoute.current
    val success = LocalGlyphSignals.current?.success ?: 0
    val reduced = LocalReducedMotion.current
    val enabled = LocalCinematicEnabled.current
    val motion = LocalCinematicMotion.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val allowed = enabled && !reduced && lifecycle.isAtLeast(Lifecycle.State.RESUMED) && motion?.busy != true && !paused()
    val glow = remember { Animatable(0f) }
    var handled by remember { mutableStateOf<Pair<Any, Int>?>(null) }
    // A blocked event waits for the modal/scroll to finish. Interruptions never replay it.
    LaunchedEffect(route, success, allowed, reduced, enabled) {
        val event = route to success
        if (reduced || !enabled) handled = event
        if (!allowed || handled == event) {
            glow.snapTo(0f)
            return@LaunchedEffect
        }
        handled = event
        glow.snapTo(0f)
        glow.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        glow.animateTo(0f, tween(660, easing = FastOutSlowInEasing))
    }
    Canvas(modifier.fillMaxSize().testTag("glyph-background")) {
        drawRect(Color(0xFF090A0B))
        if (enabled) drawGlyphGeometry(layout, glow.value)
    }
}

private fun DrawScope.drawGlyphGeometry(layout: GlyphLayout, pulse: Float = 0f) {
    val graphite = Color(0xFF87908F).copy(alpha = .095f)
    val light = Color(0xFFEDEFE9).copy(alpha = .15f * pulse)
    val line = 1.5.dp.toPx()
    val cap = StrokeCap.Round
    if (layout == GlyphLayout.Detail) {
        val x = size.width * .975f
        for (segment in 0..2) {
            val y = size.height * (.42f + segment * .155f)
            drawLine(graphite, Offset(x, y), Offset(x, y + size.height * .095f), line, cap)
        }
        drawLine(light, Offset(x, size.height * .575f), Offset(x, size.height * .67f), 3.dp.toPx(), cap)
    } else {
        val diameter = size.width * if (layout == GlyphLayout.Home) 1.72f else 1.48f
        val origin = Offset(size.width * .38f, size.height * .19f)
        listOf(105f to 68f, 181f to 43f, 233f to 48f).forEach { (start, sweep) ->
            drawArc(graphite, start, sweep, false, origin, Size(diameter, diameter), style = Stroke(line, cap = cap))
        }
        drawArc(light, 181f, 43f, false, origin, Size(diameter, diameter), style = Stroke(3.dp.toPx(), cap = cap))
        drawLine(graphite, Offset(-12.dp.toPx(), size.height * .80f), Offset(size.width * .16f, size.height * .80f), line, cap)
    }
    // One tiny signal point, outside the main content column.
    drawCircle(Color(0xFFD71921).copy(alpha = .35f), 1.4.dp.toPx(), Offset(size.width * .975f, size.height * .38f))
}

@Composable fun BackgroundStylePicker(selected: BackgroundStyle, onSelect: (BackgroundStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Background", fontFamily = SpaceMonoFont, fontSize = 12.sp, color = StakTextSoft)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackgroundStyle.entries.forEach { style ->
                val active = selected == style
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .border(1.dp, if (active) StakTextSoft else Color(0xFF3B3B3B), RoundedCornerShape(14.dp))
                    .selectable(active, role = Role.RadioButton, onClick = { onSelect(style) })
                    .testTag("background-${style.key}")) {
                    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
                        drawRect(Color(0xFF090A0B))
                        if (style == BackgroundStyle.Glyph) drawGlyphGeometry(GlyphLayout.Home)
                        else drawRect(androidx.compose.ui.graphics.Brush.radialGradient(
                            listOf(Color(0xFF35303D), Color.Transparent),
                            center = Offset(size.width * .1f, size.height * .1f), radius = size.width))
                    }
                    Row(Modifier.fillMaxWidth().background(Color(0xFF131414)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(style.label, fontFamily = HankenFont, fontSize = 14.sp, color = StakText)
                        if (active) Canvas(Modifier.size(5.dp)) { drawCircle(StakAccent) }
                    }
                }
            }
        }
        Text("Glyph adds quiet graphite lines and brief light signals. Atmosphere controls both backgrounds.",
            fontFamily = HankenFont, fontSize = 12.sp, lineHeight = 17.sp, color = StakTextMuted)
    }
}
