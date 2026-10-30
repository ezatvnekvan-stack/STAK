package app.stak.nativeapp.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Aggregates the viewing journal into the numbers the Tracking screen shows.
 * Pure functions only: no Android, no coroutines, so the whole feature is
 * covered by JVM tests.
 */
data class GenreShare(val genre: String, val count: Int)

data class WeekSlice(val start: LocalDate, val watched: Int)

data class ViewingInsights(
    val total: Int = 0,
    val movies: Int = 0,
    val episodes: Int = 0,
    val series: Int = 0,
    val knownMinutes: Long = 0,
    val knownRuntimeTitles: Int = 0,
    val streakDays: Int = 0,
    val busiestDay: LocalDate? = null,
    val busiestDayCount: Int = 0,
    val topGenres: List<GenreShare> = emptyList(),
    val weeks: List<WeekSlice> = emptyList(),
    val thisWeek: Int = 0,
    val lastWeek: Int = 0,
    val averagePerWeek: Double = 0.0,
    val lastWatched: Long? = null
) {
    val hasData: Boolean get() = total > 0
    val knownHours: Double get() = knownMinutes / 60.0
    /** True when at least one entry is missing runtime, so the hour total is a floor. */
    val runtimeIncomplete: Boolean get() = knownRuntimeTitles < movies + episodes
    val weekDelta: Int get() = thisWeek - lastWeek
}

/**
 * @param entries journal entries, already de-duplicated per title or episode.
 * @param weeks how many weeks the activity strip covers (most recent last).
 */
fun viewingInsights(
    entries: List<JournalEntry>,
    weeks: Int = 12,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    weekStart: DayOfWeek = DayOfWeek.MONDAY
): ViewingInsights {
    val played = entries.filter { it.played }
    if (played.isEmpty()) {
        return ViewingInsights(weeks = weekSlices(emptyList(), weeks, today, zone, weekStart))
    }
    val dated = played.mapNotNull { journalDay(it.watchedAt, zone) }

    var movies = 0
    var episodes = 0
    var series = 0
    var minutes = 0L
    var knownRuntime = 0
    var lastWatched: Long? = null
    val genreCounts = HashMap<String, Int>()
    played.forEach { entry ->
        when {
            entry.isEpisode -> episodes++
            entry.media.isSeries -> series++
            else -> movies++
        }
        runtimeMinutes(entry.media).takeIf { !entry.media.isSeries || entry.isEpisode }?.let {
            minutes += it
            knownRuntime++
        }
        val stamp = entry.watchedAt
        if (stamp != null && (lastWatched == null || stamp > lastWatched)) lastWatched = stamp
        entry.media.genres.orEmpty().forEach { genre ->
            val label = genre.trim()
            if (label.isNotEmpty()) genreCounts[label] = (genreCounts[label] ?: 0) + 1
        }
    }

    val perDay = dated.groupingBy { it }.eachCount()
    val busiest = perDay.maxWithOrNull(compareBy<Map.Entry<LocalDate, Int>> { it.value }.thenByDescending { it.key })
    val streak = streakLength(perDay, today)

    val slices = weekSlices(dated, weeks, today, zone, weekStart)
    val thisWeek = slices.lastOrNull()?.watched ?: 0
    val lastWeek = slices.getOrNull(slices.size - 2)?.watched ?: 0
    val spanWeeks = if (dated.isEmpty()) 1L else {
        val first = dated.min()
        (ChronoUnit.DAYS.between(first.with(weekStart), today.with(weekStart)) / 7 + 1).coerceAtLeast(1)
    }

    return ViewingInsights(
        total = played.size,
        movies = movies,
        episodes = episodes,
        series = series,
        knownMinutes = minutes,
        knownRuntimeTitles = knownRuntime,
        streakDays = streak,
        busiestDay = busiest?.key,
        busiestDayCount = busiest?.value ?: 0,
        topGenres = genreCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(4)
            .map { GenreShare(it.key, it.value) },
        weeks = slices,
        thisWeek = thisWeek,
        lastWeek = lastWeek,
        averagePerWeek = played.size.toDouble() / spanWeeks,
        lastWatched = lastWatched
    )
}

/** Consecutive days with at least one watch, counting back from [today]. */
fun streakLength(perDay: Map<LocalDate, Int>, today: LocalDate): Int {
    if (perDay.isEmpty()) return 0
    // A streak survives until the end of today: start at today, or yesterday if
    // nothing has been watched yet today.
    var cursor = when {
        perDay.containsKey(today) -> today
        perDay.containsKey(today.minusDays(1)) -> today.minusDays(1)
        else -> return 0
    }
    var streak = 0
    while (perDay.containsKey(cursor)) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

private fun weekSlices(
    dates: List<LocalDate>,
    count: Int,
    today: LocalDate,
    zone: ZoneId,
    weekStart: DayOfWeek
): List<WeekSlice> {
    val currentWeek = today.with(weekStart)
    val safeCount = count.coerceIn(0, 52)
    return (safeCount - 1 downTo 0).map { back ->
        val start = currentWeek.minusWeeks(back.toLong())
        WeekSlice(start, dates.count { it >= start && it < start.plusWeeks(1) })
    }
}

/** Short label for the activity strip, e.g. "Oct 21". */
fun weekLabel(start: LocalDate): String =
    start.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
