@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package app.stak.nativeapp

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.time.*
import kotlinx.coroutines.*
import okhttp3.Headers

@Composable
fun PageHeading(
    title: String,
    subtitle: String = "",
    eyebrow: String = "",
    action: (@Composable () -> Unit)? = null,
) {
    val discover = title == "Discover"
    val width = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val heading =
        if (discover) {
            val size = (width * .08f).coerceIn(32f, 42f)
            MaterialTheme.typography.headlineLarge.copy(
                fontSize = size.sp,
                lineHeight = (size * 1.08f).sp,
            )
        } else MaterialTheme.typography.headlineLarge
    Column {
        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp, bottom = if (discover) 14.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (eyebrow.isNotBlank()) {
                    Label(eyebrow)
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    title,
                    Modifier.padding(vertical = 3.dp).height(heading.lineHeight.value.dp),
                    style = heading,
                )
                if (subtitle.isNotBlank())
                    Text(
                        subtitle,
                        Modifier.padding(top = if (discover) 6.dp else 7.dp),
                        fontSize = 12.sp,
                        lineHeight = if (discover) 16.8.sp else 18.sp,
                        color = Muted,
                    )
            }
            action?.invoke()
        }
        HorizontalDivider(color = Rule)
    }
}

@Composable
fun EmptyState(message: String, retry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = Muted, fontSize = 13.sp)
        retry?.let { MotionButton("Retry", onClick = it) }
    }
}

@Composable
fun Artwork(media: Media, modifier: Modifier = Modifier, backdrop: Boolean = false) {
    val context = LocalContext.current
    val repo = androidx.lifecycle.viewmodel.compose.viewModel<StakViewModel>().repository
    val url = if (backdrop) media.background.ifBlank { media.poster } else media.poster
    val headers =
        remember(url) {
            if (
                media.jellyfinId.isNotBlank() &&
                    repo.credentials.get("jellyfin_url").isNotBlank() &&
                    url.startsWith(repo.credentials.get("jellyfin_url").trimEnd('/') + "/")
            )
                Headers.headersOf("X-Emby-Token", repo.credentials.get("jellyfin_token"))
            else Headers.headersOf()
        }
    Box(modifier.background(Color(0xff111111))) {
        AsyncImage(
            ImageRequest.Builder(context).data(url).headers(headers).crossfade(false).build(),
            null,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
fun SharedPoster(media: Media, source: String, modifier: Modifier = Modifier) {
    Artwork(media, modifier.clip(RoundedCornerShape(9.dp)))
}

/** The cell paths and SVG view box are copied from the original RatingLogo. */
@Composable
fun RatingLogo(source: String, modifier: Modifier = Modifier) {
    val word =
        when (source.lowercase()) {
            "tmdb" -> "TMDB"
            "rt",
            "rotten tomatoes" -> "RT"
            "metacritic" -> "MC"
            else -> "IMDB"
        }
    val letters =
        mapOf(
            'I' to listOf("111", "010", "010", "010", "111"),
            'M' to listOf("10001", "11011", "10101", "10001", "10001"),
            'D' to listOf("110", "101", "101", "101", "110"),
            'B' to listOf("110", "101", "110", "101", "110"),
            'T' to listOf("111", "010", "010", "010", "010"),
            'R' to listOf("110", "101", "110", "101", "101"),
            'C' to listOf("011", "100", "100", "100", "011"),
        )
    val foreground = LocalContentColor.current
    Canvas(modifier.size(28.dp, 12.dp)) {
        val width = 3 + word.sumOf { letters.getValue(it)[0].length + 1 }
        val scale = minOf(size.width / width, size.height / 9f)
        withTransform({
            translate((size.width - width * scale) / 2, (size.height - 9 * scale) / 2)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawRoundRect(
                foreground,
                Offset(.5f, .5f),
                androidx.compose.ui.geometry.Size(width - 1f, 8f),
                androidx.compose.ui.geometry.CornerRadius(1f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(.5f),
            )
            var left = 2
            word.forEach { letter ->
                val rows = letters.getValue(letter)
                rows.forEachIndexed { y, row ->
                    row.forEachIndexed { x, value ->
                        if (value == '1')
                            drawRect(
                                foreground,
                                Offset((left + x).toFloat(), (y + 2).toFloat()),
                                androidx.compose.ui.geometry.Size(.8f, .8f),
                            )
                    }
                }
                left += rows[0].length + 1
            }
        }
    }
}

@Composable
fun RatingBadge(
    media: Media,
    compact: Boolean = false,
    quiet: Boolean = false,
    hero: Boolean = false,
    bare: Boolean = false,
    valueSize: TextUnit = 11.sp,
) {
    val number = media.rating.removeSuffix("%").toFloatOrNull() ?: return
    val max = if (media.ratingSource.lowercase() in listOf("imdb", "tmdb")) 10f else 100f
    if (
        number <= 0 ||
            number > max ||
            !number.isFinite() ||
            LocalOptions.current["rating_${media.ratingSource}"] == "false"
    )
        return
    val foreground = if (hero) Color.White else MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(8.dp)
    val decorated =
        if (quiet || bare) Modifier
        else
            Modifier.background(
                    if (hero) Color.Black.copy(alpha = .65f)
                    else if (MaterialTheme.colorScheme.background.red < .5f) Color(0xff101010)
                    else Color(0xffeaeae7),
                    shape,
                )
                .border(1.dp, if (hero) Color.White.copy(alpha = .25f) else Rule, shape)
    CompositionLocalProvider(LocalContentColor provides foreground) {
        Row(
            decorated
                .heightIn(min = if (quiet) 22.dp else 0.dp)
                .padding(
                    horizontal = if (quiet || bare) 0.dp else 8.dp,
                    vertical = if (quiet) 0.dp else if (bare) 2.dp else 6.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (quiet) 5.dp else 6.dp),
        ) {
            RatingLogo(media.ratingSource, Modifier.alpha(if (quiet) .65f else .8f))
            Text(
                media.rating,
                Modifier.height(valueSize.value.dp),
                fontFamily = MonoFont,
                fontSize = valueSize,
                lineHeight = valueSize,
                letterSpacing = 0.sp,
            )
        }
    }
}

@Composable
fun PosterCard(
    media: Media,
    source: String,
    modifier: Modifier = Modifier,
    onOpen: (Media, String) -> Unit,
    term: String = "",
) {
    val isSearch = source.startsWith("search")
    val highlight = AccentInk
    val interactions = remember { MutableInteractionSource() }
    var menu by remember { mutableStateOf(false) }
    val vm: StakViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val watchlisted = saved.any { it.mediaKey == media.key && it.collection == "watchlist" }
    val watched =
        saved.any {
            it.mediaKey == media.key &&
                it.collection == "tracking" &&
                org.json.JSONObject(it.json).optBoolean("played")
        }
    Column(
        modifier.combinedClickable(
            interactionSource = interactions,
            indication = null,
            role = Role.Button,
            onClickLabel = "Open ${media.name}",
            onLongClickLabel = "Actions for ${media.name}",
            onClick = { onOpen(media, source) },
            onLongClick = { menu = true },
        )
    ) {
        Box(
            Modifier.fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(9.dp))
                .border(1.dp, Rule, RoundedCornerShape(9.dp))
        ) {
            SharedPoster(media, source, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxWidth()
                    .fillMaxHeight(.5f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = .9f))
                        )
                    )
            )
            if (watchlisted && source != "watchlist")
                Icon(
                    StakIcons.Bookmark,
                    "In watchlist",
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp),
                    tint = Accent,
                )
            if (watched)
                Icon(
                    StakIcons.Check,
                    "Watched",
                    Modifier.align(Alignment.TopStart).padding(8.dp).size(16.dp),
                    tint = Color(0xff10b981),
                )
            if (!watched && media.progress > 0)
                Box(
                    Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth(media.progress / 100)
                        .height(4.dp)
                        .background(Accent)
                )
        }
        Column(
            Modifier.padding(top = 8.dp, start = 1.dp, end = 1.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    val offset =
                        if (term.isBlank()) -1
                        else media.name.indexOf(term.trim(), ignoreCase = true)
                    append(media.name)
                    if (offset >= 0)
                        addStyle(
                            androidx.compose.ui.text.SpanStyle(color = highlight),
                            offset,
                            offset + term.trim().length,
                        )
                },
                fontSize = if (LocalOptions.current["compact"] == "true") 12.sp else 13.sp,
                lineHeight = 18.2.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                media.year.ifBlank { "—" },
                fontFamily = MonoFont,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = Muted,
                maxLines = 1,
            )
            Box(Modifier.padding(bottom = 4.dp)) {
                RatingBadge(
                    media,
                    compact = true,
                    bare = source.startsWith("discover"),
                    valueSize = if (isSearch) 9.sp else 11.sp,
                )
            }
        }
    }
    if (menu)
        StakBottomSheet({ menu = false }) {
            Column(
                Modifier.padding(20.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(media.name, style = MaterialTheme.typography.titleLarge)
                MotionButton("Open details", Modifier.fillMaxWidth()) {
                    menu = false
                    onOpen(media, source)
                }
                MotionButton(
                    "${if (watchlisted) "Remove from" else "Add to"} watchlist",
                    Modifier.fillMaxWidth(),
                ) {
                    vm.toggle(media, "watchlist")
                    menu = false
                }
                MotionButton(
                    if (watched) "Mark unwatched" else "Mark watched",
                    Modifier.fillMaxWidth(),
                ) {
                    vm.operation { vm.changeWatched(media, !watched) }
                    menu = false
                }
            }
        }
}

@Composable
fun PosterSkeletons() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) {
            Box(Modifier.weight(1f).aspectRatio(2f / 3f).background(Soft, RoundedCornerShape(9.dp)))
        }
    }
}

@Composable
fun HomeScreen(
    vm: StakViewModel,
    saved: List<SavedMedia>,
    onOpen: (Media, String) -> Unit,
    initialQuery: String = "",
) {
    var rows by remember { mutableStateOf<Map<String, List<Media>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var input by rememberSaveable { mutableStateOf(initialQuery) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var searchPage by remember { mutableIntStateOf(1) }
    var searchRetry by remember { mutableIntStateOf(0) }
    var searchItems by remember { mutableStateOf(emptyList<Media>()) }
    var searchNext by remember { mutableStateOf<Int?>(null) }
    var searchNotices by remember { mutableStateOf(emptyList<String>()) }
    var searchError by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var appliedRouteQuery by rememberSaveable { mutableStateOf(initialQuery) }
    LaunchedEffect(initialQuery) {
        if (initialQuery != appliedRouteQuery) {
            appliedRouteQuery = initialQuery
            input = initialQuery
            query = initialQuery
            searchPage = 1
            searchItems = emptyList()
        }
    }
    LaunchedEffect(input) {
        delay(320)
        if (query != input.trim()) {
            query = input.trim()
            searchPage = 1
            searchItems = emptyList()
        }
    }
    LaunchedEffect(query, searchPage, searchRetry) {
        if (query.isBlank()) {
            searchItems = emptyList()
            searchNotices = emptyList()
            searchError = ""
            searching = false
            return@LaunchedEffect
        }
        searching = true
        try {
            val result = vm.repository.searchTitles(query, searchPage)
            searchItems =
                (if (searchPage == 1) result.items else searchItems + result.items)
                    .distinctBy { it.key }
                    .sortedBy { searchRelevance(it, query) }
            searchNext = result.next
            searchNotices = result.notices
            searchError = ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            searchError = e.message.orEmpty()
        } finally {
            searching = false
        }
    }
    val options = LocalOptions.current
    val navigate = LocalNavigate.current
    val rowSpecs =
        listOf(
            Triple("trending_movie", "Trending movies", "movie"),
            Triple("trending_series", "Trending series", "series"),
            Triple("top_movie", "Top rated movies", "movie"),
            Triple("new_movie", "Released in ${Year.now()} · movies", "movie"),
            Triple("top_series", "Top rated series", "series"),
            Triple("new_series", "Released in ${Year.now()} · series", "series"),
        )
    LaunchedEffect(refresh, query, options.filterKeys { it.startsWith("home_") }) {
        if (query.isNotBlank()) return@LaunchedEffect
        loading = true
        try {
            val result = linkedMapOf<String, List<Media>>()
            coroutineScope {
                rowSpecs
                    .filterIndexed { index, spec ->
                        options["home_${spec.first}"]?.toBooleanStrictOrNull() ?: (index < 4)
                    }
                    .map { (id, _, type) ->
                        async {
                            val category =
                                when {
                                    id.startsWith("top_") -> "top"
                                    id.startsWith("new_") -> "new"
                                    else -> "popular"
                                }
                            val media =
                                if (vm.repository.credentials.get("tmdb_key").isBlank())
                                    vm.catalog(type, refresh = refresh > 0)
                                else vm.repository.browse(type, category)
                            id to media
                        }
                    }
                    .awaitAll()
                    .forEach { (id, data) -> result[id] = data }
            }
            if (vm.repository.credentials.get("jellyfin_url").isNotBlank())
                runCatching { vm.repository.library(true) }.onSuccess { result["continue"] = it }
            rows = result
            error = ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message.orEmpty()
        } finally {
            loading = false
        }
    }
    StakRefresh(loading && refresh > 0, { refresh++ }) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().height(58.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StakBrand(Modifier.size(34.dp))
                        Text(
                            "STAK",
                            Modifier.padding(start = 10.dp).weight(1f).height(25.dp),
                            fontFamily = DotFont,
                            fontWeight = FontWeight.Black,
                            fontSize = 25.sp,
                            lineHeight = 25.sp,
                            letterSpacing = (-.06).em,
                        )
                        Row(
                            Modifier.height(34.dp)
                                .border(1.dp, Rule, CircleShape)
                                .clickable { navigate("settings?section=account") }
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(6.dp)
                                    .background(
                                        if (
                                            vm.repository.credentials
                                                .get("torbox_token")
                                                .isNotBlank()
                                        )
                                            Color(0xff10b981)
                                        else Muted,
                                        CircleShape,
                                    )
                            )
                            Text(
                                if (vm.repository.credentials.get("torbox_token").isBlank())
                                    "No key"
                                else "TorBox",
                                fontFamily = MonoFont,
                                fontSize = 10.sp,
                            )
                        }
                    }
                    HomeSearchInput(input, { input = it }) {
                        input = ""
                        query = ""
                        searchItems = emptyList()
                        searchPage = 1
                    }
                }
            }
            if (query.isNotBlank()) {
                if (searchNotices.isNotEmpty())
                    item {
                        searchNotices.forEach {
                            Text(it, fontSize = 12.sp, lineHeight = 18.sp, color = Muted)
                        }
                    }
                item {
                    Column {
                        Label("Results: “$query” · ${searchItems.size}")
                        Spacer(Modifier.height(12.dp))
                        if (searchError.isNotBlank())
                            OriginalEmpty("Error", searchError, "Retry") { searchRetry++ }
                        if (searching && searchItems.isEmpty()) PosterSkeletons()
                        else if (searchItems.isEmpty() && searchError.isBlank())
                            OriginalEmpty(
                                "No results",
                                "Try another term or loosen the filters.",
                                "Clear filters",
                            ) {
                                input = ""
                                query = ""
                            }
                        else if (searchItems.isNotEmpty())
                            SearchPosterRow(searchItems.take(4), query, onOpen)
                    }
                }
                items(searchItems.chunked(4).drop(1)) { row -> SearchPosterRow(row, query, onOpen) }
                if (searchNext != null && searchItems.isNotEmpty())
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            MotionButton(
                                if (searching) "Loading…" else "Load more",
                                enabled = !searching,
                            ) {
                                searchPage = searchNext!!
                            }
                        }
                    }
            } else {
                val heroes =
                    rows["trending_movie"]
                        .orEmpty()
                        .take(3)
                        .zip(rows["trending_series"].orEmpty().take(3))
                        .flatMap { listOf(it.first, it.second) }
                        .ifEmpty { rows.values.flatten().take(6) }
                if (heroes.isNotEmpty()) item { HomeHero(vm, heroes, onOpen) }
                if (loading && rows.isEmpty()) item { PosterSkeletons() }
                if (error.isNotBlank()) item { EmptyState(error) { refresh++ } }
                if (options["home_continue"] != "false") {
                    if (rows["continue"].orEmpty().isNotEmpty())
                        item {
                            MediaRail(
                                "Continue watching",
                                rows["continue"].orEmpty(),
                                "home-continue",
                                onOpen,
                            )
                        }
                    else if (vm.repository.credentials.get("jellyfin_url").isBlank())
                        item {
                            FlowRow(
                                Modifier.padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "Connect Jellyfin to resume watching.",
                                    Modifier.align(Alignment.CenterVertically),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = Muted,
                                )
                                MotionButton("Connect Jellyfin") {
                                    navigate("settings?section=jellyfin")
                                }
                            }
                        }
                }
                val watchlist = saved.filter { it.collection == "watchlist" }.map { it.media() }
                if (watchlist.isNotEmpty() && options["home_watchlist"] != "false")
                    item { MediaRail("Your watchlist", watchlist, "home-watchlist", onOpen) }
                rowSpecs.forEach { (id, label, _) ->
                    if (rows[id].orEmpty().isNotEmpty())
                        item { MediaRail(label, rows[id].orEmpty(), "home-$id", onOpen) }
                }
            }
        }
    }
}

