package app.stak.nativeapp.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Uses the same public providers as Capacitor's offlineAdapter; no PC backend. */
object NativeRuntime {
    lateinit var settings: SettingsStore
}

class DirectCatalog(
    private val tmdbKey: () -> String = { NativeRuntime.settings.tmdbKey.orEmpty() },
    private val preferCinemeta: (String) -> Boolean = { false },
    private val request: (String, Map<String, String>, String?) -> JsonObject = ::providerJson
) {
    suspend fun discover(type: String, category: String, page: Int, year: Int? = null): DiscoverResult = withContext(Dispatchers.IO) {
        val kind = if (type == "series") "series" else "movie"
        val key = tmdbKey()
        val selectedYear = year ?: if (category == "new") java.time.LocalDate.now().year else null
        if (key.isBlank()) {
            val catalog = if (category in listOf("top", "top_rated", "rating")) "imdbRating" else "top"
            val extra = if (page > 1) "/skip=${(page - 1) * 20}" else ""
            val data = request("https://v3-cinemeta.strem.io/catalog/$kind/$catalog$extra.json", emptyMap(), null)
            val raw = data.getAsJsonArray("metas") ?: throw IOException("Cinemeta returned an invalid catalog.")
            val items = raw.map { cinemetaItem(it.asJsonObject, kind) }.filter { selectedYear == null || it.year?.take(4)?.toIntOrNull() == selectedYear }
            DiscoverResult(items, page, if (raw.size() >= 20) page + 1 else page, "cinemeta")
        } else {
            val mediaType = if (kind == "series") "tv" else "movie"
            val path = when {
                selectedYear != null -> "/discover/$mediaType"
                category in listOf("trending", "week") -> "/trending/$mediaType/week"
                category in listOf("top", "top_rated", "rating") -> "/$mediaType/top_rated"
                else -> "/$mediaType/popular"
            }
            val params = mutableMapOf("page" to page.toString(), "language" to "en-US")
            if (selectedYear != null) {
                params[if (mediaType == "tv") "first_air_date_year" else "primary_release_year"] = selectedYear.toString()
                params[if (mediaType == "tv") "first_air_date.lte" else "primary_release_date.lte"] = java.time.LocalDate.now().toString()
            }
            val data = tmdb(path, params, key)
            val rows = data.getAsJsonArray("results") ?: throw IOException("TMDb returned an invalid catalog.")
            DiscoverResult(rows.map { tmdbItem(it.asJsonObject, kind) }, page, data.number("total_pages")?.toInt() ?: page, "tmdb")
        }
    }

    suspend fun heroMetadata(item:MediaItem):MediaItem=withContext(Dispatchers.IO) {
        if(tmdbKey().isBlank())return@withContext item.copy(rating=null,cinematicArtwork=item.cinematicImage)
        val kind=if(item.isSeries)"tv" else "movie"
        val id=item.id.removePrefix("tmdb:").takeIf{it.matches(Regex("\\d+"))} ?: item.imdbId?.takeIf{it.matches(Regex("tt\\d+"))}?.let{imdb->
            tmdb("/find/$imdb",mapOf("external_source" to "imdb_id"),tmdbKey()).objects(if(item.isSeries)"tv_results"else "movie_results").firstOrNull()?.text("id")
        } ?: return@withContext item.copy(rating=null)
        val data=tmdb("/$kind/$id",mapOf("append_to_response" to "images,external_ids,${if(item.isSeries)"content_ratings" else "release_dates"}","include_image_language" to "en,null","language" to "en-US"),tmdbKey())
        val verifiedImdb=data.objectOrNull("external_ids")?.text("imdb_id")
        if(item.imdbId!=null&&verifiedImdb!=null&&item.imdbId!=verifiedImdb)return@withContext item.copy(rating=null)
        val us=data.objectOrNull(if(item.isSeries)"content_ratings" else "release_dates")?.objects("results")?.firstOrNull{it.text("iso_3166_1")=="US"}
        val certification=if(item.isSeries)us?.text("rating") else us?.objects("release_dates")?.firstNotNullOfOrNull{it.text("certification")?.takeIf{value->value.isNotBlank()}}
        val runtime=(if(item.isSeries)data.get("episode_run_time")?.takeIf{it.isJsonArray}?.asJsonArray?.firstOrNull()?.takeIf{it.isJsonPrimitive}?.asInt ?: data.objectOrNull("last_episode_to_air")?.number("runtime")?.toInt() ?: data.objectOrNull("next_episode_to_air")?.number("runtime")?.toInt() else data.number("runtime")?.toInt())?.takeIf{it>0}?.let{"$it min"}
        item.copy(rating=null,imdbId=data.objectOrNull("external_ids")?.text("imdb_id") ?: item.imdbId,
            cinematicArtwork=CinematicArtwork.choose(data.objectOrNull("images"),item.background?:item.poster),
            logo=CinematicArtwork.logo(data.objectOrNull("images"),item.logo),genres=data.objects("genres").mapNotNull{it.text("name")}.ifEmpty{item.genres},runtime=runtime ?: item.runtime,certification=certification?.takeIf{it.isNotBlank()})
    }
    suspend fun imdbIdentity(item: MediaItem): String? = withContext(Dispatchers.IO) {
        item.imdbId?.let { return@withContext it }
        val id=item.id.removePrefix("tmdb:").takeIf { it.matches(Regex("\\d+")) } ?: return@withContext null
        val data=tmdb("/${if(item.isSeries)"tv" else "movie"}/$id/external_ids",emptyMap(),tmdbKey())
        data.text("imdb_id")?.takeIf { it.matches(Regex("tt\\d+")) }
    }

    /** Public artwork only. Episode playback/progress/identity stays with the original row. */
    internal suspend fun continueArtwork(row:MediaItem,resolveParent:suspend(String)->MediaItem?={null}):ContinueArtwork=withContext(Dispatchers.IO){
        val fallback=ContinueArtwork(row.continueBackground,row.logo)
        val key=tmdbKey();if(key.isBlank())return@withContext fallback
        val parentId=row.jellyfinSeriesId?.takeIf{row.isSeries&&it.matches(Regex("[A-Za-z0-9-]+"))}
        val needsParent=parentId!=null&&row.imdbId==null&&!row.id.matches(Regex("(?:tmdb:)?\\d+|tt\\d+"))&&
            row.jellyfinProviderKeys.orEmpty().none{it.matches(Regex("tmdb:\\d+"))}
        val parent=if(needsParent)resolveParent(parentId!!) else null
        val item=if(parent?.isSeries==true&&parent.jellyfinId==parentId)
            row.copy(imdbId=parent.imdbId,jellyfinProviderKeys=parent.jellyfinProviderKeys) else row
        val kind=if(item.isSeries)"tv"else "movie"
        val expectedImdb=(item.imdbId?:item.id).takeIf{it.matches(Regex("tt\\d+"))}
        val id=item.id.removePrefix("tmdb:").takeIf{it.matches(Regex("\\d+"))}
            ?: expectedImdb?.let{imdb->
                tmdb("/find/$imdb",mapOf("external_source" to "imdb_id"),key).objects(if(item.isSeries)"tv_results"else "movie_results").firstOrNull()?.text("id")
            } ?: item.jellyfinProviderKeys.orEmpty().filter{it.matches(Regex("tmdb:\\d+"))}.singleOrNull()?.substringAfter(':')
            ?: return@withContext fallback
        val data=tmdb("/$kind/$id",mapOf("append_to_response" to "images,external_ids","include_image_language" to "en,null","language" to "en-US"),key)
        val imdb=data.objectOrNull("external_ids")?.text("imdb_id")
        if(expectedImdb!=null&&imdb!=null&&expectedImdb!=imdb)return@withContext fallback
        val images=data.objectOrNull("images")
        ContinueArtwork(CinematicArtwork.neutralBackdrop(images,item.continueBackground),
            CinematicArtwork.logo(images,item.logo))
    }
    suspend fun search(query: String, page: Int = 1, type: String = "all"): DiscoverResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext DiscoverResult(source = "cinemeta")
        val kinds = if (type in listOf("movie", "series")) listOf(type) else listOf("movie", "series")
        val key = tmdbKey()
        if (key.isNotBlank() && !query.matches(Regex("tt\\d+"))) {
            val kind = if (type == "series") "tv" else type
            val data = tmdb("/search/${if (kind == "all") "multi" else kind}", mapOf("query" to query, "page" to page.toString(), "include_adult" to "false"), key)
            val rows = data.getAsJsonArray("results") ?: throw IOException("TMDb returned invalid search results.")
            return@withContext DiscoverResult(rows.map { it.asJsonObject }.filter { type != "all" || it.text("media_type") in listOf("movie", "tv") }.map { tmdbItem(it, if (it.text("media_type") == "tv" || type == "series") "series" else "movie") }, page, data.number("total_pages")?.toInt() ?: page, "tmdb")
        }
        var succeeded = false
        val items = kinds.flatMap { kind ->
            try {
                val path = if (query.matches(Regex("tt\\d+"))) "/meta/$kind/$query.json" else "/catalog/$kind/top/search=${java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")}.json"
                val data = request("https://v3-cinemeta.strem.io$path", emptyMap(), null)
                if (!data.has("meta") && data.get("metas")?.isJsonArray != true) throw IOException("Cinemeta returned invalid search results.")
                succeeded = true
                if (data.has("meta") && data.get("meta").isJsonObject) listOf(cinemetaItem(data.getAsJsonObject("meta"), kind))
                else data.getAsJsonArray("metas")?.map { cinemetaItem(it.asJsonObject, kind) }.orEmpty()
            } catch (error: IOException) { emptyList() }
        }
        if (!succeeded) throw IOException("Cinemeta search unavailable. Check your connection and retry.")
        DiscoverResult(items.distinctBy { "${it.type}:${it.mediaKey}" }, 1, 1, "cinemeta")
    }

    suspend fun details(type: String, id: String, firstSeasonOnly: Boolean = false): TitleDetails = DirectTitleMetadata(tmdbKey, preferCinemeta, request).load(type, id, firstSeasonOnly)
    suspend fun season(id:String, season:Int):List<Episode> = DirectTitleMetadata(tmdbKey,preferCinemeta,request).season(id,season)

    private fun tmdb(path: String, params: Map<String, String>, key: String): JsonObject = request("https://api.themoviedb.org/3$path", if (key.contains('.')) params else params + ("api_key" to key), if (key.contains('.')) key else null)

    companion object {
        private fun image(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w780$it" }
        fun cinemetaItem(data: JsonObject, type: String): MediaItem {
            val id = data.text("id") ?: throw IOException("Cinemeta title has no identifier.")
            return MediaItem(id, id.takeIf { it.matches(Regex("tt\\d+")) }, type, data.text("name").orEmpty(), data.text("year") ?: data.text("releaseInfo")?.take(4), data.text("runtime"), data.text("description"), data.text("poster"), data.text("background"), genres = data.getAsJsonArray("genres")?.mapNotNull { it.takeIf { it.isJsonPrimitive }?.asString }.orEmpty(), aliases = data.getAsJsonArray("aliases")?.mapNotNull { it.takeIf { it.isJsonPrimitive }?.asString }.orEmpty())
        }
        fun tmdbItem(data: JsonObject, type: String): MediaItem {
            val id = data.text("id") ?: throw IOException("TMDb title has no identifier.")
            val imdb = data.text("imdb_id") ?: data.objectOrNull("external_ids")?.text("imdb_id")
            return MediaItem("tmdb:$id", imdb, type, data.text("title") ?: data.text("name").orEmpty(), (data.text("release_date") ?: data.text("first_air_date"))?.take(4), data.text("runtime")?.let { "$it min" }, data.text("overview"), image(data.text("poster_path")), image(data.text("backdrop_path")), tmdbRating = data.number("vote_average"), genres = data.getAsJsonArray("genres")?.mapNotNull { it.asJsonObject.text("name") }.orEmpty(), aliases = listOfNotNull(data.text("original_title"),data.text("original_name")))
        }
    }
}

