package app.stak.nativeapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun StakTheme(content: @Composable () -> Unit) {
    // Rebuilt whenever the accent snapshot changes so Material controls follow
    // the Appearance setting too.
    val scheme = darkColorScheme(
        background = StakBackground,
        surface = StakSurface,
        primary = StakAccent,
        onPrimary = StakOnAccent,
        onBackground = StakText,
        onSurface = StakText,
        outline = StakBorder
    )
    MaterialTheme(colorScheme = scheme, typography = StakTypography, content = content)
}