@Composable
private fun SearchPosterRow(row: List<Media>, query: String, onOpen: (Media, String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        row.forEach { PosterCard(it, "search-home", Modifier.weight(1f), onOpen, term = query) }
        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
fun StakBrand(modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(9.dp)).background(Color.Black)) {
        val path =
            Path().apply {
                moveTo(33f, 30f)
                lineTo(79f, 30f)
                lineTo(79f, 42f)
                lineTo(45f, 42f)
                lineTo(45f, 48f)
                lineTo(75f, 48f)
                lineTo(75f, 78f)
                lineTo(29f, 78f)
                lineTo(29f, 66f)
                lineTo(63f, 66f)
                lineTo(63f, 60f)
                lineTo(33f, 60f)
                close()
            }
        withTransform({ scale(size.width / 108f, size.height / 108f, pivot = Offset.Zero) }) {
            drawPath(path, Color.White)
        }
    }
}

@Composable
fun HomeHero(vm: StakViewModel, media: List<Media>, onOpen: (Media, String) -> Unit) {
    val pager =
        rememberPagerState(initialPage = (Int.MAX_VALUE / 2 / media.size) * media.size) {
            Int.MAX_VALUE
        }
    val activeIndex = pager.currentPage % media.size
    var details by remember { mutableStateOf<Map<String, Media>>(emptyMap()) }
    LaunchedEffect(activeIndex) {
        val active = media[activeIndex]
        if (details.containsKey(active.key)) return@LaunchedEffect
        try {
            val result =
                if (vm.repository.credentials.get("tmdb_key").isNotBlank())
                    vm.repository.tmdbDetails(active, loadEpisodes = false)
                else vm.repository.details(active)
            details = details + (active.key to result)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            /* Hero retains its catalog data. */
        }
    }
    val reduced = LocalStakMotion.current.reduced
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(reduced, lifecycle) {
        if (reduced || !lifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            return@LaunchedEffect
        while (true) {
            delay(9000)
            if (!pager.isScrollInProgress) pager.animateScrollToPage(pager.currentPage + 1)
        }
    }
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth()
                .height(
                    (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * .65f)
                        .coerceIn(240f, 310f)
                        .dp
                )
                .clip(RoundedCornerShape(14.dp))
        ) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { index ->
                val summary = media[index % media.size]
                val title = details[summary.key] ?: summary
                Box(Modifier.fillMaxSize().clickable { onOpen(title, "home-hero") }) {
                    Artwork(title, Modifier.fillMaxSize(), backdrop = true)
                    Box(
                        Modifier.matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = .03f),
                                    .2f to Color.Black.copy(alpha = .03f),
                                    .44f to Color.Black.copy(alpha = .25f),
                                    1f to Color.Black.copy(alpha = .92f),
                                )
                            )
                    )
                    Column(
                        Modifier.align(Alignment.BottomStart)
                            .padding(start = 20.dp, end = 20.dp, bottom = 48.dp),
                        verticalArrangement = Arrangement.Top,
                    ) {
                        Text(
                            title.name,
                            fontFamily = BodyFont,
                            fontWeight = FontWeight.Bold,
                            fontSize =
                                (LocalConfiguration.current.screenWidthDp * .07f)
                                    .coerceIn(26f, 40f)
                                    .sp,
                            lineHeight =
                                ((LocalConfiguration.current.screenWidthDp * .07f).coerceIn(
                                        26f,
                                        40f,
                                    ) * 1.1f)
                                    .sp,
                            letterSpacing = (-.025).em,
                            color = Color.White,
                            maxLines = 2,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (title.genres.isNotEmpty() || title.certificate.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (title.genres.isNotEmpty())
                                    Text(
                                        title.genres.take(2).joinToString(" · "),
                                        fontSize = 10.sp,
                                        lineHeight = 15.sp,
                                        color = Color.White.copy(alpha = .8f),
                                    )
                                if (title.certificate.isNotBlank())
                                    Text(
                                        title.certificate,
                                        Modifier.border(
                                                1.dp,
                                                Color.White.copy(alpha = 101f / 255),
                                                RoundedCornerShape(4.dp),
                                            )
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                        fontFamily =
                                            androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        lineHeight = 11.7.sp,
                                        color = Color.White,
                                    )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                title.year,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = Color.White,
                            )
                            RatingBadge(title, hero = true)
                        }
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                media.indices.forEach { i ->
                    Box(
                        Modifier.size(36.dp, 44.dp).clickable {
                            scope.launch {
                                val forward = (i - activeIndex + media.size) % media.size
                                val delta =
                                    if (forward > media.size / 2) forward - media.size else forward
                                if (reduced) pager.scrollToPage(pager.currentPage + delta)
                                else pager.animateScrollToPage(pager.currentPage + delta)
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(if (activeIndex == i) 20.dp else 4.dp, 4.dp)
                                .background(
                                    Color.White.copy(alpha = if (activeIndex == i) 1f else .5f),
                                    CircleShape,
                                )
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "%02d / %02d".format(activeIndex + 1, media.size),
                    fontFamily = MonoFont,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = .7f),
                )
            }
        }
    }
}

