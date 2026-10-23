package app.stak.nativeapp

import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

data class NativeDiscoveryPage(val items: List<Media>, val next: Int?, val filteredPage: Boolean = false)

fun discoveryParams(type: String, category: String, page: Int, filters: Map<String, String>, today: LocalDate): Map<String, String> {
    require(discoveryYearError(filters) == null) { discoveryYearError(filters).orEmpty() }
    val date = if (type == "series") "first_air_date" else "primary_release_date"
    return buildMap {
        put("page", page.coerceIn(1, 500).toString())
        put("include_adult", "false")
        when (category) {
            "top" -> { put("sort_by", "vote_average.desc"); put("vote_count.gte", "300") }
            "new" -> { put("$date.gte", today.minusDays(90).toString()); put("$date.lte", today.toString()); put("sort_by", "$date.desc") }
            "upcoming" -> { put("$date.gte", today.toString()); put("$date.lte", today.plusYears(1).toString()); put("sort_by", "$date.asc") }
            "hidden" -> { put("sort_by", "popularity.asc"); put("vote_count.gte", "100"); put("vote_average.gte", "7") }
            else -> put("sort_by", "popularity.desc")
        }
        filters["genre"]?.takeIf { it.isNotBlank() }?.let { put("with_genres", it) }
        filters["language"]?.takeIf { it.isNotBlank() }?.let { put("with_original_language", it) }
        filters["provider"]?.takeIf { it.isNotBlank() }?.let { put("with_watch_providers", it); put("watch_region", "US") }
        filters["year_from"]?.takeIf { it.isNotBlank() }?.let { put("$date.gte", "$it-01-01") }
        filters["year_to"]?.takeIf { it.isNotBlank() }?.let { put("$date.lte", "$it-12-31") }
        filters["certification"]?.takeIf { it.isNotBlank() && type != "series" }?.let { put("certification", it); put("certification_country", "US") }
        discoverySort(filters["sort"].orEmpty(), type).takeIf { it.isNotBlank() }?.let { put("sort_by", it) }
    }
}

internal fun matchesDiscoveryFields(item: JSONObject, filters: Map<String, String>): Boolean {
    val year = item.optString("release_date", item.optString("first_air_date")).take(4).toIntOrNull()
    return (filters["genre"].isNullOrBlank() || item.optJSONArray("genre_ids").strings().contains(filters["genre"])) &&
        (filters["language"].isNullOrBlank() || item.optString("original_language") == filters["language"]) &&
        (filters["year_from"].isNullOrBlank() || (year != null && year >= filters.getValue("year_from").toInt())) &&
        (filters["year_to"].isNullOrBlank() || (year != null && year <= filters.getValue("year_to").toInt()))
}

