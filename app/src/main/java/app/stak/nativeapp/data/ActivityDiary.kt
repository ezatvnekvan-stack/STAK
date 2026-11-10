package app.stak.nativeapp.data
import java.time.LocalDate
import java.time.YearMonth
fun diaryEntries(entries:List<JournalEntry>,month:YearMonth,today:LocalDate):List<JournalEntry> = individualWatches(entries).filter{e->journalDay(e.watchedAt)?.let{YearMonth.from(it)==month&&!it.isAfter(today)}==true}
fun diaryMonths(entries:List<JournalEntry>,today:LocalDate):List<YearMonth> = ((0L..11L).map{YearMonth.from(today).minusMonths(it)}+entries.mapNotNull{journalDay(it.watchedAt)?.takeIf{d->!d.isAfter(today)}?.let{d->YearMonth.from(d)}}).distinct().sortedDescending()