@Composable
fun MediaRail(title: String, media: List<Media>, source: String, onOpen: (Media, String) -> Unit) {
    val navigate = LocalNavigate.current
    Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title.uppercase(),
                Modifier.weight(1f),
                fontFamily = SectionFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                lineHeight = 21.75.sp,
                letterSpacing = .7.sp,
            )
            Text(
                "SEE ALL ›",
                Modifier.clickable { navigate("discover") }.padding(vertical = 12.dp),
                fontFamily = MonoFont,
                fontSize = 10.sp,
                color = Muted,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = (maxWidth - 20.dp) / 3
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(media.distinctBy { it.key }, key = { it.key }) {
                    PosterCard(it, source, Modifier.width(width), onOpen)
                }
            }
        }
    }
}

@Composable
fun SearchEntry(value: String, onSearch: (String) -> Unit, readOnly: Boolean = false) {
    OriginalField(
        value,
        if (readOnly) ({ _: String -> }) else onSearch,
        "Movie, series or IMDb ID",
        Modifier.height(if (readOnly) 44.dp else 48.dp),
        icon = StakIcons.Search,
        horizontalInset = 20.dp,
    )
}

@Composable
fun StakRefresh(refreshing: Boolean, onRefresh: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        refreshing,
        onRefresh,
        Modifier.fillMaxSize(),
        state = state,
        indicator = {
            if (refreshing || state.distanceFraction > 0f)
                Surface(
                    Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                    shape = CircleShape,
                ) {
                    DotStatus(
                        Modifier.padding(12.dp),
                        "Refreshing",
                        if (refreshing) null else state.distanceFraction.coerceIn(0f, 1f),
                    )
                }
        },
        content = { content() },
    )
}

