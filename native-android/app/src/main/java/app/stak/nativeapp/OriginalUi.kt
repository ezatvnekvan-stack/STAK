package app.stak.nativeapp

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.currentStateAsState

val LocalOptions = staticCompositionLocalOf<Map<String, String>> { emptyMap() }
val LocalNavigate = staticCompositionLocalOf<(String) -> Unit> { {} }
val Accent: Color
    @Composable
    get() =
        colorFromHex(
            resolvedAccentHex(
                LocalOptions.current["accent"] ?: "red",
                MaterialTheme.colorScheme.background.red > .5f,
            )
        )
val AccentInk: Color
    @Composable get() = accentInk(Accent, MaterialTheme.colorScheme.background.red > .5f)
val AccentForeground: Color
    @Composable get() = if (Accent.luminance() >= .1791288f) Color.Black else Color.White
val Muted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Rule: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)
val Soft: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = .045f)

@Composable
fun Ambient(
    modifier: Modifier = Modifier,
    cinematic: Boolean = false,
    tone: Color = Color(0xff6c7078),
) {
    val options = LocalOptions.current
    val dot = ((options["dotIntensity"] ?: "5").toFloatOrNull() ?: 5f).coerceIn(0f, 10f) / 100
    val glow = ((options["glowIntensity"] ?: "16").toFloatOrNull() ?: 16f).coerceIn(0f, 30f) / 100
    val dark = MaterialTheme.colorScheme.background.red < .5f
    val adaptive = cinematic && options["cinematicColors"] != "false"
    val opacity =
        if (!adaptive) glow
        else if (dark) (glow * 2.6f).coerceAtMost(.5f) else (glow * 1.9f).coerceAtMost(.3f)
    val color = MaterialTheme.colorScheme.onSurface
    val reduced = LocalStakMotion.current.reduced
    val lifecycle by
        androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(options["ambientAnimation"], reduced, lifecycle) {
        if (
            options["ambientAnimation"] != "true" ||
                reduced ||
                !lifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        )
            return@LaunchedEffect
        var previous = 0L
        while (true) withFrameNanos { time ->
            if (previous > 0) elapsed += time - previous
            previous = time
        }
    }
    val progress =
        if (reduced || options["ambientAnimation"] != "true") 0f
        else {
            val phase = (elapsed / 1_000_000_000.0 / 100.0) % 2
            val linear = (if (phase <= 1) phase else 2 - phase).toFloat()
            androidx.compose.animation.core.CubicBezierEasing(.42f, 0f, .58f, 1f).transform(linear)
        }
    Box(modifier.fillMaxSize()) {
        Canvas(
            Modifier.matchParentSize().graphicsLayer {
                alpha = opacity
                translationX = size.width * .02f * progress
                translationY = size.height * .01f * progress
            }
        ) {
            fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, stop: Float) {
                withTransform({
                    translate(cx, cy)
                    scale(rx, ry, pivot = Offset.Zero)
                }) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(tone, Color.Transparent),
                            center = Offset.Zero,
                            radius = stop,
                        ),
                        radius = stop,
                        center = Offset.Zero,
                    )
                }
            }
            // CSS radial-gradient's farthest-corner ellipse, in the original -5% layer.
            ellipse(
                size.width * 1.05f,
                size.height * .808f,
                size.width * 1.1f * 1.41421356f,
                size.height * .78f * 1.1f * 1.41421356f,
                if (adaptive) .85f else .70f,
            )
            ellipse(
                size.width * .082f,
                size.height * .038f,
                size.width * .88f * 1.1f * 1.41421356f,
                size.height * .92f * 1.1f * 1.41421356f,
                if (adaptive) .80f else .65f,
            )
        }
        Canvas(Modifier.matchParentSize()) {
            val step = 20.dp.toPx()
            var x = step / 2
            while (x < size.width) {
                var y = step / 2
                while (y < size.height) {
                    val fraction = y / size.height
                    val mask =
                        if (fraction < .3f) .6f + .4f * fraction / .3f
                        else 1f - (1f - 2f / 3f) * (fraction - .3f) / .7f
                    drawCircle(color.copy(alpha = dot * mask), .7.dp.toPx(), Offset(x, y))
                    y += step
                }
                x += step
            }
        }
    }
}

