package app.stak.nativeapp

import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import kotlinx.coroutines.*
import org.junit.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.*
import java.util.concurrent.*

class TorznabProtocolTest {
    private var previous:SettingsStore?=null;private lateinit var prefix:String
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="nab-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix);StreamAddonStore(NativeRuntime.settings).save(emptyList())}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
    private data class Reply(val code:Int=200,val type:String="application/xml",val body:String="",val extra:String="")
    private class Http(val handler:(String,Map<String,String>)->Reply):AutoCloseable {
        val server=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));val requests=CopyOnWriteArrayList<String>();val failures=CopyOnWriteArrayList<Throwable>();val pool=Executors.newCachedThreadPool();val url="http://127.0.0.1:${server.localPort}"
        init{pool.submit{while(!server.isClosed)try{val socket=server.accept();pool.submit{socket.use{try{it.soTimeout=5000;val reader=it.getInputStream().bufferedReader();val line=reader.readLine();val path=line.split(' ')[1];val headers=mutableMapOf<String,String>();while(true){val h=reader.readLine();if(h.isEmpty())break;headers[h.substringBefore(':').lowercase()]=h.substringAfter(':').trim()};Assert.assertTrue(line.startsWith("GET "));requests+=path;val reply=handler(path,headers);val bytes=reply.body.toByteArray();it.getOutputStream().apply{write("HTTP/1.1 ${reply.code} Test\r\nContent-Type: ${reply.type}\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n${reply.extra}\r\n".toByteArray());write(bytes);flush()}}catch(error:Throwable){failures+=error}}}}catch(_:java.io.IOException){}}}
        fun check(){Assert.assertTrue(failures.joinToString(),failures.isEmpty())};override fun close(){server.close();pool.shutdownNow()}
    }
    private val caps="""<caps><limits max="100"/><searching><search available="no" supportedParams="q"/><movie-search available="yes" supportedParams="imdbid"/><tv-search available="yes" supportedParams="imdbid,season,ep"/></searching></caps>"""
    @Test fun onlyTorznabConfigurationSearchesAioByIdAndPreservesPrivateMagnet()=runBlocking {
        val key="uuid:private-password"
        Http{path,headers->val url=("http://localhost"+path).toHttpUrl();Assert.assertEquals(key,url.queryParameter("apikey"));Assert.assertNull(headers["authorization"]);Assert.assertNull(headers["x-emby-token"]);if(url.queryParameter("t")=="caps")Reply(body=caps)else{Assert.assertEquals("movie",url.queryParameter("t"));Assert.assertEquals("1234567",url.queryParameter("imdbid"));Assert.assertNull(url.queryParameter("q"));Reply(body="""<rss><channel><item><title>Film.2160p.REMUX</title><guid>one</guid><link>magnet:?xt=urn:btih:${"a".repeat(40)}&amp;tr=https%3A%2F%2Ftracker.test%2Fprivate</link></item></channel></rss>""")}}.use{server->TorznabStore(NativeRuntime.settings).save(listOf(TorznabIndexer(name="AIOStreams",url=server.url+"/api/v1/torznab",apiKey=key)));val result=SourceSearch.search(NabQuery("movie","tt1234567",title="Film"));Assert.assertEquals(1,result.releases.size);Assert.assertTrue(result.releases.single().magnetUrl!!.contains("tr="));Assert.assertTrue(server.requests.all{it.startsWith("/api/v1/torznab/api?")});server.check()};Unit
    }
    @Test fun metadataFetchNeverForwardsApiKeyAcrossRedirects()=runBlocking {
        Http{path,headers->Assert.assertFalse(path.contains("secret"));Assert.assertNull(headers["authorization"]);Reply(type="application/x-bittorrent",body="d4:infod4:name4:testee")}.use{remote->Http{path,_->Assert.assertTrue(path.contains("apikey=secret"));Reply(302,extra="Location: ${remote.url}/file.torrent\r\n")}.use{origin->val indexer=TorznabIndexer(name="Local",url=origin.url+"/api",apiKey="secret");val source=SourceRelease("Film",torznabIndexerId=indexer.id,torrentFileUrl=origin.url+"/download");val result=TorznabClient().torrent(indexer,source);Assert.assertTrue(result is NabMaterial.Torrent);Assert.assertEquals(1,remote.requests.size);origin.check();remote.check()}};Unit
    }
    @Test fun metadataRedirectCanReturnMagnetWithoutDownloadingMedia()=runBlocking{
        Http{_,_->Reply(302,extra="Location: magnet:?xt=urn:btih:${"a".repeat(40)}&tr=https%3A%2F%2Ftracker.test%2Fprivate\r\n")}.use{server->val indexer=TorznabIndexer(name="Local",url=server.url+"/api");val result=TorznabClient().torrent(indexer,SourceRelease("Film",torrentFileUrl=server.url+"/download"));Assert.assertTrue(result is NabMaterial.Magnet);Assert.assertTrue((result as NabMaterial.Magnet).input.magnet.contains("tr="));server.check()};Unit
    }
    @Test fun videoResponseIsRejectedAsTorrentMetadata()=runBlocking{
        Http{_,_->Reply(type="video/mp4",body="not media metadata")}.use{server->try{TorznabClient().torrent(TorznabIndexer(name="Local",url=server.url+"/api"),SourceRelease("Film",torrentFileUrl=server.url+"/download"));Assert.fail()}catch(_:IllegalArgumentException){};server.check()};Unit
    }
    @Test fun indexerSecretsPersistEncryptedWithoutChangingExistingAddons(){val settings=NativeRuntime.settings;val entry=TorznabIndexer(name="AIO",url="https://indexer.test/api",apiKey="unique-private-key");TorznabStore(settings).save(listOf(entry));Assert.assertEquals(entry.apiKey,TorznabStore(SettingsStore(context,prefix)).all().single().apiKey);Assert.assertTrue(StreamAddonStore(settings).all().isEmpty());val plain=context.getSharedPreferences(prefix+"stak_settings",0).all.toString();val encrypted=java.io.File(context.applicationInfo.dataDir,"shared_prefs/${prefix}stak_secrets.xml").readText();Assert.assertFalse(plain.contains(entry.apiKey));Assert.assertFalse(encrypted.contains(entry.apiKey))}
    @Test fun deniedIndexerDoesNotMasqueradeAsEmptySuccessfulFeed()=runBlocking{Http{_,_->Reply(403)}.use{server->try{TorznabClient().test(TorznabIndexer(name="Local",url=server.url+"/api"));Assert.fail()}catch(error:NabFailure){Assert.assertTrue(error.message!!.contains("authentication"))};server.check()};Unit}
    @Test fun corruptSavedCredentialsAreNeverSilentlyOverwritten(){
        val settings=NativeRuntime.settings;val broken="[{not valid json]"
        settings.torznabIndexersJson=broken
        Assert.assertTrue(runCatching{TorznabStore(settings).save(listOf(TorznabIndexer(name="New",url="https://indexer.test/api")))}.isFailure)
        Assert.assertEquals(broken,SettingsStore(context,prefix).torznabIndexersJson)
    }
}
