package app.stak.nativeapp

import org.junit.Assert.*
import org.junit.Test

class ContinueWatchingTest {
    @Test fun resumeRemainingUsesPlaybackTicksAndNeverShowsNegativeTime() {
        val movie = Media("tt1", "movie", "Movie", runtime = "100m", playbackTicks = 41 * 600_000_000L)
        assertEquals(59L, continueRemainingMinutes(movie))
        assertEquals(0L, continueRemainingMinutes(movie.copy(playbackTicks = 150 * 600_000_000L)))
        assertNull(continueRemainingMinutes(movie.copy(runtime = "")))
    }
    @Test fun nextUpDoesNotDuplicateAResumedSeriesOrTheSameEpisode() {
        val resumed = Media("jf:ep1", "series", "Show", jellyfinId = "ep1", seriesName = "Show", progress = 50f)
        val next = resumed.copy(id = "jf:ep2", jellyfinId = "ep2", progress = 0f)
        val another = next.copy(id = "jf:ep3", jellyfinId = "ep3", seriesName = "Another")
        assertEquals(listOf(resumed, another), combineContinueItems(listOf(resumed), listOf(next, another, another)))
    }
    @Test fun episodeIdentitySurvivesCacheAndBackupSerialization() {
        val media = Media("jf:ep1", "series", "Episode", seriesName = "Show", episodeSeason = 2, episodeNumber = 4)
        assertEquals(media, Media.fromJson(media.json()))
        assertEquals("Show", media.copy(seriesName = "", episodeSeason = 0).enrichedBy(media).seriesName)
    }
}
