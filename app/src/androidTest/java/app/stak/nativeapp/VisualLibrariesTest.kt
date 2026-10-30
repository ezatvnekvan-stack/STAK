package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import com.airbnb.lottie.LottieCompositionFactory
import dev.chrisbanes.haze.HazeState
import org.junit.*
import java.io.File

class VisualLibrariesTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null
    private lateinit var prefix:String
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="visual-libs-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix);NativeRuntime.settings.reducedMotion=true;NativeRuntime.settings.torboxKey="synthetic-only"}
    @After fun restore(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}}
    private fun capture(name:String,window:Boolean=false):Bitmap{
        compose.waitForIdle()
        val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.absolutePath,"visual-1.1");dir.mkdirs()
        val bitmap=if(window){android.os.SystemClock.sleep(400);InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()}else compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        return bitmap
    }
    @Test fun hazeDialogBlursParentAndUpdatesItsColorAcrossWindows(){
        var tint by mutableStateOf(Color.White)
        compose.setContent{StakTheme{
            val layer=rememberGraphicsLayer();val source=remember{GlassSource(layer,crossWindow=true,hazeState=HazeState())};val view=LocalView.current
            CompositionLocalProvider(LocalGlassSource provides source,LocalReducedMotion provides true){
                Box(Modifier.fillMaxSize().recordGlassSource(source,view).drawBehind{
                    drawRect(Color.Black);var x=0f;while(x<size.width){drawRect(tint,androidx.compose.ui.geometry.Offset(x,0f),androidx.compose.ui.geometry.Size(5.dp.toPx(),size.height));x+=10.dp.toPx()}
                }){GlassDialog({}){Box(Modifier.fillMaxWidth().height(180.dp))}}
            }
        }}
        val frame=capture("haze-white",true);val y=frame.height/2+40
        val values=(frame.width/2-50..frame.width/2+50).map{android.graphics.Color.red(frame.getPixel(it,y))/255f}
        Assert.assertTrue("Haze must soften the sharp stripes",values.max()-values.min()<.09f)
        Assert.assertTrue("Parent content must be visible through the glass",values.average()>.025)
        compose.runOnIdle{tint=Color.Blue}
        val blue=capture("haze-blue",true).getPixel(frame.width/2,y)
        Assert.assertTrue("Dialog glass must track the parent source",android.graphics.Color.blue(blue)>android.graphics.Color.red(blue)*2)
    }
    @Test fun hazeNavigationKeepsLabelsSharpAndTracksTheContentBehindIt(){
        var tint by mutableStateOf(Color.Red)
        compose.setContent{StakTheme{
            val layer=rememberGraphicsLayer();val view=LocalView.current
            val source=remember{GlassSource(layer,crossWindow=true,hazeState=HazeState()).also{it.ownerView=view}}
            CompositionLocalProvider(LocalGlassSource provides source,LocalReducedMotion provides true){
                Box(Modifier.fillMaxSize()){
                    Box(Modifier.fillMaxSize().recordGlassSource(source,view).background(tint))
                    Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(16.dp)){
                        StakBottomBar(Dest.Home,layer,listOf(NavTab(Dest.Home,"Home",Lucide.House,"home"),NavTab(Dest.Discover,"Discover",Lucide.Compass,"discover")),{})
                    }
                }
            }
        }}
        compose.onNodeWithContentDescription("Home").assertIsDisplayed()
        val red=capture("haze-navigation-red")
        compose.runOnIdle{tint=Color.Blue}
        val blue=capture("haze-navigation-blue")
        // Sample a clear part of the navigation material away from text and icons.
        val x=red.width/2;val y=red.height-48
        Assert.assertTrue(android.graphics.Color.red(red.getPixel(x,y))>android.graphics.Color.blue(red.getPixel(x,y)))
        Assert.assertTrue(android.graphics.Color.blue(blue.getPixel(x,y))>android.graphics.Color.red(blue.getPixel(x,y)))
        compose.onNodeWithText("HOME").assertIsDisplayed()
    }
    @Test fun releaseSheetExpandsCollapsesAndKeepsManualAddWorking(){
        val movie=MediaItem("tt1234567",imdbId="tt1234567",name="Library integration")
        val hash="a".repeat(40);var adds=0;var visible by mutableStateOf(true)
        val vm=TitleViewModel(sourceLoader={_,_,_->listOf(SourceRelease("Film.2160p.REMUX.HDR",infoHash=hash,resolution="2160p",remux=true,hdr=true,sizeBytes=10000000))},cacheLoader={_,_->setOf(hash)},addLoader={_,_->adds++;"123"})
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(StakBackground)){if(visible)FindSourcesSheet(movie,emptyList(),vm,{visible=false},{},{})}}}}
        compose.onNodeWithContentDescription("Expand sources").assertExists()
        val mid=compose.onNodeWithText(movie.name).getUnclippedBoundsInRoot().top
        capture("sources-medium",true)
        compose.onNodeWithText("Film 2160p REMUX HDR").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(movie.name).performScrollTo()
        compose.onNodeWithContentDescription("Expand sources").performClick()
        compose.onNodeWithContentDescription("Collapse sources").assertExists()
        val full=compose.onNodeWithText(movie.name).getUnclippedBoundsInRoot().top
        Assert.assertTrue("Expanded panel must move its heading upward",mid-full>100.dp)
        compose.waitUntil(5000){!vm.sourceLoading.value&&!vm.cacheLoading.value}
        compose.onNodeWithContentDescription("Add to TorBox").performScrollTo().performClick()
        compose.waitUntil(5000){vm.added.value[hash]=="123"}
        Assert.assertEquals(1,adds)
        compose.onNodeWithContentDescription("Added successfully").assertExists()
        capture("sources-added",true)
        compose.onNodeWithContentDescription("Close").performScrollTo().performClick()
        compose.onNodeWithText(movie.name).assertDoesNotExist()
    }
    @Test fun bundledLottieAssetsAreValidOfflineVectorsAndReducedMotionFreezesThem(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        StakAnimation.entries.forEach{animation->
            val result=LottieCompositionFactory.fromRawResSync(context,animation.asset)
            Assert.assertNull(result.exception);Assert.assertNotNull(result.value)
            Assert.assertTrue(result.value!!.duration>0);Assert.assertTrue(result.value!!.images.isEmpty())
        }
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Row(Modifier.fillMaxWidth().background(Color.Black)){StakAnimation.entries.forEach{StakLottie(it,Modifier.size(96.dp),loop=true)}}}}}
        val first=capture("lottie-reduced")
        compose.mainClock.advanceTimeBy(2400)
        val second=capture("lottie-reduced-later")
        Assert.assertEquals(first.width,second.width)
        var differences=0
        for(y in 0 until first.height step 3)for(x in 0 until first.width step 3)if(first.getPixel(x,y)!=second.getPixel(x,y))differences++
        Assert.assertEquals("Reduced motion must render a stable frame",0,differences)
    }
}
