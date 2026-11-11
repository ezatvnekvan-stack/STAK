@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class Cinematic154UiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "cinematic154-${UUID.randomUUID()}-"
    private var previous: SettingsStore? = null
    @Before fun before() {
        previous = runCatching { NativeRuntime.settings }.getOrNull()
        NativeRuntime.settings = SettingsStore(context, prefix).apply { ratingsEnabled = false; reducedMotion = true }
    }
    @After fun after() {
        previous?.let { NativeRuntime.settings = it }
        listOf("stak_settings", "stak_secrets").forEach { context.deleteSharedPreferences(prefix + it) }
        context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith(prefix) }.forEach { it.delete() }
    }
    private fun art(): String {
        val f = File(context.cacheDir, "${prefix}art.png")
        val b = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(0f, 0f, 800f, 1000f,
            intArrayOf(0xff40677a.toInt(), 0xff172e3d.toInt(), 0xff091018.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
        c.drawPaint(paint); paint.shader = null; paint.color = 0xff8eafbc.toInt()
        c.drawCircle(400f, 320f, 170f, paint)
        paint.color = 0xff0a1c27.toInt(); c.drawRect(310f, 340f, 490f, 1000f, paint)
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
        return f.toURI().toString()
    }
    private fun media(series: Boolean = false): MediaItem {
        val image = art()
        return MediaItem(if (series) "tt1540002" else "tt1540001", name = if (series) "Distant Signals" else "Night Signal",
            type = if (series) "series" else "movie", year = "2026", runtime = if (series) null else "117 min",
            certification = if (series) "TV-MA" else "PG-13", poster = image, background = image,
            genres = listOf("Mystery", "Drama"), overview = "An unexpected signal leads a distant crew into the unknown. ".repeat(5))
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(200)
        compose.waitForIdle()
        val dir = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: context.getExternalFilesDir(null)!!.path, "cinematic154").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun outlineSelectorHasEqualTouchTargetsAndKeepsSelectionAtLargeText() {
        compose.setContent { StakTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalReducedMotion provides true, LocalDensity provides Density(density.density, 1.5f)) {
                var selected by remember { mutableStateOf("all") }
                Box(Modifier.fillMaxSize().background(StakBackground).statusBarsPadding().padding(16.dp)) {
                    MediaTypeSelector(listOf("all" to "All", "movie" to "Movies", "series" to "Series"), selected, { selected = it })
                }
            }
        } }
        compose.onNodeWithTag("media-type-all").assertIsSelected()
        compose.onNodeWithTag("media-type-series").performClick().assertIsSelected()
        compose.onNodeWithTag("media-type-all").assertIsNotSelected()
        val film = compose.onNodeWithTag("media-type-movie").getUnclippedBoundsInRoot()
        val series = compose.onNodeWithTag("media-type-series").getUnclippedBoundsInRoot()
        assertTrue(film.height >= 44.dp); assertEquals(film.width.value, series.width.value, 1f)
        compose.onNodeWithText("MOVIES").assertIsDisplayed(); capture("outline-large-text")
    }
    @Test fun sharedClassificationStampsRetainProviderLabelsAndHideMissingValues() {
        compose.setContent { StakTheme { Row(Modifier.statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("PG-13", "R", "TV-MA", null).forEach { CertificationBadge(it) }
        } } }
        compose.onAllNodesWithTag("certification-badge").assertCountEquals(3)
        listOf("PG-13", "R", "TV-MA").forEach { compose.onNodeWithContentDescription("Age rating $it").assertIsDisplayed() }
        capture("classification-stamps")
    }
    @Test fun homeArtworkReachesEdgesWithoutHeaderControls() {
        val item = media(); lateinit var vm: HomeViewModel; lateinit var search: LiveSearchViewModel
        compose.runOnUiThread { vm = HomeViewModel(feedLoader = { HomeFeed(hero = listOf(item)) }); search = LiveSearchViewModel { emptyList() } }
        compose.setContent { StakTheme { CompositionLocalProvider(LocalReducedMotion provides true) {
            HomeScreen(onOpenTitle = { _, _ -> }, onSearch = {}, onOpenAccount = {}, onOpenLibrary = {}, onOpenWatchlist = {}, onBrowseSection = {},
                torboxConnected = false, sharedScope = null, animatedScope = null, reducedMotion = true, viewModel = vm, searchViewModel = search)
        } } }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("home-hero").fetchSemanticsNodes().isNotEmpty() }
        val hero = compose.onNodeWithTag("home-hero").getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertEquals(root.left.value, hero.left.value, 1f); assertEquals(root.right.value, hero.right.value, 1f)
        val screenHeight=androidx.compose.ui.unit.Dp(context.resources.configuration.screenHeightDp.toFloat())
        assertEquals(screenHeight.value*.5f,hero.height.value,2f)
        compose.onNodeWithContentDescription("Age rating PG-13", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("VIEW DETAILS").assertDoesNotExist(); capture("home-edge-to-edge")
        compose.onNodeWithTag("home-search-toggle").assertDoesNotExist()
        compose.onNodeWithText(item.overview!!).assertDoesNotExist()
        compose.onNodeWithTag("home-hero").assertIsDisplayed()
    }
    @Test fun movieUsesCenteredFactsAndVisibleSeparateActionRows() {
        val item = media(); var saves = 0; var writes = 0
        val vm = TitleViewModel(detailsLoader = { _, _ -> TitleDetails(item, certification = "PG-13", imdbRating = 8.2) }, sourceLoader = { _, _, _ -> emptyList() })
        compose.setContent { StakTheme { CompositionLocalProvider(LocalReducedMotion provides true) {
            TitleScreen(item, {}, false, false, { saves++ }, null, null, onMarkWatched = { _, _ -> writes++ }, viewModel = vm)
        } } }
        compose.waitUntil(5000) { vm.detail.value != null }
        compose.onNodeWithContentDescription("IMDb 8.2").assertIsDisplayed()
        val artwork = compose.onNodeWithTag("detail-hero-artwork").getUnclippedBoundsInRoot()
        val facts = compose.onNodeWithTag("title-facts").getUnclippedBoundsInRoot()
        val rating = compose.onNodeWithTag("title-rating-slot").getUnclippedBoundsInRoot()
        assertTrue(artwork.height >= 280.dp); assertTrue(rating.top >= facts.top)
        assertTrue(rating.bottom <= facts.bottom)
        val genres = compose.onNodeWithTag("title-genres").getUnclippedBoundsInRoot()
        assertTrue(genres.bottom <= facts.top)
        val source = compose.onNodeWithTag("detail-source-button").getUnclippedBoundsInRoot()
        val trailer = compose.onNodeWithContentDescription("Trailer").getUnclippedBoundsInRoot()
        assertTrue(trailer.top - source.bottom >= 11.dp)
        compose.onNodeWithContentDescription("Add to watchlist").performClick(); assertEquals(1, saves); assertEquals(0, writes)
        compose.onNodeWithText("SERIES CENTER").assertDoesNotExist(); capture("movie-details")
    }
    @Test fun seriesKeepsAutoAddAndSeasonSpecificEpisodes() {
        val item = media(true)
        val first = Episode(1, 1, "First transmission", image = item.background)
        val second = Episode(2, 1, "A new chapter", image = item.background)
        val vm = TitleViewModel(detailsLoader = { _, _ -> TitleDetails(item, imdbRating = 8.3, seasons = listOf(1, 2), episodes = listOf(first)) },
            sourceLoader = { _, _, _ -> emptyList() }, seasonLoader = { _, season -> listOf(if (season == 2) second else first) })
        compose.setContent { StakTheme { CompositionLocalProvider(LocalReducedMotion provides true) {
            TitleScreen(item, {}, false, false, {}, null, null, viewModel = vm)
        } } }
        compose.waitUntil(5000) { vm.detail.value != null }
        capture("series-details")
        compose.onNodeWithTag("detail-content").performScrollToNode(hasContentDescription("Auto-add settings"))
        compose.onNodeWithContentDescription("Auto-add settings").assertIsDisplayed()
        compose.onNodeWithTag("detail-content").performScrollToNode(hasContentDescription("Choose season"))
        compose.onNodeWithContentDescription("Choose season").performClick()
        compose.onNodeWithText("Season 2").performClick()
        compose.waitUntil(5000) { vm.season.value == 2 && !vm.seasonLoading.value }
        compose.onNodeWithTag("detail-content").performScrollToNode(hasText("A new chapter"))
        compose.onNodeWithText("A new chapter").assertIsDisplayed()
        compose.onNodeWithText("First transmission").assertDoesNotExist()
        capture("series-season-two")
    }
    @Test fun ageFilterUsesTheSameStampAndStillSelectsAndClears() {
        compose.setContent { StakTheme { Column(Modifier.statusBarsPadding().padding(16.dp)) {
            var value by remember { mutableStateOf("PG-13") }
            DiscoverySelect("Age rating", value, listOf("" to "Any", "PG-13" to "PG-13", "R" to "R"), { value = it }, classification = true)
            if(value.isNotBlank())CertificationFilterChip(value) { value = "" }
        } } }
        compose.onNodeWithContentDescription("Age rating", useUnmergedTree = true).performClick()
        compose.onNodeWithText("R").performClick()
        compose.onNodeWithContentDescription("Clear age rating R").performClick()
        compose.onNodeWithText("Any").assertIsDisplayed()
        compose.onAllNodesWithTag("certification-badge").assertCountEquals(0)
    }

}