suspend fun Repository.discoveryPage(
    type: String, category: String = "popular", query: String = "", page: Int = 1,
    filters: Map<String, String> = emptyMap(),
): NativeDiscoveryPage {
    require(discoveryYearError(filters) == null) { discoveryYearError(filters).orEmpty() }
    if (credentials.get("tmdb_key").isBlank()) {
        require(category == "popular" && filters.values.all { it.isBlank() }) {
            "Configure a TMDb key to use curated discovery and filters."
        }
        if (query.isNotBlank()) {
            val found = searchTitles(query, page)
            return NativeDiscoveryPage(found.items, found.next)
        }
        val items = catalog(type, skip = (page - 1) * 100)
        return NativeDiscoveryPage(items, if (items.isNotEmpty()) page + 1 else null)
    }
    val kind = if (type == "series") "tv" else "movie"
    val term = query.trim().take(160)
    val search = term.isNotBlank()
    val trending = !search && category == "trending"
    val imdb = search && Regex("tt[0-9]+").matches(term)
    var data = when {
        imdb -> {
            val found = tmdb("/find/$term", mapOf("external_source" to "imdb_id"))
            val items = found.optJSONArray("movie_results").objects().map { it.put("media_type", "movie") } +
                found.optJSONArray("tv_results").objects().map { it.put("media_type", "tv") }
            JSONObject().put("results", org.json.JSONArray(items)).put("total_pages", 1)
        }
        search -> tmdb("/search/multi", mapOf("query" to term, "include_adult" to "false", "page" to page.toString()))
        trending -> tmdb("/trending/$kind/week", mapOf("page" to page.toString()))
        else -> tmdb("/discover/$kind", discoveryParams(type, category, page, filters, today))
    }
    fun mediaType(item: JSONObject) = if (search) { if (item.optString("media_type") == "tv") "series" else "movie" } else type
    fun entries(root: JSONObject) = root.optJSONArray("results").objects()
        .filter { it.optInt("id") > 0 && (!search || it.optString("media_type") in listOf("movie", "tv")) }
    var items = entries(data)
    val yearQuery = Regex("^(.*?)\\s+\\(?((?:19|20)\\d{2})\\)?$").matchEntire(term)
    if (search && !imdb && yearQuery != null && items.none { matchesSearchTitle(tmdbMedia(it, mediaType(it)), term) }) {
        data = tmdb("/search/multi", mapOf("query" to yearQuery.groupValues[1], "include_adult" to "false", "page" to page.toString()))
        items = entries(data).filter { it.optString("release_date", it.optString("first_air_date")).startsWith(yearQuery.groupValues[2]) }
    }
    if (search || trending) items = items.filter { matchesDiscoveryFields(it, filters) }
    val gate = Semaphore(4)
    val accepted = coroutineScope {
        items.map { item -> async {
            gate.withPermit {
                val itemType = mediaType(item)
                val itemKind = if (itemType == "series") "tv" else "movie"
                val id = item.optInt("id")
                val provider = filters["provider"].orEmpty()
                if ((search || trending) && provider.isNotBlank()) {
                    val local = tmdb("/$itemKind/$id/watch/providers").optJSONObject("results")?.optJSONObject("US")
                    val wanted = provider.split('|').toSet()
                    if (listOf("flatrate", "free", "ads").none { key -> local?.optJSONArray(key).objects().any { it.optInt("provider_id").toString() in wanted } }) return@withPermit null
                }
                val certification = filters["certification"].orEmpty()
                if (certification.isNotBlank() && (itemType == "series" || search || trending)) {
                    val results = tmdb("/$itemKind/$id/${if (itemType == "series") "content_ratings" else "release_dates"}").optJSONArray("results").objects()
                    val matches = results.any { region -> region.optString("iso_3166_1") == "US" &&
                        if (itemType == "series") region.optString("rating") == certification
                        else region.optJSONArray("release_dates").objects().any { it.optString("certification") == certification }
                    }
                    if (!matches) return@withPermit null
                }
                item
            }
        } }.awaitAll().filterNotNull()
    }
    val sort = filters["sort"].orEmpty()
    val ordered = if ((search || trending) && sort.isNotBlank()) {
        val ascending = sort.endsWith(".asc")
        val comparator = when (sort.substringBefore('.')) {
            "primary_release_date", "first_air_date" -> compareBy<JSONObject> { it.optString("release_date", it.optString("first_air_date")) }
            "title", "name" -> compareBy<JSONObject> { it.optString("title", it.optString("name")).lowercase(java.util.Locale.ROOT) }
            "vote_average" -> compareBy<JSONObject> { it.optDouble("vote_average", 0.0) }
            else -> compareBy<JSONObject> { it.optDouble("popularity", 0.0) }
        }
        accepted.sortedWith(if (ascending) comparator else comparator.reversed())
    } else if (search) accepted.sortedBy { searchRelevance(tmdbMedia(it, mediaType(it)), term) } else accepted
    val total = data.optInt("total_pages", page).coerceIn(0, 500)
    return NativeDiscoveryPage(
        ordered.map { item -> remember(tmdbMedia(item, mediaType(item)).let { if (imdb) it.copy(id = term, imdbId = term) else it }) }.distinctBy { it.key },
        if (page < total) page + 1 else null,
        ((search || trending) && filters.values.any { it.isNotBlank() }) || (type == "series" && !filters["certification"].isNullOrBlank()),
    )
}
