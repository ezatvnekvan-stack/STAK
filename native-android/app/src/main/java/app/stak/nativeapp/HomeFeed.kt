package app.stak.nativeapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

fun homeShelfFilters(shelf: HomeShelf, year: Int): Map<String, String> = buildMap {
    if (shelf.genre > 0) put("genre", shelf.genre.toString())
    if (shelf.category == "year") {
        put("year_from", year.toString())
        put("year_to", year.toString())
    }
}

@Composable
fun HomeFeedShelf(
    vm: StakViewModel,
    shelf: HomeShelf,
    saved: List<SavedMedia>,
    refresh: Int,
    onOpen: (Media, String) -> Unit,
) {
    if (shelf.id == "watchlist") {
        val own = saved.filter { it.collection == "watchlist" }.map { it.media() }
        if (own.isNotEmpty()) MediaRail(shelf.label, own, "home-watchlist", onOpen, "watchlist")
        return
    }
    if (shelf.id == "upcoming") {
        HomeUpcoming(vm, saved, refresh, onOpen)
        return
    }
    if (shelf.id == "surprise") {
        HomeTonight(vm, saved, refresh, onOpen)
        return
    }
    val jellyfin = shelf.id in listOf("continue", "recent", "available", "new_seasons")
    val connected = vm.repository.credentials.get("jellyfin_url").isNotBlank()
    val navigate = LocalNavigate.current
    if (jellyfin && !connected) {
        if (shelf.id == "continue")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connect Jellyfin to resume watching.", fontSize = 12.sp, color = Muted)
                MotionButton("Connect Jellyfin") { navigate("settings?section=jellyfin") }
            }
        return
    }
    var items by remember(shelf.id) { mutableStateOf(emptyList<Media>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    val favorite =
        saved
            .filter { it.collection == "watchlist" }
            .flatMap { it.media().genres }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    LaunchedEffect(shelf.id, refresh, retry, favorite, connected) {
        loading = true
        error = ""
        try {
            items =
                when {
                    shelf.id == "continue" -> vm.repository.library(true)
                    jellyfin -> vm.repository.jellyfinShelf(shelf.id)
                    else -> {
                        val filters =
                            homeShelfFilters(shelf, vm.repository.today.year).toMutableMap()
                        if (shelf.id in listOf("for_you", "genre_trending"))
                            homeGenres(shelf.type)
                                .firstOrNull { it.second.equals(favorite, true) }
                                ?.let { filters["genre"] = it.first.toString() }
                        if (
                            vm.repository.credentials.get("tmdb_key").isBlank() && filters.isEmpty()
                        )
                            vm.catalog(shelf.type, refresh = refresh > 0)
                        else
                            vm.repository.browse(
                                shelf.type,
                                if (shelf.category == "year") "new" else shelf.category,
                                filters = filters,
                            )
                    }
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Could not load shelf."
        } finally {
            loading = false
        }
    }
    when {
        items.isNotEmpty() ->
            MediaRail(
                if (shelf.id in listOf("for_you", "genre_trending") && favorite == null)
                    "Popular picks · add titles to personalize"
                else shelf.label,
                items,
                "home-${shelf.id}",
                onOpen,
                if (jellyfin) "library" else "browse/${android.net.Uri.encode(shelf.id)}",
            )
        loading ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Label(shelf.label)
                PosterSkeletons()
            }
    }
    if (error.isNotBlank())
        Column {
            Label(shelf.label)
            EmptyState(error) { retry++ }
        }
}

@Composable
private fun HomeUpcoming(
    vm: StakViewModel,
    saved: List<SavedMedia>,
    refresh: Int,
    onOpen: (Media, String) -> Unit,
) {
    val followed = saved.filter { it.collection == "watchlist" }.map { it.media() }
    var events by remember { mutableStateOf(emptyList<Pair<Media, Episode>>()) }
    var errors by remember { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    val today = vm.repository.today
    LaunchedEffect(followed.map { it.key }, refresh, retry) {
        val gate = Semaphore(3)
        val outcomes = coroutineScope {
            followed
                .map { title ->
                    async {
                        gate.withPermit {
                            try {
                                val media = vm.repository.details(title)
                                val episodes =
                                    if (media.type == "series") media.episodes
                                    else
                                        listOf(
                                            Episode(
                                                "release",
                                                0,
                                                0,
                                                "Movie",
                                                media.releaseDate,
                                                media.poster,
                                                "",
                                            )
                                        )
                                Result.success(
                                    episodes
                                        .filter { episode ->
                                            runCatching {
                                                    LocalDate.parse(episode.released.take(10))
                                                }
                                                .getOrNull()
                                                ?.let { it >= today && it < today.plusDays(7) } ==
                                                true
                                        }
                                        .map { media to it }
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                Result.failure<List<Pair<Media, Episode>>>(failure)
                            }
                        }
                    }
                }
                .awaitAll()
        }
        events = outcomes.mapNotNull { it.getOrNull() }.flatten().sortedBy { it.second.released }
        errors = outcomes.count { it.isFailure }
    }
    if (events.isNotEmpty())
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Label("New releases · next 7 days")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(
                    events,
                    key = { "${it.first.key}:${it.second.season}:${it.second.number}" },
                ) { (media, episode) ->
                    Surface(
                        Modifier.width(240.dp).clickable { onOpen(media, "home-upcoming") },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Rule),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SharedPoster(media, "home-upcoming", Modifier.size(48.dp, 72.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                val date = LocalDate.parse(episode.released.take(10))
                                Text(
                                    when (date) {
                                        today -> "TODAY"
                                        today.plusDays(1) -> "TOMORROW"
                                        else -> date.format(DateTimeFormatter.ofPattern("EEE d"))
                                    },
                                    color = AccentInk,
                                    fontFamily = MonoFont,
                                    fontSize = 10.sp,
                                )
                                Text(
                                    media.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    if (media.type == "movie") "Movie"
                                    else "S%02d E%02d".format(episode.season, episode.number),
                                    fontFamily = MonoFont,
                                    fontSize = 10.sp,
                                    color = Muted,
                                )
                            }
                        }
                    }
                }
            }
        }
    if (errors > 0)
        Column {
            Label("New releases")
            MotionButton("Retry $errors unavailable titles") { retry++ }
        }
}

