@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.stak.nativeapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.stak.nativeapp.data.SampleData
import app.stak.nativeapp.ui.screens.TitleScreen
import app.stak.nativeapp.ui.screens.TitleViewModel
import app.stak.nativeapp.data.TitleDetails
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TitleScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun titleShowsName() {
        composeRule.setContent {
            TitleScreen(
                item = SampleData.catalog.first(),
                onBack = {},
                isWatchlisted = false,
                watchlistBusy = false,
                onToggleWatchlist = {},
                sharedScope = null,
                animatedScope = null,
                viewModel = TitleViewModel(detailsLoader={_,_->TitleDetails(SampleData.catalog.first())})
            )
        }
        composeRule.onNodeWithText("Dune: Part Two", substring = true).assertIsDisplayed()
    }
}
