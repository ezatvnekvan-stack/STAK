@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.stak.nativeapp

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.time.*
import kotlinx.coroutines.*

@Composable
fun LibraryScreen(vm: StakViewModel, onOpen: (Media, String) -> Unit, initialTab: Int = 0) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    if (tab == 1) {
        TorBoxScreen(vm) { tab = 0 }
        return
    }
    var refresh by remember { mutableIntStateOf(0) }
    var media by remember { mutableStateOf(emptyList<Media>()) }
    var torrents by remember { mutableStateOf(emptyList<Torrent>()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Torrent?>(null) }
    var add by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableIntStateOf(0) }
    var layout by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val navigate = LocalNavigate.current
    val connected =
        vm.repository.credentials.get(if (tab == 0) "jellyfin_url" else "torbox_token").isNotBlank()
    LaunchedEffect(tab, refresh) {
        if (!connected) return@LaunchedEffect
        loading = true
        error = ""
        try {
            if (tab == 0) media = vm.repository.library() else torrents = vm.repository.torrents()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message.orEmpty()
        } finally {
            loading = false
        }
    }
    LazyVerticalGrid(
        GridCells.Fixed(if (tab == 0 && layout == 0) 3 else 1),
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { PageHeading("Library") }
        item(span = { GridItemSpan(maxLineSpan) }) {
            MotionTabs(listOf("Jellyfin", "TorBox"), tab) {
                tab = it
                query = ""
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.width(2.dp).height(56.dp).background(Accent))
                Icon(
                    if (tab == 0) StakIcons.Jellyfin else StakIcons.Database,
                    null,
                    Modifier.size(23.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            if (tab == 0) "Jellyfin" else "TorBox",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (connected) "• Connected" else "• Not connected",
                            fontSize = 10.sp,
                            color = Muted,
                        )
                    }
                    Text(
                        if (tab == 0) "Your personal collection" else "Your cloud library",
                        fontFamily = MonoFont,
                        fontSize = 10.sp,
                        color = Muted,
                    )
                }
                StakIconButton(StakIcons.RefreshCw, "Refresh library", enabled = !loading) {
                    refresh++
                }
                StakIconButton(StakIcons.Settings2, "Library settings") {
                    navigate("settings?section=${if (tab == 0) "jellyfin" else "account"}")
                }
            }
        }
        if (!connected)
            item(span = { GridItemSpan(maxLineSpan) }) {
                LiquidSurface(
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Rule),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .74f),
                ) {
                    Column(
                        Modifier.padding(17.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            if (tab == 0) "Your library awaits" else "No key",
                            fontFamily = DotFont,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            lineHeight = 28.sp,
                            letterSpacing = .4.sp,
                        )
                        Text(
                            if (tab == 0)
                                "Connect your Jellyfin server to browse your personal collection."
                            else "This feature needs a TorBox API key.",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = Muted,
                        )
                        MotionButton(if (tab == 0) "Connect Jellyfin" else "Add key") {
                            navigate("settings?section=${if (tab == 0) "jellyfin" else "account"}")
                        }
                    }
                }
            }
        if (connected) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OriginalField(
                        query,
                        { query = it },
                        if (tab == 0) "Search library" else "Search torrents",
                        Modifier.weight(1f),
                        StakIcons.Search,
                    )
                    if (tab == 1) StakIconButton(StakIcons.Plus, "Add magnet") { add = true }
                }
            }
            if (tab == 0)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        listOf("All", "Movies", "Series").forEachIndexed { index, label ->
                            Text(
                                label.uppercase(),
                                Modifier.background(
                                        if (type == index) Soft else Color.Transparent,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable { type = index }
                                    .padding(13.dp),
                                fontFamily = MonoFont,
                                fontSize = 10.sp,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        GlassIconButton({ layout = 0 }) {
                            Icon(StakIcons.LayoutGrid, "Grid view", Modifier.size(18.dp))
                        }
                        GlassIconButton({ layout = 1 }) {
                            Icon(StakIcons.List, "List view", Modifier.size(18.dp))
                        }
                    }
                }
            if (error.isNotBlank())
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(error) { refresh++ } }
            if (loading)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DotStatus(label = "Refreshing library")
                }
            if (tab == 0) {
                val items =
                    media.filter {
                        it.name.contains(query, true) &&
                            (type == 0 || it.type == if (type == 1) "movie" else "series")
                    }
                if (items.isEmpty() && !loading && error.isBlank())
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        OriginalEmpty(
                            "No matching titles",
                            "Try another search or clear your filters.",
                        )
                    }
                items(items.distinctBy { it.key }, key = { it.key }) {
                    if (layout == 0) PosterCard(it, "library", onOpen = onOpen)
                    else MediaListRow(it, "library", onOpen)
                }
            } else {
                val items = torrents.filter { it.name.contains(query, true) }
                item {
                    Text(
                        "${items.size} torrents",
                        fontFamily = MonoFont,
                        fontSize = 10.sp,
                        color = Muted,
                    )
                }
                items(items, key = { it.id }) { torrent ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { selected = torrent }
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(StakIcons.Folder, null, Modifier.size(20.dp), tint = Muted)
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    torrent.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                )
                                Text(
                                    "${torrent.files.sumOf { it.size } / 1_000_000} MB · ${torrent.files.size} files · ${torrent.state}",
                                    fontFamily = MonoFont,
                                    fontSize = 10.sp,
                                    color = Muted,
                                )
                            }
                            Icon(StakIcons.ChevronRight, null, Modifier.size(16.dp), tint = Muted)
                        }
                        SegmentedProgress(torrent.progress)
                        HorizontalDivider(color = Rule)
                    }
                }
            }
        }
    }
    if (add) {
        var magnet by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        StakBottomSheet({ add = false }) {
            Column(
                Modifier.padding(20.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Add magnet", style = MaterialTheme.typography.headlineSmall)
                OriginalField(magnet, { magnet = it }, "magnet:?xt=urn:btih:…", singleLine = false)
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
                            add = false
                            refresh++
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }
    }
    selected?.let { torrent ->
        var busy by remember { mutableStateOf(false) }
        var confirmDelete by remember { mutableStateOf(false) }
        StakBottomSheet({ selected = null }) {
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
                        GlassIconButton({ selected = null }) {
                            Icon(StakIcons.X, "Close dialog", Modifier.size(18.dp))
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MotionButton("Pause", enabled = !busy) {
                            vm.operation {
                                vm.repository.controlTorrent(torrent.id, "pause")
                                refresh++
                            }
                        }
                        MotionButton("Resume", enabled = !busy) {
                            vm.operation {
                                vm.repository.controlTorrent(torrent.id, "resume")
                                refresh++
                            }
                        }
                        MotionButton("Delete", enabled = !busy) { confirmDelete = true }
                    }
                }
                items(torrent.files, key = { it.id }) { file ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(file.name, fontSize = 13.sp)
                        Text(
                            "${file.size / 1_000_000} MB",
                            fontFamily = MonoFont,
                            fontSize = 10.sp,
                            color = Muted,
                        )
                        MotionButton(
                            if (busy) "Requesting link…" else "Open file",
                            enabled = !busy,
                        ) {
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
            GlassAlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete torrent?") },
                text = { Text(torrent.name) },
                confirmButton = {
                    GlassTextButton({
                        vm.operation {
                            busy = true
                            try {
                                vm.repository.controlTorrent(torrent.id, "delete")
                                selected = null
                                refresh++
                            } finally {
                                busy = false
                                confirmDelete = false
                            }
                        }
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = { GlassTextButton({ confirmDelete = false }) { Text("Cancel") } },
            )
    }
}
