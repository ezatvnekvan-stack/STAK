package app.stak.nativeapp.data

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ContinueArtworkTest {
    private fun json(s:String)=JsonParser.parseString(s).asJsonObject
    @Test fun textlessBackdropWinsOverEnglishAndProgressRemainsUnchanged()=runBlocking{
        val item=MediaItem("tmdb:42",name="Film",progress=.42f,positionMs=1000,durationMs=2000)
        val catalog=DirectCatalog({"fixture"}){_,params,_->
            assertEquals("en,null",params["include_image_language"])
            json("""{"images":{"backdrops":[{"file_path":"/neutral.jpg","width":8000,"height":4500},{"file_path":"/small-en.jpg","iso_639_1":"en","width":1920,"height":1080},{"file_path":"/large-en.jpg","iso_639_1":"en","width":3840,"height":2160}]}}""")
        }
        val art=catalog.continueArtwork(item)
        assertFalse(art.embeddedTitle);assertEquals("https://image.tmdb.org/t/p/original/neutral.jpg",art.image)
        assertEquals(.42f,item.progress);assertEquals(1000L,item.positionMs)
    }
    @Test fun missingEnglishUsesNeutralAndSeparateEnglishLogo()=runBlocking{
        val c=DirectCatalog({"fixture"}){_,_,_->json("""{"images":{"backdrops":[{"file_path":"/neutral.jpg","width":3840,"height":2160}],"logos":[{"file_path":"/logo.png","iso_639_1":"en"}]}}""")}
        val art=c.continueArtwork(MediaItem("tmdb:42",name="Film"))
        assertFalse(art.embeddedTitle);assertTrue(art.image!!.endsWith("/original/neutral.jpg"));assertTrue(art.logo!!.endsWith("/original/logo.png"))
    }
    @Test fun missingKeyNeverMakesNetworkRequests()=runBlocking{
        val c=DirectCatalog({""}){_,_,_->error("No request expected")}
        assertEquals("existing",c.continueArtwork(MediaItem("tt1234567",name="Film",background="existing")).image)
    }
    @Test fun episodeFallbackUsesItsExplicitParentBackdropWithoutThumbnailResize()=runBlocking{
        val row=MediaItem("jellyfin:show-42",name="Show",type="series",jellyfinId="episode-9",jellyfinSeriesId="show-42",
            background="https://fixture.invalid/jellyfin/Items/episode-9/Images/Primary?maxWidth=780&tag=episode-tag",season=2,episode=3,positionMs=1200)
        val catalog=DirectCatalog({""}){_,_,_->error("No public request expected")}
        val art=catalog.continueArtwork(row){error("No parent request without TMDb key")}
        assertEquals("https://fixture.invalid/jellyfin/Items/show-42/Images/Backdrop/0",art.image)
        assertEquals("episode-9",row.jellyfinId);assertEquals(1200L,row.positionMs)
    }
    @Test fun missingSeriesIdentityResolvesParentNotEpisodeProviderIds()=runBlocking{
        val row=MediaItem("jellyfin:show-42",name="Show",type="series",jellyfinId="episode-9",jellyfinSeriesId="show-42",season=2,episode=3)
        var lookups=0
        val catalog=DirectCatalog({"fixture"}){url,_,_->
            assertTrue(url.endsWith("/tv/42"))
            json("""{"images":{"backdrops":[{"file_path":"/show-neutral.jpg","iso_639_1":null,"width":3840,"height":2160}]}}""")
        }
        val art=catalog.continueArtwork(row){id->lookups++;assertEquals("show-42",id);MediaItem("jellyfin:show-42",name="Show",type="series",jellyfinId=id,jellyfinProviderKeys=listOf("tmdb:42"))}
        assertEquals(1,lookups);assertFalse(art.embeddedTitle);assertTrue(art.image!!.endsWith("/original/show-neutral.jpg"))
        assertNull(row.imdbId);assertEquals(3,row.episode);assertEquals("episode-9",row.jellyfinId)
    }
    @Test fun unrelatedParentCannotSupplyArtworkIdentity()=runBlocking{
        val row=MediaItem("jellyfin:show-42",name="Show",type="series",jellyfinId="episode-9",jellyfinSeriesId="show-42",background="existing")
        val catalog=DirectCatalog({"fixture"}){_,_,_->error("Unrelated identity must not reach public provider")}
        assertEquals("existing",catalog.continueArtwork(row){MediaItem("wrong",name="Wrong",type="series",jellyfinId="another-show",jellyfinProviderKeys=listOf("tmdb:99"))}.image)
    }
    @Test fun conflictingIdentityCannotReceiveAnotherTitlesArtwork()=runBlocking{
        val c=DirectCatalog({"fixture"}){url,_,_->if(url.contains("/find/"))json("""{"movie_results":[{"id":42}]}""")else json("""{"external_ids":{"imdb_id":"tt7654321"},"images":{"backdrops":[{"file_path":"/wrong.jpg","iso_639_1":"en"}]}}""")}
        val result=c.continueArtwork(MediaItem("tt1234567",imdbId="tt1234567",name="Film",background="existing"))
        assertEquals("existing",result.image);assertFalse(result.embeddedTitle)
    }
    @Test fun reverseAlphabeticalLibrarySortRetainsSearchAndStableIdentity(){
        val items=listOf(MediaItem("a",name="Álpha"),MediaItem("z",name="Zulu"),MediaItem("b",name="Beta"))
        assertEquals(listOf("z","b","a"),filterJellyfinLibrary(items,LibraryFilters(sort="name_desc")).map{it.id})
        assertEquals(listOf("b"),filterJellyfinLibrary(items,LibraryFilters(query="beta",sort="name_desc")).map{it.id})
    }
}
