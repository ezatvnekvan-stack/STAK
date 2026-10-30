@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.screens.*
import org.junit.*

class HomeSourcesTest {
    @get:Rule val compose=createComposeRule()
    private val files=mutableSetOf<String>();private var previous:SettingsStore?=null
    @Before fun isolated(){
        previous=runCatching{NativeRuntime.settings}.getOrNull()
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val prefix="home-sources-test-${java.util.UUID.randomUUID()}-"
        files+=prefix+"stak_settings";files+=prefix+"stak_secrets"
        NativeRuntime.settings=SettingsStore(context,prefix)
        NativeRuntime.settings.hasOnboarded=true
    }
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};files.forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(it)}}
    @Test fun homeShowsServerResumeWithEpisodeAndProgressInsteadOfConnectPrompt(){
        val vm=HomeViewModel(feedLoader={HomeFeed()})
        val item=MediaItem("jellyfin:series",type="series",name="Resume series",progress=.35f,releaseInfo="S2 E3 · Episode",jellyfinId="episode",season=2,episode=3,positionMs=21000,durationMs=60000)
        compose.setContent{HomeScreen({_,_->},{},{},{},{},{},continueWatching=listOf(item),jellyfinConnected=true,sharedScope=null,animatedScope=null,reducedMotion=true,viewModel=vm)}
        compose.onNodeWithText("Resume series").assertExists();compose.onNodeWithText("1 min left").assertExists();compose.onNodeWithText("CONNECT JELLYFIN").assertDoesNotExist()
    }
    @Test fun connectedEmptyResumeIsNotMisreportedAsDisconnected(){
        val vm=HomeViewModel(feedLoader={HomeFeed()})
        compose.setContent{HomeScreen({_,_->},{},{},{},{},{},jellyfinConnected=true,sharedScope=null,animatedScope=null,reducedMotion=true,viewModel=vm)}
        compose.onNodeWithText("Nothing to resume in Jellyfin.").assertExists();compose.onNodeWithText("CONNECT JELLYFIN").assertDoesNotExist()
    }
    @Test fun sourceSheetChecksCacheFiltersAndAddsEachHashOnlyOnce(){
        val hash="a".repeat(40);NativeRuntime.settings.torboxKey="synthetic-only"
        var additions=0
        val source=SourceRelease("Source fixture 2160p HDR",resolution="2160p",infoHash=hash,hdr=true,sizeBytes=1_000_000_000)
        val vm=TitleViewModel(sourceLoader={_,_,_->listOf(source)},cacheLoader={_,_->setOf(hash)},addLoader={_,_->additions++;"123"})
        compose.setContent{FindSourcesSheet(MediaItem("tt1234567","tt1234567",name="Source fixture"),emptyList(),vm,{},{},{})}
        compose.waitUntil(5000){vm.cached.value==setOf(hash)}
        compose.onNodeWithText("Source fixture 2160p HDR").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Add to TorBox").performScrollTo().performClick()
        compose.waitUntil(5000){vm.added.value[hash]=="123"}
        compose.onNodeWithContentDescription("View files").assertExists()
        compose.runOnIdle{vm.addSource(source)};Assert.assertEquals(1,additions)
        compose.onNodeWithContentDescription("Filters and sorting").performScrollTo().performClick()
        compose.onNodeWithText("Source filters").assertIsDisplayed()
        compose.onNodeWithText("Maximum source size").assertExists()
    }
}
