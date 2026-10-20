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
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.*
import java.time.format.DateTimeFormatter
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LibraryScreen(vm: StakViewModel, onOpen: (Media, String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }; var refresh by remember { mutableIntStateOf(0) }; var media by remember { mutableStateOf(emptyList<Media>()) }; var torrents by remember { mutableStateOf(emptyList<Torrent>()) }; var error by remember { mutableStateOf("") }; var loading by remember { mutableStateOf(false) }; var selected by remember { mutableStateOf<Torrent?>(null) }; var add by remember { mutableStateOf(false) }; var query by rememberSaveable { mutableStateOf("") }; var type by rememberSaveable { mutableIntStateOf(0) }; var layout by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current; val navigate = LocalNavigate.current
    val connected = vm.repository.credentials.get(if (tab == 0) "jellyfin_url" else "torbox_token").isNotBlank()
    LaunchedEffect(tab, refresh) { if (!connected) return@LaunchedEffect; loading = true; error = ""; try { if (tab == 0) media = vm.repository.library() else torrents = vm.repository.torrents() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message.orEmpty() } finally { loading = false } }
    LazyVerticalGrid(GridCells.Fixed(if (tab == 0 && layout == 0) 3 else 1), Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 112.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { PageHeading("Library") }
        item(span = { GridItemSpan(maxLineSpan) }) { MotionTabs(listOf("Jellyfin", "TorBox"), tab) { tab = it; query = "" } }
        item(span = { GridItemSpan(maxLineSpan) }) { Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(2.dp).height(56.dp).background(Accent))
            Icon(if (tab == 0) StakIcons.MonitorPlay else StakIcons.Database, null, Modifier.size(23.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(if (tab == 0) "Jellyfin" else "TorBox", fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text(if (connected) "• Connected" else "• Not connected", fontSize = 10.sp, color = Muted) }; Text(if (tab == 0) "Your personal collection" else "Your cloud library", fontFamily = MonoFont, fontSize = 10.sp, color = Muted) }
            StakIconButton(StakIcons.RefreshCw, "Refresh library", enabled = !loading) { refresh++ }; StakIconButton(StakIcons.Settings2, "Library settings") { navigate("settings?section=${if (tab == 0) "jellyfin" else "account"}") }
        } }
        if (!connected) item(span = { GridItemSpan(maxLineSpan) }) { Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Rule), color = MaterialTheme.colorScheme.surface.copy(alpha = .74f)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text(if (tab == 0) "Your library awaits" else "No key", fontFamily = DotFont, fontWeight = FontWeight.Black, fontSize = 22.sp); Text(if (tab == 0) "Connect your Jellyfin server to browse your personal collection." else "This feature needs a TorBox API key.", fontSize = 14.sp, color = Muted); MotionButton(if (tab == 0) "Connect Jellyfin" else "Add key") { navigate("settings?section=${if (tab == 0) "jellyfin" else "account"}") } } } }
        if (connected) {
            item(span = { GridItemSpan(maxLineSpan) }) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { OriginalField(query, { query = it }, if (tab == 0) "Search library" else "Search torrents", Modifier.weight(1f), StakIcons.Search); if (tab == 1) StakIconButton(StakIcons.Plus, "Add magnet") { add = true } } }
            if (tab == 0) item(span = { GridItemSpan(maxLineSpan) }) { Row(verticalAlignment = Alignment.CenterVertically) { listOf("All", "Movies", "Series").forEachIndexed { index, label -> Text(label.uppercase(), Modifier.background(if (type == index) Soft else Color.Transparent, RoundedCornerShape(10.dp)).clickable { type = index }.padding(13.dp), fontFamily = MonoFont, fontSize = 10.sp) }; Spacer(Modifier.weight(1f)); IconButton({ layout = 0 }) { Icon(StakIcons.LayoutGrid, "Grid view", Modifier.size(18.dp)) }; IconButton({ layout = 1 }) { Icon(StakIcons.List, "List view", Modifier.size(18.dp)) } } }
            if (error.isNotBlank()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(error) { refresh++ } }
            if (loading) item(span = { GridItemSpan(maxLineSpan) }) { DotStatus(label = "Refreshing library") }
            if (tab == 0) {
                val items = media.filter { it.name.contains(query, true) && (type == 0 || it.type == if (type == 1) "movie" else "series") }
                if (items.isEmpty() && !loading && error.isBlank()) item(span = { GridItemSpan(maxLineSpan) }) { OriginalEmpty("No matching titles", "Try another search or clear your filters.") }
                items(items.distinctBy { it.key }, key = { it.key }) { if (layout == 0) PosterCard(it, "library", onOpen = onOpen) else MediaListRow(it, "library", onOpen) }
            } else {
                val items = torrents.filter { it.name.contains(query, true) }
                item { Text("${items.size} torrents", fontFamily = MonoFont, fontSize = 10.sp, color = Muted) }
                items(items, key = { it.id }) { torrent -> Column(Modifier.fillMaxWidth().clickable { selected = torrent }.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(StakIcons.Folder, null, Modifier.size(20.dp), tint = Muted); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(torrent.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2); Text("${torrent.files.sumOf { it.size } / 1_000_000} MB · ${torrent.files.size} files · ${torrent.state}", fontFamily = MonoFont, fontSize = 10.sp, color = Muted) }; Icon(StakIcons.ChevronRight, null, Modifier.size(16.dp), tint = Muted) }; SegmentedProgress(torrent.progress); HorizontalDivider(color = Rule) } }
            }
        }
    }
    if (add) { var magnet by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; StakBottomSheet({ add = false }) { Column(Modifier.padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text("Add magnet", style = MaterialTheme.typography.headlineSmall); OriginalField(magnet, { magnet = it }, "magnet:?xt=urn:btih:…", singleLine = false); MotionButton(if (busy) "Adding…" else "Add to TorBox", Modifier.fillMaxWidth(), primary = true, enabled = !busy && magnet.isNotBlank()) { vm.operation { busy = true; try { vm.repository.addMagnet(magnet.trim()); add = false; refresh++ } finally { busy = false } } } } } }
    selected?.let { torrent -> var busy by remember { mutableStateOf(false) }; var confirmDelete by remember { mutableStateOf(false) }
        StakBottomSheet({ selected = null }) { LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.7f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Row { Text(torrent.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); IconButton({ selected = null }) { Icon(StakIcons.X, "Close dialog", Modifier.size(18.dp)) } } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MotionButton("Pause", enabled = !busy) { vm.operation { vm.repository.controlTorrent(torrent.id, "pause"); refresh++ } }; MotionButton("Resume", enabled = !busy) { vm.operation { vm.repository.controlTorrent(torrent.id, "resume"); refresh++ } }; MotionButton("Delete", enabled = !busy) { confirmDelete = true } } }
            items(torrent.files, key = { it.id }) { file -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(file.name, fontSize = 13.sp); Text("${file.size / 1_000_000} MB", fontFamily = MonoFont, fontSize = 10.sp, color = Muted); MotionButton(if (busy) "Requesting link…" else "Open file", enabled = !busy) { vm.operation { busy = true; try { openExternal(context, vm.repository.torboxLink(torrent.id, file.id), video = file.name.substringAfterLast('.').lowercase() in listOf("mp4", "mkv", "webm", "avi", "mov")) } finally { busy = false } } }; HorizontalDivider(color = Rule) } }
        } }
        if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete torrent?") }, text = { Text(torrent.name) }, confirmButton = { TextButton({ vm.operation { busy = true; try { vm.repository.controlTorrent(torrent.id, "delete"); selected = null; refresh++ } finally { busy = false; confirmDelete = false } } }) { Text("Delete") } }, dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
    }
}

