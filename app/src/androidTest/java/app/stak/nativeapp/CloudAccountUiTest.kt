package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.data.db.AppDatabase
import app.stak.nativeapp.ui.screens.CloudAccountScreen
import app.stak.nativeapp.ui.theme.StakTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class CloudAccountUiTest {
    @get:Rule val compose=createComposeRule()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix="cloud-ui-${java.util.UUID.randomUUID()}-"
    private val settings=SettingsStore(context,prefix)
    private val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @After fun cleanup(){scope.cancel();db.close();listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
    private fun capture(name:String){
        compose.waitForIdle();val dir=File(context.getExternalFilesDir(null),"accounts-1.0.1");dir.mkdirs();val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot();File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun registrationOnlyNeedsUsernameAndPasswordAndShowsActualAcknowledgement(){
        val cloud=FakeAccountCloud();val controller=CloudAccountController(settings,CloudSnapshotStore(settings,db),cloud,scope)
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xff090b0e)).statusBarsPadding()){CloudAccountScreen({},controller)}}}
        compose.onNodeWithText("Username").assertIsDisplayed();compose.onNodeWithText("Password").assertIsDisplayed();compose.onNodeWithText("Email").assertDoesNotExist();compose.onNodeWithText("Continue with Google").assertDoesNotExist()
        capture("sign-in")
        compose.onNodeWithText("CREATE ACCOUNT").performClick();compose.onNodeWithText("Username").performTextInput("fixture_user");compose.onNodeWithText("Password").performTextInput("synthetic-password")
        compose.onNodeWithTag("cloud-submit").performClick();compose.waitUntil(15000){controller.state.value.username=="fixture_user"&&!controller.state.value.busy}
        assertEquals(1,cloud.writes);assertTrue(controller.state.value.savedAt>0);compose.onNodeWithText("fixture_user").assertIsDisplayed();capture("signed-in")
    }
    @Test fun unconfiguredCloudCannotPretendToSignInOrBackUp(){
        val controller=CloudAccountController(settings,CloudSnapshotStore(settings,db),CloudAccountApi(CloudConfig("","")),scope)
        compose.setContent{StakTheme{CloudAccountScreen({},controller)}}
        compose.onNodeWithText("Username").performTextInput("fixture_user");compose.onNodeWithText("Password").performTextInput("synthetic-password")
        compose.onNodeWithTag("cloud-submit").assertIsNotEnabled();compose.onNodeWithText("Account service is not set up yet.").assertIsDisplayed();assertEquals(0,controller.state.value.savedAt)
    }
    @Test fun badPasswordRetainsLocalSettingsAndNoSavedClaim(){
        settings.torboxKey="synthetic-keep-me"
        val cloud=FakeAccountCloud().apply{users["fixture_user"]="correct-password"}
        val controller=CloudAccountController(settings,CloudSnapshotStore(settings,db),cloud,scope)
        compose.setContent{StakTheme{CloudAccountScreen({},controller)}}
        compose.onNodeWithText("Username").performTextInput("fixture_user");compose.onNodeWithText("Password").performTextInput("wrong-password");compose.onNodeWithTag("cloud-submit").performClick()
        compose.waitUntil(15000){!controller.state.value.busy&&controller.state.value.message!=null};assertNull(controller.state.value.username);assertEquals(0,controller.state.value.savedAt);assertEquals("synthetic-keep-me",settings.torboxKey);assertEquals(0,cloud.writes)
        compose.onNodeWithText("Could not sign in. Check your username and password.").assertIsDisplayed()
    }
}