internal fun JsonObject.text(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString
internal fun JsonObject.number(key: String): Double? = text(key)?.toDoubleOrNull()

internal val providerClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
internal class ProviderFailure(val status: Int, message: String): IOException(message)
internal fun providerJson(url: String, params: Map<String, String> = emptyMap(), bearer: String? = null): JsonObject {
    val publicCache = bearer == null && params.isEmpty() && url.startsWith("https://v3-cinemeta.strem.io/")
    val metadataKey=MetadataResponseCache.key(url,params,bearer)
    metadataKey?.let { MetadataResponseCache.read(it)?.let { cached -> return cached } }
    if (publicCache) PublicMetadataCache.read(url)?.let { return it }
    val builder = url.toHttpUrl().newBuilder()
    params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
    val request = Request.Builder().url(builder.build()).apply { if (bearer != null) header("Authorization", "Bearer $bearer") }.build()
    providerClient.newCall(request).execute().use { response ->
        // Do not include request URLs or raw provider responses: either can contain credentials.
        if (!response.isSuccessful) throw ProviderFailure(response.code, when (response.code) {
            401, 403 -> "Provider access denied. Check the API key in Settings."
            429 -> "Provider rate limit reached. Wait and retry."
            else -> "Provider request failed (${response.code}). Please retry."
        })
        val body = response.body?.string() ?: throw IOException("Provider returned an empty response.")
        val parsed = try { JsonParser.parseString(body).asJsonObject } catch (_: RuntimeException) { throw IOException("Provider returned an invalid response.") }
        if (publicCache) PublicMetadataCache.write(url, parsed)
        metadataKey?.let { MetadataResponseCache.write(it,parsed) }
        return parsed
    }
}


