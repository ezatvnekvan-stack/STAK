package app.stak.nativeapp

import java.util.Locale
import org.json.JSONObject

val discoveryCategories = listOf("popular", "trending", "top", "new", "upcoming", "hidden")
private val discoveryFilterKeys = setOf("genre", "year_from", "year_to", "certification", "language", "provider", "sort")

data class DiscoveryPreferences(
    val type: String = "movie",
    val category: String = "popular",
    val filters: Map<String, String> = emptyMap(),
) {
    fun json(): String = JSONObject().put("type", type).put("category", category)
        .put("filters", JSONObject(filters)).toString()

    fun forType(value: String) = copy(type = value, filters = filters - "genre" - "certification")

    companion object {
        fun parse(value: String?): DiscoveryPreferences = try {
            val json = JSONObject(value ?: "{}")
            val filters = json.optJSONObject("filters")
            DiscoveryPreferences(
                type = json.optString("type").takeIf { it == "series" } ?: "movie",
                category = json.optString("category").takeIf { it in discoveryCategories } ?: "popular",
                filters = discoveryFilterKeys.mapNotNull { key ->
                    (filters?.opt(key) as? String)?.takeIf { it.isNotBlank() && it.length <= 80 }?.let { key to it }
                }.toMap(),
            )
        } catch (_: Exception) { DiscoveryPreferences() }
    }
}

fun discoveryYearError(filters: Map<String, String>): String? {
    val from = filters["year_from"].orEmpty()
    val to = filters["year_to"].orEmpty()
    if (listOf(from, to).any { it.isNotEmpty() && (!Regex("[0-9]{4}").matches(it) || it.toInt() !in 1880..2100) })
        return "Use years between 1880 and 2100."
    if (from.isNotEmpty() && to.isNotEmpty() && from.toInt() > to.toInt())
        return "Choose a start year before the end year."
    return null
}

fun discoverySort(value: String, type: String): String = when (value) {
    "primary_release_date.desc", "first_air_date.desc" -> if (type == "series") "first_air_date.desc" else "primary_release_date.desc"
    "title.asc", "name.asc" -> if (type == "series") "name.asc" else "title.asc"
    "popularity.desc", "vote_average.desc" -> value
    else -> ""
}

fun discoveryCertifications(type: String) = if (type == "series")
    listOf("", "TV-Y", "TV-Y7", "TV-G", "TV-PG", "TV-14", "TV-MA")
else listOf("", "G", "PG", "PG-13", "R", "NC-17")

fun discoveryLanguages(query: String, selected: String = ""): List<Pair<String, String>> {
    val term = query.trim()
    return Locale.getISOLanguages().map { code -> code to Locale(code).getDisplayLanguage(Locale.ENGLISH) }
        .filter { (code, name) -> code == selected || code.contains(term, true) || name.contains(term, true) }
        .sortedBy { it.second.lowercase(Locale.ROOT) }
}

fun discoveryChips(filters: Map<String, String>, type: String): List<Pair<String, String>> = buildList {
    filters["provider"]?.takeIf { it.isNotBlank() }?.let { add("provider" to "Selected service") }
    filters["genre"]?.takeIf { it.isNotBlank() }?.let { id ->
        add("genre" to (homeGenres(type).firstOrNull { it.first.toString() == id }?.second ?: "Genre"))
    }
    if (!filters["year_from"].isNullOrBlank() || !filters["year_to"].isNullOrBlank())
        add("year" to "${filters["year_from"]?.takeIf { it.isNotBlank() } ?: "…"}–${filters["year_to"]?.takeIf { it.isNotBlank() } ?: "…"}")
    filters["language"]?.takeIf { it.isNotBlank() }?.let { add("language" to it.uppercase(Locale.ROOT)) }
    filters["certification"]?.takeIf { it.isNotBlank() }?.let { add("certification" to it) }
    filters["sort"]?.takeIf { it.isNotBlank() }?.let { add("sort" to "Custom order") }
}

fun removeDiscoveryFilter(filters: Map<String, String>, key: String) =
    if (key == "year") filters - "year_from" - "year_to" else filters - key
