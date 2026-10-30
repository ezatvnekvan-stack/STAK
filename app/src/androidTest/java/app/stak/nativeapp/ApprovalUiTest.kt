package app.stak.nativeapp
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import org.junit.*
import java.io.File

class ApprovalUiTest {
    @get:Rule val compose=createComposeRule();private var previous:SettingsStore?=null;private lateinit var prefix:String
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="approval-ui135-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{torboxKey="synthetic-only";reducedMotion=true}}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
    @Composable private fun Frame(content:@Composable ()->Unit){StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(StakBackground)){content()}}}}
    private fun capture(name:String){val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:context.getExternalFilesDir(null)!!.path,"approvals-1.3.5");dir.mkdirs();val roots=compose.onAllNodes(isRoot());File(dir,"$name.png").outputStream().use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun unconfiguredFilterNeverClaimsIsolationIsActive(){compose.setContent{Frame{LibraryAccessDialog{}}};compose.onNodeWithText("Library access").assertIsDisplayed();compose.onNodeWithText("0 approved").assertExists();compose.onNodeWithText("Jellyfin paths verified").assertDoesNotExist();compose.onNodeWithText("Saving approvals alone does not filter your existing library.",substring=true).assertExists();capture("setup-required")}
    @Test fun approvalCanBeCategorizedWithoutChangingTorBox(){ApprovalStore().approve("17","synthetic-only",name="Chosen movie");compose.setContent{Frame{LibraryAccessDialog{}}};compose.waitUntil(5000){compose.onAllNodesWithText("Movies").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText("Movies").performScrollTo().assertIsDisplayed();capture("category-before");compose.onNodeWithText("Movies").performClick();try{compose.waitUntil(5000){compose.onAllNodesWithText("• Movies").fetchSemanticsNodes().isNotEmpty()}}finally{capture("category-after")};Assert.assertEquals("movie",ApprovalStore().read().entries.single().type);Assert.assertTrue(ApprovalStore().read().entries.single().approved);capture("saved-approval")}
    @Test fun revokeRequiresConfirmationAndRemainsPendingUntilSync(){ApprovalStore().approve("17","synthetic-only",name="Chosen movie",type="movie");compose.setContent{Frame{LibraryAccessDialog{}}};compose.waitUntil(5000){compose.onAllNodesWithText("Remove").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText("Remove").performScrollTo().performClick();compose.onNodeWithText("Remove library approval?").assertIsDisplayed();Assert.assertTrue(ApprovalStore().read().entries.single().approved);compose.onNodeWithText("REMOVE APPROVAL").performClick();compose.waitUntil(5000){!ApprovalStore().read().entries.single().approved};Assert.assertNotEquals("revoked",ApprovalStore().read().entries.single().state);capture("revoke-waits-for-server")}
    @Test fun corruptApprovalsAreNotOverwrittenBySetup(){NativeRuntime.settings.libraryApprovalsJson="broken";compose.setContent{Frame{LibraryAccessDialog{}}};compose.waitUntil(5000){compose.onAllNodesWithText("Saved approvals could not be read.",substring=true).fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText("CONNECT AND SYNC").assertIsNotEnabled();Assert.assertEquals("broken",NativeRuntime.settings.libraryApprovalsJson)}
}
