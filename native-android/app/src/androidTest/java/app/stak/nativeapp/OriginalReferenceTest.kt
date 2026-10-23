package app.stak.nativeapp

import android.graphics.Bitmap
import android.view.WindowInsets
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.ImageLoader
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.*
import org.junit.*
import org.junit.runner.RunWith

/**
 * Identical public TEST ONLY records and artwork to scripts/browser-smoke.cjs. These captures
 * expose differences; they never assert or certify pixel parity.
 */
@androidx.test.filters.SdkSuppress(minSdkVersion = 30)
@RunWith(AndroidJUnit4::class)
class OriginalReferenceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: StakViewModel
    private val instrument
        get() = InstrumentationRegistry.getInstrumentation()

    private val year = "2026"

    @Test
    fun captureOriginalReferenceRoutesInBothThemes() {
        val picture = instrument.context.assets.open("provider-fixture.png").readBytes()
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val path = request.url.encodedPath
                    val body: ResponseBody =
                        if (request.url.host == "image.tmdb.org")
                            picture.toResponseBody("image/png".toMediaType())
                        else
                            response(path, request.url.queryParameter("query").orEmpty())
                                .toString()
                                .toResponseBody("application/json".toMediaType())
                    Response.Builder()
                        .request(request)
                        .code(200)
                        .message("TEST ONLY fixture")
                        .protocol(Protocol.HTTP_1_1)
                        .body(body)
                        .build()
                }
                .build()
        val app = compose.activity.application
        vm =
            StakViewModel(
                app,
                Repository(
                    app,
                    client,
                    java.time.Clock.fixed(
                        java.time.Instant.parse("2026-10-20T12:00:00Z"),
                        java.time.ZoneOffset.UTC,
                    ),
                ),
            )
        runBlocking(Dispatchers.IO) {
            vm.repository.db.clearAllTables()
            vm.repository.metadataDirectory.listFiles()?.forEach { it.delete() }
        }
        vm.repository.credentials.set("tmdb_key", "test-fixture-not-a-credential")
        vm.repository.credentials.set("torbox_token", "test-fixture-not-a-credential")
        listOf("jellyfin_url", "jellyfin_token", "jellyfin_user").forEach {
            vm.repository.credentials.set(it, "")
        }
        Coil.setImageLoader(ImageLoader.Builder(app).okHttpClient(client).build())
        // Match browser prefers-reduced-motion without changing the app's saved switch.
        instrument.uiAutomation
            .executeShellCommand("settings put global animator_duration_scale 0")
            .close()
        compose.runOnUiThread { vm.appearance("reduced", false) }
        vm.repository.remember(
            Media(
                "tt0903747",
                "series",
                "Test title",
                poster = "https://image.tmdb.org/t/p/w500/p.jpg",
                background = "https://image.tmdb.org/t/p/w1280/b.jpg",
                year = "2026",
            )
        )
        vm.repository.remember(
            Media(
                "tt0133093",
                "movie",
                "Test title",
                poster = "https://image.tmdb.org/t/p/w500/p.jpg",
                background = "https://image.tmdb.org/t/p/w1280/b.jpg",
                year = "2026",
            )
        )
        val routes =
            linkedMapOf(
                "home" to "home",
                "homeSearch" to "home?q=Test%20title",
                "homeSearchEmpty" to "home?q=stak-no-match",
                "discover" to "discover",
                "watchlist" to "watchlist",
                "tracking" to "tracking",
                "library" to "library",
                "settings" to "settings",
                "appearance" to "settings?section=appearance",
                "metadata" to "settings?section=metadata",
                "sources" to "settings?section=sources",
                "account" to "settings?section=account",
                "jellyfin" to "settings?section=jellyfin",
                "homeSettings" to "settings?section=home",
                "advanced" to "settings?section=advanced",
                "remote" to "remote",
                "shares" to "shares",
                "torbox" to "torbox",
                "calendar" to "tracking?view=calendar",
                "person" to "person/42",
                "movie" to "title/movie%3Att0133093/reference",
                "title" to "title/series%3Att0903747/reference",
            )
        for (theme in listOf("dark", "light")) {
            compose.runOnUiThread { vm.option("theme", theme) }
            for ((name, route) in routes) {
                compose.runOnUiThread {
                    compose.activity.setContent {
                        androidx.compose.runtime.key(name, theme) { StakApp(vm, route) }
                    }
                }
                compose.waitForIdle()
                Thread.sleep(1000)
                capture(name, theme)
            }
        }
        listOf("tmdb_key", "torbox_token").forEach { vm.repository.credentials.set(it, "") }
    }

    @After
    fun restoreSystemMotion() {
        instrument.uiAutomation
            .executeShellCommand("settings put global animator_duration_scale 1")
            .close()
    }

    private fun movie(id: Int = 603) =
        JSONObject()
            .put("id", id)
            .put("title", "Test title")
            .put("name", "Test title")
            .put("overview", "A browser test title.")
            .put("release_date", "$year-04-01")
            .put("first_air_date", "$year-04-01")
            .put("vote_average", 8)
            .put("vote_count", 200)
            .put("poster_path", "/p.jpg")
            .put("backdrop_path", "/b.jpg")
            .put("genre_ids", JSONArray(listOf(18)))

    private fun response(path: String, query: String = ""): JSONObject =
        when {
            path == "/3/search/multi" ->
                JSONObject()
                    .put("page", 1)
                    .put("total_pages", 1)
                    .put(
                        "results",
                        JSONArray(
                            if (query == "stak-no-match") emptyList<JSONObject>()
                            else
                                (0..7).map {
                                    movie(700 + it)
                                        .put("media_type", if (it % 2 == 1) "tv" else "movie")
                                }
                        ),
                    )
            path.endsWith("/external_ids") ->
                JSONObject()
                    .put(
                        "imdb_id",
                        if (path.contains("/movie/603/")) "tt0133093"
                        else if (path.contains("/tv/1396/")) "tt0903747"
                        else
                            "tt" +
                                (if (path.contains("/tv/")) "1" else "0") +
                                path.split('/')[3].padStart(6, '0'),
                    )
            path.startsWith("/3/find/") ->
                JSONObject()
                    .put(
                        if (path.endsWith("tt0903747")) "tv_results" else "movie_results",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("id", if (path.endsWith("tt0903747")) 1396 else 603)
                            ),
                    )
            path.contains("/season/") ->
                JSONObject()
                    .put(
                        "episodes",
                        JSONArray(
                            (1..12).map { n ->
                                JSONObject()
                                    .put("season_number", 1)
                                    .put("episode_number", n)
                                    .put(
                                        "name",
                                        if (n == 2)
                                            "Episode 2 with a very long release filename that must wrap consistently without changing the thumbnail alignment"
                                        else "Episode $n",
                                    )
                                    .put("air_date", "2025-01-01")
                                    .put("overview", "Complete test-only episode synopsis")
                                    .put("still_path", "/p.jpg")
                            }
                        ),
                    )
            Regex("/3/(tv|movie)/[0-9]+$").matches(path) ->
                movie(path.substringAfterLast('/').toInt())
                    .put("genres", JSONArray().put(JSONObject().put("id", 18).put("name", "Drama")))
                    .put(
                        "seasons",
                        JSONArray()
                            .put(JSONObject().put("season_number", 0))
                            .put(JSONObject().put("season_number", 1)),
                    )
                    .put(
                        "external_ids",
                        JSONObject()
                            .put("imdb_id", if (path.contains("/tv/")) "tt0903747" else "tt0133093"),
                    )
                    .put(
                        "created_by",
                        JSONArray().put(JSONObject().put("id", 42).put("name", "Test creator")),
                    )
                    .put(
                        "images",
                        JSONObject()
                            .put("logos", JSONArray())
                            .put(
                                "backdrops",
                                JSONArray().put(JSONObject().put("file_path", "/b.jpg")),
                            ),
                    )
                    .put(
                        "content_ratings",
                        JSONObject()
                            .put(
                                "results",
                                JSONArray()
                                    .put(
                                        JSONObject().put("iso_3166_1", "US").put("rating", "TV-MA")
                                    ),
                            ),
                    )
                    .put(
                        "release_dates",
                        JSONObject()
                            .put(
                                "results",
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("iso_3166_1", "US")
                                            .put(
                                                "release_dates",
                                                JSONArray()
                                                    .put(JSONObject().put("certification", "PG-13")),
                                            )
                                    ),
                            ),
                    )
                    .put("credits", JSONObject().put("cast", JSONArray()).put("crew", JSONArray()))
            path.startsWith("/3/person/") ->
                JSONObject()
                    .put("id", 42)
                    .put("name", "Test creator")
                    .put("biography", "Test-only creator biography")
                    .put(
                        "movie_credits",
                        JSONObject().put("cast", JSONArray()).put("crew", JSONArray()),
                    )
                    .put(
                        "tv_credits",
                        JSONObject().put("cast", JSONArray()).put("crew", JSONArray()),
                    )
            path.startsWith("/3/watch/providers/") ->
                JSONObject()
                    .put(
                        "results",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("provider_id", 8)
                                    .put("provider_name", "Netflix")
                                    .put("logo_path", "/n.jpg")
                            )
                            .put(
                                JSONObject()
                                    .put("provider_id", 119)
                                    .put("provider_name", "Amazon Prime Video")
                                    .put("logo_path", "/prime.jpg")
                            ),
                    )
            path.endsWith("/torrents/mylist") ->
                JSONObject()
                    .put("success", true)
                    .put(
                        "data",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("id", 1)
                                    .put("name", "Test-only ready torrent")
                                    .put("size", 1000000000)
                                    .put("progress", 1)
                                    .put("hash", "a".repeat(40))
                                    .put("download_finished", true)
                                    .put("download_present", true)
                                    .put(
                                        "files",
                                        JSONArray()
                                            .put(
                                                JSONObject()
                                                    .put("id", 1)
                                                    .put("name", "Test-only video.mkv")
                                                    .put("size", 1000000000)
                                            ),
                                    )
                            ),
                    )
            path.endsWith("/configuration") ->
                JSONObject()
                    .put(
                        "images",
                        JSONObject().put("secure_base_url", "https://image.tmdb.org/t/p/"),
                    )
            path.contains("/catalog/") -> JSONObject().put("metas", JSONArray())
            path.contains("/discover/") || path.contains("/trending/") ->
                JSONObject()
                    .put("results", JSONArray((603..611).map { movie(it) }))
                    .put("page", 1)
                    .put("total_pages", 2)
            path.endsWith("/user/me") ->
                JSONObject()
                    .put("success", true)
                    .put("data", JSONObject().put("email", "test-only@example.invalid"))
            else -> JSONObject()
        }

    private fun capture(route: String, theme: String) {
        compose.waitForIdle()
        Thread.sleep(400) // Image decode settles; reduced motion keeps frames static.
        val full = instrument.uiAutomation.takeScreenshot()
        val insets =
            compose.activity.window.decorView.rootWindowInsets.getInsets(
                WindowInsets.Type.systemBars()
            )
        val bitmap =
            Bitmap.createBitmap(
                full,
                insets.left,
                insets.top,
                full.width - insets.left - insets.right,
                full.height - insets.top - insets.bottom,
            )
        val density = compose.activity.resources.displayMetrics.density
        val width = (bitmap.width / density).toInt()
        val height = (bitmap.height / density).toInt()
        val filename = "$route-$width-$theme.png"
        val metricsDirectory = java.io.File(compose.activity.getExternalFilesDir(null), "parity-metrics").apply { mkdirs() }
        java.io.File(metricsDirectory, filename.removeSuffix(".png") + ".json").writeText(
            JSONObject().put("captureWidthDp", width).put("captureHeightDp", height)
                .put("density", density).put("fontScale", compose.activity.resources.configuration.fontScale)
                .put("configurationWidthDp", compose.activity.resources.configuration.screenWidthDp)
                .put("configurationHeightDp", compose.activity.resources.configuration.screenHeightDp)
                .put("windowWidthPx", compose.activity.window.decorView.width)
                .put("windowHeightPx", compose.activity.window.decorView.height)
                .toString(2)
        )
        val normalized = Bitmap.createScaledBitmap(bitmap, width, height, false)
        val resolver = compose.activity.contentResolver
        val values =
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/STAK-native-evidence",
                )
            }
        val uri =
            requireNotNull(
                resolver.insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values,
                )
            )
        requireNotNull(resolver.openOutputStream(uri)).use {
            normalized.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        android.util.Log.i(
            "STAK-REFERENCE",
            "$filename: ${width}x$height dp, density $density; crop top ${insets.top}, bottom ${insets.bottom}",
        )
        Assert.assertEquals(
            "Reference viewport height must match; never stretch a capture",
            880,
            height,
        )
        normalized.recycle()
        bitmap.recycle()
        full.recycle()
    }
}
