package app.stak.nativeapp.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Locale

class ActivityDatesTest {
    private val monday=LocalDate.of(2026,10,26)
    private val slice=ActivitySlice(monday,monday.plusDays(6),2)
    private fun event(id:String,date:LocalDate?)=JournalEntry(MediaItem("film",name="Movie"),date?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),eventId=id)
    @Test fun weekRangeIncludesBothDatesAndYear(){assertEquals("Oct 26 – Nov 1, 2026",activityRangeLabel(slice,false))}
    @Test fun crossingYearKeepsBothYears(){assertEquals("Dec 28, 2026 – Jan 3, 2027",activityRangeLabel(ActivitySlice(LocalDate.of(2026,12,28),LocalDate.of(2027,1,3),0),false))}
    @Test fun monthUsesReadableFullNameInEnglish(){val before=Locale.getDefault();try{Locale.setDefault(Locale.forLanguageTag("hu-HU"));assertEquals("October 2026",activityRangeLabel(slice,true))}finally{Locale.setDefault(before)}}
    @Test fun dailyBreakdownKeepsGapsAndCountsSeparateRewatches(){
        val rows=listOf(event("a",monday),event("b",monday),event("unknown",null),event("outside",monday.minusDays(1)))
        assertEquals(listOf(2,0,0,0,0,0,0),activityDays(slice,rows).map{it.count})
        assertEquals(2,watchesOn(rows,monday).size)
    }
    @Test fun dailyBreakdownDoesNotCountUnwatchedEntriesOrDuplicateEvents(){val e=event("one",monday);assertEquals(1,activityDays(slice,listOf(e,e,event("no",monday).copy(played=false))).first().count)}
    @Test fun monthlyBreakdownUsesActualMonthLength(){val feb=ActivitySlice(LocalDate.of(2028,2,1),LocalDate.of(2028,2,29),0);assertEquals(29,activityDays(feb,emptyList()).size)}
}
