@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalView
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

class PreviewThirteenTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null;private lateinit var prefix:String
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="preview-thirteen-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix);NativeRuntime.settings.hasOnboarded=true}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}}
    private fun capture(name:String,window:Boolean=false):Bitmap{compose.waitForIdle();val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.absolutePath,"v1.0.1");dir.mkdirs();val bitmap=if(window){android.os.SystemClock.sleep(500);InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()}else compose.onRoot().captureToImage().asAndroidBitmap();File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};return bitmap}
    @Test fun glassActuallyBlursSharpStripesAndTracksTheUnderlyingColor(){
        var tint by mutableStateOf(Color.White)
        compose.setContent{StakTheme{
            val layer=rememberGraphicsLayer();val source=remember(layer){GlassSource(layer,crossWindow=true)};val view=LocalView.current
            CompositionLocalProvider(LocalGlassSource provides source){
                Box(Modifier.fillMaxSize().recordGlassSource(source,view).drawBehind{drawRect(Color.Black);var x=0f;while(x<size.width){drawRect(tint,androidx.compose.ui.geometry.Offset(x,0f),androidx.compose.ui.geometry.Size(5.dp.toPx(),size.height));x+=10.dp.toPx()}}){
                    GlassDialog({}){Box(Modifier.fillMaxWidth().height(180.dp))}
                }
            }
        }}
        val frame=capture("real-blur-stripes",true);val y=frame.height/2+45
        val values=(frame.width/2-60..frame.width/2+60).map{android.graphics.Color.red(frame.getPixel(it,y))/255f}
        Assert.assertTrue("Blur should suppress the original high-frequency stripe edges",values.max()-values.min()<.07f)
        Assert.assertTrue("Recorded background must actually be visible",values.average()>.025)
        compose.runOnIdle{tint=Color.Blue};val blue=capture("real-blur-blue",true);val pixel=blue.getPixel(blue.width/2,y)
        Assert.assertTrue(android.graphics.Color.blue(pixel)>android.graphics.Color.red(pixel)*2)
    }
    @org.junit.Ignore("Manual watched mutations retired in 1.3.4; PassiveTracking tests verify read-only behavior")
    @Test fun watchedSeriesHasUnwatchSeasonActionAndOnlyWritesAfterConfirmation(){
        val media=MediaItem("tt1234567","tt1234567",type="series",name="Watched Series",watched=true)
        val episodes=listOf(Episode(1,1,"One"),Episode(1,2,"Two"))
        val vm=TitleViewModel(detailsLoader={_,_->TitleDetails(media,seasons=listOf(1),episodes=episodes)})
        var states by mutableStateOf<Map<String,Boolean>>(emptyMap());var writes=0;var last:Boolean?=null
        compose.setContent{StakTheme{TitleScreen(media,{},false,false,{},null,null,episodeStates=states,onSetSeasonWatched={_,rows,played->writes++;last=played;states=rows.associate{"tt1234567:${it.season}:${it.episode}" to played}},viewModel=vm)}}
        compose.onNodeWithText("2 / 2 watched").assertExists();compose.onNodeWithText("UNWATCH SEASON").performScrollTo().performClick();Assert.assertEquals(0,writes)
        compose.onNodeWithText("MARK SEASON UNWATCHED").performClick();Assert.assertEquals(false,last);Assert.assertEquals(1,writes)
        compose.onNodeWithText("WATCH SEASON").assertExists();compose.onNodeWithText("0 / 2 watched").assertExists();capture("watched-season-actions")
    }
    @Test fun jellyfinUsesCompactInAndRedNotInLibraryStates(){
        compose.setContent{StakTheme{Column(Modifier.fillMaxSize().background(Color(0xff080808)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){JellyfinBadge(available=true);JellyfinBadge(available=false,connected=true);RatingStrip(8.0,"92%","76")}}}
        compose.onNodeWithText("In library").assertExists();compose.onNodeWithText("Not in library").assertExists();compose.onNodeWithText("Available on Jellyfin").assertDoesNotExist();capture("compact-status-ratings")
    }
    @Test fun crewCardsHaveEqualHeightsAndTwoColumns(){
        val media=MediaItem("1",name="Fixture")
        val details=TitleDetails(media,crew=listOf(TitlePerson("1","Dan Erickson","Creator · Writer"),TitlePerson("2","Anna Ouyang Moench","Writer"),TitlePerson("3","Helen Leigh","Writer"),TitlePerson("4","Chris Black","Writer")))
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xff080808))){TitleExtras(details,media,{})}}}
        val a=compose.onNodeWithText("Dan Erickson").fetchSemanticsNode().boundsInRoot
        val b=compose.onNodeWithText("Anna Ouyang Moench").fetchSemanticsNode().boundsInRoot
        val c=compose.onNodeWithText("Helen Leigh").fetchSemanticsNode().boundsInRoot
        Assert.assertEquals(a.height,b.height,.1f);Assert.assertEquals(a.top,b.top,.1f);Assert.assertTrue(c.top>=a.bottom);capture("crew-two-columns")
    }
    @Test fun titleTrailerActionOpensAnInAppWindowAndClosesBackToDetails(){
        val media=MediaItem("1",name="Trailer Fixture")
        val vm=TitleViewModel(detailsLoader={_,_->TitleDetails(media,trailerUrl="https://unsupported.invalid/trailer")})
        compose.setContent{StakTheme{TitleScreen(media,{},false,false,{},null,null,viewModel=vm)}}
        compose.onNodeWithText("TRAILER").performClick()
        compose.onNodeWithContentDescription("Close trailer").assertExists()
        compose.onNodeWithText("No supported trailer is available for this title.").assertExists()
        compose.onNodeWithContentDescription("Close trailer").performClick()
        compose.onNodeWithText("TRAILER").assertExists()
    }
    @Test fun supportedTrailerCreatesAnOwnedWebViewWithAppOriginAndReleasesItOnClose(){
        var open by mutableStateOf(true);var browser:android.webkit.WebView?=null;var origin:String?=null;var html:String?=null
        compose.setContent{StakTheme{if(open)TrailerDialog("Trailer Fixture","https://youtu.be/AbCdEf12_-3",{open=false},loadContent={view,page,base->browser=view;origin=base;html=page;view.loadDataWithBaseURL(base,"<html><body style='background:black;color:white'>Synthetic trailer fixture</body></html>","text/html","UTF-8",null)})}}
        compose.waitUntil(5000){browser!=null};compose.onNodeWithTag("trailer-popup").getUnclippedBoundsInRoot().let{Assert.assertTrue(it.bottom-it.top<450.dp)};compose.onNodeWithTag("trailer-player").assertHeightIsAtLeast(140.dp);capture("trailer-popup",window=true)
        Assert.assertTrue(origin!!.startsWith("https://app.stak.nativeapp"));Assert.assertTrue(html!!.contains("enablejsapi=1"));compose.runOnIdle{Assert.assertFalse(browser!!.settings.allowFileAccess)}
        var fullClosed=0;compose.runOnIdle{browser!!.webChromeClient!!.onShowCustomView(android.view.View(browser!!.context),android.webkit.WebChromeClient.CustomViewCallback{fullClosed++})};compose.onNodeWithTag("trailer-popup").assertHeightIsAtLeast(700.dp);compose.runOnIdle{browser!!.webChromeClient!!.onHideCustomView()};compose.waitForIdle();Assert.assertEquals(1,fullClosed);compose.onNodeWithContentDescription("Close trailer").performClick();compose.waitForIdle();compose.runOnIdle{Assert.assertNull(browser!!.parent)}
    }
}
