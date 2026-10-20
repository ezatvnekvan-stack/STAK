package app.stak.nativeapp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*

data class SettingsDestination(val id: String, val title: String, val hint: String, val icon: ImageVector)

@Composable
fun SettingsScreen(vm: StakViewModel, section: String = "") {
    val navigate = LocalNavigate.current
    val destinations = listOf(
        SettingsDestination("account", "TorBox account", if (vm.repository.credentials.get("torbox_token").isBlank()) "No key saved" else "Key saved", StakIcons.KeyRound),
        SettingsDestination("metadata", "Metadata & ratings", "TMDb · Cinemeta · OMDb", StakIcons.Database),
        SettingsDestination("sources", "Search sources", "Torrentio · Torznab · indexers", StakIcons.Radio),
        SettingsDestination("jellyfin", "Jellyfin", "Server · playback · sync", StakIcons.MonitorPlay),
        SettingsDestination("appearance", "Appearance", "Theme · accent · atmosphere · ratings", StakIcons.Palette),
        SettingsDestination("home", "Home & browsing", "Shelves · navigation · genres", StakIcons.LayoutGrid),
        SettingsDestination("advanced", "Data & app", "Backup · tools · about STAK", StakIcons.Shield))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeading(destinations.firstOrNull { it.id == section }?.title ?: "Settings")
        if (section.isBlank()) {
            ConnectionStatus(vm) { navigate("settings?section=$it") }
            destinations.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { destination ->
                        Surface(modifier = Modifier.weight(1f).heightIn(min = 124.dp).testTag("settings-${destination.id}"), onClick = { navigate("settings?section=${destination.id}") }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .74f), border = BorderStroke(1.dp, Rule)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(40.dp).background(Soft, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(destination.icon, null, Modifier.size(20.dp)) }
                                    Icon(StakIcons.ChevronRight, null, Modifier.size(17.dp), tint = Muted)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(destination.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(destination.hint, fontSize = 11.sp, lineHeight = 16.sp, color = Muted)
                                }
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(68.dp).clickable { navigate("settings") }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(StakIcons.ArrowLeft, "All settings", Modifier.size(18.dp)); Text("All settings", fontSize = 13.sp, color = Muted)
            }
            when (section) {
                "appearance" -> AppearanceSettings(vm)
                "account" -> TorBoxSettings(vm)
                "metadata" -> MetadataSettings(vm)
                "jellyfin" -> JellyfinSettings(vm)
                "sources" -> SourcesSettings(vm)
                "home" -> HomeSettings(vm)
                "advanced" -> BackupSettings(vm)
            }
        }
    }
}

@Composable
fun ConnectionStatus(vm: StakViewModel, choose: (String) -> Unit) {
    val c = vm.repository.credentials
    var states by remember { mutableStateOf(mapOf<String, String>()) }
    var checking by remember { mutableStateOf(false) }
    fun check() { vm.operation {
        checking = true
        try {
            for ((id, saved) in listOf("account" to c.get("torbox_token").isNotBlank(), "jellyfin" to c.get("jellyfin_url").isNotBlank())) {
                if (!saved) continue
                states = states + (id to "Checking…")
                val result = runCatching { if (id == "account") vm.repository.torbox("/user/me") else vm.repository.jellyfin("/System/Info") }
                states = states + (id to if (result.isSuccess) "Verified" else "Unavailable")
            }
        } finally { checking = false }
    } }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .74f), border = BorderStroke(1.dp, Rule)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Label("Connections", Modifier.weight(1f)); IconButton({ check() }, enabled = !checking) { Icon(StakIcons.RefreshCw, "Test connections", Modifier.size(15.dp)) } }
            listOf(Triple("TorBox", "account", c.get("torbox_token").isNotBlank()), Triple("Jellyfin", "jellyfin", c.get("jellyfin_url").isNotBlank()), Triple("Indexers", "sources", false)).forEach { (label, id, configured) ->
                HorizontalDivider(color = Rule)
                Row(Modifier.fillMaxWidth().height(48.dp).clickable { choose(id) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val status = states[id] ?: if (configured) "Configured" else "Not configured"
                    Box(Modifier.size(6.dp).background(if (status == "Verified") Color(0xff10b981) else Muted, CircleShape))
                    Text(label, Modifier.weight(1f), fontSize = 14.sp)
                    Text(status, fontFamily = MonoFont, fontSize = 10.sp, color = Muted)
                    Icon(StakIcons.ChevronRight, null, Modifier.size(15.dp), tint = Muted)
                }
            }
        }
    }
}

