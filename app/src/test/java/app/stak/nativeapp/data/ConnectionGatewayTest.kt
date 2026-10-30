package app.stak.nativeapp.data

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ConnectionGatewayTest {
    @Test fun tmdbReadTokenUsesBearerWithoutApiQuery() = runBlocking {
        val gateway = DirectConnectionGateway { url, params, token ->
            assertEquals("https://api.themoviedb.org/3/configuration", url)
            assertTrue(params.isEmpty()); assertEquals("a.b.c", token)
            JsonParser.parseString("""{"images":{"secure_base_url":"https://image.tmdb.org/"}}""").asJsonObject
        }
        gateway.metadata("tmdb", "bEaReR a.b.c")
    }
    @Test fun tmdbLegacyKeyUsesApiQuery() = runBlocking {
        DirectConnectionGateway { _, params, token ->
            assertEquals("legacy", params["api_key"]); assertNull(token)
            JsonParser.parseString("""{"images":{"secure_base_url":"https://image.tmdb.org/"}}""").asJsonObject
        }.metadata("tmdb", "legacy")
    }
    @Test fun malformedTmdbCannotPassVerification() = runBlocking {
        try { DirectConnectionGateway { _,_,_ -> JsonParser.parseString("{}").asJsonObject }.metadata("tmdb", "key"); fail("Must reject malformed configuration") }
        catch (_: IOException) { }
    }
    @Test fun omdbFalseResponseCannotPassVerification() = runBlocking {
        try { DirectConnectionGateway { _,_,_ -> JsonParser.parseString("""{"Response":"False","Error":"Invalid API key!"}""").asJsonObject }.metadata("omdb", "key"); fail("Must reject key") }
        catch (error: IOException) { assertEquals("OMDb rejected the API key.", error.message) }
    }
    @Test fun omdbQuotaExplainsFailure() = runBlocking {
        try { DirectConnectionGateway { _,_,_ -> JsonParser.parseString("""{"Response":"False","Error":"Request limit reached!"}""").asJsonObject }.metadata("omdb", "key"); fail("Must reject quota") }
        catch (error: IOException) { assertTrue(error.message!!.contains("quota")) }
    }
    @Test fun omdbVerificationChecksKnownTitle() = runBlocking {
        DirectConnectionGateway { _,params,token ->
            assertEquals("tt0111161", params["i"]); assertEquals("test", params["apikey"]); assertNull(token)
            JsonParser.parseString("""{"Response":"True","imdbRating":"9.3"}""").asJsonObject
        }.metadata("omdb", "test")
    }
    @Test fun torboxReadsActualAccountAndDoesNotTrustFailure() = runBlocking {
        val account = DirectConnectionGateway { _,params,token ->
            assertEquals("false", params["settings"]); assertEquals("test", token)
            JsonParser.parseString("""{"success":true,"data":{"email":"test@example.invalid","plan":2,"total_downloaded":123,"premium_expires_at":"2026-01-01"}}""").asJsonObject
        }.torbox("test")
        assertEquals("Pro", account.plan); assertEquals(123L, account.downloaded)
        try { DirectConnectionGateway { _,_,_ -> JsonParser.parseString("""{"success":false,"data":{}}""").asJsonObject }.torbox("bad"); fail("Must reject failure") }
        catch (_: IOException) { }
    }
    @Test fun credentialsRejectLineBreaksAndNormalizeBearer() {
        assertEquals("a.b.c", normalizedCredential("  BEARER a.b.c  ", true))
        try { normalizedCredential("key\r\nsecret", true); fail("Must reject header injection") } catch (_: IllegalArgumentException) { }
    }
}
