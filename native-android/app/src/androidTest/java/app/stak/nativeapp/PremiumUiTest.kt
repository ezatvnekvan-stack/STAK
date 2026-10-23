package app.stak.nativeapp

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PremiumUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        saveEvidence("premium-$name-${compose.activity.resources.configuration.screenWidthDp}.png", bitmap)
        bitmap.recycle()
    }

    @Test fun sharedControlsRetainAccessibleTargetsAndWorkingActionsAtLargeFontScale() {
        var clicks = 0
        val input = mutableStateOf("")
        for (dark in listOf(true, false)) {
            compose.runOnUiThread {
                compose.activity.setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 1.35f), LocalStakMotion provides MotionPreferences(reduced = true)) {
                        MaterialTheme(colorScheme = premiumColors(dark), typography = premiumTypography()) {
                            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                MotionButton("Continue", Modifier.testTag("premium-primary"), primary = true) { clicks++ }
                                OriginalField(input.value, { input.value = it }, "Example input", Modifier.testTag("premium-field"))
                                StakSwitch("Example switch", false, Modifier.testTag("premium-switch"), {})
                                StakIconButton(StakIcons.RefreshCw, "Example action", Modifier.testTag("premium-icon")) { clicks++ }
                            }
                        }
                    }
                }
            }
            for (tag in listOf("premium-primary", "premium-field", "premium-switch", "premium-icon"))
                compose.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp)
            val before = clicks
            compose.onNodeWithTag("premium-primary").performClick()
            assertEquals(before + 1, clicks)
            compose.onNodeWithContentDescription("Example input").performTextReplacement("A real input")
            compose.onNodeWithContentDescription("Example input").assertTextEquals("A real input")
            capture("controls-${if (dark) "dark" else "light"}-large")
        }
    }

    @Test fun settingsHierarchyAndHeadingRemainUsableWithLargerText() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).code(200).message("TEST ONLY").protocol(Protocol.HTTP_1_1)
                .body("""{"results":[],"metas":[],"success":true,"data":{}}""".toResponseBody()).build()
        }.build()
        val vm = StakViewModel(compose.activity.application, Repository(compose.activity.application, client))
        val oldTheme = vm.options.value["theme"] ?: "system"
        val oldReduced = vm.reduced.value
        try {
            vm.appearance("reduced", true)
            for (theme in listOf("dark", "light")) {
                vm.option("theme", theme)
                compose.runOnUiThread {
                    compose.activity.setContent {
                        val density = LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density, 1.35f)) { StakApp(vm, "settings") }
                    }
                }
                compose.onNodeWithText("Settings", substring = false).assertHeightIsAtLeast(50.dp)
                capture("settings-$theme-large")
                compose.onNodeWithTag("settings-appearance").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
                compose.onNodeWithText("All settings").performScrollTo().assertIsDisplayed()
                compose.onNodeWithContentDescription("Reduce animations").performScrollTo().assertExists()
                capture("appearance-$theme-large")
                compose.onNodeWithText("All settings").performScrollTo().performClick()
                compose.onNodeWithTag("settings-advanced").performScrollTo().performClick()
                compose.onNodeWithTag("backup-export").performScrollTo().assertIsDisplayed()
                capture("data-$theme-large")
                compose.onNodeWithText("All settings").performScrollTo().performClick()
                for (section in listOf("account", "metadata", "sources", "jellyfin", "home")) {
                    compose.onNodeWithTag("settings-$section").performScrollTo().performClick()
                    compose.onNodeWithText("All settings").performScrollTo().assertIsDisplayed()
                    capture("$section-$theme-large")
                    compose.onNodeWithText("All settings").performScrollTo().performClick()
                }
            }
        } finally {
            vm.option("theme", oldTheme)
            vm.appearance("reduced", oldReduced)
        }
    }
}
