package app.stak.nativeapp
import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class JellyfinSyncProtocolTest {
    private lateinit var server:ServerSocket
    private val executor=Executors.newSingleThreadExecutor()
    private var previous:SettingsStore?=null
    private val files=mutableSetOf<String>()
    @Before fun setup(){
        previous=runCatching{NativeRuntime.settings}.getOrNull();val context=InstrumentationRegistry.getInstrumentation().targetContext;val prefix="jf-sync-test-${java.util.UUID.randomUUID()}-"
        files+=prefix+"stak_settings";files+=prefix+"stak_secrets"
        NativeRuntime.settings=SettingsStore(context,prefix)
        server=ServerSocket(0,4,java.net.InetAddress.getByName("127.0.0.1"));server.soTimeout=5000
        NativeRuntime.settings.apply{jellyfinUrl="http://127.0.0.1:${server.localPort}";jellyfinToken="synthetic-token";jellyfinUserId="test-user"}
    }
    @After fun cleanup(){server.close();executor.shutdownNow();previous?.let{NativeRuntime.settings=it};files.forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(it)}}
    private data class Exchange(val method:String,val path:String,val response:String="",val status:Int=200)
    private fun serve(vararg exchanges:Exchange)=executor.submit{
        val pending=exchanges.toMutableList()
        repeat(exchanges.size){server.accept().use{socket->socket.soTimeout=5000;val reader=socket.getInputStream().bufferedReader();val request=reader.readLine().split(' ')
            val expected=pending.first{it.method==request[0]&&it.path==request[1].substringBefore('?')};pending.remove(expected)
            val headers=mutableMapOf<String,String>();while(true){val line=reader.readLine();if(line.isEmpty())break;headers[line.substringBefore(':').lowercase()]=line.substringAfter(':').trim()}
            Assert.assertEquals("synthetic-token",headers["x-emby-token"]);Assert.assertTrue(headers["authorization"].orEmpty().contains("Token=\"synthetic-token\""))
            var remaining=headers["content-length"]?.toInt() ?: 0;while(remaining>0){val chunk=CharArray(remaining);val count=reader.read(chunk);if(count<0)break;remaining-=count}
            val body=expected.response.toByteArray();socket.getOutputStream().apply{write("HTTP/1.1 ${expected.status} Test\r\nContent-Length: ${body.size}\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n".toByteArray());write(body);flush()}
        }}
    }
    @Test fun resumeReadsActualEpisodePositionAndSeriesArtwork()=runBlocking{
        val served=serve(Exchange("GET","/Users/test-user/Items/Resume","""{"Items":[{"Id":"ep-3","Type":"Episode","SeriesId":"show-1","SeriesName":"Series","Name":"Episode three","ParentIndexNumber":2,"IndexNumber":3,"RunTimeTicks":600000000,"UserData":{"Played":false,"PlaybackPositionTicks":150000000}}]}"""))
        val item=JellyfinPlayback.resume().single();Assert.assertEquals("Series",item.name);Assert.assertEquals("ep-3",item.jellyfinId);Assert.assertEquals(2,item.season);Assert.assertEquals(3,item.episode);Assert.assertEquals(15000L,item.positionMs);Assert.assertEquals(.25f,item.progress,.001f);Assert.assertTrue(item.background!!.contains("ep-3/Images/Primary"));served.get(6,TimeUnit.SECONDS);Unit
    }
    @Test fun continueWatchingMergesResumeAndNextUpWithoutDuplicateEpisodes()=runBlocking {
        val episode="""{"Id":"ep-3","Type":"Episode","SeriesId":"show-1","SeriesName":"Series","Name":"Three","ParentIndexNumber":1,"IndexNumber":3,"RunTimeTicks":600000000,"UserData":{"Played":false,"PlaybackPositionTicks":150000000}}"""
        val next="""{"Id":"ep-4","Type":"Episode","SeriesId":"show-1","SeriesName":"Series","Name":"Four","ParentIndexNumber":1,"IndexNumber":4,"UserData":{"Played":false}}"""
        val served=serve(Exchange("GET","/Users/test-user/Items/Resume","{\"Items\":[$episode]}"),Exchange("GET","/Shows/NextUp","{\"Items\":[$episode,$next]}"))
        val items=JellyfinPlayback.continueWatching()
        Assert.assertEquals(listOf("ep-3","ep-4"),items.map{it.jellyfinId});Assert.assertFalse(items[0].nextUp);Assert.assertTrue(items[1].nextUp);Assert.assertEquals(0L,items[1].positionMs)
        served.get(6,TimeUnit.SECONDS);Unit
    }
    @org.junit.Ignore("Manual watched mutations retired in 1.3.4; PassiveTracking tests verify read-only behavior")
    @Test fun watchedAndUndoUseRealEndpointsAndAcceptEmpty204Responses()=runBlocking{
        val media=MediaItem("tt1234567","tt1234567",name="Film",jellyfinId="movie-1")
        val served=serve(Exchange("POST","/Users/test-user/PlayedItems/movie-1",status=204),Exchange("DELETE","/Users/test-user/PlayedItems/movie-1",status=204))
        JellyfinPlayback.queueWatched(media,null,true);Assert.assertEquals(0,JellyfinPlayback.flushPending());Assert.assertFalse(PlaybackStore().all().single().pending)
        JellyfinPlayback.queueWatched(media,null,false);Assert.assertEquals(0,JellyfinPlayback.flushPending());Assert.assertFalse(PlaybackStore().all().single().played);served.get(6,TimeUnit.SECONDS);Unit
    }
    @org.junit.Ignore("Manual watched mutations retired in 1.3.4; PassiveTracking tests verify read-only behavior")
    @Test fun failedSyncStaysEncryptedAndCanRetryWithoutLosingLatestState()=runBlocking{
        val media=MediaItem("tt1234567","tt1234567",name="Film",jellyfinId="movie-1")
        val served=serve(Exchange("POST","/Users/test-user/PlayedItems/movie-1",status=503),Exchange("POST","/Users/test-user/PlayedItems/movie-1",status=204))
        JellyfinPlayback.queueWatched(media,null,true);Assert.assertEquals(1,JellyfinPlayback.flushPending());Assert.assertTrue(PlaybackStore().all().single().pending)
        Assert.assertEquals(0,JellyfinPlayback.flushPending());Assert.assertFalse(PlaybackStore().all().single().pending);served.get(6,TimeUnit.SECONDS);Unit
    }
    @org.junit.Ignore("Manual watched mutations retired in 1.3.4; PassiveTracking tests verify read-only behavior")
    @Test fun switchingUserDoesNotSendThePreviousUsersPendingUpdates()=runBlocking{
        JellyfinPlayback.queueWatched(MediaItem("tt1234567","tt1234567",name="Film",jellyfinId="movie-1"),null,true)
        NativeRuntime.settings.jellyfinUserId="another-user"
        Assert.assertEquals(0,JellyfinPlayback.flushPending());Assert.assertTrue(PlaybackStore().all().single().pending);Unit
    }
    @Test fun acknowledgingAnOldQueueEntryDoesNotClearANewerUndo(){
        val media=MediaItem("tt1234567","tt1234567",name="Film")
        val old=PlaybackRecord(media,jellyfinScope(),0,0,true,1,pending=true,manual=true)
        val undo=old.copy(played=false,updatedAt=2,jellyfinItemId="movie-1")
        val store=PlaybackStore();store.save(old);store.save(undo);store.acknowledge(old)
        Assert.assertEquals(1,store.all().size);Assert.assertTrue(store.all().single().pending);Assert.assertFalse(store.all().single().played)
    }
    @org.junit.Ignore("Manual watched mutations retired in 1.3.4; PassiveTracking tests verify read-only behavior")
    @Test fun seasonUndoResolvesEpisodeIdInsteadOfDeletingTheSeriesState()=runBlocking {
        val served=serve(Exchange("GET","/Shows/show-1/Episodes","""{"Items":[{"Id":"ep-1","ParentIndexNumber":1,"IndexNumber":1}]}"""),Exchange("DELETE","/Users/test-user/PlayedItems/ep-1",status=204))
        JellyfinPlayback.queueWatched(MediaItem("tt1234567","tt1234567",type="series",name="Series",jellyfinId="show-1"),Episode(1,1,"One"),false)
        Assert.assertNull(PlaybackStore().all().single().jellyfinItemId)
        Assert.assertEquals(0,JellyfinPlayback.flushPending())
        served.get(6,TimeUnit.SECONDS);Unit
    }
    @org.junit.Ignore("Manual watched mutations retired in 1.3.4; PassiveTracking tests verify read-only behavior")
    @Test fun confirmedAbsentMovieIsLocalOnlyInsteadOfAnEndlessFailedSync()=runBlocking {
        val served=serve(Exchange("GET","/Users/test-user/Items","{\"Items\":[]}"))
        JellyfinPlayback.queueWatched(MediaItem("tt8888801",imdbId="tt8888801",name="Not in library"),null,true)
        Assert.assertEquals(0,JellyfinPlayback.flushPending());val saved=PlaybackStore().all().single()
        Assert.assertTrue(saved.played);Assert.assertTrue(saved.localOnly);Assert.assertFalse(saved.pending);served.get(6,TimeUnit.SECONDS);Unit
    }
    @org.junit.Ignore("Manual watched mutations retired in 1.3.4; PassiveTracking tests verify read-only behavior")
    @Test fun confirmedAbsentEpisodeKeepsLocalWatchedStateWithoutAFailedQueue()=runBlocking {
        val served=serve(Exchange("GET","/Shows/show-1/Episodes","{\"Items\":[]}"))
        JellyfinPlayback.queueWatched(MediaItem("tt8888802",imdbId="tt8888802",type="series",name="Series",jellyfinId="show-1"),Episode(1,2,"Two"),true)
        Assert.assertEquals(0,JellyfinPlayback.flushPending());Assert.assertTrue(PlaybackStore().all().single().localOnly);Assert.assertEquals(2,PlaybackStore().all().single().media.episode);served.get(6,TimeUnit.SECONDS);Unit
    }
    @org.junit.Ignore("Manual watched mutations retired in 1.3.4; PassiveTracking tests verify read-only behavior")
    @Test fun failedLookupRemainsPendingAndIsNotMislabelledLocalOnly()=runBlocking {
        val served=serve(Exchange("GET","/Users/test-user/Items",status=503))
        JellyfinPlayback.queueWatched(MediaItem("tt8888803",imdbId="tt8888803",name="Unknown availability"),null,true)
        Assert.assertEquals(1,JellyfinPlayback.flushPending());val saved=PlaybackStore().all().single();Assert.assertTrue(saved.pending);Assert.assertFalse(saved.localOnly);served.get(6,TimeUnit.SECONDS);Unit
    }
    @Test fun localOnlyIsPromotedOnlyByAnExactItemInTheSameAccount(){
        val media=MediaItem("tt8888804",imdbId="tt8888804",name="Local viewing")
        val record=PlaybackRecord(media,jellyfinScope(),0,0,true,1,localOnly=true,manual=true)
        val store=PlaybackStore();store.save(record)
        val item=media.copy(id="jellyfin:added",jellyfinId="added",jellyfinUserDataKnown=true)
        store.requeueAvailable("another-account",listOf(item));Assert.assertTrue(store.all().single().localOnly)
        store.requeueAvailable(jellyfinScope(),listOf(item.copy(imdbId="tt0000000")));Assert.assertTrue(store.all().single().localOnly)
        store.requeueAvailable(jellyfinScope(),listOf(item));Assert.assertFalse(store.all().single().localOnly);Assert.assertTrue(store.all().single().pending);Assert.assertEquals("added",store.all().single().jellyfinItemId)
    }
    @Test fun oldUnavailableResultCannotClearANewerUndo(){
        val old=PlaybackRecord(MediaItem("tt8888805",name="Movie"),jellyfinScope(),0,0,true,1,pending=true,manual=true)
        val newer=old.copy(played=false,updatedAt=2);val store=PlaybackStore();store.save(old);store.save(newer);store.markLocalOnly(old)
        Assert.assertEquals(newer,store.all().single())
    }
}
