package app.stak.nativeapp.data

import app.stak.nativeapp.data.db.TrackingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the indexed poster lookups to the original inline logic that used to
 * live in `StakApp`. Both implementations run on the same fixtures so a future
 * change to either one is caught.
 */
class PosterIdentityIndexTest {

    private fun movie(
        id: String,
        imdb: String? = null,
        jellyfin: String? = null,
        watched: Boolean = false,
        providerKeys: List<String>? = emptyList()
    ) = MediaItem(
        id = id,
        imdbId = imdb,
        type = "movie",
        name = "Title $id",
        jellyfinId = jellyfin,
        watched = watched,
        jellyfinProviderKeys = providerKeys
    )

    private fun row(id: String, played: Boolean, type: String = "movie") =
        TrackingEntity(id = id, type = type, name = "row $id", played = played)

    // ---- reference implementation, copied from the previous lambdas ----

    private fun referenceIsWatched(
        media: MediaItem,
        jellyfinItems: List<MediaItem>,
        localTracking: List<TrackingEntity>
    ): Boolean {
        val matching = jellyfinItems.filter { known ->
            jellyfinIdentityKeys(known).intersect(jellyfinIdentityKeys(media)).isNotEmpty()
        }
        return localTracking.firstOrNull {
            it.type != "episode" && (it.id == media.mediaKey || it.id == media.id || matching.any { known -> known.mediaKey == it.id })
        }?.played
            ?: if (matching.isNotEmpty()) matching.any { it.watched } else media.watched
    }

    private fun referenceIsInJellyfin(media: MediaItem, jellyfinItems: List<MediaItem>): Boolean {
        val keys = jellyfinItems.flatMap { jellyfinIdentityKeys(it) }.toSet()
        return jellyfinIdentityKeys(media).any { it in keys }
    }

    private fun referenceIsWatchlisted(media: MediaItem, watchlist: List<MediaItem>): Boolean =
        watchlist.any { it.sameIdentity(media) }

    private fun assertSame(
        jellyfinItems: List<MediaItem>,
        tracking: List<TrackingEntity>,
        watchlist: List<MediaItem>,
        probes: List<MediaItem>
    ) {
        val index = PosterIdentityIndex(jellyfinItems, tracking, watchlist)
        probes.forEach { probe ->
            assertEquals(
                "isWatched for ${probe.name}",
                referenceIsWatched(probe, jellyfinItems, tracking),
                index.isWatched(probe)
            )
            assertEquals(
                "isInJellyfin for ${probe.name}",
                referenceIsInJellyfin(probe, jellyfinItems),
                index.isInJellyfin(probe)
            )
            assertEquals(
                "isWatchlisted for ${probe.name}",
                referenceIsWatchlisted(probe, watchlist),
                index.isWatchlisted(probe)
            )
        }
    }

    @Test
    fun emptyDataFallsBackToTheItemFlag() {
        assertSame(emptyList(), emptyList(), emptyList(), listOf(movie("tt1", watched = true)))
        assertSame(emptyList(), emptyList(), emptyList(), listOf(movie("tt2", watched = false)))
    }

    @Test
    fun directTrackingRowWinsOverJellyfinState() {
        val item = movie("tt100", imdb = "tt100")
        val tracked = row("tt100", played = true)
        val jellyfin = listOf(movie("tt100", imdb = "tt100", watched = false))
        assertSame(jellyfin, listOf(tracked), emptyList(), listOf(item))
    }

    @Test
    fun trackingRowReachedThroughJellyfinIdentityStillApplies() {
        // Jellyfin reports a provider id; tracking stored the IMDb id.
        val jellyfin = listOf(movie("jf-1", imdb = "tt200", watched = false))
        val tracked = row("tt200", played = true)
        val probe = movie("jf-1", imdb = "tt200")
        assertSame(jellyfin, listOf(tracked), emptyList(), listOf(probe))
    }

    @Test
    fun earlierTrackingRowWinsWhenTwoRowsMatch() {
        val jellyfin = listOf(movie("jf-2", imdb = "tt300", watched = false))
        val rows = listOf(row("tt300", played = false), row("jf-2", played = true))
        assertSame(jellyfin, rows, emptyList(), listOf(movie("tt300", imdb = "tt300")))
    }

    @Test
    fun episodeRowsNeverDecideTitleState() {
        val jellyfin = listOf(movie("tt400", imdb = "tt400", watched = true))
        val rows = listOf(row("tt400", played = false, type = "episode"))
        assertSame(jellyfin, rows, emptyList(), listOf(movie("tt400", imdb = "tt400")))
    }

    @Test
    fun jellyfinWatchedStateAppliesWhenNoLocalRowExists() {
        val jellyfin = listOf(movie("jf-3", imdb = "tt500", watched = true))
        assertSame(jellyfin, emptyList(), emptyList(), listOf(movie("tt500", imdb = "tt500")))
    }

    @Test
    fun providerKeysLinkTheSameTitleAcrossProviders() {
        val jellyfin = listOf(movie("jf-4", imdb = null, providerKeys = listOf("imdb:tt600")))
        val probe = movie("tt600", imdb = "tt600")
        assertSame(jellyfin, emptyList(), emptyList(), listOf(probe))
        assertTrue(PosterIdentityIndex(jellyfin).isInJellyfin(probe))
    }

    @Test
    fun watchlistMatchesByIdentityNotByInstance() {
        val saved = movie("tt700", imdb = "tt700")
        val probe = saved.copy(name = "Renamed")
        assertSame(emptyList(), emptyList(), listOf(saved), listOf(probe))
        assertTrue(PosterIdentityIndex(watchlist = listOf(saved)).isWatchlisted(probe))
    }

    @Test
    fun typeMismatchIsNotTheSameTitle() {
        val saved = movie("tt800", imdb = "tt800")
        val probe = saved.copy(type = "series")
        assertFalse(PosterIdentityIndex(watchlist = listOf(saved)).isWatchlisted(probe))
    }

    @Test
    fun repeatedLookupsStayCheapAndStable() {
        val index = PosterIdentityIndex(
            jellyfinItems = (1..50).map { movie("tt$it", imdb = "tt$it", watched = it % 3 == 0) },
            tracking = (1..50).filter { it % 5 == 0 }.map { row("tt$it", played = true) },
            watchlist = (1..50).filter { it % 7 == 0 }.map { movie("tt$it", imdb = "tt$it") }
        )
        repeat(500) {
            assertEquals(true, index.isWatched(movie("tt5", imdb = "tt5")))
            assertEquals(false, index.isWatched(movie("tt1", imdb = "tt1")))
            assertTrue(index.isWatchlisted(movie("tt7", imdb = "tt7")))
        }
    }
}
