package app.stak.nativeapp.data

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.util.Collections

class DirectDiscoveryTest {
    private fun json(s:String)=JsonParser.parseString(s).asJsonObject
    private val today={LocalDate.of(2026,10,25)}
    @Test fun filteredSearchPagesEachKindIndependentlyAndStopsExhaustedMovieRequests()=runBlocking {
        val pages=Collections.synchronizedList(mutableListOf<String>())
        val d=DirectDiscovery({"test.key"},{""},{url,p,_->
            if(url.endsWith("external_ids")) json("{}") else {
                val kind=if(url.endsWith("/search/movie"))"movie" else "tv"
                val n=p["page"]!!.toInt();pages.add("$kind:$n")
                if(kind=="movie") json("""{"results":[{"id":1,"title":"Movie","release_date":"2020-01-01"}],"total_pages":1}""")
                else json("""{"results":[{"id":${n+1},"name":"Series $n","first_air_date":"2020-01-01"}],"total_pages":3}""")
            }
        },today)
        val filters=DiscoveryFilters(yearFrom="2000")
        val first=d.search("all","Example",1,filters,"US")
        assertEquals(2,first.items.size);assertEquals(mapOf("series" to 2),first.nextTypePages)
        val second=d.search("all","Example",2,filters,"US",first.nextTypePages)
        assertEquals("Series 2",second.items.single().name)
        assertEquals(mapOf("series" to 3),second.nextTypePages)
        assertEquals(1,pages.count{it.startsWith("movie:")})
    }
    @Test fun partialFilteredSearchRetainsSuccessfulKindAndReportsFailure()=runBlocking {
        val d=DirectDiscovery({"test.key"},{""},{url,_,_->when {
            url.endsWith("/search/movie")->throw IOException("unavailable")
            url.endsWith("external_ids")->json("{}")
            else->json("""{"results":[{"id":2,"name":"Available series","first_air_date":"2020-01-01"}],"total_pages":1}""")
        }},today)
        val result=d.search("all","Example",1,DiscoveryFilters(yearFrom="2000"),"US")
        assertEquals("Available series",result.items.single().name)
        assertEquals(listOf("Some TMDb search results could not load."),result.partialErrors)
    }
    @Test fun malformedCatalogIsNotReportedAsAnEmptySuccess()=runBlocking {
        val d=DirectDiscovery({"test.key"},{""},{_,_,_->json("{}")},today)
        try { d.discover("movie","popular",1,DiscoveryFilters(),"US");fail("Expected invalid response") }catch(_:IOException){}
    }
    @Test fun quotaResponseStopsRepeatedOmdbRequestsUntilCooldown()=runBlocking {
        var calls=0
        val d=DirectDiscovery({"test.key"},{"quota-test"},{url,_,_->when{
            url.contains("omdbapi")-> { calls++;json("""{"Response":"False","Error":"Request limit reached!"}""") }
            url.endsWith("external_ids")->json("""{"imdb_id":"tt1234567"}""")
            else->json("""{"results":[{"id":1,"title":"Title"}],"total_pages":1}""")
        }},today)
        repeat(2) { assertEquals(1,d.discover("movie","popular",1,DiscoveryFilters(imdbRating="7"),"US").ratingErrors) }
        assertEquals(1,calls)
    }
    @Test fun validEmptyResultStaysEmpty()=runBlocking {
        val d=DirectDiscovery({"test.key"},{""},{_,_,_->json("""{"results":[],"total_pages":1}""")},today)
        assertTrue(d.discover("movie","popular",1,DiscoveryFilters(),"US").items.isEmpty())
    }
    @Test fun failureDoesNotBecomeAnEmptySuccess()=runBlocking {
        val d=DirectDiscovery({"test.key"},{""},{_,_,_->throw IOException("unavailable")},today)
        try { d.discover("movie","popular",1,DiscoveryFilters(),"US");fail("Expected failure") }catch(_:IOException){}
    }
    @Test fun tvCertificationUsesContentRatingsInsteadOfMovieCertificationParams()=runBlocking {
        val paths=Collections.synchronizedList(mutableListOf<String>())
        val d=DirectDiscovery({"test.key"},{""},{url,_,_->
            paths.add(url)
            if(url.endsWith("/content_ratings"))json("""{"results":[{"iso_3166_1":"US","rating":"TV-14"}]}""")
            else json("""{"results":[{"id":1,"name":"Show"}],"total_pages":1}""")
        },today)
        val r=d.discover("series","popular",1,DiscoveryFilters(certification="TV-14"),"US")
        assertEquals(1,r.items.size);assertTrue(paths.any{it.endsWith("/tv/1/content_ratings")})
    }
    @Test fun trendingServiceFilterChecksOnlyRegionalSubscriptionFreeOrAdSupportedAvailability()=runBlocking {
        val d=DirectDiscovery({"test.key"},{""},{url,_,_->when{
            url.endsWith("/1/watch/providers")->json("""{"results":{"HU":{"rent":[{"provider_id":8}]}}}""")
            url.endsWith("/2/watch/providers")->json("""{"results":{"US":{"flatrate":[{"provider_id":8}]}}}""")
            url.endsWith("/3/watch/providers")->json("""{"results":{"HU":{"ads":[{"provider_id":8}]}}}""")
            else->json("""{"results":[{"id":1,"title":"Rental"},{"id":2,"title":"Wrong region"},{"id":3,"title":"Accepted"}],"total_pages":1}""")
        }},today)
        val r=d.discover("movie","trending",1,DiscoveryFilters(provider="8"),"HU")
        assertEquals("Accepted",r.items.single().name);assertNotNull(r.notice)
    }
    @Test fun imdbThresholdUsesVerifiedOmdbRatherThanTmdbScoreAndCachesRating()=runBlocking {
        var ratingRequests=0
        val d=DirectDiscovery({"test.key"},{"omdb-test"},{url,_,_->when{
            url.contains("omdbapi")-> { ratingRequests++;json("""{"Response":"True","imdbRating":"6.5"}""") }
            url.endsWith("external_ids")->json("""{"imdb_id":"tt1234567"}""")
            else->json("""{"results":[{"id":1,"title":"High TMDb only","vote_average":9.9}],"total_pages":1}""")
        }},today)
        assertTrue(d.discover("movie","popular",1,DiscoveryFilters(imdbRating="8"),"US").items.isEmpty())
        val r=d.discover("movie","popular",1,DiscoveryFilters(imdbRating="6"),"US")
        assertEquals(6.5,r.items.single().rating!!,.001);assertTrue(r.verifiedImdb);assertEquals(1,ratingRequests)
    }
    @Test fun missingOmdbKeyFailsBeforeConsumingProviderRequests()=runBlocking {
        var calls=0
        val d=DirectDiscovery({"test.key"},{""},{_,_,_->calls++;json("{}")},today)
        try { d.discover("movie","popular",1,DiscoveryFilters(imdbRating="7"),"US");fail("Expected missing key") }catch(_:IOException){}
        assertEquals(0,calls)
    }
    @Test fun ratingProviderFailureExcludesUnverifiedTitleAndReportsFailure()=runBlocking {
        val d=DirectDiscovery({"test.key"},{"omdb-test"},{url,_,_->when{
            url.contains("omdbapi")->throw IOException("quota")
            url.endsWith("external_ids")->json("""{"imdb_id":"tt1234567"}""")
            else->json("""{"results":[{"id":1,"title":"Unknown"}],"total_pages":1}""")
        }},today)
        val r=d.discover("movie","popular",1,DiscoveryFilters(imdbRating="7"),"US")
        assertTrue(r.items.isEmpty());assertEquals(1,r.ratingErrors)
    }
    @Test fun hideFlagsResolveIdentifiersWithoutHidingUnmatchedTitles()=runBlocking {
        val d=DirectDiscovery({"test.key"},{""},{url,_,_->when{
            url.endsWith("/1/external_ids")->json("""{"imdb_id":"tt1234567"}""")
            url.endsWith("/2/external_ids")->throw IOException("no match")
            else->json("""{"results":[{"id":1,"title":"Known"},{"id":2,"title":"Unknown"}],"total_pages":1}""")
        }},today)
        val items=d.discover("movie","popular",1,DiscoveryFilters(hideWatched=true),"US").items
        assertEquals("tt1234567",items[0].mediaKey);assertEquals("tmdb:2",items[1].mediaKey)
    }
    @Test fun imdbQueryUsesTmdbFindAndPreservesIdentity()=runBlocking {
        val d=DirectDiscovery({"test.key"},{""},{url,p,token->
            assertTrue(url.endsWith("/find/tt1234567"));assertEquals("imdb_id",p["external_source"]);assertEquals("test.key",token)
            json("""{"movie_results":[{"id":1,"title":"Found"}],"tv_results":[]}""")
        },today)
        assertEquals("tt1234567",d.search("movie","tt1234567",1,DiscoveryFilters(),"US").items.single().mediaKey)
    }
    @Test fun titleYearSearchRetriesWithoutYearAndExcludesOtherYears()=runBlocking {
        val queries=mutableListOf<String>()
        val d=DirectDiscovery({"test.key"},{""},{url,p,_->
            if (url.endsWith("external_ids")) json("""{"imdb_id":"tt1160419"}""") else {
                queries.add(p["query"].orEmpty())
                json("""{"results":[{"id":1,"title":"Dune","release_date":"2021-10-22"},{"id":2,"title":"Dune","release_date":"1984-12-14"}],"total_pages":1}""")
            }
        },today)
        val r=d.search("movie","Dune 2021",1,DiscoveryFilters(),"US")
        assertEquals(listOf("Dune 2021","Dune"),queries);assertEquals("2021",r.items.single().year)
    }
    @Test fun fallbackRejectsUnsupportedFiltersRatherThanSilentlyIgnoringThem()=runBlocking {
        val d=DirectDiscovery({""},{""},{_,_,_->json("{}")},today)
        try { d.filterFallback(DiscoverResult(),DiscoveryFilters(language="hu"));fail("Expected unsupported filter") }catch(_:IOException){}
    }
    @Test fun topRatedDropsFutureAndLowVoteResultsEvenIfProviderReturnsThem()=runBlocking {
        val d=DirectDiscovery({"test.key"},{""},{_,_,_->json("""{"results":[{"id":1,"title":"Future","release_date":"2027-01-01","vote_count":900},{"id":2,"title":"Few votes","release_date":"2020-01-01","vote_count":20},{"id":3,"title":"Valid","release_date":"2020-01-01","vote_count":201}],"total_pages":1}""")},today)
        assertEquals("Valid",d.discover("movie","top",1,DiscoveryFilters(),"US").items.single().name)
    }
}
