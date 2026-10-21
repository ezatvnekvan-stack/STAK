package app.stak.nativeapp

import androidx.room.withTransaction
import java.time.Instant
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** Capture a server session before paging or mutating; do not mix sessions mid-request. */
data class JellyfinConnection(
    val url: String,
    val token: String,
    val user: String,
    val scope: String,
) {
    val identity
        get() = accountScope("$url|$user")
}

fun Repository.jellyfinConnection(): JellyfinConnection {
    val scope = accountScope
    val address = credentials.get("jellyfin_url").trim().trimEnd('/')
    require(address.isNotBlank()) { "Connect Jellyfin in Settings." }
    val url = address.toHttpUrl()
    val token = credentials.get("jellyfin_token")
    val user = credentials.get("jellyfin_user")
    require(
        url.query == null &&
            url.fragment == null &&
            url.username.isEmpty() &&
            url.password.isEmpty()
    ) {
        "Enter only your Jellyfin server address."
    }
    require(token.isNotBlank() && Regex("[A-Za-z0-9-]+").matches(user)) {
        "Connect Jellyfin in Settings."
    }
    check(accountScope == scope) { "Account changed. Try again." }
    return JellyfinConnection(url.toString().trimEnd('/'), token, user, scope)
}

fun Repository.requireSameConnection(session: JellyfinConnection) {
    check(
        accountScope == session.scope &&
            credentials.get("jellyfin_url").trim().toHttpUrlOrNull()?.toString()?.trimEnd('/') ==
                session.url &&
            credentials.get("jellyfin_user") == session.user &&
            credentials.get("jellyfin_token") == session.token
    ) {
        "Jellyfin connection changed. Try again from the intended account."
    }
}

suspend fun Repository.jellyfinItems(
    session: JellyfinConnection,
    path: String,
    params: Map<String, String>,
    maximum: Int = 100000,
): List<JSONObject> {
    val items = linkedMapOf<String, JSONObject>()
    var start = 0
    while (true) {
        requireSameConnection(session)
        val url = (session.url + path).toHttpUrl().newBuilder()
        params.forEach { (name, value) -> url.addQueryParameter(name, value) }
        val limit = minOf(250, maximum - start)
        check(limit > 0) { "Jellyfin collection exceeds the supported 100,000-item safety limit." }
        url.addQueryParameter("StartIndex", start.toString())
            .addQueryParameter("Limit", limit.toString())
        val root = request(url.build().toString(), session.token, true)
        val page = root.optJSONArray("Items").objects()
        val total = if (root.has("TotalRecordCount")) root.getInt("TotalRecordCount") else null
        if (page.isEmpty()) {
            check(total == null || start >= total) {
                "Jellyfin returned an incomplete library. No sync was saved."
            }
            break
        }
        val before = items.size
        page.forEach { item ->
            val id = item.optString("Id")
            require(Regex("[A-Za-z0-9-]+").matches(id)) { "Jellyfin returned an invalid item ID." }
            items[id] = item
        }
        check(items.size > before) {
            "Jellyfin pagination repeated the same page. No sync was saved."
        }
        start += page.size
        if ((total != null && start >= total) || (total == null && page.size < limit)) break
        check(start < maximum) {
            "Jellyfin collection exceeds the supported 100,000-item safety limit."
        }
    }
    requireSameConnection(session)
    return items.values.toList()
}

fun jellyfinMedia(item: JSONObject, session: JellyfinConnection): Media {
    val id = item.optString("Id")
    val episode = item.optString("Type") == "Episode"
    val posterId = if (episode) item.optString("SeriesId", id) else id
    val ids = item.optJSONObject("ProviderIds")
    val imdb = ids?.optString("Imdb").orEmpty().takeIf { Regex("tt\\d+").matches(it) }.orEmpty()
    val type = if (item.optString("Type") == "Movie") "movie" else "series"
    val data = item.optJSONObject("UserData")
    return Media(
        id = if (episode) "jf:$id" else imdb.ifBlank { "jf:$id" },
        type = type,
        name =
            if (episode)
                "${item.optString("SeriesName")} · S${item.optInt("ParentIndexNumber")}E${item.optInt("IndexNumber")}"
            else item.optString("Name"),
        poster = "${session.url}/Items/$posterId/Images/Primary?maxWidth=500&quality=90",
        background = "${session.url}/Items/$posterId/Images/Backdrop?maxWidth=1280&quality=90",
        year = item.optString("ProductionYear"),
        description = item.optString("Overview"),
        rating = item.optString("CommunityRating"),
        ratingSource = "Jellyfin",
        runtime =
            item.optLong("RunTimeTicks").takeIf { it > 0 }?.let { "${it / 600000000}m" }.orEmpty(),
        genres = item.optJSONArray("Genres").strings(),
        jellyfinId = id,
        progress =
            if (data?.optBoolean("Played") == true) 100f
            else data?.optDouble("PlayedPercentage", 0.0)?.toFloat()?.coerceIn(0f, 100f) ?: 0f,
        playbackTicks = data?.optLong("PlaybackPositionTicks") ?: 0L,
        imdbId = if (episode) "" else imdb,
        tmdbId = if (episode) 0 else ids?.optString("Tmdb")?.toIntOrNull() ?: 0,
        totalEpisodes = item.optInt("RecursiveItemCount"),
        releaseDate = item.optString("PremiereDate"),
    )
}

