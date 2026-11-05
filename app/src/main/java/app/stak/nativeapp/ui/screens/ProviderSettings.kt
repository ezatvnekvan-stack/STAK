@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

@Composable
fun ProviderSettings(id: String, controller: ConnectionSettingsController, onBack: () -> Unit, backLabel:String="All settings") {
    val s by controller.state.collectAsState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit(work: () -> Unit) { focus.clearFocus(); keyboard?.hide(); work() }
    LaunchedEffect(id) { controller.reload(); if (id == "account" && controller.state.value.account == null) controller.loadTorbox() }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 120.dp)) {
        StakPageHeader(when (id) { "account" -> "TorBox account"; "metadata" -> "Metadata & ratings"; else -> "Jellyfin" })
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onBack), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, backLabel, tint = StakText, modifier = Modifier.size(18.dp))
            Text(backLabel, fontFamily = HankenFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, color = StakText)
        }
        when (id) {
            "metadata" -> MetadataProviderSettings(controller, s) { submit(it) }
            "account" -> TorboxProviderSettings(controller, s) { submit(it) }
            "jellyfin" -> JellyfinProviderSettings(controller, s) { submit(it) }
        }
        if (s.busy.isNotBlank()) { Spacer(Modifier.height(12.dp)); SmallLabel("Saving…") }
        s.error?.let { Spacer(Modifier.height(12.dp)); Text(it, fontFamily = HankenFont, fontSize = 12.sp, lineHeight = 18.sp, color = Color(0xFFFF7A80)) }
        s.message?.let { Spacer(Modifier.height(12.dp)); Body(it, 12) }
    }
}

@Composable private fun Body(text: String, size: Int = 14) { Text(text, fontFamily = HankenFont, fontSize = size.sp, lineHeight = (size * 1.5).sp, color = StakTextMuted) }
@Composable private fun SmallLabel(text: String) { Text(text.uppercase(), fontFamily = SpaceMonoFont, fontSize = 10.sp, letterSpacing = 1.1.sp, color = StakTextMuted) }
@Composable private fun SectionHeading(text: String) { Text(text.uppercase(), fontFamily = DotoFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 21.75.sp, letterSpacing = .675.sp, color = StakText) }
@Composable private fun Panel(padding: Int = 12, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xBD111111), RoundedCornerShape(12.dp)).border(1.dp, Color(0x21FFFFFF), RoundedCornerShape(12.dp)).padding(padding.dp), content = content)
}
@Composable private fun ProviderLink(label: String, url: String) {
    val uri = LocalUriHandler.current
    Text(label.uppercase(), fontFamily = SpaceMonoFont, fontSize = 11.sp, letterSpacing = 1.1.sp, color = StakTextMuted, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { uri.openUri(url) }.padding(vertical = 14.dp))
}
@Composable private fun MetadataToggle(text: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontFamily = HankenFont, fontSize = 14.sp, color = StakText, modifier = Modifier.weight(1f))
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) { Checkbox(checked, onChange, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = StakAccent, uncheckedColor = StakTextMuted), modifier = Modifier.size(16.dp).semantics { contentDescription = text }) }
    }
}

