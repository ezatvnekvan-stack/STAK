package app.stak.nativeapp

import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TorrentMutationProtocolTest {
    private lateinit var server:ServerSocket;private val executor=Executors.newSingleThreadExecutor()
    @Before fun setup(){server=ServerSocket(0,4,java.net.InetAddress.getByName("127.0.0.1"));server.soTimeout=5000}
    @After fun cleanup(){server.close();executor.shutdownNow()}
    private fun gateway()=DirectTorBoxMutations("http://127.0.0.1:${server.localPort}/v1/api")
    private fun exchange(path:String,response:String,status:Int=200,inspect:(String,Map<String,String>)->Unit={_,_->}) {
        server.accept().use{socket->socket.soTimeout=4000;val reader=socket.getInputStream().bufferedReader();val request=reader.readLine();Assert.assertEquals("POST /v1/api$path HTTP/1.1",request)
            val headers=mutableMapOf<String,String>();while(true){val line=reader.readLine();if(line.isEmpty())break;headers[line.substringBefore(':').lowercase()]=line.substringAfter(':').trim()};Assert.assertEquals("Bearer synthetic-only",headers["authorization"])
            val size=headers["content-length"]?.toInt() ?: error("Expected bounded Content-Length");val data=CharArray(size);var offset=0;while(offset<size){val count=reader.read(data,offset,size-offset);if(count<0)break;offset+=count};inspect(String(data),headers)
            val bytes=response.toByteArray();socket.getOutputStream().apply{write("HTTP/1.1 $status Test\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray());write(bytes);flush()}
        }
    }
    @Test fun pauseResumeReannounceAndDeleteAreSingleIdJsonOperationsNeverAccountWide()=runBlocking {
        val served=executor.submit{TorrentOperation.entries.forEach{operation->exchange("/torrents/controltorrent","""{"success":true,"data":null}"""){body,headers->
            Assert.assertTrue(headers["content-type"].orEmpty().contains("application/json"));val json=com.google.gson.JsonParser.parseString(body).asJsonObject
            Assert.assertEquals(42,json.get("torrent_id").asInt);Assert.assertEquals(operation.wire,json.get("operation").asString);Assert.assertFalse(json.get("all").asBoolean)
        }}}
        val api=gateway();TorrentOperation.entries.forEach{api.control("synthetic-only","42",it)};served.get(7,TimeUnit.SECONDS);Unit
    }
    @Test fun magnetAndTorrentFileUseMultipartWithBearerOnlyAndReturnActualIds()=runBlocking {
        val hash="a".repeat(40)
        val served=executor.submit{
            exchange("/torrents/createtorrent","""{"success":true,"data":{"torrent_id":12}}"""){body,headers->Assert.assertTrue(headers["content-type"].orEmpty().startsWith("multipart/form-data"));Assert.assertTrue(body.contains("name=\"magnet\""));Assert.assertTrue(body.contains("magnet:?xt=urn:btih:$hash"));Assert.assertFalse(body.contains("synthetic-only"))}
            exchange("/torrents/createtorrent","""{"success":true,"data":{"torrent_id":13}}"""){body,_->Assert.assertTrue(body.contains("name=\"file\"; filename=\"sample.torrent\""));Assert.assertTrue(body.contains("application/x-bittorrent"));Assert.assertTrue(body.contains("d4:infodee"))}
        }
        val api=gateway();Assert.assertEquals("12",api.addMagnet("synthetic-only",parseMagnetInput(hash)));Assert.assertEquals("13",api.upload("synthetic-only",TorrentUpload("sample.torrent","d4:infodee".toByteArray())));served.get(7,TimeUnit.SECONDS);Unit
    }
    @Test fun serverErrorsHaveUnknownOutcomeAndPostIsNotRetried()=runBlocking {
        val served=executor.submit{exchange("/torrents/controltorrent","{}",503)}
        try{gateway().control("synthetic-only","42",TorrentOperation.Delete);Assert.fail("Unknown outcome expected")}catch(_:UnconfirmedTorrentMutation){}
        served.get(6,TimeUnit.SECONDS);server.soTimeout=300
        try{server.accept().close();Assert.fail("POST must never repeat automatically")}catch(_:java.net.SocketTimeoutException){};Unit
    }
    @Test fun successWithoutAnAddIdentifierIsUnknownRatherThanSafeToRepeat()=runBlocking {
        val served=executor.submit{exchange("/torrents/createtorrent","""{"success":true,"data":null}""")}
        try{gateway().addMagnet("synthetic-only",parseMagnetInput("a".repeat(40)));Assert.fail("Unconfirmed") }catch(_:UnconfirmedTorrentMutation){}
        served.get(6,TimeUnit.SECONDS);Unit
    }
    @Test fun invalidIdsAreRejectedBeforeAnyNetworkRequest()=runBlocking {
        try{gateway().control("synthetic-only","all",TorrentOperation.Delete);Assert.fail("Invalid ID") }catch(_:IllegalArgumentException){}
        server.soTimeout=300;try{server.accept().close();Assert.fail("No request expected")}catch(_:java.net.SocketTimeoutException){};Unit
    }
}
