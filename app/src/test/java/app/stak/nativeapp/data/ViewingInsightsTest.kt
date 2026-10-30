package app.stak.nativeapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ViewingInsightsTest {

    private val today = LocalDate.of(2026, 10, 27) // Tuesday

    private fun entry(
        id: String,
        at: LocalDate?,
        series: Boolean = false,
        season: Int? = null,
        episode: Int? = null,
        runtime: String? = null,
        genres: List<String> = emptyList(),
        played: Boolean = true
    ) = JournalEntry(
        media = MediaItem(
            id = id,
            type = if (series) "series" else "movie",
            name = "Title $id",
            runtime = runtime,
            genres = genres,
            season = season,
            episode = episode
        ),
        watchedAt = at?.atTime(20, 0)?.atZone(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        played = played
    )

    private fun insights(entries: List<JournalEntry>) =
        viewingInsights(entries, weeks = 12, today = today, zone = ZoneOffset.UTC)

    @Test
    fun emptyJournalHasNoInsightsButStillAShape() {
        val result = insights(emptyList())
        assertFalse(result.hasData)
        assertEquals(0, result.total)
        assertEquals(12, result.weeks.size)
        assertEquals(0, result.streakDays)
        assertEquals(0.0, result.averagePerWeek, 0.0)
    }

    @Test
    fun moviesEpisodesAndSeriesAreCountedSeparately() {
        val result = insights(
            listOf(
                entry("tt1", today, runtime = "100 min"),
                entry("tt2", today, series = true),
                entry("tt2", today, series = true, season = 1, episode = 3),
                entry("tt2", today, series = true, season = 1, episode = 4)
            )
        )
        assertEquals(4, result.total)
        assertEquals(1, result.movies)
        assertEquals(2, result.episodes)
        assertEquals(1, result.series)
    }

    @Test
    fun runtimeIsSummedAndFlaggedWhenIncomplete() {
        val complete = insights(
            listOf(
                entry("tt1", today, runtime = "100 min"),
                entry("tt2", today, runtime = "20 min")
            )
        )
        assertEquals(120, complete.knownMinutes)
        assertFalse(complete.runtimeIncomplete)
        assertEquals(2.0, complete.knownHours, 0.001)

        val partial = insights(listOf(entry("tt1", today, runtime = "100 min"), entry("tt2", today)))
        assertEquals(100, partial.knownMinutes)
        assertTrue(partial.runtimeIncomplete)
    }

    @Test
    fun streakCountsConsecutiveDaysBackFromToday() {
        val result = insights(
            listOf(
                entry("a", today),
                entry("b", today.minusDays(1)),
                entry("c", today.minusDays(2))
            )
        )
        assertEquals(3, result.streakDays)
    }

    @Test
    fun streakSurvivesADayWithoutWatchingYet() {
        val result = insights(
            listOf(entry("a", today.minusDays(1)), entry("b", today.minusDays(2)))
        )
        assertEquals(2, result.streakDays)
    }

    @Test
    fun aMissedDayBreaksTheStreak() {
        val result = insights(
            listOf(
                entry("a", today),
                entry("b", today.minusDays(2)),
                entry("c", today.minusDays(3))
            )
        )
        assertEquals(1, result.streakDays)
    }

    @Test
    fun thisWeekAndLastWeekUseMondayBoundaries() {
        val result = insights(
            listOf(
                entry("mon", LocalDate.of(2026, 10, 26)),
                entry("tue", LocalDate.of(2026, 10, 27)),
                entry("sun", LocalDate.of(2026, 10, 25)),
                entry("mon-before", LocalDate.of(2026, 10, 19))
            )
        )
        assertEquals(2, result.thisWeek)
        assertEquals(2, result.lastWeek)
        assertEquals(0, result.weekDelta)
    }

    @Test
    fun weekDeltaFollowsTheDirectionOfChange() {
        val result = insights(
            listOf(
                entry("a", today),
                entry("b", today),
                entry("c", today.minusDays(1)),
                entry("d", today.minusDays(7))
            )
        )
        assertEquals(3, result.thisWeek)
        assertEquals(1, result.lastWeek)
        assertEquals(2, result.weekDelta)
    }

    @Test
    fun topGenresAreRankedAndCapped() {
        val result = insights(
            listOf(
                entry("a", today, genres = listOf("Drama", "Comedy")),
                entry("b", today, genres = listOf("Drama")),
                entry("c", today, genres = listOf("Comedy")),
                entry("d", today, genres = listOf("Horror")),
                entry("e", today, genres = listOf("Western")),
                entry("f", today, genres = listOf("Musical"))
            )
        )
        assertEquals(4, result.topGenres.size)
        assertEquals(setOf("Drama", "Comedy"), result.topGenres.take(2).map { it.genre }.toSet())
        assertEquals(2, result.topGenres.first().count)
    }

    @Test
    fun busiestDayKeepsTheHighestCount() {
        val busy = today.minusDays(5)
        val result = insights(
            listOf(
                entry("a", busy), entry("b", busy), entry("c", busy),
                entry("d", today)
            )
        )
        assertEquals(busy, result.busiestDay)
        assertEquals(3, result.busiestDayCount)
    }

    @Test
    fun averagePerWeekSpansTheWholeHistory() {
        val first = today.minusWeeks(3)
        val result = insights(
            listOf(
                entry("a", first),
                entry("b", today),
                entry("c", today),
                entry("d", today)
            )
        )
        // Four weeks inclusive: 4 entries / 4 weeks.
        assertEquals(1.0, result.averagePerWeek, 0.001)
    }

    @Test
    fun weeksCoverTheRecentHistoryInOrder() {
        val result = insights(listOf(entry("a", today)))
        assertEquals(12, result.weeks.size)
        assertEquals(1, result.weeks.last().watched)
        assertEquals(0, result.weeks.first().watched)
        assertTrue(result.weeks.zipWithNext().all { (a, b) -> b.start == a.start.plusWeeks(1) })
    }

    @Test
    fun unwatchedEntriesDoNotCount() {
        val watched = entry("a", today)
        val result = insights(listOf(watched, entry("b", today, played = false)))
        assertEquals(1, result.total)
    }
}
