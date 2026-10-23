package app.stak.nativeapp

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

/** Fixed-size lock striping coalesces matching reads without retaining unbounded URL keys. */
class ReadLocks {
    private val locks = Array(32) { Mutex() }
    fun forKey(key: String): Mutex = locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]
}

/** Leaving a screen cancels the socket, including response delivery races. */
suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, delivered, _ -> delivered.close() }
        }
    })
}

/** Prefer 90 Hz when available, otherwise the nearest faster mode. Android retains final control. */
fun preferredMotionRefreshRate(rates: List<Float>): Float =
    rates.filter { it.isFinite() && it >= 89f }.minOrNull()
        ?: rates.filter { it.isFinite() && it > 0f }.maxOrNull() ?: 0f