@Composable
private fun HomeTonight(
    vm: StakViewModel,
    saved: List<SavedMedia>,
    refresh: Int,
    onOpen: (Media, String) -> Unit,
) {
    var type by remember { mutableIntStateOf(0) }
    var short by remember { mutableStateOf(false) }
    var shuffle by remember { mutableIntStateOf(0) }
    var pick by remember { mutableStateOf<Media?>(null) }
    var loading by remember { mutableStateOf(false) }
    val watched =
        saved
            .filter { it.collection == "tracking" && JSONObject(it.json).optBoolean("played") }
            .map { it.mediaKey }
            .toSet()
    LaunchedEffect(type, short, shuffle, refresh, saved.map { it.mediaKey to it.updatedAt }) {
        loading = true
        try {
            val own = saved.filter { it.collection == "watchlist" }.map { it.media() }
            val popular =
                vm.catalog(if (type == 2) "series" else "movie") +
                    if (type == 0) vm.catalog("series") else emptyList()
            val candidates =
                (own + popular)
                    .distinctBy { it.key }
                    .filter {
                        it.key !in watched &&
                            (type == 0 || it.type == if (type == 1) "movie" else "series")
                    }
                    .shuffled()
            pick = null
            for (candidate in candidates.take(8)) {
                val media =
                    try {
                        vm.repository.details(candidate)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        continue
                    }
                if (short && runtimeHours(media.runtime)?.let { it > 0 && it <= 1.5 } != true)
                    continue
                pick = media
                break
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            pick = null
        } finally {
            loading = false
        }
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Rule),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Label("Tonight")
            MotionTabs(listOf("Any", "Movies", "Series"), type) { type = it }
            OriginalToggle("90 minutes or less", short) { short = it }
            pick?.let { media ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SharedPoster(
                        media,
                        "home-tonight",
                        Modifier.size(88.dp, 132.dp).clickable { onOpen(media, "home-tonight") },
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(media.name, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                        Text(
                            listOf(media.year, media.runtime)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            fontFamily = MonoFont,
                            fontSize = 10.sp,
                            color = Muted,
                        )
                        Text(
                            media.description,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            color = Muted,
                        )
                    }
                }
                MotionButton("Open title", Modifier.fillMaxWidth()) {
                    onOpen(media, "home-tonight")
                }
            }
                ?: Text(
                    if (loading) "Finding your next watch…"
                    else "No matching suggestion. Try another filter or shuffle.",
                    fontSize = 13.sp,
                    color = Muted,
                )
            MotionButton("Shuffle", Modifier.fillMaxWidth(), enabled = !loading) { shuffle++ }
        }
    }
}