@Composable
fun AppearanceSettings(vm: StakViewModel) {
    val o = LocalOptions.current
    val dark by vm.dark.collectAsStateWithLifecycle()
    val reduced by vm.reduced.collectAsStateWithLifecycle()
    val haptics by vm.haptics.collectAsStateWithLifecycle()
    val accent = Accent
    val colorChoices = listOf("Nothing Red" to "#D71921", "Green" to "#10B981", "Blue" to "#3B82F6", "Pure White" to "#FFFFFF", "Graphite" to "#707070", "Orange" to "#F97316", "Purple" to "#A78BFA")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.fillMaxWidth().border(1.dp, Rule, RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(accent.copy(alpha = .09f), Color.Transparent)), RoundedCornerShape(14.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Label("Live preview"); StakIconButton(if (dark) StakIcons.Moon else StakIcons.Sun, "Toggle theme") { vm.option("theme", if (dark) "light" else "dark") } }
            Text("Make it yours.", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { Box(Modifier.size(34.dp, 48.dp).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.onSurface.copy(alpha = .12f), Color.Transparent)), RoundedCornerShape(6.dp)).border(1.dp, if (it == 0) accent else Rule, RoundedCornerShape(6.dp))) } }
            Text("Your accent · your atmosphere", fontFamily = MonoFont, fontSize = 10.sp, color = accent)
        }
        Label("Theme")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("dark", "light", "system").forEach { theme ->
            val selected = (o["theme"] ?: if (dark) "dark" else "light") == theme
            Box(Modifier.weight(1f).height(44.dp).border(1.dp, if (selected) accent else Rule, RoundedCornerShape(12.dp)).background(if (selected) accent.copy(alpha = .1f) else Color.Transparent, RoundedCornerShape(12.dp)).clickable { vm.option("theme", theme) }, contentAlignment = Alignment.Center) { Text(theme.uppercase(), fontFamily = MonoFont, fontSize = 10.sp, color = if (selected) accent else Muted) }
        } }
        Label("Accent · ${colorChoices.firstOrNull { it.second.equals(o["accent"] ?: "#D71921", true) }?.first ?: "Custom"}")
        colorChoices.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(26.dp)) { row.forEach { (name, hex) ->
            val selected = hex.equals(o["accent"] ?: "#D71921", true)
            Box(Modifier.size(48.dp).border(if (selected) 2.dp else 1.dp, if (selected) accent else Rule, CircleShape).clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { vm.option("accent", hex) }.padding(11.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().background(Color(android.graphics.Color.parseColor(hex)), CircleShape))
                if (selected) Icon(StakIcons.Check, name, Modifier.size(13.dp), tint = if (hex == "#FFFFFF") Color.Black else Color.White)
            }
        } } }
        var custom by remember { mutableStateOf(o["accent"] ?: "#D71921") }
        Row(verticalAlignment = Alignment.CenterVertically) { Label("Custom colour", Modifier.weight(1f)); OriginalField(custom, { custom = it; if (Regex("#[0-9a-fA-F]{6}").matches(it)) vm.option("accent", it) }, "#D71921", Modifier.width(124.dp)) }
        OriginalToggle("Reduce animations", reduced) { vm.appearance("reduced", it) }
        OriginalToggle("Compact layout", o["compact"] == "true") { vm.option("compact", it.toString()) }
        OriginalToggle("AMOLED black", o["amoled"] == "true") { vm.option("amoled", it.toString()) }
        Label("Atmosphere")
        listOf(Triple("dotIntensity", "Dot Matrix intensity", 10f), Triple("glowIntensity", "Ambient Glow intensity", 30f)).forEach { (key, label, max) ->
            val value = (o[key] ?: if (key == "dotIntensity") "5" else "16").toFloatOrNull() ?: 0f
            Column { Row { Text(label, Modifier.weight(1f), fontSize = 14.sp); Text("${value.toInt()}%", fontFamily = MonoFont, fontSize = 10.sp, color = Muted) }; Slider(value, { vm.option(key, it.toInt().toString()) }, valueRange = 0f..max, colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)) }
        }
        OriginalToggle("Ambient animation", o["ambientAnimation"] == "true") { vm.option("ambientAnimation", it.toString()) }
        OriginalToggle("Cinematic adaptive colours", o["cinematicColors"] != "false") { vm.option("cinematicColors", it.toString()) }
        MotionButton("Reset appearance") { vm.option("theme", "dark"); vm.option("accent", "#D71921"); vm.appearance("reduced", false); mapOf("dotIntensity" to "5", "glowIntensity" to "16", "compact" to "false", "amoled" to "false", "ambientAnimation" to "false", "cinematicColors" to "true").forEach { (k, v) -> vm.option(k, v) } }
        Label("Visible ratings")
        listOf("IMDb", "TMDB", "Rotten Tomatoes", "Metacritic").forEach { rating -> OriginalToggle(rating, o["rating_$rating"] != "false") { vm.option("rating_$rating", it.toString()) } }
        OriginalToggle("Haptics", haptics) { vm.appearance("haptics", it) }
    }
}

