@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.StakTheme
import com.google.gson.*
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CompletableDeferred
import org.junit.*
import java.io.File

class ListsAndFloatingUiTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null
    private lateinit var prefix:String
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup(){
        previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="lists-smooth-ui-${java.util.UUID.randomUUID()}-"
        NativeRuntime.settings=SettingsStore(context,prefix).apply{ratingsEnabled=false;reducedMotion=true;traktClientId="fixture-client"}
    }
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)};File(context.cacheDir,"$prefix-art.png").delete()}
    private fun capture(name:String){
        val base=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:context.getExternalFilesDir(null)!!.path
        val dir=File(base,"lists-smooth-ui").apply{mkdirs()}
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let{bitmap->File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
    }
    @Test fun publicListsSaveOpenAndReturnWithoutWaitingForArtwork(){
        val artGate=CompletableDeferred<Unit>();var saved:String?=null;var opened:MediaItem?=null
        val api=TraktLists({"fixture"},{path,_,_->TraktPage(JsonParser.parseString(if(path.endsWith("/items"))"""[{"movie":{"title":"Fixture Film","year":2024,"ids":{"imdb":"tt1234567"}}}]"""else """[{"list":{"name":"Weekend cinema","ids":{"trakt":42},"privacy":"public","item_count":1},"user":{"username":"STAK community"}}]""").asJsonArray,1)},{artGate.await();it})
        lateinit var vm:ListsViewModel
        compose.runOnUiThread{vm=ListsViewModel(api,{saved},{saved=it})}
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xFF090A0B))){ListsScreen({item,_->opened=item},null,null,emptySet(),vm)}}}
        compose.waitUntil(8000){compose.onAllNodesWithText("Weekend cinema").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("SAVE").performClick();Assert.assertTrue(saved!!.contains("Weekend cinema"))
        capture("community-lists")
        compose.onNodeWithText("Weekend cinema").performClick()
        compose.waitUntil(8000){compose.onAllNodesWithText("Fixture Film").fetchSemanticsNodes().isNotEmpty()}
        capture("ranked-list")
        compose.onNodeWithText("Fixture Film").performClick();Assert.assertEquals("tt1234567",opened?.imdbId)
        compose.onNodeWithText("BACK").performClick()
        compose.onAllNodesWithText("SAVED",substring=false).assertCountEquals(2)
        artGate.complete(Unit)
    }
    @Test fun topFindSourcesHasAnAccessibleTouchTarget(){
        var taps=0
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xFF111214)).testTag("surface")){
            Column(Modifier.fillMaxSize().padding(16.dp)){DetailSourcesAction("fixture",{taps++})}
        }}}
        val button=compose.onNodeWithTag("detail-source-button")
        button.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(150.dp)
        val root=compose.onNodeWithTag("surface").fetchSemanticsNode().boundsInRoot
        Assert.assertTrue(button.fetchSemanticsNode().boundsInRoot.width<root.width*.75f)
        button.performClick();Assert.assertEquals(1,taps)
        capture("top-find-sources")
    }
    private fun artworkFixture():String {
        val file=File(context.cacheDir,"$prefix-art.png")
        val bitmap=Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(bitmap)
        val paint=android.graphics.Paint().apply{shader=android.graphics.LinearGradient(0f,0f,600f,900f,android.graphics.Color.rgb(15,65,70),android.graphics.Color.rgb(2,6,10),android.graphics.Shader.TileMode.CLAMP)}
        canvas.drawRect(0f,0f,600f,900f,paint)
        paint.shader=null;paint.color=android.graphics.Color.rgb(140,184,175);paint.textSize=56f
        canvas.drawText("STAK CINEMA",60f,440f,paint)
        file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        return file.toURI().toString()
    }
    @Test fun detailsRendersBeforeEpisodesAndSeasonRetryCannotBeOverwritten(){
        val first=kotlinx.coroutines.CompletableDeferred<Unit>();var seasonTwoCalls=0
        val art=artworkFixture()
        val show=MediaItem("tt99999989","tt99999989","series","Fixture Series",year="2026",overview="A controlled fixture for responsive Details and independent season loading.",poster=art,background=art)
        lateinit var vm:TitleViewModel
        compose.runOnUiThread{vm=TitleViewModel(detailsLoader={_,_->TitleDetails(show,seasons=listOf(1,2))},seasonLoader={_,season->
            if(season==1){first.await();listOf(Episode(1,1,"Old first season"))}
            else {seasonTwoCalls++;if(seasonTwoCalls==1)throw java.io.IOException("fixture outage");listOf(Episode(2,1,"Second season ready"))}
        })}
        compose.setContent{StakTheme{app.stak.nativeapp.ui.screens.TitleScreen(show,{},false,false,{},null,null,viewModel=vm)}}
        compose.waitUntil(8000){vm.detail.value?.seasons==listOf(1,2)}
        compose.onNodeWithContentDescription("Add to watchlist").assertIsDisplayed()
        compose.onNodeWithText("FIND SOURCES").assertIsDisplayed()
        capture("details-with-floating-action")
        compose.onNodeWithContentDescription("Choose season").performScrollTo().performClick();compose.onNodeWithText("Season 2").performClick()
        compose.waitUntil(8000){vm.seasonError.value!=null}
        compose.onNodeWithText("RETRY SEASON").performScrollTo().performClick()
        compose.waitUntil(8000){vm.detail.value!!.episodes.any{it.season==2}}
        first.complete(Unit)
        compose.waitForIdle()
        Assert.assertEquals(2,vm.season.value);Assert.assertEquals("Second season ready",vm.episode.value?.name)
        Assert.assertFalse(vm.seasonLoading.value);Assert.assertNull(vm.seasonError.value)
        compose.onNodeWithText("Second season ready",ignoreCase=true).performScrollTo().assertIsDisplayed()
    }
    @Test fun communityCardLoadsCollageWithoutChangingListRank(){
        val art=artworkFixture()
        val api=TraktLists({"fixture"},{path,_,_->TraktPage(JsonParser.parseString(if(path.endsWith("/items"))"""[{"movie":{"title":"First","ids":{"tmdb":1}}},{"show":{"title":"Second","ids":{"tmdb":2}}},{"movie":{"title":"Third","ids":{"tmdb":3}}}]"""else """[{"list":{"name":"After dark","ids":{"trakt":42},"privacy":"public","item_count":3},"user":{"username":"Cinema club"}}]""").asJsonArray,1)},{it.copy(poster=art)})
        lateinit var vm:ListsViewModel
        compose.runOnUiThread{vm=ListsViewModel(api,{null},{})}
        compose.setContent{StakTheme{Column(Modifier.fillMaxSize().background(Color(0xFF090A0B)).statusBarsPadding()){
            app.stak.nativeapp.ui.components.StakPageHeader("Discover",modifier=Modifier.padding(horizontal=16.dp))
            app.stak.nativeapp.ui.components.StakTabs(listOf("titles" to "Titles","lists" to "Lists"),"lists",{},Modifier.padding(horizontal=16.dp))
            Box(Modifier.weight(1f)){ListsScreen({_,_->},null,null,emptySet(),vm)}
        }}}
        compose.waitUntil(8000){vm.previews.value[42]?.size==3}
        compose.waitForIdle();Thread.sleep(250)
        capture("community-list-artwork")
        Assert.assertEquals(listOf("First","Second","Third"),vm.previews.value[42]!!.map{it.name})
    }
}
