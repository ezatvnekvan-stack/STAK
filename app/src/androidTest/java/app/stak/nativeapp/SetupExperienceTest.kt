package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import java.io.File
import org.junit.*

class SetupExperienceTest{
    @get:Rule val compose=createComposeRule()
    private lateinit var settings:SettingsStore
    private lateinit var prefix:String
    @Before fun setup(){prefix="setup-${java.util.UUID.randomUUID()}-";settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix)}
    @After fun cleanup(){listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}}
    private fun capture(name:String){compose.waitForIdle();val folder=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.absolutePath,"release-1.0");folder.mkdirs();File(folder,"$name.png").outputStream().use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun field(label:String)=compose.onNode(hasSetTextAction() and hasAnyAncestor(hasContentDescription(label)))
    @Test fun onboardingAllowsOptionalConnectionsAndPersistsOnlyOnFinish(){
        var finished=false
        settings.torboxKey="existing-synthetic-key"
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){val scope=rememberCoroutineScope();val controller=remember{ConnectionSettingsController(settings,scope)};SetupScreen(settings,controller){settings.finishSetup();finished=true}}}}
        capture("01-welcome")
        compose.onNodeWithText("GET STARTED").performClick()
        compose.onNodeWithText("TorBox").assertExists();compose.onNodeWithText("Saved").assertExists();capture("02-connections")
        Assert.assertFalse(settings.setupComplete)
        compose.onNodeWithText("CONTINUE").performClick()
        compose.onNodeWithText("Search sources").assertExists();capture("03-sources")
        compose.onNodeWithText("CONTINUE").performClick()
        compose.onNodeWithText("Cinematic atmosphere").assertExists();capture("04-personalize")
        compose.onNodeWithText("OPEN STAK").performClick()
        compose.runOnIdle{Assert.assertTrue(finished);Assert.assertTrue(settings.setupComplete);Assert.assertEquals("existing-synthetic-key",settings.torboxKey)}
    }
    @Test fun configuredAioCanReplaceTorrentioAndCancelDoesNotSaveDraft(){
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){SourcesSettings({},settings)}}}
        compose.onNodeWithText("ADD AIOSTREAMS").performClick()
        field("Source manifest URL").performTextInput("stremio://example.org/private-config/manifest.json")
        compose.onNodeWithText("SAVE SOURCE").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Enable Torrentio").performClick()
        compose.runOnIdle{val all=StreamAddonStore(SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix)).all();Assert.assertEquals(listOf("AIOStreams"),all.filter{it.enabled}.map{it.name});Assert.assertEquals("https://example.org/private-config/manifest.json",all.last().manifestUrl)}
        capture("05-aio-sources")
        compose.onNodeWithText("ADD ANOTHER ADDON").performScrollTo().performClick()
        field("Source name").performTextInput("Unsaved")
        compose.onNodeWithText("CANCEL").performScrollTo().performClick()
        compose.runOnIdle{Assert.assertEquals(2,StreamAddonStore(settings).all().size)}
    }
    @Test fun malformedSourceIsRejectedWithoutChangingSavedConfiguration(){
        compose.setContent{StakTheme{SourcesSettings({},settings)}}
        compose.onNodeWithText("ADD AIOSTREAMS").performClick()
        field("Source manifest URL").performTextInput("https://example.org/not-manifest")
        compose.onNodeWithText("SAVE SOURCE").performScrollTo().performClick()
        compose.onNodeWithText("Use a manifest.json link for addons, or Add Torznab indexer for an indexer API URL.").assertExists()
        compose.runOnIdle{Assert.assertEquals(1,StreamAddonStore(settings).all().size)}
    }
}
