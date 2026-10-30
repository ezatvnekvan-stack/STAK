package app.stak.nativeapp.ui.components

/** Opacity of the faintest and strongest dot in the matrix wave. */
const val DOT_MIN_ALPHA = .065f
const val DOT_MAX_ALPHA = .28f

fun advanceDotPhase(phase: Float, seconds: Float, enabled: Boolean = true): Float {
    val current = if (phase.isFinite()) ((phase % 1f) + 1f) % 1f else .35f
    if (!enabled) return current
    val step = if (seconds.isFinite()) seconds.coerceIn(0f, .05f) else 0f
    return (current + step / 14f) % 1f
}

/** Vertical envelope: the matrix fades out above and below the artwork. */
fun dotFade(y: Float): Float =
    ((y - .16f) / .22f).coerceIn(0f, 1f) * ((1f - y) / .18f).coerceIn(0f, 1f)

/**
 * Opacity of one dot for a given diagonal position [bias] (`x*.28 + y*.72`).
 * Precomputing [bias] and [fade] once per size keeps the per-frame loop to a
 * single modulo, a multiply and an exp.
 */
fun dotWaveAlpha(bias: Float, fade: Float, phase: Float): Float {
    val travel = ((bias - phase) % 1f + 1f) % 1f
    val distance = minOf(travel, 1f - travel)
    return fade * (DOT_MIN_ALPHA + (DOT_MAX_ALPHA - DOT_MIN_ALPHA) * kotlin.math.exp(-distance * distance / .006f))
}

fun dotMatrixOpacity(x: Float, y: Float, phase: Float): Float =
    dotWaveAlpha(x * .28f + y * .72f, dotFade(y), phase)
