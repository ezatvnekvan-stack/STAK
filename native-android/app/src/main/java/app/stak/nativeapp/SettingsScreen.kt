package app.stak.nativeapp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: StakViewModel) {
    val context = LocalContext.current
    val credentials = vm.repository.credentials
    var jellyfinUrl by remember { mutableStateOf(credentials.get("jellyfin_url")) }
    var jellyfinToken by remember { mutableStateOf(credentials.get("jellyfin_token")) }
    var jellyfinUser by remember { mutableStateOf(credentials.get("jellyfin_user")) }
    var torboxToken by remember { mutableStateOf(credentials.get("torbox_token")) }
    var testing by remember { mutableStateOf(false) }
    val dark by vm.dark.collectAsStateWithLifecycle()
    val reduced by vm.reduced.collectAsStateWithLifecycle()
    val haptics by vm.haptics.collectAsStateWithLifecycle()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.operation {
            val text = BackupCodec.encode(vm.repository.dao.all(), vm.repository.dao.allEpisodes())
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) } ?: error("Could not create backup.") }
            vm.messages.emit("Backup exported without connection credentials.")
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.operation {
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val length = input.read(buffer)
                        if (length < 0) break
                        require(output.size() + length <= 2 * 1024 * 1024) { "Backup exceeds 2 MB." }
                        output.write(buffer, 0, length)
                    }
                    output.toString("UTF-8")
                } ?: error("Could not open backup.")
            }
            vm.importBackup(text)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeading("Settings", "Appearance & connections")
        Text("Appearance", style = MaterialTheme.typography.titleLarge)
        SettingToggle("Dark appearance", dark) { vm.appearance("dark", it) }
        SettingToggle("Reduce motion", reduced) { vm.appearance("reduced", it) }
        SettingToggle("Touch haptics", haptics) { vm.appearance("haptics", it) }
        Text("System animation settings are always respected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text("Jellyfin", style = MaterialTheme.typography.titleLarge)
        ConnectionField("Server address", jellyfinUrl, { jellyfinUrl = it }, uri = true)
        ConnectionField("Access token / API key", jellyfinToken, { jellyfinToken = it }, secret = true)
        ConnectionField("User ID", jellyfinUser, { jellyfinUser = it })
        Text("Use the ID of the Jellyfin user whose library and watched state you want to access. HTTP is supported for a server you configure; HTTPS protects credentials in transit.", style = MaterialTheme.typography.bodySmall)
        Text("TorBox", style = MaterialTheme.typography.titleLarge)
        ConnectionField("API key", torboxToken, { torboxToken = it }, secret = true)
        MotionButton("Save connections", Modifier.fillMaxWidth(), primary = true) {
            vm.operation {
                credentials.set("jellyfin_url", jellyfinUrl.trim().trimEnd('/'))
                credentials.set("jellyfin_token", jellyfinToken.trim())
                credentials.set("jellyfin_user", jellyfinUser.trim())
                credentials.set("torbox_token", torboxToken.trim())
                vm.messages.emit("Connections saved securely on this device.")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MotionButton("Test Jellyfin", Modifier.weight(1f), enabled = !testing) {
                vm.operation { testing = true; try { vm.repository.library(); vm.messages.emit("Jellyfin connected.") } finally { testing = false } }
            }
            MotionButton("Test TorBox", Modifier.weight(1f), enabled = !testing) {
                vm.operation { testing = true; try { vm.repository.torrents(); vm.messages.emit("TorBox connected.") } finally { testing = false } }
            }
        }
        Text("Tests use the saved connection settings.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Backups", style = MaterialTheme.typography.titleLarge)
        Text("Import your existing STAK JSON backup to merge watchlist, watched titles, and episode marks. This native alpha keeps its own data alongside your current app.", style = MaterialTheme.typography.bodyMedium)
        MotionButton("Import backup", Modifier.fillMaxWidth()) { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        MotionButton("Export backup", Modifier.fillMaxWidth()) { export.launch("STAK-native-backup.json") }
        HorizontalDivider()
        Text("STAK Native ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge)
        Text("Kotlin · Jetpack Compose · Room\nNative alpha. Cinemeta discovery, local tracking, Jellyfin library, and TorBox file access. Advanced provider filters, source aggregation, full remote controls, and background sync are still being ported.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MotionButton("Source & releases", Modifier.fillMaxWidth()) { openExternal(context, "https://github.com/ezatvnekvan-stack/STAK/releases") }
        Text("Fonts: Doto, Hanken Grotesk, Space Mono (SIL Open Font License). Metadata: Cinemeta. STAK is an independent app and is not affiliated with Nothing.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingToggle(label: String, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(enabled, change)
    }
}

@Composable
private fun ConnectionField(label: String, value: String, change: (String) -> Unit, secret: Boolean = false, uri: Boolean = false) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) },
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else if (uri) KeyboardType.Uri else KeyboardType.Text))
}
