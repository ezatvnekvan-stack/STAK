package app.stak.nativeapp.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderStateTest {
    private val media=MediaItem("film",name="Film",runtime="120 min")
    private val zone=ZoneId.of("Europe/Budapest")
    @Test fun onlyActiveProfileAndLocalHistoryInfluenceReminders(){
        val rows=listOf(trackingEntity(media,null,true,1000,"profile-a"),trackingEntity(media.copy(id="other"),null,true,999999,"profile-b"),trackingEntity(media.copy(id="manual"),null,true,2000,"local"))
        assertEquals(setOf("film","manual"),reminderJournal(rows,"profile-a").map{it.media.id}.toSet())
        assertEquals(setOf("manual"),reminderJournal(rows,"local").map{it.media.id}.toSet())
    }
    @Test fun duplicateLocalAndProfileRowsCountOnce(){
        val local=trackingEntity(media,null,true,1000,"local").copy(updatedAt=1001)
        val active=trackingEntity(media,null,true,2000,"profile-a").copy(updatedAt=2001)
        assertEquals(1,reminderJournal(listOf(local,active),"profile-a").size)
    }
    @Test fun unwatchedOverrideDoesNotMaintainAStreak(){
        val local=trackingEntity(media,null,true,1000,"local").copy(updatedAt=1001)
        val active=trackingEntity(media,null,false,2000,"profile-a").copy(updatedAt=2001)
        assertTrue(reminderJournal(listOf(local,active),"profile-a").isEmpty())
    }
    @Test fun localCalendarDayRatherThanTwentyHourDelayLimitsSending(){
        val sent=ZonedDateTime.of(2026,10,26,23,30,0,0,zone)
        assertTrue(reminderAlreadySentToday(sent.toInstant().toEpochMilli(),sent.plusMinutes(10).toInstant(),zone))
        assertFalse(reminderAlreadySentToday(sent.toInstant().toEpochMilli(),sent.plusHours(1).toInstant(),zone))
    }
    @Test fun delayedRunReturnsToTheNextLocalEvening(){
        val now=ZonedDateTime.of(2026,10,27,21,0,0,0,zone)
        assertEquals(now.toLocalDate().plusDays(1).atTime(19,0).atZone(zone),nextReminderRun(now))
    }
    @Test fun exactTargetSchedulesTomorrow(){
        val now=ZonedDateTime.of(2026,10,27,19,0,0,0,zone)
        assertEquals(now.plusDays(1),nextReminderRun(now))
    }
    @Test fun daylightSavingTransitionUsesTwentyThreeOrTwentyFiveHours(){
        val spring=ZonedDateTime.of(2026,3,28,19,0,0,0,zone)
        val autumn=ZonedDateTime.of(2026,10,24,19,0,0,0,zone)
        assertEquals(23,Duration.between(spring,nextReminderRun(spring)).toHours())
        assertEquals(25,Duration.between(autumn,nextReminderRun(autumn)).toHours())
    }
    @Test fun changingZoneRebasesToTheNewLocalEvening(){
        val original=ZonedDateTime.of(2026,10,27,17,0,0,0,zone)
        val moved=original.withZoneSameInstant(ZoneId.of("Europe/Bucharest"))
        assertEquals(19,nextReminderRun(moved).hour)
        assertNotEquals(nextReminderRun(original).toInstant(),nextReminderRun(moved).toInstant())
    }
}
