package app.stak.nativeapp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.*
import java.security.KeyStore
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class Media(
    val id: String,
    val type: String,
    val name: String,
    val poster: String = "",
    val background: String = "",
    val year: String = "",
    val description: String = "",
    val rating: String = "",
    val runtime: String = "",
    val genres: List<String> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val jellyfinId: String = "",
    val progress: Float = 0f,
    val playbackTicks: Long = 0,
    val tmdbId: Int = 0,
    val imdbId: String = "",
    val logo: String = "",
    val trailer: String = "",
    val cast: List<PersonCredit> = emptyList(),
    val crew: List<PersonCredit> = emptyList(),
    val gallery: List<String> = emptyList(),
    val certificate: String = "",
    val ratingSource: String = "IMDb",
    val totalEpisodes: Int = 0,
    val releaseDate: String = "",
    val aliases: List<String> = emptyList(),
) {
    val key
        get() = "$type:$id"

    fun enrichedBy(previous: Media?): Media {
        if (previous == null || previous.key != key) return this
        return copy(
            poster = poster.ifBlank { previous.poster },
            background = background.ifBlank { previous.background },
            year = year.ifBlank { previous.year },
            description = description.ifBlank { previous.description },
            rating = rating.ifBlank { previous.rating },
            runtime = runtime.ifBlank { previous.runtime },
            genres = genres.ifEmpty { previous.genres },
            episodes = episodes.ifEmpty { previous.episodes },
            tmdbId = tmdbId.takeIf { it > 0 } ?: previous.tmdbId,
            imdbId = imdbId.ifBlank { previous.imdbId },
            logo = logo.ifBlank { previous.logo },
            trailer = trailer.ifBlank { previous.trailer },
            cast = cast.ifEmpty { previous.cast },
            crew = crew.ifEmpty { previous.crew },
            gallery = gallery.ifEmpty { previous.gallery },
            certificate = certificate.ifBlank { previous.certificate },
            totalEpisodes = totalEpisodes.takeIf { it > 0 } ?: previous.totalEpisodes,
            releaseDate = releaseDate.ifBlank { previous.releaseDate },
            aliases = (aliases + previous.aliases).distinct(),
        )
    }

    fun json() =
        JSONObject()
            .put("imdb_id", id)
            .put("type", type)
            .put("name", name)
            .put("poster", poster)
            .put("background", background)
            .put("year", year)
            .put("description", description)
            .put("rating", rating)
            .put("runtime", runtime)
            .put("genres", JSONArray(genres))
            .put("jellyfinId", jellyfinId)
            .put("progress", progress)
            .put("playbackTicks", playbackTicks)
            .put("tmdbId", tmdbId)
            .put("imdbId", imdbId)
            .put("released", releaseDate)
            .put("aliases", JSONArray(aliases))
            .put("ratingSource", ratingSource)
            .put("total_episodes", totalEpisodes.takeIf { it > 0 } ?: episodes.size)

    companion object {
        fun fromJson(j: JSONObject) =
            Media(
                id = j.optString("imdb_id", j.optString("id")),
                type = if (j.optString("type") == "movie") "movie" else "series",
                name = j.optString("name", j.optString("title")),
                aliases = mediaAliases(j),
                poster = j.optString("poster"),
                background = j.optString("background"),
                year = j.optString("year", j.optString("releaseInfo")),
                description = j.optString("description"),
                rating = j.optString("rating", j.optString("imdbRating")),
                runtime = j.optString("runtime"),
                genres = j.optJSONArray("genres").strings(),
                jellyfinId = j.optString("jellyfinId"),
                progress = j.optDouble("progress", 0.0).toFloat().coerceIn(0f, 100f),
                playbackTicks = j.optLong("playbackTicks"),
                tmdbId = j.optInt("tmdbId"),
                imdbId = j.optString("imdbId"),
                ratingSource = j.optString("ratingSource", "IMDb"),
                totalEpisodes = j.optInt("total_episodes"),
                releaseDate = j.optString("released", j.optString("release_date")),
            )
    }
}

data class PersonCredit(val id: Int, val name: String, val role: String, val image: String)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val name: String,
    val released: String,
    val image: String,
    val overview: String,
)

data class Torrent(
    val id: Int,
    val name: String,
    val progress: Float,
    val state: String,
    val files: List<TorrentFile>,
    val hash: String = "",
    val size: Long = 0,
)

data class TorrentFile(val id: Int, val name: String, val size: Long)

fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }

@Entity(tableName = "saved_media", primaryKeys = ["accountScope", "mediaKey", "collection"])
data class SavedMedia(
    val mediaKey: String,
    val collection: String,
    val json: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val accountScope: String = "local",
) {
    fun media() = Media.fromJson(JSONObject(json))
}

@Entity(
    tableName = "watched_episodes",
    primaryKeys = ["accountScope", "mediaKey", "season", "episode"],
)
data class WatchedEpisode(
    val mediaKey: String,
    val season: Int,
    val episode: Int,
    val watchedAt: Long = System.currentTimeMillis(),
    val accountScope: String = "local",
)

@Dao
interface StakDao {
    @Query("SELECT * FROM saved_media WHERE accountScope = :scope ORDER BY updatedAt DESC")
    fun observe(scope: String): Flow<List<SavedMedia>>

    @Query("SELECT * FROM watched_episodes WHERE accountScope = :scope")
    fun episodes(scope: String): Flow<List<WatchedEpisode>>

    @Query("SELECT * FROM saved_media WHERE accountScope = :scope ORDER BY updatedAt DESC")
    suspend fun all(scope: String): List<SavedMedia>

    @Query("SELECT * FROM watched_episodes WHERE accountScope = :scope")
    suspend fun allEpisodes(scope: String): List<WatchedEpisode>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(item: SavedMedia)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun merge(items: List<SavedMedia>)

    @Query(
        "DELETE FROM saved_media WHERE accountScope = :scope AND mediaKey = :key AND collection = :collection"
    )
    suspend fun remove(scope: String, key: String, collection: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putEpisode(item: WatchedEpisode)

    @Query(
        "DELETE FROM watched_episodes WHERE accountScope = :scope AND mediaKey = :key AND season = :season AND episode = :episode"
    )
    suspend fun removeEpisode(scope: String, key: String, season: Int, episode: Int)
}

@Database(entities = [SavedMedia::class, WatchedEpisode::class], version = 2, exportSchema = true)
abstract class StakDatabase : RoomDatabase() {
    abstract fun dao(): StakDao
}

/** Keystore-backed storage for provider credentials and account-scoped indexer configuration. */
class Credentials(context: Context) {
    private val prefs = context.getSharedPreferences("stak_secure", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("stak_native_v1", null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            "stak_native_v1",
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }

    private val scopedNames =
        setOf("jellyfin_url", "jellyfin_token", "jellyfin_user", "jellyfin_username")

    init {
        // The old global Jellyfin session belongs only to the account selected at upgrade.
        synchronized(Credentials::class.java) {
            if (!prefs.getBoolean("jellyfin_scoped_v1", false)) {
                val scope = accountScope(get("torbox_token"))
                val edit = prefs.edit()
                scopedNames.forEach { name ->
                    prefs.getString(name, null)?.let { edit.putString("${name}_$scope", it) }
                    edit.remove(name)
                }
                check(edit.putBoolean("jellyfin_scoped_v1", true).commit()) {
                    "Could not preserve Jellyfin connection."
                }
            }
        }
    }

    val scope: String
        get() = accountScope(get("torbox_token"))

    fun scopes(): Flow<String> =
        kotlinx.coroutines.flow
            .callbackFlow {
                val listener =
                    android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, name ->
                        if (name == "torbox_token") trySend(scope)
                    }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                trySend(scope)
                awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            .distinctUntilChanged()

    private fun storageName(name: String): String =
        if (name in scopedNames) "${name}_$scope" else name

    fun get(name: String): String {
        val value = prefs.getString(storageName(name), null) ?: return ""
        return runCatching {
                val bytes = Base64.decode(value, Base64.NO_WRAP)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key(),
                    GCMParameterSpec(128, bytes.copyOfRange(0, 12)),
                )
                String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
            }
            .getOrDefault("")
    }

    fun set(name: String, value: String) {
        if (value.isBlank()) {
            check(prefs.edit().remove(storageName(name)).commit()) { "Could not save credentials." }
            return
        }
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val saved =
            prefs
                .edit()
                .putString(
                    storageName(name),
                    Base64.encodeToString(
                        cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)),
                        Base64.NO_WRAP,
                    ),
                )
                .commit()
        check(saved) { "Could not save credentials." }
    }
}

