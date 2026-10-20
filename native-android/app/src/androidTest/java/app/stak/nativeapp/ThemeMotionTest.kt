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

    private fun frame(): Bitmap {
        var frame: Bitmap? = null
        compose.runOnUiThread { frame = art()?.bitmap }
        return requireNotNull(frame) { "The original Rive art did not render a frame" }
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
        val dark = frame()
        compose.runOnUiThread {
            Assert.assertFalse("Theme illustration must stop after settling", art()!!.isPlaying)
        }
        compose.onNodeWithContentDescription("Switch to light theme").performClick()
        compose.onNodeWithContentDescription("Switch to dark theme").assertExists()
        Thread.sleep(1800)
        val light = frame()
        var changed = 0
        for (y in 0 until minOf(dark.height, light.height)) for (x in
            0 until minOf(dark.width, light.width)) if (dark.getPixel(x, y) != light.getPixel(x, y))
            changed++
        Assert.assertTrue(
            "The original state-machine input must change the rendered illustration",
            changed > dark.width * dark.height / 100,
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
    }
}
