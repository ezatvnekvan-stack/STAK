@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
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

class NothingSharedArtworkTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private var previous:SettingsStore?=null
    private val namespace="shared-motion-${java.util.UUID.randomUUID()}-"
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();NativeRuntime.settings=SettingsStore(context,namespace)}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(namespace+it)}}
    private fun capture(name:String){val dir=File(context.getExternalFilesDir(null),"motion-1.0.2");dir.mkdirs();val bitmap=compose.onRoot().captureToImage().asAndroidBitmap();File(dir,"$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun onlyArtworkMorphsAndDetailsControlsWorkAfterReturning(){
        var open by mutableStateOf(false)
        val movie=MediaItem("motion-film",name="Motion Film",year="2026",overview="A cinematic fixture for the artwork transition.")
        val vm=TitleViewModel(detailsLoader={_,_->TitleDetails(movie)},sourceLoader={_,_,_->emptyList()})
        compose.mainClock.autoAdvance=false
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides false){SharedTransitionLayout{
            val shared=this
            AnimatedContent(open,transitionSpec={fadeIn(tween(260)) togetherWith fadeOut(tween(260))},label="fixture-route"){details->
                Box(Modifier.fillMaxSize().background(StakBackground)){
                    if(details)TitleScreen(movie,{open=false},false,false,{},shared,this@AnimatedContent,sharedKey="fixture-art",viewModel=vm)
                    else StakPosterCard(movie,{open=true},Modifier.padding(16.dp).width(120.dp).testTag("motion-card"),artworkModifier=Modifier.stakSharedElement(shared,this@AnimatedContent,"fixture-art").testTag("motion-art"))
                }
            }
        }}}}
        compose.mainClock.advanceTimeBy(400)
        val art=compose.onNodeWithTag("motion-art",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val title=compose.onNodeWithText("Motion Film",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue("Title must sit outside the artwork morph",title.top>=art.bottom)
        compose.onNodeWithTag("motion-card").performClick();compose.mainClock.advanceTimeBy(128);capture("details-transition")
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("FIND SOURCES").assertIsDisplayed()
        compose.onNodeWithTag("detail-source-dock").assertDoesNotExist()
        val action=compose.onNodeWithTag("detail-source-button").fetchSemanticsNode().boundsInRoot
        val content=compose.onNodeWithTag("detail-content").fetchSemanticsNode().boundsInRoot
        assertTrue(action.top>=content.top&&action.bottom<=content.bottom)
        capture("details-settled")
        compose.onNodeWithContentDescription("Back").performClick();compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("motion-card").assertIsDisplayed()
        compose.onNodeWithTag("motion-card").performClick();compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithContentDescription("Add to watchlist").assertIsDisplayed()
    }
}

