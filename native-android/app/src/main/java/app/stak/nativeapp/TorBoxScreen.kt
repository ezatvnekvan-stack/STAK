@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.stak.nativeapp

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*

@Composable
fun TorBoxScreen(vm: StakViewModel, openJellyfin: () -> Unit) {
    var torrents by remember { mutableStateOf(emptyList<Torrent>()) }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableIntStateOf(0) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selection by remember { mutableStateOf(emptySet<Int>()) }
    var files by remember { mutableStateOf<Torrent?>(null) }
    var add by remember { mutableStateOf(false) }
    var saveList by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("newest") }
    val navigate = LocalNavigate.current
    val connected = vm.repository.credentials.get("torbox_token").isNotBlank()
    LaunchedEffect(refresh) {
        loading = true
        try {
            if (connected) torrents = vm.repository.torrents()
            error = ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message.orEmpty()
        } finally {
            loading = false
        }
    }
    fun completed(t: Torrent) = t.progress >= 1f || t.state == "Completed"
    fun failed(t: Torrent) = t.state.contains("error", true) || t.state.contains("fail", true)
    val visible =
        torrents
            .filter {
                (it.name.contains(query, true) || it.hash.contains(query, true)) &&
                    when (status) {
                        1 -> !completed(it) && !failed(it)
                        2 -> completed(it)
                        3 -> failed(it)
                        else -> true
                    }
            }
            .let {
                if (sort == "name") it.sortedBy { torrent -> torrent.name.lowercase() }
                else if (sort == "size") it.sortedByDescending { torrent -> torrent.size }
                else it.sortedByDescending { torrent -> torrent.id }
            }
    LazyColumn(
        Modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        item {
            PageHeading("Library") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StakIconButton(StakIcons.RefreshCw, "Refresh library", enabled = !loading) {
                        refresh++
                    }
                    Surface(onClick = { add = true }, shape = CircleShape, color = Accent) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                StakIcons.Plus,
                                "Add magnet",
                                Modifier.size(18.dp),
                                tint = AccentForeground,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            MotionTabs(listOf("Jellyfin", "TorBox"), 1) { if (it == 0) openJellyfin() }
            Spacer(Modifier.height(16.dp))
        }
        if (!connected)
            item {
                OriginalEmpty("No key", "This feature needs a TorBox API key.", "Add key") {
                    navigate("settings?section=account")
                }
            }
        else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PremiumMetric(torrents.size.toString(), "Torrents", Modifier.weight(1f))
                    PremiumMetric(formatBytes(torrents.sumOf { it.size }), "Cloud library", Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OriginalField(
                        query,
                        { query = it },
                        "Search by name or hash",
                        Modifier.weight(1f).height(50.dp),
                        StakIcons.Search,
                        horizontalInset = 16.dp,
                    )
                    StakIconButton(StakIcons.SlidersHorizontal, "Filter library") { filters = true }
                }
                Spacer(Modifier.height(16.dp))
            }
            item {
                Row(Modifier.fillMaxWidth()) {
                    listOf(
                            "All" to torrents.size,
                            "Active" to torrents.count { !completed(it) && !failed(it) },
                            "Completed" to torrents.count { completed(it) },
                            "Error" to torrents.count { failed(it) },
                        )
                        .forEachIndexed { index, (label, count) ->
                            Box(
                                Modifier.weight(1f)
                                    .height(44.dp)
                                    .background(
                                        if (status == index) Soft else Color.Transparent,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .border(
                                        1.dp,
                                        if (status == index) Rule else Color.Transparent,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable { status = index },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${label.uppercase()} $count",
                                    fontFamily = MonoFont,
                                    fontSize = 9.sp,
                                    lineHeight = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color =
                                        if (index == status) MaterialTheme.colorScheme.onSurface
                                        else Muted,
                                )
                            }
                        }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().height(74.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${visible.size} of ${torrents.size} torrents · ${if (sort == "newest") "Newest" else if (sort == "size") "Largest" else "Name"}",
                        Modifier.weight(1f),
                        fontFamily = MonoFont,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        color = Muted,
                    )
                    Text(
                        if (selecting) "DONE" else "SELECT",
                        Modifier.clickable {
                                selecting = !selecting
                                if (!selecting) selection = emptySet()
                            }
                            .padding(12.dp),
                        fontFamily = MonoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
                HorizontalDivider(color = Rule)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Rule)
            }
            if (selecting)
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MotionButton("Select all") { selection = visible.map { it.id }.toSet() }
                        MotionButton(
                            "Save list",
                            enabled = selection.isNotEmpty(),
                            icon = StakIcons.Share2,
                        ) {
                            saveList = true
                        }
                        MotionButton("My lists") { navigate("shares") }
                    }
                }
            if (loading) item { DotStatus(Modifier.padding(vertical = 16.dp), "Loading library") }
            if (error.isNotBlank()) item { EmptyState(error) { refresh++ } }
            if (!loading && torrents.isEmpty() && error.isBlank())
                item {
                    OriginalEmpty(
                        "Your library is empty",
                        "Add a magnet link to get started.",
                        "Add magnet",
                    ) {
                        add = true
                    }
                }
            items(visible, key = { it.id }) { torrent ->
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(min = 100.dp)
                        .clickable {
                            if (selecting)
                                selection =
                                    if (torrent.id in selection) selection - torrent.id
                                    else selection + torrent.id
                            else files = torrent
                        }
                        .padding(vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(42.dp, 50.dp).border(1.dp, Rule, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (selecting && torrent.id in selection) StakIcons.SquareCheck
                            else StakIcons.FileVideo,
                            null,
                            Modifier.size(20.dp),
                            tint = Muted,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            torrent.name,
                            fontSize = 14.sp,
                            lineHeight = 19.6.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                Modifier.size(5.dp)
                                    .background(
                                        if (failed(torrent)) MaterialTheme.colorScheme.error
                                        else if (completed(torrent)) Color(0xff10b981) else Muted,
                                        CircleShape,
                                    )
                            )
                            Text(
                                "${torrent.state.ifBlank { "Downloading" }} ${formatBytes(torrent.size)} ${torrent.files.size} ${if (torrent.files.size == 1) "file" else "files"}",
                                fontFamily = MonoFont,
                                fontSize = 10.sp,
                                lineHeight = 15.sp,
                                color = Muted,
                            )
                        }
                        if (!completed(torrent)) SegmentedProgress(torrent.progress)
                    }
                    Icon(StakIcons.ChevronRight, null, Modifier.size(15.dp), tint = Muted)
                }
                HorizontalDivider(color = Rule)
            }
        }
    }
    if (filters)
        StakBottomSheet({ filters = false }) {
            Column(
                Modifier.padding(20.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Library filters", style = MaterialTheme.typography.headlineSmall)
                Choice(
                    "Sort by",
                    sort,
                    listOf("newest" to "Newest", "name" to "Name", "size" to "Largest"),
                    { sort = it },
                )
                MotionButton("Reset filters") {
                    sort = "newest"
                    status = 0
                }
                MotionButton("Done", Modifier.fillMaxWidth()) { filters = false }
            }
        }
    if (add)
        AddMagnetSheet(vm, { add = false }) {
            add = false
            refresh++
        }
    files?.let { torrent ->
        TorrentFilesSheet(vm, torrent) {
            files = null
            refresh++
        }
    }
    if (saveList)
        SaveHashListDialog(vm, torrents.filter { it.id in selection }) { saveList = false }
}

fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_099_511_627_776L ->
            "%.2f TB".format(java.util.Locale.US, bytes / 1_099_511_627_776.0)
        bytes >= 1_073_741_824L -> "%.2f GB".format(java.util.Locale.US, bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "${kotlin.math.round(bytes / 1_048_576.0).toLong()} MB"
        bytes >= 1024 -> "${kotlin.math.round(bytes / 1024.0).toLong()} KB"
        else -> "$bytes B"
    }

@Composable
fun AddMagnetSheet(vm: StakViewModel, dismiss: () -> Unit, added: () -> Unit) {
    var magnet by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    StakBottomSheet(dismiss) {
        Column(
            Modifier.padding(20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Add magnet", style = MaterialTheme.typography.headlineSmall)
            OriginalField(magnet, { magnet = it }, "Magnet link", singleLine = false)
            MotionButton(
                if (busy) "Adding…" else "Add to TorBox",
                Modifier.fillMaxWidth(),
                primary = true,
                enabled = !busy && magnet.isNotBlank(),
            ) {
                vm.operation {
                    busy = true
                    try {
                        vm.repository.addMagnet(magnet.trim())
                        added()
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}

@Composable
fun TorrentFilesSheet(vm: StakViewModel, torrent: Torrent, dismiss: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    StakBottomSheet(dismiss) {
        LazyColumn(
            Modifier.fillMaxWidth().fillMaxHeight(.7f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Row {
                    Text(
                        torrent.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(dismiss) { Icon(StakIcons.X, "Close dialog", Modifier.size(18.dp)) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MotionButton("Pause", enabled = !busy) {
                        vm.operation {
                            busy = true
                            try {
                                vm.repository.controlTorrent(torrent.id, "pause")
                            } finally {
                                busy = false
                            }
                        }
                    }
                    MotionButton("Resume", enabled = !busy) {
                        vm.operation {
                            busy = true
                            try {
                                vm.repository.controlTorrent(torrent.id, "resume")
                            } finally {
                                busy = false
                            }
                        }
                    }
                    MotionButton("Delete", enabled = !busy) { confirmDelete = true }
                }
            }
            items(torrent.files, key = { it.id }) { file ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(file.name, fontSize = 13.sp, lineHeight = 20.sp)
                    Text(
                        formatBytes(file.size),
                        fontFamily = MonoFont,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        color = Muted,
                    )
                    MotionButton(if (busy) "Requesting link…" else "Open file", enabled = !busy) {
                        vm.operation {
                            busy = true
                            try {
                                openExternal(
                                    context,
                                    vm.repository.torboxLink(torrent.id, file.id),
                                    video =
                                        file.name.substringAfterLast('.').lowercase() in
                                            listOf("mp4", "mkv", "webm", "avi", "mov"),
                                )
                            } finally {
                                busy = false
                            }
                        }
                    }
                    HorizontalDivider(color = Rule)
                }
            }
        }
    }
    if (confirmDelete)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete torrent?") },
            text = { Text(torrent.name) },
            confirmButton = {
                TextButton({
                    vm.operation {
                        busy = true
                        try {
                            vm.repository.controlTorrent(torrent.id, "delete")
                            dismiss()
                        } finally {
                            busy = false
                            confirmDelete = false
                        }
                    }
                }) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        )
}