@Composable
fun TorBoxSettings(vm: StakViewModel) {
    val credentials = vm.repository.credentials
    var token by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(credentials.get("torbox_token").isNotBlank()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var account by remember { mutableStateOf<org.json.JSONObject?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Label("TorBox API key")
        OriginalField(token, { token = it }, "API key", secret = true)
        MotionButton(if (busy) "Verifying…" else "Save & verify", Modifier.fillMaxWidth(), primary = true, enabled = !busy && token.isNotBlank()) {
            vm.operation { busy = true; error = ""; try {
                val result = vm.repository.request("https://api.torbox.app/v1/api/user/me", token.trim())
                check(result.optBoolean("success")) { "TorBox could not verify this key." }
                credentials.set("torbox_token", token.trim()); token = ""; saved = true; account = result.optJSONObject("data")
            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message.orEmpty() } finally { busy = false } }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        if (saved) {
            Label("Active key · ••••••••")
            account?.let { a -> listOf("Email" to a.optString("email"), "Plan" to a.optString("plan"), "Expires" to a.optString("premium_expires_at"), "Downloaded" to "${a.optLong("total_downloaded") / 1_000_000_000} GB").forEach { (label, value) -> Text("$label · $value", fontSize = 13.sp) } }
            MotionButton("Sign out") { credentials.set("torbox_token", ""); saved = false; account = null }
        }
        val context = LocalContext.current
        MotionButton("Get your TorBox API key") { openExternal(context, "https://torbox.app/settings") }
    }
}

@Composable
fun MetadataSettings(vm: StakViewModel) {
    val o = LocalOptions.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("search" to "Search source", "movie" to "Movie metadata source", "series" to "Series metadata source").forEach { (key, label) ->
            Text(label, fontSize = 13.sp); Choice(label, o["metadata_$key"] ?: "tmdb", if (key == "search") listOf("tmdb" to "TMDb") else listOf("tmdb" to "TMDb", "cinemeta" to "Cinemeta"), { vm.option("metadata_$key", it); vm.catalogs.clear() })
        }
        OriginalToggle("Ratings: IMDb/RT/Metacritic via OMDb", o["ratings"] != "false") { vm.option("ratings", it.toString()) }
        OriginalToggle("Show IMDb rating on lists", o["imdbOnLists"] == "true") { vm.option("imdbOnLists", it.toString()) }
        Text("IMDb list ratings and IMDb filters use your daily OMDb quota. Cached ratings are reused for at least 24 hours.", fontSize = 12.sp, color = Muted)
        listOf("tmdb", "omdb").forEach { provider ->
            var key by remember { mutableStateOf("") }; var status by remember { mutableStateOf(if (vm.repository.credentials.get("${provider}_key").isBlank()) "missing" else "saved") }; var busy by remember { mutableStateOf(false) }
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .74f), border = BorderStroke(1.dp, Rule)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row { Label(provider, Modifier.weight(1f)); Label(status) }
                    OriginalField(key, { key = it }, if (provider == "tmdb") "API key or read access token" else "API key", secret = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MotionButton("Save & verify", Modifier.weight(1f), enabled = !busy && key.isNotBlank()) { vm.operation { busy = true; try {
                            if (provider == "tmdb") vm.repository.tmdb("/configuration", suppliedKey = key.trim())
                            else check(vm.repository.request("https://www.omdbapi.com/".toHttpUrlBuilder("apikey", key.trim(), "i", "tt0133093")).optString("Response") == "True") { "OMDb rejected the key." }
                            vm.repository.credentials.set("${provider}_key", key.trim()); key = ""; status = "verified"; vm.catalogs.clear()
                        } finally { busy = false } } }
                        MotionButton("Save", enabled = key.isNotBlank() && !busy) { vm.repository.credentials.set("${provider}_key", key.trim()); key = ""; status = "saved"; vm.catalogs.clear() }
                    }
                    MotionButton("Remove", enabled = status != "missing") { vm.repository.credentials.set("${provider}_key", ""); status = "missing"; vm.catalogs.clear() }
                }
            }
        }
        Text("This product uses the TMDB API but is not endorsed or certified by TMDB.", fontSize = 12.sp, color = Muted)
    }
}

