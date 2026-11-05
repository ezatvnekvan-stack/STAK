package app.stak.nativeapp

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.data.db.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Explicit opt-in only: creates synthetic QA accounts on the configured service. */
class CloudHostedIntegrationTest {
    @Test fun hostedBackupRestoresFreshInstallationAndKeepsAccountsIsolated()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("stakHostedCloud")=="true")
        val config=CloudConfig.bundled()
        assertTrue(config.configured)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val suffix=UUID.randomUUID().toString().replace("-","").take(16)
        val username="qa_$suffix"
        val password="Qa-"+UUID.randomUUID().toString()
        val settings=(1..2).map{SettingsStore(context,"hosted-$suffix-$it-")}
        val databases=(1..2).map{Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()}
        val scopes=(1..2).map{CoroutineScope(SupervisorJob()+Dispatchers.IO)}
        val api=CloudAccountApi(config)
        var auth:CloudAuth?=null
        var other:CloudAuth?=null
        suspend fun settled(c:CloudAccountController){withTimeout(60000){while(c.state.value.busy)delay(50)};assertNull(c.state.value.message);assertEquals(username,c.state.value.username);assertTrue(c.state.value.savedAt>0)}
        try {
            settings[0].finishSetup();settings[0].torboxKey="synthetic-hosted-secret"
            databases[0].watchlistDao().upsert(WatchlistEntity("tt1234567","movie","QA fixture","2026",null,.3f,1234))
            databases[0].trackingDao().upsert(TrackingEntity("qa-history","movie","QA watched record",true,1f,1234,scope="local",updatedAt=1234))
            val first=CloudAccountController(settings[0],CloudSnapshotStore(settings[0],databases[0]),api,scopes[0])
            first.signIn(username,password,true);settled(first)
            val originalId=settings[1].jellyfinDeviceId
            val second=CloudAccountController(settings[1],CloudSnapshotStore(settings[1],databases[1]),api,scopes[1])
            second.signIn(username,password,false);settled(second)
            assertEquals("synthetic-hosted-secret",settings[1].torboxKey)
            assertEquals(databases[0].watchlistDao().getAll(),databases[1].watchlistDao().getAll())
            assertEquals(databases[0].trackingDao().getAll(),databases[1].trackingDao().getAll())
            assertEquals(originalId,settings[1].jellyfinDeviceId)
            val primaryAuth=api.authenticate(username,password,false);auth=primaryAuth
            assertEquals(1L,api.read(primaryAuth).revision)
            first.start();settings[0].torboxKey="synthetic-hosted-updated"
            withTimeout(60000){while(api.read(primaryAuth).revision<2)delay(1000)}
            val remote=api.read(primaryAuth)
            assertFalse(remote.envelope.toString().contains("synthetic-hosted"))
            assertEquals("synthetic-hosted-updated",CloudBackupCodec.open(remote.envelope!!,CloudBackupCodec.key(password.toCharArray(),primaryAuth.userId),primaryAuth.userId).getAsJsonObject("secrets")["torbox_key"].asString)
            settings[1].torboxKey="synthetic-stale-device"
            assertFalse(second.syncNow());assertTrue(second.state.value.message!!.contains("newer backup"))
            val otherAuth=api.authenticate("qb_$suffix",password,true);other=otherAuth
            assertEquals(0L,api.read(otherAuth).revision)
            assertEquals(2L,api.read(primaryAuth).revision)
        } finally {
            scopes.forEach{it.cancel()};databases.forEach{it.close()}
            settings.forEach{s->listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(s.storageNamespace+it)}}
            auth?.let{runCatching{api.logout(it)}};other?.let{runCatching{api.logout(it)}}
        }
    }
}