suspend fun Repository.jellyfinShelf(kind: String): List<Media> {
    val session = jellyfinConnection()
    val params =
        mutableMapOf(
            "Recursive" to "true",
            "IncludeItemTypes" to if (kind == "new_seasons") "Series" else "Movie,Series",
            "Fields" to "Overview,ProviderIds,Genres,DateCreated",
            "SortBy" to "DateCreated",
            "SortOrder" to "Descending",
        )
    if (kind == "available") params["IsPlayed"] = "false"
    // These are intentionally short Home rails; the Library and Sync page through all items.
    val root =
        request(
            (session.url + "/Users/${session.user}/Items")
                .toHttpUrl()
                .newBuilder()
                .apply {
                    params.forEach { (key, value) -> addQueryParameter(key, value) }
                    addQueryParameter("Limit", "20")
                }
                .build()
                .toString(),
            session.token,
            true,
        )
    requireSameConnection(session)
    return root.optJSONArray("Items").objects().map { remember(jellyfinMedia(it, session)) }
}

suspend fun Repository.setJellyfinEpisodesWatched(
    media: Media,
    episodes: List<Episode>,
    watched: Boolean,
) {
    if (media.jellyfinId.isBlank() || episodes.isEmpty()) return
    val session = jellyfinConnection()
    val available =
        jellyfinItems(
            session,
            "/Shows/${media.jellyfinId}/Episodes",
            mapOf("UserId" to session.user),
        )
    val ids =
        episodes
            .map { wanted ->
                available
                    .firstOrNull {
                        it.optInt("ParentIndexNumber") == wanted.season &&
                            wanted.number in
                                it.optInt("IndexNumber")..it.optInt(
                                        "IndexNumberEnd",
                                        it.optInt("IndexNumber"),
                                    )
                    }
                    ?.getString("Id")
                    ?: error(
                        "Episode S${wanted.season}E${wanted.number} is unavailable on Jellyfin. Nothing was changed locally."
                    )
            }
            .distinct()
    var changed = 0
    try {
        for (id in ids) {
            requireSameConnection(session)
            request(
                "${session.url}/Users/${session.user}/PlayedItems/$id",
                session.token,
                true,
                if (watched) JSONObject() else null,
                delete = !watched,
            )
            changed++
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        error(
            "Jellyfin updated $changed of ${ids.size} items. Local history was kept. Sync again to reconcile. ${failure.message.orEmpty()}"
        )
    }
    requireSameConnection(session)
}

/**
 * Explicit, paginated snapshot sync. Manual records outside this server's prior snapshot survive.
 */
suspend fun Repository.syncJellyfin(): String {
    val session = jellyfinConnection()
    val dao = daoFor(session.scope)
    val items =
        jellyfinItems(
            session,
            "/Users/${session.user}/Items",
            mapOf(
                "Recursive" to "true",
                "IncludeItemTypes" to "Movie,Series,Episode",
                "Fields" to "Overview,ProviderIds,Genres,DateCreated",
                "EnableUserData" to "true",
                "SortBy" to "SortName",
            ),
        )
    val episodes =
        items
            .filter {
                it.optString("Type") == "Episode" &&
                    it.optInt("ParentIndexNumber") > 0 &&
                    it.optInt("IndexNumber") > 0
            }
            .groupBy { it.optString("SeriesId") }
    val syncKey = "jellyfin_sync_${session.scope}_${session.identity}"
    val old =
        runCatching { JSONObject(credentials.get(syncKey).ifBlank { "{}" }) }
            .getOrDefault(JSONObject())
    var movieCount = 0
    var episodeCount = 0
    val snapshot = JSONObject()
    requireSameConnection(session)
    db.withTransaction {
        val existing = dao.all().filter { it.collection == "tracking" }.associateBy { it.mediaKey }
        val existingEpisodes =
            dao.allEpisodes().associateBy { "${it.mediaKey}|${it.season}|${it.episode}" }
        val newMarks = linkedMapOf<String, WatchedEpisode>()
        val parents =
            items.filter { it.optString("Type") == "Series" }.associateBy { it.getString("Id") }
        val serverCoordinates =
            episodes
                .flatMap { (_, values) ->
                    values.mapNotNull { episode ->
                        val parent =
                            parents[episode.optString("SeriesId")] ?: return@mapNotNull null
                        val key = jellyfinMedia(parent, session).key
                        "$key|${episode.optInt("ParentIndexNumber")}|${episode.optInt("IndexNumber")}"
                    }
                }
                .toSet()
        for (item in
            items.filter { it.optString("Type") == "Movie" || it.optString("Type") == "Series" }) {
            var media = jellyfinMedia(item, session)
            val userData = item.optJSONObject("UserData")
            val timestamp =
                runCatching { Instant.parse(userData?.optString("LastPlayedDate")).toEpochMilli() }
                    .getOrNull()
            val previous = existing[media.key]
            val serverEpisodes = episodes[item.getString("Id")].orEmpty()
            if (item.optString("Type") == "Series") {
                media = media.copy(totalEpisodes = serverEpisodes.size)
                serverEpisodes
                    .filter { it.optJSONObject("UserData")?.optBoolean("Played") == true }
                    .forEach { episode ->
                        val season = episode.getInt("ParentIndexNumber")
                        val end =
                            episode
                                .optInt("IndexNumberEnd", episode.getInt("IndexNumber"))
                                .coerceAtMost(episode.getInt("IndexNumber") + 20)
                        for (number in episode.getInt("IndexNumber")..end) {
                            val key = "${media.key}|$season|$number"
                            val date =
                                runCatching {
                                        Instant.parse(
                                                episode
                                                    .optJSONObject("UserData")
                                                    ?.optString("LastPlayedDate")
                                            )
                                            .toEpochMilli()
                                    }
                                    .getOrNull() ?: existingEpisodes[key]?.watchedAt ?: 0L
                            val row = WatchedEpisode(media.key, season, number, date)
                            newMarks[key] = row
                            snapshot.put(key, date)
                        }
                    }
                val fromServer = newMarks.values.filter { it.mediaKey == media.key }
                val preserved =
                    existingEpisodes
                        .filter { (key, row) ->
                            row.mediaKey == media.key &&
                                key !in newMarks &&
                                !(key in serverCoordinates &&
                                    old.has(key) &&
                                    old.optLong(key) == row.watchedAt)
                        }
                        .values
                val count = fromServer.size + preserved.size
                if (serverEpisodes.isNotEmpty())
                    media =
                        media.copy(
                            progress =
                                (count.toFloat() / serverEpisodes.size * 100).coerceAtMost(100f)
                        )
                episodeCount += fromServer.size
            } else if (media.progress >= 100) movieCount++
            val manualCompletion =
                previous?.let {
                    val json = JSONObject(it.json)
                    json.optString("sync_source") != session.identity && json.optBoolean("played")
                } == true
            if (manualCompletion && media.progress < 100 && media.type == "movie") {
                remember(media)
                continue
            }
            if (media.progress > 0 || newMarks.values.any { it.mediaKey == media.key }) {
                val latest =
                    (newMarks.values + existingEpisodes.values)
                        .filter { it.mediaKey == media.key }
                        .maxOfOrNull { it.watchedAt }
                dao.put(
                    SavedMedia(
                        media.key,
                        "tracking",
                        media
                            .json()
                            .put("played", media.progress >= 100)
                            .put("sync_source", session.identity)
                            .toString(),
                        timestamp ?: latest ?: previous?.updatedAt ?: 0L,
                    )
                )
            } else if (
                previous?.let {
                    JSONObject(it.json).optString("sync_source") == session.identity
                } == true
            )
                dao.remove(media.key, "tracking")
            remember(media)
        }
        // Remove only unchanged rows previously imported from this same server session identity.
        old.keys().forEach { key ->
            val previous = existingEpisodes[key]
            if (
                key in serverCoordinates &&
                    key !in newMarks &&
                    previous != null &&
                    previous.watchedAt == old.optLong(key)
            )
                dao.removeEpisode(previous.mediaKey, previous.season, previous.episode)
        }
        newMarks.values.forEach { dao.putEpisode(it) }
        requireSameConnection(session)
    }
    requireSameConnection(session)
    credentials.set(syncKey, snapshot.toString())
    return "$movieCount watched movies · $episodeCount watched episodes · ${items.size} server items checked"
}
