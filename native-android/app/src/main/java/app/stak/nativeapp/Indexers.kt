package app.stak.nativeapp

import java.io.StringReader
import java.util.UUID
import javax.xml.parsers.SAXParserFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.*
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

data class Indexer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: String,
    val url: String,
    val apiKey: String = "",
    val enabled: Boolean = true,
) {
    fun json() =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("kind", kind)
            .put("url", url)
            .put("api_key", apiKey)
            .put("enabled", enabled)
}

fun indexerUrl(value: String): HttpUrl {
    val url = value.trim().toHttpUrlOrNull()
    require(url != null && url.username.isEmpty() && url.password.isEmpty()) {
        "Enter an HTTP or HTTPS endpoint without a username or password in its address."
    }
    return url.newBuilder().fragment(null).build()
}

fun validateIndexer(item: Indexer): Indexer {
    require(item.kind in listOf("torznab", "stremio")) { "Choose Torznab or Stremio." }
    require(item.url.length <= 8192 && item.apiKey.length <= 4096) {
        "Endpoint or API key is too long."
    }
    return item.copy(
        name =
            item.name
                .trim()
                .ifBlank { if (item.kind == "torznab") "Torznab" else "Stremio" }
                .take(160),
        url = indexerUrl(item.url).toString(),
    )
}

/**
 * Indexer URLs can contain credentials, so the entire account-scoped list is Keystore encrypted.
 */
class IndexerStore(private val repository: Repository) {
    private fun storageKey(): String {
        val scope = repository.accountScope
        return "indexers_$scope"
    }

    fun read(): List<Indexer> {
        val text = repository.credentials.get(storageKey()).ifBlank { "[]" }
        return runCatching {
                JSONArray(text).objects().map {
                    Indexer(
                        it.getString("id"),
                        it.optString("name", "Indexer"),
                        it.getString("kind"),
                        it.getString("url"),
                        it.optString("api_key"),
                        it.optBoolean("enabled", true),
                    )
                }
            }
            .getOrDefault(emptyList())
    }

    val items = MutableStateFlow(read())

    fun reload() {
        items.value = read()
    }

    private fun write(list: List<Indexer>) {
        repository.credentials.set(storageKey(), JSONArray(list.map { it.json() }).toString())
        items.value = list
    }

    fun mergeMissing(imported: List<Indexer>) {
        val existing = read()
        val endpoints = existing.map { it.kind to it.url }.toSet()
        val ids = existing.map { it.id }.toMutableSet()
        val additions =
            imported
                .distinctBy { it.kind to it.url }
                .filter { (it.kind to it.url) !in endpoints }
                .map {
                    val id =
                        if (ids.add(it.id)) it.id
                        else java.util.UUID.randomUUID().toString().also(ids::add)
                    it.copy(id = id)
                }
        require(existing.size + additions.size <= 5000) { "Indexer limit reached." }
        write(existing + additions)
    }

    fun add(item: Indexer) {
        val list = read()
        require(list.size < 5000) { "Indexer limit reached." }
        write(list + validateIndexer(item))
    }

    fun toggle(id: String, enabled: Boolean) =
        write(read().map { if (it.id == id) it.copy(enabled = enabled) else it })

    fun remove(id: String) = write(read().filterNot { it.id == id })
}

suspend fun Repository.testIndexer(item: Indexer): String {
    val valid = validateIndexer(item)
    val url = indexerUrl(valid.url).newBuilder()
    if (valid.kind == "torznab") {
        url.setQueryParameter("t", "caps")
        if (valid.apiKey.isNotBlank()) url.setQueryParameter("apikey", valid.apiKey)
    }
    val response = indexerText(url.build().toString())
    return if (valid.kind == "stremio") {
        val manifest = JSONObject(response)
        check(manifest.optString("id").isNotBlank()) { "Invalid Stremio manifest." }
        manifest.optString("name", "Stremio addon")
    } else {
        check(Regex("<caps(?:\\s|>)", RegexOption.IGNORE_CASE).containsMatchIn(response)) {
            "No Torznab capabilities received. Check the endpoint and API key."
        }
        "Torznab capabilities received"
    }
}

fun normalizeTorrentHash(value: String): String {
    Regex("(?:urn:btih:|^)([a-f0-9]{40})(?:$|[^a-f0-9])", RegexOption.IGNORE_CASE)
        .find(value)
        ?.let {
            return it.groupValues[1].lowercase()
        }
    val base32 =
        Regex("(?:urn:btih:|^)([a-z2-7]{32})(?:$|[^a-z2-7])", RegexOption.IGNORE_CASE)
            .find(value)
            ?.groupValues
            ?.get(1) ?: return ""
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    var bits = 0
    var accumulator = 0
    return buildString {
        for (ch in base32.uppercase(java.util.Locale.ROOT)) {
            accumulator = (accumulator shl 5) or alphabet.indexOf(ch)
            bits += 5
            if (bits >= 8) {
                bits -= 8
                append("%02x".format((accumulator ushr bits) and 255))
            }
        }
    }
}

data class TorznabResults(val streams: List<SourceStream>, val missingHashes: Int)

