package app.stak.nativeapp

import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private fun searchName(value: String) =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

private fun editDistance(a: String, b: String): Int {
    val left = a.take(100)
    val right = b.take(100)
    var row = IntArray(right.length + 1) { it }
    left.forEachIndexed { i, x ->
        val next = IntArray(right.length + 1)
        next[0] = i + 1
        right.forEachIndexed { j, y ->
            next[j + 1] = minOf(next[j] + 1, row[j + 1] + 1, row[j] + if (x == y) 0 else 1)
        }
        row = next
    }
    return row.last()
}

/** Same ordering as frontend/src/lib/searchRelevance.js. Stable ties retain provider order. */
fun searchRelevance(media: Media, query: String): Double {
    val raw = query.trim()
    val year = Regex("\\b((?:19|20)\\d{2})\\)?$").find(raw)?.groupValues?.get(1)
    val stripped =
        if (year != null) raw.replace(Regex("\\(?\\b(?:19|20)\\d{2}\\)?$"), "").trim() else raw
    val term = searchName(stripped.ifBlank { raw })
    if (term.isBlank()) return 0.0
    if (searchName(media.imdbId.ifBlank { media.id }) == term) return -1.0
    val name = searchName(media.name)
    if (name == searchName(raw)) return -.5
    val tokens = term.split(' ')
    val words = name.split(' ')
    val score =
        when {
            name == term -> 0.0
            name.startsWith("$term ") -> 100.0
            tokens.all { it in words } -> 200.0 + kotlin.math.abs(name.length - term.length)
            name.contains(term) -> 300.0 + name.length - term.length
            else ->
                400 +
                    (1 -
                        tokens.count { token -> words.any { it.startsWith(token) } }.toDouble() /
                            tokens.size) * 100 +
                    editDistance(name, term).toDouble() / maxOf(name.length, term.length, 1) * 100
        }
    return score + if (year != null && media.year.take(4) != year) 30 else 0
}

data class NativeSearchPage(
    val items: List<Media>,
    val next: Int?,
    val notices: List<String> = emptyList(),
)

suspend fun Repository.searchTitles(query: String, page: Int = 1): NativeSearchPage {
    val term = query.trim().take(160)
    if (term.isBlank()) return NativeSearchPage(emptyList(), null)
    if (credentials.get("tmdb_key").isBlank()) {
        val results = coroutineScope {
            listOf("movie", "series")
                .map { kind ->
                    async {
                        try {
                            Result.success(
                                if (Regex("tt[0-9]+").matches(term)) {
                                    request("https://v3-cinemeta.strem.io/meta/$kind/$term.json")
                                        .optJSONObject("meta")
                                        ?.let {
                                            listOf(remember(Media.fromJson(it).copy(type = kind)))
                                        }
                                        .orEmpty()
                                } else catalog(kind, term)
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Result.failure<List<Media>>(e)
                        }
                    }
                }
                .awaitAll()
        }
        if (results.all { it.isFailure })
            throw IllegalStateException("Cinemeta search unavailable.")
        return NativeSearchPage(
            results
                .flatMap { it.getOrDefault(emptyList()) }
                .distinctBy { it.key }
                .sortedBy { searchRelevance(it, term) },
            null,
            listOf(
                "TMDb key missing. Search is using Cinemeta. Configure TMDb in Metadata sources."
            ),
        )
    }
    var totalPages = page
    val media =
        if (Regex("tt[0-9]+").matches(term)) {
            val found = tmdb("/find/$term", mapOf("external_source" to "imdb_id"))
            listOf("movie" to "movie_results", "series" to "tv_results").flatMap { (kind, key) ->
                found.optJSONArray(key).objects().map {
                    tmdbMedia(it, kind).copy(id = term, imdbId = term)
                }
            }
        } else {
            val params =
                mapOf("query" to term, "include_adult" to "false", "page" to page.toString())
            var result = tmdb("/search/multi", params)
            val parts = Regex("^(.*?)\\s+\\(?((?:19|20)\\d{2})\\)?$").matchEntire(term)
            val year = parts?.groupValues?.get(2).orEmpty()
            if (
                year.isNotBlank() &&
                    result.optJSONArray("results").objects().none {
                        it.optString("release_date", it.optString("first_air_date"))
                            .startsWith(year)
                    }
            )
                result = tmdb("/search/multi", params + ("query" to parts!!.groupValues[1]))
            totalPages = result.optInt("total_pages", page).coerceIn(page, 500)
            result
                .optJSONArray("results")
                .objects()
                .filter { it.optString("media_type") in listOf("movie", "tv") }
                .filter {
                    year.isBlank() ||
                        it.optString("release_date", it.optString("first_air_date"))
                            .startsWith(year)
                }
                .map {
                    tmdbMedia(it, if (it.optString("media_type") == "tv") "series" else "movie")
                }
        }
    val gate = Semaphore(4)
    val canonical = coroutineScope {
        media
            .map { item ->
                async {
                    gate.withPermit {
                        if (item.imdbId.isNotBlank()) remember(item)
                        else
                            try {
                                val kind = if (item.type == "series") "tv" else "movie"
                                val imdb =
                                    tmdb("/$kind/${item.tmdbId}/external_ids")
                                        .optString("imdb_id")
                                        .takeIf { Regex("tt[0-9]+").matches(it) }
                                remember(
                                    if (imdb != null) item.copy(id = imdb, imdbId = imdb) else item
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                remember(item)
                            }
                    }
                }
            }
            .awaitAll()
    }
    return NativeSearchPage(
        canonical.distinctBy { it.key }.sortedBy { searchRelevance(it, term) },
        if (page < totalPages) page + 1 else null,
    )
}
