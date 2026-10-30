package app.stak.nativeapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val StakTypography = Typography(
    displayLarge = TextStyle(fontFamily = DotoFont, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = DotoFont, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp),
    headlineLarge = TextStyle(fontFamily = DotoFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = DotoFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = DotoFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = SpaceMonoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.8.sp),
    bodyLarge = TextStyle(fontFamily = HankenFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = HankenFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = HankenFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = SpaceMonoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.9.sp),
    labelMedium = TextStyle(fontFamily = SpaceMonoFont, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 1.0.sp),
    labelSmall = TextStyle(fontFamily = SpaceMonoFont, fontWeight = FontWeight.Medium, fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 0.8.sp)
)
