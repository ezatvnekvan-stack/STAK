package app.stak.nativeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import app.stak.nativeapp.ui.theme.*

/** Compact film-classification stamp. Keeps the provider's actual rating, never invents one. */
@Composable fun CertificationBadge(value: String?, modifier: Modifier = Modifier) {
    val label = value?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT) ?: return
    Box(modifier.testTag("certification-badge").semantics(mergeDescendants = true) {
        contentDescription = "Age rating $label"
    }.border(1.dp, Color(0xCCE7E7E2), RoundedCornerShape(2.dp))
        .padding(horizontal = 5.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
        Text(label, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black,
            fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = .2.sp, color = Color(0xFFE7E7E2))
    }
}

/** An editable filter still has a 44dp touch target, with the same stamp inside. */
@Composable fun CertificationFilterChip(value: String, onClear: () -> Unit) {
    Row(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClear)
        .semantics(mergeDescendants = true) { contentDescription = "Clear age rating $value" }
        .padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CertificationBadge(value)
        Icon(Lucide.X, null, tint = StakTextMuted, modifier = Modifier.size(14.dp))
    }
}
