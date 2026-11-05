package app.stak.nativeapp.data

import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException

internal class DirectTitleMetadata(
    private val key: () -> String,
    private val preferCinemeta: (String) -> Boolean,
    private val request: (String, Map<String,String>, String?) -> JsonObject
) {
    private fun tmdb(path: String, params: Map<String,String> = emptyMap()): JsonObject {
        val secret = key()
        return request("https://api.themoviedb.org/3$path", params + mapOf("language" to "en-US") + if (secret.contains('.')) emptyMap() else mapOf("api_key" to secret), secret.takeIf { it.contains('.') })
    }
    suspend fun load(type: String, id: String, firstSeasonOnly: Boolean = false): TitleDetails = withContext(Dispatchers.IO) {
        val kind = if (type == "series") "series" else "movie"
        var fallbackNotice: String? = null
        if (preferCinemeta(kind) && id.matches(Regex("tt\\d+"))) {
            try { return@withContext cinema(kind, id, null) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fallbackNotice = "Cinemeta metadata unavailable. Using TMDb." }
        }
        if (key().isNotBlank()) {
            val apiKind = if (kind == "series") "tv" else "movie"
            val tmdbId = if (id.matches(Regex("tmdb:\\d+"))) id.substringAfter(':') else if (id.matches(Regex("tt\\d+"))) {
                try { tmdb("/find/$id", mapOf("external_source" to "imdb_id")).objects(if (kind == "series") "tv_results" else "movie_results").firstOrNull()?.text("id") }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { fallbackNotice = "TMDb identity resolution failed. Showing Cinemeta metadata."; null }
            } else null
            if (tmdbId != null) {
                val data = tmdb("/$apiKind/$tmdbId", mapOf("append_to_response" to "external_ids,videos,images,credits,release_dates,content_ratings,similar,recommendations${if (kind == "series") ",aggregate_credits" else ""}", "include_image_language" to "en,null"))
                val mapped = DirectCatalog.tmdbItem(data, kind)
                val item = mapped.copy(runtime = mapped.runtime ?: data.getAsJsonArray("episode_run_time")?.firstOrNull()?.asInt?.let { "$it min" })
                if (preferCinemeta(kind) && item.imdbId != null && !id.matches(Regex("tt\\d+"))) {
                    try { return@withContext cinema(kind, item.imdbId, null) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { fallbackNotice = "Cinemeta metadata unavailable. Using TMDb." }
                }
                val notices = java.util.Collections.synchronizedList(mutableListOf<String>())
                fallbackNotice?.let { notices += it }
                val episodes = if (kind == "series") coroutineScope {
                    val permits = Semaphore(4)
                    data.objects("seasons").mapNotNull { it.number("season_number")?.toInt()?.takeIf { n -> n > 0 } }.distinct().sorted().let { if(firstSeasonOnly) emptyList() else it }.map { season -> async {
                        permits.withPermit {
                            try { tmdb("/tv/$tmdbId/season/$season").objects("episodes").mapNotNull { episode ->
                                episode.number("episode_number")?.toInt()?.takeIf { it > 0 }?.let { n -> Episode(season, n, episode.text("name") ?: "Episode $n", episode.text("overview"), image(episode.text("still_path"), "w300"), episode.number("runtime")?.toInt()?.let { "$it min" }, released = episode.text("air_date")) }
                            } }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { notices += "Season $season could not load. Retry metadata."; emptyList() }
                        }
                    } }.awaitAll().flatten().distinctBy { it.season to it.episode }.sortedWith(compareBy({ it.season }, { it.episode }))
                } else emptyList()
                val creators = data.objects("created_by").map { it.deepCopy().apply { addProperty("job", "Creator") } }
                val aggregate = data.objectOrNull("aggregate_credits")?.objects("crew").orEmpty().flatMap { person -> person.objects("jobs").filter { it.text("job") in setOf("Writer", "Screenplay", "Story", "Teleplay", "Showrunner") }.map { job -> person.deepCopy().apply { addProperty("job", job.text("job")) } } }
                val crewRows = creators + aggregate + data.objectOrNull("credits")?.objects("crew").orEmpty().filter { it.text("job") in setOf("Director", "Creator", "Showrunner", "Writer", "Screenplay", "Story", "Teleplay") && (kind == "movie" || it.text("job") != "Director") }
                val crew = crewRows.groupBy { it.text("id") }.mapNotNull { (personId, rows) -> personId?.let { TitlePerson(it, rows.first().text("name").orEmpty(), rows.mapNotNull { r -> r.text("job") }.distinct().joinToString(" · "), image(rows.first().text("profile_path"), "w185")) } }.sortedBy { if (it.role.split(" · ").any { role -> role in setOf("Director", "Creator", "Showrunner") }) 0 else 1 }
                val cast = data.objectOrNull("credits")?.objects("cast").orEmpty().take(18).mapNotNull { p -> p.text("id")?.let { TitlePerson(it, p.text("name").orEmpty(), p.text("character").orEmpty(), image(p.text("profile_path"), "w185")) } }
                val images = data.objectOrNull("images")
                val gallery = images?.objects("backdrops").orEmpty().take(12).mapNotNull { it.text("file_path")?.let { path -> "https://image.tmdb.org/t/p/w780$path" } }.distinct()
                val logos = images?.objects("logos").orEmpty().filter { it.text("iso_639_1") in listOf(null, "en") }
                val logo = image(logos.firstOrNull { it.text("iso_639_1") == "en" }?.text("file_path") ?: logos.firstOrNull()?.text("file_path"))
                val trailer = data.objectOrNull("videos")?.objects("results").orEmpty().sortedByDescending { it.get("official")?.asBoolean == true }.firstOrNull { it.text("site") == "YouTube" && it.text("type") == "Trailer" && it.text("key")?.matches(Regex("[A-Za-z0-9_-]{6,32}")) == true }?.text("key")
                val us = data.objectOrNull(if (kind == "series") "content_ratings" else "release_dates")?.objects("results")?.firstOrNull { it.text("iso_3166_1") == "US" }
                val certification = if (kind == "series") us?.text("rating") else us?.objects("release_dates")?.firstNotNullOfOrNull { it.text("certification")?.takeIf { value -> value.isNotBlank() } }
                val collectionId = data.objectOrNull("belongs_to_collection")?.text("id")
                val collection = if (collectionId != null && kind == "movie") try { tmdb("/collection/$collectionId").objects("parts").sortedBy { it.text("release_date").orEmpty() }.map { DirectCatalog.tmdbItem(it, "movie") } } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { notices += "Collection could not load. Retry metadata."; emptyList() } else emptyList()
                return@withContext TitleDetails(item, item.genres, certification = certification, tmdbRating = item.tmdbRating, episodes = episodes, seasons = (data.objects("seasons").mapNotNull { it.number("season_number")?.toInt()?.takeIf { n -> n > 0 } } + episodes.map { it.season }).distinct().sorted(), notice = notices.takeIf { it.isNotEmpty() }?.joinToString(" "), trailerUrl = trailer?.let { "https://www.youtube.com/watch?v=$it" }, gallery = gallery, cast = cast, crew = crew, collection = collection, related = (data.objectOrNull("recommendations")?.objects("results").orEmpty() + data.objectOrNull("similar")?.objects("results").orEmpty()).distinctBy { it.text("id") }.take(18).map { DirectCatalog.tmdbItem(it, kind) }, logo = logo, status = data.text("status"))
            }
        }
        require(id.matches(Regex("tt\\d+"))) { "Configure TMDb to load metadata for this title." }
        cinema(kind, id, fallbackNotice ?: "TMDb key or matching identity missing. Showing Cinemeta metadata.")
    }
    private fun cinema(type: String, id: String, notice: String?): TitleDetails {
        val data = request("https://v3-cinemeta.strem.io/meta/$type/$id.json", emptyMap(), null)
        val meta = data.get("meta")?.takeIf { it.isJsonObject }?.asJsonObject ?: throw IOException("Title not found in Cinemeta.")
        val item = DirectCatalog.cinemetaItem(meta, type)
        val episodes = meta.objects("videos").mapNotNull { ep ->
            val season = ep.number("season")?.toInt()?.takeIf { it > 0 } ?: return@mapNotNull null
            val number = ep.number("episode")?.toInt()?.takeIf { it > 0 } ?: return@mapNotNull null
            Episode(season, number, ep.text("name") ?: ep.text("title") ?: "Episode $number", ep.text("overview") ?: ep.text("description"), ep.text("thumbnail"), released = ep.text("released"))
        }.distinctBy { it.season to it.episode }.sortedWith(compareBy({ it.season }, { it.episode }))
        val trailer = meta.objects("trailers").firstNotNullOfOrNull { it.text("source")?.takeIf { s -> s.matches(Regex("[A-Za-z0-9_-]{6,32}")) && it.text("type") == "Trailer" } }
        return TitleDetails(item, item.genres, episodes = episodes, seasons = episodes.map { it.season }.distinct(), notice = notice, trailerUrl = trailer?.let { "https://www.youtube.com/watch?v=$it" }, logo = meta.text("logo"))
    }
    suspend fun season(id: String, season: Int): List<Episode> = withContext(Dispatchers.IO) {
        require(season >= 0)
        val tmdbId = if(id.startsWith("tmdb:")) id.substringAfter(':') else
            tmdb("/find/$id", mapOf("external_source" to "imdb_id")).objects("tv_results").firstOrNull()?.text("id")
        require(tmdbId?.matches(Regex("[0-9]+")) == true)
        tmdb("/tv/$tmdbId/season/$season").objects("episodes").mapNotNull { ep ->
            ep.number("episode_number")?.toInt()?.takeIf { it > 0 }?.let { n ->
                Episode(season,n,ep.text("name") ?: "Episode $n",ep.text("overview"),image(ep.text("still_path"),"w300"),ep.number("runtime")?.toInt()?.let { "$it min" },released=ep.text("air_date"))
            }
        }
    }
    private fun image(path: String?, size: String = "w500"): String? = path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/$size$it" }
}
internal fun JsonObject.objects(name: String): List<JsonObject> = get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject }.orEmpty()
internal fun JsonObject.objectOrNull(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject


