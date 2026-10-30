@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

/** Synthetic data only: screenshots document layout, never owner account results. */
class ReferenceUiTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null
    private lateinit var prefix:String
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="reference-ui-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix);NativeRuntime.settings.hasOnboarded=true;NativeRuntime.settings.torboxKey="synthetic-only"}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}}
    private fun capture(name:String){
        val folder=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.absolutePath,"preview-6");folder.mkdirs()
        val bitmap=if(name.startsWith("sources-")) {
            compose.onNodeWithText("Best quality").assertIsDisplayed()
            compose.waitForIdle()
            val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
            var frame=automation.takeScreenshot()
            var attempts=0
            while(android.graphics.Color.red(frame.getPixel(frame.width/2,frame.height/2))<12&&attempts++<20) {
                android.os.SystemClock.sleep(40)
                frame=automation.takeScreenshot()
            }
            Assert.assertTrue("Bottom sheet must be visible in the device capture",android.graphics.Color.red(frame.getPixel(frame.width/2,frame.height/2))>=12)
            frame
        } else compose.onRoot().captureToImage().asAndroidBitmap()
        File(folder,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun sheet(series:Boolean) {
        val hash="a".repeat(40)
        val media=MediaItem(if(series)"tt14688458" else "tt12042730",type=if(series)"series" else "movie",name=if(series)"Silo" else "Project Hail Mary",year=if(series)"2023" else "2026",runtime=if(series)null else "157 min",genres=listOf("Science Fiction","Adventure"))
        val episodes=(1..10).map{Episode(1,it,"Episode $it")}
        val sources=listOf(SourceRelease(if(series)"Silo (2023) Season 1 S01 (2160p ATVP WEB-DL x265 HEVC 10bit DDP 5.1 Vyndros)" else "Project.Hail.Mary.2026.PROPER.HDR.2160p.WEB.h265-GRACE",resolution="2160p",hdr=!series,webdl=true,sizeBytes=if(series)17_062_000_000 else 29_624_000_000,seeders=939,indexer="ThePirateBay",infoHash=hash),SourceRelease(if(series)"Silo.2023.S01.MULTI.2160p.WEB-DL.SDR.H265-AOC" else "Project Hail Mary (2026) 2160p H265 iMAX HDR DV iTA EnG MIRCrew",resolution="2160p",hdr=true,dolbyVision=true,seeders=214,sizeBytes=6_754_000_000,infoHash="b".repeat(40)))
        val vm=TitleViewModel(detailsLoader={_,_->TitleDetails(media,episodes=episodes,seasons=listOf(1))},sourceLoader={_,_,_->sources},cacheLoader={_,_->setOf(hash,"b".repeat(40))},packSizeLoader={_,_->mapOf(hash to 39_000_000_000L,"b".repeat(40) to 28_000_000_000L)})
        vm.load(media.type,media.mediaKey);vm.setPackMode(series)
        compose.setContent{StakTheme {FindSourcesSheet(media,episodes,vm,{},{},{})}}
        compose.waitUntil(5000){vm.cached.value.size==2}
        // This fixture documents the full release-list layout. The new picker starts at medium height.
        compose.onNodeWithContentDescription("Expand sources").performClick()
        compose.onNodeWithText("Best quality").assertExists()
        compose.onAllNodesWithContentDescription("Add to TorBox").onFirst().assertExists()
        capture(if(series)"sources-series" else "sources-movie")
    }
    @Test fun movieSourceReferenceLayout()=sheet(false)
    @Test fun seriesSourceReferenceLayout()=sheet(true)
    @Test fun ratingStripExcludesTmdbAndUsesAllThreeVerifiedSources(){
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xFF141414)),contentAlignment=Alignment.Center){RatingStrip(8.0,"90%","65")}}}
        compose.onNodeWithContentDescription("IMDb 8.0").assertExists();compose.onNodeWithContentDescription("RT 90%").assertExists();compose.onNodeWithContentDescription("MC 65").assertExists();compose.onNodeWithText("TMDB",substring=true,ignoreCase=true).assertDoesNotExist();capture("ratings")
    }
    @Test fun homeCombinesResumeAndNextUpCards(){
        val resumed=MediaItem("resume",name="Toy Story 5",jellyfinId="resume",progress=.42f,positionMs=2_562_000,durationMs=6_100_000)
        val next=MediaItem("next",type="series",name="Sharp Objects",jellyfinId="next",nextUp=true,season=1,episode=1)
        val vm=HomeViewModel(feedLoader={HomeFeed()})
        compose.setContent{StakTheme{HomeScreen({_,_->},{},{},{},{},{},continueWatching=listOf(resumed,next),jellyfinConnected=true,sharedScope=null,animatedScope=null,reducedMotion=true,viewModel=vm)}}
        compose.onNodeWithText("RESUME").assertExists();compose.onNodeWithText("NEXT UP").assertExists();compose.onNodeWithText("59 min left").assertExists();compose.onNodeWithText("S1 E1").assertExists();compose.onNodeWithText("42%").assertExists();compose.onNodeWithText("0%").assertDoesNotExist();capture("continue-watching")
    }
    @Test fun glassNavigationBlursLiveBackdropAndKeepsLabelsSharp(){
        var switched by mutableStateOf(false)
        compose.setContent{StakTheme{
            val layer=rememberGraphicsLayer()
            Box(Modifier.fillMaxSize()){
                Canvas(Modifier.fillMaxSize().drawWithContent{layer.record{this@drawWithContent.drawContent()};drawLayer(layer)}) {
                    val stripe=8.dp.toPx();var x=0f
                    while(x<size.width){drawRect(if((x/stripe).toInt()%2==0)Color.Red else if(switched)Color.Green else Color.Blue,Offset(x,0f),Size(stripe,size.height));x+=stripe}
                }
                Box(Modifier.align(Alignment.BottomCenter).padding(12.dp).testTag("glass")){
                    StakBottomBar(Dest.Home,layer,listOf(NavTab(Dest.Home,"Home",Lucide.House,"home"),NavTab(Dest.Discover,"Discover",Lucide.Compass,"discover"),NavTab(Dest.Watchlist,"Watchlist",Lucide.Bookmark,"watchlist"),NavTab(Dest.Tracking,"Tracking",Lucide.Tracking,"tracking"),NavTab(Dest.Library,"Library",Lucide.Library,"library"),NavTab(Dest.Account,"Settings",Lucide.Settings,"settings")),{})
                }
            }
        }}
        compose.onNodeWithText("HOME").assertIsDisplayed()
        val before=compose.onNodeWithTag("glass").captureToImage().toPixelMap()[100,3]
        compose.runOnIdle{switched=true};compose.waitForIdle()
        val after=compose.onNodeWithTag("glass").captureToImage().toPixelMap()[100,3]
        Assert.assertTrue("Blur must mix adjacent red and blue stripes",before.red>.02f&&before.blue>.02f)
        Assert.assertTrue("Backdrop must follow live content",after.green>before.green+.02f)
        capture("glass-navigation")
    }
}
