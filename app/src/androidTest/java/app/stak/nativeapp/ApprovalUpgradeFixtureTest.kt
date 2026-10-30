package app.stak.nativeapp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import org.junit.Test
import org.junit.Assert.*

/** Disposable emulator data, never shipped in the application APK. */
class ApprovalUpgradeFixtureTest {
    @Test fun seed(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val settings=SettingsStore(context);NativeRuntime.settings=settings
        SecureStorage(context).putSecretChecked("torbox_key","synthetic-upgrade135")
        settings.finishSetup();settings.reducedMotion=true
        ApprovalStore().approve("13517","synthetic-upgrade135",name="Upgrade approval",type="movie")
        assertTrue(context.getSharedPreferences("approval_upgrade135",0).edit().putString("playback",settings.playbackJson).commit())
    }
}
class ApprovalUpgradeVerifyTest {
    @Test fun approvalAndTrackingPersist(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val settings=SettingsStore(context);NativeRuntime.settings=settings
        val row=ApprovalStore().read().entries.single{it.id=="13517"&&it.scope==torboxScope()}
        assertFalse(row.approved);assertEquals("series",row.type)
        assertEquals(context.getSharedPreferences("approval_upgrade135",0).getString("playback",null),settings.playbackJson)
        assertFalse(ApprovalStore().read().connection.enabled)
    }
}
