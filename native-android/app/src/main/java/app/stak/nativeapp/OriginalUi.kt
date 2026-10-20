package app.stak.nativeapp

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*

val LocalOptions = staticCompositionLocalOf<Map<String, String>> { emptyMap() }
val LocalNavigate = staticCompositionLocalOf<(String) -> Unit> { {} }
val Accent: Color @Composable get() = runCatching { Color(android.graphics.Color.parseColor(LocalOptions.current["accent"] ?: "#D71921")) }.getOrDefault(Color(0xffd71921))
val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Rule: Color @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)
val Soft: Color @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = .045f)

@Composable
fun Ambient(modifier: Modifier = Modifier, cinematic: Boolean = false) {
    val options = LocalOptions.current
    val dot = ((options["dotIntensity"] ?: "5").toFloatOrNull() ?: 5f).coerceIn(0f, 10f) / 100
    val glow = ((options["glowIntensity"] ?: "16").toFloatOrNull() ?: 16f).coerceIn(0f, 30f) / 100
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxSize()) {
        val hue = Color(0xff6c7078).copy(alpha = if (cinematic) (glow * 2.6f).coerceAtMost(.5f) else glow)
        drawRect(Brush.radialGradient(listOf(hue, Color.Transparent), center = Offset(size.width * .12f, size.height * .08f), radius = size.height * .7f))
        drawRect(Brush.radialGradient(listOf(hue, Color.Transparent), center = Offset(size.width, size.height * .78f), radius = size.height * .7f))
        val step = 20.dp.toPx()
        var x = step / 2
        while (x < size.width) {
            var y = step / 2
            while (y < size.height) { drawCircle(color.copy(alpha = dot), .7.dp.toPx(), Offset(x, y)); y += step }
            x += step
        }
    }
}

@Composable
fun StakIconButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.size(44.dp).clip(CircleShape).border(1.dp, Rule, CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(17.dp), tint = if (enabled) Muted else Muted.copy(alpha = .3f))
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(java.util.Locale.ROOT), modifier, fontFamily = MonoFont, fontSize = 10.sp, letterSpacing = 1.sp, color = Muted)
}

@Composable
fun OriginalField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, icon: ImageVector? = null, secret: Boolean = false, singleLine: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = .74f)).border(1.dp, Rule, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = Muted)
        BasicTextField(value, onChange, Modifier.weight(1f).padding(vertical = 13.dp).semantics { contentDescription = placeholder }, singleLine = singleLine,
            textStyle = TextStyle(fontFamily = BodyFont, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(Accent), visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, fontFamily = BodyFont, fontSize = 14.sp, color = Muted); inner() } })
        if (secret) IconButton({ visible = !visible }, Modifier.size(32.dp)) { Icon(if (visible) StakIcons.EyeOff else StakIcons.Eye, if (visible) "Hide key" else "Show key", Modifier.size(16.dp)) }
    }
}

@Composable
fun OriginalToggle(label: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.size(48.dp, 44.dp).semantics { contentDescription = label }.toggleable(enabled, role = Role.Switch, onValueChange = onChange), contentAlignment = Alignment.Center) {
            Box(Modifier.size(48.dp, 24.dp).background(if (enabled) Accent else MaterialTheme.colorScheme.onSurface.copy(alpha = .07f), CircleShape).border(1.dp, Rule, CircleShape))
            val x by androidx.compose.animation.core.animateDpAsState(if (enabled) 12.dp else (-12).dp, motionSpec(LocalStakMotion.current.reduced, 120), label = "Switch position")
            Box(Modifier.offset(x = x).size(18.dp).background(Color.White, CircleShape))
        }
    }
}

@Composable
fun Choice(label: String, value: String, options: List<Pair<String, String>>, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Rule, RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = .74f)).clickable(role = Role.Button) { open = true }.padding(horizontal = 12.dp, vertical = 10.dp).semantics { contentDescription = label }, verticalAlignment = Alignment.CenterVertically) {
            Text(options.firstOrNull { it.first == value }?.second ?: value, Modifier.weight(1f), fontSize = 13.sp)
            Icon(StakIcons.ChevronDown, null, Modifier.size(14.dp), tint = Muted)
        }
        DropdownMenu(open, { open = false }) { options.forEach { (id, title) -> DropdownMenuItem(text = { Text(title, fontSize = 13.sp) }, onClick = { onChange(id); open = false }) } }
    }
}

@Composable
fun OriginalEmpty(title: String, description: String, action: String = "", icon: ImageVector = StakIcons.Film, onClick: () -> Unit = {}) {
    val rule = Rule
    Column(Modifier.fillMaxWidth().drawWithContent {
        drawContent()
        drawRoundRect(rule, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
    }.background(MaterialTheme.colorScheme.surface.copy(alpha = .74f), RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(52.dp).background(Soft, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(23.dp), tint = Muted) }
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action.isNotBlank()) MotionButton(action, onClick = onClick)
    }
}
