package app.stak.nativeapp.data
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DirectRatingsTest {
    @Test fun mapsVerifiedProviderRatingsAndReusesCacheWithoutMixingEpisodes() = runBlocking {
        var calls=0
        val ratings=DirectRatings({"synthetic-key-${System.nanoTime()}"}) { _,_,_-> calls++; JsonParser.parseString("""{"Response":"True","imdbRating":"8.5","Metascore":"82","Ratings":[{"Source":"Rotten Tomatoes","Value":"94%"}]}""").asJsonObject }
        // A stable scope is required for reuse; each episode has a separate cache entry.
        val stable=DirectRatings({"stable-synthetic-key"}) { _,_,_-> calls++; JsonParser.parseString("""{"Response":"True","imdbRating":"8.5","Metascore":"82","Ratings":[{"Source":"Rotten Tomatoes","Value":"94%"}]}""").asJsonObject }
        val score=stable.get("tt1234567"); assertEquals(8.5,score.imdb!!,.001); assertEquals("94%",score.rottenTomatoes); assertEquals("82",score.metacritic)
        stable.get("tt1234567"); assertEquals(1,calls)
        stable.get("tt1234567",1,1); assertEquals(2,calls)
    }
    @Test fun missingOrFailedRatingsAreNeverInvented() = runBlocking {
        val missing=DirectRatings({"test"}) { _,_,_->JsonParser.parseString("""{"Response":"True","imdbRating":"N/A","Metascore":"N/A","Ratings":[]}""").asJsonObject }
        assertNull(missing.get("tt1234567").imdb)
        try { DirectRatings({"test"}) { _,_,_->JsonParser.parseString("""{"Response":"False","Error":"Invalid API key!"}""").asJsonObject }.get("tt7654321"); fail("Expected provider failure") } catch (_:java.io.IOException) { }
    }
}
