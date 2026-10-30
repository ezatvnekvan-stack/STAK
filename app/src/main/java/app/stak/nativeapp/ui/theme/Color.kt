package app.stak.nativeapp.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// STAK Nothing palette — mirrors src/index.css tokens.
val StakBackground = Color(0xFF080808)
val StakBackgroundAlt = Color(0xFF0C0C0C)
val StakSurface = Color(0xFF111111)
val StakSurface2 = Color(0xFF101010)
val StakSurface3 = Color(0xFF191919)
val StakBorder = Color(0xFF2A2A2A)
val StakBorderSoft = Color(0xFF222222)
val StakText = Color(0xFFF4F4F2)
val StakTextSoft = Color(0xFFD4D4D2)
val StakTextMuted = Color(0xFFA0A0A0)
val StakTextFaint = Color(0xFF707070)

/**
 * Accent is a single snapshot state so the Appearance setting can re-tint the
 * whole native UI without threading a theme object through every screen.
 */
var StakAccent by mutableStateOf(Color(0xFFD71921))
val StakAccentSoft: Color get() = StakAccent.copy(alpha = 0.20f)

/**
 * Web parity: the browser computes the "ink" accent with a naive per-channel sRGB
 * lerp (accent + 0.25 * (255 - accent)). Compose's own Color.lerp routes through
 * OKLab and produces visibly different values, so the blend is done by hand here.
 */
val StakAccentInk: Color get() {
    val accent = StakAccent
    return Color(
        red = accent.red + (1f - accent.red) * 0.25f,
        green = accent.green + (1f - accent.green) * 0.25f,
        blue = accent.blue + (1f - accent.blue) * 0.25f,
        alpha = accent.alpha
    )
}

val StakSuccess = Color(0xFF10B981)
val StakWarning = Color(0xFFF5A623)
val StakGold = Color(0xFFE9B949)
val StakGlass = Color(0xB3111111)
val StakGlassStrong = Color(0xCC101010)
val StakOverlay = Color(0xB3000000)

val StakOnAccent:Color get() = if(StakAccent.red*.299f+StakAccent.green*.587f+StakAccent.blue*.114f>.68f)Color(0xFF111111) else Color.White