object BackupCodec {
    fun decode(text: String): List<SavedMedia> {
        require(text.toByteArray().size <= 2 * 1024 * 1024) { "Backup exceeds 2 MB." }
        val root = JSONObject(text)
        require(
            root.optString("format") in listOf("dmm-backup", "stak-native-backup") &&
                (root.optInt("version") == 1 ||
                    (root.optString("format") == "stak-native-backup" &&
                        root.optInt("version") == 2))
        ) {
            "Unsupported backup format."
        }
        return listOf("watchlist", "tracking")
            .flatMap { collection ->
                val items = root.optJSONArray(collection) ?: error("Missing $collection list.")
                require(items.length() <= 5000) { "Too many backup entries." }
                items.objects().mapNotNull { item ->
                    val media = Media.fromJson(item)
                    if (
                        !Regex("^(tt\\d+|tmdb:\\d+|jf:[A-Za-z0-9-]+)$").matches(media.id) ||
                            media.name.isBlank()
                    )
                        null
                    else
                        SavedMedia(
                            media.key,
                            collection,
                            media
                                .json()
                                .put("played", item.optBoolean("played", media.progress >= 100f))
                                .toString(),
                            runCatching {
                                    Instant.parse(
                                            item.optString("updated_at", item.optString("added_at"))
                                        )
                                        .toEpochMilli()
                                }
                                .getOrDefault(System.currentTimeMillis()),
                        )
                }
            }
            .distinctBy { it.mediaKey to it.collection }
    }

    fun encode(items: List<SavedMedia>, episodes: List<WatchedEpisode>): String {
        val root =
            JSONObject()
                .put("format", "stak-native-backup")
                .put("version", 1)
                .put("exported_at", Instant.now().toString())
        for (collection in listOf("watchlist", "tracking")) root.put(
            collection,
            JSONArray(
                items
                    .filter { it.collection == collection }
                    .map {
                        JSONObject(it.json)
                            .put("updated_at", Instant.ofEpochMilli(it.updatedAt).toString())
                    }
            ),
        )
        root.put(
            "episodes",
            JSONArray(
                episodes.map {
                    JSONObject()
                        .put("mediaKey", it.mediaKey)
                        .put("season", it.season)
                        .put("episode", it.episode)
                        .put("watchedAt", it.watchedAt)
                }
            ),
        )
        return root.toString(2)
    }
}

