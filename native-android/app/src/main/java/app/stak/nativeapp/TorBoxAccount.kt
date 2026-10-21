package app.stak.nativeapp

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class TorBoxAccountState(
    /** A key is stored, even if it has not been verified yet. Never carries the key itself. */
    val configured: Boolean = false,
    val data: JSONObject? = null,
    val loading: Boolean = false,
    val error: String = "",
)

/** The original AppContext shares verified account data with Settings and Connections. */
class TorBoxAccountStore(private val repository: Repository) {
    val state = MutableStateFlow(TorBoxAccountState())
    private var checkedKey: String? = null
    private val lock = Mutex()
    private val revision = java.util.concurrent.atomic.AtomicLong()

    private suspend fun verify(value: String): JSONObject {
        require(value.isNotBlank()) { "Enter a TorBox API key." }
        val result = repository.request("https://api.torbox.app/v1/api/user/me", value)
        check(result.optBoolean("success")) { "TorBox could not verify this key." }
        return checkNotNull(result.optJSONObject("data")) {
            "TorBox account details are unavailable."
        }
    }

    suspend fun refresh(force: Boolean = false) =
        lock.withLock {
            val key = repository.credentials.get("torbox_token")
            if (key.isBlank()) {
                checkedKey = null
                state.value = TorBoxAccountState()
                return@withLock
            }
            // The mutex serializes callers. Once this key has a definitive result, repeated
            // refresh calls (for example after a recomposition) must not start another request.
            // Explicit retries use force = true.
            if (
                !force &&
                    checkedKey == key &&
                    (state.value.data != null || state.value.error.isNotBlank())
            )
                return@withLock
            val owner = revision.incrementAndGet()
            state.value = TorBoxAccountState(configured = true, loading = true)
            try {
                val data = verify(key)
                if (repository.credentials.get("torbox_token") == key && revision.get() == owner) {
                    checkedKey = key
                    state.value = TorBoxAccountState(configured = true, data = data)
                }
            } catch (cancelled: CancellationException) {
                // A newer account decision superseded this verification. Never leave the badge
                // stuck on "Connecting…" when the request was cancelled.
                if (repository.credentials.get("torbox_token") == key && revision.get() == owner)
                    state.value = TorBoxAccountState(configured = true)
                throw cancelled
            } catch (failure: Exception) {
                if (repository.credentials.get("torbox_token") == key && revision.get() == owner) {
                    checkedKey = key
                    state.value =
                        TorBoxAccountState(
                            configured = true,
                            error = failure.message ?: "Account could not be verified.",
                        )
                }
            }
        }

    suspend fun connect(value: String) =
        lock.withLock {
            val key = value.trim()
            val startRevision = revision.get()
            // A rejected replacement must leave the working account and saved key intact.
            val data = verify(key)
            // Signing out while this verification was in flight wins over the older connect.
            if (revision.get() != startRevision) return@withLock
            revision.incrementAndGet()
            repository.credentials.set("torbox_token", key)
            checkedKey = key
            state.value = TorBoxAccountState(configured = true, data = data)
        }

    fun signOut() {
        revision.incrementAndGet()
        repository.credentials.set("torbox_token", "")
        checkedKey = null
        state.value = TorBoxAccountState()
    }
}

fun accountPlan(value: Any?) =
    when ((value as? Number)?.toInt() ?: value?.toString()?.toIntOrNull()) {
        0 -> "Free"
        1 -> "Essential"
        2 -> "Pro"
        3 -> "Standard"
        else -> "Unknown"
    }

fun accountBytes(value: Any?): String {
    if (value == null || value == JSONObject.NULL) return "—"
    var bytes =
        (value as? Number)?.toDouble() ?: value.toString().toDoubleOrNull() ?: return "NaN B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var index = 0
    while (bytes >= 1024 && index < units.lastIndex) {
        bytes /= 1024
        index++
    }
    val pattern = if (index >= 3) "%.2f %s" else "%.0f %s"
    return String.format(Locale.US, pattern, bytes, units[index])
}

fun accountDate(value: Any?, zone: ZoneId = ZoneId.systemDefault()): String {
    if (value == null || value == JSONObject.NULL || value == "" || value == 0) return "—"
    val text = value.toString()
    val date =
        runCatching {
                when {
                    value is Number ->
                        Instant.ofEpochMilli(value.toLong()).atZone(zone).toLocalDate()
                    else ->
                        runCatching { Instant.parse(text).atZone(zone).toLocalDate() }
                            .getOrElse {
                                runCatching {
                                        LocalDateTime.parse(text.replace(' ', 'T')).toLocalDate()
                                    }
                                    .getOrElse {
                                        LocalDate.parse(text)
                                            .atStartOfDay(ZoneOffset.UTC)
                                            .withZoneSameInstant(zone)
                                            .toLocalDate()
                                    }
                            }
                }
            }
            .getOrNull() ?: return "Invalid Date"
    return date.format(DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US))
}
