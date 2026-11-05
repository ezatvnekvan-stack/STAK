package app.stak.nativeapp.data

import com.google.gson.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class DiscoveryPerformanceTest {
    private fun array(raw:String)=JsonParser.parseString(raw).asJsonArray
    private fun json(raw:String)=JsonParser.parseString(raw).asJsonObject
    @Test fun fastSeriesDoesNotRequestAnySeasonButRetainsAllSeasonChoices()=runBlocking {
        val paths=mutableListOf<String>()
        val catalog=DirectCatalog(tmdbKey={"fixture"}){url,_,_->paths+=url;json("""{"id":42,"name":"Fixture","seasons":[{"season_number":1},{"season_number":2},{"season_number":3}]}""")}
        val core=catalog.details("series","tmdb:42",true)
        assertEquals(listOf(1,2,3),core.seasons);assertTrue(core.episodes.isEmpty());assertEquals(1,paths.size)
    }
    @Test fun selectingASeasonOnlyRequestsThatSeasonAndKeepsCorrectNumbers()=runBlocking {
        val paths=mutableListOf<String>()
        val catalog=DirectCatalog(tmdbKey={"fixture"}){url,_,_->paths+=url;json("""{"episodes":[{"episode_number":2,"name":"Second","still_path":"/still.jpg"}]}""")}
        val episodes=catalog.season("tmdb:42",3)
        assertEquals(listOf("https://api.themoviedb.org/3/tv/42/season/3"),paths)
        assertEquals(3,episodes.single().season);assertEquals(2,episodes.single().episode);assertTrue(episodes.single().image!!.contains("/w300/"))
    }
    @Test fun listParsingDropsPrivateMalformedAndDuplicateRows(){
        val rows=array("""[{"list":{"name":"Cinema","ids":{"trakt":42},"privacy":"public","item_count":9},"user":{"username":"creator"}},{"list":{"name":"Private","ids":{"trakt":43},"privacy":"private"}},{"list":{"name":"Broken","ids":{"trakt":0}}},{"list":{"name":"Duplicate","ids":{"trakt":42}}}]""")
        val lists=parsePublicTraktLists(rows)
        assertEquals(1,lists.size);assertEquals(42L,lists.single().id);assertEquals("creator",lists.single().owner);assertEquals(9,lists.single().count)
    }
    @Test fun mixedListPreservesRankAndSkipsPeopleAndUnresolvableTitles(){
        val titles=parsePublicListTitles(array("""[{"rank":1,"show":{"title":"First","ids":{"imdb":"tt12345"}}},{"rank":2,"person":{"name":"Someone","ids":{"trakt":7}}},{"rank":3,"movie":{"title":"Second","ids":{"tmdb":8}}},{"rank":4,"movie":{"title":"Unknown","ids":{}}}]"""))
        assertEquals(listOf("First","Second"),titles.map{it.name});assertEquals(listOf("series","movie"),titles.map{it.type})
    }
    @Test fun browseSearchAndItemEndpointsArePaginatedAndReadOnly()=runBlocking {
        val paths=mutableListOf<String>()
        val api=TraktLists({"fixture-client"},{path,params,key->
            assertEquals("fixture-client",key);assertEquals("2",params["page"]);paths+=path
            when(path){"/search/list"->assertEquals("noir",params["query"]);"/lists/42/items"->{assertEquals("rank",params["sort_by"]);assertEquals("asc",params["sort_how"])}}
            TraktPage(JsonArray(),4)
        },{it})
        api.browse("popular",page=2);api.browse("trending",page=2);api.browse("popular","noir",2);api.titles(42,2)
        assertEquals(listOf("/lists/popular","/lists/trending","/search/list","/lists/42/items"),paths)
    }
    @Test fun missingKeyAndInvalidIdsFailBeforeTransport()=runBlocking {
        var calls=0;val api=TraktLists({""},{_,_,_->calls++;TraktPage(JsonArray(),1)},{it})
        assertTrue(runCatching{api.browse("popular")}.isFailure);assertTrue(runCatching{api.titles(-1)}.isFailure);assertEquals(0,calls)
    }
    @Test fun savedListsRoundTripWithoutTraktCredentials(){
        val original=listOf(PublicTraktList(42,"Cinema","Creator",12,"A collection"))
        assertEquals(original,decodeSavedLists(encodeSavedLists(original)))
        assertTrue(decodeSavedLists("not-json").isEmpty());assertTrue(cloudBackupKey("saved_public_lists"))
    }
    @Test fun metadataCacheNeverCachesPrivateOrAcquisitionEndpoints(){
        listOf("http://192.168.0.5:8096/Items","https://api.torbox.app/v1/api/torrents/mylist","https://private-tracker/torrent/1").forEach{assertNull(MetadataResponseCache.key(it,emptyMap(),"secret"))}
    }
    @Test fun metadataCacheScopesKeysWithoutStoringCredentialsInFileNames(){
        val url="https://api.themoviedb.org/3/movie/42"
        val one=MetadataResponseCache.key(url,mapOf("api_key" to "fixture-one"),null)!!
        val two=MetadataResponseCache.key(url,mapOf("api_key" to "fixture-two"),null)!!
        assertNotEquals(one,two);assertTrue(one.matches(Regex("[0-9a-f]{64}")))
    }
    @Test fun metadataCacheReplacesOldDataAndIsolatesReturnedObjects(){
        val old=PublicMetadataCache.directory;val root=Files.createTempDirectory("stak-public-cache").toFile()
        try{
            PublicMetadataCache.directory=root
            val key=MetadataResponseCache.key("https://api.themoviedb.org/3/movie/989898",emptyMap(),null)!!
            MetadataResponseCache.write(key,json("""{"title":"First"}"""));MetadataResponseCache.write(key,json("""{"title":"Second"}"""))
            val returned=MetadataResponseCache.read(key)!!;returned.addProperty("title","Mutated")
            assertEquals("Second",MetadataResponseCache.read(key)!!.text("title"))
            assertEquals("Second",JsonParser.parseString(root.resolve("tmdb/$key.json").readText()).asJsonObject.text("title"))
        }finally{PublicMetadataCache.directory=old;root.deleteRecursively()}
    }
    @Test fun traktPublishesRankedTitlesBeforeAnyArtworkFinishes()=runBlocking {
        val first=kotlinx.coroutines.CompletableDeferred<Unit>();val release=kotlinx.coroutines.CompletableDeferred<Unit>()
        val snapshots=java.util.concurrent.CopyOnWriteArrayList<List<MediaItem>>()
        val catalog=TraktCatalog({"fixture"},{_,_,_->TraktPage(array("""[{"movie":{"title":"First","ids":{"tmdb":1}}},{"movie":{"title":"Second","ids":{"tmdb":2}}}]"""),1)},{release.await();it.copy(poster="file:///fixture.png")})
        val result=async { catalog.list("movie","trending",onPartial={snapshots+=it;first.complete(Unit)}) }
        kotlinx.coroutines.withTimeout(3000){first.await()}
        assertEquals(listOf("First","Second"),snapshots.first().map{it.name});assertTrue(snapshots.first().all{it.poster==null})
        assertFalse(result.isCompleted);release.complete(Unit)
        assertTrue(result.await().items.all{it.poster!=null})
    }
    @Test fun privateTrackingAndUntrustedPathsAreRejectedBeforeTransport(){
        listOf("/users/me/history","/sync/history","https://other-host/lists/popular","/lists/42/items/../../sync").forEach{path->
            assertTrue(runCatching{traktCatalogRequest(path,mapOf("page" to "1"),"fixture")}.exceptionOrNull() is IllegalArgumentException)
        }
    }
    @Test fun metadataMemoryBudgetEvictsLargeResponsesInsteadOfGrowingWithoutBound(){
        val previous=PublicMetadataCache.directory
        try{
            PublicMetadataCache.directory=null
            val value=JsonObject().apply{addProperty("fixture","x".repeat(3*1024*1024))}
            val keys=(1..3).map{MetadataResponseCache.key("https://api.themoviedb.org/3/movie/budget$it",emptyMap(),null)!!}
            keys.forEach{MetadataResponseCache.write(it,value)}
            assertNull(MetadataResponseCache.read(keys.first()));assertNotNull(MetadataResponseCache.read(keys.last()))
        }finally{PublicMetadataCache.directory=previous}
    }
}
