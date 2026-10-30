@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.*
import org.junit.*
import java.io.File
import android.graphics.Bitmap

class PreviewElevenTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null;private lateinit var prefix:String
    private var previousAccent=Color.Red
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="preview-eleven-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix);NativeRuntime.settings.hasOnboarded=true;previousAccent=StakAccent}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};StakAccent=previousAccent;listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}}
    private fun capture(name:String,window:Boolean=false){compose.waitForIdle();val folder=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.absolutePath,"preview-11");folder.mkdirs();val bitmap=if(window){android.os.SystemClock.sleep(350);InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()}else compose.onRoot().captureToImage().asAndroidBitmap();File(folder,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun homeReturnsResultsWhileTypingWithoutSubmitAndClearingRestoresHome(){
        var navigations=0;val seen=mutableListOf<String>();val media=MediaItem("tt1234567","tt1234567",name="Carrie",year="2026",jellyfinId="jf")
        val search=LiveSearchViewModel{q->seen+=q;listOf(media)}
        val home=HomeViewModel(feedLoader={HomeFeed()})
        compose.setContent{StakTheme{HomeScreen({_,_->navigations++},{navigations++},{},{},{},{},sharedScope=null,animatedScope=null,reducedMotion=true,searchViewModel=search,viewModel=home)}}
        compose.onNode(hasSetTextAction()).performTextInput("carr")
        compose.waitUntil(5000){search.items.value.isNotEmpty()&&!search.loading.value}
        compose.onNodeWithText("Carrie").assertExists();Assert.assertEquals(listOf("carr"),seen);Assert.assertEquals(0,navigations)
        capture("live-search")
        compose.onNodeWithContentDescription("Clear search").performClick();compose.onNodeWithText("Search results",ignoreCase=true).assertDoesNotExist();Assert.assertEquals(0,navigations)
    }
    @Test fun supersededRequestsAndClearedQueriesCannotShowOldResults(){
        val pending=CompletableDeferred<List<MediaItem>>();val started=CompletableDeferred<Unit>()
        val vm=LiveSearchViewModel{q->if(q=="old"){started.complete(Unit);withContext(NonCancellable){pending.await()}}else listOf(MediaItem(q,name=q))}
        compose.setContent{}
        compose.runOnIdle{vm.setQuery("old",true)};compose.waitUntil(5000){started.isCompleted}
        compose.runOnIdle{vm.setQuery("new",true)};compose.waitUntil(5000){vm.items.value.singleOrNull()?.name=="new"}
        pending.complete(listOf(MediaItem("old",name="old")));compose.waitForIdle();Assert.assertEquals("new",vm.items.value.single().name)
        compose.runOnIdle{vm.setQuery("")};Assert.assertTrue(vm.items.value.isEmpty());Assert.assertFalse(vm.loading.value)
    }
    @Test fun localMatchesAreImmediateAndRepeatedRemoteQueriesUseCache(){
        var calls=0;val vm=LiveSearchViewModel{calls++;emptyList()};val local=MediaItem("1",name="Árvíztűrő",year="2026")
        compose.setContent{};compose.runOnIdle{vm.knownTitles(listOf(local));vm.setQuery("arviz",true)}
        Assert.assertEquals(local,vm.items.value.single());compose.waitUntil(5000){!vm.loading.value}
        compose.runOnIdle{vm.setQuery("");vm.setQuery("ARVIZ",true)};compose.waitForIdle();Assert.assertEquals(1,calls)
    }
    @Test fun posterYearFollowsOneLineTitleAndOnlyTheWhiteJellyfinLogoRemains(){
        val item=MediaItem("1",name="Short title",year="2026",jellyfinId="jf")
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xff080808)).padding(16.dp)){StakPosterCard(item,{},Modifier.width(140.dp),reserveTitleLines=true)}}}
        val title=compose.onNodeWithText("Short title",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val year=compose.onNodeWithText("2026",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        Assert.assertTrue(year.top-title.bottom<8);compose.onNodeWithText("Jellyfin").assertDoesNotExist();compose.onNodeWithContentDescription("Available on Jellyfin").assertExists();capture("poster-spacing")
    }
    @Test fun whiteAccentRetainsReadableButtonContentAndPersists(){
        NativeRuntime.settings.accentColor=0xFFFFFFFF;StakAccent=Color.White
        compose.setContent{StakTheme{Column(Modifier.fillMaxSize().background(Color(0xff080808)).padding(16.dp)){StakButton("Find sources",{},Modifier.testTag("button"),variant=StakButtonVariant.Primary);Spacer(Modifier.height(16.dp));RatingStrip(8.0,"90%","65");Spacer(Modifier.height(16.dp));JellyfinBadge()}}}
        Assert.assertEquals(0xFFFFFFFF,NativeRuntime.settings.accentColor);Assert.assertEquals(Color(0xff111111),StakOnAccent)
        val pixels=compose.onNodeWithTag("button").captureToImage().toPixelMap();var dark=0;for(x in 15 until pixels.width-15)for(y in 10 until pixels.height-10)if(pixels[x,y].red<.3f)dark++
        Assert.assertTrue(dark>15);capture("white-accent-and-ratings")
    }
    @Test fun posterDialogHasTitleCloseAndExplicitActionsWithoutMutation(){
        var mutations=0;val media=MediaItem("1",name="Carrie");var open by mutableStateOf(false)
        compose.setContent{StakTheme{CompositionLocalProvider(LocalPosterActions provides PosterActions({false},{false},{false},{mutations++},{_,_->mutations++})){Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xff49361e),Color(0xff142838))))){StakPosterCard(media,{},Modifier.width(130.dp))}}}}
        compose.onNodeWithText("Carrie").performTouchInput{longClick()};compose.onNodeWithContentDescription("Close poster menu").assertExists();compose.onNodeWithText("Add to watchlist").assertExists();Assert.assertEquals(0,mutations);capture("glass-poster-menu",true)
        compose.onNodeWithContentDescription("Close poster menu").performClick();Assert.assertEquals(0,mutations)
    }
}
