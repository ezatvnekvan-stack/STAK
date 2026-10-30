package app.stak.nativeapp.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TrackingOverviewTest {
    private val day=LocalDate.of(2026,10,28)
    private val zone=ZoneId.systemDefault()
    private fun entry(id:String="show",season:Int=1,episode:Int=1,date:LocalDate?=day)=JournalEntry(MediaItem(id,type="series",name="Same title",season=season,episode=episode,runtime="45 min",genres=listOf("Crime"," Crime ","Drama")),date?.atStartOfDay(zone)?.toInstant()?.toEpochMilli())
    @Test fun fourSeasonsCollapseIntoOneDailySeriesWithoutLosingEpisodes(){
        val rows=(1..4).flatMap{s->(1..if(s==4)12 else 11).map{entry(season=s,episode=it)}}
        val group=historyDays(rows).single().groups.single()
        assertEquals("4 seasons · 45 episodes",group.subtitle)
        assertEquals(listOf(4,3,2,1),group.seasons.map{it.number})
        assertEquals((1..12).toList(),group.seasons.first().entries.map{it.media.episode})
        assertEquals(45,group.entries.size)
    }
    @Test fun sameNameWithDifferentStableIdsRemainsSeparate(){
        assertEquals(2,historyDays(listOf(entry("a"),entry("b"))).single().groups.size)
        assertEquals(2,topWatchedSeries(listOf(entry("a"),entry("b"))).size)
    }
    @Test fun episodeIdsWithoutKnownParentAreNotMergedByName(){
        assertEquals(2,historyDays(listOf(entry("jellyfin:a"),entry("jellyfin:b"))).single().groups.size)
    }
    @Test fun sameSeriesOnDifferentDaysRemainsSeparate(){assertEquals(2,historyDays(listOf(entry(),entry(episode=2,date=day.minusDays(1)))).size)}
    @Test fun markersDuplicatesAndUnwatchedDoNotInflateStats(){
        val ep=entry();val marker=ep.copy(media=ep.media.copy(season=null,episode=null))
        assertEquals(1,individualWatches(listOf(ep,ep,marker,entry(episode=2).copy(played=false))).size)
        assertEquals(1,topWatchedSeries(listOf(ep,ep,marker)).single().episodes)
    }
    @Test fun datesAreRequiredForChartAndAverage(){
        val entries=listOf(entry(date=null),entry(episode=2,date=day.plusDays(1)))
        assertTrue(trackingActivity(entries,false,"all",day).isEmpty())
        assertNull(datedWeeklyAverage(entries,"all",day))
    }
    @Test fun chartTrimsLeadingGapsButRetainsInternalGaps(){
        val rows=listOf(entry(date=day.minusWeeks(2)),entry(episode=2))
        assertEquals(listOf(1,0,1),trackingActivity(rows,false,"all",day).map{it.count})
    }
    @Test fun monthBucketsAndWeekBucketsCountTheSameDatedRecords(){
        val rows=listOf(entry(date=day.minusDays(15)),entry(episode=2),entry(episode=3,date=null))
        assertEquals(2,trackingActivity(rows,false,"all",day).sumOf{it.count})
        assertEquals(2,trackingActivity(rows,true,"all",day).sumOf{it.count})
    }
    @Test fun selectedPeriodIsEnforcedIncludingPartialFirstBucket(){
        val rows=listOf(entry(date=day.minusDays(7)),entry(episode=2,date=day.minusDays(6)),entry(episode=3))
        assertEquals(2,trackingActivity(rows,false,"week",day).sumOf{it.count})
        assertEquals(2.0,datedWeeklyAverage(rows,"week",day)!!,0.001)
    }
    @Test fun olderHistoryAnchorsChartAtLastRealRecord(){
        val old=day.minusYears(2)
        assertEquals(old.with(DayOfWeek.MONDAY),trackingActivity(listOf(entry(date=old)),false,"all",day).single().start)
    }
    @Test fun genreCountsNormalizeWhitespaceWithoutLosingOverlaps(){
        assertEquals(listOf(GenreShare("Crime",1),GenreShare("Drama",1)),trackingGenres(listOf(entry())))
    }
    @Test fun listsAreLimitedToFiveAndRankedByActualEpisodeCount(){
        val rows=(1..7).flatMap{i->(1..i).map{entry(id="show$i",episode=it).let{e->e.copy(media=e.media.copy(genres=listOf("Genre$i")))}}}
        assertEquals(listOf(7,6,5,4,3),topWatchedSeries(rows).map{it.episodes})
        assertEquals(5,trackingGenres(rows).size)
    }
    @Test fun seriesNavigationUsesKnownParentInsteadOfEpisodeId(){
        val media=entry("jellyfin:episode").media.copy(jellyfinId="episode",jellyfinSeriesId="parent")
        val target=journalSeriesTitle(media)
        assertEquals("jellyfin:parent",target.id);assertEquals("parent",target.jellyfinId)
        assertNull(target.season);assertNull(target.episode)
    }
}
