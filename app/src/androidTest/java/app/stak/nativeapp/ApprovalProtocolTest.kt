package app.stak.nativeapp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ApprovalProtocolTest {
    private var previous:SettingsStore?=null;private lateinit var prefix:String;private lateinit var server:ServerSocket;private val executor=Executors.newSingleThreadExecutor()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="approval135-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{torboxKey="synthetic-torbox-private"};server=ServerSocket(0,4,java.net.InetAddress.getByName("127.0.0.1"));server.soTimeout=5000}
    @After fun cleanup(){server.close();executor.shutdownNow();previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
    private val key get()=NativeRuntime.settings.torboxKey!!
    private val connection get()=LibraryFilterConnection("http://127.0.0.1:${server.localPort}","test-filter-token-"+"a".repeat(32),true)
    @Test fun onlyExplicitApprovalsAreStoredAndSurviveReopen(){val store=ApprovalStore();Assert.assertTrue(store.read().entries.isEmpty());store.approve("12",key,"a".repeat(40),"Chosen","movie");val reopened=ApprovalStore().read();Assert.assertEquals("12",reopened.entries.single().id);Assert.assertTrue(reopened.entries.single().approved);Assert.assertEquals(torboxScope(),reopened.entries.single().scope)}
    @Test fun unrelatedAccountIsNotIncluded(){ApprovalStore().approve("12",key,name="Chosen");NativeRuntime.settings.torboxKey="another-account";Assert.assertTrue(ApprovalStore().read().entries.none{it.scope==torboxScope()})}
    @Test fun staleAckCannotOverrideRevoke(){val store=ApprovalStore();val row=store.approve("12",key);store.decide(row,false);store.acknowledge(row,"old-endpoint","published","");val current=store.read().entries.single();Assert.assertFalse(current.approved);Assert.assertNull(current.sentTo);Assert.assertEquals("saved",current.state)}
    @Test fun unreadableDocumentIsNeverOverwritten(){NativeRuntime.settings.libraryApprovalsJson="{broken";Assert.assertTrue(runCatching{ApprovalStore().approve("12",key)}.isFailure);Assert.assertEquals("{broken",NativeRuntime.settings.libraryApprovalsJson)}
    @Test fun typeUpdatePreservesHashAndApprovalIdentity(){val store=ApprovalStore();val row=store.approve("12",key,"a".repeat(40));store.decide(row,true,"series");Assert.assertEquals("series",store.read().entries.single().type);Assert.assertEquals(row.hash,store.read().entries.single().hash)}
    @Test fun endpointRejectsPublicHttpAndEmbeddedCredentials(){listOf("http://example.com","https://u:p@example.com","https://example.com/?token=secret").forEach{Assert.assertTrue(runCatching{approvalEndpoint(it)}.isFailure)};Assert.assertEquals("100.80.1.2",approvalEndpoint("http://100.80.1.2:8787").host)}
    @Test fun filterRequestNeverContainsTheTorBoxKey()=runBlocking{
        val row=ApprovalStore().approve("12",key,"a".repeat(40),"Chosen","movie")
        val served=executor.submit{server.accept().use{socket->val reader=socket.getInputStream().bufferedReader();val first=reader.readLine();Assert.assertTrue(first.startsWith("POST /v1/approvals "));val headers=mutableMapOf<String,String>();while(true){val line=reader.readLine();if(line.isEmpty())break;headers[line.substringBefore(':').lowercase()]=line.substringAfter(':').trim()};Assert.assertEquals("Bearer ${connection.token}",headers["authorization"]);val body=CharArray(headers["content-length"]!!.toInt());var n=0;while(n<body.size)n+=reader.read(body,n,body.size-n);val text=String(body);Assert.assertFalse(text.contains(key));Assert.assertFalse(text.contains("magnet:"));val reply="""{"id":"12","state":"pending","scope":"${torboxScope()}"}""".toByteArray();socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${reply.size}\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n".toByteArray()+reply)}}
        Assert.assertEquals("pending",LibraryFilterClient(connection).send(row).text("state"));served.get(6,TimeUnit.SECONDS);Unit
    }
    @Test fun wrongServerAccountIsRejected()=runBlocking{
        val served=executor.submit{server.accept().use{socket->val reader=socket.getInputStream().bufferedReader();while(reader.readLine().isNotEmpty()){};val body="""{"protocol":"stak-library-filter/1","scope":"different"}""".toByteArray();socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()+body)}}
        Assert.assertTrue(runCatching{LibraryFilterClient(connection).status(torboxScope())}.isFailure);served.get(6,TimeUnit.SECONDS);Unit
    }
    @Test fun oldConfirmedStakAddsAreImportedButNeverReapproveARevokedEntry(){val store=ApprovalStore();val account=torboxScope();val rows=listOf(DeliveryRecord(torboxId="19",torboxAccount=account,jellyfinAccount=null,releaseName="Earlier STAK add"),DeliveryRecord(torboxId="20",torboxAccount="other",jellyfinAccount=null));store.importRecorded(account,rows);Assert.assertEquals("19",store.read().entries.single().id);store.decide(store.read().entries.single(),false);store.importRecorded(account,rows);Assert.assertFalse(store.read().entries.single().approved);Assert.assertEquals(1,store.read().entries.size)}

}
