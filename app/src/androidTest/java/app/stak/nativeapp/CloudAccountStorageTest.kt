package app.stak.nativeapp

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.data.db.*
import com.google.gson.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.util.*

internal class FakeAccountCloud:CloudAccountGateway {
    override val config=CloudConfig("https://cloud.example.invalid","sb_publishable_synthetic")
    val users=mutableMapOf<String,String>();val copies=mutableMapOf<String,CloudRemote>();var writes=0;var authentications=0;var unknownOutcome=false
    override fun authenticate(username:String,password:String,create:Boolean):CloudAuth {
        authentications++
        val name=CloudBackupCodec.username(username)
        if(create){check(name !in users);users[name]=password}
        if(users[name]!=password)throw CloudFailure("auth","Could not sign in. Check your username and password.")
        val id=UUID.nameUUIDFromBytes(name.toByteArray()).toString()
        return CloudAuth(id,name,"synthetic-access","synthetic-refresh",System.currentTimeMillis()+3600000)
    }
    override fun refresh(auth:CloudAuth)=auth.copy(expiresAt=System.currentTimeMillis()+3600000)
    override fun read(auth:CloudAuth)=synchronized(this){copies[auth.userId]?:CloudRemote(0,null,null,0)}
    override fun write(auth:CloudAuth,expected:Long,fingerprint:String,envelope:JsonObject,operation:String):CloudRemote=synchronized(this){
        if((copies[auth.userId]?.revision?:0)!=expected)throw CloudFailure("conflict","A newer backup exists. Sign in again to restore it.")
        writes++;val reply=CloudRemote(expected+1,fingerprint,envelope.deepCopy(),System.currentTimeMillis())
        copies[auth.userId]=reply
        if(unknownOutcome){unknownOutcome=false;throw java.io.IOException("Synthetic acknowledgement lost")}
        reply
    }
    override fun logout(auth:CloudAuth){}
}

class CloudAccountStorageTest {
    data class Device(val settings:SettingsStore,val db:AppDatabase,val scope:CoroutineScope){val snapshots=CloudSnapshotStore(settings,db)}
    private val devices=mutableListOf<Device>()
    private fun device():Device {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val settings=SettingsStore(context,"cloud-storage-${UUID.randomUUID()}-")
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        return Device(settings,db,CoroutineScope(SupervisorJob()+Dispatchers.IO)).also{devices+=it}
    }
    @After fun cleanup(){devices.forEach{d->d.scope.cancel();d.db.close();listOf("stak_settings","stak_secrets").forEach{d.settings.applicationContext.deleteSharedPreferences(d.settings.storageNamespace+it)}}}
    private suspend fun seed(d:Device){
        d.settings.finishSetup();d.settings.torboxKey="synthetic-torbox-source";d.settings.jellyfinUrl="https://jellyfin.example.invalid";d.settings.jellyfinToken="synthetic-jellyfin";d.settings.jellyfinUserId="user-one";d.settings.discoveryJson="{\"genre\":\"drama\"}"
        d.settings.streamAddonsJson="[]";d.settings.torznabIndexersJson="[]";d.settings.libraryApprovalsJson="{\"version\":1}"
        d.db.watchlistDao().upsert(WatchlistEntity("tt1234567","movie","Saved movie","2026",null,.3f,1234))
        d.db.trackingDao().upsert(TrackingEntity("earlier","movie","Earlier watched record",true,1f,1234,scope="local",updatedAt=1234))
        d.db.preferenceDao().set(PreferenceEntity("personal-tracker-v1:local","{\"preserved\":\"old-records\"}"))
    }
    private fun controller(d:Device,cloud:FakeAccountCloud)=CloudAccountController(d.settings,d.snapshots,cloud,d.scope)
    private suspend fun waitUntil(condition:()->Boolean){withTimeout(15000){while(!condition())delay(25)}}
    private suspend fun enter(c:CloudAccountController,create:Boolean=true,name:String="fixture_user"){c.signIn(name,"synthetic-password",create);waitUntil{!c.state.value.busy};assertNull(c.state.value.message);assertEquals(name,c.state.value.username)}

