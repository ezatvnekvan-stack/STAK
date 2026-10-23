package app.stak.nativeapp

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.*

private fun imagePath(path: String, size: String = "w500") =
    if (path.isBlank() || path == "null") "" else "https://image.tmdb.org/t/p/$size$path"

suspend fun Repository.tmdb(
    path: String,
    params: Map<String, String> = emptyMap(),
    suppliedKey: String = credentials.get("tmdb_key"),
    cache: Boolean = true,
): JSONObject {
    require(suppliedKey.isNotBlank()) { "Configure TMDb in Metadata & ratings." }
    val url =
        "https://api.themoviedb.org/3$path"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("language", "en-US")
    params.filterValues { it.isNotBlank() }.forEach { (k, v) -> url.addQueryParameter(k, v) }
    val bearer = if (suppliedKey.contains('.')) suppliedKey else ""
    if (bearer.isBlank()) url.addQueryParameter("api_key", suppliedKey)
    if (!cache) return request(url.build().toString(), bearer)
    return publicMetadata("tmdb:$path:${params.toSortedMap()}") {
        request(url.build().toString(), bearer)
    }
}

fun tmdbMedia(item: JSONObject, type: String) =
    Media(
        id = "tmdb:${item.optInt("id")}",
        type = type,
        name = item.optString("title", item.optString("name")),
        aliases = mediaAliases(item),
        releaseDate = item.optString("release_date", item.optString("first_air_date")),
        poster = imagePath(item.optString("poster_path")),
        background = imagePath(item.optString("backdrop_path"), "w1280"),
        year = item.optString("release_date", item.optString("first_air_date")).take(4),
        description = item.optString("overview"),
        rating =
            item
                .optDouble("vote_average")
                .takeIf { it.isFinite() && it > 0 }
                ?.let { "%.1f".format(java.util.Locale.US, it).removeSuffix(".0") }
                .orEmpty(),
        tmdbId = item.optInt("id"),
        ratingSource = "TMDB",
        genres = item.optJSONArray("genres").objects().map { it.optString("name") },
    )

suspend fun Repository.browse(
    type: String,
    category: String = "popular",
    query: String = "",
    page: Int = 1,
    filters: Map<String, String> = emptyMap(),
): List<Media> = discoveryPage(type, category, query, page, filters).items

suspend fun Repository.tmdbDetails(media: Media, loadEpisodes: Boolean = true): Media =
    coroutineScope {
        val kind = if (media.type == "movie") "movie" else "tv"
        val id =
            media.tmdbId.takeIf { it > 0 }
                ?: media.id.removePrefix("tmdb:").toIntOrNull()
                ?: run {
                    val found = tmdb("/find/${media.id}", mapOf("external_source" to "imdb_id"))
                    found
                        .optJSONArray(if (kind == "tv") "tv_results" else "movie_results")
                        ?.optJSONObject(0)
                        ?.optInt("id") ?: return@coroutineScope media
                }
        val root =
            tmdb(
                "/$kind/$id",
                mapOf(
                    "append_to_response" to
                        "external_ids,images,credits,videos,release_dates,content_ratings",
                    "include_image_language" to "en,null",
                ),
            )
        val episodes =
            if (kind == "tv" && loadEpisodes) {
                val gate = Semaphore(3)
                root
                    .optJSONArray("seasons")
                    .objects()
                    .filter { it.optInt("season_number") > 0 }
                    .map { season ->
                        async {
                            gate.withPermit {
                                val s = season.optInt("season_number")
                                tmdb("/tv/$id/season/$s").optJSONArray("episodes").objects().map { e
                                    ->
                                    Episode(
                                        "${media.id}:$s:${e.optInt("episode_number")}",
                                        s,
                                        e.optInt("episode_number"),
                                        e.optString("name"),
                                        e.optString("air_date")
                                            .takeIf { it.isNotBlank() }
                                            ?.plus("T00:00:00Z")
                                            .orEmpty(),
                                        imagePath(e.optString("still_path")),
                                        e.optString("overview"),
                                    )
                                }
                            }
                        }
                    }
                    .awaitAll()
                    .flatten()
            } else emptyList()
        fun credit(j: JSONObject, field: String) =
            PersonCredit(
                j.optInt("id"),
                j.optString("name"),
                j.optString(field),
                imagePath(j.optString("profile_path"), "w185"),
            )
        val creators =
            (if (kind == "tv") root.optJSONArray("created_by").objects() else emptyList()).map {
                credit(it, "job").copy(role = "Creator")
            }
        val cert =
            if (kind == "tv")
                root
                    .optJSONObject("content_ratings")
                    ?.optJSONArray("results")
                    .objects()
                    .firstOrNull { it.optString("iso_3166_1") == "US" }
                    ?.optString("rating")
                    .orEmpty()
            else
                root
                    .optJSONObject("release_dates")
                    ?.optJSONArray("results")
                    .objects()
                    .firstOrNull { it.optString("iso_3166_1") == "US" }
                    ?.optJSONArray("release_dates")
                    .objects()
                    .firstOrNull { it.optString("certification").isNotBlank() }
                    ?.optString("certification")
                    .orEmpty()
        val raw = tmdbMedia(root, media.type)
        remember(
            raw.copy(
                id = media.id,
                imdbId =
                    root.optString(
                        "imdb_id",
                        root.optJSONObject("external_ids")?.optString("imdb_id").orEmpty(),
                    ),
                episodes = episodes,
                jellyfinId = media.jellyfinId,
                progress = media.progress,
                playbackTicks = media.playbackTicks,
                runtime = root.optInt("runtime").takeIf { it > 0 }?.let { "${it}m" }.orEmpty(),
                certificate = cert,
                logo =
                    imagePath(
                        root
                            .optJSONObject("images")
                            ?.optJSONArray("logos")
                            .objects()
                            .firstOrNull { it.optString("iso_639_1") == "en" }
                            ?.optString("file_path")
                            .orEmpty()
                    ),
                trailer =
                    root
                        .optJSONObject("videos")
                        ?.optJSONArray("results")
                        .objects()
                        .firstOrNull {
                            it.optString("site") == "YouTube" && it.optString("type") == "Trailer"
                        }
                        ?.optString("key")
                        .orEmpty(),
                cast =
                    root.optJSONObject("credits")?.optJSONArray("cast").objects().take(20).map {
                        credit(it, "character")
                    },
                crew =
                    (creators +
                            root
                                .optJSONObject("credits")
                                ?.optJSONArray("crew")
                                .objects()
                                .filter {
                                    it.optString("job") in
                                        listOf("Director", "Writer", "Screenplay")
                                }
                                .map { credit(it, "job") })
                        .distinctBy { it.id to it.role },
                gallery =
                    root.optJSONObject("images")?.optJSONArray("backdrops").objects().take(12).map {
                        imagePath(it.optString("file_path"), "w780")
                    },
            )
        )
    }

