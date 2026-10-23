package app.stak.nativeapp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Shared palette for every native destination, including opaque modal surfaces. */
fun premiumColors(dark: Boolean, amoled: Boolean = false): ColorScheme = if (dark) darkColorScheme(
    primary = Color(0xffF4F0E8), onPrimary = Color(0xff101316),
    background = if (amoled) Color.Black else Color(0xff0C0F13),
    surface = Color(0xff151A20), surfaceDim = Color(0xff0C0F13), surfaceBright = Color(0xff343B44),
    surfaceContainerLowest = Color(0xff080A0D), surfaceContainerLow = Color(0xff13181E),
    surfaceContainer = Color(0xff1B222A), surfaceContainerHigh = Color(0xff232B35),
    surfaceContainerHighest = Color(0xff29323D), surfaceVariant = Color(0xff29323D),
    surfaceTint = Color.Transparent,
    onBackground = Color(0xffF3F1EC), onSurface = Color(0xffF3F1EC), onSurfaceVariant = Color(0xffA5AFBB),
    outline = Color(0xff677483), outlineVariant = Color(0xff2C3642),
    error = Color(0xffFF9A91),
) else lightColorScheme(
    primary = Color(0xff16202A), onPrimary = Color(0xffFFFDF9),
    background = Color(0xffF4F1EB), surface = Color(0xffFFFDF9),
    surfaceDim = Color(0xffD8D2C9), surfaceBright = Color(0xffFFFDF9),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xffF9F6F0),
    surfaceContainer = Color(0xffEDE8E0), surfaceContainerHigh = Color(0xffE6E0D7),
    surfaceContainerHighest = Color(0xffDED8CE), surfaceVariant = Color(0xffDED8CE),
    surfaceTint = Color.Transparent,
    onBackground = Color(0xff17212C), onSurface = Color(0xff17212C), onSurfaceVariant = Color(0xff586572),
    outline = Color(0xff7A858E), outlineVariant = Color(0xffD9D3C9),
    error = Color(0xffAA3029),
)

fun premiumTypography() = Typography(
    displaySmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-.045).em),
    headlineLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-.04).em),
    headlineSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 30.sp, letterSpacing = (-.025).em),
    titleLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-.02).em),
    titleMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = BodyFont, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = .25.sp),
    labelMedium = TextStyle(fontFamily = MonoFont, fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = .4.sp),
    labelSmall = TextStyle(fontFamily = MonoFont, fontSize = 9.sp, lineHeight = 14.sp, letterSpacing = .5.sp),
)

@Composable
fun PremiumPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val accent = Accent
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(colors.surface, colors.surface, accent.copy(alpha = .065f))))
            .border(1.dp, Rule, RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), content = content,
    )
}

@Composable
fun PremiumIntro(eyebrow: String, title: String, description: String = "") {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(5.dp).background(Accent, RoundedCornerShape(3.dp)))
            Label(eyebrow)
        }
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}

@Composable
fun PremiumMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, Rule)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Label(label)
        }
    }
}

/** A single entry transition, never attached to a modal's shared-element layout. */
@Composable
fun Modifier.premiumReveal(key: Any): Modifier {
    val reduced = LocalStakMotion.current.reduced
    var shown by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { shown = true }
    val progress by animateFloatAsState(if (shown) 1f else 0f, if (reduced) snap() else tween(280, easing = StakEase), label = "Page content entry")
    return graphicsLayer { alpha = progress; translationY = if (reduced) 0f else (1f - progress) * 10.dp.toPx() }
}

fun premiumPageCaption(title: String): Pair<String, String> = when (title) {
    "Discover" -> "THE DISCOVERY ROOM" to "Find something worth your evening."
    "Watchlist" -> "YOUR NEXT WATCH" to "The stories you want to come back to."
    "Tracking" -> "THE VIEWING JOURNAL" to "Every episode. Every memorable ending."
    "Library" -> "YOUR COLLECTION" to "Everything you keep, beautifully organized."
    "Settings" -> "MAKE IT YOURS" to "A considered space for every preference."
    "TorBox account" -> "CONNECTED SERVICES" to "Your cloud account, clearly in view."
    "Jellyfin" -> "PERSONAL MEDIA" to "Bring your own cinema into STAK."
    "Jellyfin Remote" -> "NOW PLAYING" to "Your cinema, within reach."
    "Search sources" -> "SOURCE STUDIO" to "Connect the places you discover releases."
    "Metadata & ratings" -> "THE DETAILS MATTER" to "Choose the information behind every title."
    "Appearance" -> "SET THE ATMOSPHERE" to "Shape the way your cinema feels."
    "Home & browsing" -> "CURATE YOUR SPACE" to "A home arranged around what you love."
    "Data & app" -> "YOUR DATA, YOURS" to "Back up, restore and keep your collection safe."
    "Share" -> "CURATED TOGETHER" to "Collections worth passing on."
    else -> "STAK / COLLECTION" to ""
}
