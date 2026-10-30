package app.stak.nativeapp

import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import com.google.gson.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LibraryPagingProtocolTest {
    private var previous:SettingsStore?=null;private lateinit var prefix:String;private lateinit var server:ServerSocket
    private val executor=Executors.newSingleThreadExecutor()
    @Before fun setup(){
        previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="library-http-${java.util.UUID.randomUUID()}-"
        NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix)
        server=ServerSocket(0,4,java.net.InetAddress.getByName("127.0.0.1"));server.soTimeout=8000
        NativeRuntime.settings.apply{jellyfinUrl="http://127.0.0.1:${server.localPort}/jellyfin";jellyfinToken="synthetic-token";jellyfinUserId="user-1"}
    }
    @After fun cleanup(){server.close();executor.shutdownNow();previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}}
    private fun serve(total:Int,failSecond:Boolean=false)=executor.submit {
        val offsets=if(failSecond)listOf(0,2) else (0 until total step 200).toList()
        offsets.forEachIndexed{page,offset->server.accept().use{socket->
            socket.soTimeout=5000;val reader=socket.getInputStream().bufferedReader();val path=reader.readLine().split(' ')[1]
            Assert.assertEquals("/jellyfin/Users/user-1/Items",path.substringBefore('?'))
            val params=path.substringAfter('?').split('&').associate{URLDecoder.decode(it.substringBefore('='),"UTF-8") to URLDecoder.decode(it.substringAfter('='),"UTF-8")}
            Assert.assertEquals(offset.toString(),params["StartIndex"]);Assert.assertEquals("200",params["Limit"]);Assert.assertEquals("Movie,Series",params["IncludeItemTypes"])
            val headers=mutableMapOf<String,String>();while(true){val line=reader.readLine();if(line.isEmpty())break;headers[line.substringBefore(':').lowercase()]=line.substringAfter(':').trim()}
            Assert.assertEquals("synthetic-token",headers["x-emby-token"])
            val failed=failSecond&&page==1
            val data=JsonObject().apply {
                addProperty("TotalRecordCount",total)
                add("Items",JsonArray().apply{for(i in offset until minOf(total,offset+if(failSecond)2 else 200))add(JsonObject().apply{addProperty("Id","item-$i");addProperty("Name","Film $i");addProperty("Type","Movie");addProperty("DateCreated","2026-01-01T00:00:00Z");add("ProviderIds",JsonNull.INSTANCE);add("UserData",JsonObject().apply{addProperty("Played",i==0)})})})
            }.toString().toByteArray(Charsets.UTF_8)
            socket.getOutputStream().apply{write("HTTP/1.1 ${if(failed)503 else 200} Test\r\nContent-Type: application/json\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n".toByteArray());write(data);flush()}
        }}
    }
    @Test fun jellyfinLibraryReadsEveryPageWithRealAuthenticatedTransport()=runBlocking {
        val served=serve(503);val counts=mutableListOf<Int>()
        val items=DirectJellyfin.library(NativeRuntime.settings){partial,total->counts+=partial.size;Assert.assertEquals(503,total)}
        Assert.assertEquals(503,items.size);Assert.assertEquals(listOf(200,400,503),counts);Assert.assertTrue(items.first().watched);Assert.assertTrue(items.first().addedAt>0);Assert.assertTrue(items.first().poster!!.contains("/jellyfin/Items/item-0/Images/Primary"));served.get(8,TimeUnit.SECONDS);Unit
    }
    @Test fun failedSecondPageDoesNotDiscardThePublishedFirstPage()=runBlocking {
        val served=serve(3,true);var partial=emptyList<MediaItem>()
        try{DirectJellyfin.library(NativeRuntime.settings){items,_->partial=items};Assert.fail("Expected failure")}catch(_:java.io.IOException){}
        Assert.assertEquals(2,partial.size);served.get(8,TimeUnit.SECONDS);Unit
    }
}