@Composable
fun DiscoverScreen(vm: StakViewModel, onOpen: (Media, String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableIntStateOf(0) }
    var category by rememberSaveable { mutableIntStateOf(0) }
    var results by remember { mutableStateOf(emptyList<Media>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var filterSheet by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var providers by remember { mutableStateOf(emptyList<org.json.JSONObject>()) }
    var providerError by remember { mutableStateOf("") }
    var serviceSheet by remember { mutableStateOf(false) }
    var serviceQuery by remember { mutableStateOf("") }
    val channels =
        listOf("Popular", "Trending", "Top rated", "New releases", "Upcoming", "Hidden gems")
    val ids = listOf("popular", "trending", "top", "new", "upcoming", "hidden")
    val kind = if (type == 0) "movie" else "series"
    LaunchedEffect(type) {
        if (vm.repository.credentials.get("tmdb_key").isBlank()) return@LaunchedEffect
        try {
            providers =
                vm.repository
                    .tmdb(
                        "/watch/providers/${if (type == 0) "movie" else "tv"}",
                        mapOf("watch_region" to "US"),
                    )
                    .optJSONArray("results")
                    .objects()
            providerError = ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            providerError = e.message.orEmpty()
        }
    }
    LaunchedEffect(query, type, category, filters, page) {
        loading = true
        error = ""
        try {
            if (query.isNotBlank()) delay(350)
            val titles = vm.repository.browse(kind, ids[category], query, page, filters)
            results = if (page == 1) titles else (results + titles).distinctBy { it.key }
            hasMore = titles.isNotEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message.orEmpty()
            if (page == 1) results = emptyList()
        } finally {
            loading = false
        }
    }
    val brands = listOf("Apple TV", "Netflix", "Max", "Disney", "Hulu", "Prime Video", "Paramount")
    val availableBrands =
        brands
            .mapNotNull { brand ->
                providers.firstOrNull { provider ->
                    val name = provider.optString("provider_name")
                    when (brand) {
                        "Apple TV" ->
                            name.matches(Regex("Apple TV(?:\\+| Plus)?", RegexOption.IGNORE_CASE))
                        "Max" -> name in listOf("Max", "HBO Max")
                        "Disney" -> name.startsWith("Disney")
                        "Prime Video" -> name.contains("Prime Video")
                        else -> name.startsWith(brand)
                    }
                }
            }
            .distinctBy { it.optInt("provider_id") }
    LazyColumn(
        Modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PageHeading("Discover", "Find your next watch") {
                    MotionButton("Filters", icon = StakIcons.SlidersHorizontal) {
                        filterSheet = true
                    }
                }
                SearchEntry(
                    query,
                    {
                        query = it
                        page = 1
                    },
                )
                if (query.isBlank())
                    MotionTabs(listOf("Movies", "Series"), type) {
                        type = it
                        page = 1
                    }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item {
                        Surface(
                            onClick = { serviceSheet = true },
                            shape = RoundedCornerShape(13.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, Rule),
                        ) {
                            Row(
                                Modifier.height(48.dp).padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    providers
                                        .firstOrNull {
                                            it.optInt("provider_id").toString() ==
                                                filters["provider"]
                                        }
                                        ?.optString("provider_name") ?: "All services",
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                )
                                Icon(
                                    StakIcons.ChevronDown,
                                    null,
                                    Modifier.size(14.dp),
                                    tint = Muted,
                                )
                            }
                        }
                    }
                    items(availableBrands, key = { it.optInt("provider_id") }) { provider ->
                        val id = provider.optInt("provider_id").toString()
                        Box(
                            Modifier.size(48.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .border(
                                    1.dp,
                                    if (filters["provider"] == id) Accent else Rule,
                                    RoundedCornerShape(13.dp),
                                )
                                .clickable {
                                    filters =
                                        filters +
                                            ("provider" to
                                                if (filters["provider"] == id) "" else id)
                                    page = 1
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                "https://image.tmdb.org/t/p/w92${provider.optString("logo_path")}",
                                provider.optString("provider_name"),
                                Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Fit,
                            )
                            if (filters["provider"] == id)
                                Icon(
                                    StakIcons.Check,
                                    "Selected service",
                                    Modifier.align(Alignment.BottomEnd).size(14.dp),
                                    tint = Accent,
                                )
                        }
                    }
                }
                if (providerError.isNotBlank())
                    Text(providerError, fontSize = 12.sp, lineHeight = 18.sp, color = Muted)
                if (query.isBlank())
                    Column {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            itemsIndexed(channels) { index, label ->
                                Column(
                                    Modifier.clickable {
                                        category = index
                                        page = 1
                                    }
                                ) {
                                    Box(
                                        Modifier.height(42.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            label,
                                            fontSize = 12.sp,
                                            lineHeight = 15.6.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color =
                                                if (index == category)
                                                    MaterialTheme.colorScheme.onSurface
                                                else Muted,
                                        )
                                    }
                                    Box(
                                        Modifier.height(2.dp)
                                            .width((label.length * 6.4f).dp)
                                            .background(
                                                if (index == category) Accent else Color.Transparent
                                            )
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = Rule)
                    }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (query.isNotBlank()) "Search across movies & series"
                        else if (type == 0) "Movies to explore" else "Series to explore",
                        Modifier.weight(1f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Muted,
                    )
                    Choice(
                        "Sort",
                        filters["sort"].orEmpty(),
                        listOf(
                            "" to "Recommended",
                            "popularity.desc" to "Popularity",
                            "vote_average.desc" to "Rating",
                            "primary_release_date.desc" to "Release date",
                            "title.asc" to "Title A–Z",
                        ),
                        {
                            filters = filters + ("sort" to it)
                            page = 1
                        },
                        Modifier.width(190.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        if (error.isNotBlank())
            item {
                EmptyState(error) {
                    page = 1
                    filters = emptyMap()
                    category = 0
                }
            }
        if (loading && results.isEmpty())
            item {
                PosterSkeletons()
                DotStatus(label = "Loading discovery")
            }
        items(results.chunked(3), key = { it.first().key }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { PosterCard(it, "discover", Modifier.weight(1f), onOpen) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(22.dp))
        }
        if (hasMore)
            item {
                MotionButton(
                    if (loading) "Loading…" else "Load more",
                    Modifier.fillMaxWidth(),
                    enabled = !loading,
                ) {
                    page++
                }
            }
        item {
            Spacer(Modifier.height(20.dp))
            Text(
                "TMDb / JustWatch · US",
                fontFamily = MonoFont,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = Muted,
            )
        }
    }
    if (filterSheet)
        DiscoveryFilterSheet(filters, { filterSheet = false }) {
            filters = it
            page = 1
            filterSheet = false
        }
    if (serviceSheet)
        StakBottomSheet({ serviceSheet = false }) {
            LazyColumn(
                Modifier.fillMaxWidth().fillMaxHeight(.75f).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item {
                    Text("Streaming services", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Availability follows your saved region.",
                        fontSize = 13.sp,
                        lineHeight = 19.5.sp,
                        color = Muted,
                    )
                }
                item { OriginalField(serviceQuery, { serviceQuery = it }, "Find a service") }
                item {
                    MotionButton("All services", Modifier.fillMaxWidth()) {
                        filters = filters + ("provider" to "")
                        page = 1
                        serviceSheet = false
                        serviceQuery = ""
                    }
                }
                items(
                    providers.filter { it.optString("provider_name").contains(serviceQuery, true) },
                    key = { it.optInt("provider_id") },
                ) { provider ->
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).clickable {
                            filters =
                                filters + ("provider" to provider.optInt("provider_id").toString())
                            page = 1
                            serviceSheet = false
                            serviceQuery = ""
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AsyncImage(
                            "https://image.tmdb.org/t/p/w92${provider.optString("logo_path")}",
                            null,
                            Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                        )
                        Text(
                            provider.optString("provider_name"),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
        }
}

@Composable
fun DiscoveryFilterSheet(
    filters: Map<String, String>,
    onDismiss: () -> Unit,
    apply: (Map<String, String>) -> Unit,
) {
    var draft by remember { mutableStateOf(filters) }
    StakBottomSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Filters", style = MaterialTheme.typography.headlineSmall)
            Label("Genre")
            Choice(
                "Genre",
                draft["genre"].orEmpty(),
                listOf(
                    "" to "All genres",
                    "28" to "Action",
                    "12" to "Adventure",
                    "16" to "Animation",
                    "35" to "Comedy",
                    "80" to "Crime",
                    "99" to "Documentary",
                    "18" to "Drama",
                    "10751" to "Family",
                    "14" to "Fantasy",
                    "27" to "Horror",
                    "9648" to "Mystery",
                    "10749" to "Romance",
                    "878" to "Science Fiction",
                    "53" to "Thriller",
                ),
                { draft = draft + ("genre" to it) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OriginalField(
                    draft["year_from"].orEmpty(),
                    { draft = draft + ("year_from" to it) },
                    "From year",
                    Modifier.weight(1f),
                )
                OriginalField(
                    draft["year_to"].orEmpty(),
                    { draft = draft + ("year_to" to it) },
                    "To year",
                    Modifier.weight(1f),
                )
            }
            Label("Original language")
            Choice(
                "Original language",
                draft["language"].orEmpty(),
                listOf(
                    "" to "Any language",
                    "en" to "English",
                    "es" to "Spanish",
                    "fr" to "French",
                    "de" to "German",
                    "ja" to "Japanese",
                    "ko" to "Korean",
                ),
                { draft = draft + ("language" to it) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MotionButton("Reset filters", Modifier.weight(1f)) { draft = emptyMap() }
                MotionButton(
                    "Show results",
                    Modifier.weight(1f),
                    primary = true,
                    enabled =
                        listOf("year_from", "year_to").all {
                            draft[it].isNullOrBlank() || draft[it]?.toIntOrNull() in 1880..2100
                        } &&
                            (draft["year_from"].isNullOrBlank() ||
                                draft["year_to"].isNullOrBlank() ||
                                draft.getValue("year_from") <= draft.getValue("year_to")),
                ) {
                    apply(draft)
                }
            }
        }
    }
}

@Composable
fun WatchlistScreen(vm: StakViewModel, saved: List<SavedMedia>, onOpen: (Media, String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableIntStateOf(0) }
    var layout by rememberSaveable { mutableIntStateOf(0) }
    var filters by remember { mutableStateOf(false) }
    var hideWatched by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("added") }
    val list = saved.filter { it.collection == "watchlist" }
    val watched =
        saved
            .filter {
                it.collection == "tracking" && org.json.JSONObject(it.json).optBoolean("played")
            }
            .map { it.mediaKey }
            .toSet()
    val media =
        list
            .map { it.media() }
            .filter {
                (type == 0 || it.type == if (type == 1) "movie" else "series") &&
                    it.name.contains(query, true) &&
                    (!hideWatched || it.key !in watched)
            }
            .let { items ->
                when (sort) {
                    "name" -> items.sortedBy { it.name }
                    "year" -> items.sortedByDescending { it.year }
                    "rating" -> items.sortedByDescending { it.rating.toFloatOrNull() ?: 0f }
                    else -> items
                }
            }
    val navigate = LocalNavigate.current
    LazyVerticalGrid(
        GridCells.Fixed(if (layout == 0) 3 else 1),
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            PageHeading("Watchlist") {
                StakIconButton(StakIcons.RefreshCw, "Refresh watchlist") {
                    vm.operation { vm.messages.emit("Watchlist is up to date.") }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "${list.size} saved ${if (list.size == 1) "title" else "titles"}",
                fontFamily = MonoFont,
                fontSize = 11.sp,
                color = Muted,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OriginalField(
                    query,
                    { query = it },
                    "Search watchlist",
                    Modifier.weight(1f),
                    StakIcons.Search,
                )
                StakIconButton(StakIcons.SlidersHorizontal, "Filter watchlist") { filters = true }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                listOf("All", "Movies", "Series").forEachIndexed { index, label ->
                    Box(
                        Modifier.height(44.dp)
                            .background(
                                if (index == type) Soft else Color.Transparent,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { type = index }
                            .padding(horizontal = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label.uppercase(),
                            fontFamily = MonoFont,
                            fontSize = 10.sp,
                            color =
                                if (index == type) MaterialTheme.colorScheme.onSurface else Muted,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton({ layout = 0 }) {
                    Icon(StakIcons.LayoutGrid, "Grid view", Modifier.size(18.dp))
                }
                IconButton({ layout = 1 }) {
                    Icon(StakIcons.List, "List view", Modifier.size(18.dp))
                }
            }
        }
        if (list.isEmpty())
            item(span = { GridItemSpan(maxLineSpan) }) {
                OriginalEmpty(
                    "Your next watch starts here",
                    "Save a movie or series from Discover to keep it here.",
                    "Browse Discover",
                ) {
                    navigate("discover")
                }
            }
        else if (media.isEmpty())
            item(span = { GridItemSpan(maxLineSpan) }) {
                OriginalEmpty(
                    "No matching titles",
                    "Try another search or clear your filters.",
                    "Clear filters",
                ) {
                    query = ""
                    type = 0
                    hideWatched = false
                }
            }
        items(media, key = { it.key }) {
            if (layout == 0) PosterCard(it, "watchlist", onOpen = onOpen)
            else MediaListRow(it, "watchlist", onOpen)
        }
    }
    if (filters)
        StakBottomSheet({ filters = false }) {
            Column(
                Modifier.padding(20.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Watchlist filters", style = MaterialTheme.typography.headlineSmall)
                Label("Sort by")
                Choice(
                    "Sort by",
                    sort,
                    listOf(
                        "added" to "Date added",
                        "year" to "Year",
                        "rating" to "IMDb rating",
                        "name" to "Name",
                    ),
                    { sort = it },
                )
                OriginalToggle("Hide watched", hideWatched) { hideWatched = it }
                MotionButton("Reset filters") {
                    sort = "added"
                    hideWatched = false
                }
                MotionButton("Done", Modifier.fillMaxWidth(), primary = true) { filters = false }
            }
        }
}

@Composable
fun MediaListRow(media: Media, source: String, onOpen: (Media, String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(media, source) }.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SharedPoster(media, source, Modifier.width(60.dp).aspectRatio(2f / 3f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(media.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(
                "${media.year} · ${if (media.type == "movie") "Movie" else "Series"}",
                fontFamily = MonoFont,
                fontSize = 10.sp,
                color = Muted,
            )
            RatingBadge(media, true)
        }
        Icon(StakIcons.ChevronRight, null, Modifier.size(16.dp), tint = Muted)
    }
}

@Composable
fun DetailScreen(
    vm: StakViewModel,
    key: String,
    source: String,
    saved: List<SavedMedia>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val initial = remember(key) { vm.media(key) }
    if (initial == null) {
        EmptyState("This title is unavailable.", onBack)
        return
    }
    var media by remember(key) { mutableStateOf(initial) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var season by rememberSaveable {
        mutableIntStateOf(initial.episodes.firstOrNull()?.season ?: 1)
    }
    var selected by remember { mutableStateOf<Episode?>(null) }
    var sources by remember { mutableStateOf(false) }
    var sourceEpisode by remember { mutableStateOf<Episode?>(null) }
    val episodes by vm.watchedEpisodes.collectAsStateWithLifecycle()
    val watched =
        saved.any {
            it.mediaKey == media.key &&
                it.collection == "tracking" &&
                org.json.JSONObject(it.json).optBoolean("played")
        }
    val watchlisted = saved.any { it.mediaKey == media.key && it.collection == "watchlist" }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cinematicColors = LocalOptions.current["cinematicColors"] != "false"
    LaunchedEffect(media.background, cinematicColors) {
        vm.artworkTone.value =
            if (cinematicColors) vm.repository.artworkTone(context, media) else GRAPHITE_TONE
    }
    DisposableEffect(key) { onDispose { vm.artworkTone.value = GRAPHITE_TONE } }
    val titleEpisodes = media.episodes.filter { it.season > 0 }
    val count =
        episodes.count {
            it.mediaKey == media.key &&
                titleEpisodes.any { ep -> ep.season == it.season && ep.number == it.episode }
        }
    val next =
        titleEpisodes.firstOrNull { ep ->
            episodes.none {
                it.mediaKey == media.key && it.season == ep.season && it.episode == ep.number
            }
        }
    val navigate = LocalNavigate.current
    LaunchedEffect(key, retry) {
        try {
            media = vm.repository.details(initial)
            error = ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message.orEmpty()
        } finally {
            loading = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.testTag("title-scroll"),
            contentPadding = PaddingValues(bottom = 112.dp),
        ) {
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .height(if (media.background.isBlank()) 220.dp else 380.dp)
                ) {
                    if (media.background.isNotBlank()) {
                        Artwork(
                            media,
                            Modifier.fillMaxSize()
                                .graphicsLayer {
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            .08f to Color.Black,
                                            .38f to Color.Black,
                                            .58f to Color.Black.copy(alpha = .73f),
                                            .82f to Color.Black.copy(alpha = .2f),
                                            1f to Color.Transparent,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                },
                            backdrop = true,
                        )
                    }
                    StakIconButton(
                        StakIcons.ArrowLeft,
                        "Back",
                        Modifier.align(Alignment.TopStart)
                            .padding(start = 16.dp, top = 12.dp)
                            .background(
                                MaterialTheme.colorScheme.background.copy(alpha = .85f),
                                CircleShape,
                            ),
                        onClick = onBack,
                    )
                    Column(
                        Modifier.align(Alignment.BottomStart)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            media.name,
                            fontFamily = DotFont,
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp,
                            lineHeight = 38.sp,
                        )
                        Text(
                            listOf(media.year, media.runtime)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            fontFamily = MonoFont,
                            fontSize = 10.sp,
                            color = Muted,
                        )
                        RatingBadge(media, quiet = true)
                        if (media.genres.isNotEmpty())
                            Text(media.genres.joinToString(" · "), fontSize = 10.sp, color = Muted)
                    }
                }
            }
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MotionButton("Watch trailer", icon = StakIcons.Play) {
                        openExternal(
                            context,
                            if (media.trailer.isNotBlank())
                                "https://www.youtube.com/watch?v=${Uri.encode(media.trailer)}"
                            else
                                "https://www.youtube.com/results?search_query=" +
                                    Uri.encode("${media.name} official trailer"),
                        )
                    }
                    MotionButton(
                        "Watchlist",
                        icon = if (watchlisted) StakIcons.Check else StakIcons.Bookmark,
                    ) {
                        vm.toggle(media, "watchlist")
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .heightIn(min = 36.dp)
                        .clickable {
                            if (media.jellyfinId.isBlank()) navigate("settings?section=jellyfin")
                            else
                                openExternal(
                                    context,
                                    vm.repository.credentials.get("jellyfin_url").trimEnd('/') +
                                        "/web/#/details?id=" +
                                        Uri.encode(media.jellyfinId),
                                )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(StakIcons.Jellyfin, null, Modifier.size(17.dp), tint = Muted)
                    Text(
                        if (media.jellyfinId.isBlank()) "Jellyfin · Disconnected"
                        else "Available in Jellyfin",
                        fontSize = 12.sp,
                        color = Muted,
                    )
                }
            }
            item {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    if (titleEpisodes.isNotEmpty()) {
                        Text(
                            "$count of ${titleEpisodes.size} watched",
                            fontFamily = MonoFont,
                            fontSize = 11.sp,
                            color = Muted,
                        )
                        SegmentedProgress(count.toFloat() / titleEpisodes.size)
                        Text(
                            "${count * 100 / titleEpisodes.size}% complete · ${titleEpisodes.size - count} remaining",
                            fontFamily = MonoFont,
                            fontSize = 11.sp,
                            color = Muted,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MotionButton(
                                if (next == null) "Mark unwatched"
                                else "Mark watched · S%02d E%02d".format(next.season, next.number),
                                icon = StakIcons.Check,
                            ) {
                                vm.operation {
                                    if (next != null) {
                                        vm.changeEpisodesWatched(media, listOf(next), true)
                                        if (!watchlisted) vm.toggle(media, "watchlist")
                                    } else vm.changeEpisodesWatched(media, titleEpisodes, false)
                                }
                            }
                            MotionButton("Mark S%02d watched".format(season)) {
                                vm.operation {
                                    vm.changeEpisodesWatched(
                                        media,
                                        titleEpisodes.filter { it.season == season },
                                        true,
                                    )
                                }
                            }
                        }
                    } else
                        MotionButton(
                            if (watched) "Watched" else "Mark watched",
                            icon = StakIcons.Check,
                        ) {
                            vm.operation {
                                val previous = vm.changeWatched(media, !watched)
                                if (
                                    snackbar.showSnackbar(
                                        if (watched) "Marked unwatched" else "Marked watched",
                                        "Undo",
                                    ) == SnackbarResult.ActionPerformed
                                )
                                    vm.restoreWatched(media, previous)
                            }
                        }
                    HorizontalDivider(color = Rule)
                }
            }
            if (loading) item { DotStatus(Modifier.padding(16.dp), "Loading title details") }
            if (error.isNotBlank()) item { EmptyState(error) { retry++ } }
            if (media.description.isNotBlank())
                item {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Label("Synopsis")
                        Text(
                            media.description,
                            fontSize = 14.sp,
                            lineHeight = 25.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .83f),
                        )
                    }
                }
            if (titleEpisodes.isNotEmpty())
                item {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        HorizontalDivider(color = Rule)
                        Label("Season")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(titleEpisodes.map { it.season }.distinct()) { s ->
                                MotionButton("S%02d".format(s), primary = s == season) {
                                    season = s
                                }
                            }
                        }
                        val inSeason = titleEpisodes.filter { it.season == season }
                        Label("Episodes · ${inSeason.size}")
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val width = (maxWidth - 16.dp) / 3
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(inSeason) { ep ->
                                    Column(
                                        Modifier.width(width).clickable { selected = ep },
                                        verticalArrangement = Arrangement.spacedBy(9.dp),
                                    ) {
                                        Box {
                                            AsyncImage(
                                                ep.image,
                                                null,
                                                Modifier.fillMaxWidth()
                                                    .aspectRatio(4f / 3f)
                                                    .clip(RoundedCornerShape(9.dp))
                                                    .background(Soft),
                                                contentScale = ContentScale.Crop,
                                            )
                                            if (
                                                episodes.any {
                                                    it.mediaKey == media.key &&
                                                        it.season == ep.season &&
                                                        it.episode == ep.number
                                                }
                                            )
                                                Icon(
                                                    StakIcons.Check,
                                                    "Watched",
                                                    Modifier.align(Alignment.BottomEnd)
                                                        .padding(5.dp)
                                                        .size(16.dp),
                                                    tint = Color.White,
                                                )
                                        }
                                        Text(
                                            "${ep.number}. ${ep.name}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            lineHeight = 18.sp,
                                            maxLines = 2,
                                            minLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            ep.released.take(10),
                                            fontFamily = MonoFont,
                                            fontSize = 9.sp,
                                            color = Muted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            if (media.crew.isNotEmpty())
                item {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        HorizontalDivider(color = Rule)
                        Text("BEHIND THE CAMERA", fontFamily = DotFont, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            media.crew.take(3).forEach { person ->
                                Column(
                                    Modifier.weight(1f)
                                        .border(1.dp, Rule, RoundedCornerShape(10.dp))
                                        .clickable { navigate("person/${person.id}") }
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(person.name, fontSize = 12.sp)
                                    Text(person.role, fontSize = 11.sp, color = Muted)
                                }
                            }
                        }
                    }
                }
            if (media.cast.isNotEmpty())
                item {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("CAST", fontFamily = DotFont, fontSize = 14.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(media.cast) { person ->
                                Column(
                                    Modifier.width(118.dp).clickable {
                                        navigate("person/${person.id}")
                                    }
                                ) {
                                    AsyncImage(
                                        person.image,
                                        null,
                                        Modifier.fillMaxWidth()
                                            .aspectRatio(.75f)
                                            .clip(RoundedCornerShape(9.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                    Text(person.name, fontSize = 12.sp)
                                    Text(person.role, fontSize = 11.sp, color = Muted, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            if (media.gallery.isNotEmpty())
                item {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        HorizontalDivider(color = Rule)
                        Text("GALLERY", fontFamily = DotFont, fontSize = 14.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(media.gallery) {
                                AsyncImage(
                                    it,
                                    null,
                                    Modifier.width(118.dp)
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(9.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
        }
        Surface(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Rule),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(Modifier.padding(4.dp)) {
                Surface(
                    onClick = {
                        sourceEpisode = null
                        sources = true
                    },
                    shape = RoundedCornerShape(11.dp),
                    color = Accent,
                ) {
                    Row(
                        Modifier.padding(horizontal = 20.dp).height(44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(StakIcons.Plus, null, Modifier.size(16.dp), tint = Color.White)
                        Text(
                            "FIND SOURCES",
                            fontFamily = MonoFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
    selected?.let { ep ->
        EpisodeDetails(vm, media, ep, { selected = null }) {
            selected = null
            sourceEpisode = ep
            sources = true
        }
    }
    if (sources) SourcesSheet(vm, media, sourceEpisode) { sources = false }
}

@Composable
fun EpisodeDetails(
    vm: StakViewModel,
    media: Media,
    episode: Episode,
    dismiss: () -> Unit,
    findSources: () -> Unit,
) {
    val watched by vm.watchedEpisodes.collectAsStateWithLifecycle()
    val marked =
        watched.any {
            it.mediaKey == media.key && it.season == episode.season && it.episode == episode.number
        }
    StakBottomSheet(dismiss) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = 500.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row {
                Text(episode.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(dismiss) { Icon(StakIcons.X, "Close dialog", Modifier.size(18.dp)) }
            }
            Label(
                "S%02d E%02d · %s".format(episode.season, episode.number, episode.released.take(10))
            )
            if (episode.overview.isNotBlank())
                Text(episode.overview, fontSize = 14.sp, lineHeight = 23.sp)
            MotionButton(
                if (marked) "Mark unwatched" else "Mark watched",
                Modifier.fillMaxWidth(),
            ) {
                vm.operation { vm.changeEpisodesWatched(media, listOf(episode), !marked) }
            }
            MotionButton(
                "Find sources",
                Modifier.fillMaxWidth(),
                primary = true,
                onClick = findSources,
            )
        }
    }
}

@Composable
fun SourcesSheet(vm: StakViewModel, media: Media, episode: Episode?, dismiss: () -> Unit) {
    var results by remember { mutableStateOf(emptyList<SourceStream>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(retry) {
        loading = true
        try {
            results = vm.repository.sources(media, episode)
            error = ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message.orEmpty()
        } finally {
            loading = false
        }
    }
    StakBottomSheet(dismiss) {
        LazyColumn(
            Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SharedPoster(media, "source", Modifier.size(42.dp, 63.dp))
                    Column(Modifier.weight(1f)) {
                        Text(media.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            listOf(
                                    media.year,
                                    episode?.let { "S%02d E%02d".format(it.season, it.number) },
                                )
                                .filterNotNull()
                                .joinToString(" · "),
                            fontFamily = MonoFont,
                            fontSize = 10.sp,
                            color = Muted,
                        )
                    }
                    IconButton(dismiss) { Icon(StakIcons.X, "Close dialog", Modifier.size(18.dp)) }
                }
            }
            if (loading) item { DotStatus(label = "Searching sources") }
            if (error.isNotBlank()) item { EmptyState(error) { retry++ } }
            if (!loading && results.isEmpty() && error.isBlank())
                item { EmptyState("No sources found.") }
            items(results) { source ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Rule),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Label(source.name)
                        Text(
                            source.title,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MotionButton("Add to TorBox") {
                            vm.operation {
                                vm.repository.addMagnet("magnet:?xt=urn:btih:${source.hash}")
                                vm.messages.emit("Added to TorBox.")
                            }
                        }
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
            context.startActivity(
                Intent(Intent.ACTION_VIEW, parsed).apply {
                    if (video) setDataAndType(parsed, "video/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
        .onFailure {
            android.widget.Toast.makeText(
                    context,
                    "No compatible app found to open this link.",
                    android.widget.Toast.LENGTH_SHORT,
                )
                .show()
        }
}
