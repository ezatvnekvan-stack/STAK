package app.stak.nativeapp

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AppearanceSettings(vm: StakViewModel) {
    val o = LocalOptions.current
    val dark = MaterialTheme.colorScheme.background.red < .5f
    val reduced by vm.reduced.collectAsStateWithLifecycle()
    val haptics by vm.haptics.collectAsStateWithLifecycle()
    val accent = Accent
    val ink = AccentInk
    val rawAccent = o["accent"] ?: "red"
    var customOpen by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 16.dp)) {
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, Rule, RoundedCornerShape(14.dp))
        ) {
            Canvas(Modifier.matchParentSize()) {
                withTransform({ scale(1f, size.height / size.width, pivot = Offset.Zero) }) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = .12f), Color.Transparent),
                            center = Offset(size.width, 0f),
                            radius = size.width * .9899495f,
                        ),
                        size = Size(size.width, size.width),
                    )
                }
            }
            Column(Modifier.padding(21.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "LIVE PREVIEW",
                    Modifier.padding(bottom = 10.dp).height(13.5.dp),
                    fontFamily = MonoFont,
                    fontSize = 9.sp,
                    lineHeight = 13.5.sp,
                    letterSpacing = .99.sp,
                    color = Muted,
                )
                Text(
                    "Make it yours.",
                    Modifier.height(36.dp),
                    fontFamily = DotFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    letterSpacing = (-.04).em,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { index ->
                        Box(
                            Modifier.size(34.dp, 48.dp)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = .15f),
                                            Color.Transparent,
                                        )
                                    ),
                                    RoundedCornerShape(6.dp),
                                )
                                .border(
                                    1.dp,
                                    if (index == 0) ink else Rule,
                                    RoundedCornerShape(6.dp),
                                )
                        )
                    }
                }
                Text(
                    "Your accent · your atmosphere",
                    Modifier.height(16.dp),
                    fontFamily = MonoFont,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.sp,
                    color = ink,
                )
            }
            ThemeMotionPreview(
                dark,
                { vm.option("theme", if (dark) "light" else "dark") },
                Modifier.align(Alignment.TopEnd).padding(top = 15.dp, end = 15.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        AppearanceLabel("Theme")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("dark", "light", "system").forEach { theme ->
                val selected = (o["theme"] ?: "dark") == theme
                Box(
                    Modifier.weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) accent.copy(alpha = .1f) else Color.Transparent)
                        .border(1.dp, if (selected) ink else Rule, RoundedCornerShape(12.dp))
                        .semantics {
                            role = Role.RadioButton
                            this.selected = selected
                        }
                        .clickable { vm.option("theme", theme) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        theme.uppercase(),
                        fontFamily = MonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        letterSpacing = .4.sp,
                        color = if (selected) ink else Muted,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        AppearanceLabel(
            "Accent · ${OriginalAccents.firstOrNull { it.id==rawAccent || it.hex.equals(rawAccent,true) }?.label ?: "Custom"}"
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OriginalAccents.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { choice ->
                        val selected = rawAccent == choice.id || rawAccent.equals(choice.hex, true)
                        val paint = colorFromHex(choice.hex)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (selected)
                                Box(Modifier.requiredSize(52.dp).border(2.dp, paint, CircleShape))
                            Box(
                                Modifier.size(44.dp)
                                    .border(1.dp, Rule, CircleShape)
                                    .semantics {
                                        contentDescription = choice.label
                                        role = Role.RadioButton
                                        this.selected = selected
                                    }
                                    .clickable { vm.option("accent", choice.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(24.dp).background(paint, CircleShape))
                                if (selected)
                                    Icon(
                                        StakIcons.Check,
                                        null,
                                        Modifier.size(13.dp),
                                        tint =
                                            if (paint.luminance() >= .1791288f) Color.Black
                                            else Color.White,
                                    )
                            }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppearanceLabel("Custom colour", Modifier.weight(1f), bottom = 0.dp)
            Box(
                Modifier.size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, Rule, RoundedCornerShape(12.dp))
                    .clickable { customOpen = true }
                    .padding(7.dp)
                    .semantics { contentDescription = "Custom accent colour" }
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(colorFromHex(resolvedAccentHex(rawAccent)))
                        .border(1.dp, Muted)
                )
            }
            Text(
                resolvedAccentHex(rawAccent).uppercase(Locale.ROOT),
                fontFamily = MonoFont,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
        OriginalToggle("Reduce animations", reduced) { vm.appearance("reduced", it) }
        OriginalToggle("Compact layout", o["compact"] == "true") {
            vm.option("compact", it.toString())
        }
        OriginalToggle("AMOLED black", o["amoled"] == "true") { vm.option("amoled", it.toString()) }
        Spacer(Modifier.height(16.dp))
        AppearanceLabel("Atmosphere")
        listOf(
                Triple("dotIntensity", "Dot Matrix intensity", 10),
                Triple("glowIntensity", "Ambient Glow intensity", 30),
            )
            .forEach { (key, label, max) ->
                val value =
                    (o[key]?.toIntOrNull() ?: if (key == "dotIntensity") 5 else 16).coerceIn(0, max)
                Column(
                    Modifier.padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(label, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 21.sp)
                        Text(
                            "$value%",
                            fontFamily = MonoFont,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = Muted,
                        )
                    }
                    OriginalRange(value, max, label) { vm.option(key, it.toString()) }
                }
            }
        OriginalToggle("Ambient animation", o["ambientAnimation"] == "true") {
            vm.option("ambientAnimation", it.toString())
        }
        OriginalToggle("Cinematic adaptive colours", o["cinematicColors"] != "false") {
            vm.option("cinematicColors", it.toString())
        }
        Box(Modifier.padding(vertical = 12.dp)) {
            MotionButton("Reset appearance") {
                vm.option("theme", "dark")
                vm.option("accent", "red")
                vm.appearance("reduced", false)
                mapOf(
                        "dotIntensity" to "5",
                        "glowIntensity" to "16",
                        "compact" to "false",
                        "amoled" to "false",
                        "ambientAnimation" to "false",
                        "cinematicColors" to "true",
                    )
                    .forEach { (k, v) -> vm.option(k, v) }
                listOf("IMDb", "TMDB", "Rotten Tomatoes", "Metacritic").forEach {
                    vm.option("rating_$it", "true")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        AppearanceLabel("Visible ratings")
        listOf("IMDb", "TMDB", "Rotten Tomatoes", "Metacritic").forEach { rating ->
            Box(Modifier.height(48.dp)) {
                OriginalToggle(rating, o["rating_$rating"] != "false") {
                    vm.option("rating_$rating", it.toString())
                }
            }
        }
        OriginalToggle("Haptics", haptics) { vm.appearance("haptics", it) }
    }
    if (customOpen) {
        var custom by remember { mutableStateOf(resolvedAccentHex(rawAccent)) }
        AlertDialog(
            onDismissRequest = { customOpen = false },
            title = { Text("Custom colour") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier.fillMaxWidth()
                            .height(64.dp)
                            .background(
                                runCatching { colorFromHex(custom) }.getOrDefault(accent),
                                RoundedCornerShape(12.dp),
                            )
                    )
                    OriginalField(custom, { custom = it }, "#D71921")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.option("accent", custom)
                        customOpen = false
                    },
                    enabled = Regex("#[0-9a-fA-F]{6}").matches(custom),
                ) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { customOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AppearanceLabel(text: String, modifier: Modifier = Modifier, bottom: Dp = 10.dp) {
    Text(
        text.uppercase(Locale.ROOT),
        modifier.padding(bottom = bottom).height(15.dp),
        fontFamily = MonoFont,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        letterSpacing = 1.1.sp,
        color = Muted,
    )
}

/** Blink's range geometry, with native drag, tap and accessibility progress actions. */
@Composable
private fun OriginalRange(value: Int, max: Int, label: String, onChange: (Int) -> Unit) {
    val latest by rememberUpdatedState(onChange)
    val accent = Accent
    Canvas(
        Modifier.fillMaxWidth()
            .height(44.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo =
                    ProgressBarRangeInfo(value.toFloat(), 0f..max.toFloat(), max - 1)
                setProgress {
                    latest(it.roundToInt().coerceIn(0, max))
                    true
                }
            }
            .pointerInput(max) {
                detectTapGestures {
                    latest(
                        ((it.x - 8.dp.toPx()) / (size.width - 16.dp.toPx()) * max)
                            .roundToInt()
                            .coerceIn(0, max)
                    )
                }
            }
            .pointerInput(max) {
                detectDragGestures { change, _ ->
                    change.consume()
                    latest(
                        ((change.position.x - 8.dp.toPx()) / (size.width - 16.dp.toPx()) * max)
                            .roundToInt()
                            .coerceIn(0, max)
                    )
                }
            }
    ) {
        val center = size.height / 2
        val x = 8.dp.toPx() + (size.width - 16.dp.toPx()) * value / max
        drawRoundRect(
            Color(0xffeeeeee),
            Offset(1.dp.toPx(), center - 3.5.dp.toPx()),
            Size(size.width - 2.dp.toPx(), 7.dp.toPx()),
            CornerRadius(2.dp.toPx()),
        )
        drawRoundRect(
            accent,
            Offset(1.dp.toPx(), center - 3.5.dp.toPx()),
            Size((x - 1.dp.toPx()).coerceAtLeast(0f), 7.dp.toPx()),
            CornerRadius(2.dp.toPx()),
        )
        drawCircle(accent, 7.5.dp.toPx(), Offset(x, center))
    }
}
