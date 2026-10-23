package app.stak.nativeapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

@Composable
fun SourcesSheet(vm: StakViewModel, media: Media, episode: Episode?, dismiss: () -> Unit) {
    val series = media.type == "series"
    val knownEpisodes = media.episodes.filter { it.season > 0 && it.number > 0 }
    var season by rememberSaveable(media.key, episode?.id) { mutableIntStateOf(episode?.season ?: knownEpisodes.firstOrNull()?.season ?: 1) }
    var number by rememberSaveable(media.key, episode?.id) { mutableIntStateOf(episode?.number ?: knownEpisodes.firstOrNull()?.number ?: 1) }
    var packMode by rememberSaveable(media.key, episode?.id) { mutableStateOf(series && episode == null) }
    var showAll by remember { mutableStateOf(false) }
    val seasonEpisodes = knownEpisodes.filter { it.season == season }.sortedBy { it.number }
    val lookupNumber = if (packMode) seasonEpisodes.firstOrNull()?.number ?: 1 else number
    val lookupEpisode = if (series) {
        seasonEpisodes.firstOrNull { it.number == lookupNumber }
            ?: Episode("${media.id}:$season:$lookupNumber", season, lookupNumber, "Episode $lookupNumber", "", "", "")
    } else null
    LaunchedEffect(season, packMode) { showAll = false }
    var results by remember { mutableStateOf(emptyList<SourceStream>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var warnings by remember { mutableStateOf(emptyList<String>()) }
    var retry by remember { mutableIntStateOf(0) }
    var cache by remember { mutableStateOf(SourceCache()) }
    var checking by remember { mutableStateOf(false) }
    var library by remember { mutableStateOf(emptyMap<String, Torrent>()) }
    var libraryNotice by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("cached") }
    var cachedOnly by remember { mutableStateOf(false) }
    val navigate = LocalNavigate.current
    val account = vm.repository.accountScope
    val connected = vm.repository.credentials.get("torbox_token").isNotBlank()
    LaunchedEffect(media.key, lookupEpisode?.season, lookupEpisode?.number, retry) {
        loading = true
        results = emptyList()
        warnings = emptyList()
        cache = SourceCache()
        library = emptyMap()
        error = ""
        try {
            val found = vm.repository.searchSources(media, lookupEpisode)
            results = found.streams
            warnings = found.errors
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message.orEmpty()
        } finally {
            loading = false
        }
    }
    LaunchedEffect(results, connected) {
        if (results.isEmpty() || !connected) return@LaunchedEffect
        checking = true
        try {
            coroutineScope {
                launch {
                    cache =
                        try {
                            vm.repository.checkSourceCache(results.map { it.hash })
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            SourceCache(errors = listOf(failure.message ?: "Cache unavailable."))
                        }
                }
                launch {
                    try {
                        val torrents = vm.repository.torrents()
                        if (vm.repository.accountScope == account)
                            library = torrents.associateBy { normalizeTorrentHash(it.hash) }
                        libraryNotice = ""
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        libraryNotice = "Your TorBox library could not be checked."
                    }
                }
            }
        } finally {
            checking = false
        }
    }
    val filtered =
        results.filter {
            (quality.isBlank() || it.resolution() == quality) &&
                (provider.isBlank() || it.source == provider) &&
                (!cachedOnly || it.hash in cache.cached) &&
                (!series || !packMode || showAll || it.isSeasonPackFor(season))
        }
    val visible =
        when (sort) {
            "seeders" -> filtered.sortedByDescending { it.seeders ?: -1 }
            "small" -> filtered.sortedBy { it.size.takeIf { n -> n > 0 } ?: Long.MAX_VALUE }
            "large" -> filtered.sortedByDescending { it.size }
            else -> filtered.sortedByDescending { it.hash in cache.cached }
        }
    StakBottomSheet(dismiss) {
        LazyColumn(
            Modifier.fillMaxWidth()
                .fillMaxHeight(.88f)
                .padding(horizontal = 16.dp)
                .testTag("sources-results"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // A modal sheet has its own window, outside the navigation shared-transition
                    // layout. Registering a shared element here crashes with normal motion.
                    Artwork(media, Modifier.size(56.dp, 84.dp).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(media.name, fontSize = 17.sp, lineHeight = 21.25.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                    media.year.takeIf { it.isNotBlank() },
                                    if (series) "Series" else "Movie",
                                    media.runtime.takeIf { it.isNotBlank() },
                                )
                                .joinToString(" · "),
                            fontFamily = MonoFont,
                            fontSize = 10.sp,
                            color = Muted,
                        )
                        Text(if (series) "S$season · ${if (packMode) "Season pack" else "E$number"}" else media.genres.take(2).joinToString(" · "),
                            fontSize = 10.sp, lineHeight = 15.sp, color = Muted)
                    }
                    Column {
                        StakIconButton(StakIcons.X, "Close dialog", onClick = dismiss)
                        StakIconButton(StakIcons.RefreshCw, "Refresh torrent sources", iconSize = 14.dp) { retry++ }
                    }
                }
            }
            if (series) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Choose season", season.toString(),
                        (knownEpisodes.map { it.season } + season).distinct().sorted().map { it.toString() to "Season $it" },
                        { value ->
                            season = value.toInt()
                            number = knownEpisodes.filter { it.season == season }.minOfOrNull { it.number } ?: 1
                        })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MotionButton("Season packs", tone = if (packMode) ButtonTone.Selection else ButtonTone.Chip) { packMode = true }
                        MotionButton("Episode", tone = if (!packMode) ButtonTone.Selection else ButtonTone.Chip) { packMode = false }
                    }
                    if (!packMode) Choice("Search episode", number.toString(),
                        (seasonEpisodes.ifEmpty { listOf(requireNotNull(lookupEpisode)) }).map { it.number.toString() to "E${it.number} · ${it.name}" },
                        { number = it.toInt() })
                    if (packMode && results.any { !it.isSeasonPackFor(season) }) {
                        MotionButton(if (showAll) "Packs only" else "Show all releases") { showAll = !showAll }
                    }
                }
            }
            if (loading) item { DotStatus(label = "Searching sources") }
            if (error.isNotBlank()) item { EmptyState(error) { retry++ } }
            if (results.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choice(
                                "Source quality",
                                quality,
                                listOf("" to "All quality") +
                                    results.map { it.resolution() }.distinct().map { it to it },
                                { quality = it },
                                Modifier.weight(1f),
                            )
                            Choice(
                                "Source provider",
                                provider,
                                listOf("" to "All providers") +
                                    results.map { it.source }.distinct().map { it to it },
                                { provider = it },
                                Modifier.weight(1f),
                            )
                        }
                        Choice(
                            "Sort sources",
                            sort,
                            listOf(
                                "cached" to "Cached first",
                                "seeders" to "Most seeders",
                                "small" to "Smallest first",
                                "large" to "Largest first",
                            ),
                            { sort = it },
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Cached only", Modifier.weight(1f), fontSize = 13.sp)
                            StakSwitch(
                                "Cached sources only",
                                cachedOnly,
                                onChange = { cachedOnly = it },
                                enabled = connected && cache.checked.isNotEmpty(),
                            )
                        }
                        if (checking) DotStatus(label = "Checking TorBox availability")
                        if (!connected)
                            MotionButton("Connect TorBox to check cache") {
                                dismiss()
                                navigate("settings?section=account")
                            }
                        Text(
                            "${visible.size} of ${results.size} releases · tags and title matches are inferred from filenames.",
                            fontSize = 11.sp,
                            color = Muted,
                        )
                    }
                }
            }
            val notices =
                warnings +
                    cache.errors.distinct().map { "$it Unchecked availability is unknown." } +
                    listOf(libraryNotice).filter { it.isNotBlank() }
            if (notices.isNotEmpty())
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Soft,
                        border = BorderStroke(1.dp, Rule),
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Label("Source status")
                            notices.forEach {
                                Text(it, fontSize = 12.sp, lineHeight = 18.sp, color = Muted)
                            }
                            MotionButton("Retry sources") { retry++ }
                        }
                    }
                }
            if (!loading && results.isEmpty() && error.isBlank())
                item { EmptyState("No sources found.") }
            if (results.isNotEmpty() && visible.isEmpty())
                item {
                    OriginalEmpty(
                        if (series && packMode && !showAll) "No season packs found" else "No matching releases",
                        if (series && packMode && !showAll) "Try episodes or show all releases. Pack labels are inferred from filenames."
                        else "Try another quality, provider or cached filter.",
                        if (series && packMode && !showAll) "Show all releases" else "Clear filters",
                    ) {
                        quality = ""
                        provider = ""
                        cachedOnly = false
                        if (series && packMode) showAll = true
                    }
                }
            items(visible, key = { "${it.hash}:${it.fileIndex}" }) { source ->
                SourceReleaseRow(vm, media, lookupEpisode, source, cache, library[source.hash], dismiss)
            }
        }
    }
}

