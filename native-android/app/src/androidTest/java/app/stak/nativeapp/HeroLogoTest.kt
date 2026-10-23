package app.stak.nativeapp

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.ImageLoader
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeroLogoTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun heroDisplaysProviderLogoAndFallsBackWhenArtworkFails() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val picture = instrumentation.context.assets.open("provider-fixture.png").use { it.readBytes() }
        val loaded = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val failed = chain.request().url.encodedPath.contains("missing")
            if (!failed) loaded.incrementAndGet()
            Response.Builder().request(chain.request()).code(if (failed) 404 else 200)
                .message("FIXTURE").protocol(Protocol.HTTP_1_1).body(picture.toResponseBody()).build()
        }.build()
        Coil.setImageLoader(ImageLoader.Builder(compose.activity).okHttpClient(client).build())
        val title = Media("tt5555000", "movie", "Hero logo fixture", logo = "https://image.tmdb.org/fixture-logo.png")
        compose.runOnUiThread { compose.activity.setContent { HomeHeroBrand(title) } }
        compose.waitUntil(10000) { loaded.get() > 0 }
        compose.waitUntil(10000) { compose.onAllNodesWithText(title.name).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("home-hero-logo").assertIsDisplayed()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        saveEvidence("home-hero-logo.png", bitmap)
        bitmap.recycle()
        compose.runOnUiThread { compose.activity.setContent { HomeHeroBrand(title.copy(logo = "https://image.tmdb.org/missing-logo.png")) } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("home-hero-logo").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(title.name).assertIsDisplayed()
        compose.runOnUiThread { compose.activity.setContent { HomeHeroBrand(title.copy(logo = "")) } }
        compose.onNodeWithText(title.name).assertIsDisplayed()
    }

    @Test fun concurrentPublicMetadataReadsUseOneLoadAndRefreshStillFetches() = runBlocking {
        val repository = Repository(compose.activity)
        val key = "coalesced-fixture-${System.nanoTime()}"
        val calls = AtomicInteger()
        suspend fun read(maxAge: Long = 60_000) = repository.publicMetadata(key, maxAge) {
            calls.incrementAndGet()
            delay(60)
            JSONObject().put("value", calls.get())
        }
        val found = coroutineScope { List(12) { async { read().getInt("value") } }.awaitAll() }
        assertEquals(List(12) { 1 }, found)
        assertEquals(1, calls.get())
        assertEquals(2, read(0).getInt("value"))
        assertEquals(2, calls.get())
    }
}
