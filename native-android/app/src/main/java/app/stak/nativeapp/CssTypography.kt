package app.stak.nativeapp

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em

/** CSS vw follows the rendered window rather than Configuration's compatibility width. */
@Composable
fun viewportWidthDp(): Float = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.width.toDp().value
}

/** CSS keeps the complete line box, including leading on the first and last line. */
private fun TextStyle.cssLineBox() = copy(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

fun Typography.withCssLineBoxes() = copy(
    displayLarge = displayLarge.cssLineBox(), displayMedium = displayMedium.cssLineBox(), displaySmall = displaySmall.cssLineBox(),
    headlineLarge = headlineLarge.cssLineBox(), headlineMedium = headlineMedium.cssLineBox(), headlineSmall = headlineSmall.cssLineBox(),
    titleLarge = titleLarge.cssLineBox(), titleMedium = titleMedium.cssLineBox(), titleSmall = titleSmall.cssLineBox(),
    bodyLarge = bodyLarge.cssLineBox().copy(lineHeight = 1.5.em), bodyMedium = bodyMedium.cssLineBox(), bodySmall = bodySmall.cssLineBox(),
    labelLarge = labelLarge.cssLineBox(), labelMedium = labelMedium.cssLineBox(), labelSmall = labelSmall.cssLineBox(),
)