data class SourceStream(
    val name: String,
    val title: String,
    val hash: String,
    val fileIndex: Int?,
    val source: String = name,
    val size: Long = 0,
    val seeders: Int? = null,
    val magnet: String = "",
)

suspend fun Repository.addMagnet(magnet: String) {
    require(magnet.startsWith("magnet:?xt=urn:btih:") && magnet.length <= 8192) {
        "Enter a valid magnet link."
    }
    val token = credentials.get("torbox_token")
    require(token.isNotBlank()) { "Connect TorBox in Settings." }
    val result =
        request(
            "https://api.torbox.app/v1/api/torrents/createtorrent",
            token,
            form = mapOf("magnet" to magnet, "seed" to "1", "allow_zip" to "false"),
        )
    check(result.optBoolean("success")) {
        result.optString("detail", "TorBox could not add this magnet.")
    }
}

suspend fun Repository.controlTorrent(id: Int, operation: String) {
    require(id >= 0 && operation in listOf("pause", "resume", "delete", "reannounce"))
    val token = credentials.get("torbox_token")
    require(token.isNotBlank()) { "Connect TorBox in Settings." }
    val result =
        request(
            "https://api.torbox.app/v1/api/torrents/controltorrent",
            token,
            body = JSONObject().put("torrent_id", id).put("operation", operation),
        )
    check(result.optBoolean("success")) {
        result.optString("detail", "TorBox could not complete this action.")
    }
}

suspend fun Repository.publicMetadata(
    key: String,
    maxAge: Long = 86_400_000L,
    load: suspend () -> JSONObject,
): JSONObject =
    withContext(Dispatchers.IO) {
        val hash =
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(key.toByteArray())
                .joinToString("") { "%02x".format(it) }
        val file = java.io.File(metadataDirectory, "$hash.json")
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < maxAge) {
            runCatching { JSONObject(file.readText()) }
                .getOrNull()
                ?.let {
                    return@withContext it
                }
        }
        val result = load()
        val temporary = java.io.File.createTempFile("metadata-", ".tmp", metadataDirectory)
        try {
            temporary.writeText(result.toString())
            if (!temporary.renameTo(file)) temporary.delete()
        } finally {
            temporary.delete()
        }
        result
    }

data class JellyfinUser(val id: String, val name: String)

data class JellyfinLogin(val token: String, val users: List<JellyfinUser>, val serverName: String, val serverUrl: String = "")

suspend fun Repository.testJellyfin(
    address: String,
    username: String,
    password: String,
    apiKey: String,
    passwordMode: Boolean,
): JellyfinLogin {
    val url = normalizeJellyfinAddress(address)
    if (passwordMode) {
        val result =
            request(
                "$url/Users/AuthenticateByName",
                jellyfin = true,
                body = JSONObject().put("Username", username.trim()).put("Pw", password),
            )
        val token = result.optString("AccessToken")
        require(token.isNotBlank()) { "Jellyfin did not return a session." }
        val user = result.optJSONObject("User") ?: error("Jellyfin user unavailable.")
        return JellyfinLogin(
            token,
            listOf(JellyfinUser(user.optString("Id"), user.optString("Name"))),
            result.optString("ServerId", "Jellyfin"),
            url,
        )
    }
    val key = apiKey.trim()
    require(key.isNotBlank()) { "Enter your Jellyfin API key." }
    val info = request("$url/System/Info", key, jellyfin = true)
    val users =
        request("$url/Users", key, jellyfin = true).optJSONArray("items").objects().map {
            JellyfinUser(it.optString("Id"), it.optString("Name"))
        }
    require(users.isNotEmpty()) { "This key cannot list Jellyfin users." }
    return JellyfinLogin(key, users, info.optString("ServerName", "Jellyfin"), url)
}
