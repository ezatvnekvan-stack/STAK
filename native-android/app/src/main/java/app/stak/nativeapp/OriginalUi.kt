package app.stak.nativeapp

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
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

/** A CSS border belongs inside the row's declared height. */
fun Modifier.cssTopBorder(color: Color) = drawBehind {
    val stroke = 1.dp.toPx()
    drawLine(color, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), strokeWidth = stroke)
}


fun Modifier.cssDashedBorder(color: Color, radius: Dp = 16.dp) = drawWithContent {
    drawContent()
    val stroke = 1.dp.toPx()
    drawRoundRect(color, Offset(stroke / 2, stroke / 2), androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
}

@Composable
fun CapacitorSettingsPanel(modifier: Modifier = Modifier, verticalPadding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(modifier, radius = 18.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = verticalPadding), content = content)
    }
}

@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(java.util.Locale.ROOT), modifier, fontFamily = SectionFont,
        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 21.75.sp, letterSpacing = .675.sp)
}

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
    val elapsed = remember { mutableLongStateOf(0L) }
    LaunchedEffect(options["ambientAnimation"], reduced, lifecycle) {
        if (
            options["ambientAnimation"] != "true" ||
                reduced ||
                !lifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        )
            return@LaunchedEffect
        var previous = 0L
        while (true) withFrameNanos { time ->
            if (previous > 0) elapsed.longValue += time - previous
            previous = time
        }
    }
    val easing = remember { androidx.compose.animation.core.CubicBezierEasing(.42f, 0f, .58f, 1f) }
    Box(modifier.fillMaxSize()) {
        Canvas(
            Modifier.matchParentSize().graphicsLayer {
                // Read the clock in the layer phase: drifting does not recompose or redraw
                // the gradient/dot canvases at 90/120 Hz.
                val phase = (elapsed.longValue / 1_000_000_000.0 / 100.0) % 2
                val linear = (if (phase <= 1) phase else 2 - phase).toFloat()
                val progress = if (reduced || options["ambientAnimation"] != "true") 0f else easing.transform(linear)
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
        CachedDotField(color, dot, Modifier.matchParentSize())
    }
}

@Composable
fun StakIconButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp = 17.dp,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val interactions = remember {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    val pressed by interactions.collectIsPressedAsState()
    val policy = LocalStakMotion.current
    val view = androidx.compose.ui.platform.LocalView.current
    val scale by
        androidx.compose.animation.core.animateFloatAsState(
            if (pressed && enabled && !policy.reduced) .92f else 1f,
            if (policy.reduced) androidx.compose.animation.core.snap()
            else androidx.compose.animation.core.spring(dampingRatio = .8f, stiffness = 1100f),
            label = "Icon press",
        )
    Box(
        modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else .4f
            }
            .clip(CircleShape)
            .background(if (primary) MaterialTheme.colorScheme.onSurface else Color.Transparent)
            .border(1.dp, if (primary) MaterialTheme.colorScheme.onSurface else Rule, CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactions,
                indication = null,
            ) {
                if (policy.haptics)
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            label,
            Modifier.size(iconSize),
            tint = if (primary) MaterialTheme.colorScheme.background else Muted,
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
    accessibilityLabel: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val border by
        androidx.compose.animation.animateColorAsState(
            if (focused && enabled) AccentInk.copy(alpha = .65f) else Rule,
            motionSpec(LocalStakMotion.current.reduced, 180),
            label = "Field focus",
        )
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .74f))
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = horizontalInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = Muted)
        BasicTextField(
            value,
            onChange,
            Modifier.weight(1f)
                .padding(vertical = 10.dp)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = accessibilityLabel ?: placeholder },
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
            IconButton({ visible = !visible }, Modifier.size(44.dp), enabled = enabled) {
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
        StakSwitch(label, enabled, onChange = onChange)
    }
}

@Composable
fun StakSwitch(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val policy = LocalStakMotion.current
    val view = androidx.compose.ui.platform.LocalView.current
    val track by
        androidx.compose.animation.animateColorAsState(
            if (checked) Accent
            else if (MaterialTheme.colorScheme.background.red < .5f) Color(0xff222222)
            else Color(0xffd9d9d5),
            motionSpec(policy.reduced, 180),
            label = "Switch track",
        )
    val thumb by
        androidx.compose.animation.animateColorAsState(
            if (checked) AccentForeground else MaterialTheme.colorScheme.onSurface,
            motionSpec(policy.reduced, 160),
            label = "Switch thumb",
        )
    val x by
        androidx.compose.animation.core.animateDpAsState(
            if (checked) 11.dp else (-11).dp,
            if (policy.reduced) androidx.compose.animation.core.snap()
            else androidx.compose.animation.core.spring(dampingRatio = .85f, stiffness = 900f),
            label = "Switch position",
        )
    Box(
        modifier
            .size(48.dp, 44.dp)
            .graphicsLayer { alpha = if (enabled) 1f else .5f }
            .semantics { contentDescription = label }
            .toggleable(checked, enabled = enabled, role = Role.Switch) {
                if (policy.haptics)
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                onChange(it)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(48.dp, 24.dp)
                .background(track, CircleShape)
                .border(1.dp, Rule, CircleShape)
        )
        Box(Modifier.offset(x = x).size(18.dp).background(thumb, CircleShape))
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
    enabled: Boolean = true,
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
                .graphicsLayer { alpha = if (enabled) 1f else .4f }
                .clickable(enabled = enabled, role = Role.Button) { open = true }
                .padding(horizontal = 15.dp, vertical = 11.dp)
                .semantics { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                options.firstOrNull { it.first == value }?.second ?: value,
                Modifier.weight(1f),
                fontSize = 14.sp,
                lineHeight = 19.6.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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

/** Paint the compact HTML checkbox while the enclosing 44 dp row owns interaction. */
@Composable
fun CompactCheckbox(checked: Boolean) {
    val light = MaterialTheme.colorScheme.background.red > .5f
    Box(Modifier.size(13.dp)
        .background(if (checked) Accent else if (light) Color.White else Color(0xff3b3b3b), RoundedCornerShape(2.dp))
        .border(1.dp, if (checked) Accent else if (light) Color(0xff767676) else Color(0xff858585), RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center) {
        if (checked) Icon(StakIcons.Check, null, Modifier.size(11.dp), tint = AccentForeground)
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
