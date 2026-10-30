package app.stak.nativeapp.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DiscoveryRequestTest {
    private val today = LocalDate.of(2026,10,25)
    @Test fun newReleasesCoverNinetyDaysRatherThanTheWholeYear() {
        val request = discoveryRequest("movie","new",1,DiscoveryFilters(),"US",today)
        assertEquals("/discover/movie",request.path)
        assertEquals("2026-07-27",request.params["primary_release_date.gte"])
        assertEquals("2026-10-25",request.params["primary_release_date.lte"])
        assertEquals("primary_release_date.desc",request.params["sort_by"])
    }
    @Test fun upcomingIsFutureAndNotAnAliasForNewReleases() {
        val request = discoveryRequest("series","upcoming",1,DiscoveryFilters(),"RO",today)
        assertEquals("2026-10-25",request.params["first_air_date.gte"])
        assertEquals("2027-10-25",request.params["first_air_date.lte"])
        assertEquals("first_air_date.asc",request.params["sort_by"])
        assertFalse(request.params.keys.any { it.startsWith("primary_release_date") })
    }
    @Test fun hiddenGemsRequireVotesRatingAndLimitedPopularity() {
        val p = discoveryRequest("movie","hidden",1,DiscoveryFilters(),"US",today).params
        assertEquals("100",p["vote_count.gte"]); assertEquals("7",p["vote_average.gte"]); assertEquals("30",p["popularity.lte"])
    }
    @Test fun topRatedExcludesUnreleasedTitlesAndLowVoteCounts() {
        val p = discoveryRequest("movie","top",1,DiscoveryFilters(yearTo="2030"),"US",today).params
        assertEquals("200",p["vote_count.gte"]); assertEquals("2026-10-25",p["primary_release_date.lte"])
        assertEquals("vote_average.desc",p["sort_by"])
    }
    @Test fun providerLanguageGenreYearsAndSortReachTheProvider() {
        val f = DiscoveryFilters(genre="18",language="hu",yearFrom="1990",yearTo="1999",provider="8|9",sort="primary_release_date.asc",certification="PG-13")
        val r = discoveryRequest("movie","popular",2,f,"HU",today)
        assertEquals("/discover/movie",r.path)
        assertEquals("18",r.params["with_genres"]); assertEquals("hu",r.params["with_original_language"])
        assertEquals("1990-01-01",r.params["primary_release_date.gte"]); assertEquals("1999-12-31",r.params["primary_release_date.lte"])
        assertEquals("8|9",r.params["with_watch_providers"]); assertEquals("HU",r.params["watch_region"])
        assertEquals("flatrate|free|ads",r.params["with_watch_monetization_types"]); assertEquals("PG-13",r.params["certification"])
    }
    @Test fun trendingRetainsTrendingInsteadOfBecomingPopular() {
        val r=discoveryRequest("movie","trending",1,DiscoveryFilters(genre="18"),"US",today)
        assertEquals("/trending/movie/week",r.path); assertTrue(r.pageFilters)
        assertFalse(r.params.containsKey("with_genres"))
    }
    @Test fun impossibleDateIntersectionMakesNoProviderRequestNecessary() {
        assertTrue(discoveryRequest("movie","upcoming",1,DiscoveryFilters(yearFrom="1990",yearTo="1999"),"US",today).empty)
    }
    @Test fun invalidYearsAreRejectedBeforeRequests() {
        assertNotNull(DiscoveryFilters(yearFrom="2101").validationError())
        assertNotNull(DiscoveryFilters(yearFrom="2025",yearTo="2020").validationError())
        assertNotNull(DiscoveryFilters(yearFrom="20").validationError())
        assertNull(DiscoveryFilters(yearFrom="1880",yearTo="2100").validationError())
    }
    @Test fun removingYearChipClearsBothBoundsWithoutClearingOtherFilters() {
        val f=DiscoveryFilters(yearFrom="2000",yearTo="2009",language="hu",hideWatched=true).without("year")
        assertEquals("",f.yearFrom);assertEquals("",f.yearTo);assertEquals("hu",f.language);assertTrue(f.hideWatched)
    }
}