@Composable
private fun MetadataProviderSettings(c: ConnectionSettingsController, s: ConnectionFormState, submit: (() -> Unit) -> Unit) {
    val enabled = s.busy.isBlank()
    Body("Connect TMDb for discovery. IMDb ratings use OMDb when configured, with Cinemeta as a fallback.",12)
    for (provider in listOf("tmdb", "omdb")) {
        Spacer(Modifier.height(16.dp))
        val saved = if (provider == "tmdb") s.tmdbSaved else s.omdbSaved
        val draft = if (provider == "tmdb") s.tmdbDraft else s.omdbDraft
        var visible by remember(provider) { mutableStateOf(false) }
        Panel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { SectionHeading(provider.uppercase()); Spacer(Modifier.weight(1f)); SmallLabel(if (saved) "set" else "missing") }
            Spacer(Modifier.height(10.dp))
            StakTextField(draft, { c.draft(provider, it) }, if (provider == "tmdb") "API key or read access token" else "API key", secret = !visible, enabled = enabled, height = 44, modifier = Modifier.semantics { contentDescription = "${provider.uppercase()} API key" })
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StakButton(if (visible) "Hide" else "Show", { visible = !visible }, variant = StakButtonVariant.Ghost, modifier = Modifier.semantics { contentDescription = "Toggle ${provider.uppercase()} key visibility" })
                StakButton("Save & verify", { submit { c.saveMetadata(provider, true) } }, enabled = enabled && draft.isNotBlank(), modifier = Modifier.semantics { contentDescription = "Save and verify ${provider.uppercase()}" })
                StakButton("Save", { submit { c.saveMetadata(provider, false) } }, enabled = enabled && draft.isNotBlank(), modifier = Modifier.semantics { contentDescription = "Save ${provider.uppercase()}" })
                StakButton("Test connection", { submit { c.testMetadata(provider) } }, enabled = enabled && saved, modifier = Modifier.semantics { contentDescription = "Test ${provider.uppercase()} connection" })
                StakButton("Remove", { submit { c.removeMetadata(provider) } }, enabled = enabled && saved, variant = StakButtonVariant.Ghost, modifier = Modifier.semantics { contentDescription = "Remove ${provider.uppercase()}" })
            }
            ProviderLink("Get a ${provider.uppercase()} key ↗", if (provider == "tmdb") "https://www.themoviedb.org/settings/api" else "https://www.omdbapi.com/apikey.aspx")
        }
    }
    Spacer(Modifier.height(20.dp)); TraktSettingsCard()
    Spacer(Modifier.height(24.dp)); SectionHeading("Metadata preferences")
    Spacer(Modifier.height(12.dp))
    DiscoverySelect("Movie metadata source", s.movie, listOf("tmdb" to "TMDb", "cinemeta" to "Cinemeta"), { c.metadataPreference("movie", it) }, fieldHeight = 44, labelSize = 11, enabled = enabled)
    Spacer(Modifier.height(16.dp))
    DiscoverySelect("Series metadata source", s.series, listOf("tmdb" to "TMDb", "cinemeta" to "Cinemeta"), { c.metadataPreference("series", it) }, fieldHeight = 44, labelSize = 11, enabled = enabled)
    Spacer(Modifier.height(16.dp))
    MetadataToggle("IMDb ratings", s.ratings, enabled) { c.metadataPreference("ratings", it.toString()) }
    Spacer(Modifier.height(16.dp))
    MetadataToggle("Show IMDb rating on lists", s.imdbOnLists, enabled) { c.metadataPreference("imdbOnLists", it.toString()) }
    Spacer(Modifier.height(16.dp))
    Body("IMDb list ratings and IMDb filters use your daily OMDb quota. Cached ratings are reused for at least 24 hours.", 12)
    Spacer(Modifier.height(16.dp)); Body("This product uses the TMDB API but is not endorsed or certified by TMDB.", 12)
}

@Composable
private fun TorboxProviderSettings(c: ConnectionSettingsController, s: ConnectionFormState, submit: (() -> Unit) -> Unit) {
    val enabled = s.busy.isBlank()
    if (s.torboxSaved) {
        val account = s.account
        for (row in listOf(listOf("E-mail" to (account?.email ?: "…"), "Plan" to (account?.plan ?: "…")), listOf("Expires" to (account?.expires?.take(10) ?: "…"), "Downloaded" to (account?.let { "%.2f GB".format(it.downloaded / 1_000_000_000.0) } ?: "…")))) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { (label, value) -> Column(Modifier.weight(1f)) { Panel(16) { SmallLabel(label); Spacer(Modifier.height(10.dp)); Text(value, fontFamily = SpaceMonoFont, fontSize = 14.sp, color = StakText) } } } }; Spacer(Modifier.height(10.dp))
        }
        Panel(16) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { SmallLabel("Active key"); Spacer(Modifier.height(4.dp)); Text(s.maskedTorbox, fontFamily = SpaceMonoFont, fontSize = 14.sp, color = StakText) }; StakButton("Sign out", { submit(c::signOutTorbox) }, variant = StakButtonVariant.Destructive, enabled = enabled) } }
        Spacer(Modifier.height(12.dp))
    }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp)) {
        SectionHeading(if (s.torboxSaved) "Replace key" else "Connect")
        Spacer(Modifier.height(10.dp)); Body("Stored on this device; encrypted with Android Keystore.")
        Spacer(Modifier.height(16.dp))
        var show by remember { mutableStateOf(false) }
        StakTextField(s.torboxDraft, { c.draft("torbox", it) }, "TorBox API key", secret = !show, enabled = enabled, height = 44, modifier = Modifier.semantics { contentDescription = "TorBox API key" }, trailing = { Box(Modifier.size(36.dp).clickable { show = !show }.semantics { contentDescription = "Toggle key visibility" }, contentAlignment = Alignment.Center) { Icon(if (show) Lucide.EyeOff else Lucide.Eye, null, tint = StakText, modifier = Modifier.size(16.dp)) } })
        Spacer(Modifier.height(12.dp))
        StakButton(if (enabled) "Save & verify" else "Verifying…", { submit(c::saveTorbox) }, enabled = enabled && s.torboxDraft.isNotBlank(), variant = StakButtonVariant.Primary, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp))
        Spacer(Modifier.height(16.dp)); ProviderLink("Get a TorBox key ↗", "https://torbox.app/settings")
    }
}

