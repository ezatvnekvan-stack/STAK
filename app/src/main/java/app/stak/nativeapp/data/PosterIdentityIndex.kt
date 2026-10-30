package app.stak.nativeapp.data

import app.stak.nativeapp.data.db.TrackingEntity

/**
 * Pre-indexes Jellyfin items, local tracking rows and the watchlist so poster
 * state is a couple of hash lookups instead of scanning every collection for
 * every visible poster on every recomposition.
 *
 * Before this class, each poster called `isWatched`/`isWatchlisted`, which
 * filtered the whole Jellyfin list, built identity key sets and then looked for
 * a matching tracking row. With a 1k-item library that is O(library x posters)
 * work on the UI thread during scroll and after every tracking emit.
 *
 * The resolution order below intentionally mirrors the previous inline lambdas;
 * `PosterIdentityIndexTest` compares both implementations on the same data.
 */
@androidx.compose.runtime.Stable
class PosterIdentityIndex(
    jellyfinItems: List<MediaItem> = emptyList(),
    tracking: List<TrackingEntity> = emptyList(),
    watchlist: List<MediaItem> = emptyList()
) {
    private val nonEpisodeTracking: List<TrackingEntity> =
        tracking.filter { it.type != "episode" }

    /** Identity key -> Jellyfin items still carrying that key. */
    private val jellyfinByKey: Map<String, List<MediaItem>> = buildMap<String, MutableList<MediaItem>> {
        jellyfinItems.forEach { item ->
            jellyfinIdentityKeys(item).forEach { key ->
                getOrPut(key) { mutableListOf() }.add(item)
            }
        }
    }

    private val jellyfinKeys: Set<String> = jellyfinByKey.keys

    private val jellyfinWatchedKeys: Set<String> =
        jellyfinItems.asSequence().filter { it.watched }.map { it.mediaKey }.toSet()

    /** Earliest tracking row per id, matching `firstOrNull { it.id == key }`. */
    private val rowsById: Map<String, TrackingEntity>
    private val rowIndexById: Map<String, Int>

    private val watchlistIdentities: Set<String>

    /** Memoises identity keys per poster so the regex work happens once. */
    private val keyCache = HashMap<String, Set<String>>()

    init {
        val rows = LinkedHashMap<String, TrackingEntity>(nonEpisodeTracking.size)
        val indexes = HashMap<String, Int>(nonEpisodeTracking.size)
        nonEpisodeTracking.forEachIndexed { index, row ->
            if (rows.putIfAbsent(row.id, row) == null) indexes[row.id] = index
        }
        rowsById = rows
        rowIndexById = indexes
        watchlistIdentities = buildSet {
            watchlist.forEach {
                add("${it.type}|${it.mediaKey}")
                add("${it.type}|${it.id}")
            }
        }
    }

    fun keysOf(media: MediaItem): Set<String> {
        val cacheKey = keyOf(media)
        return keyCache[cacheKey] ?: jellyfinIdentityKeys(media).also { keyCache[cacheKey] = it }
    }

    fun isInJellyfin(media: MediaItem): Boolean = keysOf(media).any { it in jellyfinKeys }

    fun isWatchlisted(media: MediaItem): Boolean =
        "${media.type}|${media.mediaKey}" in watchlistIdentities ||
            "${media.type}|${media.id}" in watchlistIdentities

    fun isWatched(media: MediaItem): Boolean {
        firstTrackingRow(media.mediaKey, media.id)?.let { return it.played }

        val matchedKeys = jellyfinMediaKeys(media)
        if (matchedKeys.isNotEmpty()) {
            var best: TrackingEntity? = null
            var bestIndex = Int.MAX_VALUE
            matchedKeys.forEach { key ->
                val row = rowsById[key] ?: return@forEach
                val index = rowIndexById[key] ?: return@forEach
                if (index < bestIndex) {
                    bestIndex = index
                    best = row
                }
            }
            best?.let { return it.played }
            return matchedKeys.any { it in jellyfinWatchedKeys }
        }
        return media.watched
    }

    /** Media keys of every Jellyfin item sharing an identity key with [media]. */
    private fun jellyfinMediaKeys(media: MediaItem): Set<String> {
        val result = LinkedHashSet<String>(2)
        keysOf(media).forEach { key ->
            jellyfinByKey[key]?.forEach { result.add(it.mediaKey) }
        }
        return result
    }

    private fun firstTrackingRow(first: String, second: String): TrackingEntity? {
        val a = rowsById[first]
        val b = rowsById[second]
        if (a == null) return b
        if (b == null || first == second) return a
        val indexA = rowIndexById[first] ?: Int.MAX_VALUE
        val indexB = rowIndexById[second] ?: Int.MAX_VALUE
        return if (indexA <= indexB) a else b
    }

    private fun keyOf(media: MediaItem): String = buildString {
        append(media.type).append('|')
        append(media.id).append('|')
        append(media.imdbId).append('|')
        append(media.jellyfinId).append('|')
        media.jellyfinProviderKeys.orEmpty().forEach { append(it).append(',') }
    }
}
