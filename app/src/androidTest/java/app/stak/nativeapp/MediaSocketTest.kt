package app.stak.nativeapp

import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import kotlinx.coroutines.*
import org.junit.*
import java.net.*
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.*

class MediaSocketTest {
    @Test fun authenticatedSocketTriggersReconciliationRespondsToKeepAliveAndStopsInBackground()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val prior=runCatching{NativeRuntime.settings}.getOrNull();val prefix="socket-${java.util.UUID.randomUUID()}-";val settings=SettingsStore(context,prefix)
        val server=ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));server.soTimeout=6000;val worker=Executors.newSingleThreadExecutor()
        try {
            settings.jellyfinUrl="http://127.0.0.1:${server.localPort}/jf";settings.jellyfinToken="synthetic-only";settings.jellyfinUserId="me";NativeRuntime.settings=settings
            val keepAlive=CompletableFuture<String>()
            val served=worker.submit<String>{server.accept().use{socket->socket.soTimeout=6000;val input=socket.getInputStream();val reader=input.bufferedReader();val request=reader.readLine();val headers=mutableMapOf<String,String>();while(true){val line=reader.readLine();if(line.isEmpty())break;headers[line.substringBefore(':').lowercase()]=line.substringAfter(':').trim()};Assert.assertTrue(request.startsWith("GET /jf/socket?"));Assert.assertTrue(request.contains("api_key=synthetic-only"));Assert.assertTrue(request.contains("deviceId="));val accept=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((headers["sec-websocket-key"]+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()));val output=socket.getOutputStream();output.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray());output.flush()
                fun frame(text:String){val bytes=text.toByteArray();require(bytes.size<126);output.write(0x81);output.write(bytes.size);output.write(bytes);output.flush()}
                frame("""{"MessageType":"LibraryChanged"}""");frame("""{"MessageType":"ForceKeepAlive"}""")
                val first=input.read();val second=input.read();Assert.assertEquals(1,first and 15);Assert.assertTrue(second and 128!=0);val length=second and 127;val mask=ByteArray(4);require(input.read(mask)==4);val body=ByteArray(length);var count=0;while(count<length){val read=input.read(body,count,length-count);if(read<0)break;count+=read};val answer=String(body.mapIndexed{i,b->(b.toInt() xor mask[i%4].toInt()).toByte()}.toByteArray());keepAlive.complete(answer);try{input.read()}catch(_:java.io.IOException){};answer
            }}
            MediaLiveSync.consume();MediaLiveSync.start(context)
            withTimeout(6000){while(!keepAlive.isDone)delay(20)}
            val answer=withContext(Dispatchers.IO){keepAlive.get(6,TimeUnit.SECONDS)};Assert.assertTrue(MediaLiveSync.state.value.connected);Assert.assertTrue(answer.contains("KeepAlive"));Assert.assertTrue(MediaLiveSync.consume().reasons.contains("library"))
            MediaLiveSync.stop();withContext(Dispatchers.IO){served.get(6,TimeUnit.SECONDS)};Assert.assertFalse(MediaLiveSync.state.value.connected);delay(200);Assert.assertFalse(MediaLiveSync.state.value.connected)
        }finally{MediaLiveSync.stop();server.close();worker.shutdownNow();prior?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
        Unit
    }

    @Test fun rejectedSocketKeepsApiReconciliationAvailableUntilBackgrounded()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val prior=runCatching{NativeRuntime.settings}.getOrNull();val prefix="socket-denied-${java.util.UUID.randomUUID()}-";val settings=SettingsStore(context,prefix);val server=ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));server.soTimeout=6000;val worker=Executors.newSingleThreadExecutor()
        try {
            settings.jellyfinUrl="http://127.0.0.1:${server.localPort}";settings.jellyfinToken="synthetic";settings.jellyfinUserId="me";NativeRuntime.settings=settings
            val served=worker.submit{server.accept().use{socket->val reader=socket.getInputStream().bufferedReader();while(!reader.readLine().isNullOrBlank()){};socket.getOutputStream().apply{write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray());flush()}}}
            MediaLiveSync.start(context);withTimeout(6000){while(!MediaLiveSync.state.value.status.contains("API reconciliation"))delay(20)};served.get(6,TimeUnit.SECONDS);delay(200);Assert.assertFalse(MediaLiveSync.state.value.connected);Assert.assertTrue(MediaLiveSync.state.value.status.contains("API reconciliation"));MediaLiveSync.stop();Assert.assertEquals("Paused in background",MediaLiveSync.state.value.status)
        }finally{MediaLiveSync.stop();server.close();worker.shutdownNow();prior?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
        Unit
    }
}
