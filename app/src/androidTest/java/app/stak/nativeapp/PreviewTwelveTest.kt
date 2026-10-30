@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.performance.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import org.junit.*
import java.io.File
import org.json.JSONObject
import kotlin.math.abs

class PreviewTwelveTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null;private lateinit var prefix:String;private val files=mutableListOf<File>()
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="preview-twelve-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix);NativeRuntime.settings.hasOnboarded=true}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)};files.forEach{it.delete()}}
    private fun folder():File=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.absolutePath,"preview-12").apply{mkdirs()}
    private fun capture(name:String){val bitmap=compose.onRoot().captureToImage().asAndroidBitmap();File(folder(),"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun activity(context:Context):Activity=if(context is Activity)context else activity((context as ContextWrapper).baseContext)
    private fun image(name:String,color:Int):String{val file=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"$prefix$name.png");files+=file;val bitmap=Bitmap.createBitmap(360,540,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color);file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle();return file.absolutePath}
    @Test fun refreshRequestUsesOnlySupportedModesAndKeepsResolution(){
        var window:Window?=null
        compose.setContent{window=activity(LocalContext.current).window;Box(Modifier.fillMaxSize())}
        compose.runOnIdle{
            val target=window!!;val before=target.attributes;val display=target.decorView.display;val mode=display.mode
            val expected=preferredSmoothMode(display.supportedModes.map{DisplayChoice(it.modeId,it.physicalWidth,it.physicalHeight,it.refreshRate)},mode.physicalWidth,mode.physicalHeight)!!
            requestSmoothDisplay(target)
            Assert.assertEquals(expected.hz,target.attributes.preferredRefreshRate,.1f)
            Assert.assertEquals(mode.physicalWidth,expected.width);Assert.assertEquals(mode.physicalHeight,expected.height)
            File(folder(),"display-request.json").writeText(JSONObject().put("actualDisplayHz",display.refreshRate).put("requestedHz",target.attributes.preferredRefreshRate).put("ninetyHzHardwareVerified",false).put("device",android.os.Build.MODEL).toString(2))
            target.attributes=before
        }
    }
    @Test fun reducedMotionHasStaticLoadingAndSnapsProgressChanges(){
        var progress by mutableStateOf(.2f)
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Column(Modifier.fillMaxSize().background(Color.Black)){ShimmerBox(Modifier.size(80.dp).testTag("shimmer"));StakLoadingProgress(Modifier.testTag("loading"));StakProgress(progress,Modifier.testTag("progress"))}}}}
        val before=compose.onNodeWithTag("shimmer").captureToImage().toPixelMap()[20,20]
        compose.mainClock.advanceTimeBy(1600)
        Assert.assertEquals(before,compose.onNodeWithTag("shimmer").captureToImage().toPixelMap()[20,20])
        compose.runOnIdle{progress=.8f};compose.onNodeWithTag("progress").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(.8f,0f..1f))
    }
    @Test fun titleUsesArtworkColorsAndTheBackdropBlendsWithoutAHardBoundary(){
        val warm=image("warm",0xffba6328.toInt());val cool=image("cool",0xff245fbd.toInt())
        var selected by mutableStateOf(MediaItem("warm",name="Warm Film",background=warm,genres=listOf("Drama"),overview="A synthetic fixture used to verify a continuous cinematic backdrop."))
        val vm=TitleViewModel(detailsLoader={_,_->TitleDetails(selected)})
        compose.setContent{StakTheme{TitleScreen(selected,{},false,false,{},null,null,viewModel=vm)}}
        compose.waitUntil(8000){val pixel=compose.onRoot().captureToImage().toPixelMap()[4,360];pixel.red>pixel.blue*1.4f}
        var pixels=compose.onRoot().captureToImage().toPixelMap()
        val a=pixels[4,388];val b=pixels[4,394]
        Assert.assertTrue("Hero/body edge must blend into the same page surface",abs(a.red-b.red)+abs(a.green-b.green)+abs(a.blue-b.blue)<.06f)
        capture("warm-details")
        compose.runOnIdle{selected=selected.copy(id="cool",name="Cool Series",type="series",background=cool)}
        compose.waitUntil(8000){val pixel=compose.onRoot().captureToImage().toPixelMap()[4,360];pixel.blue>pixel.red*1.4f}
        capture("cool-details")
        compose.onRoot().performTouchInput{swipeUp()};capture("cool-details-scrolled")
    }
    @Test fun repeatedHomeScrollProducesAFrameTimingReportWithoutNetworkArtwork(){
        val artwork=image("poster",0xff284965.toInt());val posters=(1..48).map{MediaItem("fixture-$it",name="Fixture $it",poster=artwork,year="2026")}
        val feed=HomeFeed(rows=HomeSections.defaults().filter{it.type=="movie"&&it.id!="continue"&&it.id!="watchlist"}.map{HomeRow(it.id,it.title,posters)})
        val vm=HomeViewModel(feedLoader={feed});var window:Window?=null
        compose.setContent{window=activity(LocalContext.current).window;StakTheme{HomeScreen({_,_->},{},{},{},{},{},sharedScope=null,animatedScope=null,reducedMotion=false,viewModel=vm)}}
        compose.waitUntil(5000){vm.feed.value!=null};compose.waitForIdle()
        val samples=java.util.Collections.synchronizedList(mutableListOf<Long>())
        val thread=HandlerThread("fixture-frame-metrics").apply{start()}
        val listener=Window.OnFrameMetricsAvailableListener{_,metrics,_->val total=metrics.getMetric(FrameMetrics.TOTAL_DURATION);if(total>0)samples.add(total)}
        try{
            compose.runOnIdle{window!!.addOnFrameMetricsAvailableListener(listener,Handler(thread.looper))}
            repeat(3){compose.onRoot().performTouchInput{swipeUp(durationMillis=500)};compose.onRoot().performTouchInput{swipeDown(durationMillis=500)}}
            compose.waitUntil(5000){samples.size>0}
        }finally{compose.runOnIdle{window!!.removeOnFrameMetricsAvailableListener(listener)};thread.quitSafely()}
        val ms=synchronized(samples){samples.map{it/1000000.0}.sorted()}
        val report=JSONObject().put("build","debug instrumentation fixture").put("device",android.os.Build.MODEL).put("displayHz",window!!.decorView.display.refreshRate).put("frames",ms.size).put("p50Ms",ms[ms.size/2]).put("p95Ms",ms[(ms.size*.95).toInt().coerceAtMost(ms.lastIndex)]).put("over11Point11Ms",ms.count{it>1000.0/90}).put("ninetyFpsProven",false).put("scope","Synthetic Home shelves; emulator results are not phone performance guarantees")
        File(folder(),"scroll-frame-timings.json").writeText(report.toString(2));capture("home-scroll-fixture")
    }
}