class Repository(
    private val context: Context,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build(),
    val clock: java.time.Clock = java.time.Clock.systemDefaultZone(),
) {
    val today: java.time.LocalDate
        get() = java.time.LocalDate.now(clock)

    val credentials = Credentials(context)
    val db = openStakDatabase(context, credentials.scope)
    val accountScope: String
        get() = credentials.scope

    fun daoFor(scope: String) = ScopedStakDao(db.dao(), scope)

    val dao: ScopedStakDao
        get() = daoFor(accountScope)

    val metadataDirectory = java.io.File(context.cacheDir, "public-metadata").apply { mkdirs() }
    val preferences = context.getSharedPreferences("stak_appearance", 0)
    private val metadataCache = java.util.concurrent.ConcurrentHashMap<String, Media>()
    val metadataReads = ReadLocks()

    fun remember(media: Media): Media =
        metadataCache.compute(media.key) { _, previous -> media.enrichedBy(previous) }!!

    fun cached(key: String) = metadataCache[key]

    fun forgetPrivateMetadata() {
        metadataCache.entries.removeIf { it.value.jellyfinId.isNotBlank() }
    }

    suspend fun request(
        url: String,
        token: String = "",
        jellyfin: Boolean = false,
        body: JSONObject? = null,
        delete: Boolean = false,
        form: Map<String, String>? = null,
    ): JSONObject =
        withContext(Dispatchers.IO) {
            val parsed = url.toHttpUrl()
            require(parsed.scheme == "https" || (jellyfin && parsed.scheme == "http")) {
                "HTTPS is required."
            }
            require(parsed.username.isEmpty() && parsed.password.isEmpty()) {
                "Remove credentials from the server URL."
            }
            val builder =
                Request.Builder()
                    .url(parsed)
                    .header("Accept", "application/json")
                    .header("User-Agent", "STAK Native/0.1")
            if (jellyfin) {
                val authorization = jellyfinAuthorization(token)
                builder.header("Authorization", authorization)
                    .header("X-Emby-Authorization", authorization)
                if (token.isNotBlank()) builder.header("X-Emby-Token", token)
            } else if (token.isNotBlank()) {
                require(!token.contains('\r') && !token.contains('\n') && token.length <= 4096) {
                    "Invalid API credential."
                }
                builder.header("Authorization", "Bearer $token")
            }
            if (delete) builder.delete()
            else if (form != null)
                builder.post(
                    okhttp3.FormBody.Builder()
                        .apply { form.forEach { (k, v) -> add(k, v) } }
                        .build()
                )
            else if (body != null)
                builder.post(body.toString().toRequestBody("application/json".toMediaType()))
            client.newCall(builder.build()).awaitResponse().use { response ->
                check(response.isSuccessful) {
                    when (response.code) {
                        401,
                        403 -> "Connection rejected. Check your credentials and permissions."
                        in 300..399 ->
                            "Server redirected the request. Enter its final address in Settings."
                        400 -> if (jellyfin)
                            "Jellyfin returned HTTP 400. Check the server's http/https protocol, port and base path. A reverse proxy must forward Authorization headers."
                            else "Service returned HTTP 400. Try again."
                        else -> "Service returned HTTP ${response.code}. Try again."
                    }
                }
                val source = response.body?.source() ?: error("Empty response.")
                require(!source.request(8L * 1024 * 1024 + 1)) { "Response exceeds 8 MB." }
                val text = source.readUtf8()
                if (text.isBlank()) JSONObject()
                else if (text.trimStart().startsWith("["))
                    JSONObject().put("items", JSONArray(text))
                else JSONObject(text)
            }
        }

    /** Direct configured indexer requests never inherit TorBox or Jellyfin authorization. */
    suspend fun indexerText(url: String): String =
        withContext(Dispatchers.IO) {
            val parsed = indexerUrl(url)
            val request =
                Request.Builder()
                    .url(parsed)
                    .header("Accept", "application/json, application/xml, text/xml")
                    .header("User-Agent", "STAK Native/0.1")
                    .build()
            client.newCall(request).awaitResponse().use { response ->
                check(response.isSuccessful) {
                    when (response.code) {
                        401,
                        403 -> "Indexer rejected the credentials."
                        in 300..399 -> "Indexer redirected the request. Enter its final endpoint."
                        else -> "Indexer returned HTTP ${response.code}."
                    }
                }
                val source = response.body?.source() ?: error("Empty indexer response.")
                require(!source.request(8L * 1024 * 1024 + 1)) { "Indexer response exceeds 8 MB." }
                source.readUtf8()
            }
        }

    suspend fun catalog(type: String, query: String = "", skip: Int = 0, refresh: Boolean = false): List<Media> = withContext(Dispatchers.Default) {
        require(type == "movie" || type == "series")
        if (credentials.get("tmdb_key").isNotBlank())
            return@withContext browse(type, query = query, page = skip / 20 + 1)
        val extra =
            if (query.isBlank()) (if (skip == 0) "" else "/skip=$skip")
            else
                "/search=${java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")}" +
                    if (skip > 0) "&skip=$skip" else ""
        val url = "https://v3-cinemeta.strem.io/catalog/$type/top$extra.json"
        val root = if (query.isBlank()) publicMetadata("catalog:$type:$skip", maxAge = if (refresh) 0L else 300_000L) {
            request(url)
        } else request(url)
        root.optJSONArray("metas").objects().map { remember(Media.fromJson(it)) }
    }

    suspend fun details(media: Media): Media = withContext(Dispatchers.Default) {
        if (
            credentials.get("tmdb_key").isNotBlank() &&
                preferences.getString("metadata_${media.type}", "tmdb") == "tmdb"
        )
            return@withContext tmdbDetails(media)
        if (!media.id.startsWith("tt")) return@withContext media
        val root =
            publicMetadata("cinemeta:${media.type}:${media.id}") {
                    request("https://v3-cinemeta.strem.io/meta/${media.type}/${media.id}.json")
                }
                .optJSONObject("meta") ?: error("Title unavailable.")
        remember(
            Media.fromJson(root)
                .copy(
                    episodes =
                        root
                            .optJSONArray("videos")
                            .objects()
                            .filter { it.optInt("season") > 0 && it.optInt("episode") > 0 }
                            .map {
                                Episode(
                                    it.optString("id"),
                                    it.optInt("season"),
                                    it.optInt("episode"),
                                    it.optString(
                                        "name",
                                        it.optString("title", "Episode ${it.optInt("episode")}"),
                                    ),
                                    it.optString("released"),
                                    it.optString("thumbnail"),
                                    it.optString("overview"),
                                )
                            }
                )
        )
    }

    private fun jellyfinUrl(path: String): String {
        val base = credentials.get("jellyfin_url").trim().trimEnd('/').toHttpUrl()
        require(
            base.query == null &&
                base.fragment == null &&
                base.username.isEmpty() &&
                base.password.isEmpty()
        ) {
            "Enter only your server address."
        }
        return base.toString().trimEnd('/') + path
    }

    suspend fun jellyfin(
        path: String,
        body: JSONObject? = null,
        delete: Boolean = false,
    ): JSONObject =
        request(jellyfinUrl(path), credentials.get("jellyfin_token"), true, body, delete)

    suspend fun library(resume: Boolean = false): List<Media> {
        val session = jellyfinConnection()
        val items =
            if (resume) {
                request(
                        "${session.url}/Users/${session.user}/Items/Resume?Limit=30&Fields=Overview,ProviderIds,Genres&MediaTypes=Video",
                        session.token,
                        true,
                    )
                    .optJSONArray("Items")
                    .objects()
            } else
                jellyfinItems(
                    session,
                    "/Users/${session.user}/Items",
                    mapOf(
                        "Recursive" to "true",
                        "IncludeItemTypes" to "Movie,Series",
                        "Fields" to "Overview,ProviderIds,Genres",
                        "SortBy" to "SortName",
                    ),
                )
        requireSameConnection(session)
        return items.map { remember(jellyfinMedia(it, session)) }
    }

    suspend fun setJellyfinWatched(media: Media, watched: Boolean) {
        if (media.jellyfinId.isBlank()) return
        val user = credentials.get("jellyfin_user")
        require(
            Regex("^[A-Za-z0-9-]+$").matches(user) &&
                Regex("^[A-Za-z0-9-]+$").matches(media.jellyfinId)
        )
        jellyfin(
            "/Users/$user/PlayedItems/${media.jellyfinId}",
            if (watched) JSONObject() else null,
            !watched,
        )
    }

    suspend fun torbox(path: String): JSONObject {
        val token = credentials.get("torbox_token")
        require(token.isNotBlank()) { "Connect TorBox in Settings." }
        val root = request("https://api.torbox.app/v1/api$path", token)
        check(root.optBoolean("success")) { "TorBox could not complete this request." }
        return root
    }

    suspend fun torrents(): List<Torrent> =
        torbox("/torrents/mylist").optJSONArray("data").objects().map {
            Torrent(
                it.optInt("id"),
                it.optString("name"),
                it.optDouble("progress").toFloat().coerceIn(0f, 1f),
                if (it.optBoolean("download_finished")) "Completed"
                else it.optString("download_state"),
                it.optJSONArray("files").objects().map { f ->
                    TorrentFile(
                        f.optInt("id"),
                        f.optString("short_name", f.optString("name")),
                        f.optLong("size"),
                    )
                },
                it.optString("hash"),
                it.optLong("size"),
            )
        }

    suspend fun torboxLink(torrent: Int, file: Int): String {
        // TorBox's requestdl API requires the token as a query parameter. Keep it off the external
        // player URI.
        val url =
            "https://api.torbox.app/v1/api/torrents/requestdl"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("token", credentials.get("torbox_token"))
                .addQueryParameter("torrent_id", torrent.toString())
                .addQueryParameter("file_id", file.toString())
                .addQueryParameter("zip_link", "false")
                .build()
        val result = request(url.toString(), credentials.get("torbox_token"))
        check(result.optBoolean("success")) { "File link unavailable." }
        return result.optString("data").also {
            require(it.toHttpUrl().isHttps) { "Invalid download link." }
        }
    }
}


/** Preserve provider title variants through cache, backup and metadata enrichment. */
internal fun mediaAliases(item: JSONObject): List<String> {
    val alternatives = item.optJSONArray("alternative_titles")
        ?: item.optJSONObject("alternative_titles")?.let {
            it.optJSONArray("titles") ?: it.optJSONArray("results")
        }
    return (listOf(item.optString("title"), item.optString("original_title"),
        item.optString("original_name")) + item.optJSONArray("aliases").strings() +
        (0 until (alternatives?.length() ?: 0)).map { index ->
            when (val value = alternatives?.opt(index)) {
                is String -> value
                is JSONObject -> value.optString("title", value.optString("name"))
                else -> ""
            }
        }).filter { it.isNotBlank() }.distinct()
}
