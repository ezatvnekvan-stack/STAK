@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
package app.stak.nativeapp.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import app.stak.nativeapp.R

// Both assets are variable fonts. Register the requested axis explicitly instead
// of synthesizing bold from a single default instance (which changes dot shapes).
val DotoFont = FontFamily((100..900 step 100).map { weight ->
    Font(R.font.doto, weight = FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
})
val SpaceMonoFont = FontFamily(Font(R.font.space_mono), Font(R.font.space_mono_bold, FontWeight.Bold))
val HankenFont = FontFamily((100..900 step 100).map { weight ->
    Font(R.font.hanken_grotesk, weight = FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
})
