package app.stak.nativeapp.data

import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class DirectDiscovery(
    private val key: () -> String = { NativeRuntime.settings.tmdbKey.orEmpty() },
    private val omdbKey: () -> String = { NativeRuntime.settings.omdbKey.orEmpty() },
    private val request: (String, Map<String, String>, String?) -> JsonObject = ::providerJson,
    private val today: () -> LocalDate = { LocalDate.now() }
) {
    private val identities = ConcurrentHashMap<String, String>()
    private val ratingLocks = ConcurrentHashMap<String, Any>()
    private val quotas = ConcurrentHashMap<String, Long>()
    private val ratings = ConcurrentHashMap<String, Pair<Long, Double?>>()
    private fun tmdb(path: String, params: Map<String, String> = emptyMap()): JsonObject {
        val token = key().trim()
        if (token.isBlank()) throw IOException("Configure your TMDb API key or read access token in Metadata sources.")
        return request("https://api.themoviedb.org/3$path", params + if (token.contains('.')) emptyMap() else mapOf("api_key" to token), token.takeIf { it.contains('.') })
    }
    private suspend fun <T,R> limited(items: List<T>, block: suspend (T) -> R): List<R> = coroutineScope {
        val gate = Semaphore(4)
        items.map { item -> async { gate.withPermit { block(item) } } }.awaitAll()
    }
    suspend fun genres(type: String): List<DiscoveryOption> = withContext(Dispatchers.IO) {
        if (key().isBlank()) return@withContext fallbackGenres.map { DiscoveryOption(it, it) }
        val data = tmdb("/genre/${if (type == "series") "tv" else "movie"}/list")
        data.requiredArray("genres").map { DiscoveryOption(it.text("id").orEmpty(), it.text("name").orEmpty()) }
    }
    suspend fun providers(type: String, region: String): List<DiscoveryOption> = withContext(Dispatchers.IO) {
        require(region.matches(Regex("[A-Z]{2}"))) { "Choose a valid region." }
        val data = tmdb("/watch/providers/${if (type == "series") "tv" else "movie"}", mapOf("watch_region" to region))
        data.requiredArray("results").map { DiscoveryOption(it.text("provider_id").orEmpty(), it.text("provider_name").orEmpty(), it.text("logo_path")?.let { path -> "https://image.tmdb.org/t/p/w92$path" }) }
    }
    suspend fun regions(): List<DiscoveryOption> = withContext(Dispatchers.IO) {
        tmdb("/watch/providers/regions").requiredArray("results").map { DiscoveryOption(it.text("iso_3166_1").orEmpty(), it.text("english_name").orEmpty()) }.sortedBy { it.name }
    }
    suspend fun discover(type: String, category: String, page: Int, filters: DiscoveryFilters, region: String): DiscoverResult = withContext(Dispatchers.IO) {
        val normalized = normalizeGenre(type, filters)
        checkRatings(normalized)
        val plan = discoveryRequest(type, category, page, normalized, region, today())
        if (plan.empty) return@withContext DiscoverResult(page = page, totalPages = page, source = "tmdb")
        val data = tmdb(plan.path, plan.params)
        val kind = if (type == "series") "tv" else "movie"
        var rows = data.requiredArray("results").map { kind to it }
        if (category == "top") rows = rows.filter { (_, row) -> val date = row.text("release_date") ?: row.text("first_air_date"); date != null && date <= today().toString() && (row.number("vote_count") ?: 0.0) >= 200 }
        finish(rows, page, data.number("total_pages")?.toInt() ?: page, normalized, region, category == "trending")
    }
    suspend fun search(type: String, query: String, page: Int, filters: DiscoveryFilters, region: String, cursor: Map<String,Int>? = null): DiscoverResult = withContext(Dispatchers.IO) {
        filters.validationError()?.let { throw IllegalArgumentException(it) }
        val hasProviderFilters = listOf(filters.genre,filters.certification,filters.language,filters.yearFrom,filters.yearTo,filters.imdbRating,filters.provider,filters.sort).any { it.isNotBlank() }
        if (type == "all" && hasProviderFilters) {
            val kinds = cursor?.keys?.toList() ?: listOf("movie","series")
            val results = limited(kinds) { kind ->
                try { kind to Result.success(search(kind,query,cursor?.get(kind) ?: page,filters,region)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { kind to Result.failure<DiscoverResult>(error) }
            }
            if (results.isNotEmpty() && results.all { it.second.isFailure }) throw results.first().second.exceptionOrNull()!!
            val successful = results.mapNotNull { it.second.getOrNull() }
            val next = results.mapNotNull { (kind,result) -> result.getOrNull()?.takeIf { it.page < it.totalPages }?.let { kind to it.page + 1 } }.toMap()
            return@withContext DiscoverResult(successful.flatMap { it.items }.distinctBy { "${it.type}:${it.mediaKey}" },page,if (next.isEmpty()) page else page+1,"tmdb",notice = successful.mapNotNull { it.notice }.firstOrNull(),ratingErrors = successful.sumOf { it.ratingErrors },verifiedImdb = filters.imdbRating.isNotBlank(),nextTypePages = next,partialErrors = if (results.any { it.second.isFailure }) listOf("Some TMDb search results could not load.") else emptyList())
        }
        val selected = if (type == "series") "tv" else if (type == "movie") "movie" else "multi"
        val f = if (type == "all" && filters.genre.isNotBlank() && !filters.genre.matches(Regex("\\d+(?:[|,]\\d+)*"))) {
            val ids = (genres("movie") + genres("series")).filter { it.name.equals(filters.genre,true) }.map { it.id }.distinct()
            if (ids.isEmpty()) throw IOException("This genre is unavailable for the selected media type.")
            filters.copy(genre = ids.joinToString("|"))
        } else if (type == "all") filters else normalizeGenre(type, filters)
        checkRatings(f)
        val q = query.trim().take(160)
        if (q.isBlank()) return@withContext DiscoverResult(source = "tmdb")
        val found = q.matches(Regex("tt\\d+"))
        var data = if (found) tmdb("/find/$q", mapOf("external_source" to "imdb_id")) else tmdb("/search/$selected", mapOf("query" to q, "page" to page.coerceIn(1,500).toString(), "include_adult" to "false"))
        var rows = if (found) listOf("movie", "tv").filter { selected == "multi" || selected == it }.flatMap { kind -> data.array("${kind}_results").map { kind to it.apply { addProperty("imdb_id", q) } } }
        else data.requiredArray("results").mapNotNull { row -> val kind = if (selected == "multi") row.text("media_type") else selected; if (kind in listOf("movie", "tv")) kind!! to row else null }
        val year = Regex("^(.*?)\\s*[( ]((?:19|20)\\d{2})\\)?$").matchEntire(q)
        if (!found && year != null && rows.none { (_, row) -> listOf(row.text("title"), row.text("name")).any { it.equals(q, true) } }) {
            data = tmdb("/search/$selected", mapOf("query" to year.groupValues[1].trim(), "page" to page.coerceIn(1,500).toString(), "include_adult" to "false"))
            rows = data.requiredArray("results").mapNotNull { row -> val kind = if (selected == "multi") row.text("media_type") else selected; if (kind in listOf("movie", "tv") && (row.text("release_date") ?: row.text("first_air_date")).orEmpty().startsWith(year.groupValues[2])) kind!! to row else null }
        }
        finish(rows, if (found) 1 else page, if (found) 1 else data.number("total_pages")?.toInt() ?: page, f, region, true)
    }
    suspend fun filterFallback(result: DiscoverResult, filters: DiscoveryFilters): DiscoverResult = withContext(Dispatchers.IO) {
        filters.validationError()?.let { throw IllegalArgumentException(it) }
        if (listOf(filters.provider, filters.language, filters.certification).any { it.isNotBlank() }) throw IOException("Provider, original-language and age-rating filters require TMDb. Configure TMDb or clear these filters.")
        checkRatings(filters)
        var items = result.items.filter { item ->
            val year = item.year?.take(4)?.toIntOrNull()
            (filters.genre.isBlank() || item.genres.any { it.equals(filters.genre, true) }) &&
                (filters.yearFrom.isBlank() || year != null && year >= filters.yearFrom.toInt()) &&
                (filters.yearTo.isBlank() || year != null && year <= filters.yearTo.toInt())
        }
        var failures = 0
        if (filters.imdbRating.isNotBlank()) items = limited(items) { item ->
            val imdb = item.imdbId?.takeIf { it.matches(Regex("tt\\d+")) } ?: return@limited null
            try {
                val verified = rating(imdb)
                if (verified != null && verified >= filters.imdbRating.toDouble()) item.copy(rating = verified) else null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { synchronized(this) { failures++ }; null }
        }.filterNotNull()
        if (filters.sort == "original_title.asc") items = items.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
        if (filters.sort.startsWith("primary_release_date")) {
            items = items.sortedBy { it.year?.take(4)?.toIntOrNull() ?: 0 }
            if (filters.sort.endsWith("desc")) items = items.reversed()
        }
        result.copy(items = items, notice = "TMDb key missing. Search is using Cinemeta. Configure TMDb in Metadata sources.", ratingErrors = failures, verifiedImdb = filters.imdbRating.isNotBlank())
    }
    private suspend fun normalizeGenre(type: String, filters: DiscoveryFilters): DiscoveryFilters {
        if (filters.genre.isBlank() || filters.genre.matches(Regex("\\d+(?:[|,]\\d+)*"))) return filters
        val genre = genres(type).find { it.name.equals(filters.genre, true) } ?: throw IOException("This genre is unavailable for the selected media type.")
        return filters.copy(genre = genre.id)
    }
    private fun checkRatings(filters: DiscoveryFilters) {
        if (filters.imdbRating.isNotBlank() && omdbKey().isBlank()) throw IOException("Configure OMDb in Metadata sources before using the IMDb filter. Daily OMDb quota applies.")
    }
    private fun identity(kind: String, row: JsonObject): String? {
        row.text("imdb_id")?.takeIf { it.matches(Regex("tt\\d+")) }?.let { return it }
        val id = row.text("id") ?: return null
        val cacheKey = "$kind:$id"
        identities[cacheKey]?.let { return it.takeIf { value -> value.isNotBlank() } }
        val result = tmdb("/$kind/$id/external_ids").text("imdb_id")?.takeIf { it.matches(Regex("tt\\d+")) }
        identities[cacheKey] = result.orEmpty()
        return result
    }
    private fun rating(imdb: String): Double? {
        val secret = omdbKey()
        val scope = java.security.MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()).joinToString("") { "%02x".format(it) }
        val cacheKey = "$scope:$imdb"
        return synchronized(ratingLocks.computeIfAbsent(cacheKey) { Any() }) {
            val now = System.currentTimeMillis()
            val cached = ratings[cacheKey] ?: VerifiedRatingCache.read(scope, imdb)
            if (cached != null && now - cached.first < 86_400_000L) { ratings[cacheKey] = cached; return@synchronized cached.second }
            if (maxOf(quotas[scope] ?: 0L, VerifiedRatingCache.quotaUntil(scope)) > now) throw IOException("OMDb daily quota reached. Cached ratings remain available; try after the quota resets.")
            fun quota() { val until = now + 86_400_000L; quotas[scope] = until; VerifiedRatingCache.markQuota(scope,until) }
            val data = try { request("https://www.omdbapi.com/", mapOf("apikey" to secret, "i" to imdb, "plot" to "full"), null) }
                catch (error: ProviderFailure) { if (error.status == 429) quota(); throw error }
            if (data.text("Response") == "False") {
                if (Regex("limit|quota",RegexOption.IGNORE_CASE).containsMatchIn(data.text("Error").orEmpty())) quota()
                throw IOException("OMDb rating could not be checked. Check the API key and daily quota.")
            }
            if (!data.has("imdbRating") && !data.has("Ratings")) throw IOException("OMDb returned invalid rating data.")
            val value = data.number("imdbRating")?.takeIf { it > 0 && it <= 10 }
            ratings[cacheKey] = now to value
            VerifiedRatingCache.write(scope,imdb,now,value)
            value
        }
    }
    private suspend fun finish(input: List<Pair<String,JsonObject>>, page: Int, pages: Int, filters: DiscoveryFilters, region: String, pageFilter: Boolean): DiscoverResult {
        var rows = input.filter { (_, row) ->
            if (!pageFilter) true else {
                val date = row.text("release_date") ?: row.text("first_air_date").orEmpty()
                val year = date.take(4).toIntOrNull()
                val ids = row.getAsJsonArray("genre_ids")?.map { it.asString }.orEmpty()
                (filters.genre.isBlank() || filters.genre.split('|', ',').any { it in ids }) &&
                    (filters.yearFrom.isBlank() || year != null && year >= filters.yearFrom.toInt()) &&
                    (filters.yearTo.isBlank() || year != null && year <= filters.yearTo.toInt()) &&
                    (filters.language.isBlank() || row.text("original_language") == filters.language)
            }
        }
        if (pageFilter && filters.provider.isNotBlank()) rows = limited(rows) { (kind,row) ->
            val data = tmdb("/$kind/${row.text("id")}/watch/providers").getAsJsonObject("results")?.getAsJsonObject(region)
            val accepted = filters.provider.split('|').toSet()
            val matches = data != null && listOf("flatrate", "free", "ads").any { field -> data.array(field).any { it.text("provider_id") in accepted } }
            if (matches) kind to row else null
        }.filterNotNull()
        if (filters.certification.isNotBlank()) rows = limited(rows) { (kind,row) ->
            if (kind == "movie" && !pageFilter) return@limited kind to row
            val data = tmdb("/$kind/${row.text("id")}/${if (kind == "tv") "content_ratings" else "release_dates"}")
            val us = data.requiredArray("results").find { it.text("iso_3166_1") == "US" }
            val matches = if (kind == "tv") us?.text("rating") == filters.certification else us?.array("release_dates")?.any { it.text("certification") == filters.certification } == true
            if (matches) kind to row else null
        }.filterNotNull()
        if (pageFilter && filters.sort.isNotBlank()) {
            val field = filters.sort.substringBefore('.')
            rows = if (field == "original_title") rows.sortedBy { (_,row) -> (row.text("original_title") ?: row.text("original_name") ?: row.text("title") ?: row.text("name")).orEmpty().lowercase(java.util.Locale.ROOT) }
                else if (field == "primary_release_date") rows.sortedBy { (_,row) -> row.text("release_date") ?: row.text("first_air_date").orEmpty() }
                else rows.sortedBy { (_,row) -> row.number(field) ?: 0.0 }
            if (filters.sort.endsWith("desc")) rows = rows.reversed()
        }
        var failures = 0
        val converted = limited(rows) { (kind,row) ->
            val type = if (kind == "tv") "series" else "movie"
            var item = DirectCatalog.tmdbItem(row, type)
            if (filters.imdbRating.isNotBlank()) {
                try {
                    val imdb = identity(kind, row) ?: return@limited null
                    val verified = rating(imdb) ?: return@limited null
                    if (verified < filters.imdbRating.toDouble()) return@limited null
                    item = item.copy(imdbId = imdb, rating = verified)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { synchronized(this) { failures++ }; return@limited null }
            } else {
                try { item = item.copy(imdbId = identity(kind, row)) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* Unknown identities stay visible. */ }
            }
            item
        }.filterNotNull().distinctBy { "${it.type}:${it.mediaKey}" }
        val scoped = pageFilter && listOf(filters.genre, filters.yearFrom, filters.yearTo, filters.language, filters.provider, filters.certification, filters.sort).any { it.isNotBlank() } || filters.certification.isNotBlank() && input.any { it.first == "tv" }
        return DiscoverResult(converted, page, pages.coerceIn(1,500), "tmdb", notice = if (scoped) "Filters apply to the returned provider page; availability is regional TMDb / JustWatch data." else null, ratingErrors = failures, verifiedImdb = filters.imdbRating.isNotBlank())
    }
    companion object { val fallbackGenres = listOf("Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family", "Fantasy", "History", "Horror", "Mystery", "Romance", "Science Fiction", "Thriller", "War", "Western") }
}

internal fun JsonObject.array(name: String): List<JsonObject> = get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject }.orEmpty()

internal fun JsonObject.requiredArray(name: String): List<JsonObject> {
    if (get(name)?.isJsonArray != true) throw IOException("Provider returned invalid catalog data. Please retry.")
    return array(name)
}
