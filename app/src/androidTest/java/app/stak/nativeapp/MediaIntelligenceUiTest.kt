package app.stak.nativeapp

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import org.junit.*
import java.io.File
class MediaIntelligenceUiTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null;private lateinit var prefix:String
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="mi-ui-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix);NativeRuntime.settings.reducedMotion=true}
    @After fun finish(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}}
    private fun capture(name:String){compose.waitForIdle();android.os.SystemClock.sleep(600);val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.absolutePath,"media-intelligence");dir.mkdirs();val bitmap=(if(name=="quality")compose.onNode(isDialog())else compose.onRoot()).captureToImage().asAndroidBitmap();File(dir,"$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun zurgBrowserOnlyNeedsThreeConnectionFields(){
        compose.setContent{StakTheme{CompositionLocalProvider(app.stak.nativeapp.ui.components.LocalReducedMotion provides true){val scope=rememberCoroutineScope();val controller=remember{ConnectionSettingsController(NativeRuntime.settings,scope)};Box(Modifier.fillMaxSize().background(StakBackground)){MediaCenterScreen({},controller)}}}}
        compose.onNodeWithText("CONNECT ZURG").assertIsDisplayed();compose.onNodeWithText("CONNECT ZURG").performClick();compose.onNodeWithText("WebDAV URL").assertIsDisplayed();compose.onNodeWithText("Username (optional)").assertIsDisplayed();compose.onNodeWithText("Password").assertIsDisplayed();compose.onNodeWithText("Jellyfin library ID (optional)").assertDoesNotExist();compose.onNodeWithText("SAVE").assertIsNotEnabled();compose.onNodeWithText("CANCEL").performClick()
    }
    @Test fun inspectorExposesConfidenceWithoutInventingMissingFacts(){
        compose.setContent{StakTheme{CompositionLocalProvider(app.stak.nativeapp.ui.components.LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(StakBackground)){MediaQualityDialog(inferredQuality("Film.2160p.HDR10.mkv",10000),{})}}}}
        compose.onNodeWithText("Media quality").assertIsDisplayed();compose.onAllNodesWithText("2160p",substring=true).onLast().assertIsDisplayed();compose.onAllNodesWithText("INFERRED").onFirst().assertIsDisplayed()
        capture("quality")
        val bitmap=compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        var light=0;for(y in 0 until bitmap.height step 3)for(x in 0 until bitmap.width step 3)if(android.graphics.Color.red(bitmap.getPixel(x,y))>150)light++
        Assert.assertTrue("Inspector labels must be rendered with readable contrast",light>80)
        compose.onNodeWithText("REPORTED",substring=true).performScrollTo().assertIsDisplayed()
    }
}
