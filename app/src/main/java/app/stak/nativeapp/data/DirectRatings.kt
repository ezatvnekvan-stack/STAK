package app.stak.nativeapp.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class TitleRatings(val imdb: Double?, val rottenTomatoes: String?, val metacritic: String?)
class DirectRatings(private val key: () -> String = { NativeRuntime.settings.omdbKey.orEmpty() }, private val request: (String,Map<String,String>,String?) -> JsonObject = ::providerJson) {
    private val memory = ConcurrentHashMap<String, Pair<Long,JsonObject>>()
    private val locks = ConcurrentHashMap<String,Any>()
    suspend fun get(imdb: String, season: Int? = null, episode: Int? = null): TitleRatings = withContext(Dispatchers.IO) {
        require(imdb.matches(Regex("tt\\d+"))) { "Ratings require an IMDb identifier." }
        val secret = key()
        require(secret.isNotBlank()) { "Configure OMDb in Metadata & ratings." }
        val scope = java.security.MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()).joinToString("") { "%02x".format(it) }
        val cacheKey = "$scope-$imdb-${season ?: 0}-${episode ?: 0}"
        synchronized(locks.computeIfAbsent(cacheKey) { Any() }) {
            val now = System.currentTimeMillis()
            val file = PublicMetadataCache.directory?.parentFile?.resolve("omdb-details")?.resolve("$cacheKey.json")
            val cached = memory[cacheKey] ?: runCatching { file?.takeIf { it.exists() && now-it.lastModified() < 86_400_000L }?.let { it.lastModified() to JsonParser.parseString(it.readText()).asJsonObject } }.getOrNull()
            val data = if (cached != null && now-cached.first < 86_400_000L) cached.second else {
                if (VerifiedRatingCache.quotaUntil(scope) > now) throw IOException("OMDb daily quota reached. Try after the quota resets.")
                val params = mutableMapOf("apikey" to secret, "i" to imdb, "plot" to "full")
                season?.let { params["Season"] = it.toString() }; episode?.let { params["Episode"] = it.toString() }
                val response = try { request("https://www.omdbapi.com/", params, null) } catch (error: ProviderFailure) { if (error.status == 429) VerifiedRatingCache.markQuota(scope, now+86_400_000L); throw error }
                if (response.text("Response") == "False") {
                    if (Regex("quota|limit",RegexOption.IGNORE_CASE).containsMatchIn(response.text("Error").orEmpty())) VerifiedRatingCache.markQuota(scope,now+86_400_000L)
                    throw IOException("OMDb rating unavailable. Check the API key and daily quota.")
                }
                if (!response.has("imdbRating") && !response.has("Ratings")) throw IOException("OMDb returned invalid rating data.")
                memory[cacheKey] = now to response
                runCatching { file?.parentFile?.mkdirs(); file?.writeText(response.toString()) }
                response
            }
            TitleRatings(data.number("imdbRating")?.takeIf { it > 0 && it <= 10 }, data.objects("Ratings").firstOrNull { it.text("Source") == "Rotten Tomatoes" }?.text("Value"), data.text("Metascore")?.takeIf { it.toIntOrNull() in 1..100 })
        }
    }
}
