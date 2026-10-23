package app.stak.nativeapp

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BetaUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: StakViewModel

    @Before
    fun setup() {
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .code(200)
                        .message("TEST ONLY")
                        .protocol(Protocol.HTTP_1_1)
                        .body(
                            "{\"success\":false,\"metas\":[],\"results\":[]}"
                                .toResponseBody("application/json".toMediaType())
                        )
                        .build()
                }
                .build()
        vm =
            StakViewModel(
                compose.activity.application,
                Repository(compose.activity.application, client),
            )
        vm.repository.credentials.set("torbox_token", "")
        vm.appearance("reduced", true)
        vm.option("home_order", "[]")
        vm.option("home_genres", "[]")
        vm.option(
            "nav_order",
            "[\"home\",\"discover\",\"watchlist\",\"tracking\",\"library\",\"settings\"]",
        )
    }

    @After
    fun cleanup() {
        vm.option("home_order", "[]")
        vm.option("home_genres", "[]")
        vm.option(
            "nav_order",
            "[\"home\",\"discover\",\"watchlist\",\"tracking\",\"library\",\"settings\"]",
        )
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        saveEvidence(
            "beta-$name-${compose.activity.resources.configuration.screenWidthDp}.png",
            bitmap,
        )
        bitmap.recycle()
    }

    @Test
    fun backupRequiresMatchingPasswordAndKeepsConnectionExportOptional() {
        compose.runOnUiThread {
            compose.activity.setContent { StakApp(vm, "settings?section=advanced") }
        }
        compose.onNodeWithTag("backup-export").performScrollTo().performClick()
        compose.onNodeWithText("CHOOSE FILE").assertIsNotEnabled()
        compose
            .onNodeWithContentDescription("Backup password")
            .performTextInput("test-only strong password")
        compose
            .onNodeWithContentDescription("Confirm password")
            .performTextInput("test-only different password")
        compose.onNodeWithText("CHOOSE FILE").assertIsNotEnabled()
        compose
            .onNodeWithContentDescription("Confirm password")
            .performTextReplacement("test-only strong password")
        compose.onNodeWithText("CHOOSE FILE").assertIsEnabled()
        compose.onNodeWithContentDescription("Include saved connections").assertIsOff()
        compose.runOnUiThread {
            androidx.core.view
                .WindowInsetsControllerCompat(
                    compose.activity.window,
                    compose.activity.window.decorView,
                )
                .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
        for (theme in listOf("dark", "light")) {
            compose.runOnUiThread { vm.option("theme", theme) }
            capture("backup-$theme")
        }
        compose.onNodeWithText("CANCEL").performClick()
        compose.onNodeWithText("Protect your backup").assertDoesNotExist()
    }

    @Test
    fun homeReorderingAndGenreShelfRemainSavedAcrossNavigation() {
        compose.runOnUiThread {
            compose.activity.setContent { StakApp(vm, "settings?section=home") }
        }
        compose
            .onNodeWithContentDescription("Move New releases up")
            .performScrollTo()
            .performClick()
        assertEquals("upcoming", homeShelves(vm.options.value, 2026).first().id)
        for (theme in listOf("dark", "light")) {
            compose.runOnUiThread { vm.option("theme", theme) }
            capture("home-layout-$theme")
        }
        compose.onNodeWithContentDescription("Genre for new shelf").performScrollTo().performClick()
        compose.onNodeWithText("Drama").performClick()
        compose.onNodeWithContentDescription("Add genre shelf").performScrollTo().performClick()
        assertTrue(homeShelves(vm.options.value, 2026).any { it.id == "genre:movie:18" })
        compose.onNodeWithTag("nav-home").performClick()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("settings-home").performClick()
        compose.onNodeWithContentDescription("Show Drama movies").performScrollTo().assertIsOn()
    }

    @Test
    fun taskbarCanHideReorderAndAddOptionalTorBoxWithoutLosingSettings() {
        compose.runOnUiThread {
            compose.activity.setContent { StakApp(vm, "settings?section=home") }
        }
        compose.onNodeWithContentDescription("Watchlist tab").performScrollTo().performClick()
        compose.onNodeWithTag("nav-watchlist").assertDoesNotExist()
        compose.onNodeWithContentDescription("TorBox tab").performScrollTo().performClick()
        compose.onNodeWithTag("nav-torbox").assertExists()
        compose.onNodeWithContentDescription("Move TorBox tab up").performScrollTo().performClick()
        assertEquals("torbox", navigationOrder(vm.options.value)[4])
        compose.onNodeWithContentDescription("Home tab").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Settings tab").assertIsNotEnabled()
        for (theme in listOf("dark", "light")) {
            compose.runOnUiThread { vm.option("theme", theme) }
            capture("taskbar-$theme")
        }
        compose.onNodeWithTag("nav-torbox").performScrollTo().performClick()
        compose.onNodeWithTag("nav-settings").performScrollTo().performClick()
        compose.onNodeWithTag("settings-home").assertExists()
    }
}
