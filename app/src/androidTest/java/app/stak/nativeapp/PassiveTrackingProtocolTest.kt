package app.stak.nativeapp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.net.ServerSocket
import java.net.SocketTimeoutException

class PassiveTrackingProtocolTest {
    private var previous:SettingsStore?=null;private lateinit var prefix:String;private lateinit var server:ServerSocket
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="passive-http-${java.util.UUID.randomUUID()}-";server=ServerSocket(0,4,java.net.InetAddress.getByName("127.0.0.1"));server.soTimeout=250;NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix).apply{jellyfinUrl="http://127.0.0.1:${server.localPort}";jellyfinUserId="fixture";jellyfinToken="synthetic-only"}}
    @After fun cleanup(){server.close();previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}}
    private val movie=MediaItem("id",name="Movie",jellyfinId="jf")
    private fun noRequest(){try{server.accept().close();Assert.fail("Unexpected Jellyfin network write")}catch(expected:SocketTimeoutException){}}
    @Test fun noNewManualQueueOrNetworkWrites()=runBlocking{JellyfinPlayback.queueWatched(movie,null,true);JellyfinPlayback.queueWatched(movie,null,false);Assert.assertTrue(PlaybackStore().all().isEmpty());Assert.assertEquals(0,JellyfinPlayback.flushPending());noRequest()}
    @Test fun oldPendingRecordSurvivesButNeverReplays()=runBlocking{val record=PlaybackRecord(movie,jellyfinScope(),0,90_000,true,100,jellyfinItemId="jf",pending=true,manual=true);PlaybackStore().save(record);JellyfinPlayback.sync(record);JellyfinIntelligence.watched("jf",false);Assert.assertEquals(0,JellyfinPlayback.flushPending());Assert.assertEquals(record,PlaybackStore().all().single());noRequest()}
    @Test fun directMutationGuardBlocksBothApiRoutesAndPositionWrites()=runBlocking{listOf("/Users/fixture/PlayedItems/jf","/UserPlayedItems/jf","/Users/fixture/Items/jf/UserData").forEach{path->val error=runCatching{DirectJellyfin.sessionRequest(path,method="POST")}.exceptionOrNull();Assert.assertTrue(error is IllegalArgumentException)};noRequest()}
    @Test fun bodyCannotBypassTheReadOnlyGuard()=runBlocking{val error=runCatching{DirectJellyfin.sessionRequest("/Users/fixture/Items/jf/UserData",body=com.google.gson.JsonObject().apply{addProperty("Played",true)})}.exceptionOrNull();Assert.assertTrue(error is IllegalArgumentException);noRequest()}
}
