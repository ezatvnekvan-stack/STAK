package app.stak.nativeapp

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle

/** CSS keeps the complete line box, including leading on the first and last line. */
private fun TextStyle.cssLineBox() = copy(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

fun Typography.withCssLineBoxes() = copy(
    displayLarge = displayLarge.cssLineBox(), displayMedium = displayMedium.cssLineBox(), displaySmall = displaySmall.cssLineBox(),
    headlineLarge = headlineLarge.cssLineBox(), headlineMedium = headlineMedium.cssLineBox(), headlineSmall = headlineSmall.cssLineBox(),
    titleLarge = titleLarge.cssLineBox(), titleMedium = titleMedium.cssLineBox(), titleSmall = titleSmall.cssLineBox(),
    bodyLarge = bodyLarge.cssLineBox(), bodyMedium = bodyMedium.cssLineBox(), bodySmall = bodySmall.cssLineBox(),
    labelLarge = labelLarge.cssLineBox(), labelMedium = labelMedium.cssLineBox(), labelSmall = labelSmall.cssLineBox(),
)
