@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.stak.nativeapp

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Composable
fun TrackingScreen(
    vm: StakViewModel,
    saved: List<SavedMedia>,
    onOpen: (Media, String) -> Unit,
    initialTab: Int = 0,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var refresh by remember { mutableIntStateOf(0) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(bottom = 124.dp),
    ) {
        item {
            Box(Modifier.padding(horizontal = 4.dp)) {
                PageHeading(
                    "Tracking",
                    if (tab == 0) "Your viewing journal" else "Episodes & releases",
                ) {
                    StakIconButton(
                        StakIcons.RefreshCw,
                        if (tab == 0) "Refresh activity" else "Refresh calendar",
                    ) {
                        refresh++
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        stickyHeader {
            Column {
                MotionTabs(listOf("Activity", "Calendar"), tab, height = 56.dp) { tab = it }
                Spacer(Modifier.height(16.dp))
            }
        }
        item {
            if (tab == 0) ActivityScreen(vm, saved, onOpen)
            else CalendarScreen(vm, saved, refresh, onOpen)
        }
    }
}

@Composable
private fun ActivityScreen(
    vm: StakViewModel,
    saved: List<SavedMedia>,
    onOpen: (Media, String) -> Unit,
) {
    var type by rememberSaveable { mutableIntStateOf(0) }
    var period by rememberSaveable { mutableStateOf("all") }
    var limit by rememberSaveable { mutableIntStateOf(40) }
    var details by remember { mutableStateOf(false) }
    var insight by rememberSaveable { mutableStateOf(false) }
    var selectedDay by rememberSaveable { mutableStateOf("") }
    val navigate = LocalNavigate.current
    val watched by vm.watchedEpisodes.collectAsStateWithLifecycle()
    val history =
        (saved.filter {
                it.collection == "tracking" && org.json.JSONObject(it.json).optBoolean("played")
            } +
                watched
                    .groupBy { it.mediaKey }
                    .mapNotNull { (key, marks) ->
                        val title = vm.media(key) ?: return@mapNotNull null
                        SavedMedia(
                            key,
                            "tracking",
                            title.json().toString(),
                            marks.maxOf { it.watchedAt },
                        )
                    })
            .groupBy { it.mediaKey }
            .map { (_, rows) -> rows.maxBy { it.updatedAt } }
            .sortedByDescending { it.updatedAt }
    val cutoff =
        if (period == "all") 0L
        else
            vm.repository.today
                .minusDays(if (period == "week") 6 else 29)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
    val filtered = history.filter { it.updatedAt >= cutoff }
    val episodes = watched.filter { it.watchedAt >= cutoff }
    val summary =
        viewingSummary(
            filtered.filter { it.media().type == "movie" }.map { it.media() },
            episodes.map { vm.media(it.mediaKey) },
        )
    val visible =
        filtered.filter {
            (type == 0 || it.media().type == if (type == 1) "movie" else "series") &&
                (selectedDay.isBlank() ||
                    Instant.ofEpochMilli(it.updatedAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .toString() == selectedDay)
        }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .74f),
            border = BorderStroke(1.dp, Rule),
        ) {
            Column(Modifier.padding(17.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Choice(
                        "Activity period",
                        period,
                        listOf(
                            "all" to "All time",
                            "week" to "Last 7 days",
                            "month" to "Last 30 days",
                        ),
                        {
                            period = it
                            selectedDay = ""
                            limit = 40
                        },
                        Modifier.width(190.dp),
                        plain = true,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton({ details = true }, Modifier.size(48.dp)) {
                        Icon(StakIcons.Info, "About activity", Modifier.size(17.dp))
                    }
                }
                val size = (viewportWidthDp() * .08f).coerceIn(26f, 38f)
                Row(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
                    listOf(
                            summary.movies.toString() to "Movies",
                            summary.episodes.toString() to "Episodes",
                            summary.runtimeValue to summary.runtimeLabel,
                        )
                        .forEachIndexed { index, (value, label) ->
                            Row(Modifier.weight(1f).height(IntrinsicSize.Min)) {
                                if (index > 0) {
                                    Box(Modifier.width(1.dp).fillMaxHeight().background(Rule))
                                    Spacer(Modifier.width(14.dp))
                                }
                                Column(
                                    Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        value,
                                        fontFamily = DotFont,
                                        fontWeight = FontWeight.Black,
                                        fontSize = size.sp,
                                        lineHeight = (size * 1.1f).sp,
                                    )
                                    Spacer(Modifier.height(13.dp))
                                    Text(
                                        label,
                                        fontFamily = MonoFont,
                                        fontSize = 10.sp,
                                        lineHeight = 15.sp,
                                        color = Muted,
                                    )
                                }
                            }
                        }
                }
                HorizontalDivider(color = Rule)
                Row(
                    Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Manual history",
                        Modifier.weight(1f),
                        fontSize = 11.sp,
                        lineHeight = 16.5.sp,
                        color = Muted,
                    )
                    Text(
                        "Connect Jellyfin",
                        Modifier.clickable { navigate("settings?section=jellyfin") }
                            .padding(horizontal = 4.dp, vertical = 14.dp),
                        fontSize = 12.sp,
                        lineHeight = 16.8.sp,
                        color = Accent,
                    )
                }
            }
        }
        Column(
            Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (selectedDay.isBlank()) "History" else "Watched $selectedDay",
                    fontFamily = DotFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    lineHeight = 25.2.sp,
                )
                Text(
                    visible.size.toString(),
                    fontFamily = MonoFont,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Muted,
                )
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("All", "Movies", "Series").forEachIndexed { index, label ->
                    Column(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (type == index) Soft else Color.Transparent)
                            .clickable {
                                type = index
                                limit = 40
                            }
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(46.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                fontSize = 12.sp,
                                lineHeight = 16.8.sp,
                                fontWeight = FontWeight.SemiBold,
                                color =
                                    if (type == index) MaterialTheme.colorScheme.onSurface
                                    else Muted,
                            )
                        }
                        Box(
                            Modifier.fillMaxWidth()
                                .height(2.dp)
                                .background(if (type == index) Accent else Color.Transparent)
                        )
                    }
                }
            }
            HorizontalDivider(color = Rule)
            if (selectedDay.isNotBlank()) MotionButton("Show all dates") { selectedDay = "" }
        }
        if (visible.isEmpty())
            OriginalEmpty(
                if (history.isEmpty()) "Nothing watched yet" else "No activity in this selection",
                if (history.isEmpty())
                    "Mark a title as watched or connect Jellyfin to populate your journal."
                else "Choose another period or media type.",
                if (history.isEmpty()) "Connect Jellyfin" else "Show loaded history",
                compact = true,
            ) {
                if (history.isEmpty()) navigate("settings?section=jellyfin")
                else {
                    period = "all"
                    type = 0
                    selectedDay = ""
                }
            }
        visible
            .take(limit)
            .groupBy {
                if (it.updatedAt == 0L) null
                else Instant.ofEpochMilli(it.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            .forEach { (date, rows) ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label(date?.format(DateTimeFormatter.ofPattern("EEE, MMM d")) ?: "Date unknown")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.forEach { row ->
                            val title = vm.media(row.mediaKey) ?: row.media()
                            Surface(
                                onClick = { onOpen(title, "tracking") },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = .74f),
                                border = BorderStroke(1.dp, Rule),
                            ) {
                                Row(
                                    Modifier.heightIn(min = 70.dp)
                                        .padding(horizontal = 11.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SharedPoster(title, "tracking", Modifier.size(35.dp, 52.dp))
                                    Column(
                                        Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            title.name,
                                            fontSize = 13.sp,
                                            lineHeight = 17.55.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            if (title.type == "series")
                                                "${watched.count { it.mediaKey == title.key }}${if (title.totalEpisodes > 0) " / ${title.totalEpisodes}" else ""} episodes watched"
                                            else title.year.ifBlank { "Movie" },
                                            fontFamily = MonoFont,
                                            fontSize = 10.sp,
                                            lineHeight = 15.sp,
                                            color = Muted,
                                        )
                                        if (title.type == "series" && title.totalEpisodes > 0)
                                            SegmentedProgress(
                                                watched
                                                    .count { it.mediaKey == title.key }
                                                    .toFloat() / title.totalEpisodes
                                            )
                                    }
                                    if (title.type == "movie")
                                        Text(
                                            "✓",
                                            fontFamily = MonoFont,
                                            fontSize = 10.sp,
                                            lineHeight = 15.sp,
                                            color = Muted,
                                        )
                                }
                            }
                        }
                    }
                }
            }
        if (visible.size > limit)
            MotionButton("Show more · $limit / ${visible.size}", Modifier.fillMaxWidth()) {
                limit += 40
            }
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = Rule)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { insight = !insight },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label("Viewing activity", Modifier.weight(1f))
                Text(
                    "${history.count { it.updatedAt > System.currentTimeMillis() - 7 * 86_400_000L }} watched in 7 days",
                    fontFamily = MonoFont,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Muted,
                )
            }
            if (insight) {
                Text(
                    "Last 12 weeks · tap a day to filter your history.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Muted,
                )
                val today = vm.repository.today
                val start = today.minusDays(77 + (today.dayOfWeek.value - 1).toLong())
                val counts =
                    history
                        .groupingBy {
                            Instant.ofEpochMilli(it.updatedAt)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        .eachCount()
                Row(
                    Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(12) { week ->
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            repeat(7) { weekday ->
                                val date = start.plusDays((week * 7 + weekday).toLong())
                                Box(
                                    Modifier.fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(
                                            if (date > today) Color.Transparent
                                            else if ((counts[date] ?: 0) > 0) Accent
                                            else MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                        .clickable(enabled = date <= today) {
                                            period = "all"
                                            selectedDay =
                                                if (selectedDay == date.toString()) ""
                                                else date.toString()
                                            limit = 40
                                        }
                                        .semantics {
                                            contentDescription =
                                                "$date: ${counts[date] ?: 0} watched"
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (details)
        AlertDialog(
            onDismissRequest = { details = false },
            title = { Text("About activity") },
            text = {
                Text(
                    "Manual history stored on this device.\n\nAll time includes saved history and explicitly synced Jellyfin watch state; it is not a playback-event audit. Missing server watch dates are shown as unknown.\n\nRuntime estimates use known title lengths. They are not measured viewing duration. Repeated viewings may not be represented."
                )
            },
            confirmButton = { TextButton({ details = false }) { Text("Close") } },
        )
}

@Composable
fun CalendarScreen(
    vm: StakViewModel,
    saved: List<SavedMedia>,
    refresh: Int,
    onOpen: (Media, String) -> Unit,
) {
    val o = LocalOptions.current
    val currentDate = vm.repository.today
    var selected by rememberSaveable { mutableStateOf(currentDate.toString()) }
    var events by remember { mutableStateOf(emptyList<Pair<Media, Episode>>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(false) }
    val followed = saved.filter { it.collection == "watchlist" }.map { it.media() }
    LaunchedEffect(followed.map { it.key }, refresh) {
        loading = true
        try {
            val gate = Semaphore(3)
            events = coroutineScope {
                followed
                    .map { title ->
                        async {
                            gate.withPermit {
                                val media = vm.repository.details(title)
                                if (media.type == "series")
                                    media.episodes
                                        .filter { it.released.isNotBlank() }
                                        .map { media to it }
                                else
                                    media.releaseDate
                                        .takeIf { it.isNotBlank() }
                                        ?.let {
                                            listOf(
                                                media to
                                                    Episode(
                                                        "release",
                                                        0,
                                                        0,
                                                        "Movie release",
                                                        it.take(10) + "T00:00:00Z",
                                                        media.poster,
                                                        "",
                                                    )
                                            )
                                        }
                                        .orEmpty()
                            }
                        }
                    }
                    .awaitAll()
                    .flatten()
            }
            error = ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message.orEmpty()
        } finally {
            loading = false
        }
    }
    val date = LocalDate.parse(selected)
    val month = YearMonth.from(date)
    val today = vm.repository.today
    val weekStart =
        o["calendarWeekStart"]?.toIntOrNull()?.takeIf { it in 0..1 }
            ?: (WeekFields.of(java.util.Locale.getDefault()).firstDayOfWeek.value % 7)
    val start =
        month
            .atDay(1)
            .minusDays(((month.atDay(1).dayOfWeek.value % 7 - weekStart + 7) % 7).toLong())
    val cells = (0..41).map { start.plusDays(it.toLong()) }
    val visible =
        events.filter {
            o["calendarType"].isNullOrBlank() ||
                o["calendarType"] == "all" ||
                o["calendarType"] == it.first.type
        }
    val byDate = visible.groupBy { it.second.released.take(10) }
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 60.dp.toPx() }
    val cellHeight = (viewportWidthDp() * .14f).coerceIn(48f, 80f).dp
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 2.dp, end = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Following" +
                    when (o["calendarType"]) {
                        "movie" -> " · Movies"
                        "series" -> " · Episodes"
                        else -> ""
                    },
                Modifier.weight(1f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = Muted,
            )
            Text(
                "Today",
                Modifier.height(48.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .clickable { selected = today.toString() }
                    .padding(horizontal = 4.dp),
                fontSize = 12.sp,
                lineHeight = 16.8.sp,
                color = Accent,
            )
            StakIconButton(StakIcons.SlidersHorizontal, "Calendar filters") { filters = true }
        }
        Column(
            Modifier.fillMaxWidth()
                .pointerInput(month, swipeThreshold) {
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onHorizontalDrag = { _, amount -> drag += amount },
                        onDragEnd = {
                            if (kotlin.math.abs(drag) > swipeThreshold) {
                                val next = date.plusMonths(if (drag < 0) 1 else -1)
                                if (next.year in 1900..2100) selected = next.toString()
                            }
                        },
                    )
                }
                .semantics { collectionInfo = CollectionInfo(6, 7) }
        ) {
            Row(
                Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    { selected = date.minusMonths(1).toString() },
                    Modifier.size(44.dp),
                    enabled = month > YearMonth.of(1900, 1),
                ) {
                    Icon(
                        StakIcons.ChevronRight,
                        "Previous month",
                        Modifier.size(18.dp).graphicsLayer { rotationZ = 180f },
                    )
                }
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    Modifier.weight(1f),
                    fontFamily = DotFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 23.sp,
                    lineHeight = 29.9.sp,
                    textAlign = TextAlign.Center,
                )
                IconButton(
                    { selected = date.plusMonths(1).toString() },
                    Modifier.size(44.dp),
                    enabled = month < YearMonth.of(2100, 12),
                ) {
                    Icon(StakIcons.ChevronRight, "Next month", Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                cells.take(7).forEach { day ->
                    Text(
                        day.dayOfWeek.getDisplayName(
                            java.time.format.TextStyle.NARROW,
                            java.util.Locale.getDefault(),
                        ),
                        Modifier.weight(1f).padding(vertical = 8.dp),
                        fontFamily = MonoFont,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        color = Muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            cells.chunked(7).forEachIndexed { row, week ->
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEachIndexed { column, day ->
                        val selectedCell = day == date
                        val inMonth = YearMonth.from(day) == month
                        val releases = byDate[day.toString()].orEmpty()
                        val art =
                            if (o["calendarArtwork"] == "true")
                                releases
                                    .firstOrNull()
                                    ?.first
                                    ?.let { it.background.ifBlank { it.poster } }
                                    .orEmpty()
                            else ""
                        Box(
                            Modifier.weight(1f)
                                .height(cellHeight)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    if (selectedCell) Accent.copy(alpha = .14f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = .3f)
                                )
                                .border(
                                    if (selectedCell) 2.dp else 1.dp,
                                    if (selectedCell || day == today) Accent else Color.Transparent,
                                    RoundedCornerShape(7.dp),
                                )
                                .clickable(enabled = day.year in 1900..2100) {
                                    selected = day.toString()
                                }
                                .semantics {
                                    this.selected = selectedCell
                                    collectionItemInfo = CollectionItemInfo(row, 1, column, 1)
                                    contentDescription =
                                        "$day${if (day == today) ", today" else ""}, ${releases.size} reported releases"
                                }
                        ) {
                            if (art.isNotBlank())
                                AsyncImage(
                                    art,
                                    null,
                                    Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            Text(
                                day.dayOfMonth.toString(),
                                Modifier.align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .background(
                                        if (selectedCell) Accent else Color.Transparent,
                                        RoundedCornerShape(5.dp),
                                    )
                                    .padding(horizontal = if (selectedCell) 3.dp else 0.dp),
                                fontFamily = MonoFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color =
                                    if (selectedCell) AccentForeground
                                    else if (day == today) Accent
                                    else if (art.isNotBlank()) Color.White
                                    else if (inMonth) MaterialTheme.colorScheme.onSurface
                                    else Muted.copy(alpha = .5f),
                            )
                            if (releases.isNotEmpty())
                                Text(
                                    if (releases.size > 1) "+${releases.size - 1}" else "•",
                                    Modifier.align(Alignment.BottomEnd)
                                        .padding(end = 5.dp, bottom = 6.dp),
                                    fontFamily = MonoFont,
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp,
                                    color = if (art.isNotBlank()) Color.White else Muted,
                                )
                        }
                    }
                }
            }
        }
        if (loading)
            Text("Loading schedule…", fontSize = 11.sp, lineHeight = 16.5.sp, color = Muted)
        if (error.isNotBlank()) OriginalEmpty("Calendar could not update", error)
        Column(Modifier.padding(top = 20.dp)) {
            HorizontalDivider(color = Rule)
            Spacer(Modifier.height(18.dp))
            Text(
                (if (date == today) "Today · " else "") +
                    date
                        .format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                        .uppercase(java.util.Locale.ROOT),
                fontFamily = MonoFont,
                fontSize = 11.sp,
                lineHeight = 16.5.sp,
                color = Muted,
            )
            Spacer(Modifier.height(10.dp))
            val selectedReleases = byDate[selected].orEmpty()
            if (!loading && selectedReleases.isEmpty())
                Column(
                    Modifier.padding(horizontal = 4.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("No releases for this day.", fontSize = 14.sp, lineHeight = 21.sp)
                    if (followed.isEmpty())
                        Text(
                            "Add movies or series to your watchlist to follow their releases.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = Muted,
                        )
                }
            else
                selectedReleases.forEach { (media, episode) ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { onOpen(media, "calendar") }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(media.name, fontSize = 15.sp, lineHeight = 20.sp)
                        Text(
                            if (media.type == "movie") "Movie release"
                            else
                                "S%02d E%02d · %s"
                                    .format(episode.season, episode.number, episode.name),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = Muted,
                        )
                    }
                }
        }
    }
    if (filters)
        StakBottomSheet({ filters = false }) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Calendar filters", style = MaterialTheme.typography.headlineSmall)
                Label("Releases")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "All", "series" to "Episodes", "movie" to "Movies").forEach {
                        (key, label) ->
                        MotionButton(label, primary = (o["calendarType"] ?: "all") == key) {
                            vm.option("calendarType", key)
                        }
                    }
                    OriginalToggle("Artwork in calendar", o["calendarArtwork"] == "true") {
                        vm.option("calendarArtwork", it.toString())
                    }
                    Text("Week starts", fontSize = 13.sp, lineHeight = 19.5.sp)
                    Choice(
                        "Week starts",
                        o["calendarWeekStart"] ?: "locale",
                        listOf("locale" to "Device preference", "1" to "Monday", "0" to "Sunday"),
                        { vm.option("calendarWeekStart", it) },
                    )
                    Text(
                        "Following uses your watchlist. Release dates are reported by metadata providers and may change.",
                        fontSize = 12.sp,
                        lineHeight = 19.2.sp,
                        color = Muted,
                    )
                    MotionButton("Reset filters") {
                        vm.option("calendarType", "all")
                        vm.option("calendarArtwork", "false")
                        vm.option("calendarWeekStart", "locale")
                    }
                }
            }
        }
}
