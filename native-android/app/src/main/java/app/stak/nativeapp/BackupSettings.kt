package app.stak.nativeapp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

@Composable
fun BackupSettings(vm: StakViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var connections by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var encrypted by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<BackupArchive?>(null) }
    var targetAccount by remember { mutableStateOf("") }
    fun task(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = ""
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Could not complete backup."
            } finally {
                busy = false
            }
        }
    }
    fun clear() {
        stage = ""
        password = ""
        confirm = ""
        encrypted = ""
        preview = null
        error = ""
    }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            val text = output
            output = ""
            if (uri != null && text.isNotEmpty())
                task {
                    withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                            .bufferedWriter(Charsets.UTF_8)
                            .use { it.write(text) }
                    }
                    vm.messages.emit("Encrypted backup exported. Keep its password somewhere safe.")
                }
        }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                stage = "reading"
                targetAccount = vm.repository.accountScope
                task {
                    val text =
                        withContext(Dispatchers.IO) {
                            requireNotNull(context.contentResolver.openInputStream(uri)).use {
                                String(
                                    it.readBytesWithLimit(EncryptedBackup.MAX_FILE_BYTES),
                                    Charsets.UTF_8,
                                )
                            }
                        }
                    if (EncryptedBackup.isEncrypted(text)) {
                        encrypted = text
                        stage = "unlock"
                    } else {
                        preview = withContext(Dispatchers.Default) { parseBackupArchive(text) }
                        stage = "preview"
                    }
                }
            }
        }
    var platformOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MediaToolkitSettings(vm)
        CapacitorSettingsPanel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("Local backup")
                Text("Export this account’s watchlist, manual tracking, home layout and saved magnet lists. Credentials and indexer URLs are excluded unless encrypted export is selected. Backups contain personal viewing data; share them carefully.",
                    fontSize = 12.sp, lineHeight = 16.sp, color = Muted)
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).toggleable(connections, enabled = !busy, role = Role.Checkbox, onValueChange = { connections = it }),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactCheckbox(connections)
                    Text("Include credentials (encrypted)", fontSize = 14.sp, lineHeight = 20.sp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Passphrase (encrypted export/import)", fontSize = 12.sp, lineHeight = 16.sp)
                    OriginalField(password, { password = it }, "", secret = true, enabled = !busy, accessibilityLabel = "Backup passphrase")
                }
                MotionButton("Prepare backup", Modifier.testTag("backup-export"), tone = ButtonTone.Chip, enabled = !busy) {
                    val passphrase = password
                    val include = connections
                    clear()
                    password = passphrase
                    connections = include
                    stage = "export"
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Import backup (merge missing records)", fontSize = 12.sp, lineHeight = 16.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(onClick = { clear(); import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                            enabled = !busy, modifier = Modifier.testTag("backup-import"), shape = RoundedCornerShape(12.dp), color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Rule)) {
                            Box(Modifier.width(104.dp).height(44.dp), contentAlignment = Alignment.Center) { Text("Choose File", fontSize = 12.sp, lineHeight = 16.sp) }
                        }
                        Text("No file chosen", fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
                if (stage.isEmpty() && error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        CapacitorSettingsPanel {
            SectionHeading("STAK 1.0.0")
            Spacer(Modifier.height(10.dp))
            Label("Android")
            Spacer(Modifier.height(12.dp))
            Text("${if (platformOpen) "▾" else "▸"} Platform information", Modifier.clickable { platformOpen = !platformOpen }, fontSize = 12.sp, lineHeight = 16.sp, color = Muted)
            if (platformOpen) {
                Text("Standalone Android app. Providers connect directly from this device; no companion web server is required.", Modifier.padding(top = 12.dp), fontSize = 14.sp, lineHeight = 20.sp, color = Muted)
                Text("Native build ${BuildConfig.VERSION_NAME}", Modifier.padding(top = 8.dp), fontSize = 12.sp, lineHeight = 16.sp, color = Muted)
                Text("Local watchlists and settings stay on this device. Public share links and server webhooks require separate hosting.", Modifier.padding(top = 8.dp), fontSize = 12.sp, lineHeight = 16.sp, color = Muted)
                MotionButton("Source & releases", Modifier.padding(top = 12.dp)) { openExternal(context, "https://github.com/ezatvnekvan-stack/STAK") }
            }
        }
    }
    if (stage.isNotEmpty())
        AlertDialog(
            onDismissRequest = { if (!busy) clear() },
            title = {
                Text(
                    when (stage) {
                        "export" -> "Protect your backup"
                        "unlock" -> "Unlock backup"
                        "preview" -> "Review import"
                        else -> "Reading backup"
                    }
                )
            },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (stage) {
                        "export",
                        "unlock" -> {
                            Text(
                                if (stage == "export")
                                    "Use at least 12 characters. There is no password recovery. Sources may include private API keys."
                                else "Enter the password used when this backup was exported."
                            )
                            OriginalField(
                                password,
                                { password = it },
                                "Backup password",
                                secret = true,
                                modifier = Modifier.testTag("backup-password"),
                            )
                            if (stage == "export") {
                                OriginalField(
                                    confirm,
                                    { confirm = it },
                                    "Confirm password",
                                    secret = true,
                                    modifier = Modifier.testTag("backup-password-confirm"),
                                )
                                OriginalToggle("Include saved connections", connections) {
                                    connections = it
                                }
                                Text(
                                    "Optional: TMDb/OMDb keys and Jellyfin session. Your TorBox key is never exported.",
                                    fontSize = 12.sp,
                                    color = Muted,
                                )
                            }
                        }
                        "preview" -> {
                            Text(preview?.summary.orEmpty())
                            Text(
                                "Merge into the currently selected TorBox account, or local profile if no key is saved. Existing titles, watched dates, sources, lists, settings and connection fields take priority. This does not update Jellyfin.",
                                fontSize = 12.sp,
                                color = Muted,
                            )
                        }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                when (stage) {
                    "export" ->
                        MotionButton(
                            if (busy) "Encrypting…" else "Choose file",
                            enabled = !busy && password.length >= 12 && password == confirm,
                        ) {
                            task {
                                val secret = password.toCharArray()
                                try {
                                    val payload = vm.repository.createBackupArchive(connections)
                                    output =
                                        withContext(Dispatchers.Default) {
                                            EncryptedBackup.encrypt(payload, secret)
                                        }
                                    clear()
                                    export.launch("STAK-backup-encrypted.json")
                                } finally {
                                    secret.fill('\u0000')
                                }
                            }
                        }
                    "unlock" ->
                        MotionButton(
                            if (busy) "Unlocking…" else "Unlock",
                            enabled = !busy && password.isNotEmpty(),
                        ) {
                            task {
                                val secret = password.toCharArray()
                                try {
                                    val text = encrypted
                                    preview =
                                        withContext(Dispatchers.Default) {
                                            parseBackupArchive(
                                                EncryptedBackup.decrypt(text, secret)
                                            )
                                        }
                                    password = ""
                                    encrypted = ""
                                    stage = "preview"
                                } finally {
                                    secret.fill('\u0000')
                                }
                            }
                        }
                    "preview" ->
                        MotionButton(
                            if (busy) "Importing…" else "Merge backup",
                            enabled = !busy && preview != null,
                        ) {
                            task {
                                vm.restoreBackup(requireNotNull(preview), targetAccount)
                                clear()
                            }
                        }
                }
            },
            dismissButton = { MotionButton("Cancel", enabled = !busy) { clear() } },
        )
}
