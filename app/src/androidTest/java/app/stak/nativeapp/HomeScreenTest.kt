@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.stak.nativeapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.stak.nativeapp.ui.screens.HomeScreen
import app.stak.nativeapp.ui.screens.HomeViewModel
import app.stak.nativeapp.data.HomeFeed
import app.stak.nativeapp.data.NativeRuntime
import app.stak.nativeapp.data.SettingsStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeShowsBrandAndSearch() {
        NativeRuntime.settings = SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext)
        composeRule.setContent {
            HomeScreen(
                onOpenTitle = { _, _ -> },
                onSearch = {},
                onOpenAccount = {},
                onOpenLibrary = {},
                onOpenWatchlist = {},
                onBrowseSection = {},
                sharedScope = null,
                animatedScope = null,
                reducedMotion = true,
                viewModel=HomeViewModel(feedLoader={HomeFeed()})
            )
        }
        composeRule.onNodeWithText("STAK").assertIsDisplayed()
        composeRule.onNodeWithText("Movie, series or IMDb ID").assertIsDisplayed()
    }
}
