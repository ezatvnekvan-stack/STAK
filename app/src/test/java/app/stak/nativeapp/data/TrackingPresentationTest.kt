package app.stak.nativeapp.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Locale

class TrackingPresentationTest {
    private val day=LocalDate.of(2026,10,20)
    private fun episode(number:Int,season:Int=1,date:LocalDate=day,minute:Int=0)=JournalEntry(
        MediaItem("show",imdbId="tt12345",type="series",name="Fixture Series",runtime="45 min",season=season,episode=number),
        date.atTime(20,minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),source="Jellyfin")
    @Test fun nineEpisodesBecomeOneExpandableCard(){
        val groups=historyDays((1..9).map{episode(it)}).single().groups
        assertEquals(1,groups.size);assertEquals(9,groups.single().entries.size)
        assertEquals("Season 1 · 9 episodes",groups.single().subtitle)
    }
    @Test fun seasonsMergeWithinADayButDifferentDaysStaySeparate(){
        val days=historyDays(listOf(episode(1),episode(2),episode(1,2),episode(2,2),episode(3,date=day.plusDays(1))))
        assertEquals(2,days.size);assertEquals(1,days.last().groups.size);assertEquals(2,days.last().groups.single().seasons.size)
    }
    @Test fun gapsAreNotInventedAsRanges(){
        val group=historyDays(listOf(episode(7),episode(9),episode(10))).single().groups.single()
        assertEquals(listOf(10,9,7),group.entries.map{it.media.episode});assertEquals(3,group.entries.size)
        assertEquals("Season 1 · 3 episodes",group.subtitle)
    }
    @Test fun realWatchTimesWinOverEpisodeNumber(){
        val group=historyDays(listOf(episode(10,minute=1),episode(7,minute=5))).single().groups.single()
        assertEquals(7,group.first.media.episode)
    }
    @Test fun moviesWithTheSameNameStaySeparate(){
        val groups=historyDays(listOf(JournalEntry(MediaItem("1",name="Same"),null),JournalEntry(MediaItem("2",name="Same"),null))).single().groups
        assertEquals(2,groups.size)
    }
    @Test fun unknownDatesStayLast(){
        assertNull(historyDays(listOf(episode(1).copy(watchedAt=null),episode(2))).last().date)
    }
    @Test fun datesStayEnglishWithHungarianLocale(){
        val previous=Locale.getDefault()
        try{Locale.setDefault(Locale.forLanguageTag("hu-HU"));assertEquals("Today",historyDateLabel(day,day));assertEquals("Yesterday",historyDateLabel(day.minusDays(1),day));assertEquals("Oct 20, 2026",historyDateLabel(day,day.plusDays(2)))}finally{Locale.setDefault(previous)}
    }
    @Test fun episodeFilterDoesNotTreatWholeSeriesMarkersAsEpisodes(){
        val actual=episode(1);val marker=actual.copy(media=actual.media.copy(season=null,episode=null))
        assertEquals(listOf(actual),filterJournal(listOf(marker,actual),JournalPreferences(type="episode")))
    }
    @Test fun seriesLevelRuntimeIsNotCountedAsAnEpisode(){
        val episode=episode(1)
        val marker=episode.copy(media=episode.media.copy(season=null,episode=null,runtime="500 min"))
        val stats=viewingInsights(listOf(episode,marker),today=day)
        assertEquals(45,stats.knownMinutes);assertEquals(1,stats.knownRuntimeTitles);assertFalse(stats.runtimeIncomplete)
    }
}