@Composable
fun StakIconButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.dp, Rule, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            label,
            Modifier.size(17.dp),
            tint = if (enabled) Muted else Muted.copy(alpha = .3f),
        )
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(java.util.Locale.ROOT),
        modifier,
        fontFamily = MonoFont,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        letterSpacing = 1.1.sp,
        color = Muted,
    )
}

@Composable
fun OriginalField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    secret: Boolean = false,
    reveal: Boolean = false,
    singleLine: Boolean = true,
    horizontalInset: Dp = 14.dp,
    enabled: Boolean = true,
    visibilityLabel: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .74f))
            .border(1.dp, Rule, RoundedCornerShape(12.dp))
            .padding(horizontal = horizontalInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = Muted)
        BasicTextField(
            value,
            onChange,
            Modifier.weight(1f).padding(vertical = 10.dp).semantics {
                contentDescription = placeholder
            },
            singleLine = singleLine,
            enabled = enabled,
            textStyle =
                TextStyle(
                    fontFamily = BodyFont,
                    fontSize = 14.sp,
                    lineHeight = 19.6.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            cursorBrush = SolidColor(Accent),
            visualTransformation =
                if (secret && !visible) PasswordVisualTransformation()
                else VisualTransformation.None,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text
                ),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty())
                        Text(
                            placeholder,
                            fontFamily = BodyFont,
                            fontSize = 14.sp,
                            lineHeight = 19.6.sp,
                            color =
                                if (MaterialTheme.colorScheme.background.red < .5f)
                                    Color(0xff909090)
                                else Color(0xff737373),
                        )
                    inner()
                }
            },
        )
        if (secret && reveal)
            IconButton({ visible = !visible }, Modifier.size(32.dp), enabled = enabled) {
                Icon(
                    if (visible) StakIcons.EyeOff else StakIcons.Eye,
                    visibilityLabel ?: if (visible) "Hide key" else "Show key",
                    Modifier.size(16.dp),
                )
            }
    }
}

@Composable
fun OriginalToggle(label: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box(
            Modifier.size(48.dp, 44.dp)
                .semantics { contentDescription = label }
                .toggleable(enabled, role = Role.Switch, onValueChange = onChange),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(48.dp, 24.dp)
                    .background(
                        if (enabled) Accent
                        else if (MaterialTheme.colorScheme.background.red < .5f) Color(0xff222222)
                        else Color(0xffd9d9d5),
                        CircleShape,
                    )
                    .border(1.dp, Rule, CircleShape)
            )
            val x by
                androidx.compose.animation.core.animateDpAsState(
                    if (enabled) 11.dp else (-11).dp,
                    motionSpec(LocalStakMotion.current.reduced, 120),
                    label = "Switch position",
                )
            Box(
                Modifier.offset(x = x)
                    .size(18.dp)
                    .background(
                        if (enabled) AccentForeground else MaterialTheme.colorScheme.onSurface,
                        CircleShape,
                    )
            )
        }
    }
}

@Composable
fun Choice(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    plain: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, if (plain) Color.Transparent else Rule, RoundedCornerShape(12.dp))
                .background(
                    if (plain) Color.Transparent
                    else MaterialTheme.colorScheme.surface.copy(alpha = .74f)
                )
                .clickable(role = Role.Button) { open = true }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                options.firstOrNull { it.first == value }?.second ?: value,
                Modifier.weight(1f),
                fontSize = 13.sp,
            )
            Icon(StakIcons.ChevronDown, null, Modifier.size(14.dp), tint = Muted)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { (id, title) ->
                DropdownMenuItem(
                    text = { Text(title, fontSize = 13.sp) },
                    onClick = {
                        onChange(id)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
fun OriginalEmpty(
    title: String,
    description: String,
    action: String = "",
    icon: ImageVector = StakIcons.Film,
    compact: Boolean = false,
    onClick: () -> Unit = {},
) {
    val rule = Rule
    Column(
        Modifier.fillMaxWidth()
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    rule,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                    style =
                        androidx.compose.ui.graphics.drawscope.Stroke(
                            1.dp.toPx(),
                            pathEffect =
                                PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                        ),
                )
            }
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = .74f),
                RoundedCornerShape(16.dp),
            )
            .padding(
                horizontal = if (compact) 17.dp else 25.dp,
                vertical = if (compact) 25.dp else 49.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(52.dp).background(Soft, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(23.dp), tint = Muted)
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action.isNotBlank())
            MotionButton(action, Modifier.padding(top = 4.dp), onClick = onClick)
    }
}
