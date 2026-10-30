package app.stak.nativeapp.data

import java.time.LocalDate

data class DiscoveryFilters(
    val genre: String = "",
    val certification: String = "",
    val language: String = "",
    val yearFrom: String = "",
    val yearTo: String = "",
    val imdbRating: String = "",
    val provider: String = "",
    val sort: String = "",
    val hideWatched: Boolean = false,
    val hideLibrary: Boolean = false
) {
    fun validationError(): String? {
        val years = listOf(yearFrom, yearTo).filter { it.isNotBlank() }
        if (years.any { !it.matches(Regex("\\d{4}")) || it.toInt() !in 1880..2100 }) return "Use years between 1880 and 2100."
        if (yearFrom.isNotBlank() && yearTo.isNotBlank() && yearFrom.toInt() > yearTo.toInt()) return "Choose a start year before the end year."
        if (language.isNotBlank() && !language.matches(Regex("[a-z]{2}"))) return "Choose a valid two-letter language code."
        if (imdbRating !in listOf("", "6", "7", "8", "9")) return "Choose a valid IMDb rating."
        if (sort !in discoverySorts.map { it.first } + "original_title.asc") return "Choose a valid sort order."
        return null
    }
    fun chips(genres: List<DiscoveryOption> = emptyList(), providers: List<DiscoveryOption> = emptyList()): List<Pair<String, String>> = buildList {
        if (provider.isNotBlank()) add("provider" to (providers.find { it.id == provider }?.name ?: "Selected service"))
        if (genre.isNotBlank()) add("genre" to (genres.find { it.id == genre }?.name ?: genre))
        if (yearFrom.isNotBlank() || yearTo.isNotBlank()) add("year" to "${yearFrom.ifBlank { "…" }}–${yearTo.ifBlank { "…" }}")
        if (imdbRating.isNotBlank()) add("imdbRating" to "IMDb $imdbRating+")
        if (language.isNotBlank()) add("language" to language.uppercase())
        if (certification.isNotBlank()) add("certification" to certification)
        if (sort.isNotBlank()) add("sort" to "Custom order")
        if (hideWatched) add("hideWatched" to "Hide watched")
        if (hideLibrary) add("hideLibrary" to "Hide library titles")
    }
    fun without(field: String): DiscoveryFilters = when (field) {
        "provider" -> copy(provider = "")
        "genre" -> copy(genre = "")
        "year" -> copy(yearFrom = "", yearTo = "")
        "imdbRating" -> copy(imdbRating = "")
        "language" -> copy(language = "")
        "certification" -> copy(certification = "")
        "sort" -> copy(sort = "")
        "hideWatched" -> copy(hideWatched = false)
        "hideLibrary" -> copy(hideLibrary = false)
        else -> this
    }
}

data class DiscoveryState(val type: String = "movie", val category: String = "popular", val region: String = "US", val filters: DiscoveryFilters = DiscoveryFilters())
data class DiscoveryOption(val id: String, val name: String, val logo: String? = null)
val discoveryCategories = listOf("popular" to "Popular", "trending" to "Trending", "top" to "Top rated", "new" to "New releases", "upcoming" to "Upcoming", "hidden" to "Hidden gems")
val discoverySorts = listOf("" to "Recommended", "popularity.desc" to "Popular first", "primary_release_date.desc" to "Newest first", "primary_release_date.asc" to "Oldest first", "vote_average.desc" to "Highest TMDb score")
data class DiscoveryRequest(val path: String, val params: Map<String, String>, val pageFilters: Boolean = false, val empty: Boolean = false)

/** Mirrors catalogRoutes.js; dates are injectable so boundary tests remain deterministic. */
fun discoveryRequest(type: String, category: String, page: Int, filters: DiscoveryFilters, region: String, today: LocalDate = LocalDate.now()): DiscoveryRequest {
    filters.validationError()?.let { throw IllegalArgumentException(it) }
    require(type in listOf("movie", "series")) { "Choose movies or series." }
    require(category in discoveryCategories.map { it.first }) { "Unknown discovery category." }
    require(region.matches(Regex("[A-Z]{2}"))) { "Choose a valid region." }
    val kind = if (type == "series") "tv" else "movie"
    val date = if (kind == "tv") "first_air_date" else "primary_release_date"
    val filtered = listOf(filters.genre, filters.certification, filters.language, filters.yearFrom, filters.yearTo, filters.provider, filters.sort).any { it.isNotBlank() }
    if (category == "trending") return DiscoveryRequest("/trending/$kind/week", mapOf("page" to page.coerceIn(1,500).toString()), pageFilters = filtered)
    if (category == "popular" && !filtered) return DiscoveryRequest("/$kind/popular", mapOf("page" to page.coerceIn(1,500).toString()))
    val params = mutableMapOf("page" to page.coerceIn(1,500).toString(), "include_adult" to "false", "sort_by" to (filters.sort.ifBlank { if (category == "top") "vote_average.desc" else "popularity.desc" }).replace("primary_release_date", date))
    if (kind == "tv" && params["sort_by"] == "original_title.asc") params["sort_by"] = "original_name.asc"
    if (filters.genre.isNotBlank()) params["with_genres"] = filters.genre
    if (filters.language.isNotBlank()) params["with_original_language"] = filters.language
    if (filters.yearFrom.isNotBlank()) params["$date.gte"] = "${filters.yearFrom}-01-01"
    if (filters.yearTo.isNotBlank()) params["$date.lte"] = "${filters.yearTo}-12-31"
    if (filters.provider.isNotBlank()) {
        params["with_watch_providers"] = filters.provider
        params["watch_region"] = region
        params["with_watch_monetization_types"] = "flatrate|free|ads"
    }
    if (filters.certification.isNotBlank() && kind == "movie") { params["certification_country"] = "US"; params["certification"] = filters.certification }
    if (category == "top") { params["vote_count.gte"] = "200"; params["$date.lte"] = minOf(params["$date.lte"] ?: today.toString(), today.toString()) }
    if (category == "hidden") { params["vote_count.gte"] = "100"; params["vote_average.gte"] = "7"; params["popularity.lte"] = "30" }
    if (category in listOf("new", "upcoming")) {
        val from = if (category == "new") today.minusDays(90) else today
        val to = if (category == "new") today else today.plusDays(365)
        params["$date.gte"] = maxOf(params["$date.gte"] ?: from.toString(), from.toString())
        params["$date.lte"] = minOf(params["$date.lte"] ?: to.toString(), to.toString())
        if (filters.sort.isBlank()) params["sort_by"] = "$date.${if (category == "new") "desc" else "asc"}"
    }
    return DiscoveryRequest("/discover/$kind", params, empty = params["$date.gte"] != null && params["$date.lte"] != null && params.getValue("$date.gte") > params.getValue("$date.lte"))
}