@Composable
private fun JellyfinProviderSettings(c: ConnectionSettingsController, s: ConnectionFormState, submit: (() -> Unit) -> Unit) {
    val enabled = s.busy.isBlank()
    Spacer(Modifier.height(10.dp)); SectionHeading("Jellyfin")
    Spacer(Modifier.height(10.dp)); Body("Track what you watch on Jellyfin. Watched status, progress and history sync into STAK, and marking watched here updates Jellyfin too.")
    Spacer(Modifier.height(16.dp))
    if (s.jellyfinConnected && !s.editing) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Column(Modifier.weight(1f)) { SmallLabel("Server"); Body(s.savedServer, 11) }; Column(Modifier.weight(1f)) { SmallLabel("User"); Body(s.savedUser, 11) } }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Column(Modifier.weight(1f)) { SmallLabel("Last sync"); Body("—", 11) }; Column(Modifier.weight(1f)) { SmallLabel("Synced"); Body("—", 11) } }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { StakButton("Sync now", { submit(c::refreshJellyfin) }, enabled = enabled, variant = StakButtonVariant.Primary, modifier = Modifier.weight(1f)); StakButton("Disconnect", { submit(c::disconnectJellyfin) }, enabled = enabled, variant = StakButtonVariant.Ghost) }
        Spacer(Modifier.height(12.dp)); StakButton("Reconnect / replace session", c::reconnectJellyfin, enabled = enabled, variant = StakButtonVariant.Ghost, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp)); Body("A saved connection can expire or be revoked by Jellyfin. Reconnecting preserves your local lists and history.", 12)
    } else {
        if (s.editing) { StakChip("Cancel · keep saved connection", false, c::cancelReconnect); Spacer(Modifier.height(10.dp)) }
        StakTextField(s.server, { c.jellyfinField("url", it) }, "http://192.168.1.50:8096", enabled = enabled, height = 44, modifier = Modifier.semantics { contentDescription = "Jellyfin server URL" }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { LoginModeChip("User login", s.mode == "password", enabled) { c.jellyfinField("mode", "password") }; LoginModeChip("API key", s.mode == "key", enabled) { c.jellyfinField("mode", "key") } }
        Spacer(Modifier.height(10.dp))
        if (s.mode == "password") {
            StakTextField(s.username, { c.jellyfinField("username", it) }, "Username", enabled = enabled, height = 44, modifier = Modifier.semantics { contentDescription = "Jellyfin username" })
            Spacer(Modifier.height(10.dp)); StakTextField(s.password, { c.jellyfinField("password", it) }, "Password", secret = true, enabled = enabled, height = 44, modifier = Modifier.semantics { contentDescription = "Jellyfin password" })
        } else StakTextField(s.jellyfinKey, { c.jellyfinField("key", it) }, "API key (Dashboard → API Keys)", secret = true, enabled = enabled, height = 44, modifier = Modifier.semantics { contentDescription = "Jellyfin API key" })
        Spacer(Modifier.height(10.dp))
        StakButton(if (s.busy == "jellyfin-test") "Connecting…" else "Connect", { submit(c::connectJellyfin) }, enabled = enabled && s.server.isNotBlank() && (if (s.mode == "password") s.username.isNotBlank() else s.jellyfinKey.isNotBlank()), variant = StakButtonVariant.Primary, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp))
        Spacer(Modifier.height(10.dp)); Body("HTTP sends credentials and media without encryption. Use it only on a trusted local network; prefer HTTPS.", 12)
        s.probe?.let { probe ->
            Spacer(Modifier.height(14.dp)); SmallLabel("Pick your user")
            probe.users.forEach { user -> StakButton(user.name, { submit { c.pickJellyfinUser(user) } }, enabled = enabled, variant = StakButtonVariant.Ghost, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Choose Jellyfin user ${user.name}" }) }
            if (probe.users.isEmpty()) Body("No users returned. Check the API key permissions.", 12)
        }
    }
}

@Composable private fun LoginModeChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).background(if (selected) StakAccent.copy(alpha = .12f) else Color.Transparent, RoundedCornerShape(12.dp)).border(1.dp, if (selected) StakAccent else Color(0x29FFFFFF), RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(label.uppercase(), fontFamily = SpaceMonoFont, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, lineHeight = 14.sp, color = if (selected) StakAccent else StakText)
    }
}
