package app.stak.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage

/** Original TitleHero layout: artwork and identity, with no secondary poster. */
@Composable
fun CapacitorTitleHeader(media: Media, onBack: () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    val titleSize = (viewportWidthDp() * .075f).coerceIn(28f, 48f)
    var failedLogo by remember(media.logo) { mutableStateOf(false) }
    var failedArtwork by remember(media.background) { mutableStateOf(false) }
    val hasArtwork = media.background.isNotBlank() && !failedArtwork
    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        if (hasArtwork) {
            Artwork(media, Modifier.matchParentSize().graphicsLayer {
                alpha = .94f
                compositingStrategy = CompositingStrategy.Offscreen
            }.drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(
                    0f to Color.Transparent, .08f to Color.Black, .38f to Color.Black,
                    .58f to Color.Black.copy(alpha = 11f / 15f),
                    .82f to Color.Black.copy(alpha = .2f), 1f to Color.Transparent,
                ), blendMode = BlendMode.DstIn)
            }, backdrop = true, alignment = androidx.compose.ui.BiasAlignment(0f, -.52f), onError = { failedArtwork = true })
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to background.copy(alpha = .05f), .26f to Color.Transparent,
            .54f to background.copy(alpha = .35f), .72f to background.copy(alpha = .48f),
            1f to Color.Transparent,
        )))
        Column(Modifier.fillMaxWidth().heightIn(min = if (hasArtwork) 390.dp else 220.dp)
            .padding(horizontal = 16.dp).padding(top = if (hasArtwork) 205.dp else 90.dp, bottom = 20.dp)) {
            Column(Modifier.padding(bottom = 8.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 60.dp), contentAlignment = Alignment.BottomStart) {
                    if (media.logo.isNotBlank() && !failedLogo) {
                        AsyncImage(media.logo, media.name, Modifier.widthIn(max = 360.dp).heightIn(max = 84.dp),
                            contentScale = ContentScale.Fit, onError = { failedLogo = true })
                    } else Text(media.name, Modifier.testTag("title-name"), fontFamily = DotFont,
                        fontWeight = FontWeight.Black, fontSize = titleSize.sp,
                        lineHeight = (titleSize * 1.1f).sp, letterSpacing = (titleSize * .02f).sp)
                }
                Spacer(Modifier.height(10.dp))
                Text(listOf(media.year, media.runtime).filter(String::isNotBlank).joinToString(" · "),
                    fontSize = 10.sp, lineHeight = 15.sp, color = Muted)
                Spacer(Modifier.height(11.dp))
                RatingBadge(media, quiet = true)
                Spacer(Modifier.height(8.dp))
                Text(media.genres.take(3).joinToString(" · "), fontSize = 10.sp, lineHeight = 15.sp, color = Muted)
            }
        }
        StakIconButton(StakIcons.ArrowLeft, "Back", Modifier.align(Alignment.TopStart)
            .padding(start = 16.dp, top = 12.dp).background(background.copy(alpha = .75f), CircleShape), onClick = onBack)
    }
}
