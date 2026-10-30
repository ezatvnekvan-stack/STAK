package app.stak.nativeapp

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Local HTTP server verifies the actual transport with synthetic credentials. */
class DirectJellyfinProtocolTest {
    private lateinit var server: ServerSocket
    private val executor = Executors.newSingleThreadExecutor()
    private val files = mutableSetOf<String>()
    private var previous: SettingsStore? = null
    @Before fun setup() {
        previous = runCatching { NativeRuntime.settings }.getOrNull()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "jellyfin-protocol-test-${java.util.UUID.randomUUID()}-"
        files += prefix+"stak_settings"; files += prefix+"stak_secrets"
        NativeRuntime.settings = SettingsStore(context,prefix)
        server = ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 5000
    }
    @After fun cleanup() {
        server.close(); executor.shutdownNow()
        previous?.let { NativeRuntime.settings = it }
        files.forEach { InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(it) }
    }
    private data class Exchange(val path: String, val response: String, val post: Boolean = false, val token: String? = null)
    private fun serve(vararg exchanges: Exchange) = executor.submit {
        exchanges.forEach { expected -> server.accept().use { socket ->
            socket.soTimeout = 5000
            val reader = socket.getInputStream().bufferedReader()
            val request = reader.readLine()
            Assert.assertEquals("${if (expected.post) "POST" else "GET"} ${expected.path} HTTP/1.1", request)
            val headers = mutableMapOf<String,String>()
            while (true) { val line = reader.readLine(); if (line.isEmpty()) break; headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim() }
            Assert.assertTrue(headers["authorization"].orEmpty().startsWith("MediaBrowser Client=\"STAK\""))
            Assert.assertFalse(headers["authorization"].orEmpty().contains("stak-native"))
            expected.token?.let {
                Assert.assertEquals(it, headers["x-emby-token"])
                Assert.assertTrue(headers["authorization"].orEmpty().contains("Token=\"$it\""))
                Assert.assertEquals(headers["authorization"], headers["x-emby-authorization"])
            }
            val body = CharArray(headers["content-length"]?.toInt() ?: 0)
            var count = 0
            while (count < body.size) { val read = reader.read(body, count, body.size-count); if (read < 0) break; count += read }
            if (expected.post) {
                val json = com.google.gson.JsonParser.parseString(String(body)).asJsonObject
                Assert.assertEquals("Tester", json.get("Username").asString)
                Assert.assertEquals(" test password ", json.get("Pw").asString)
            }
            val bytes = expected.response.toByteArray(Charsets.UTF_8)
            val output = socket.getOutputStream()
            output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            output.write(bytes); output.flush()
        } }
    }
    private fun url() = "http://127.0.0.1:${server.localPort}/jellyfin"
    @Test fun passwordLoginAndUserVerificationPreserveBasePath() = runBlocking {
        val served = serve(Exchange("/jellyfin/Users/AuthenticateByName", """{"AccessToken":"fake-token","User":{"Id":"user-1","Name":"Tester"}}""", post = true), Exchange("/jellyfin/Users/user-1", """{"Id":"user-1"}""", token = "fake-token"))
        val probe = DirectJellyfin.probeLogin(url(), "Tester", " test password ")
        Assert.assertEquals("Tester", probe.users.single().name)
        DirectJellyfin.verifyUser(url(), probe.token, probe.users.single().id)
        served.get(6, TimeUnit.SECONDS)
        Unit
    }
    @Test fun apiKeyLoadsServerAndArrayOfUsers() = runBlocking {
        val served = serve(Exchange("/jellyfin/System/Info/Public", """{"ServerName":"Local test","Version":"10"}""", token = "api-test"), Exchange("/jellyfin/Users", """[{"Id":"one","Name":"First"},{"Id":"two","Name":"Second"}]""", token = "api-test"))
        val probe = DirectJellyfin.probeKey(url(), "api-test")
        Assert.assertEquals("Local test", probe.serverName)
        Assert.assertEquals(listOf("one", "two"), probe.users.map { it.id })
        served.get(6, TimeUnit.SECONDS)
        Unit
    }
    @Test fun mismatchedUserCannotBeVerified() = runBlocking {
        val served = serve(Exchange("/jellyfin/Users/expected", """{"Id":"different"}""", token = "api-test"))
        try { DirectJellyfin.verifyUser(url(), "api-test", "expected"); Assert.fail("Must reject mismatched user") } catch (_: java.io.IOException) { }
        served.get(6, TimeUnit.SECONDS)
        Unit
    }
}
