package app.stak.nativeapp.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HistoryGroup(val id: String, val entries: List<JournalEntry>) {
    val first: JournalEntry get() = entries.first()
    val groupedEpisodes: Boolean get() = entries.size > 1 && first.isEpisode
    val seasons:List<HistorySeason> get()=entries.filter{it.isEpisode}.groupBy{it.media.season!!}.entries.sortedByDescending{it.key}.map{HistorySeason(it.key,it.value.sortedBy{entry->entry.media.episode})}
    val subtitle: String get() = if (groupedEpisodes) {
        val seasonLabel=if(seasons.size==1)"Season ${seasons.first().number}"else "${seasons.size} seasons"
        "$seasonLabel · ${entries.size} ${if(entries.map{it.media.season to it.media.episode}.distinct().size<entries.size)"watches"else "episodes"}"
    }else if(first.isEpisode)"S${first.media.season} E${first.media.episode}"else if(first.media.isSeries)"Series marked watched"else "Movie"

}
data class HistorySeason(val number:Int,val entries:List<JournalEntry>)

data class HistoryDay(val date: LocalDate?, val groups: List<HistoryGroup>)

/** Group a series on one calendar day; keep the individual episode records unchanged. */
fun journalSeriesIdentity(media:MediaItem):String = media.imdbId?.takeIf{it.isNotBlank()}?.let{"imdb:$it"}
    ?: media.jellyfinSeriesId?.takeIf{it.isNotBlank()}?.let{"jellyfin:$it"}
    ?: media.id.takeIf{it.isNotBlank()}?.let{"id:$it"}
    ?: "title:${librarySearchText(media.name)}:${media.year.orEmpty()}"

fun historyDays(entries: List<JournalEntry>): List<HistoryDay> {
    val sorted = entries.sortedWith(compareByDescending<JournalEntry> { it.watchedAt ?: Long.MIN_VALUE }
        .thenByDescending { it.media.season ?: 0 }.thenByDescending { it.media.episode ?: 0 }.thenBy { it.key })
    return sorted.groupBy { journalDay(it.watchedAt) }.map { (day, rows) ->
        val groups = rows.groupBy { entry ->
            if (!entry.isEpisode) "title:${entry.key}" else {
                "episodes:${journalSeriesIdentity(entry.media)}"
            }
        }.map { (id, records) -> HistoryGroup("$day:$id", records) }
        HistoryDay(day, groups)
    }
}

fun historyDateLabel(date: LocalDate?, today: LocalDate = LocalDate.now()): String = when (date) {
    null -> "Unknown date"
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))
}
fun journalPeriodLabel(period: String): String = when (period) { "week" -> "Last 7 days"; "month" -> "Last 30 days"; "this_month" -> "This month"; else -> "All time" }
