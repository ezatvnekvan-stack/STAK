package app.stak.nativeapp

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private fun legacy(watchlist: JSONArray = JSONArray(), tracking: JSONArray = JSONArray()) = JSONObject()
        .put("format", "dmm-backup").put("version", 1).put("watchlist", watchlist).put("tracking", tracking)
    private fun title(id: String = "tt11280740") = JSONObject().put("imdb_id", id).put("type", "series").put("name", "Severance")

    @Test fun importsExistingStakWithoutMixingMovieAndSeriesIdentity() {
        val list = JSONArray().put(title()).put(title().put("type", "movie"))
        val result = BackupCodec.decode(legacy(list).toString())
        assertEquals(2, result.size)
        assertEquals(setOf("series:tt11280740", "movie:tt11280740"), result.map { it.mediaKey }.toSet())
    }
    @Test fun ignoresInvalidIdentifiers() {
        val list = JSONArray().put(title("https://unexpected.invalid")).put(title("../../local")).put(title())
        assertEquals(1, BackupCodec.decode(legacy(list).toString()).size)
    }
    @Test fun duplicateImportsHaveOneEntryPerCollection() {
        val list = JSONArray().put(title()).put(title())
        assertEquals(2, BackupCodec.decode(legacy(list, list).toString()).size)
    }
    @Test fun rejectsUnknownVersionsAndFormats() {
        for (root in listOf(legacy().put("version", 2), legacy().put("format", "other"), legacy().remove("tracking").let { JSONObject().put("format", "dmm-backup").put("version", 1) })) {
            assertThrows(Exception::class.java) { BackupCodec.decode(root.toString()) }
        }
    }
    @Test fun rejectsOversizedBackupsBeforeParsing() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("x".repeat(2 * 1024 * 1024 + 1)) }
    }
    @Test fun credentialFieldsAreRemovedOnImportAndExportRoundTrip() {
        val unsafe = title().put("api_key", "secret").put("token", "secret").put("password", "secret")
        val imported = BackupCodec.decode(legacy(JSONArray().put(unsafe)).toString())
        val output = BackupCodec.encode(imported, emptyList())
        assertFalse(output.contains("secret"))
        assertFalse(output.contains("password"))
        assertEquals(imported.map { it.mediaKey }, BackupCodec.decode(output).map { it.mediaKey })
    }
    @Test fun nativeBackupKeepsEpisodeCoordinatesAndTimestamps() {
        val episode = WatchedEpisode("series:tt11280740", 2, 3, 123456789L)
        val root = JSONObject(BackupCodec.encode(emptyList(), listOf(episode)))
        val result = root.getJSONArray("episodes").getJSONObject(0)
        assertEquals(2, result.getInt("season")); assertEquals(3, result.getInt("episode")); assertEquals(123456789L, result.getLong("watchedAt"))
    }
    @Test fun clampsInvalidPlaybackProgress() {
        assertEquals(100f, Media.fromJson(title().put("progress", 900)).progress)
        assertEquals(0f, Media.fromJson(title().put("progress", -10)).progress)
    }
    @Test fun importingPartialPlaybackDoesNotMarkTitleComplete() {
        val progress = title().put("played", false).put("progress", 42)
        val result = BackupCodec.decode(legacy(tracking = JSONArray().put(progress)).toString()).single()
        assertFalse(JSONObject(result.json).getBoolean("played"))
        assertEquals(42f, result.media().progress)
    }
    @Test fun summaryCacheKeepsLoadedEpisodesAndDoesNotCopyPlaybackState() {
        val full = Media("tt11280740", "series", "Severance", description = "Loaded overview", jellyfinId = "old-server-id", progress = 90f,
            episodes = listOf(Episode("tt11280740:1:1", 1, 1, "First", "2026-01-01T00:00:00Z", "", "")))
        val summary = Media(full.id, full.type, full.name)
        val result = summary.enrichedBy(full)
        assertEquals(full.episodes, result.episodes)
        assertEquals(full.description, result.description)
        assertEquals("", result.jellyfinId)
        assertEquals(0f, result.progress)
        assertTrue(summary.copy(id = "tt1234").enrichedBy(full).episodes.isEmpty())
    }
}
