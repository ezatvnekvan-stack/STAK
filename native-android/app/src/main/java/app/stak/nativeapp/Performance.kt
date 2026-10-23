package app.stak.nativeapp

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Fixed-size lock striping coalesces matching reads without retaining unbounded URL keys. */
class ReadLocks {
    private val locks = Array(32) { Mutex() }
    fun forKey(key: String): Mutex = locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]
}

/** Cancellation stays attached until the body has been consumed, not just until headers arrive. */
suspend fun <T> Call.readResponse(read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            try {
                val value = response.use(read)
                continuation.resume(value)
            } catch (failure: Exception) {
                continuation.resumeWithException(failure)
            }
        }
    })
}

/** Prefer 90 Hz when available, otherwise the nearest faster mode. Android retains final control. */
fun preferredMotionRefreshRate(rates: List<Float>): Float =
    rates.filter { it.isFinite() && it >= 89f }.minOrNull()
        ?: rates.filter { it.isFinite() && it > 0f }.maxOrNull() ?: 0f
