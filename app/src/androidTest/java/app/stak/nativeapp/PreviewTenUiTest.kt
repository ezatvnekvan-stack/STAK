@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.drawLayer
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
import java.io.File

class PreviewTenUiTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null;private lateinit var prefix:String;private val files=mutableListOf<File>()
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="preview-ten-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix);NativeRuntime.settings.hasOnboarded=true}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)};files.forEach{it.delete()}}
    private fun bitmap(name:String,logo:Boolean=false):String {
        val file=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"$prefix$name.png");files+=file
        val bitmap=Bitmap.createBitmap(if(logo)480 else 700,if(logo)120 else 500,Bitmap.Config.ARGB_8888);val canvas=android.graphics.Canvas(bitmap)
        if(!logo)canvas.drawColor(0xff16293b.toInt())
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.WHITE;textSize=if(logo)58f else 38f;typeface=android.graphics.Typeface.create("serif",android.graphics.Typeface.BOLD)}
        if(logo)canvas.drawText("FIXTURE FILM",8f,86f,paint)
        file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle();return file.absolutePath
    }
    private fun capture(name:String,window:Boolean=false){
        compose.waitForIdle()
        val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.absolutePath,"preview-10");dir.mkdirs()
        val popup=name in setOf("sources-total-pack","torbox-long-press","poster-menu")
        val bitmap=if(popup||window){
            // Separate dialog windows are drawn after Compose's semantic tree is ready.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            android.os.SystemClock.sleep(350)
            val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
            var frame=automation.takeScreenshot()
            if(name=="sources-total-pack"){
                var attempts=0
                while(android.graphics.Color.red(frame.getPixel(frame.width/2,frame.height/2))<12&&attempts++<20){android.os.SystemClock.sleep(40);frame=automation.takeScreenshot()}
                Assert.assertTrue("Source sheet must be drawn in the device capture",android.graphics.Color.red(frame.getPixel(frame.width/2,frame.height/2))>=12)
            }
            frame
        }else compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun heroUsesASmallImageLogoGenreRuntimeCertificateAndNoImdbScore(){
        val item=MediaItem("tmdb:42",name="Fixture Film",year="2026",runtime="145 min",background=bitmap("backdrop"),logo=bitmap("logo",true),certification="PG-13",genres=listOf("Action","Adventure"),rating=8.8)
        val vm=HomeViewModel(feedLoader={HomeFeed(hero=listOf(item))})
        compose.setContent{StakTheme{HomeScreen({_,_->},{},{},{},{},{},sharedScope=null,animatedScope=null,reducedMotion=true,viewModel=vm)}}
        compose.onNodeWithText("ACTION · ADVENTURE").assertExists();compose.onNodeWithText("2026 · 145 min").assertExists();compose.onNodeWithText("PG-13").assertExists();compose.onNodeWithText("Fixture Film").assertDoesNotExist();compose.onNodeWithContentDescription("IMDb 8.8").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Fixture Film",useUnmergedTree=true).assertCountEquals(2);capture("home-hero")
    }
    @Test fun posterLongPressOpensExplicitActionsWithoutNavigatingOrMutatingBeforeSelection(){
        val item=MediaItem("tt1234567","tt1234567",name="Fixture Film",poster=bitmap("poster"));var saved by mutableStateOf(false);var watched=false;var navigations=0;var changes=0
        compose.setContent{StakTheme{CompositionLocalProvider(LocalPosterActions provides PosterActions({saved},{watched},{true},{saved=!saved;changes++},{_,state->watched=state;changes++})){Box(Modifier.fillMaxSize().background(Color(0xff090909)).padding(20.dp)){StakPosterCard(item,{navigations++},Modifier.width(140.dp))}}}}
        compose.onNodeWithContentDescription("Available on Jellyfin").assertExists();compose.onNodeWithText("Fixture Film").performTouchInput{longClick()}
        compose.onNodeWithText("Add to watchlist").assertExists();Assert.assertEquals(0,changes);Assert.assertEquals(0,navigations);capture("poster-menu")
        compose.onNodeWithText("Add to watchlist").performClick();Assert.assertTrue(saved)
        compose.onNodeWithText("Fixture Film").performTouchInput{longClick()};compose.onNodeWithText("Remove from watchlist").assertExists();compose.onNodeWithText("Mark watched").performClick();Assert.assertTrue(watched);Assert.assertEquals(0,navigations)
    }
    @Test fun torboxLongPressSelectsTheTorrentAndOpensASmallMenuBeforeDeleteConfirmation(){
        NativeRuntime.settings.torboxKey="synthetic-only";var calls=0
        val api=object:TorBoxMutations{override suspend fun addMagnet(key:String,input:MagnetInput)="unused";override suspend fun upload(key:String,file:TorrentUpload)="unused";override suspend fun control(key:String,id:String,operation:TorrentOperation){calls++}}
        val vm=TorBoxViewModel(loader={_,_->listOf(TorBoxTorrent("1","Torrent one",100,.4f,false,"downloading",emptyList()))},match={null},mutations=api)
        compose.setContent{StakTheme{TorBoxScreen({},{},"synthetic-only",{},vm)}}
        compose.onNodeWithText("Torrent one").performScrollTo().performTouchInput{longClick()};compose.onNodeWithText("DELETE").assertIsDisplayed();Assert.assertEquals(setOf("1"),vm.selected.value);Assert.assertEquals(0,calls);capture("torbox-long-press")
        compose.onNodeWithText("DELETE").performClick();compose.onNodeWithText("Delete 1 torrent?").assertExists();Assert.assertEquals(0,calls);compose.onNodeWithText("CANCEL").performClick();Assert.assertEquals(0,calls)
    }
    @Test fun packCardShowsConfirmedTotalAndNeverSubstitutesTheSingleEpisodeSize(){
        val hash="a".repeat(40);NativeRuntime.settings.torboxKey="synthetic-only";var sizes=mapOf(hash to 30*1073741824L)
        val media=MediaItem("tt1234567","tt1234567",type="series",name="Fixture Series")
        val source=SourceRelease("Fixture.Series.S01.2160p.WEB-DL",infoHash=hash,sizeBytes=3*1073741824L,seeders=939)
        val vm=TitleViewModel(detailsLoader={_,_->TitleDetails(media,seasons=listOf(1),episodes=listOf(Episode(1,1,"One")))},sourceLoader={_,_,_->listOf(source)},cacheLoader={_,_->setOf(hash)},packSizeLoader={_,_->sizes})
        compose.runOnIdle{vm.load("series",media.mediaKey);vm.setPackMode(true)}
        compose.setContent{StakTheme{FindSourcesSheet(media,listOf(Episode(1,1,"One")),vm,{},{},{})}}
        compose.waitUntil(5000){vm.sources.value.singleOrNull()?.torrentSizeBytes==30*1073741824L}
        compose.onNodeWithContentDescription("Expand sources").performClick()
        compose.onNodeWithText("30.00 GB").performScrollTo().assertIsDisplayed();compose.onNodeWithText("Cached on TorBox").assertExists();compose.onNodeWithText("Release details",substring=true).assertDoesNotExist();compose.onNodeWithText("seeds",substring=true).assertDoesNotExist();capture("sources-total-pack")
        compose.runOnIdle{sizes=emptyMap();vm.loadSources("series",media.mediaKey)};compose.waitUntil(5000){!vm.cacheLoading.value&&vm.sources.value.singleOrNull()?.torrentSizeBytes==null}
        compose.onNodeWithText("Pack size unavailable").performScrollTo().assertIsDisplayed();compose.onNodeWithText("3.00 GB",substring=true).assertDoesNotExist()
    }
    @Test fun ratingsAreCompactAndJellyfinAvailabilityHasAVisibleBrandedBadge(){
        compose.setContent{StakTheme{Column(Modifier.fillMaxSize().background(Color(0xff090909)).padding(16.dp),verticalArrangement=Arrangement.Center){Box(Modifier.testTag("ratings")){RatingStrip(8.0,"90%","65")};Spacer(Modifier.height(16.dp));JellyfinBadge()}}}
        val bounds=compose.onNodeWithTag("ratings").fetchSemanticsNode().boundsInRoot;val density=InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        Assert.assertTrue(bounds.height<=36*density);Assert.assertTrue(bounds.width<360*density);compose.onNodeWithText("In library").assertIsDisplayed();capture("compact-ratings-jellyfin")
    }
    @Test fun segmentedProgressContainsVisibleGapsAndReportsItsActualFraction(){
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){StakProgress(.5f,Modifier.width(240.dp).testTag("segments"),height=8,color=Color(0xff6c85ff))}}}
        compose.onNodeWithTag("segments").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(.5f,0f..1f))
        val pixels=compose.onNodeWithTag("segments").captureToImage().toPixelMap();val y=pixels.height/2;var starts=0;var previous=false
        for(x in 0 until pixels.width){val pixel=pixels[x,y];val filled=pixel.blue>pixel.red+.2f;if(filled&&!previous)starts++;previous=filled}
        Assert.assertTrue("Filled segments must have distinct gaps",starts>=8);capture("segmented-progress")
    }
    @Test fun blackBackdropStaysBlackAndBlueBackdropRetainsItsHue(){
        var color by mutableStateOf(Color.Black)
        compose.setContent{val layer=rememberGraphicsLayer();Box(Modifier.fillMaxSize()){Box(Modifier.fillMaxSize().background(color).drawWithContent{layer.record{this@drawWithContent.drawContent()};drawLayer(layer)});GlassBackdrop(layer,Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp).testTag("backdrop"))}}
        var pixel=compose.onNodeWithTag("backdrop").captureToImage().toPixelMap()[100,20];Assert.assertTrue(pixel.red<.03f&&pixel.green<.03f&&pixel.blue<.03f)
        compose.runOnIdle{color=Color(0xff1030b0)};pixel=compose.onNodeWithTag("backdrop").captureToImage().toPixelMap()[100,20];Assert.assertTrue(pixel.blue>pixel.red*2);capture("backdrop-hue")
    }
}