@Composable
fun JellyfinSettings(vm: StakViewModel) {
    val c = vm.repository.credentials
    var connected by remember { mutableStateOf(c.get("jellyfin_url").isNotBlank()) }
    var editing by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }; var key by remember { mutableStateOf("") }; var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var mode by remember { mutableIntStateOf(0) }; var busy by remember { mutableStateOf(false) }; var tested by remember { mutableStateOf<JellyfinLogin?>(null) }; var error by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Label("Jellyfin")
        Text("Track what you watch on Jellyfin. Watched status, progress and history sync into STAK, and marking watched here updates Jellyfin too.", fontSize = 14.sp, color = Muted, lineHeight = 21.sp)
        if (connected && !editing) {
            listOf(listOf("Server" to c.get("jellyfin_url"), "User" to c.get("jellyfin_username")), listOf("Last sync" to (LocalOptions.current["last_sync"] ?: "—"), "Synced" to (LocalOptions.current["sync_stats"] ?: "—"))).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { (label, value) -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { Label(label); Text(value, fontFamily = MonoFont, fontSize = 11.sp) } } } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MotionButton("Sync now", Modifier.weight(1f), primary = true, enabled = !busy) { vm.operation { busy = true; try { val items = vm.repository.library(); items.filter { it.progress > 0 }.forEach { item -> vm.repository.dao.put(SavedMedia(item.key, "tracking", item.json().put("played", item.progress >= 100).toString())) }; vm.option("last_sync", java.time.LocalDate.now().toString()); vm.option("sync_stats", "${items.size} titles"); vm.messages.emit("Jellyfin connection refreshed") } finally { busy = false } } }
                MotionButton("Disconnect", enabled = !busy) { listOf("jellyfin_url", "jellyfin_token", "jellyfin_user", "jellyfin_username").forEach { c.set(it, "") }; connected = false }
            }
            MotionButton("Reconnect / replace session", Modifier.fillMaxWidth()) { editing = true; url = c.get("jellyfin_url"); tested = null }
            Text("A saved connection can expire or be revoked by Jellyfin. Reconnecting preserves your local lists and history.", fontSize = 12.sp, color = Muted)
        } else {
            if (editing) MotionButton("Cancel · keep saved connection") { editing = false; tested = null; password = ""; key = "" }
            OriginalField(url, { url = it; tested = null; password = ""; key = "" }, "http://192.168.1.50:8096")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MotionButton("User login", primary = mode == 0) { mode = 0; tested = null }; MotionButton("API key", primary = mode == 1) { mode = 1; tested = null } }
            if (mode == 0) { OriginalField(username, { username = it; tested = null }, "Username"); OriginalField(password, { password = it; tested = null }, "Password", secret = true) }
            else OriginalField(key, { key = it; tested = null }, "API key (Dashboard → API Keys)", secret = true)
            MotionButton(if (busy) "Connecting…" else "Connect", Modifier.fillMaxWidth(), primary = true, enabled = !busy && url.isNotBlank() && (if (mode == 0) username.isNotBlank() else key.isNotBlank())) { vm.operation { busy = true; error = ""; try { tested = vm.repository.testJellyfin(url, username, password, key, mode == 0); password = "" } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message.orEmpty() } finally { busy = false } } }
            Text("HTTP sends credentials and media without encryption. Use it only on a trusted local network; prefer HTTPS.", fontSize = 12.sp, color = Muted)
            if (error.isNotBlank()) Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            tested?.let { session -> Label("Pick your user"); session.users.forEach { user -> MotionButton(user.name, Modifier.fillMaxWidth()) { c.set("jellyfin_url", url.trim().trimEnd('/')); c.set("jellyfin_token", session.token); c.set("jellyfin_user", user.id); c.set("jellyfin_username", user.name); connected = true; editing = false; tested = null; password = ""; key = "" } } }
        }
    }
}

