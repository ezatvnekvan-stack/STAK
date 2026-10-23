package app.stak.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException

@Composable
fun MediaToolkitSettings(vm: StakViewModel) {
    var busy by remember { mutableStateOf(false) }
    var refreshed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var titles by remember { mutableIntStateOf(0) }
    var torrents by remember { mutableStateOf(emptyList<Torrent>()) }
    var torboxLoaded by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf("") }
    val navigate = LocalNavigate.current
    CapacitorSettingsPanel(Modifier.testTag("media-toolkit")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Label("Media toolkit")
                Spacer(Modifier.height(10.dp))
                SectionHeading("Server & Library")
                Spacer(Modifier.height(10.dp))
            }
            MotionButton(if (busy) "Loading…" else "Refresh", enabled = !busy) {
                vm.operation {
                    busy = true
                    error = ""
                    val failures = mutableListOf<String>()
                    try {
                        titles = vm.repository.dao.all().map { it.mediaKey }.distinct().size
                        if (vm.repository.credentials.get("torbox_token").isNotBlank()) {
                            try { torrents = vm.repository.torrents(); torboxLoaded = true }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { failures += "TorBox: ${failure.message ?: "Could not load library"}" }
                        }
                        if (vm.repository.credentials.get("jellyfin_url").isNotBlank()) {
                            try {
                                val info = vm.repository.jellyfin("/System/Info")
                                server = listOf(info.optString("ServerName"), info.optString("Version")).filter { it.isNotBlank() }.joinToString(" · ")
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { failures += "Jellyfin: ${failure.message ?: "Could not reach server"}" }
                        }
                        refreshed = true
                        error = failures.joinToString("\n")
                    } finally { busy = false }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Device and provider diagnostics. Playback controls require a compatible Jellyfin client.", fontSize = 14.sp, lineHeight = 20.sp, color = Muted)
        if (error.isNotBlank()) Text(error, Modifier.padding(top = 12.dp), fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.error)
        if (refreshed) {
            Spacer(Modifier.height(16.dp))
            Text("Local titles · $titles", fontSize = 14.sp, lineHeight = 20.sp)
            if (torboxLoaded) {
                Text("TorBox torrents · ${torrents.size}", fontSize = 14.sp, lineHeight = 20.sp)
                Text("TorBox total size · ${formatBytes(torrents.sumOf { it.size })}", fontSize = 14.sp, lineHeight = 20.sp)
                val duplicates = torrents.filter { it.hash.isNotBlank() }.groupBy { it.hash.lowercase() }.count { it.value.size > 1 }
                Text("Duplicate TorBox hashes · $duplicates groups", fontSize = 14.sp, lineHeight = 20.sp)
            }
            if (server.isNotBlank()) Text(server, fontSize = 14.sp, lineHeight = 20.sp)
            if (vm.repository.credentials.get("jellyfin_url").isNotBlank()) MotionButton("Jellyfin remote", Modifier.padding(top = 12.dp)) { navigate("remote") }
        }
    }
}
