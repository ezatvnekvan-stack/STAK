package app.stak.nativeapp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun HomeHeroBrand(media: Media) {
    val context = LocalContext.current
    val reduced = LocalStakMotion.current.reduced
    var failed by remember(media.logo) { mutableStateOf(false) }
    var loaded by remember(media.logo) { mutableStateOf(false) }
    val opacity = animateFloatAsState(if (loaded) 1f else 0f, motionSpec(reduced, 180), label = "Hero logo reveal")
    val size = (viewportWidthDp() * .07f).coerceIn(26f, 40f)
    val fallback: @Composable () -> Unit = {
        Text(media.name, fontFamily = BodyFont, fontWeight = FontWeight.Bold,
            fontSize = size.sp, lineHeight = (size * 1.1f).sp, color = Color.White,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (media.logo.isBlank() || failed) fallback()
    else Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.BottomStart) {
        if (!loaded) fallback()
        val request = remember(context, media.logo) { ImageRequest.Builder(context).data(media.logo).build() }
        AsyncImage(request, media.name, Modifier.fillMaxSize().testTag("home-hero-logo")
            .graphicsLayer { alpha = opacity.value }, contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart, onSuccess = { loaded = true }, onError = { failed = true })
    }
}
