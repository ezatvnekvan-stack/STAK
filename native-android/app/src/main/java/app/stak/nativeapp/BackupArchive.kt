package app.stak.nativeapp

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

data class BackupArchive(
    val entries: List<SavedMedia>,
    val episodes: List<WatchedEpisode>,
    val settings: Map<String, Any> = emptyMap(),
    val lists: List<JSONObject> = emptyList(),
    val indexers: List<Indexer> = emptyList(),
    val connections: Map<String, String> = emptyMap(),
) {
    val summary: String
        get() =
            "${entries.count { it.collection == "watchlist" }} watchlist titles · ${entries.count { it.collection == "tracking" }} tracked titles · ${episodes.size} watched episodes\n${settings.size} preferences · ${lists.size} hash lists · ${indexers.size} sources · ${connections.size} connection fields"
}

val backupConnectionNames =
    setOf(
        "tmdb_key",
        "omdb_key",
        "jellyfin_url",
        "jellyfin_token",
        "jellyfin_user",
        "jellyfin_username",
    )

fun backupSettingAllowed(name: String): Boolean =
    name in
        setOf(
            "dark",
            "reduced",
            "haptics",
            "theme",
            "accent",
            "compact",
            "amoled",
            "dotIntensity",
            "glowIntensity",
            "ambientAnimation",
            "cinematicColors",
            "ratings",
            "imdbOnLists",
            "home_order",
            "nav_order",
            "discover_state",
        ) ||
        name.matches(Regex("home_[a-z0-9_:]{1,80}|rating_[A-Za-z ]{1,30}|metadata_[a-z_]{1,30}"))

fun parseBackupArchive(text: String): BackupArchive {
    val entries = BackupCodec.decode(text)
    val root = JSONObject(text)
    val episodes =
        if (root.optString("format") == "stak-native-backup") {
            val array = root.optJSONArray("episodes") ?: JSONArray()
            require(array.length() <= 20000) { "Too many episode entries." }
            array.objects().mapNotNull {
                val key = it.optString("mediaKey")
                val season = it.optInt("season")
                val episode = it.optInt("episode")
                if (
                    Regex("series:(tt\\d+|tmdb:\\d+|jf:[A-Za-z0-9-]+)").matches(key) &&
                        season > 0 &&
                        episode > 0
                )
                    WatchedEpisode(
                        key,
                        season,
                        episode,
                        it.optLong("watchedAt", System.currentTimeMillis()).coerceAtLeast(0),
                    )
                else null
            }
        } else
            root.optJSONArray("tracking").objects().flatMap { title ->
                val media = Media.fromJson(title)
                if (entries.none { it.mediaKey == media.key } || media.type != "series") emptyList()
                else
                    title
                        .optJSONArray("episodes")
                        .objects()
                        .filter {
                            it.optBoolean("played") &&
                                it.optInt("season") > 0 &&
                                it.optInt("episode") > 0
                        }
                        .map {
                            WatchedEpisode(media.key, it.optInt("season"), it.optInt("episode"))
                        }
            }
    require(episodes.size <= 20000) { "Too many episode entries." }
    val settings =
        root
            .optJSONObject("settings")
            ?.let { source ->
                require(source.length() <= 200) { "Too many preferences." }
                source.keys().asSequence().filter(::backupSettingAllowed).associateWith { name ->
                    val value = source.get(name)
                    require(
                        if (name in setOf("dark", "reduced", "haptics")) value is Boolean
                        else value is String && value.length <= 4096
                    ) {
                        "Invalid preference."
                    }
                    value
                }
            }
            .orEmpty()
    val lists =
        root
            .optJSONArray("hash_lists")
            ?.let { array ->
                require(array.length() <= 1000) { "Too many hash lists." }
                array.objects().map { list ->
                    val id = list.getString("id")
                    require(id.length in 1..200 && list.optString("title").length <= 160) {
                        "Invalid hash list."
                    }
                    val items = list.getJSONArray("items")
                    require(items.length() <= 1000) { "Too many hashes in a list." }
                    val safe =
                        items.objects().map {
                            val hash = normalizeTorrentHash(it.optString("hash"))
                            require(hash.isNotBlank()) { "Invalid torrent hash in backup." }
                            JSONObject()
                                .put("hash", hash)
                                .put("name", it.optString("name").take(500))
                                .put("size", it.optLong("size").coerceAtLeast(0))
                        }
                    JSONObject()
                        .put("id", id)
                        .put("title", list.optString("title"))
                        .put("items", JSONArray(safe))
                        .put("created_at", list.optString("created_at").take(80))
                        .put("local_only", true)
                }
            }
            .orEmpty()
    val indexers =
        root
            .optJSONArray("indexers")
            ?.let { array ->
                require(array.length() <= 5000) { "Too many sources." }
                array.objects().map {
                    validateIndexer(
                        Indexer(
                            it.getString("id"),
                            it.optString("name"),
                            it.getString("kind"),
                            it.getString("url"),
                            it.optString("api_key"),
                            it.optBoolean("enabled", true),
                        )
                    )
                }
            }
            .orEmpty()
    val connections =
        root
            .optJSONObject("connections")
            ?.let { source ->
                backupConnectionNames
                    .filter { source.has(it) }
                    .associateWith {
                        source.getString(it).also { value ->
                            require(value.length <= 8192) { "Invalid connection." }
                        }
                    }
            }
            .orEmpty()
    return BackupArchive(
        entries,
        episodes.distinctBy { Triple(it.mediaKey, it.season, it.episode) },
        settings,
        lists,
        indexers,
        connections,
    )
}

suspend fun Repository.createBackupArchive(includeConnections: Boolean): String {
    val dao = dao // capture one account for the whole export
    val root = db.withTransaction { JSONObject(BackupCodec.encode(dao.all(), dao.allEpisodes())) }
    val settings = JSONObject()
    preferences.all.filterKeys(::backupSettingAllowed).forEach { (name, value) ->
        settings.put(name, value)
    }
    root
        .put("version", 2)
        .put("settings", settings)
        .put("hash_lists", JSONArray(hashLists()))
        .put("indexers", JSONArray(IndexerStore(this).read().map { it.json() }))
    if (includeConnections)
        root.put(
            "connections",
            JSONObject().also { target ->
                backupConnectionNames.forEach { name ->
                    credentials.get(name).takeIf { it.isNotBlank() }?.let { target.put(name, it) }
                }
            },
        )
    check(accountScope == dao.scope) { "Account changed. Export again from the intended account." }
    return root.toString(2).also {
        require(it.toByteArray().size <= EncryptedBackup.MAX_PAYLOAD_BYTES) {
            "Backup exceeds 2 MB."
        }
    }
}
