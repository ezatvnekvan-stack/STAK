package app.stak.nativeapp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CachedPrivateProtocolTest {
 private lateinit var server:ServerSocket;private val worker=Executors.newSingleThreadExecutor();private var previous:SettingsStore?=null;private lateinit var prefix:String
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 private val bytes="d8:announce31:https://tracker.invalid/passkey4:infod6:lengthi1e4:name1:x12:piece lengthi16384e6:pieces20:abcdefghijklmnopqrstee".toByteArray()
 @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="private137-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{torboxKey="synthetic-only"};server=ServerSocket(0,5,java.net.InetAddress.getByName("127.0.0.1"));server.soTimeout=4000}
 @After fun cleanup(){server.close();worker.shutdownNow();previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
 private fun response(body:ByteArray,inspect:(String,Map<String,String>)->Unit){server.accept().use{socket->val input=socket.getInputStream().bufferedReader();val line=input.readLine();val headers=linkedMapOf<String,String>();while(true){val h=input.readLine();if(h.isEmpty())break;headers[h.substringBefore(':').lowercase()]=h.substringAfter(':').trim()};val n=headers["content-length"]?.toInt()?:0;val chars=CharArray(n);var at=0;while(at<n)at+=input.read(chars,at,n-at);inspect(line+"\n"+String(chars),headers);socket.getOutputStream().write("HTTP/1.0 200 OK\r\nContent-Type: application/x-bittorrent\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()+body)}}
 @Test fun privateMetadataIsHashedReusedAndPartitionedByAccount()=runBlocking{
  val endpoint="http://127.0.0.1:${server.localPort}";val indexer=TorznabIndexer(name="TorrentLeech",url="$endpoint/api",apiKey="private-indexer-key");TorznabStore(NativeRuntime.settings).save(listOf(indexer))
  val source=SourceRelease("Private",torznabIndexerId=indexer.id,torrentFileUrl="$endpoint/download",sourceToken="one")
  val served=worker.submit{repeat(2){response(bytes){request,headers->Assert.assertTrue(request.contains("apikey=private-indexer-key"));Assert.assertNull(headers["authorization"]);Assert.assertFalse(request.contains("synthetic-only"))}}}
  val first=SourceMaterials.prepare(source,"synthetic-only");val second=SourceMaterials.prepare(source,"synthetic-only");Assert.assertEquals(torrentInfoHash(TorrentUpload("x.torrent",bytes)),first.source.infoHash);Assert.assertSame((first.material as NabMaterial.Torrent).upload,(second.material as NabMaterial.Torrent).upload)
  val other=SourceMaterials.prepare(source,"different-account");Assert.assertEquals(first.source.infoHash,other.source.infoHash);served.get(5,TimeUnit.SECONDS);Unit
 }
 @Test fun cachedOnlyMagnetAndUploadSendTheHardCutoffAndOriginalBytes()=runBlocking{
  val hash="a".repeat(40);val file=TorrentUpload("private.torrent",bytes)
  val served=worker.submit{repeat(2){i->response("""{"success":true,"data":{"torrent_id":137}}""".toByteArray()){request,headers->Assert.assertTrue(request.startsWith("POST /v1/api/torrents/createtorrent"));Assert.assertEquals("Bearer synthetic-only",headers["authorization"]);Assert.assertTrue(Regex("name=\"add_only_if_cached\"[\\s\\S]*?\\r\\n\\r\\ntrue\\r\\n").containsMatchIn(request));if(i==1)Assert.assertTrue(request.contains(String(bytes)))}}}
  val gateway=DirectTorBoxMutations("http://127.0.0.1:${server.localPort}/v1/api");Assert.assertEquals("137",gateway.addMagnetCached("synthetic-only",parseMagnetInput(hash)));Assert.assertEquals("137",gateway.uploadCached("synthetic-only",file));served.get(5,TimeUnit.SECONDS);Unit
 }
 @Test fun cachedRefusalIsNotRetriedOrReportedAsAnAddition()=runBlocking{
  val served=worker.submit{response("""{"success":false,"error":"NOT_CACHED"}""".toByteArray()){request,_->Assert.assertTrue(request.contains("add_only_if_cached"))}}
  try{DirectTorBoxMutations("http://127.0.0.1:${server.localPort}/v1/api").addMagnetCached("synthetic-only",parseMagnetInput("a".repeat(40)));Assert.fail("A refused add must fail") }catch(_:NabFailure){}
  served.get(5,TimeUnit.SECONDS);server.soTimeout=250;try{server.accept().close();Assert.fail("No retry or uncached fallback") }catch(_:java.net.SocketTimeoutException){};Unit
 }
}
