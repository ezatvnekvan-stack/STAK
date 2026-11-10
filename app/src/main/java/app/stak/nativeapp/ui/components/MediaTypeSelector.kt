package app.stak.nativeapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.ui.theme.*

/** One visual language for content-type filters; page navigation keeps its underline. */
@Composable fun MediaTypeSelector(
    options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val reduced = LocalReducedMotion.current
    Row(modifier.fillMaxWidth().selectableGroup().testTag("media-type-selector"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (id, label) ->
            val active = selected == id
            val press = rememberPressMotion()
            val edge by animateColorAsState(if (active) StakText.copy(alpha = .85f) else Color.Transparent,
                if (reduced) snap() else tween(160), label = "media-type-outline")
            val ink by animateColorAsState(if (active) StakText else StakTextMuted,
                if (reduced) snap() else tween(160), label = "media-type-ink")
            Box(Modifier.weight(1f).heightIn(min = 46.dp).pressMotion(press)
                .clip(RoundedCornerShape(12.dp)).border(1.dp, edge, RoundedCornerShape(12.dp))
                .selectable(active, role = Role.RadioButton, interactionSource = press.source,
                    indication = LocalIndication.current, onClick = { onSelect(id) })
                .testTag("media-type-$id").padding(horizontal = 8.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center) {
                Text(label.uppercase(), fontFamily = SpaceMonoFont, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, lineHeight = 18.sp, color = ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