fun parseTorznab(xml: String, source: String): TorznabResults {
    require(!Regex("<!DOCTYPE", RegexOption.IGNORE_CASE).containsMatchIn(xml)) {
        "Unsupported XML document type."
    }
    val results = mutableListOf<SourceStream>()
    val seen = mutableSetOf<String>()
    var missing = 0
    var inItem = false
    var tag = ""
    val text = StringBuilder()
    val fields = mutableMapOf<String, String>()
    val properties = mutableMapOf<String, String>()
    val factory =
        SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
    factory
        .newSAXParser()
        .parse(
            InputSource(StringReader(xml)),
            object : DefaultHandler() {
                override fun startElement(
                    uri: String?,
                    localName: String?,
                    qName: String,
                    attrs: Attributes,
                ) {
                    if (qName.equals("error", true))
                        error("Torznab rejected the search. Check credentials and capabilities.")
                    if (qName.equals("item", true)) {
                        inItem = true
                        fields.clear()
                        properties.clear()
                    }
                    if (!inItem) return
                    tag = qName.lowercase()
                    text.setLength(0)
                    if (tag.endsWith(":attr"))
                        properties[attrs.getValue("name").orEmpty().lowercase()] =
                            attrs.getValue("value").orEmpty()
                    if (tag == "enclosure") {
                        fields["enclosure"] = attrs.getValue("url").orEmpty()
                        fields["size"] = attrs.getValue("length").orEmpty()
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (inItem) text.append(ch, start, length)
                }

                override fun endElement(uri: String?, localName: String?, qName: String) {
                    if (!inItem) return
                    val current = qName.lowercase()
                    if (current == tag && current in listOf("title", "link", "guid"))
                        fields[current] = text.toString().trim()
                    if (current == "item") {
                        val magnet =
                            properties["magneturl"]
                                ?: listOf("enclosure", "link", "guid")
                                    .mapNotNull { fields[it] }
                                    .firstOrNull { it.startsWith("magnet:") }
                                    .orEmpty()
                        val hash =
                            normalizeTorrentHash(
                                    properties["infohash"] ?: properties["hash"].orEmpty()
                                )
                                .ifBlank { normalizeTorrentHash(magnet) }
                        if (hash.isBlank()) missing++
                        else if (seen.add(hash))
                            results +=
                                SourceStream(
                                    source,
                                    fields["title"].orEmpty(),
                                    hash,
                                    null,
                                    source,
                                    (properties["size"] ?: fields["size"])?.toLongOrNull() ?: 0,
                                    properties["seeders"]?.toIntOrNull()?.coerceAtLeast(0),
                                    magnet = magnet.takeIf { it.startsWith("magnet:") }.orEmpty(),
                                )
                        inItem = false
                    }
                }
            },
        )
    return TorznabResults(results, missing)
}

data class SourceSearch(val streams: List<SourceStream>, val errors: List<String>)

suspend fun Repository.searchSources(media: Media, episode: Episode? = null): SourceSearch =
    withContext(Dispatchers.Default) {
        val id = media.imdbId.ifBlank { media.id.takeIf { it.startsWith("tt") }.orEmpty() }
        require(Regex("tt[0-9]+").matches(id)) {
            "No IMDb identity is available for source search."
        }
        val configured =
            IndexerStore(this@searchSources).read().filter { it.enabled }.toMutableList()
        if (preferences.getString("torrentio", "true") != "false")
            configured.add(
                0,
                Indexer(
                    name = "Torrentio",
                    kind = "stremio",
                    url = "https://torrentio.strem.fun/manifest.json",
                ),
            )
        require(configured.isNotEmpty()) { "Enable a source in Settings → Search sources." }
        val gate = Semaphore(4)
        val responses =
            configured
                .map { item ->
                    async {
                        gate.withPermit {
                            try {
                                if (item.kind == "stremio") {
                                    val suffix =
                                        if (episode == null) id
                                        else "$id:${episode.season}:${episode.number}"
                                    val base =
                                        indexerUrl(item.url)
                                            .newBuilder()
                                            .query(null)
                                            .fragment(null)
                                            .build()
                                            .toString()
                                            .replace(
                                                Regex(
                                                    "/manifest\\.json/?$",
                                                    RegexOption.IGNORE_CASE,
                                                ),
                                                "",
                                            )
                                            .trimEnd('/')
                                    val data =
                                        JSONObject(
                                            indexerText("$base/stream/${media.type}/$suffix.json")
                                        )
                                    SourceSearch(
                                        data.optJSONArray("streams").objects().mapNotNull { parseAddonStream(it, item.name) },
                                        emptyList(),
                                    )
                                } else {
                                    val url =
                                        indexerUrl(item.url)
                                            .newBuilder()
                                            .setQueryParameter(
                                                "t",
                                                if (media.type == "series") "tvsearch" else "movie",
                                            )
                                            .setQueryParameter("imdbid", id.removePrefix("tt"))
                                            .setQueryParameter("q", media.name)
                                    if (item.apiKey.isNotBlank())
                                        url.setQueryParameter("apikey", item.apiKey)
                                    episode?.let {
                                        url.setQueryParameter("season", it.season.toString())
                                            .setQueryParameter("ep", it.number.toString())
                                    }
                                    val result =
                                        parseTorznab(indexerText(url.build().toString()), item.name)
                                    SourceSearch(
                                        result.streams,
                                        if (result.missingHashes > 0)
                                            listOf(
                                                "${item.name}: ${result.missingHashes} results lacked a torrent hash. Add their .torrent files through Library."
                                            )
                                        else emptyList(),
                                    )
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                SourceSearch(
                                    emptyList(),
                                    listOf("${item.name}: ${failure.message ?: "Search failed."}"),
                                )
                            }
                        }
                    }
                }
                .awaitAll()
        SourceSearch(
            responses.flatMap { it.streams }.distinctBy { it.hash to it.fileIndex },
            responses.flatMap { it.errors },
        )
    }
