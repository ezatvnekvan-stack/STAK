package app.stak.nativeapp

import java.text.Normalizer
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

/** Same filename tags as the original mediaQuality.js. These are inferred, never verified. */
private val qualityRules =
        listOf(
            "\\b(?:2160p|4k|uhd)\\b" to "4K",
            "\\b(?:1080p|fhd)\\b" to "1080p",
            "\\b(?:720p|hd)\\b" to "720p",
            "\\b(?:dolby[ ._-]?vision|dovi|dv(?:[ ._-]?p\\d)?)\\b" to "Dolby Vision",
            "\\b(?:hdr10\\+|hdr10plus)(?![a-z0-9])" to "HDR10+",
            "\\bhdr10\\b" to "HDR10",
            "\\bhdr\\b" to "HDR",
            "\\batmos\\b" to "Atmos",
            "\\baac\\b" to "AAC",
            "\\bflac\\b" to "FLAC",
            "\\bopus\\b" to "Opus",
            "\\b10[ ._-]?bit\\b" to "10-bit",
            "\\btruehd\\b" to "TrueHD",
            "\\b(?:dts[ ._-]?hd|dts[ ._-]?x)\\b" to "DTS-HD / X",
            "\\b(?:ddp|eac3)\\b" to "DD+",
            "\\bremux\\b" to "REMUX",
            "\\b(?:web[ ._-]?dl|webdl)\\b" to "WEB-DL",
            "\\b(?:bluray|blu[ ._-]?ray)\\b" to "Blu-ray",
            "\\b(?:hevc|h265|x265)\\b" to "HEVC",
            "\\bav1\\b" to "AV1",
            "\\b(?:avc|h264|x264)\\b" to "H.264",
            "\\b(?:hun|hungarian|magyar)\\b" to "HU",
        )
        .map { Regex(it.first, RegexOption.IGNORE_CASE) to it.second }

fun qualityTags(release: String): List<String> {
    // An underscore is a filename separator, but regex word boundaries treat it as a letter.
    val normalized = release.replace('_', '.')
    return qualityRules.filter { it.first.containsMatchIn(normalized) }.map { it.second }
}

fun SourceStream.quality() = qualityTags("$name $title")

fun SourceStream.resolution() =
    quality().firstOrNull { it in listOf("4K", "1080p", "720p") } ?: "Unknown"

/** Same filename heuristic as the Capacitor seasonPack helper; contents are unverified. */
fun SourceStream.isSeasonPackFor(season: Int): Boolean {
    val release = "$title $name"
    fun pattern(value: String) = Regex(value, RegexOption.IGNORE_CASE)
    val single = pattern("\\bS\\d{1,2}[ ._-]?E\\d{1,3}\\b|\\b\\d{1,2}x\\d{2,3}\\b")
    val episodeRange = pattern("\\bS?\\d{0,2}[ ._-]?E\\d{1,3}[ ._-]?(?:-|–|~|to)[ ._-]?E?\\d{1,3}\\b|\\b\\d{1,2}x\\d{2,3}[ ._-]?(?:-|–|to)[ ._-]?\\d{2,3}\\b")
    val ranged = episodeRange.containsMatchIn(release)
    if (single.containsMatchIn(release) && !ranged) return false
    val episodeSeason = if (ranged) pattern("\\b(?:S(\\d{1,2})[ ._-]?E\\d|([0-9]{1,2})x\\d)").find(release) else null
    val range = if (episodeSeason != null) {
        val value = episodeSeason.groupValues[1].ifBlank { episodeSeason.groupValues[2] }.toInt()
        value..value
    } else {
        val matched = pattern("\\bS(\\d{1,2})(?:[ ._-]?(?:-|–|to|&|\\+)[ ._-]?S?(\\d{1,2}))?\\b(?![ ._-]?E\\d)").find(release)
            ?: pattern("\\b(?:seasons?|staffel|saison|temporadas?|stagione|[eé]vad)[ ._-]?(\\d{1,2})(?:[ ._-]?(?:-|–|to|&|\\+)[ ._-]?(\\d{1,2}))?\\b").find(release)
        matched?.let {
            val first = it.groupValues[1].toInt()
            val last = it.groupValues[2].toIntOrNull() ?: first
            minOf(first, last)..maxOf(first, last)
        }
    }
    if (range != null) return season in range
    return ranged || pattern("\\b(?:complete[ ._-]?(?:series|season|collection|edition)?|season[ ._-]?pack|full[ ._-]?(?:series|season)|batch|teljes)\\b").containsMatchIn(release)
}