@Composable
private fun SourceReleaseRow(
    vm: StakViewModel,
    media: Media,
    episode: Episode?,
    source: SourceStream,
    cache: SourceCache,
    existing: Torrent?,
    dismiss: () -> Unit,
) {
    var details by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val context = LocalContext.current
    val navigate = LocalNavigate.current
    val magnet = source.magnet.ifBlank { "magnet:?xt=urn:btih:${source.hash}" }
    val facts = source.quality()
    val match = sourceMatch(source, media, episode)
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Rule),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label(source.source)
            Text(
                source.title.ifBlank { source.name },
                fontSize = 13.sp,
                lineHeight = 20.sp,
                maxLines = if (details) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (facts.isNotEmpty())
                Text(
                    facts.joinToString(" · "),
                    fontFamily = MonoFont,
                    fontSize = 10.sp,
                    color = AccentInk,
                )
            Text(
                listOfNotNull(
                        source.size.takeIf { it > 0 }?.let { accountBytes(it) }
                            ?: "Size unavailable",
                        source.seeders?.let { "$it seeders" } ?: "Seeders unknown",
                    )
                    .joinToString(" · "),
                fontSize = 11.sp,
                color = Muted,
            )
            val availability =
                when {
                    existing != null || added -> "On TorBox"
                    source.hash in cache.cached -> "Cached on TorBox"
                    source.hash in cache.checked -> "Not cached at last check"
                    else -> "Cached availability unknown"
                }
            Text(
                availability,
                fontFamily = MonoFont,
                fontSize = 10.sp,
                color =
                    if (source.hash in cache.cached || existing != null || added) AccentInk
                    else Muted,
            )
            Text(
                (listOf(match.label) + match.reasons + match.warnings).joinToString(" · "),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = Muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MotionButton(
                    if (details) "Hide details" else "Release details",
                    Modifier.weight(1f),
                ) {
                    details = !details
                }
                MotionButton(
                    if (existing != null || added) "Open TorBox"
                    else if (busy) "Adding…"
                    else if (error.isNotBlank()) "Retry add" else "Add to TorBox",
                    Modifier.weight(1f),
                    primary = true,
                    enabled = !busy,
                ) {
                    if (existing != null || added) {
                        dismiss()
                        navigate("torbox")
                    } else {
                        val account = vm.repository.accountScope
                        vm.operation {
                            busy = true
                            error = ""
                            try {
                                vm.repository.addMagnet(magnet)
                                if (vm.repository.accountScope == account) {
                                    added = true
                                    vm.messages.emit("Added to TorBox.")
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = failure.message ?: "Could not add release."
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
            }
            if (details) {
                Text(
                    "${cache.sizes[source.hash]?.let { "Torrent total: ${accountBytes(it)}\n" }.orEmpty()}${facts.joinToString(" · ").ifBlank { "No technical tags detected" }}\n${source.hash}\n${source.fileIndex?.let { "File index: $it\n" }.orEmpty()}Cached availability does not verify content, language or playback compatibility.",
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = Muted,
                )
                MotionButton("Copy magnet", Modifier.fillMaxWidth()) {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText(media.name, magnet))
                }
            }
            if (error.isNotBlank())
                Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}
