@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.stak.nativeapp

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import okhttp3.Headers
import java.time.*

@Composable
fun PageHeading(title: String, subtitle: String = "", action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}

@Composable
fun EmptyState(message: String, retry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        retry?.let { MotionButton("Retry", onClick = it) }
    }
}

@Composable
fun Artwork(media: Media, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = (androidx.lifecycle.viewmodel.compose.viewModel<StakViewModel>()).repository
    val headers = remember(media.poster) {
        if (media.jellyfinId.isNotBlank() && media.poster.startsWith(repo.credentials.get("jellyfin_url").trimEnd('/') + "/"))
            Headers.headersOf("X-Emby-Token", repo.credentials.get("jellyfin_token")) else Headers.headersOf()
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Text(media.name.take(1), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AsyncImage(ImageRequest.Builder(context).data(media.poster).headers(headers).crossfade(false).build(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun SharedPoster(media: Media, source: String, modifier: Modifier = Modifier) {
    val shared = LocalShared.current
    val destination = LocalDestination.current
    val reduced = LocalStakMotion.current.reduced
    val bounds = if (shared != null && destination != null && !reduced) with(shared) {
        modifier.sharedElement(rememberSharedContentState("$source/${media.key}"), destination,
            boundsTransform = { _, _ -> tween(360, easing = StakEase) })
    } else modifier
    Artwork(media, bounds.clip(RoundedCornerShape(14.dp)))
}

@Composable
fun PosterCard(media: Media, source: String, modifier: Modifier = Modifier, onOpen: (Media, String) -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val policy = LocalStakMotion.current
    val view = LocalView.current
    val scale by animateFloatAsState(if (pressed && !policy.reduced) .98f else 1f, if (policy.reduced) snap() else spring(.95f, 950f), label = "Poster press")
    var menu by remember { mutableStateOf(false) }
    Column(modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = interactions, indication = null, role = Role.Button, onClickLabel = "Open ${media.name}", onLongClickLabel = "Actions for ${media.name}",
            onClick = { onOpen(media, source) }, onLongClick = { if (policy.haptics) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); menu = true })) {
        SharedPoster(media, source, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        Text(media.name, Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(media.year, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        if (media.progress > 0) SegmentedProgress(media.progress / 100f, Modifier.padding(top = 5.dp))
    }
    if (menu) {
        val vm: StakViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        StakBottomSheet(onDismissRequest = { menu = false }) {
            Column(Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(media.name, style = MaterialTheme.typography.titleLarge)
                MotionButton("Open details", Modifier.fillMaxWidth(), primary = true) { menu = false; onOpen(media, source) }
                MotionButton("Toggle watchlist", Modifier.fillMaxWidth()) { vm.toggle(media, "watchlist"); menu = false }
            }
        }
    }
}

@Composable
fun PosterSkeletons() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) { Box(Modifier.weight(1f).aspectRatio(2f / 3f).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))) }
    }
}

@Composable
fun HomeScreen(vm: StakViewModel, saved: List<SavedMedia>, onOpen: (Media, String) -> Unit, onDiscover: () -> Unit) {
    var titles by remember { mutableStateOf(vm.catalogs["movie:"].orEmpty()) }
    var resume by remember { mutableStateOf(emptyList<Media>()) }
    var loading by remember { mutableStateOf(titles.isEmpty()) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(refresh) {
        if (refresh > 0) refreshing = true
        try { titles = vm.catalog("movie", refresh = refresh > 0); error = "" }
        catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message.orEmpty() }
        finally { loading = false; refreshing = false }
        if (vm.repository.credentials.get("jellyfin_url").isNotBlank()) {
            try { resume = vm.repository.library(true) } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Library screen offers connection recovery. */ }
        }
    }
    StakRefresh(refreshing, { refresh++ }) {
        LazyColumn(state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { PageHeading("STAK", "Your media. Your collection.") }
            item { MotionButton("Search movies & series", Modifier.fillMaxWidth(), onClick = onDiscover) }
            if (loading) item { PosterSkeletons(); DotStatus(label = "Loading Home") }
            if (error.isNotBlank()) item { EmptyState(error) { refresh++ } }
            if (resume.isNotEmpty()) item { MediaRail("Continue watching", resume, "home-resume", onOpen) }
            if (titles.isNotEmpty()) item {
                val hero = titles.first()
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp)).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PosterCard(hero, "home-hero", Modifier.width(112.dp), onOpen)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(hero.name, style = MaterialTheme.typography.titleLarge)
                        Text(listOf(hero.year, hero.rating.takeIf { it.isNotBlank() }?.let { "$it IMDb" }).filterNotNull().joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        MotionButton("Details", primary = true) { onOpen(hero, "home-hero") }
                    }
                }
            }
            val watchlist = saved.filter { it.collection == "watchlist" }.map { it.media() }
            if (watchlist.isNotEmpty()) item { MediaRail("Watchlist", watchlist, "home-watchlist", onOpen) }
            if (titles.isNotEmpty()) item { MediaRail("Popular movies", titles.drop(1), "home-popular", onOpen) }
            item { Text("Metadata provided by Cinemeta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun MediaRail(title: String, media: List<Media>, source: String, onOpen: (Media, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(media.distinctBy { it.key }, key = { it.key }) { PosterCard(it, source, Modifier.width(116.dp), onOpen) } }
    }
}

