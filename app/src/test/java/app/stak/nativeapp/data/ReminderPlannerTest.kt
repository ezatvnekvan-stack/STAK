package app.stak.nativeapp.data

import app.stak.nativeapp.notifications.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPlannerTest {

    private val zone = ZoneId.of("Europe/Budapest")
    private val today = LocalDate.of(2026, 10, 27)

    private fun stamp(daysAgo: Long): Long =
        today.minusDays(daysAgo).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()

    private fun entry(id: String, daysAgo: Long, series: Boolean = false, runtime: String? = "100 min") =
        JournalEntry(
            media = MediaItem(id = id, type = if (series) "series" else "movie", name = "T $id", runtime = runtime),
            watchedAt = stamp(daysAgo),
            played = true
        )

    private fun insights(vararg daysAgo: Long) =
        viewingInsights(daysAgo.mapIndexed { index, d -> entry("t$index", d) }, today = today, zone = zone)

    @Test
    fun nothingToSayWithoutData() {
        assertNull(planReminder(viewingInsights(emptyList(), today = today, zone = zone), 0, today, null, zone))
    }

    @Test
    fun savedTitlesTriggerTheWatchlistReminder() {
        val reminder = planReminder(insights(5), 3, today, stamp(5), zone)
        assertEquals("Something to watch tonight", reminder?.title)
        assertTrue(reminder?.body?.contains("3 titles") == true)
    }

    @Test
    fun aRecentWatchKeepsItQuiet() {
        val reminder = planReminder(insights(1), 4, today, stamp(1), zone)
        assertNull(reminder)
    }

    @Test
    fun freshAccountWithWatchlistStillGetsNudged() {
        val reminder = planReminder(viewingInsights(emptyList(), today = today, zone = zone), 1, today, null, zone)
        assertTrue(reminder?.body?.contains("1 title is waiting") == true)
    }

    @Test
    fun quietJournalIsMentionedWhenNothingIsSaved() {
        val reminder = planReminder(insights(12), 0, today, stamp(12), zone)
        assertEquals("Your journal is quiet", reminder?.title)
        assertTrue(reminder?.body?.contains("12 days") == true)
    }

    @Test
    fun streakIsProtectedTheDayAfterAWatchedDay() {
        // Three days in a row, last watch yesterday: today is the day the streak can break.
        val reminder = planReminder(insights(3, 2, 1), 2, today, stamp(1), zone)
        assertEquals("Keep your 3 day streak", reminder?.title)
        assertTrue(reminder?.body?.contains("2 titles") == true)

        // Without a saved title there is nothing to suggest, so silence beats nagging.
        assertNull(planReminder(insights(3, 2, 1), 0, today, stamp(1), zone))
    }

    @Test
    fun aStreakIsNotProtectedWhenItIsStillGoingToday() {
        assertNull(planReminder(insights(2, 1, 0), 3, today, stamp(0), zone))
    }

    @Test
    fun theDailyRunLandsAtTheNextSevenPm() {
        val morning = ZonedDateTime.of(today, LocalTime.of(9, 30), zone)
        assertEquals(34_200_000.0, ReminderScheduler.millisUntilNextRun(morning, LocalTime.of(19, 0)).toDouble(), 10.0)

        val evening = ZonedDateTime.of(today, LocalTime.of(20, 15), zone)
        assertEquals(81_900_000.0, ReminderScheduler.millisUntilNextRun(evening, LocalTime.of(19, 0)).toDouble(), 10.0)

        val exactly = ZonedDateTime.of(today, LocalTime.of(19, 0), zone)
        assertEquals(86_400_000.0, ReminderScheduler.millisUntilNextRun(exactly, LocalTime.of(19, 0)).toDouble(), 10.0)
    }
}
