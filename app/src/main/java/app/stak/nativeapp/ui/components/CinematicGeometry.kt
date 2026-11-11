package app.stak.nativeapp.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Preserve shelf gutters while a single cinematic item reaches the viewport edges. */
internal fun Modifier.edgeToEdge(inset: Dp = 16.dp): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val child = measurable.measure(constraints)
        layout(child.width, child.height) { child.placeRelative(0, 0) }
    } else {
        val extra = inset.roundToPx()
        val child = measurable.measure(constraints.copy(
            minWidth = constraints.maxWidth + extra * 2, maxWidth = constraints.maxWidth + extra * 2))
        layout(constraints.maxWidth, child.height) { child.placeRelative(-extra, 0) }
    }
}

/** Bounded phone/tablet geometry; keep controls usable in landscape and large text. */
internal fun homeHeroHeight(screenHeight: Int, fontScale: Float = 1f): Float =
    (screenHeight * .5f + (fontScale - 1f).coerceIn(0f, 1f) * 32f)
        .coerceIn(minOf(220f, screenHeight * .7f), screenHeight * .7f)

internal fun detailArtworkHeight(screenHeight: Int): Float =
    (screenHeight * .576f).coerceIn(336f, 576f)

/** One full card and half of the next, including the rail's gap. */
internal fun continueCardWidth(availableWidth: Float, gap: Float = 12f): Float =
    ((availableWidth-gap).coerceAtLeast(1f)/1.5f)