@Composable
fun TrackingScreen(vm: StakViewModel, saved: List<SavedMedia>, onOpen: (Media, String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }; var refresh by remember { mutableIntStateOf(0) }; var type by rememberSaveable { mutableIntStateOf(0) }; var period by rememberSaveable { mutableStateOf("all") }
    val navigate = LocalNavigate.current
    val history = saved.filter { it.collection == "tracking" && org.json.JSONObject(it.json).optBoolean("played") }
    val watched by vm.watchedEpisodes.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PageHeading("Tracking", if (tab == 0) "Your viewing journal" else "Episodes & releases") { StakIconButton(StakIcons.RefreshCw, if (tab == 0) "Refresh activity" else "Refresh calendar") { refresh++ } }
        MotionTabs(listOf("Activity", "Calendar"), tab) { tab = it }
        if (tab == 0) LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 112.dp)) {
            val cutoff = when (period) { "week" -> System.currentTimeMillis() - 7 * 86_400_000L; "month" -> System.currentTimeMillis() - 30 * 86_400_000L; else -> 0L }
            val filtered = history.filter { it.updatedAt >= cutoff }; val episodes = watched.filter { it.watchedAt >= cutoff }
            item { Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .74f), border = BorderStroke(1.dp, Rule)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Choice("Activity period", period, listOf("all" to "All time", "week" to "Last 7 days", "month" to "Last 30 days"), { period = it }, Modifier.weight(1f)); IconButton({ vm.operation { vm.messages.emit("Estimated runtime is based on title metadata.") } }) { Icon(StakIcons.Info, "About activity statistics", Modifier.size(15.dp)) } }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) { listOf(filtered.count { it.media().type == "movie" }.toString() to "Movies", episodes.size.toString() to "Episodes", "${filtered.count { it.media().type == "movie" } * 2 + episodes.size / 2}h" to "Est. runtime").forEach { (value, label) -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(value, fontFamily = DotFont, fontWeight = FontWeight.Black, fontSize = 32.sp); Text(label, fontFamily = MonoFont, fontSize = 10.sp, color = Muted) } } }
                HorizontalDivider(color = Rule); Row { Text("Manual history", Modifier.weight(1f), fontSize = 11.sp, color = Muted); Text("Connect Jellyfin", Modifier.clickable { navigate("settings?section=jellyfin") }, fontSize = 11.sp, color = Accent) }
            } } }
            item { Text("History  ${history.size}", fontFamily = DotFont, fontSize = 20.sp) }
            item { Row { listOf("All", "Movies", "Series").forEachIndexed { index, label -> Box(Modifier.weight(1f).height(48.dp).background(if (index == type) Soft else Color.Transparent, RoundedCornerShape(10.dp)).clickable { type = index }, contentAlignment = Alignment.Center) { Text(label, fontSize = 12.sp) } } } }
            if (history.isEmpty() && watched.isEmpty()) item { OriginalEmpty("Nothing watched yet", "Mark a title as watched or connect Jellyfin to populate your journal.", "Connect Jellyfin") { navigate("settings?section=jellyfin") } }
            val visible = history.filter { type == 0 || it.media().type == if (type == 1) "movie" else "series" }
            items(visible, key = { it.mediaKey }) { row -> Column { Text(Instant.ofEpochMilli(row.updatedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, MMM d")), fontFamily = MonoFont, fontSize = 10.sp, color = Muted); MediaListRow(row.media(), "tracking", onOpen) } }
            item { Label("Viewing activity"); Text("${history.count { it.updatedAt > System.currentTimeMillis() - 7 * 86_400_000L }} watched in 7 days", fontSize = 12.sp, color = Muted) }
        } else CalendarScreen(vm, saved, refresh, onOpen)
    }
}

@Composable
fun CalendarScreen(vm: StakViewModel, saved: List<SavedMedia>, refresh: Int, onOpen: (Media, String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }; var upcoming by remember { mutableStateOf(emptyList<Pair<Media, Episode>>()) }; var loading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    val series = saved.filter { it.collection == "watchlist" && it.media().type == "series" }.map { it.media() }
    LaunchedEffect(series.map { it.key }, refresh) { loading = true; try { val gate = Semaphore(3); upcoming = coroutineScope { series.map { title -> async { gate.withPermit { vm.repository.details(title).let { media -> media.episodes.filter { it.released.isNotBlank() }.map { media to it } } } } }.awaitAll().flatten() }; error = "" } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message.orEmpty() } finally { loading = false } }
    val date = LocalDate.parse(selected); val month = YearMonth.from(date); val today = LocalDate.now(); val days = (1..month.lengthOfMonth()).map { month.atDay(it) }; val offset = month.atDay(1).dayOfWeek.value % 7
    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 112.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Following", Modifier.weight(1f), fontSize = 12.sp, color = Muted); TextButton({ selected = today.toString() }) { Text("Today", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp) } } }
        item { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold); IconButton({ selected = date.minusMonths(1).toString() }) { Icon(StakIcons.ArrowLeft, "Previous month", Modifier.size(17.dp)) }; IconButton({ selected = date.plusMonths(1).toString() }) { Icon(StakIcons.ChevronRight, "Next month", Modifier.size(17.dp)) } }
            Row { listOf("S", "M", "T", "W", "T", "F", "S").forEach { Text(it, Modifier.weight(1f), fontFamily = MonoFont, fontSize = 10.sp, color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
            (List<LocalDate?>(offset) { null } + days).chunked(7).forEach { week -> Row { week.forEach { day -> Box(Modifier.weight(1f).height(44.dp).background(if (day == date) MaterialTheme.colorScheme.onSurface else Color.Transparent, RoundedCornerShape(7.dp)).clickable(enabled = day != null) { selected = day.toString() }, contentAlignment = Alignment.Center) { if (day != null) Text(day.dayOfMonth.toString(), fontSize = 13.sp, color = if (day == date) MaterialTheme.colorScheme.background else if (day == today) Accent else MaterialTheme.colorScheme.onSurface) } }; repeat(7 - week.size) { Spacer(Modifier.weight(1f)) } } }
        } }
        if (loading) item { DotStatus(label = "Loading schedule") }
        if (error.isNotBlank()) item { EmptyState(error) }
        item { Text((if (date == today) "Today · " else "") + date.format(DateTimeFormatter.ofPattern("EEE, MMM d")), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        val events = upcoming.filter { it.second.released.take(10) == selected }
        if (!loading && events.isEmpty()) item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("No releases for this day.", fontSize = 14.sp); if (series.isEmpty()) Text("Add movies or series to your watchlist to follow their releases.", fontSize = 12.sp, color = Muted) } }
        items(events, key = { "${it.first.key}:${it.second.id}" }) { (media, ep) -> Column(Modifier.clickable { onOpen(media, "calendar") }) { Text(media.name, fontSize = 15.sp); Text("S%02d E%02d · %s".format(ep.season, ep.number, ep.name), fontSize = 12.sp, color = Muted) } }
    }
}
