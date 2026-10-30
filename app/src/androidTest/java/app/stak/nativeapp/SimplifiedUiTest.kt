package app.stak.nativeapp

import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import com.google.gson.Gson
import org.junit.*
import java.io.File

class SimplifiedUiTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null;private lateinit var prefix:String
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="simple-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix);NativeRuntime.settings.apply{jellyfinUrl="http://127.0.0.1:1";jellyfinUserId="me";jellyfinToken="synthetic";reducedMotion=true}}
    @After fun finish(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}}
    private fun capture(name:String,dialog:Boolean=false){compose.waitForIdle();val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.absolutePath,"simplify-1.2.1");dir.mkdirs();val bitmap=(if(dialog)compose.onNode(isDialog())else compose.onRoot()).captureToImage().asAndroidBitmap();File(dir,"$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
    @Composable private fun Canvas(content:@Composable ()->Unit){StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(StakBackground).padding(16.dp)){content()}}}}
    @Test fun nonLibraryTitleHasNoQualityAction(){compose.setContent{Canvas{TitleManagementPanel(MediaItem("a",name="Film"),false,true,false,false,true,false,null,1,false,0,0,true,null,{},{},{},{})}};compose.onNodeWithText("MEDIA QUALITY").assertDoesNotExist();compose.onNodeWithText("Not in library").assertIsDisplayed();compose.onNodeWithText("MARK WATCHED").assertDoesNotExist();capture("movie-not-in-library")}
    @Test fun libraryMovieGroupsStatusQualityProgressAndActions(){compose.setContent{Canvas{TitleManagementPanel(MediaItem("a",name="Film",jellyfinId="jf",progress=.42f),true,true,true,false,true,false,null,1,false,0,0,true,null,{},{},{},{})}};compose.onNodeWithText("MEDIA QUALITY").assertIsDisplayed();compose.onNodeWithText("42%").assertIsDisplayed();compose.onNodeWithText("In library").assertIsDisplayed();capture("movie-library-actions")}
    @org.junit.Ignore("Manual watched mutations retired in 1.3.4; PassiveTracking tests verify read-only behavior")
    @Test fun watchedSeasonHasClearUnwatchActions(){var season=0;compose.setContent{Canvas{TitleManagementPanel(MediaItem("a",name="Show",type="series",jellyfinId="jf"),true,true,false,false,true,true,"S01 E01",1,true,12,12,true,null,{},{},{},{season++})}};compose.onNodeWithText("12 / 12 watched").assertIsDisplayed();compose.onNodeWithText("UNWATCH S01 E01").assertDoesNotExist();compose.onNodeWithText("UNWATCH SEASON").assertDoesNotExist();Assert.assertEquals(0,season);capture("series-library-actions")}
    private class Scan:LibraryScanGateway{var now=LibraryScanSnapshot("Idle",resultKey="old",resultStatus="Completed");var requests=0;override suspend fun allowed()=true;override suspend fun snapshot()=now;override suspend fun request(){requests++}}
    @Test fun libraryScanUsesRealProgressAndCompletesOnlyForNewExecution(){val gateway=Scan();val vm=LibraryScanViewModel(gateway);var complete=0;compose.setContent{Canvas{LibraryScanControl(true,{complete++},vm)}};compose.waitUntil(5000){vm.state.value.allowed==true};compose.onNodeWithText("SCAN LIBRARY").performClick();compose.waitUntil(5000){vm.state.value.phase=="queued"};Assert.assertEquals(1,gateway.requests);gateway.now=LibraryScanSnapshot("Running",37f,"old","Completed");compose.runOnIdle{vm.refresh()};compose.waitUntil(5000){vm.state.value.phase=="running"};compose.onNodeWithText("37%").assertIsDisplayed();capture("scan-progress");gateway.now=LibraryScanSnapshot("Idle",resultKey="new",resultStatus="Completed");compose.runOnIdle{vm.refresh()};compose.waitUntil(5000){complete==1};compose.onNodeWithText("Library scan complete").assertIsDisplayed();Assert.assertEquals(1,gateway.requests)}
    @Test fun zurgBrowserShowsPathsAndFileManagementWithoutExtraDashboards(){val vm=MediaCenterViewModel();val config=ZurgConnection("https://dav.example/dav/");NativeRuntime.settings.zurgConnectionJson=Gson().toJson(config);vm.dav.value=DavListing(listOf(DavEntry(config.endpoint+"Season/","Season/","Season",true,null),DavEntry(config.endpoint+"film.mkv","film.mkv","film.mkv",false,10000)));vm.davPath.value=config.endpoint;compose.setContent{Canvas{ZurgBrowserScreen({},vm)}};compose.onNodeWithText("Season").assertIsDisplayed();compose.onNodeWithText("film.mkv").performClick();compose.onNodeWithText("COPY PATH").assertIsDisplayed();compose.onNodeWithText("MANAGE IN TORBOX").assertIsNotEnabled();compose.onNodeWithText("Library delivery status").assertDoesNotExist();capture("zurg-file-actions",true)}

    @Test fun torznabEditorSavesSeparateEncryptedIndexer(){
        compose.setContent{Canvas{SourcesSettings({})}}
        compose.onNodeWithText("ADD TORZNAB INDEXER").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("AIOStreams")
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("uuid:synthetic-password")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("https://aio.example/api/v1/torznab")
        capture("torznab-editor",true)
        compose.onNodeWithText("SAVE INDEXER").performScrollTo().performClick()
        compose.waitUntil(5000){TorznabStore(NativeRuntime.settings).all().size==1}
        compose.onNodeWithText("AIOStreams").performScrollTo().assertIsDisplayed()
        Assert.assertEquals("uuid:synthetic-password",TorznabStore(NativeRuntime.settings).all().single().apiKey)
        Assert.assertEquals("https://aio.example/api/v1/torznab/api",TorznabStore(NativeRuntime.settings).all().single().url)
    }

    @Test fun unverifiedFileOnlyResultCannotBypassCachedOnly(){
        NativeRuntime.settings.torboxKey="synthetic"
        val movie=MediaItem("tt1234567",imdbId="tt1234567",name="Film")
        var adds=0
        val source=SourceRelease("Film.2160p.REMUX",resolution="2160p",torznabIndexerId="missing",torrentFileUrl="https://indexer.test/file.torrent",sourceToken="stable",remux=true)
        val vm=TitleViewModel(sourceLoader={_,_,_->listOf(source)},cacheLoader={_,_->emptySet()},addLoader={_,_->adds++;"123"})
        compose.setContent{Canvas{FindSourcesSheet(movie,emptyList(),vm,{},{},{})}}
        compose.waitUntil(5000){!vm.cacheLoading.value&&vm.sourceWarnings.value.isNotEmpty()}
        compose.onNodeWithText("SHOW UNVERIFIED RELEASES").assertDoesNotExist()
        compose.runOnIdle{vm.addSource(source)}
        Assert.assertEquals(0,adds);Assert.assertTrue(vm.sources.value.isEmpty())
    }    @Test fun failedSaveIsVisibleInsideEditorAndKeepsInputForRetry(){
        var attempts=0
        compose.setContent{Canvas{TorznabIndexerEditor(TorznabIndexer(name="Synthetic",url="https://indexer.test/api",apiKey="uuid:synthetic-password"),{}){attempts++;throw java.io.IOException("Synthetic storage failure")}}}
        compose.onNodeWithText("SAVE INDEXER").performClick()
        compose.waitUntil(5000){attempts==1}
        compose.onNodeWithText("Could not save this indexer. Your entries are still here; please try again.").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction())[0].assertTextContains("Synthetic")
        compose.onNodeWithText("SAVE INDEXER").assertIsEnabled().performClick()
        compose.waitUntil(5000){attempts==2}
        capture("torznab-save-failure",true)
    }
}
