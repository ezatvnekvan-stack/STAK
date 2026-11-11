package app.stak.nativeapp.data

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CinematicArtworkTest {
    private fun json(value:String)=JsonParser.parseString(value).asJsonObject
    @Test fun neutralPortraitIsIndependentOfGridPosterAndUsesOriginalResolution()=runBlocking {
        val catalog=DirectCatalog({"fixture"}) { _,params,_ ->
            assertEquals("en,null",params["include_image_language"])
            json("""{"id":42,"title":"Film","poster_path":"/grid.jpg","backdrop_path":"/wide.jpg","images":{"posters":[{"file_path":"/neutral.jpg","iso_639_1":null,"width":2000,"height":3000},{"file_path":"/english.jpg","iso_639_1":"en","width":4000,"height":6000}],"logos":[{"file_path":"/logo.png","iso_639_1":"en"}]}}""")
        }
        val details=catalog.details("movie","tmdb:42")
        assertTrue(details.item.poster!!.endsWith("/w780/grid.jpg"))
        assertEquals("https://image.tmdb.org/t/p/original/neutral.jpg",details.item.cinematicImage)
        assertEquals("https://image.tmdb.org/t/p/original/logo.png",details.logo)
    }
    @Test fun highestPixelCountWinsAmongNeutralAlternatives() {
        val images=json("""{"posters":[{"file_path":"/small.jpg","width":500,"height":750,"vote_average":10},{"file_path":"/large.jpg","width":2000,"height":3000,"vote_average":4}]}""")
        assertTrue(CinematicArtwork.choose(images,null)!!.endsWith("/original/large.jpg"))
    }
    @Test fun neutralBackdropIsUsedWhenNoNeutralPosterExists() {
        val images=json("""{"posters":[{"file_path":"/labelled.jpg","iso_639_1":"en"}],"backdrops":[{"file_path":"/clean.jpg","iso_639_1":null,"width":3840,"height":2160}]}""")
        assertTrue(CinematicArtwork.choose(images,"fallback")!!.endsWith("/original/clean.jpg"))
    }
    @Test fun missingNeutralArtFallsBackWithoutInventingAPath() {
        assertEquals("https://image.tmdb.org/t/p/original/wide.jpg",CinematicArtwork.choose(json("{}"),"https://image.tmdb.org/t/p/w500/wide.jpg"))
        assertNull(CinematicArtwork.choose(json("{}"),null))
        assertEquals("https://provider.example/image.jpg",CinematicArtwork.original("https://provider.example/image.jpg"))
    }
    @Test fun logoPrefersEnglishAndHighestResolutionWithoutChoosingAnotherLanguage() {
        val images=json("""{"logos":[{"file_path":"/fr.png","iso_639_1":"fr","width":9999,"height":9999},{"file_path":"/en.png","iso_639_1":"en","width":1000,"height":200},{"file_path":"/neutral.png","width":2000,"height":400}]}""")
        assertTrue(CinematicArtwork.logo(images)!!.endsWith("/original/en.png"))
        assertNull(CinematicArtwork.logo(json("""{"logos":[{"file_path":"/fr.png","iso_639_1":"fr"}]}""")))
    }
    @Test fun heroUsesSamePolicyAndRetainsAnOriginalImageFallback()=runBlocking {
        val catalog=DirectCatalog({"fixture"}){_,_,_->json("""{"id":42,"images":{"posters":[{"file_path":"/clean.jpg","width":2000,"height":3000}]}}""")}
        val original=MediaItem("tmdb:42",name="Film",poster="grid",background="https://image.tmdb.org/t/p/w780/wide.jpg")
        val enriched=catalog.heroMetadata(original)
        assertEquals("grid",enriched.poster)
        assertTrue(enriched.cinematicImage!!.endsWith("/original/clean.jpg"))
        assertTrue(enriched.cinematicImageFallback!!.endsWith("/original/wide.jpg"))
    }
    @Test fun cinemetaRemainsAuthoritativeWhileOptionalTmdbArtIsEnriched()=runBlocking {
        val catalog=DirectCatalog({"fixture"},{true}){url,_,_->when{
            url.contains("cinemeta")->json("""{"meta":{"id":"tt1234567","name":"Cinemeta title","poster":"grid","background":"wide"}}""")
            url.contains("/find/")->json("""{"movie_results":[{"id":42}]}""")
            else->json("""{"id":42,"title":"Different title","images":{"posters":[{"file_path":"/clean.jpg"}]}}""")
        }}
        val detail=catalog.details("movie","tt1234567")
        assertEquals("Cinemeta title",detail.item.name)
        assertEquals("grid",detail.item.poster)
        assertTrue(detail.item.cinematicImage!!.endsWith("/original/clean.jpg"))
    }
    @Test fun failedOptionalArtDoesNotFailCinemetaDetails()=runBlocking {
        val catalog=DirectCatalog({"fixture"},{true}){url,_,_->if(url.contains("cinemeta"))json("""{"meta":{"id":"tt1234567","name":"Still usable","background":"wide"}}""") else throw java.io.IOException("offline")}
        assertEquals("wide",catalog.details("movie","tt1234567").item.cinematicImage)
    }
}
