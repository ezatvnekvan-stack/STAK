package app.stak.nativeapp

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class PerformanceTest {
    @Test fun prefersNinetyWithoutForcingAnUnsupportedDisplayRate() {
        assertEquals(90f, preferredMotionRefreshRate(listOf(60f, 90f, 120f)), .01f)
        assertEquals(120f, preferredMotionRefreshRate(listOf(60f, 120f)), .01f)
        assertEquals(60f, preferredMotionRefreshRate(listOf(48f, 60f)), .01f)
        assertEquals(0f, preferredMotionRefreshRate(listOf(Float.NaN)), .01f)
    }

    @Test fun leavingScreenCancelsTheActualHttpCall() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val client = OkHttpClient()
            val call = client.newCall(Request.Builder().url(server.url("/delayed")).build())
            val job = launch(Dispatchers.IO) { call.readResponse { it.body?.string() } }
            assertNotNull("The request must actually start", server.takeRequest(5, TimeUnit.SECONDS))
            withTimeout(2000) { job.cancelAndJoin() }
            assertTrue("Cancelling the screen must cancel OkHttp, not just discard its result", call.isCanceled())
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun leavingScreenAlsoCancelsAnIncompleteBodyAfterHeadersArrive() = runBlocking {
        MockWebServer().use { server ->
            // Headers and some bytes arrive immediately, then the body stalls.
            server.enqueue(MockResponse().setBody("partial").setHeader("Content-Length", "4096"))
            val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()
            val call = client.newCall(Request.Builder().url(server.url("/slow-body")).build())
            val reading = java.util.concurrent.CountDownLatch(1)
            val job = launch(Dispatchers.IO) {
                call.readResponse { response -> reading.countDown(); response.body!!.string() }
            }
            assertTrue("Headers must arrive before cancellation", withContext(Dispatchers.IO) { reading.await(5, TimeUnit.SECONDS) })
            withTimeout(2000) { job.cancelAndJoin() }
            assertTrue("Cancelling a body read must close the actual call", call.isCanceled())
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}
