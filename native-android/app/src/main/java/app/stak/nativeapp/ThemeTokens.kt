package app.stak.nativeapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.roundToInt

data class StakAccent(val id: String, val label: String, val hex: String)

val OriginalAccents =
    listOf(
        StakAccent("red", "Nothing Red", "#D71921"),
        StakAccent("green", "Green", "#10B981"),
        StakAccent("blue", "Blue", "#3B82F6"),
        StakAccent("white", "Pure White", "#FFFFFF"),
        StakAccent("graphite", "Graphite", "#707070"),
        StakAccent("orange", "Orange", "#F97316"),
        StakAccent("purple", "Purple", "#A78BFA"),
    )

fun resolvedAccentHex(value: String, light: Boolean = false): String {
    if (value == "white" && light) return "#111111"
    return OriginalAccents.firstOrNull { it.id == value }?.hex
        ?: value.takeIf { Regex("#[0-9a-fA-F]{6}").matches(it) }
        ?: "#D71921"
}

fun colorFromHex(hex: String) = Color(0xff000000L or hex.removePrefix("#").toLong(16))

/** Original resolveAccent raises label contrast against the theme's panel. */
fun accentInk(value: Color, light: Boolean): Color {
    val background = if (light) colorFromHex("#F4F4F2") else colorFromHex("#181818")
    fun contrast(a: Color, b: Color) =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
    var ink = value
    var amount = .05f
    while (contrast(ink, background) < 4.5f && amount <= 1.001f) {
        // Keep the original RGB interpolation (toward black in light mode).
        fun toward(n: Float) =
            ((n + ((if (light) 0f else 1f) - n) * amount) * 255).roundToInt() / 255f
        ink = Color(toward(value.red), toward(value.green), toward(value.blue))
        amount += .05f
    }
    return ink
}