@Composable
fun SearchEntry(value: String, onSearch: (String) -> Unit, readOnly: Boolean = false) {
    OutlinedTextField(if (readOnly) "" else value, onSearch, Modifier.fillMaxWidth(), singleLine = true, readOnly = readOnly,
        leadingIcon = { Icon(Icons.Outlined.Search, "Search") }, placeholder = { Text("Search movies & series") }, shape = RoundedCornerShape(28.dp))
}

@Composable
fun StakRefresh(refreshing: Boolean, onRefresh: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(refreshing, onRefresh, Modifier.fillMaxSize(), state = state, indicator = {
        if (refreshing || state.distanceFraction > 0f) Surface(Modifier.align(Alignment.TopCenter).padding(top = 8.dp), shape = RoundedCornerShape(24.dp), tonalElevation = 4.dp) {
            DotStatus(Modifier.padding(12.dp), "Refreshing", if (refreshing) null else state.distanceFraction.coerceIn(0f, 1f))
        }
    }, content = { content() })
}

@Composable
fun DiscoverScreen(vm: StakViewModel, onOpen: (Media, String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableIntStateOf(0) }
    var titles by remember { mutableStateOf(vm.catalogs["movie:"].orEmpty()) }
    var loading by remember { mutableStateOf(titles.isEmpty()) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var paging by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val grid = rememberLazyGridState()
    val mediaType = if (type == 0) "movie" else "series"
    val latestType by rememberUpdatedState(mediaType)
    LaunchedEffect(query, type, refresh) {
        loading = true; error = ""; hasMore = true
        try {
            if (query.isNotBlank()) delay(350)
            titles = vm.catalog(mediaType, query, refresh > 0)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { titles = emptyList(); error = e.message.orEmpty() }
        finally { loading = false }
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeading("Discover", "Find your next watch")
        SearchEntry(query, { query = it })
        MotionTabs(listOf("Movies", "Series"), type) { type = it }
        if (loading && titles.isEmpty()) { PosterSkeletons(); DotStatus() }
        if (error.isNotBlank()) EmptyState(error) { refresh++ }
        if (!loading && error.isBlank() && titles.isEmpty()) EmptyState("No titles found. Try another search.")
        StakRefresh(loading && titles.isNotEmpty(), { refresh++ }) {
            LazyVerticalGrid(GridCells.Fixed(3), state = grid, verticalArrangement = Arrangement.spacedBy(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(titles.distinctBy { it.key }, key = { it.key }) { PosterCard(it, "discover", onOpen = onOpen) }
                if (titles.isNotEmpty() && hasMore) item(span = { GridItemSpan(3) }) {
                    MotionButton(if (paging) "Loading…" else "Load more", Modifier.fillMaxWidth(), enabled = !paging && !loading) {
                        val requestedType = mediaType; val requestedQuery = query; val offset = titles.size
                        scope.launch {
                            paging = true
                            try {
                                val next = vm.repository.catalog(requestedType, requestedQuery, offset)
                                if (requestedType == latestType && requestedQuery == query) {
                                    val combined = (titles + next).distinctBy { it.key }
                                    hasMore = combined.size > titles.size
                                    titles = combined; vm.catalogs["$requestedType:$requestedQuery"] = combined
                                }
                            } catch (e: CancellationException) { throw e } catch (e: Exception) { vm.messages.emit(e.message.orEmpty()) }
                            finally { paging = false }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionScreen(title: String, media: List<Media>, source: String, onOpen: (Media, String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeading(title, "${media.size} titles")
        if (media.isEmpty()) EmptyState("Your $title is empty. Open a title to add it.")
        LazyVerticalGrid(GridCells.Fixed(3), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(media.distinctBy { it.key }, key = { it.key }) { PosterCard(it, source, onOpen = onOpen) }
        }
    }
}

@Composable
fun DetailScreen(vm: StakViewModel, key: String, source: String, saved: List<SavedMedia>, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val initial = remember(key) { vm.media(key) }
    if (initial == null) { Column { MotionButton("Back", onClick = onBack); EmptyState("This title is unavailable. Open it from your collection again.") }; return }
    var media by remember(key) { mutableStateOf(initial) }
    var loading by remember(key) { mutableStateOf(initial.id.startsWith("tt") && initial.description.isBlank()) }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val view = LocalView.current
    val policy = LocalStakMotion.current
    val scope = rememberCoroutineScope()
    val watchlisted = saved.any { it.mediaKey == key && it.collection == "watchlist" }
    val watched = saved.any { it.mediaKey == key && it.collection == "tracking" && org.json.JSONObject(it.json).optBoolean("played") }
    LaunchedEffect(key, retry) {
        try { media = vm.repository.details(initial).copy(jellyfinId = initial.jellyfinId, progress = initial.progress, playbackTicks = initial.playbackTicks); error = "" }
        catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message.orEmpty() }
        finally { loading = false }
    }
    LazyColumn(Modifier.testTag("title-scroll"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }
        item {
            SharedPoster(media, source, Modifier.fillMaxWidth().aspectRatio(1.15f))
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(media.name, style = MaterialTheme.typography.headlineSmall)
                Text(listOf(media.year, media.runtime).filter { it.isNotBlank() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (media.rating.isNotBlank()) Text("${media.rating} IMDb", style = MaterialTheme.typography.labelLarge)
                if (loading) DotStatus(label = "Loading title details")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MotionButton(if (watchlisted) "Saved" else "+ Watchlist", Modifier.weight(1f), primary = watchlisted) { vm.toggle(media, "watchlist") }
                DrawnCheck(watched)
                MotionButton(if (watched) "Unwatch" else "Watched", Modifier.weight(1f), enabled = !busy) {
                    scope.launch {
                        busy = true
                        try {
                            val previous = vm.changeWatched(media, !watched)
                            if (policy.haptics) view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK)
                            busy = false
                            if (snackbar.showSnackbar(if (!watched) "Marked watched" else "Marked unwatched", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                                vm.restoreWatched(media, previous)
                            }
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { vm.messages.emit(e.message.orEmpty()) }
                        finally { busy = false }
                    }
                }
            }
        }
        if (media.jellyfinId.isNotBlank()) item {
            MotionButton(if (media.progress > 0) "Resume in Jellyfin" else "Open in Jellyfin", Modifier.fillMaxWidth(), primary = true) {
                openExternal(context, vm.repository.credentials.get("jellyfin_url").trimEnd('/') + "/web/#/details?id=" + Uri.encode(media.jellyfinId))
            }
            if (media.progress > 0) SegmentedProgress(media.progress / 100f, Modifier.padding(top = 12.dp))
        }
        if (error.isNotBlank()) item { EmptyState("Details could not refresh. $error") { retry++ } }
        if (media.description.isNotBlank()) item { Text(media.description, style = MaterialTheme.typography.bodyLarge) }
        if (media.genres.isNotEmpty()) item { Text(media.genres.joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (media.type == "series" && media.episodes.isNotEmpty()) item { MotionButton("Seasons & episodes", Modifier.fillMaxWidth()) { sheet = true } }
        item { MotionButton("Trailers on YouTube", Modifier.fillMaxWidth()) { openExternal(context, "https://www.youtube.com/results?search_query=" + Uri.encode("${media.name} ${media.year} official trailer")) } }
    }
    if (sheet) EpisodeSheet(vm, media) { sheet = false }
}

@Composable
fun EpisodeSheet(vm: StakViewModel, media: Media, onDismiss: () -> Unit) {
    var season by rememberSaveable { mutableIntStateOf(media.episodes.minOf { it.season }) }
    val watched by vm.watchedEpisodes.collectAsStateWithLifecycle()
    val seasons = media.episodes.map { it.season }.distinct().sorted()
    StakBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.72f).padding(horizontal = 16.dp)) {
            Text(media.name, style = MaterialTheme.typography.titleLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(seasons) { s -> FilterChip(season == s, { season = s }, { Text("Season $s") }) }
            }
            val episodes = media.episodes.filter { it.season == season }.sortedBy { it.number }
            val completed = watched.count { it.mediaKey == media.key && it.season == season && episodes.any { ep -> ep.number == it.episode } }
            Text("$completed / ${episodes.size} episodes watched", style = MaterialTheme.typography.labelLarge)
            SegmentedProgress(if (episodes.isEmpty()) 0f else completed.toFloat() / episodes.size, Modifier.padding(vertical = 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                items(episodes, key = { it.id.ifBlank { "${it.season}:${it.number}" } }) { episode ->
                    val isWatched = watched.any { it.mediaKey == media.key && it.season == season && it.episode == episode.number }
                    val future = runCatching { Instant.parse(episode.released).isAfter(Instant.now()) }.getOrDefault(false)
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${episode.number}. ${episode.name}", style = MaterialTheme.typography.titleMedium)
                            Text(episode.released.take(10), style = MaterialTheme.typography.labelSmall)
                            if (episode.overview.isNotBlank()) Text(episode.overview, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        IconToggleButton(isWatched, enabled = !future, onCheckedChange = { enabled -> vm.operation {
                            if (enabled) vm.repository.dao.putEpisode(WatchedEpisode(media.key, season, episode.number))
                            else vm.repository.dao.removeEpisode(media.key, season, episode.number)
                        } }) { if (isWatched) DrawnCheck(true) else Icon(Icons.Outlined.RadioButtonUnchecked, "Mark episode watched") }
                    }
                }
            }
        }
    }
}

fun openExternal(context: android.content.Context, uri: String, video: Boolean = false) {
    runCatching {
        val parsed = Uri.parse(uri)
        require(parsed.scheme == "https" || parsed.scheme == "http")
        context.startActivity(Intent(Intent.ACTION_VIEW, parsed).apply { if (video) setDataAndType(parsed, "video/*"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }.onFailure { android.widget.Toast.makeText(context, "No compatible app found to open this link.", android.widget.Toast.LENGTH_SHORT).show() }
}
