package app.stak.nativeapp.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/** Stores only verified ratings and hashed credential scopes in app-private cache. */
internal object VerifiedRatingCache {
    private fun directory(): File? = PublicMetadataCache.directory?.parentFile?.resolve("verified-ratings")
    fun read(scope: String, imdb: String): Pair<Long,Double?>? = try {
        val root = directory()
        val file = root?.resolve("$scope-$imdb.json")
        if (file == null || !file.exists()) null else {
            val data = JsonParser.parseString(file.readText()).asJsonObject
            val timestamp = data.number("timestamp")?.toLong() ?: 0L
            if (System.currentTimeMillis() - timestamp >= 86_400_000L || !data.has("rating")) null else timestamp to data.number("rating")
        }
    } catch (_: Exception) { null }
    @Synchronized fun write(scope: String, imdb: String, timestamp: Long, rating: Double?) {
        try {
            val root = directory() ?: return
            root.mkdirs()
            root.resolve("$scope-$imdb.json").writeText(JsonObject().apply { addProperty("timestamp",timestamp); addProperty("rating",rating) }.toString())
        } catch (_: Exception) { }
    }
    fun quotaUntil(scope: String): Long = try { directory()?.resolve("$scope.quota")?.takeIf { it.exists() }?.readText()?.toLongOrNull() ?: 0L } catch (_: Exception) { 0L }
    fun markQuota(scope: String, until: Long) {
        try { val root = directory() ?: return; root.mkdirs(); root.resolve("$scope.quota").writeText(until.toString()) } catch (_: Exception) { }
    }
}
