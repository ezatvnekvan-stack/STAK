package app.stak.nativeapp.data

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CalendarJournalTest {
    private fun json(raw:String)=JsonParser.parseString(raw).asJsonObject
    @Test fun leapMonthHasFortyTwoConsecutiveCellsWithMondayAndSundayStarts() {
        val monday=calendarCells(YearMonth.of(2024,2),1);val sunday=calendarCells(YearMonth.of(2024,2),0)
        assertEquals(42,monday.size);assertEquals(LocalDate.of(2024,1,29),monday.first());assertEquals(DayOfWeek.SUNDAY,sunday.first().dayOfWeek)
        assertTrue(LocalDate.of(2024,2,29) in monday);assertTrue(monday.zipWithNext().all{(a,b)->a.plusDays(1)==b})
    }
    @Test fun decemberCalendarCrossesYearBoundaryWithoutChangingDates() {
        val cells=calendarCells(YearMonth.of(2026,12));assertTrue(cells.last().year==2027);assertEquals(42,cells.distinct().size)
    }
    @Test fun calendarUsesTypedImdbIdentityEnglishMetadataAndOnlyOverlappingSeasons()=runBlocking {
        val calls=mutableListOf<String>()
        val repository=CalendarRepository({"legacy"}){url,params,token->calls+=url;assertEquals("en-US",params["language"]);assertEquals("legacy",params["api_key"]);assertNull(token)
            when {
                url.contains("/find/")->json("""{"tv_results":[{"id":10}],"movie_results":[{"id":999}]}""")
                url.endsWith("/tv/10")->json("""{"seasons":[{"season_number":1,"air_date":"2020-01-01"},{"season_number":2,"air_date":"2026-10-01"},{"season_number":3,"air_date":"2027-01-01"}],"next_episode_to_air":{"season_number":2,"episode_number":2,"name":"Two","air_date":"2026-10-20"}}""")
                url.endsWith("/season/2")->json("""{"episodes":[{"season_number":2,"episode_number":1,"name":"One","air_date":"2026-10-10"},{"season_number":2,"episode_number":2,"name":"Two","air_date":"2026-10-20"},{"season_number":2,"episode_number":3,"name":"Later","air_date":"2026-12-01"}]}""")
                else->throw AssertionError("Unnecessary season: $url")
            }
        }
        val feed=repository.load(listOf(MediaItem("tt1234567","tt1234567",type="series",name="Show")),LocalDate.of(2026,10,1),LocalDate.of(2026,10,31))
        assertEquals(listOf(1,2),feed.events.map{it.episode});assertTrue(feed.issues.isEmpty());assertEquals(3,calls.size)
    }
    @Test fun movieDatesAreCivilDatesAndBearerKeysNeverEnterTheApiKeyQuery()=runBlocking {
        val repository=CalendarRepository({"a.b.c"}){url,params,token->assertEquals("a.b.c",token);assertFalse(params.containsKey("api_key"));assertTrue(url.endsWith("/movie/42"));json("""{"title":"Movie","release_date":"2026-10-26","poster_path":"/poster.jpg"}""")}
        val feed=repository.load(listOf(MediaItem("tmdb:42",name="Movie")),LocalDate.of(2026,10,1),LocalDate.of(2026,10,31))
        assertEquals(LocalDate.of(2026,10,26),feed.events.single().date);assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg",feed.events.single().media.poster)
    }
    @Test fun oneFailedTitleDoesNotEraseOtherReleasesAndMissingDatesAreNotInvented()=runBlocking {
        val repository=CalendarRepository({"key"}){url,_,_->when{url.endsWith("/movie/1")->json("""{"title":"Good","release_date":"2026-10-10"}""");url.endsWith("/movie/2")->throw java.io.IOException("synthetic");else->json("""{"title":"Unknown","release_date":null}""")}}
        val feed=repository.load((1..3).map{MediaItem("tmdb:$it",name="Film $it")},LocalDate.of(2026,10,1),LocalDate.of(2026,10,31))
        assertEquals(1,feed.events.size);assertEquals(1,feed.issues.size);assertEquals(3,feed.checked)
    }
    @Test fun successfulCalendarResponsesAreCachedAndExplicitRefreshRechecksThem()=runBlocking {
        var calls=0;val repository=CalendarRepository({"key"}){_,_,_->calls++;json("""{"release_date":"2026-10-10"}""")};val titles=listOf(MediaItem("tmdb:1",name="One"));val start=LocalDate.of(2026,10,1);val end=start.plusDays(30)
        repository.load(titles,start,end);repository.load(titles,start,end);assertEquals(1,calls);repository.load(titles,start,end,true);assertEquals(2,calls)
    }
    @Test fun emptyWatchlistNeedsNoProviderKey()=runBlocking {
        assertTrue(CalendarRepository({""}){_,_,_->throw AssertionError("No request expected")}.load(emptyList(),LocalDate.now(),LocalDate.now()).events.isEmpty())
    }
    @Test fun seasonCoverageIsBoundedAndReportsTheLimit() {
        val seasons=(1..6).joinToString(","){"{\"season_number\":$it,\"air_date\":\"2026-10-0$it\"}"};val result=calendarSeasons(json("{\"seasons\":[$seasons]}"),LocalDate.of(2026,10,1),LocalDate.of(2026,10,31))
        assertEquals(4,result.first.size);assertTrue(result.second)
    }
    @Test fun manualEpisodesRemainSeparateAndAnUndoSuppressesStaleServerState() {
        val media=MediaItem("tt1234567","tt1234567",type="series",name="Show")
        val one=trackingEntity(media,Episode(1,1,"One"),true,10,"account").copy(updatedAt=100)
        val two=trackingEntity(media,Episode(1,2,"Two"),true,20,"account").copy(updatedAt=101)
        val remote=listOf(JournalEntry(media.copy(season=1,episode=1),5,scope="account"))
        assertEquals(2,mergeJournal(listOf(one,two),remote,"account").size)
        assertEquals(1,mergeJournal(listOf(one.copy(played=false),two),remote,"account").size)
        assertEquals(0,mergeJournal(listOf(one),remote,"different-account").size)
    }
    @Test fun backdatedManualEditKeepsServerArtworkAndDoesNotLosePriority() {
        val movie=MediaItem("tt1234567","tt1234567",name="Movie")
        val record=trackingEntity(movie,null,true,10,"account").copy(updatedAt=200)
        val remote=JournalEntry(movie.copy(poster="https://example.invalid/poster",jellyfinId="jf-id"),100,source="Jellyfin",scope="account")
        val entry=mergeJournal(listOf(record),listOf(remote),"account").single()
        assertEquals(10L,entry.watchedAt);assertEquals(remote.media.poster,entry.media.poster);assertEquals("jf-id",entry.media.jellyfinId)
    }
    @Test fun localDayAndPeriodFiltersRespectTimezoneAndExcludeUndatedRowsFromRecentPeriods() {
        val zone=ZoneId.of("America/Los_Angeles");val time=Instant.parse("2026-10-26T01:00:00Z").toEpochMilli();val item=MediaItem("one",name="One")
        assertEquals(LocalDate.of(2026,10,25),journalDay(time,zone))
        val rows=listOf(JournalEntry(item,time),JournalEntry(item.copy(id="two"),null))
        assertEquals(1,filterJournal(rows,JournalPreferences(period="week"),LocalDate.of(2026,10,26),zone).size);assertEquals(2,filterJournal(rows,JournalPreferences(),LocalDate.of(2026,10,26),zone).size)
    }
    @Test fun watchedDatesRejectFutureDaysAndUseLocalNoonForPastDates() {
        val zone=ZoneId.of("Europe/Bucharest");val today=LocalDate.of(2026,10,26);val time=watchedOn(today.minusDays(1),today,zone)
        assertEquals(12,Instant.ofEpochMilli(time).atZone(zone).hour)
        try{watchedOn(today.plusDays(1),today,zone);fail("Future dates rejected")}catch(_:IllegalArgumentException){}
    }
    @Test fun watchlistFilteringKeepsUnknownCacheAndMissingRatingsOutOfTrustedResults() {
        val items=listOf(MediaItem("one",name="Árvíz",rating=8.0,addedAt=10),MediaItem("two",type="series",name="Show",rating=null,addedAt=20))
        assertEquals("one",filterSavedTitles(items,WatchlistPreferences(query="arviz"),emptySet(),emptySet()).single().id)
        assertEquals("two",filterSavedTitles(items,WatchlistPreferences(hideWatched=true),setOf("one"),emptySet()).single().id)
        assertEquals("one",filterSavedTitles(items,WatchlistPreferences(cachedOnly=true),emptySet(),setOf("movie:one")).single().id)
        assertEquals("one",filterSavedTitles(items,WatchlistPreferences(sort="rating"),emptySet(),emptySet()).first().id)
        assertEquals("two",filterSavedTitles(items,WatchlistPreferences(),emptySet(),emptySet()).first().id)
    }
}