private fun normalizedRelease(value: String) =
    Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(java.util.Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

data class SourceMatch(val label: String, val reasons: List<String>, val warnings: List<String>)

fun sourceMatch(source: SourceStream, media: Media, episode: Episode?): SourceMatch {
    val name = source.title.lineSequence().firstOrNull().orEmpty().ifBlank { source.name }
    val all = "$name ${source.name}"
    val reasons = mutableListOf<String>()
    val extra =
        Regex(
                "\\b(prologue|sample|trailer|teaser|featurette|behind[ ._-]the[ ._-]scenes|deleted[ ._-]scenes)\\b",
                RegexOption.IGNORE_CASE,
            )
            .find(name)
            ?.value
    if (extra != null && !normalizedRelease(media.name).contains(normalizedRelease(extra)))
        reasons += "Labelled $extra; this may be an extra rather than the full title."
    val clean = name.replace(Regex("[._]+"), " ")
    val cut =
        Regex(
                "\\bS\\d{1,2}(?:E\\d+)?\\b|\\b\\d{1,2}x\\d{2,3}\\b|\\b(?:19|20)\\d{2}\\b|\\b(?:2160p|1080p|720p|480p|4K|UHD|WEB|BluRay|REMUX|HDR)\\b",
                RegexOption.IGNORE_CASE,
            )
            .find(clean)
            ?.range
            ?.first ?: -1
    val prefix = normalizedRelease(if (cut > 0) clean.take(cut) else clean)
    val expectedName = normalizedRelease(media.name)
    val titleYears = Regex("\\b(?:19|20)\\d{2}\\b").findAll(media.name).map { it.value }.toSet()
    val releaseYear =
        Regex("\\b(?:19|20)\\d{2}\\b").findAll(clean).firstOrNull { it.value !in titleYears }?.value
    if (
        media.type == "movie" &&
            media.year.take(4).toIntOrNull() != null &&
            releaseYear != null &&
            releaseYear != media.year.take(4)
    )
        reasons += "Release year $releaseYear differs from ${media.year.take(4)}."
    val pair =
        Regex(
                "\\bS(\\d{1,2})[ ._-]?E(\\d{1,3})\\b|\\b(\\d{1,2})x(\\d{2,3})\\b",
                RegexOption.IGNORE_CASE,
            )
            .find(name)
    if (episode != null && pair != null) {
        val season = pair.groupValues[1].ifBlank { pair.groupValues[3] }.toIntOrNull()
        val number = pair.groupValues[2].ifBlank { pair.groupValues[4] }.toIntOrNull()
        if (season != episode.season || number != episode.number)
            reasons += "Filename targets a different episode."
    }
    val exact =
        if (titleYears.isEmpty()) prefix == expectedName
        else normalizedRelease(name).let { it == expectedName || it.startsWith("$expectedName ") }
    val warnings =
        if (
            Regex(
                    "\\b(prores|dcp|4444|dci|cam|hdcam|telesync|telecine)\\b",
                    RegexOption.IGNORE_CASE,
                )
                .containsMatchIn(all)
        )
            listOf("Production or camera-release format; playback compatibility is unverified.")
        else emptyList()
    return SourceMatch(
        if (reasons.isNotEmpty()) "Check title match"
        else if (exact) "Filename matches · inferred" else "Match unconfirmed",
        reasons,
        warnings,
    )
}

data class SourceCache(
    val cached: Set<String> = emptySet(),
    val checked: Set<String> = emptySet(),
    val sizes: Map<String, Long> = emptyMap(),
    val errors: List<String> = emptyList(),
)

fun parseSourceCache(root: JSONObject, requested: Set<String>): SourceCache {
    check(root.optBoolean("success")) { "TorBox cache check was rejected." }
    val data = root.optJSONArray("data") ?: error("TorBox returned an invalid cache response.")
    require(data.objects().size == data.length()) { "TorBox returned malformed cache entries." }
    val cached = linkedSetOf<String>()
    val sizes = linkedMapOf<String, Long>()
    data.objects().forEach { item ->
        val hash = normalizeTorrentHash(item.optString("hash"))
        require(hash.isNotBlank() && hash in requested) {
            "TorBox returned an unexpected cache entry."
        }
        cached += hash
        item.optLong("size", item.optLong("total_size")).takeIf { it > 0 }?.let { sizes[hash] = it }
    }
    return SourceCache(cached, requested, sizes)
}

suspend fun Repository.checkSourceCache(hashes: List<String>): SourceCache {
    val token = credentials.get("torbox_token")
    require(token.isNotBlank()) { "Connect TorBox to check cached availability." }
    val requested = hashes.map(::normalizeTorrentHash).filter { it.isNotBlank() }.distinct()
    var result = SourceCache()
    for (batch in requested.chunked(80)) {
        check(credentials.get("torbox_token") == token) { "Account changed. Search sources again." }
        try {
            val url =
                "https://api.torbox.app/v1/api/torrents/checkcached"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("hash", batch.joinToString(","))
                    .addQueryParameter("format", "list")
                    .addQueryParameter("list_files", "true")
                    .build()
            val found = parseSourceCache(request(url.toString(), token), batch.toSet())
            result =
                result.copy(
                    cached = result.cached + found.cached,
                    checked = result.checked + found.checked,
                    sizes = result.sizes + found.sizes,
                )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            result =
                result.copy(errors = result.errors + (failure.message ?: "Cache check failed."))
        }
    }
    check(credentials.get("torbox_token") == token) { "Account changed. Search sources again." }
    return result
}
