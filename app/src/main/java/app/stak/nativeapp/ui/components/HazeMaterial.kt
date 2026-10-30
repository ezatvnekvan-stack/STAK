package app.stak.nativeapp.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/** Dark tint keeps labels legible over bright posters. No animated refraction or blur-radius changes. */
fun stakHazeStyle()=HazeStyle(
    backgroundColor=Color(0xFF08090B),
    tint=HazeTint(Color(0xB808090B)),
    blurRadius=22.dp,
    noiseFactor=.025f,
    fallbackTint=HazeTint(Color(0xF008090B))
)
