package app.stak.nativeapp

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceCacheTest {
    @Test
    fun batchFailurePreservesOtherAvailabilityAndKeepsFailedHashesUnknown() = runBlocking {
        var calls = 0
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val hashes = request.url.queryParameter("hash")!!.split(',')
                    assertTrue(hashes.size <= 80)
                    calls++
                    val body =
                        JSONObject()
                            .put("success", true)
                            .put("data", JSONArray().put(JSONObject().put("hash", hashes.first())))
                    Response.Builder()
                        .request(request)
                        .code(if (calls == 2) 503 else 200)
                        .message("TEST ONLY")
                        .protocol(Protocol.HTTP_1_1)
                        .body(body.toString().toResponseBody())
                        .build()
                }
                .build()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repository = Repository(app, client)
        val original = repository.credentials.get("torbox_token")
        try {
            repository.credentials.set("torbox_token", "test-only-source-cache")
            val hashes = (1..161).map { "%040x".format(it) }
            val result = repository.checkSourceCache(hashes)
            assertEquals(3, calls)
            assertEquals(81, result.checked.size)
            assertEquals(2, result.cached.size)
            assertEquals(1, result.errors.size)
            assertFalse(hashes[80] in result.checked)
        } finally {
            repository.credentials.set("torbox_token", original)
            repository.db.close()
        }
    }
}
