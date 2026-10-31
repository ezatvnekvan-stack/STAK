package app.stak.nativeapp

import org.json.*
import org.junit.Assert.*
import org.junit.Test

class BackupArchiveTest {
    private fun root() = JSONObject(BackupCodec.encode(emptyList(), emptyList())).put("version", 2)

    @Test
    fun portableArchiveKeepsAllowedSettingsSourcesAndListsButIgnoresTorboxKey() {
        val root =
            root()
                .put(
                    "settings",
                    JSONObject()
                        .put("home_order", "watchlist,continue")
                        .put("reduced", true)
                        .put("hash_lists_other_account", "private"),
                )
                .put(
                    "connections",
                    JSONObject().put("torbox_token", "never-import").put("tmdb_key", "test-only"),
                )
                .put(
                    "indexers",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("id", "test")
                                .put("kind", "stremio")
                                .put("url", "https://example.invalid/manifest.json")
                        ),
                )
        val archive = parseBackupArchive(root.toString())
        assertEquals(setOf("home_order", "reduced"), archive.settings.keys)
        assertEquals(mapOf("tmdb_key" to "test-only"), archive.connections)
        assertEquals(1, archive.indexers.size)
    }

    @Test
    fun incompatiblePreferenceTypeFailsBeforeRestore() {
        val root = root().put("settings", JSONObject().put("reduced", "bad-type"))
        assertThrows(IllegalArgumentException::class.java) { parseBackupArchive(root.toString()) }
    }

    @Test
    fun episodeRowsValidateIdentityAndKeepOriginalWatchDate() {
        val rows =
            JSONArray()
                .put(
                    JSONObject()
                        .put("mediaKey", "series:tt11280740")
                        .put("season", 2)
                        .put("episode", 3)
                        .put("watchedAt", 1234)
                )
                .put(JSONObject().put("mediaKey", "../../local").put("season", 2).put("episode", 3))
        val archive = parseBackupArchive(root().put("episodes", rows).toString())
        assertEquals(listOf(WatchedEpisode("series:tt11280740", 2, 3, 1234)), archive.episodes)
    }

    @Test
    fun rejectsCredentialBearingUrlsAndInvalidHashLists() {
        val root =
            root()
                .put(
                    "indexers",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("id", "test")
                                .put("kind", "stremio")
                                .put("url", "https://name:password@example.invalid/manifest.json")
                        ),
                )
        assertThrows(IllegalArgumentException::class.java) { parseBackupArchive(root.toString()) }
        root.remove("indexers")
        root.put(
            "hash_lists",
            JSONArray()
                .put(
                    JSONObject()
                        .put("id", "test")
                        .put("items", JSONArray().put(JSONObject().put("hash", "invalid")))
                ),
        )
        assertThrows(IllegalArgumentException::class.java) { parseBackupArchive(root.toString()) }
    }
}
