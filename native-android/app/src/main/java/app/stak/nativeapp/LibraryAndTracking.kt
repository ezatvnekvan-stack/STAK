@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.stak.nativeapp

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun LibraryScreen(vm: StakViewModel, onOpen: (Media, String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var media by remember { mutableStateOf(emptyList<Media>()) }
    var torrents by remember { mutableStateOf(emptyList<Torrent>()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Torrent?>(null) }
    val context = LocalContext.current
    val reduced = LocalStakMotion.current.reduced
    LaunchedEffect(tab, refresh) {
        loading = true; error = ""
        try { if (tab == 0) media = vm.repository.library() else torrents = vm.repository.torrents() }
        catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message.orEmpty() }
        finally { loading = false }
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeading("Library", "Your connected services")
        MotionTabs(listOf("Jellyfin", "TorBox"), tab) { tab = it }
        if (error.isNotBlank()) EmptyState(error) { refresh++ }
        if (loading) DotStatus(label = "Refreshing library")
        StakRefresh(loading, { refresh++ }) {
            AnimatedContent(tab, transitionSpec = { fadeIn(motionSpec(reduced)) togetherWith fadeOut(motionSpec(reduced)) }, label = "Library tab") { destination ->
                if (destination == 0) LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    if (media.isEmpty() && !loading && error.isBlank()) item(span = { GridItemSpan(3) }) { EmptyState("No movies or series returned by this server.") }
                    items(media.distinctBy { it.key }, key = { it.key }) { PosterCard(it, "library", onOpen = onOpen) }
                } else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    if (torrents.isEmpty() && !loading && error.isBlank()) item { EmptyState("Your TorBox library is empty.") }
                    items(torrents, key = { it.id }) { torrent ->
                        Surface(onClick = { selected = torrent }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(torrent.name, style = MaterialTheme.typography.titleMedium)
                                SegmentedProgress(torrent.progress)
                                Text("${(torrent.progress * 100).toInt()}% · ${torrent.state}", style = MaterialTheme.typography.labelMedium)
                                Text("${torrent.files.size} files", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
    selected?.let { torrent ->
        var busy by remember(torrent.id) { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        StakBottomSheet(onDismissRequest = { selected = null }) {
            LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.7f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                item { Text(torrent.name, style = MaterialTheme.typography.titleLarge) }
                items(torrent.files, key = { it.id }) { file ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(file.name, style = MaterialTheme.typography.bodyMedium)
                        Text("${file.size / (1024 * 1024)} MB", style = MaterialTheme.typography.labelSmall)
                        MotionButton(if (busy) "Requesting link…" else "Open file", enabled = !busy) {
                            scope.launch {
                                busy = true
                                try { openExternal(context, vm.repository.torboxLink(torrent.id, file.id), video = file.name.substringAfterLast('.').lowercase() in listOf("mp4", "mkv", "webm", "avi", "mov")) }
                                catch (e: CancellationException) { throw e } catch (e: Exception) { vm.messages.emit(e.message.orEmpty()) }
                                finally { busy = false }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrackingScreen(vm: StakViewModel, saved: List<SavedMedia>, onOpen: (Media, String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var upcoming by remember { mutableStateOf(emptyList<Pair<Media, Episode>>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val series = saved.filter { it.collection == "watchlist" && it.media().type == "series" && it.media().id.startsWith("tt") }.map { it.media() }.distinctBy { it.key }
    LaunchedEffect(tab, series.map { it.key }, refresh) {
        if (tab != 1) return@LaunchedEffect
        loading = true; error = ""
        try {
            val gate = Semaphore(3)
            val failures = java.util.concurrent.atomic.AtomicInteger()
            val titles = coroutineScope { series.map { title -> async {
                gate.withPermit { try { vm.repository.details(title) } catch (e: CancellationException) { throw e } catch (_: Exception) { failures.incrementAndGet(); title } }
            } }.awaitAll() }
            upcoming = titles.flatMap { title -> title.episodes.filter { ep -> runCatching { Instant.parse(ep.released) }.isSuccess }.map { title to it } }.sortedBy { it.second.released }
            if (failures.get() > 0) error = "${failures.get()} series could not refresh. Pull to retry."
        } finally { loading = false }
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeading("Tracking", if (tab == 0) "Your viewing journal" else "Episodes & releases")
        MotionTabs(listOf("Activity", "Calendar"), tab) { tab = it }
        if (tab == 0) {
            val history = saved.filter { it.collection == "tracking" && org.json.JSONObject(it.json).optBoolean("played") }
            if (history.isEmpty()) EmptyState("Mark a title watched to start your viewing journal.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(history, key = { it.mediaKey }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PosterCard(row.media(), "tracking", Modifier.width(92.dp), onOpen)
                        Column(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(row.media().name, style = MaterialTheme.typography.titleMedium)
                            Text(Instant.ofEpochMilli(row.updatedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy")), style = MaterialTheme.typography.labelLarge)
                            SegmentedProgress(1f)
                            Text("Watched", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        } else {
            val selectedMonth = YearMonth.parse(month)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MotionButton("‹", onClick = { month = selectedMonth.minusMonths(1).toString() })
                Text(selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
                MotionButton("›", onClick = { month = selectedMonth.plusMonths(1).toString() })
            }
            if (loading) DotStatus(label = "Loading followed series releases")
            if (error.isNotBlank()) EmptyState(error) { refresh++ }
            val inMonth = upcoming.filter { (_, episode) -> runCatching { YearMonth.from(Instant.parse(episode.released).atZone(ZoneId.systemDefault())) == selectedMonth }.getOrDefault(false) }
            StakRefresh(loading, { refresh++ }) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    if (!loading && inMonth.isEmpty()) item { EmptyState(if (series.isEmpty()) "Add series to Watchlist to follow their releases." else "No known episodes for this month.") }
                    items(inMonth, key = { "${it.first.key}:${it.second.id}" }) { (media, episode) ->
                        Surface(onClick = { onOpen(media, "calendar") }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(Instant.parse(episode.released).atZone(ZoneId.systemDefault()).dayOfMonth.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineLarge)
                                Column { Text(media.name, style = MaterialTheme.typography.titleMedium); Text("S${episode.season} E${episode.number} · ${episode.name}", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
        }
    }
}
