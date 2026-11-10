@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class GlyphBackgroundUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "glyph-test-${UUID.randomUUID()}-"
    private var previous: SettingsStore? = null
    @Before fun before() {
        previous = runCatching { NativeRuntime.settings }.getOrNull()
        NativeRuntime.settings = SettingsStore(context, prefix).apply { reducedMotion = true; ratingsEnabled = false }
    }
    @After fun after() {
        previous?.let { NativeRuntime.settings = it }
        listOf("stak_settings", "stak_secrets").forEach { context.deleteSharedPreferences(prefix + it) }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val dir = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: context.getExternalFilesDir(null)!!.path, "glyph-153").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun pixels(): IntArray {
        val image = compose.onNodeWithTag("glyph-background").captureToImage()
        val map = image.toPixelMap()
        return IntArray(image.width * image.height) { map[it % image.width, it / image.width].toArgb() }
    }
    @Test fun selectionPersistsAndSurvivesBackupRestoreWithoutChangingOtherSettings() {
        val store = NativeRuntime.settings
        assertEquals(BackgroundStyle.Current, store.backgroundStyle)
        store.haptics = false
        store.backgroundStyle = BackgroundStyle.Glyph
        assertEquals(BackgroundStyle.Glyph, SettingsStore(context, prefix).backgroundStyle)
        val backup = store.backupValues()
        store.backgroundStyle = BackgroundStyle.Current
        store.restoreBackupValues(backup, emptyMap())
        assertEquals(BackgroundStyle.Glyph, store.backgroundStyle)
        assertFalse(store.haptics)
        context.getSharedPreferences(prefix + "stak_settings", Context.MODE_PRIVATE)
            .edit().putString("background_style", "future").commit()
        assertEquals(BackgroundStyle.Current, store.backgroundStyle)
    }
    @Test fun pickerSwitchesBothWaysAndMetadataAndBadgesStayReadable() {
        compose.setContent {
            var style by remember { mutableStateOf(BackgroundStyle.Current) }
            StakTheme { CompositionLocalProvider(LocalReducedMotion provides true, LocalBackgroundStyle provides style) {
                Box(Modifier.fillMaxSize()) {
                    if (style == BackgroundStyle.Glyph) GlyphBackground()
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        BackgroundStylePicker(style) { style = it }
                        TitleMetadata(MediaItem("fixture", name = "Sample film", year = "2024", runtime = "117 min", genres = listOf("Drama", "Mystery")),
                            TitleDetails(MediaItem("fixture", name = "Sample film"), certification = "PG-13", imdbRating = 8.3))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("4K", "HDR", "Dolby Vision", "Atmos", "Remux").forEach { FormatBadge(it) }
                        }
                    }
                }
            } }
        }
        compose.onNodeWithTag("background-current").assertIsSelected()
        compose.onNodeWithTag("background-glyph").performClick().assertIsSelected()
        compose.onNodeWithTag("glyph-background").assertExists()
        compose.onNodeWithContentDescription("IMDb 8.3").assertIsDisplayed()
        compose.onNodeWithText("DOLBY VISION").assertIsDisplayed()
        capture("appearance-metadata-badges")
        compose.onNodeWithTag("background-current").performClick().assertIsSelected()
        compose.onNodeWithTag("glyph-background").assertDoesNotExist()
    }
    @Test fun oneShotSignalSettlesAndDoesNotReplayAfterScrollOrModal() {
        compose.mainClock.autoAdvance = false
        val signals = GlyphSignals()
        val motion = CinematicMotion()
        compose.setContent { StakTheme { CompositionLocalProvider(LocalReducedMotion provides false,
            LocalGlyphSignals provides signals, LocalCinematicMotion provides motion) { GlyphBackground() } } }
        compose.mainClock.advanceTimeBy(1200)
        val settled = pixels()
        compose.runOnUiThread { signals.added() }
        compose.mainClock.advanceTimeBy(192)
        assertFalse("Confirmed-success signal must draw a brief highlight", settled.contentEquals(pixels()))
        compose.mainClock.advanceTimeBy(1200)
        assertArrayEquals(settled, pixels())
        compose.runOnUiThread { motion.dialogs++ }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnUiThread { motion.dialogs-- }
        compose.mainClock.advanceTimeBy(192)
        assertArrayEquals("Closing an unrelated modal must not replay a completed signal", settled, pixels())
    }
    @Test fun reducedMotionKeepsGlyphStaticEvenAfterSuccess() {
        val signals = GlyphSignals()
        compose.setContent { StakTheme { CompositionLocalProvider(LocalReducedMotion provides true,
            LocalGlyphSignals provides signals, LocalGlyphLayout provides GlyphLayout.Detail) { GlyphBackground() } } }
        val before = pixels()
        compose.runOnUiThread { signals.added() }
        compose.mainClock.advanceTimeBy(192)
        assertArrayEquals(before, pixels())
        capture("detail-glyph-reduced-motion")
    }
    @Test fun seriesDetailsKeepAutoAddWithoutSeriesCenterShortcut() {
        val series = MediaItem("tt1234000", imdbId = "tt1234000", name = "Fixture series", type = "series", overview = "A series synopsis.")
        val vm = TitleViewModel(detailsLoader = { _, _ -> TitleDetails(series) }, sourceLoader = { _, _, _ -> emptyList() })
        compose.setContent { StakTheme { CompositionLocalProvider(LocalReducedMotion provides true,
            LocalBackgroundStyle provides BackgroundStyle.Glyph, LocalGlyphLayout provides GlyphLayout.Detail) {
            TitleScreen(series, {}, false, false, {}, null, null, viewModel = vm, onOpenSeriesCenter = { error("Removed shortcut") })
        } } }
        compose.waitUntil(5000) { vm.detail.value != null }
        compose.onNodeWithText("Auto-add").assertIsDisplayed()
        compose.onNodeWithText("SERIES CENTER").assertDoesNotExist()
        capture("series-details")
    }
}
