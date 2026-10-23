package app.stak.nativeapp

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JellyfinLoginTest {
    private val requests = CopyOnWriteArrayList<Request>()
    private fun repository(reject: Boolean = false): Repository {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return Repository(app, OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests.add(request)
            val authorization = request.header("Authorization").orEmpty()
            // Reproduce a server rejecting a login without standard MediaBrowser identity.
            val accepted = authorization.startsWith("MediaBrowser ") && authorization.contains("DeviceId=") && !reject
            val path = request.url.encodedPath
            val body = when {
                path.endsWith("/Users/AuthenticateByName") -> """{"AccessToken":"test-session","User":{"Id":"test-user","Name":"Fixture user"}}"""
                path.endsWith("/System/Info") -> """{"ServerName":"Fixture server"}"""
                else -> """[{"Id":"test-user","Name":"Fixture user"}]"""
            }
            Response.Builder().request(request).code(if (accepted) 200 else 400).message("TEST ONLY")
                .protocol(Protocol.HTTP_1_1).body(body.toResponseBody()).build()
        }.build())
    }

    @Test fun usernameLoginSendsStandardIdentityAndPreservesPasswordAndProxyBasePath() = runBlocking {
        val repository = repository()
        try {
            val login = repository.testJellyfin("https://example.invalid/jellyfin/web/index.html#!/login", " fixture ", " spaced password ", "", true)
            assertEquals("https://example.invalid/jellyfin", login.serverUrl)
            assertEquals("test-session", login.token)
            assertEquals("test-user", login.users.single().id)
            val request = requests.single()
            assertEquals("/jellyfin/Users/AuthenticateByName", request.url.encodedPath)
            assertEquals(request.header("Authorization"), request.header("X-Emby-Authorization"))
            assertNull(request.header("X-Emby-Token"))
            assertFalse(request.header("Authorization")!!.contains("Token="))
            val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
            val body = JSONObject(buffer.readUtf8())
            assertEquals("fixture", body.getString("Username"))
            assertEquals(" spaced password ", body.getString("Pw"))
        } finally { repository.db.close() }
    }

    @Test fun apiKeyConnectionSendsMatchingModernAndLegacyHeaders() = runBlocking {
        val repository = repository()
        try {
            val login = repository.testJellyfin("http://example.invalid:8096/jellyfin/", "", "", " test-api-key ", false)
            assertEquals("Fixture server", login.serverName)
            assertEquals("test-api-key", login.token)
            assertEquals(2, requests.size)
            requests.forEach {
                assertEquals("test-api-key", it.header("X-Emby-Token"))
                assertEquals(it.header("Authorization"), it.header("X-Emby-Authorization"))
                assertTrue(it.header("Authorization")!!.contains("Token=\"test-api-key\""))
                assertNull(it.url.queryParameter("api_key"))
            }
        } finally { repository.db.close() }
    }

    @Test fun badRequestReportsConnectionGuidanceWithoutSavingOrExposingSecrets() = runBlocking {
        val repository = repository(reject = true)
        val previous = repository.credentials.get("jellyfin_url")
        try {
            try {
                repository.testJellyfin("https://example.invalid", "test-user", "test-secret-password", "", true)
                fail("HTTP 400 must be reported")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message!!.contains("HTTP 400"))
                assertTrue(expected.message!!.contains("protocol"))
                assertFalse(expected.message!!.contains("test-secret-password"))
            }
            assertEquals(previous, repository.credentials.get("jellyfin_url"))
        } finally { repository.db.close() }
    }
}