    @Test fun registrationSavesAllExistingLocalDataWithoutSavingThePassword()=runBlocking{
        val d=device();seed(d);val cloud=FakeAccountCloud();val c=controller(d,cloud);enter(c)
        assertEquals(1,cloud.writes);assertTrue(c.state.value.savedAt>0)
        val auth=cloud.authenticate("fixture_user","synthetic-password",false);val backup=cloud.read(auth)
        val restored=CloudBackupCodec.open(backup.envelope!!,CloudBackupCodec.key("synthetic-password".toCharArray(),auth.userId),auth.userId)
        assertEquals("synthetic-torbox-source",restored.getAsJsonObject("secrets")["torbox_key"].asString)
        assertEquals(1,restored.getAsJsonArray("watchlist").size());assertEquals(1,restored.getAsJsonArray("tracking").size());assertEquals(1,restored.getAsJsonArray("documents").size())
        assertFalse(d.settings.cloudValue("cloud_session")!!.contains("synthetic-password"));assertFalse(backup.envelope.toString().contains("synthetic-torbox-source"))
    }
    @Test fun freshInstallRestoresBeforeAnyUploadAndReencryptsCredentialsLocally()=runBlocking{
        val first=device();seed(first);val originalDeviceId=first.settings.jellyfinDeviceId
        val cloud=FakeAccountCloud();enter(controller(first,cloud))
        val second=device();val secondDeviceId=second.settings.jellyfinDeviceId;val c=controller(second,cloud);enter(c,false)
        assertEquals(1,cloud.writes);assertEquals("synthetic-torbox-source",second.settings.torboxKey);assertEquals("synthetic-jellyfin",second.settings.jellyfinToken)
        assertEquals(first.db.watchlistDao().getAll(),second.db.watchlistDao().getAll());assertEquals(first.db.trackingDao().getAll(),second.db.trackingDao().getAll());assertEquals(first.db.preferenceDao().getAllForBackup(),second.db.preferenceDao().getAllForBackup())
        assertNotEquals(originalDeviceId,secondDeviceId);assertEquals(secondDeviceId,second.settings.jellyfinDeviceId)
    }
    @Test fun changedDataIsAutomaticallySavedAndCloudMetadataDoesNotLoop()=runBlocking{
        val d=device();seed(d);val cloud=FakeAccountCloud();val c=controller(d,cloud);enter(c);c.start();delay(2800)
        assertEquals(1,cloud.writes)
        d.settings.torboxKey="synthetic-updated";d.db.preferenceDao().set(PreferenceEntity("extra-history","preserved"))
        waitUntil{cloud.writes==2};delay(2800);assertEquals(2,cloud.writes)
    }
    @Test fun staleDeviceCannotOverwriteAnotherDevicesNewerBackup()=runBlocking{
        val cloud=FakeAccountCloud();val first=device();seed(first);val a=controller(first,cloud);enter(a)
        val second=device();val b=controller(second,cloud);enter(b,false)
        first.settings.torboxKey="synthetic-newer";assertTrue(a.syncNow())
        second.settings.torboxKey="synthetic-local-change";assertFalse(b.syncNow())
        assertEquals(2,cloud.writes);assertEquals("synthetic-local-change",second.settings.torboxKey);assertTrue(b.state.value.message!!.contains("newer backup"))
    }
    @Test fun uncertainSaveReconcilesWithoutUploadingAgain()=runBlocking{
        val cloud=FakeAccountCloud();val d=device();seed(d);val c=controller(d,cloud);enter(c)
        d.settings.torboxKey="synthetic-new";cloud.unknownOutcome=true;assertFalse(c.syncNow());assertEquals(2,cloud.writes)
        assertTrue(c.syncNow());assertEquals(2,cloud.writes);assertNull(c.state.value.message)
    }
    @Test fun anotherEmptyAccountCannotReceivePreviousAccountsData()=runBlocking{
        val cloud=FakeAccountCloud();val d=device();seed(d);val c=controller(d,cloud);enter(c)
        c.signOut();waitUntil{!c.state.value.busy&&c.state.value.username==null}
        val calls=cloud.authentications;c.signIn("different_user","synthetic-password",true);waitUntil{!c.state.value.busy}
        assertEquals(calls,cloud.authentications);assertEquals(1,cloud.writes);assertEquals("synthetic-torbox-source",d.settings.torboxKey)
    }
    @Test fun corruptRemoteSnapshotCannotReplaceLocalData()=runBlocking{
        val cloud=FakeAccountCloud();val first=device();seed(first);enter(controller(first,cloud))
        val id=cloud.copies.keys.single();val old=cloud.copies[id]!!;val bad=old.envelope!!.deepCopy();bad.addProperty("nonce","AAAAAAAAAAAAAAAA");cloud.copies[id]=old.copy(envelope=bad)
        val second=device();second.settings.torboxKey="synthetic-keep-me";val c=controller(second,cloud);c.signIn("fixture_user","synthetic-password",false);waitUntil{!c.state.value.busy}
        assertEquals("synthetic-keep-me",second.settings.torboxKey);assertNull(c.state.value.username);assertEquals(1,cloud.writes)
    }
    @Test fun failedRestoreRollsBackDatabaseSettingsAndAccountBinding()=runBlocking{
        val first=device();seed(first);val second=device();second.settings.torboxKey="synthetic-new"
        val original=first.snapshots.capture();first.settings.cloudValue("cloud_session","old-session")
        assertTrue(runCatching{first.snapshots.restore(second.snapshots.capture()){first.settings.cloudValue("cloud_session","new-session");error("Synthetic commit failure")}}.isFailure)
        assertEquals(original.fingerprint,first.snapshots.capture().fingerprint);assertEquals("old-session",first.settings.cloudValue("cloud_session"));assertNull(first.settings.cloudValue("cloud_restore_recovery"))
    }
    @Test fun crashJournalRestoresPreviousDataBeforeNormalUse()=runBlocking{
        val d=device();seed(d);val previous=d.snapshots.capture()
        val journal=JsonObject().apply{addProperty("version",1);add("previous",previous.json);add("metadata",JsonObject().apply{addProperty("cloud_session","old-session");addProperty("cloud_bound_user","old-user");addProperty("cloud_bound_name","old-name")})}
        d.settings.cloudValue("cloud_restore_recovery",journal.toString());d.settings.torboxKey="synthetic-half-written";d.db.trackingDao().clearForRestore();d.settings.cloudValue("cloud_session","partial-new-session")
        d.snapshots.recoverPending()
        assertEquals(previous.fingerprint,d.snapshots.capture().fingerprint);assertEquals("old-session",d.settings.cloudValue("cloud_session"));assertEquals("old-user",d.settings.cloudValue("cloud_bound_user"));assertNull(d.settings.cloudValue("cloud_restore_recovery"))
    }
}
