package app.stak.nativeapp.data

import com.google.gson.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HomeIntelligenceTest {
    private val today=LocalDate.of(2026,10,28)
    private fun obj(raw:String)=JsonParser.parseString(raw).asJsonObject
    private fun array(raw:String)=JsonParser.parseString(raw).asJsonArray
    private val movie=MediaItem("tmdb:42",name="Film")
    private val show=MediaItem("tmdb:9",type="series",name="Show")
    @Test fun traktTrendingPreservesProviderOrderAndUsesStableExternalIds(){
        val result=parseTraktItems(array("""[{"watchers":99,"movie":{"title":"First","year":2026,"ids":{"imdb":"tt1234567","tmdb":42}}},{"watchers":2,"movie":{"title":"Second","ids":{"imdb":"tt7654321"}}}]"""),"movie")
        assertEquals(listOf("First","Second"),result.map{it.name});assertEquals("tmdb:42",result.first().id);assertEquals("tt1234567",result.first().mediaKey);assertEquals("tt7654321",result.last().id);assertNull(result.first().rating)
    }
    @Test fun anticipatedShowsUnwrapShowNotMovie(){val item=parseTraktItems(array("""[{"list_count":10,"show":{"title":"Next show","ids":{"tmdb":9}}}]"""),"series").single();assertTrue(item.isSeries);assertEquals("tmdb:9",item.id)}
    @Test fun malformedItemsCannotBecomeTitleMatchedOrBrokenRoutes(){assertTrue(parseTraktItems(array("""[{"movie":{"title":"No IDs","ids":{"trakt":3}}},{"movie":{"title":"Bad ID","ids":{"tmdb":"../bad"}}},null]"""),"movie").isEmpty())}
    @Test fun duplicateTraktIdsDoNotBreakLazyKeys(){val row="""{"movie":{"title":"Film","ids":{"imdb":"tt1234567"}}}""";assertEquals(1,parseTraktItems(array("[$row,$row]"),"movie").size)}
    @Test fun catalogUsesRequiredEndpointPaginationAndCachesWithinAccount()=runBlocking{
        var calls=0;val c=TraktCatalog({"synthetic-client"},{path,params,key->calls++;assertEquals("/shows/anticipated",path);assertEquals("synthetic-client",key);assertEquals("2",params["page"]);assertEquals("20",params["limit"]);assertEquals("full",params["extended"]);TraktPage(array("""[{"show":{"title":"Show","ids":{"tmdb":9}}}]"""),7)},{it})
        assertEquals(7,c.list("series","anticipated",2).totalPages);assertEquals("trakt",c.list("series","anticipated",2).source);assertEquals(1,calls)
    }
    @Test fun changingClientIdDoesNotReusePreviousClientCache()=runBlocking{var key="one";var calls=0;val c=TraktCatalog({key},{_,_,_->calls++;TraktPage(JsonArray(),1)},{it});c.list("movie","trending");key="two";c.list("movie","trending");assertEquals(2,calls)}
    @Test fun missingClientNeverFallsBackToFabricatedTraktData()=runBlocking{val c=TraktCatalog({""},{_,_,_->throw AssertionError("No network")},{it});assertTrue(runCatching{c.list("movie","trending")}.isFailure)}
    @Test fun artworkFailureKeepsRankedCandidates()=runBlocking{val c=TraktCatalog({"key"},{_,_,_->TraktPage(array("""[{"movie":{"title":"Film","ids":{"tmdb":42}}}]"""),1)},{throw java.io.IOException()});assertEquals("Film",c.list("movie","trending").items.single().name)}
    @Test fun cancelledArtworkDoesNotTurnIntoSuccessfulEmptyCatalog()=runBlocking{val c=TraktCatalog({"key"},{_,_,_->TraktPage(array("""[{"movie":{"title":"Film","ids":{"tmdb":42}}}]"""),1)},{throw CancellationException()});assertTrue(runCatching{c.list("movie","trending")}.exceptionOrNull() is CancellationException)}
    @Test fun countdownUsesCalendarDaysAcrossMonthAndYear(){assertEquals("Today",releaseCountdown(today,today));assertEquals("Tomorrow",releaseCountdown(today.plusDays(1),today));assertEquals("In 3 days",releaseCountdown(today.plusDays(3),today));assertEquals("In 2 days",releaseCountdown(LocalDate.of(2027,1,1),LocalDate.of(2026,12,30)));assertEquals("Released",releaseCountdown(today.minusDays(1),today))}
    @Test fun movieDigitalReleaseIsLabeledAndRegionScoped(){val data=obj("""{"results":[{"iso_3166_1":"US","release_dates":[{"type":3,"release_date":"2026-10-29T00:00:00Z"},{"type":4,"release_date":"2026-10-31T00:00:00Z"}]},{"iso_3166_1":"GB","release_dates":[{"type":4,"release_date":"2026-10-28T00:00:00Z"}]}]}""");assertEquals(today.plusDays(3) to "Digital",upcomingMovieRelease(data,"US",today,today.plusDays(30)))}
    @Test fun cinemaIsNeverPresentedAsDigital(){assertEquals(today.plusDays(1) to "In cinemas",upcomingMovieRelease(obj("""{"results":[{"iso_3166_1":"US","release_dates":[{"type":3,"release_date":"2026-10-29"}]}]}"""),"US",today,today.plusDays(30)))}
    @Test fun unknownAndPastDatesProduceNoCountdown(){assertNull(upcomingMovieRelease(obj("""{"results":[{"iso_3166_1":"US","release_dates":[{"type":4,"release_date":null},{"type":4,"release_date":"2020-01-01"}]}]}"""),"US",today,today.plusDays(30)))}
    @Test fun watchlistSeriesUsesNextEpisodeWithoutFetchingHistoricalSeasons()=runBlocking{
        val calls=mutableListOf<String>();val c=CalendarRepository({"key"}){url,_,_->calls+=url;assertTrue(url.endsWith("/tv/9"));obj("""{"next_episode_to_air":{"season_number":3,"episode_number":4,"name":"Next","air_date":"2026-10-31"}}""")}
        val event=c.upcoming(listOf(show),today).events.single();assertEquals(3,event.season);assertEquals(4,event.episode);assertEquals(today.plusDays(3),event.date);assertEquals(1,calls.size)
    }
    @Test fun upcomingSeasonCanSupplyEpisodeIfNextEpisodeIsMissing()=runBlocking{
        val c=CalendarRepository({"key"}){url,_,_->if(url.endsWith("/tv/9"))obj("""{"seasons":[{"season_number":1,"air_date":"2020-01-01"},{"season_number":2,"air_date":"2026-10-31"}]}""")else{assertTrue(url.endsWith("/season/2"));obj("""{"episodes":[{"season_number":2,"episode_number":1,"name":"Return","air_date":"2026-10-31"}]}""")}}
        assertEquals(1,c.upcoming(listOf(show),today).events.single().episode)
    }
    @Test fun undatedSeriesIsNotGivenAnInventedEpisode()=runBlocking{val c=CalendarRepository({"key"}){_,_,_->obj("""{"next_episode_to_air":null,"seasons":[]}""")};assertTrue(c.upcoming(listOf(show),today).events.isEmpty())}
    @Test fun movieAndShowFailuresAreIsolated()=runBlocking{val c=CalendarRepository({"key"}){url,_,_->if(url.endsWith("/tv/9"))obj("""{"next_episode_to_air":{"season_number":1,"episode_number":1,"air_date":"2026-10-31"}}""")else throw java.io.IOException()};val feed=c.upcoming(listOf(movie,show),today);assertEquals(1,feed.events.size);assertEquals(1,feed.issues.size);assertEquals(2,feed.checked)}
    @Test fun emptyWatchlistNeverRequestsMetadata()=runBlocking{assertTrue(CalendarRepository({""}){_,_,_->throw AssertionError()}.upcoming(emptyList(),today).events.isEmpty())}
    @Test fun homeShowsOnlyNearestEpisodePerTitleWithinThirtyDays(){val first=ReleaseEvent(show,today.plusDays(3),"First",1,2);val next=first.copy(date=today.plusDays(10),episode=3);val expired=ReleaseEvent(movie,today.minusDays(1),"Old");assertEquals(listOf(first),upcomingHomeEvents(ReleaseFeed(listOf(next,first,first,expired),emptyList(),2),today))}
    @Test fun oldHomePreferencesKeepDisabledRowsAndReceiveNewSections(){val rows=HomeSections.read("""[{"id":"continue","on":true},{"id":"trending_movie","on":false},{"id":"watchlist","on":true}]""");assertFalse(rows.first{it.id=="trending_movie"}.enabled);assertEquals("coming_up",rows[1].id);assertEquals(1,rows.count{it.id=="anticipated_movie"});assertEquals(1,rows.count{it.id=="anticipated_series"})}
    @Test fun comingUpLogoPrefersEnglishAndPreservesExistingArtwork(){
        val data=obj("""{"images":{"logos":[{"iso_639_1":null,"file_path":"/neutral.png"},{"iso_639_1":"fr","file_path":"/french.png"},{"iso_639_1":"en","file_path":"/english.png"}]}}""")
        assertEquals("https://image.tmdb.org/t/p/w500/english.png",upcomingLogo(data));assertEquals("https://existing/logo.png",upcomingLogo(data,"https://existing/logo.png"))
    }
    @Test fun comingUpLogoUsesNeutralButNeverInventsMissingArtwork(){assertEquals("https://image.tmdb.org/t/p/w500/neutral.png",upcomingLogo(obj("""{"images":{"logos":[{"file_path":"/neutral.png"}]}}""")));assertNull(upcomingLogo(obj("""{"images":{"logos":[{"iso_639_1":"fr","file_path":"/fr.png"}]}}""")));assertNull(upcomingLogo(obj("{}")))}
    @Test fun upcomingSummaryRequestsLogosWithEpisodeInOneCall()=runBlocking{
        var calls=0;val c=CalendarRepository({"key"}){_,params,_->calls++;assertEquals("images",params["append_to_response"]);assertEquals("en,null",params["include_image_language"]);obj("""{"images":{"logos":[{"iso_639_1":"en","file_path":"/logo.png"}]},"next_episode_to_air":{"season_number":1,"episode_number":2,"air_date":"2026-10-31"}}""")}
        assertEquals("https://image.tmdb.org/t/p/w500/logo.png",c.upcoming(listOf(show),today).events.single().media.logo);assertEquals(1,calls)
    }
}
