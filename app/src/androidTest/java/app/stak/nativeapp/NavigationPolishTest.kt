package app.stak.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import org.junit.*
import java.io.File

class NavigationPolishTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null;private lateinit var prefix:String
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="nav125-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{reducedMotion=true}}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
    private val tabs=listOf(NavTab(Dest.Home,"Home",Lucide.House,"home"),NavTab(Dest.Discover,"Discover",Lucide.Compass,"discover"),NavTab(Dest.Watchlist,"Watchlist",Lucide.Bookmark,"watchlist"),NavTab(Dest.Tracking,"Tracking",Lucide.Tracking,"tracking"),NavTab(Dest.Library,"Library",Lucide.Library,"library"),NavTab(Dest.Account,"Settings",Lucide.Settings,"account"))
    private fun capture(name:String){val bitmap=compose.onRoot().captureToImage().asAndroidBitmap();val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: context.getExternalFilesDir(null)!!.path,"navigation-1.2.5");dir.mkdirs();File(dir,"$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun dotFollowsSelectedTabAndSelectionIsAccessible(){
        var current:Dest by mutableStateOf(Dest.Home)
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){val layer=rememberGraphicsLayer();Box(Modifier.fillMaxSize().background(Color(0xFF08090B)).padding(16.dp)){StakBottomBar(current,layer,tabs,{current=it})}}}}
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.onNodeWithTag("navigation-active-dot").assertIsDisplayed()
        val center=compose.onNodeWithTag("navigation-active-dot").fetchSemanticsNode().boundsInRoot.center
        val pixels=compose.onRoot().captureToImage().asAndroidBitmap();val red=pixels.getPixel(center.x.toInt(),center.y.toInt())
        Assert.assertTrue("Selection dot must actually render red inside the navigation",android.graphics.Color.red(red)>160&&android.graphics.Color.green(red)<90)
        val first=compose.onNodeWithTag("navigation-active-dot").getUnclippedBoundsInRoot();capture("home-selected")
        compose.onNodeWithContentDescription("Library").performClick();compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.onNodeWithTag("navigation-active-dot").assertIsDisplayed();val last=compose.onNodeWithTag("navigation-active-dot").getUnclippedBoundsInRoot();Assert.assertTrue(last.left>first.left);Assert.assertEquals(Dest.Library,current);capture("library-selected")
        compose.runOnIdle{current=Dest.TorBox};Assert.assertEquals(last.left,compose.onNodeWithTag("navigation-active-dot").getUnclippedBoundsInRoot().left)
    }
    @Test fun dotoRanksUseNaturalDigitsWithoutOutlineOrLeadingZero(){
        compose.setContent{StakTheme{Row(Modifier.background(Color.Black).padding(16.dp)){PosterRank(1);PosterRank(10)}}}
        compose.onNodeWithContentDescription("Rank 1").assertTextEquals("1");compose.onNodeWithContentDescription("Rank 10").assertTextEquals("10");compose.onNodeWithText("01").assertDoesNotExist();capture("doto-ranks")
    }
}
