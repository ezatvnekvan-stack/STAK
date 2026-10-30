package app.stak.nativeapp

import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.time.Instant

class JournalProtocolTest {
    private var previous:SettingsStore?=null;private lateinit var prefix:String;private lateinit var server:ServerSocket
    private val executor=Executors.newSingleThreadExecutor()
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="journal-http-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix);server=ServerSocket(0,4,java.net.InetAddress.getByName("127.0.0.1"));server.soTimeout=7000;NativeRuntime.settings.apply{jellyfinUrl="http://127.0.0.1:${server.localPort}/jf";jellyfinToken="synthetic-token";jellyfinUserId="user-1"}}
    @After fun cleanup(){server.close();executor.shutdownNow();previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}}
    private fun exchange(method:String,path:String,body:String="",status:Int=200,inspect:(Map<String,String>)->Unit={}) {
        server.accept().use{socket->socket.soTimeout=5000;val reader=socket.getInputStream().bufferedReader();val request=reader.readLine().split(' ');Assert.assertEquals(method,request[0]);Assert.assertEquals(path,request[1].substringBefore('?'))
            val params=request[1].substringAfter('?','\u0000'.toString()).split('&').filter{it.contains('=')}.associate{URLDecoder.decode(it.substringBefore('='),"UTF-8") to URLDecoder.decode(it.substringAfter('='),"UTF-8")};inspect(params)
            val headers=mutableMapOf<String,String>();while(true){val line=reader.readLine();if(line.isEmpty())break;headers[line.substringBefore(':').lowercase()]=line.substringAfter(':').trim()};Assert.assertEquals("synthetic-token",headers["x-emby-token"])
            var remaining=headers["content-length"]?.toInt() ?: 0;while(remaining>0){val count=reader.read(CharArray(remaining));if(count<0)break;remaining-=count}
            val bytes=body.toByteArray();socket.getOutputStream().apply{write("HTTP/1.1 $status Test\r\nContent-Length: ${bytes.size}\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n".toByteArray());write(bytes);flush()}
        }
    }
    @org.junit.Ignore("Manual watched mutations retired in 1.3.4; PassiveTracking tests verify read-only behavior")
    @Test fun watchedDateIsSentToJellyfinAndRetainedInEncryptedRetryState()=runBlocking {
        val date=Instant.parse("2026-10-20T09:00:00Z").toEpochMilli()
        val served=executor.submit{exchange("POST","/jf/Users/user-1/PlayedItems/movie-1",status=204){params->Assert.assertEquals("2026-10-20T09:00:00Z",params["DatePlayed"])}}
        JellyfinPlayback.queueWatched(MediaItem("tt1234567","tt1234567",name="Movie",jellyfinId="movie-1"),null,true,date)
        Assert.assertEquals(date,PlaybackStore().all().single().playedAt);Assert.assertEquals(0,JellyfinPlayback.flushPending());Assert.assertFalse(PlaybackStore().all().single().pending);served.get(7,TimeUnit.SECONDS);Unit
    }
    @Test fun historyReadsActualEpisodeDatesAndResolvesItsSeriesIdentity()=runBlocking {
        val served=executor.submit {
            exchange("GET","/jf/Users/user-1/Items","""{"TotalRecordCount":1,"Items":[{"Id":"episode-2","SeriesId":"series-1","Type":"Episode","SeriesName":"Show","Name":"Second","ParentIndexNumber":1,"IndexNumber":2,"RunTimeTicks":27000000000,"UserData":{"Played":true,"LastPlayedDate":"2026-10-20T10:00:00Z"}}]}"""){params->Assert.assertEquals("true",params["IsPlayed"]);Assert.assertEquals("Movie,Episode",params["IncludeItemTypes"]);Assert.assertEquals("0",params["StartIndex"])}
            exchange("GET","/jf/Users/user-1/Items/series-1","""{"Id":"series-1","Type":"Series","Name":"Show","ProviderIds":{"Imdb":"tt1234567"}}""")
        }
        val entry=JellyfinJournal.load().single();Assert.assertTrue(entry.isEpisode);Assert.assertEquals("tt1234567",entry.media.imdbId);Assert.assertEquals(Instant.parse("2026-10-20T10:00:00Z").toEpochMilli(),entry.watchedAt);Assert.assertEquals("episode-2",entry.media.jellyfinId);served.get(7,TimeUnit.SECONDS);Unit
    }
}
