package app.stak.nativeapp

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reuse a decrypted value only while its scoped storage key AND ciphertext match. */
class SnapshotReadCache {
    private data class Snapshot(val ciphertext: String, val value: String)
    private val entries = object : LinkedHashMap<String, Snapshot>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Snapshot>?) = size > 16
    }
    fun clear() = synchronized(entries) { entries.clear() }
    fun read(key: String, ciphertext: String?, decode: () -> String): String {
        synchronized(entries) {
            if (ciphertext == null) { entries.remove(key); return "" }
            entries[key]?.takeIf { it.ciphertext == ciphertext }?.let { return it.value }
        }
        val value = decode() // Never retain a failed decryption.
        synchronized(entries) { entries[key] = Snapshot(requireNotNull(ciphertext), value) }
        return value
    }
}

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
