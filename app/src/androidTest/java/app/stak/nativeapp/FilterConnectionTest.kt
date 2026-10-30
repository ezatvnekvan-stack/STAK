package app.stak.nativeapp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.data.db.PreferenceEntity
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FilterConnectionTest {
 private lateinit var prefix:String
 private var previous:SettingsStore?=null
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="filter136-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{torboxKey="synthetic-filter136"}}
 @After fun cleanup()=runBlocking{DatabaseProvider.get(context).preferenceDao().delete(prefix+"media_intelligence_v1");previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
 @Test fun tokenPasteAcceptsEnvLineAndPreservesLeadingHyphen(){val token="-u_"+"x".repeat(40);Assert.assertEquals(token,filterToken("  STAK_ACCESS_TOKEN=$token\n"));Assert.assertEquals(token,filterToken(token))}
 @Test fun invalidTokenAndUnexpectedExceptionsNeverExposeSecrets(){val secret="private-diagnostic-credential";val error=runCatching{filterToken(secret+"\n"+"x".repeat(32))}.exceptionOrNull()!!;Assert.assertFalse(libraryFilterError(error as Exception).contains(secret));Assert.assertFalse(libraryFilterError(IllegalArgumentException("Unexpected header $secret")).contains(secret));Assert.assertTrue(libraryFilterError(java.net.ConnectException()).contains("Wi-Fi"));Assert.assertTrue(libraryFilterError(java.net.SocketTimeoutException()).contains("timed out"))}
 @Test fun actualHttp401IsReportedAsTokenRejection()=runBlocking{
  val server=ServerSocket(0,4,java.net.InetAddress.getByName("127.0.0.1"));server.soTimeout=4000;val executor=Executors.newSingleThreadExecutor()
  val served=executor.submit{server.accept().use{socket->val reader=socket.getInputStream().bufferedReader();while(reader.readLine().isNotEmpty()){};socket.getOutputStream().write("HTTP/1.0 401 Unauthorized\r\nContent-Length: 2\r\n\r\n{}".toByteArray())}}
  try{val error=runCatching{LibraryFilterClient(LibraryFilterConnection("http://127.0.0.1:${server.localPort}","synthetic-invalid-token-"+"x".repeat(32))).status(torboxScope())}.exceptionOrNull();Assert.assertTrue(error is LibraryFilterFailure);Assert.assertTrue(libraryFilterError(error as Exception).contains("access token (HTTP 401)"));served.get(5,TimeUnit.SECONDS);Unit}finally{server.close();executor.shutdownNow()}
 }
 @Test fun existingApprovalsSyncEvenWhenEarlierDeliveryJournalCannotBeRead()=runBlocking{
  val server=ServerSocket(0,4,java.net.InetAddress.getByName("127.0.0.1"));server.soTimeout=4000
  val executor=Executors.newSingleThreadExecutor()
  val c=LibraryFilterConnection("http://127.0.0.1:${server.localPort}","synthetic-filter-token-"+"x".repeat(32),true)
  val store=ApprovalStore();store.approve("136",NativeRuntime.settings.torboxKey!!,name="Approved",type="movie");store.update{it.copy(connection=c)}
  val dao=DatabaseProvider.get(context).preferenceDao();dao.set(PreferenceEntity(prefix+"media_intelligence_v1","{unreadable historical record"))
  val paths=java.util.Collections.synchronizedList(mutableListOf<String>())
  val served=executor.submit{
   repeat(3){server.accept().use{socket->
    val reader=socket.getInputStream().bufferedReader();val first=reader.readLine();paths+=first.substringBefore(" HTTP/")
    var length=0;while(true){val line=reader.readLine();if(line.isEmpty())break;if(line.startsWith("Content-Length:",true))length=line.substringAfter(':').trim().toInt()}
    repeat(length){reader.read()}
    val response=when{first.startsWith("GET /v1/status ")->"""{"protocol":"stak-library-filter/1","scope":"${torboxScope()}","source_available":true,"jellyfin_paths_verified":false}""";first.startsWith("POST ")->"""{"id":"136","state":"processing"}""";else->"""{"items":[{"id":"136","state":"processing"}]}"""}.toByteArray()
    socket.getOutputStream().write("HTTP/1.0 200 OK\r\nContent-Length: ${response.size}\r\n\r\n".toByteArray()+response)
   }}
  }
  try{
   val status=LibraryApprovals.sync();Assert.assertNotNull(status);Assert.assertTrue(status!!.get("stak_import_warning").asBoolean);served.get(5,TimeUnit.SECONDS)
   Assert.assertEquals(listOf("GET /v1/status","POST /v1/approvals","GET /v1/approvals"),paths)
   Assert.assertEquals("processing",store.read().entries.single().state)
   Assert.assertEquals("{unreadable historical record",dao.get(prefix+"media_intelligence_v1"))
  }finally{server.close();executor.shutdownNow()}
 }
}
