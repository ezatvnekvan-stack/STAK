package app.stak.nativeapp.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Decides whether a daily reminder is worth sending.
 *
 * This is deliberately conservative: at most one notification per day and only
 * when there is a reason - a streak about to break, saved titles waiting, or a
 * journal that has gone quiet. Pure functions, so the rules are unit tested
 * instead of tuned on a device.
 */
data class Reminder(val title: String, val body: String)

fun planReminder(
    insights: ViewingInsights,
    watchlistCount: Int,
    today: LocalDate,
    lastWatched: Long?,
    zone: ZoneId = ZoneId.systemDefault(),
    quietDays: Int = 2,
    quietJournalDays: Int = 7,
    streakGuard: Int = 3
): Reminder? {
    val lastDay = lastWatched?.let { journalDay(it, zone) }
    val daysSince = lastDay?.let { ChronoUnit.DAYS.between(it, today).toInt() }?.coerceAtLeast(0)

    // A streak that survived until yesterday is worth one gentle nudge today.
    if (watchlistCount > 0 && daysSince == 1 && insights.streakDays >= streakGuard) {
        return Reminder(
            "Keep your ${insights.streakDays} day streak",
            "Nothing logged yet today. ${savedTitles(watchlistCount)} ready when you are."
        )
    }

    if (watchlistCount > 0 && (daysSince == null || daysSince >= quietDays)) {
        return Reminder(
            "Something to watch tonight",
            buildString {
                append(savedTitles(watchlistCount).replaceFirstChar { it.lowercase() })
                when {
                    insights.thisWeek > 0 -> append(" ${insights.thisWeek} watched this week.")
                    insights.hasData -> append(" ${insights.total} logged so far.")
                }
            }
        )
    }

    if (daysSince != null && daysSince >= quietJournalDays) {
        return Reminder(
            "Your journal is quiet",
            "It has been $daysSince days since your last watch. Mark something watched to keep the history accurate."
        )
    }
    return null
}

private fun savedTitles(count: Int): String =
    if (count == 1) "1 title is waiting in your watchlist." else "$count titles are waiting in your watchlist."
