package app.stak.nativeapp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Label("Backup")
        Text(
            "Protect your watchlist, history, Home layout, preferences, hash lists and search sources with a password-encrypted backup.",
            fontSize = 14.sp,
            color = Muted,
        )
        Text(
            "Backups contain only the current account. Re-enter your TorBox key after reinstalling, then import. Existing records are kept when merging.",
            fontSize = 12.sp,
            color = Muted,
        )
        MotionButton(
            "Export backup",
            Modifier.fillMaxWidth().testTag("backup-export"),
            enabled = !busy,
        ) {
            clear()
            connections = false
            stage = "export"
        }
        MotionButton(
            "Import backup",
            Modifier.fillMaxWidth().testTag("backup-import"),
            enabled = !busy,
        ) {
            clear()
            import.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        if (stage.isEmpty() && error.isNotBlank())
            Text(error, color = MaterialTheme.colorScheme.error)
        HorizontalDivider(color = Rule)
        Text("STAK", style = MaterialTheme.typography.headlineLarge)
        Text(BuildConfig.VERSION_NAME, fontFamily = MonoFont, fontSize = 11.sp, color = Muted)
        MotionButton("Source & releases", Modifier.fillMaxWidth()) {
            openExternal(context, "https://github.com/ezatvnekvan-stack/STAK")
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
