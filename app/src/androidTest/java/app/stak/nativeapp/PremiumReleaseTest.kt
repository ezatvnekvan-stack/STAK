@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import org.junit.*
import java.io.File

class PremiumReleaseTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null;private lateinit var prefix:String
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="premium-release-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix);NativeRuntime.settings.hasOnboarded=true}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}}
    private fun capture(name:String){compose.waitForIdle();val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.absolutePath,"v2.4.1");dir.mkdirs();val bitmap=compose.onRoot().captureToImage().asAndroidBitmap();File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun pixels()=compose.onRoot().captureToImage().toPixelMap()
    private fun fingerprint():Long{val pixels=pixels();var hash=1L;for(y in 0 until pixels.height step 3)for(x in 0 until pixels.width step 3)hash=hash*31+pixels[x,y].toArgb();return hash}
    @Test fun cinematicLightMovesAtRestAndFreezesForScrollingAndReducedMotion(){
        compose.mainClock.autoAdvance=false
        var paused by mutableStateOf(false);var reduced by mutableStateOf(false)
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides reduced){Box(Modifier.fillMaxSize()){val tint=rememberUpdatedState(Color(0xFF17202B));CinematicBackground(tint,paused={paused})}}}}
        compose.mainClock.advanceTimeBy(64);val before=pixels();val first=fingerprint();compose.mainClock.advanceTimeBy(2400);val after=pixels();val animated=fingerprint();Assert.assertNotEquals(first,animated);var peak=0f;var changed=0;for(y in before.height/3 until before.height*9/10)for(x in 0 until before.width){val a=before[x,y];val b=after[x,y];val delta=maxOf(kotlin.math.abs(a.red-b.red),kotlin.math.abs(a.green-b.green),kotlin.math.abs(a.blue-b.blue));peak=maxOf(peak,delta);if(delta>.002f)changed++};Assert.assertTrue("Wave must be visible with dark artwork, peak=$peak",peak>.004f);Assert.assertTrue("Visible motion must cover more than isolated pixels, count=$changed",changed>1000)
        compose.runOnIdle{paused=true};compose.mainClock.advanceTimeBy(64);val frozen=fingerprint();compose.mainClock.advanceTimeBy(2400);Assert.assertEquals(frozen,fingerprint())
        compose.runOnIdle{paused=false;reduced=true};compose.mainClock.advanceTimeBy(64);val accessible=fingerprint();compose.mainClock.advanceTimeBy(2400);Assert.assertEquals(accessible,fingerprint())
        compose.mainClock.autoAdvance=true;capture("cinematic-light")
    }
    @Test fun scrollingDetailsRevealsAWorkingGlassHeader(){
        val media=MediaItem("1",name="Premium Detail",overview="A carefully composed cinematic interface. ".repeat(30),genres=listOf("Drama","Adventure"),year="2026")
        val vm=TitleViewModel(detailsLoader={_,_->TitleDetails(media)})
        var backs=0
        compose.setContent{StakTheme{TitleScreen(media,{backs++},false,false,{},null,null,viewModel=vm)}}
        capture("details-top");compose.onNodeWithText("Read more").performClick();compose.onRoot().performTouchInput{swipe(androidx.compose.ui.geometry.Offset(width*.1f,height*.8f),androidx.compose.ui.geometry.Offset(width*.1f,height*.2f))}
        compose.onNodeWithContentDescription("Back to browsing").assertIsDisplayed();capture("details-sticky-header")
        compose.onNodeWithContentDescription("Back to browsing").performClick();Assert.assertEquals(1,backs)
    }
    @Test fun sharedGlassControlsStayInteractiveAndTextStaysSharp(){
        var query by mutableStateOf("");var tab by mutableStateOf("one")
        compose.setContent{StakTheme{
            val layer=rememberGraphicsLayer();val source=remember(layer){GlassSource(layer,preBlurred=true)};val view=LocalView.current
            CompositionLocalProvider(LocalInlineGlassSource provides source){Box(Modifier.fillMaxSize()){
                Box(Modifier.fillMaxSize().recordGlassSource(source,view).background(Brush.linearGradient(listOf(Color(0xff33202d),Color(0xff102231)))))
                Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                    StakPageHeader("Discover",subtitle="Made for your next watch")
                    StakTextField(query,{query=it},"Search titles")
                    StakSegmented(listOf("one" to "Movies","two" to "Series"),tab,{tab=it},Modifier.fillMaxWidth(),fillWidth=true)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){StakButton("Trailer",{});StakButton("Watchlist",{});StakChip("4K",true,{});androidx.compose.material3.Switch(true,{})}
                    GlassSurface(source=source){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){androidx.compose.material3.Text("PIXEL GLASS",fontFamily=DotoFont,fontWeight=androidx.compose.ui.text.font.FontWeight.Black,color=Color.White);StakButton("Add to watchlist",{});StakButton("Mark watched",{})}}
                }
            }}
        }}
        compose.onNode(hasSetTextAction()).performTextInput("Dune");Assert.assertEquals("Dune",query)
        compose.onNodeWithText("SERIES").performClick();Assert.assertEquals("two",tab)
        compose.onNodeWithText("Discover").assertIsDisplayed();compose.onNodeWithText("Made for your next watch").assertDoesNotExist();compose.onNodeWithText("SERIES").assertHeightIsAtLeast(44.dp);capture("outline-design-system")
    }
    @Test fun continueWatchingRetainsResumeAndNextUpUnderTheGlassBadges(){
        val vm=HomeViewModel(feedLoader={HomeFeed()})
        val items=listOf(MediaItem("1",name="Resume Movie",progress=.4f,durationMs=600000,positionMs=240000),MediaItem("2",type="series",name="Next Series",nextUp=true,season=1,episode=2))
        compose.setContent{StakTheme{HomeScreen({_,_->},{},{},{},{},{},continueWatching=items,jellyfinConnected=true,sharedScope=null,animatedScope=null,reducedMotion=true,viewModel=vm)}}
        compose.onNodeWithText("RESUME").assertExists();compose.onNodeWithText("NEXT UP").assertExists();capture("home-glass")
    }
}
