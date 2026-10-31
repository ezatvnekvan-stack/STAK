package app.stak.nativeapp

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JellyfinSyncTest {
    private val app
        get() = ApplicationProvider.getApplicationContext<Application>()

    private val requests = CopyOnWriteArrayList<Request>()
    private var played = true
    private var failPage = false
    private var rejectSecondEpisode = false
    private val session =
        JellyfinConnection(
            "https://example.invalid/jellyfin",
            "test-only-session",
            "test-user",
            "local",
        )

    private fun movie(i: Int) =
        JSONObject()
            .put("Id", "m$i")
            .put("Type", "Movie")
            .put("Name", "Test movie $i")
            .put("ProviderIds", JSONObject().put("Imdb", "tt${i.toString().padStart(7, '0')}"))
            .put(
                "UserData",
                JSONObject()
                    .put("Played", i == 0 && played)
                    .put("LastPlayedDate", "2026-10-20T12:00:00Z"),
            )

    private fun series() =
        JSONObject()
            .put("Id", "test-series")
            .put("Type", "Series")
            .put("Name", "Test series")
            .put("ProviderIds", JSONObject().put("Imdb", "tt11280740"))

    private fun episode(number: Int) =
        JSONObject()
            .put("Id", "e$number")
            .put("Type", "Episode")
            .put("SeriesId", "test-series")
            .put("ParentIndexNumber", 1)
            .put("IndexNumber", number)
            .put(
                "UserData",
                JSONObject()
                    .put("Played", number == 1 && played)
                    .put("LastPlayedDate", "2026-10-19T12:00:00Z"),
            )

    private fun repository(): Repository {
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    requests += request
                    val path = request.url.encodedPath
                    val start = request.url.queryParameter("StartIndex")?.toIntOrNull() ?: 0
                    val includeEpisodes =
                        request.url.queryParameter("IncludeItemTypes")?.contains("Episode") == true
                    val all =
                        if (path.contains("/Shows/")) listOf(episode(1), episode(2))
                        else
                            (0..300).map(::movie) +
                                series() +
                                if (includeEpisodes) listOf(episode(1), episode(2)) else emptyList()
                    val rejected =
                        (failPage && start > 0) ||
                            (rejectSecondEpisode && path.endsWith("/PlayedItems/e2"))
                    val root =
                        if (path.contains("/PlayedItems/")) JSONObject()
                        else
                            JSONObject()
                                .put("Items", JSONArray(all.drop(start).take(250)))
                                .put("TotalRecordCount", all.size)
                    Response.Builder()
                        .request(request)
                        .code(if (rejected) 503 else 200)
                        .message("TEST ONLY")
                        .protocol(Protocol.HTTP_1_1)
                        .body(root.toString().toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
        return Repository(app, client)
    }

    private fun connect(repository: Repository) {
        repository.credentials.set("torbox_token", "test-only-jellyfin-sync-owner")
        repository.credentials.set("jellyfin_url", session.url)
        repository.credentials.set("jellyfin_user", session.user)
        repository.credentials.set("jellyfin_token", session.token)
    }

    @Test
    fun pagesBeyond300SyncsRealDatesAndPreservesManualRecords() = runBlocking {
        val repository = repository()
        val original = repository.credentials.get("torbox_token")
        try {
            connect(repository)
            withContext(Dispatchers.IO) { repository.db.clearAllTables() }
            val library = repository.library()
            assertEquals(302, library.size)
            assertTrue(requests.any { it.url.queryParameter("StartIndex") == "250" })
            val manual = Media("tt0000001", "movie", "Manual completion")
            repository.dao.put(
                SavedMedia(
                    manual.key,
                    "tracking",
                    manual.json().put("played", true).put("progress", 100).toString(),
                    9876,
                )
            )
            repository.syncJellyfin()
            assertEquals(1, repository.dao.allEpisodes().size)
            val date = java.time.Instant.parse("2026-10-19T12:00:00Z").toEpochMilli()
            assertEquals(date, repository.dao.allEpisodes().single().watchedAt)
            assertEquals(
                50f,
                repository.dao.all().first { it.mediaKey == "series:tt11280740" }.media().progress,
            )
            assertEquals(9876L, repository.dao.all().first { it.mediaKey == manual.key }.updatedAt)
            repository.dao.putEpisode(WatchedEpisode("series:tt11280740", 1, 2, 12345))
            played = false
            repository.syncJellyfin()
            assertEquals(2, repository.dao.allEpisodes().single().episode)
            assertEquals(12345L, repository.dao.allEpisodes().single().watchedAt)
            assertTrue(repository.dao.all().any { it.mediaKey == "series:tt11280740" })
            assertTrue(repository.dao.all().none { it.mediaKey == "movie:tt0000000" })
            assertTrue(
                requests.all {
                    it.header("X-Emby-Token") == session.token &&
                        it.header("Authorization")?.contains("Bearer") != true
                }
            )
        } finally {
            withContext(Dispatchers.IO) { repository.db.clearAllTables() }
            repository.credentials.set("torbox_token", original)
            repository.db.close()
        }
    }

    @Test
    fun failedPageLeavesLocalDatabaseUntouched() = runBlocking {
        val repository = repository()
        val original = repository.credentials.get("torbox_token")
        try {
            connect(repository)
            withContext(Dispatchers.IO) { repository.db.clearAllTables() }
            val media = Media("tt11280740", "series", "Before failed sync")
            repository.dao.put(SavedMedia(media.key, "watchlist", media.json().toString(), 4567))
            failPage = true
            assertThrows(Exception::class.java) { runBlocking { repository.syncJellyfin() } }
            assertEquals(4567L, repository.dao.all().single().updatedAt)
            assertTrue(repository.dao.allEpisodes().isEmpty())
        } finally {
            withContext(Dispatchers.IO) { repository.db.clearAllTables() }
            repository.credentials.set("torbox_token", original)
            repository.db.close()
        }
    }

    @Test
    fun partialRemoteSeasonFailureDoesNotPretendTheLocalChangeSucceeded() = runBlocking {
        val repository = repository()
        val original = repository.credentials.get("torbox_token")
        try {
            connect(repository)
            withContext(Dispatchers.IO) { repository.db.clearAllTables() }
            rejectSecondEpisode = true
            val vm = StakViewModel(app, repository)
            val title = Media("tt11280740", "series", "Test series", jellyfinId = "test-series")
            val episodes = (1..2).map { Episode("public:1:$it", 1, it, "Episode $it", "", "", "") }
            val failure =
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { vm.changeEpisodesWatched(title, episodes, true) }
                }
            assertTrue(failure.message!!.contains("1 of 2"))
            assertTrue(repository.dao.allEpisodes().isEmpty())
            assertTrue(
                requests.any {
                    it.url.encodedPath.endsWith("/PlayedItems/e1") && it.method == "POST"
                }
            )
        } finally {
            withContext(Dispatchers.IO) { repository.db.clearAllTables() }
            repository.credentials.set("torbox_token", original)
            repository.db.close()
        }
    }
}
