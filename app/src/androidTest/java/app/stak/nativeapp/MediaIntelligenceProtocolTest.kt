package app.stak.nativeapp

import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import app.stak.nativeapp.data.*
import app.stak.nativeapp.data.db.AppDatabase
import com.google.gson.Gson
import kotlinx.coroutines.*
import org.junit.*
import java.net.*
import java.util.concurrent.*

class MediaIntelligenceProtocolTest {
    private var saved:SettingsStore?=null
    private lateinit var settings:SettingsStore
    private lateinit var prefix:String
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup(){saved=runCatching{NativeRuntime.settings}.getOrNull();prefix="intelligence-${java.util.UUID.randomUUID()}-";settings=SettingsStore(context,prefix);NativeRuntime.settings=settings;settings.jellyfinToken="synthetic-jellyfin";settings.jellyfinUserId="me"}
    @After fun cleanup(){MediaLiveSync.stop();saved?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
    private data class Req(val method:String,val path:String,val headers:Map<String,String>,val body:String)
    private data class Reply(val code:Int=200,val body:String="{}",val extra:String="")
    private class Fixture(val handler:(Req)->Reply):AutoCloseable {
        val server=ServerSocket(0,16,InetAddress.getByName("127.0.0.1"));val requests=CopyOnWriteArrayList<Req>();val failures=CopyOnWriteArrayList<Throwable>()
        val pool=Executors.newCachedThreadPool();val url="http://127.0.0.1:${server.localPort}/jf"
        init{pool.submit{while(!server.isClosed){try{val socket=server.accept();pool.submit{socket.use{try{it.soTimeout=5000;val reader=it.getInputStream().bufferedReader();val line=reader.readLine().split(' ');val headers=mutableMapOf<String,String>();while(true){val h=reader.readLine();if(h.isEmpty())break;headers[h.substringBefore(':').lowercase()]=h.substringAfter(':').trim()};val body=CharArray(headers["content-length"]?.toInt() ?: 0);var count=0;while(count<body.size){val n=reader.read(body,count,body.size-count);if(n<0)break;count+=n};val req=Req(line[0],line[1],headers,String(body));requests+=req;val reply=handler(req);val bytes=reply.body.toByteArray();it.getOutputStream().apply{write("HTTP/1.1 ${reply.code} Test\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n${reply.extra}\r\n".toByteArray());write(bytes);flush()}}catch(t:Throwable){failures+=t}}}}catch(_:java.io.IOException){}}}}
        fun assertClean(){Assert.assertTrue(failures.joinToString(),failures.isEmpty())}
        override fun close(){server.close();pool.shutdownNow()}
    }
    @Test fun modernFallbackRetriesOnly404AndPreservesUserAndBasePath()=runBlocking{
        Fixture{req->if(req.path.startsWith("/jf/Users/me/Items/a"))Reply(404)else{Assert.assertTrue(req.path.startsWith("/jf/Items/a?"));Assert.assertTrue(req.path.contains("UserId=me"));Assert.assertEquals("synthetic-jellyfin",req.headers["x-emby-token"]);Reply(body="""{"Id":"a","Name":"Film"}""")}}.use{f->settings.jellyfinUrl=f.url;Assert.assertEquals("a",JellyfinIntelligence.item("a").text("Id"));Assert.assertEquals(2,f.requests.size);f.assertClean()};Unit
    }
    @Test fun authFailureDoesNotRetryOrLeakTokenToAnotherRoute()=runBlocking{
        Fixture{Reply(403)}.use{f->settings.jellyfinUrl=f.url;try{JellyfinIntelligence.item("a");Assert.fail()}catch(e:JellyfinHttpFailure){Assert.assertEquals(403,e.status)};Assert.assertEquals(1,f.requests.size);f.assertClean()};Unit
    }
    @Test fun webDavUsesOnlyPropfindAndItsOwnCredentials()=runBlocking{
        Fixture{req->Assert.assertEquals("PROPFIND",req.method);Assert.assertEquals("1",req.headers["depth"]);Assert.assertEquals(okhttp3.Credentials.basic("dav","private"),req.headers["authorization"]);Assert.assertNull(req.headers["x-emby-token"]);Reply(207,"""<d:multistatus xmlns:d="DAV:"><d:response><d:href>/jf/dav/file.mkv</d:href><d:propstat><d:prop><d:getcontentlength>42</d:getcontentlength></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>""")}.use{f->val listing=WebDavClient().list(ZurgConnection(f.url+"/dav/","dav","private"));Assert.assertEquals(42L,listing.entries.single().size);Assert.assertEquals(1,f.requests.size);f.assertClean()};Unit
    }
    @Test fun webDavNeverFollowsRedirects()=runBlocking{
        Fixture{req->Reply(302,extra="Location: http://127.0.0.1:1/stolen\r\n")}.use{f->try{WebDavClient().list(ZurgConnection(f.url+"/dav/","dav","private"));Assert.fail()}catch(_:java.io.IOException){};Assert.assertEquals(1,f.requests.size);f.assertClean()};Unit
    }
    private fun dashboardReply(req:Req,remote:Boolean=true):Reply=when(req.path.substringBefore('?')){
        "/jf/Users/me"->Reply(body="""{"Policy":{"IsAdministrator":false,"EnableRemoteControlOfOtherUsers":false}}""")
        "/jf/System/Info/Public"->Reply(body="""{"ServerName":"Fixture","Version":"10.11"}""")
        "/jf/Sessions"->Reply(body="""[{"Id":"s","UserId":"me","SupportsRemoteControl":$remote,"SupportsMediaControl":$remote,"NowPlayingItem":{"Name":"Film","RunTimeTicks":1000000000},"PlayState":{"CanSeek":true},"Capabilities":{"SupportedCommands":["SetVolume"]}}]""")
        "/jf/Users/me/Views"->Reply(body="""{"Items":[]}""")
        else->Reply()
    }
    @Test fun remoteVolumeRechecksActualSessionAndUsesSupportedCommand()=runBlocking{
        Fixture{req->if(req.path=="/jf/Sessions/s/Command"){Assert.assertEquals("POST",req.method);val body=com.google.gson.JsonParser.parseString(req.body).asJsonObject;Assert.assertEquals("SetVolume",body.text("Name"));Assert.assertEquals("35",body.objectOrNull("Arguments")?.text("Volume"));Reply(204,"")}else dashboardReply(req)}.use{f->settings.jellyfinUrl=f.url;val s=JellyfinIntelligence.dashboard().sessions.single();JellyfinIntelligence.command(s,"SetVolume",35);Assert.assertEquals(1,f.requests.count{it.method=="POST"});Assert.assertFalse(f.requests.any{it.path.contains("Playing/SetVolume")});f.assertClean()};Unit
    }
    @Test fun staleCapabilityCannotSendRemoteCommand()=runBlocking{
        Fixture{dashboardReply(it,remote=false)}.use{f->settings.jellyfinUrl=f.url;val s=ServerSession("s","Film","TV","client","me",false,0,10000,null,true,true,true,false,null,"");try{JellyfinIntelligence.command(s,"Stop");Assert.fail()}catch(_:IllegalArgumentException){};Assert.assertFalse(f.requests.any{it.method=="POST"});f.assertClean()};Unit
    }
    @Test fun nonAdminCannotRequestScan()=runBlocking{
        Fixture{dashboardReply(it)}.use{f->settings.jellyfinUrl=f.url;try{JellyfinIntelligence.scanLibraries();Assert.fail()}catch(_:IllegalArgumentException){};Assert.assertFalse(f.requests.any{it.method=="POST"});f.assertClean()};Unit
    }
    @Test fun zurgSecretsPersistEncryptedAndSeparateFromOrdinaryPreferences(){val secret="distinct-private-password";settings.zurgConnectionJson=Gson().toJson(ZurgConnection("https://dav.example/","u",secret));Assert.assertEquals(secret,readZurg(SettingsStore(context,prefix)).password);val plain=context.getSharedPreferences(prefix+"stak_settings",0).all.toString();val encrypted=java.io.File(context.applicationInfo.dataDir,"shared_prefs/${prefix}stak_secrets.xml").readText();Assert.assertFalse(plain.contains(secret));Assert.assertFalse(encrypted.contains(secret))}
    @Test fun journalDeduplicatesConfirmedAdditionAndRetainsOtherPreferences()=runBlocking(Dispatchers.IO){
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build();try{val store=IntelligenceStore(db);db.preferenceDao().set(app.stak.nativeapp.data.db.PreferenceEntity("untouched","kept"));store.added("123","torbox","jf",name="Film");store.added("123","torbox","jf",name="Film");Assert.assertEquals(1,store.read().deliveries.size);Assert.assertEquals(1,store.read().events.size);val item=MediaItem("one",name="Film",jellyfinId="abc",jellyfinUserDataKnown=true);store.observe("jf",listOf(item));store.observe("jf",listOf(item.copy(watched=true)));store.observe("jf",listOf(item.copy(watched=true)));Assert.assertEquals(2,store.read().events.size);Assert.assertEquals("kept",db.preferenceDao().get("untouched"))}finally{db.close()};Unit
    }
}