@Composable
fun SourcesSettings(vm: StakViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Label("Indexers")
        OriginalToggle("Torrentio", LocalOptions.current["torrentio"] != "false") { vm.option("torrentio", it.toString()) }
        Text("built-in · torrentio.strem.fun", fontFamily = MonoFont, fontSize = 10.sp, color = Muted)
    }
}

@Composable
fun HomeSettings(vm: StakViewModel) {
    val ids = listOf("continue" to "Continue watching", "upcoming" to "New releases", "watchlist" to "Your watchlist", "trending_movie" to "Trending movies", "trending_series" to "Trending series", "top_movie" to "Top rated movies", "new_movie" to "Released in ${java.time.Year.now()} · movies", "top_series" to "Top rated series", "new_series" to "Released in ${java.time.Year.now()} · series")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Label("Home sections")
        ids.forEachIndexed { index, (id, label) -> OriginalToggle(label, LocalOptions.current["home_$id"]?.toBooleanStrictOrNull() ?: (index < 7)) { vm.option("home_$id", it.toString()) } }
    }
}

@Composable
fun BackupSettings(vm: StakViewModel) {
    val context = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.operation { val text = BackupCodec.encode(vm.repository.dao.all(), vm.repository.dao.allEpisodes()); withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(text) } }; vm.messages.emit("Backup exported.") } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.operation {
        val text = withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> val bytes = input.readBytesWithLimit(2 * 1024 * 1024); String(bytes, Charsets.UTF_8) } }; vm.importBackup(text)
    } }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Label("Backup")
        Text("Export your watchlist and tracking data, or merge an existing STAK backup.", fontSize = 14.sp, color = Muted)
        MotionButton("Export backup", Modifier.fillMaxWidth()) { export.launch("STAK-backup.json") }
        MotionButton("Import backup", Modifier.fillMaxWidth()) { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        HorizontalDivider(color = Rule); Text("STAK", style = MaterialTheme.typography.headlineLarge); Text(BuildConfig.VERSION_NAME, fontFamily = MonoFont, fontSize = 11.sp, color = Muted)
        MotionButton("Source & releases", Modifier.fillMaxWidth()) { openExternal(context, "https://github.com/ezatvnekvan-stack/STAK") }
    }
}

fun java.io.InputStream.readBytesWithLimit(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val count = read(buffer); if (count < 0) break; require(output.size() + count <= limit) { "Backup exceeds 2 MB." }; output.write(buffer, 0, count) }
    return output.toByteArray()
}
fun String.toHttpUrlBuilder(vararg params: String): String {
    val builder = with(okhttp3.HttpUrl.Companion) { toHttpUrl() }.newBuilder()
    params.toList().chunked(2).forEach { builder.addQueryParameter(it[0], it[1]) }; return builder.build().toString()
}
