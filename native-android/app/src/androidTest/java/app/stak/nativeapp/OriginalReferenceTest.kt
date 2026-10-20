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

/** Identical public TEST ONLY records and artwork to scripts/browser-smoke.cjs.
 * These captures expose differences; they never assert or certify pixel parity. */
@androidx.test.filters.SdkSuppress(minSdkVersion = 30)
@RunWith(AndroidJUnit4::class)
class OriginalReferenceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: StakViewModel
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private val year = "2026"
    @Test fun captureOriginalReferenceRoutesInBothThemes() {
        val picture = instrument.context.assets.open("provider-fixture.png").readBytes()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); val path = request.url.encodedPath
            val body: ResponseBody = if (request.url.host == "image.tmdb.org") picture.toResponseBody("image/png".toMediaType()) else response(path).toString().toResponseBody("application/json".toMediaType())
            Response.Builder().request(request).code(200).message("TEST ONLY fixture").protocol(Protocol.HTTP_1_1).body(body).build()
        }.build()
        val app = compose.activity.application
        vm = StakViewModel(app, Repository(app, client))
        runBlocking(Dispatchers.IO) { vm.repository.db.clearAllTables(); vm.repository.metadataDirectory.listFiles()?.forEach { it.delete() } }
        vm.repository.credentials.set("tmdb_key", "test-fixture-not-a-credential")
        vm.repository.credentials.set("torbox_token", "test-fixture-not-a-credential")
        listOf("jellyfin_url", "jellyfin_token", "jellyfin_user").forEach { vm.repository.credentials.set(it, "") }
        Coil.setImageLoader(ImageLoader.Builder(app).okHttpClient(client).build())
        compose.runOnUiThread { vm.appearance("reduced", true); compose.activity.setContent { StakApp(vm) } }
        for (theme in listOf("dark", "light")) {
            compose.runOnUiThread { vm.option("theme", theme) }
            compose.onNodeWithTag("nav-home").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithText("Test title").fetchSemanticsNodes().isNotEmpty() }
            capture("home", theme)
            compose.onNodeWithTag("nav-discover").performClick(); compose.waitUntil(15000) { compose.onAllNodesWithText("Test title").fetchSemanticsNodes().isNotEmpty() }; capture("discover", theme)
            compose.onNodeWithTag("nav-watchlist").performClick(); capture("watchlist", theme)
            compose.onNodeWithTag("nav-tracking").performClick(); capture("tracking", theme)
            compose.onNodeWithTag("nav-library").performClick(); capture("library", theme)
            compose.onNodeWithTag("nav-settings").performClick(); capture("settings", theme)
            compose.onNodeWithTag("settings-appearance").performClick(); capture("appearance", theme)
            compose.onNodeWithText("All settings").performClick()
            compose.onNodeWithTag("settings-jellyfin").performClick(); capture("jellyfin", theme); compose.onNodeWithText("All settings").performClick()
            compose.onNodeWithTag("nav-home").performClick()
        }
        listOf("tmdb_key", "torbox_token").forEach { vm.repository.credentials.set(it, "") }
    }
    private fun movie(id: Int = 603) = JSONObject().put("id", id).put("title", "Test title").put("name", "Test title").put("overview", "A browser test title.").put("release_date", "$year-04-01").put("first_air_date", "$year-04-01").put("vote_average", 8).put("vote_count", 200).put("poster_path", "/p.jpg").put("backdrop_path", "/b.jpg").put("genre_ids", JSONArray(listOf(18)))
    private fun response(path: String): JSONObject = when {
        path.endsWith("/configuration") -> JSONObject().put("images", JSONObject().put("secure_base_url", "https://image.tmdb.org/t/p/"))
        path.contains("/catalog/") -> JSONObject().put("metas", JSONArray())
        path.contains("/discover/") || path.contains("/trending/") -> JSONObject().put("results", JSONArray((603..611).map { movie(it) })).put("page", 1).put("total_pages", 2)
        path.endsWith("/user/me") -> JSONObject().put("success", true).put("data", JSONObject().put("email", "test-only@example.invalid"))
        else -> JSONObject()
    }
    private fun capture(route: String, theme: String) {
        compose.waitForIdle()
        Thread.sleep(400) // Image decode settles; reduced motion keeps frames static.
        val full = instrument.uiAutomation.takeScreenshot()
        val insets = compose.activity.window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.systemBars())
        val bitmap = Bitmap.createBitmap(full, insets.left, insets.top, full.width - insets.left - insets.right, full.height - insets.top - insets.bottom)
        val density = compose.activity.resources.displayMetrics.density
        val width = (bitmap.width / density).toInt()
        val height = (bitmap.height / density).toInt()
        val filename = "$route-$width-$theme.png"
        val normalized = Bitmap.createScaledBitmap(bitmap, width, height, false)
        val resolver = compose.activity.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/STAK-native-evidence")
        }
        val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        requireNotNull(resolver.openOutputStream(uri)).use { normalized.compress(Bitmap.CompressFormat.PNG, 100, it) }
        android.util.Log.i("STAK-REFERENCE", "$filename: ${width}x$height dp, density $density; crop top ${insets.top}, bottom ${insets.bottom}")
        normalized.recycle(); bitmap.recycle(); full.recycle()
    }
}
