package app.stak.nativeapp

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.RiveAnimationView
import org.junit.*
import org.junit.runner.RunWith

@androidx.test.filters.SdkSuppress(minSdkVersion = 30)
@RunWith(AndroidJUnit4::class)
class ThemeMotionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrument
        get() = InstrumentationRegistry.getInstrumentation()

    private fun art(root: View = compose.activity.window.decorView): RiveAnimationView? {
        if (root is RiveAnimationView) return root
        if (root is ViewGroup)
            for (i in 0 until root.childCount) art(root.getChildAt(i))?.let {
                return it
            }
        return null
    }

    private val artBounds = IntArray(4)

    private fun frame(name: String, requireArt: Boolean = true): Bitmap {
        val location = IntArray(2)
        var width = 0
        var height = 0
        compose.runOnUiThread {
            if (requireArt) {
                val view = requireNotNull(art()) { "Original Rive view is missing" }
                Assert.assertTrue("Original art surface must be available", view.isAvailable)
                view.getLocationOnScreen(location)
                width = view.width
                height = view.height
                artBounds[0] = location[0]
                artBounds[1] = location[1]
                artBounds[2] = width
                artBounds[3] = height
            } else {
                location[0] = artBounds[0]
                location[1] = artBounds[1]
                width = artBounds[2]
                height = artBounds[3]
            }
        }
        // SurfaceFlinger readback includes TextureView. TextureView.getBitmap is unreliable
        // on the hosted emulator GPU and returned null despite an attached surface.
        val screen = requireNotNull(instrument.uiAutomation.takeScreenshot())
        val frame = Bitmap.createBitmap(screen, location[0], location[1], width, height)
        val resolver = compose.activity.contentResolver
        val values =
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "theme-motion-$name.png")
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
        resolver.openOutputStream(uri)!!.use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return frame
    }

    @After
    fun resetMotion() {
        instrument.uiAutomation
            .executeShellCommand("settings put global animator_duration_scale 1")
            .close()
    }

    @Test
    fun originalAssetRendersBothThemesStopsAndHonorsReducedMotion() {
        instrument.uiAutomation
            .executeShellCommand("settings put global animator_duration_scale 1")
            .close()
        lateinit var vm: StakViewModel
        compose.runOnUiThread {
            vm = ViewModelProvider(compose.activity)[StakViewModel::class.java]
            vm.appearance("reduced", false)
            vm.option("theme", "dark")
            compose.activity.setContent { StakApp(vm, "settings?section=appearance") }
        }
        compose.waitUntil(10000) {
            var present = false
            compose.runOnUiThread { present = art() != null }
            present
        }
        Thread.sleep(1800)
        val dark = frame("dark")
        compose.runOnUiThread {
            Assert.assertFalse("Theme illustration must stop after settling", art()!!.isPlaying)
        }
        compose.onNodeWithContentDescription("Switch to light theme").performClick()
        compose.onNodeWithContentDescription("Switch to dark theme").assertExists()
        Thread.sleep(1800)
        val light = frame("light")
        var changed = 0
        for (y in 0 until minOf(dark.height, light.height)) for (x in
            0 until minOf(dark.width, light.width)) if (dark.getPixel(x, y) != light.getPixel(x, y))
            changed++
        Assert.assertTrue(
            "The original state-machine input must change the rendered illustration",
            changed > dark.width * dark.height / 5,
        )
        compose.runOnUiThread {
            Assert.assertFalse(art()!!.isPlaying)
            vm.appearance("reduced", true)
        }
        compose.waitUntil(5000) {
            var absent = false
            compose.runOnUiThread { absent = art() == null }
            absent
        }
        compose.onNodeWithContentDescription("Switch to dark theme").performClick()
        compose.onNodeWithContentDescription("Switch to light theme").assertExists()
        val staticDark = frame("static-dark", requireArt = false)
        var illustrated = 0
        for (y in 0 until dark.height) for (x in 0 until dark.width) if (
            dark.getPixel(x, y) != staticDark.getPixel(x, y)
        )
            illustrated++
        Assert.assertTrue(
            "Motion-enabled preview must render the bundled illustration, beyond the static icon",
            illustrated > dark.width * dark.height / 5,
        )
    }
}
