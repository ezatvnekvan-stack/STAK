package app.stak.nativeapp.data

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

data class TorboxAccount(val email: String, val plan: String, val expires: String, val downloaded: Long)
data class JellyfinUser(val id: String, val name: String)
data class JellyfinProbe(val token: String, val users: List<JellyfinUser>, val serverName: String = "Jellyfin", val version: String = "")

interface ConnectionGateway {
    suspend fun metadata(provider: String, key: String)
    suspend fun torbox(key: String): TorboxAccount
    suspend fun jellyfinLogin(server: String, username: String, password: String): JellyfinProbe
    suspend fun jellyfinKey(server: String, key: String): JellyfinProbe
    suspend fun jellyfinUser(server: String, token: String, user: JellyfinUser)
}

fun normalizedCredential(value: String, bearer: Boolean = false): String {
    require(value.length <= 4096 && value.none { it == '\r' || it == '\n' }) { "Invalid provider credential." }
    val trimmed = value.trim()
    return if (bearer) trimmed.replace(Regex("^Bearer\\s+", RegexOption.IGNORE_CASE), "") else trimmed
}

class DirectConnectionGateway(private val request: (String, Map<String,String>, String?) -> JsonObject = ::providerJson) : ConnectionGateway {
    override suspend fun metadata(provider: String, key: String): Unit = withContext(Dispatchers.IO) {
        require(key.isNotBlank()) { "Enter an API key first." }
        when (provider) {
            "tmdb" -> {
                val token = normalizedCredential(key, true)
                val response = request("https://api.themoviedb.org/3/configuration", if (token.contains('.')) emptyMap() else mapOf("api_key" to token), token.takeIf { it.contains('.') })
                if (response.getAsJsonObject("images")?.text("secure_base_url").isNullOrBlank()) throw IOException("TMDb returned invalid configuration.")
            }
            "omdb" -> {
                val response = request("https://www.omdbapi.com/", mapOf("apikey" to normalizedCredential(key), "i" to "tt0111161", "plot" to "full"), null)
                if (response.text("Response") == "False") {
                    val error = response.text("Error").orEmpty()
                    throw IOException(when { error.contains("limit", true) || error.contains("quota", true) -> "OMDb daily quota reached. Wait for the quota to reset."; error.contains("key", true) -> "OMDb rejected the API key."; else -> "OMDb rating unavailable." })
                }
                if (!response.has("imdbRating") && !response.has("Ratings")) throw IOException("OMDb returned malformed rating data.")
            }
            else -> throw IllegalArgumentException("Unknown metadata provider.")
        }
    }
    override suspend fun torbox(key: String): TorboxAccount = withContext(Dispatchers.IO) {
        val token = normalizedCredential(key, true)
        require(token.isNotBlank()) { "Enter your TorBox API key." }
        val response = request("https://api.torbox.app/v1/api/user/me", mapOf("settings" to "false"), token)
        if (response.get("success")?.asBoolean != true || response.get("data")?.isJsonObject != true) throw IOException("TorBox rejected the key or returned invalid account data.")
        val data = response.getAsJsonObject("data")
        TorboxAccount(data.text("email") ?: "—", mapOf(0 to "Free", 1 to "Essential", 2 to "Pro", 3 to "Standard")[data.number("plan")?.toInt()] ?: "Unknown", data.text("premium_expires_at") ?: "—", data.number("total_downloaded")?.toLong() ?: 0)
    }
    override suspend fun jellyfinLogin(server: String, username: String, password: String) = DirectJellyfin.probeLogin(server, username, password)
    override suspend fun jellyfinKey(server: String, key: String) = DirectJellyfin.probeKey(server, key)
    override suspend fun jellyfinUser(server: String, token: String, user: JellyfinUser) = DirectJellyfin.verifyUser(server, token, user.id)
}
