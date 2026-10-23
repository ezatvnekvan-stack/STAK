package app.stak.nativeapp

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SourceFeature(val label: String, val accessibilityLabel: String) {
    UltraHd("4K", "4K"), FullHd("1080P", "1080p"), Hdr("HDR", "HDR"),
    WebDl("WEB-DL", "WEB-DL"), Remux("REMUX", "Remux"), Atmos("ATMOS", "Atmos")
}

/** Read provider facts once per release; the visible design exposes only the requested badges. */
fun sourceFeatures(tags: List<String>): Set<SourceFeature> = buildSet {
    if ("4K" in tags) add(SourceFeature.UltraHd)
    else if ("1080p" in tags) add(SourceFeature.FullHd)
    if (tags.any { it.startsWith("HDR") || it == "Dolby Vision" }) add(SourceFeature.Hdr)
    if ("WEB-DL" in tags) add(SourceFeature.WebDl)
    if ("REMUX" in tags) add(SourceFeature.Remux)
    if ("Atmos" in tags) add(SourceFeature.Atmos)
}

fun supportedSource(features: Set<SourceFeature>) =
    SourceFeature.UltraHd in features || SourceFeature.FullHd in features

fun matchesSourceFeatures(features: Set<SourceFeature>, resolution: SourceFeature?, selected: Set<SourceFeature>) =
    supportedSource(features) && (resolution == null || resolution in features) && features.containsAll(selected)

@Composable
fun SourceBadge(feature: SourceFeature, modifier: Modifier = Modifier, selected: Boolean = false, onToggle: (() -> Unit)? = null) {
    val interactive = onToggle != null
    val reduced = LocalStakMotion.current.reduced
    val interactions = remember { MutableInteractionSource() }
    val ink by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
        motionSpec(reduced, 140), label = "Badge ink")
    val fill by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface else Soft,
        motionSpec(reduced, 140), label = "Badge fill")
    Surface(
        modifier.then(if (interactive) Modifier.stakPressMotion(interactions).toggleable(value = selected, role = Role.Checkbox,
            interactionSource = interactions, indication = null,
            onValueChange = { onToggle?.invoke() }).semantics { contentDescription = "Filter ${feature.accessibilityLabel}" } else Modifier),
        shape = RoundedCornerShape(if (interactive) 12.dp else 6.dp),
        color = fill, contentColor = ink,
        border = BorderStroke(1.dp, if (selected) ink.copy(alpha = .15f) else Rule),
    ) {
        Row(Modifier.heightIn(min = if (interactive) 44.dp else 26.dp)
            .padding(horizontal = if (interactive) 13.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            if (feature == SourceFeature.Hdr || feature == SourceFeature.Atmos || feature == SourceFeature.Remux) {
                Icon(when (feature) {
                    SourceFeature.Hdr -> StakIcons.Sun
                    SourceFeature.Atmos -> StakIcons.Radio
                    else -> StakIcons.Film
                }, null, Modifier.size(if (interactive) 14.dp else 11.dp))
            }
            Text(feature.label, fontFamily = if (feature == SourceFeature.UltraHd || feature == SourceFeature.FullHd) DotFont else MonoFont,
                fontWeight = FontWeight.Bold, fontSize = if (interactive) 12.sp else 10.sp,
                lineHeight = 15.sp, letterSpacing = .3.sp)
        }
    }
}
