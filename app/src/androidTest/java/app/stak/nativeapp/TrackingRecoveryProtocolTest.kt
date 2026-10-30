package app.stak.nativeapp

import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.net.*
import java.util.concurrent.*

class TrackingRecoveryProtocolTest {
    private lateinit var server:ServerSocket;private val executor=Executors.newSingleThreadExecutor();private var previous:SettingsStore?=null;private lateinit var prefix:String
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="tracking130-http-${java.util.UUID.randomUUID()}-";server=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));server.soTimeout=6000;NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix).apply{jellyfinUrl="http://127.0.0.1:${server.localPort}/jf";jellyfinToken="synthetic";jellyfinUserId="user-1"}}
    @After fun cleanup(){server.close();executor.shutdownNow();previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}}
    private fun serve(count:Int,responder:(String,Map<String,String>)->Pair<Int,String>)=executor.submit{repeat(count){server.accept().use{socket->socket.soTimeout=5000;val input=socket.getInputStream().bufferedReader();val line=input.readLine().split(' ');val target=line[1];val params=target.substringAfter('?').split('&').filter{it.contains('=')}.associate{URLDecoder.decode(it.substringBefore('='),"UTF-8") to URLDecoder.decode(it.substringAfter('='),"UTF-8")};while(input.readLine().isNotEmpty()){}
        Assert.assertFalse(params["Fields"].orEmpty().split(',').any{it in setOf("UserData","RunTimeTicks","PresentationUniqueKey")})
        val (status,body)=responder(target.substringBefore('?'),params);val bytes=body.toByteArray();socket.getOutputStream().apply{write("HTTP/1.1 $status Test\r\nContent-Length: ${bytes.size}\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n".toByteArray());write(bytes);flush()}
    }}}
    private val resumed="""{"Items":[{"Id":"movie-1","Type":"Movie","Name":"Film","RunTimeTicks":6000000000,"UserData":{"Played":false,"PlaybackPositionTicks":3000000000}}]}"""
    @Test fun strictServerAcceptsResumeAndHistoryFields()=runBlocking {
        val future=serve(2){path,params->Assert.assertEquals("true",params["EnableUserData"]);200 to if(path.endsWith("Resume"))resumed else """{"TotalRecordCount":1,"Items":[{"Id":"movie-1","Type":"Movie","Name":"Film","UserData":{"Played":true}}]}"""}
        Assert.assertEquals(1,JellyfinPlayback.resume().size);Assert.assertEquals(1,JellyfinJournal.load().size);future.get(7,TimeUnit.SECONDS);Unit
    }
    @Test fun failedNextUpDoesNotDiscardWorkingResume()=runBlocking {
        val future=serve(2){path,_->if(path.endsWith("NextUp"))503 to ""else 200 to resumed}
        val result=JellyfinPlayback.continueFeed();Assert.assertEquals(1,result.items.size);Assert.assertTrue(result.issue!!.contains("Next Up"));future.get(7,TimeUnit.SECONDS);Unit
    }
    @Test fun failedResumeDoesNotDiscardWorkingNextUp()=runBlocking {
        val future=serve(2){path,_->if(path.endsWith("Resume"))503 to ""else 200 to resumed.replace("3000000000","0")}
        val result=JellyfinPlayback.continueFeed();Assert.assertEquals(1,result.items.size);Assert.assertTrue(result.items.single().nextUp);Assert.assertTrue(result.issue!!.contains("Resume"));future.get(7,TimeUnit.SECONDS);Unit
    }
    @Test fun authFailureIsNotMaskedAsEmptySuccessOrRetriedAsAnotherRoute()=runBlocking {
        val future=serve(2){_,_->401 to ""}
        try{JellyfinPlayback.continueFeed();Assert.fail("Expected authentication error")}catch(e:JellyfinHttpFailure){Assert.assertEquals(401,e.status)}
        future.get(7,TimeUnit.SECONDS);Unit
    }
}
