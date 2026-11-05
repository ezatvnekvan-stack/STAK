@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.StakTheme
import org.junit.*
import java.io.File
import java.time.LocalDate
import com.google.gson.JsonParser

/** Offline interaction/geometry checks, with isolated settings and no acquisition calls. */
class ConsistencyUiTest {
    @get:Rule val compose=createComposeRule()
    private var old:SettingsStore?=null
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefix:String
    @Before fun before(){old=runCatching{NativeRuntime.settings}.getOrNull();prefix="consistency-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{reducedMotion=true}}
    @After fun after(){old?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
    @Composable private fun Frame(content:@Composable ()->Unit){StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(Color(0xff090a0b)).testTag("root")){content()}}}}
    private fun capture(name:String){
        compose.waitForIdle();Thread.sleep(200)
        val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:context.getExternalFilesDir(null)!!.path,"consistency-ui").apply{mkdirs()}
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let{bitmap->File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
    }
    @Test fun continueCardsRemainNarrowAndNextCardPeeks(){
        var opened:String?=null
        val items=listOf(MediaItem("one",name="The first series",type="series",nextUp=true,season=1,episode=2),MediaItem("two",name="A second title",type="movie",progress=.42f))
        compose.setContent{Frame{Column(Modifier.fillMaxWidth().padding(16.dp)){ContinueRail(items){opened=it.id}}}}
        val cards=compose.onAllNodesWithTag("continue-card").fetchSemanticsNodes()
        val width=compose.onNodeWithTag("root").fetchSemanticsNode().boundsInRoot.width
        Assert.assertTrue(cards[0].boundsInRoot.width<width*.8f)
        Assert.assertTrue(cards[1].boundsInRoot.left<width-16)
        compose.onNodeWithText("The first series").performClick();Assert.assertEquals("one",opened)
        capture("continue-narrow")
    }
    @Test fun statsHasReadableDateAndLargeDayNavigation(){
        compose.setContent{Frame{Column(Modifier.padding(16.dp)){SimpleTrackingActivity(emptyList(),"this_month",LocalDate.of(2026,11,30),{})}}}
        compose.onNodeWithContentDescription("Next day").assertWidthIsAtLeast(44.dp).performClick()
        compose.onNodeWithText("Mon, Nov 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous day").performClick()
        compose.onNodeWithText("Sun, Nov 1").assertIsDisplayed()
        capture("stats-readable")
    }
    @Test fun dialogKeepsItsCloseActionVisibleWithLongContent(){
        var closed by mutableStateOf(false)
        compose.setContent{Frame{if(!closed)GlassAlertDialog({closed=true},title={Text("Library organization")},text={Column{repeat(60){Text("Approved title $it")}}},confirmButton={StakButton("Close",{closed=true})})}}
        compose.onNodeWithText("CLOSE").assertIsDisplayed()
        capture("dialog-footer")
        compose.onNodeWithText("CLOSE").performClick();compose.onNodeWithText("Library organization").assertDoesNotExist()
    }
    @Test fun titleModelReentryPreservesSeasonWithoutDuplicateLoad(){
        var loads=0
        val show=MediaItem("fixture",name="Fixture Series",type="series")
        lateinit var vm:TitleViewModel
        compose.runOnUiThread{vm=TitleViewModel(detailsLoader={_,_->loads++;TitleDetails(show,seasons=listOf(1,2),episodes=listOf(Episode(1,1,"First"),Episode(2,1,"Second")))},sourceLoader={_,_,_->emptyList()});vm.ensureLoaded("series","fixture",show)}
        compose.waitUntil(5000){vm.detail.value?.episodes?.size==2}
        compose.runOnUiThread{vm.selectSeason(2);vm.ensureLoaded("series","fixture",show)}
        compose.waitForIdle();Assert.assertEquals(1,loads);Assert.assertEquals(2,vm.season.value)
    }

    @Test fun listReturnKeepsTheSelectedPosterVisible(){
        val titles=(1..45).joinToString(","){"""{"movie":{"title":"Film $it","ids":{"imdb":"tt${1000000+it}"}}}"""}
        val api=TraktLists({"fixture"},{path,_,_->TraktPage(JsonParser.parseString(if(path.endsWith("/items"))"[$titles]"else """[{"list":{"name":"A long list","ids":{"trakt":99},"privacy":"public","item_count":45},"user":{"username":"Fixture"}}]""").asJsonArray,1)},{it})
        lateinit var vm:ListsViewModel
        compose.runOnUiThread{vm=ListsViewModel(api,{null},{})}
        var showDetails by mutableStateOf(false)
        compose.setContent{Frame{
            val states=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            if(showDetails)StakButton("Return to list",{showDetails=false})
            else states.SaveableStateProvider("lists"){ListsScreen({_,_->showDetails=true},null,null,emptySet(),vm)}
        }}
        compose.waitUntil(5000){compose.onAllNodesWithText("A long list").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("A long list").performClick()
        compose.waitUntil(5000){vm.state.value.titles.size==45}
        compose.onNodeWithTag("public-lists").performScrollToNode(hasText("Film 40"))
        compose.onNodeWithText("Film 40").performClick()
        compose.onNodeWithText("RETURN TO LIST").performClick()
        compose.onNodeWithText("Film 40").assertIsDisplayed()
        capture("list-return-position")
    }
}
