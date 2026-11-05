package app.stak.nativeapp.data

import com.google.gson.JsonParser
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RefinementDataTest{
    private fun json(s:String)=JsonParser.parseString(s).asJsonObject
    private val today=LocalDate.of(2026,11,4)
    private val providers=json("""{"flatrate":[{"provider_id":8}]}""")
    private fun movie(date:String="2026-10-20",country:String="US",type:Int=4)=json("""{"id":1,"status":"Released","release_dates":{"results":[{"iso_3166_1":"$country","release_dates":[{"type":$type,"release_date":"${date}T00:00:00Z"}]}]}}""")
    @Test fun cinemaOnlyFutureAndWrongRegionNeverBecomeStreamingPicks(){
        assertTrue(streamingConfirmed(movie(),providers,"movie","US",today))
        assertFalse(streamingConfirmed(movie(type=3),providers,"movie","US",today))
        assertFalse(streamingConfirmed(movie("2026-12-01"),providers,"movie","US",today))
        assertFalse(streamingConfirmed(movie(country="HU"),providers,"movie","US",today))
        assertFalse(streamingConfirmed(movie(),json("""{"rent":[{"provider_id":8}]}"""),"movie","US",today))
        assertFalse(streamingConfirmed(movie(),null,"movie","US",today))
        assertFalse(streamingConfirmed(movie(),json("""{"flatrate":[{}]}"""),"movie","US",today))
    }
    @Test fun seriesNeedsAiredDateAndActualLocalStreamingProvider(){
        assertTrue(streamingConfirmed(json("""{"first_air_date":"2025-01-01"}"""),providers,"series","US",today))
        assertFalse(streamingConfirmed(json("""{"first_air_date":"2027-01-01"}"""),providers,"series","US",today))
    }
    @Test fun picksKeepPopularityOrderButSkipUnverifiedTitlesAndUseRealLogos()=runBlocking{
        val paths=Collections.synchronizedList(mutableListOf<String>())
        val api=StreamingPicks({"fixture"},{url,params,_->
            paths+=url
            when{
                url.endsWith("/discover/movie")->{assertEquals("popularity.desc",params["sort_by"]);assertEquals("US",params["watch_region"]);assertEquals("4",params["with_release_type"]);json("""{"results":[{"id":1},{"id":2},{"id":3}]}""")}
                url.endsWith("/watch/providers")->if(url.contains("/movie/3/"))json("""{"results":{"US":{"rent":[{"provider_id":8}]}}}""")else json("""{"results":{"US":{"flatrate":[{"provider_id":8}]}}}""")
                else->{val id=url.substringAfterLast('/').toInt();movie(if(id==1)"2026-12-01"else "2026-10-20").apply{addProperty("id",id);addProperty("title","Film $id");add("images",json("""{"logos":[{"iso_639_1":"en","file_path":"/logo.png"}]}"""));add("external_ids",json("""{"imdb_id":"tt1234567"}"""))}}
            }
        },{today})
        val result=api.load("movie","US")
        assertEquals(listOf("Film 2"),result.items.map{it.name});assertTrue(result.items.single().logo!!.endsWith("/logo.png"));assertEquals(7,paths.size)
    }
    @Test fun imdbFallbackRequiresAnExactIdentityAndExplicitImdbField()=runBlocking{
        val good=ImdbRating{_,_,_->json("""{"meta":{"id":"tt1234567","imdbRating":"8.4","vote_average":9.1}}""")}
        assertEquals(8.4,good.get("movie","tt1234567")!!,.001)
        assertNull(ImdbRating{_,_,_->json("""{"meta":{"id":"tt7654321","imdbRating":"8.4"}}""")}.get("movie","tt1234567"))
        assertNull(ImdbRating{_,_,_->json("""{"meta":{"id":"tt1234567","vote_average":9.1}}""")}.get("movie","tt1234567"))
        assertNull(ImdbRating{_,_,_->json("""{"meta":{"id":"tt1234567","imdbRating":"0"}}""")}.get("movie","tt1234567"))
    }
    private fun hash(n:Int)=n.toString().padStart(40,'0')
    private fun unknown(n:Int)=SourceRelease("Release $n",torznabIndexerId="fixture",torrentFileUrl="https://indexer.invalid/$n",sourceToken="$n")
    @Test fun fastMetadataPublishesBeforeASlowReleaseAndDuplicateProviderBatchIsSafe()=runBlocking{
        val slow=CompletableDeferred<Unit>();val first=CompletableDeferred<Unit>()
        val session=CachedSourceSession("key",{h,_->h.toSet()},{s,_->if(s.sourceToken=="2")slow.await();PreparedSource(s.copy(infoHash=hash(s.sourceToken!!.toInt())),NabMaterial.Magnet(parseMagnetInput(hash(s.sourceToken!!.toInt()))))})
        session.accept(listOf(unknown(1),unknown(2)))
        val worker=async{session.more{if(it.releases.any{r->r.infoHash==hash(1)})first.complete(Unit)}}
        withTimeout(2000){first.await()};assertFalse(worker.isCompleted)
        session.accept(listOf(unknown(1)))
        session.accept(listOf(SourceRelease("Known from another provider",infoHash=hash(3))))
        session.known{};assertEquals(2,session.snapshot().releases.size)
        slow.complete(Unit);worker.await();assertEquals(3,session.snapshot().releases.size)
    }
    @Test fun fastAddonPublishesWhileASlowerAddonIsStillSearching()=runBlocking{
        val gate=CountDownLatch(1);val partial=CompletableDeferred<Unit>()
        val client=AddonSourceClient({listOf(StreamAddon("fast","Fast","https://fast.invalid/manifest.json"),StreamAddon("slow","Slow","https://slow.invalid/manifest.json"))}){url->
            if(url.contains("slow.invalid"))check(gate.await(3,TimeUnit.SECONDS))
            json("""{"streams":[{"title":"Release","infoHash":"${hash(if(url.contains("fast.invalid"))1 else 2)}"}]}""")
        }
        val job=async{client.search("movie","tt1234567"){r->if(r.releases.any{it.addonName=="Fast"})partial.complete(Unit)}}
        try{withTimeout(2000){partial.await()};assertFalse(job.isCompleted)}finally{gate.countDown()}
        assertEquals(2,job.await().releases.size)
    }

    @Test fun homeLogoResolvesTheExactImdbIdentityWithoutTitleGuessing()=runBlocking{
        val paths=mutableListOf<String>()
        val catalog=DirectCatalog(tmdbKey={"fixture"},request={url,params,_->paths+=url
            if(url.endsWith("/find/tt1234567")){assertEquals("imdb_id",params["external_source"]);json("""{"movie_results":[{"id":42}]}""")}
            else json("""{"id":42,"external_ids":{"imdb_id":"tt1234567"},"images":{"logos":[{"iso_639_1":"en","file_path":"/film-logo.png"}]}}""")
        })
        val result=catalog.heroMetadata(MediaItem("tt1234567",imdbId="tt1234567",name="Film"))
        assertEquals("tt1234567",result.mediaKey);assertTrue(result.logo!!.endsWith("/film-logo.png"));assertEquals(2,paths.size)
    }
    @Test fun mismatchedMetadataNeverBrandsTheWrongFilm()=runBlocking{
        val catalog=DirectCatalog(tmdbKey={"fixture"},request={_,_,_->json("""{"external_ids":{"imdb_id":"tt7654321"},"images":{"logos":[{"iso_639_1":"en","file_path":"/wrong.png"}]}}""")})
        assertNull(catalog.heroMetadata(MediaItem("tmdb:42",imdbId="tt1234567",name="Film")).logo)
    }
}
