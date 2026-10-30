package app.stak.nativeapp.data

import app.stak.nativeapp.data.db.TrackingEntity
import java.time.*

/** Use the same profile filtering, deduplication and unwatched overrides as History. */
fun reminderJournal(rows:List<TrackingEntity>,scope:String):List<JournalEntry> = mergeJournal(rows,emptyList(),scope)
fun reminderAlreadySentToday(lastSentAt:Long,now:Instant,zone:ZoneId):Boolean =
    lastSentAt>0 && journalDay(lastSentAt,zone)==now.atZone(zone).toLocalDate()

/** Resolve the next local evening afresh, including DST and timezone changes. */
fun nextReminderRun(now:ZonedDateTime,target:LocalTime=LocalTime.of(19,0)):ZonedDateTime {
    val candidate=now.toLocalDate().atTime(target).atZone(now.zone)
    return if(candidate.isAfter(now))candidate else now.toLocalDate().plusDays(1).atTime(target).atZone(now.zone)
}
