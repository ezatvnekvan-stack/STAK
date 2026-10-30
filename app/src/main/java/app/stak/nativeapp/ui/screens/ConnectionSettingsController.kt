package app.stak.nativeapp.ui.screens

import app.stak.nativeapp.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ConnectionFormState(
    val tmdbDraft: String = "", val omdbDraft: String = "", val torboxDraft: String = "",
    val tmdbSaved: Boolean = false, val omdbSaved: Boolean = false, val torboxSaved: Boolean = false,
    val account: TorboxAccount? = null, val maskedTorbox: String = "",
    val movie: String = "tmdb", val series: String = "tmdb", val ratings: Boolean = true, val imdbOnLists: Boolean = false,
    val server: String = "", val username: String = "", val password: String = "", val jellyfinKey: String = "", val mode: String = "password",
    val jellyfinConnected: Boolean = false, val savedServer: String = "", val savedUser: String = "", val editing: Boolean = false,
    val probe: JellyfinProbe? = null, val busy: String = "", val error: String? = null, val message: String? = null
)

/** Drafts stay in memory; an existing session is replaced only after verification. */
class ConnectionSettingsController(
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val changed: () -> Unit = {},
    private val gateway: ConnectionGateway = DirectConnectionGateway()
) {
    private val mutable = MutableStateFlow(ConnectionFormState())
    val state: StateFlow<ConnectionFormState> = mutable
    init { reload() }
    private fun update(block: (ConnectionFormState) -> ConnectionFormState) { mutable.value = block(mutable.value) }
    fun reload() {
        update { it.copy(tmdbSaved = !settings.tmdbKey.isNullOrBlank(), omdbSaved = !settings.omdbKey.isNullOrBlank(), torboxSaved = !settings.torboxKey.isNullOrBlank(), maskedTorbox = settings.torboxKey?.let { key -> key.take(4) + "••••••••" + key.takeLast(4) }.orEmpty(), movie = settings.movieMetadata, series = settings.seriesMetadata, ratings = settings.ratingsEnabled, imdbOnLists = settings.imdbOnLists, jellyfinConnected = settings.jellyfinConnected, savedServer = settings.jellyfinUrl.orEmpty(), savedUser = settings.jellyfinUserName ?: "Jellyfin user") }
    }
    fun draft(provider: String, value: String) {
        if (state.value.busy.isNotEmpty()) return
        update { when (provider) { "tmdb" -> it.copy(tmdbDraft = value, error = null, message = null); "omdb" -> it.copy(omdbDraft = value, error = null, message = null); else -> it.copy(torboxDraft = value, error = null, message = null) } }
    }
    fun metadataPreference(name: String, value: String) {
        when (name) { "movie" -> settings.movieMetadata = value; "series" -> settings.seriesMetadata = value; "ratings" -> settings.ratingsEnabled = value == "true"; "imdbOnLists" -> settings.imdbOnLists = value == "true" }
        reload(); changed()
    }
    private fun run(tag: String, work: suspend () -> Unit) {
        if (state.value.busy.isNotEmpty()) return
        update { it.copy(busy = tag, error = null, message = null) }
        scope.launch {
            try { work(); reload(); changed() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { update { it.copy(error = safeConnectionError(error)) } }
            finally { update { it.copy(busy = "") } }
        }
    }
    fun saveMetadata(provider: String, verify: Boolean) {
        val raw = if (provider == "tmdb") state.value.tmdbDraft else state.value.omdbDraft
        if (raw.isBlank()) return
        run(provider) {
            val key = normalizedCredential(raw, provider == "tmdb")
            if (verify) gateway.metadata(provider, key)
            if (provider == "tmdb") settings.tmdbKey = key else settings.omdbKey = key
            update { if (provider == "tmdb") it.copy(tmdbDraft = "") else it.copy(omdbDraft = "") }
            update { it.copy(message = if (verify) "${provider.uppercase()} · Connection verified and saved" else "Credential saved securely. Test the connection.") }
        }
    }
    fun testMetadata(provider: String) = run(provider) {
        val key = if (provider == "tmdb") settings.tmdbKey else settings.omdbKey
        gateway.metadata(provider, key.orEmpty())
        update { it.copy(message = "${provider.uppercase()} · Connection verified") }
    }
    fun removeMetadata(provider: String) = run(provider) {
        if (provider == "tmdb") settings.tmdbKey = null else settings.omdbKey = null
    }
    fun saveTorbox() {
        val draft = state.value.torboxDraft
        if (draft.isBlank()) return
        run("torbox") {
            val key = normalizedCredential(draft, true)
            val account = gateway.torbox(key)
            settings.torboxKey = key
            update { it.copy(torboxDraft = "", account = account, message = "TorBox connected") }
        }
    }
    fun loadTorbox() {
        if (!settings.torboxKey.isNullOrBlank()) run("torbox") { val account = gateway.torbox(settings.torboxKey.orEmpty()); update { it.copy(account = account) } }
    }
    fun signOutTorbox() = run("torbox") { settings.torboxKey = null; update { it.copy(account = null, message = "Signed out") } }
    fun jellyfinField(field: String, value: String) {
        if (state.value.busy.isNotEmpty()) return
        update { when (field) {
            "url" -> it.copy(server = value, jellyfinKey = "", password = "", probe = null, message = null, error = null)
            "username" -> it.copy(username = value, probe = null, message = null, error = null)
            "password" -> it.copy(password = value, probe = null, message = null, error = null)
            "mode" -> it.copy(mode = value, probe = null, message = null, error = null)
            else -> it.copy(jellyfinKey = value, probe = null, message = null, error = null)
        } }
    }
    fun connectJellyfin() {
        val form = state.value
        run("jellyfin-test") {
            val server = try { DirectJellyfin.normalizeServer(form.server) } catch (_: Exception) { throw IllegalArgumentException("Enter a valid Jellyfin URL including http:// or https://.") }
            val probe = if (form.mode == "password") gateway.jellyfinLogin(server, form.username, form.password) else gateway.jellyfinKey(server, normalizedCredential(form.jellyfinKey))
            require(probe.token.isNotBlank() && probe.users.all { it.id.isNotBlank() }) { "Jellyfin returned invalid login data." }
            update { it.copy(server = server, probe = probe, jellyfinKey = probe.token, password = "", message = "Connected to ${probe.serverName}${probe.version.takeIf { v -> v.isNotBlank() }?.let { v -> " ($v)" }.orEmpty()}") }
        }
    }
    fun pickJellyfinUser(user: JellyfinUser) {
        val form = state.value
        val probe = form.probe ?: return
        if (user !in probe.users) return
        run("jellyfin-save") {
            gateway.jellyfinUser(form.server, probe.token, user)
            settings.jellyfinUrl = form.server
            settings.jellyfinToken = probe.token
            settings.jellyfinUserId = user.id
            settings.jellyfinUserName = user.name
            update { it.copy(server = "", username = "", password = "", jellyfinKey = "", probe = null, editing = false, message = "Jellyfin connected locally") }
        }
    }
    fun reconnectJellyfin() { update { it.copy(editing = true, server = it.savedServer, username = "", password = "", jellyfinKey = "", probe = null, message = null, error = null) } }
    fun cancelReconnect() { if (state.value.busy.isEmpty()) update { it.copy(editing = false, server = "", username = "", password = "", jellyfinKey = "", probe = null, message = null, error = null) } }
    fun disconnectJellyfin() = run("jellyfin-disconnect") {
        settings.jellyfinToken = null; settings.jellyfinUserId = null; settings.jellyfinUserName = null
        update { it.copy(editing = false, probe = null, message = null) }
    }
    fun refreshJellyfin() = run("jellyfin-sync") {
        gateway.jellyfinUser(settings.jellyfinUrl.orEmpty(), settings.jellyfinToken.orEmpty(), JellyfinUser(settings.jellyfinUserId.orEmpty(), settings.jellyfinUserName.orEmpty()))
        JellyfinPlayback.resume()
        update { it.copy(message = "Jellyfin activity refreshed") }
    }
}

internal fun safeConnectionError(error: Exception): String = when (error) {
    is ProviderFailure -> when (error.status) { 401, 403 -> "Access denied. Check the API key or account permissions."; 429 -> "Provider quota reached. Try after the quota resets."; else -> "Provider request failed (${error.status}). Please retry." }
    is java.net.SocketTimeoutException -> "Connection timed out. Check the address and try again."
    is java.net.UnknownHostException, is java.net.ConnectException -> "Could not reach the server. Check the address and network."
    is javax.net.ssl.SSLException -> "The server's HTTPS certificate could not be verified."
    else -> error.message?.takeIf { message -> listOf("Invalid provider credential.", "Enter a valid Jellyfin URL including http:// or https://.", "Enter your Jellyfin username.", "Enter your Jellyfin API key.").contains(message) || message.startsWith("OMDb ") || message.startsWith("TMDb returned ") || message.startsWith("Jellyfin returned ") || message.startsWith("Jellyfin access denied") || message.startsWith("Jellyfin request failed") || message.startsWith("TorBox rejected ") } ?: "Could not verify the connection. Check your credentials and try again."
}
