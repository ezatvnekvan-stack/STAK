package app.stak.nativeapp

import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import app.stak.nativeapp.ui.screens.TitleViewModel
import org.junit.*
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class JellyfinCollectionsProtocolTest {
    private var previous:SettingsStore?=null;private lateinit var prefix:String;private lateinit var server:ServerSocket;private val executor=Executors.newSingleThreadExecutor()
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="sets-http-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix);server=ServerSocket(0,4,java.net.InetAddress.getByName("127.0.0.1"));server.soTimeout=6000;NativeRuntime.settings.apply{jellyfinUrl="http://127.0.0.1:${server.localPort}/jf";jellyfinUserId="fixture";jellyfinToken="synthetic-only"}}
    @After fun cleanup(){server.close();executor.shutdownNow();previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}}
    private fun exchange(respond:(String,Map<String,String>)->String) {
        server.accept().use{socket->socket.soTimeout=5000;val reader=socket.getInputStream().bufferedReader();val url=reader.readLine().split(' ')[1];val params=url.substringAfter('?').split('&').filter{it.contains('=')}.associate{URLDecoder.decode(it.substringBefore('='),"UTF-8") to URLDecoder.decode(it.substringAfter('='),"UTF-8")};val headers=mutableMapOf<String,String>();while(true){val line=reader.readLine();if(line.isEmpty())break;headers[line.substringBefore(':').lowercase()]=line.substringAfter(':').trim()};Assert.assertEquals("synthetic-only",headers["x-emby-token"])
            val body=respond(url.substringBefore('?'),params).toByteArray();socket.getOutputStream().apply{write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n".toByteArray());write(body);flush()}
        }
    }
    @Test fun readsRealUserLibrariesAndBoxSetsWithAuthenticatedBasePaths()=runBlocking {
        val seen=mutableSetOf<String>();val served=executor.submit{repeat(2){exchange{path,params->seen+=path;when(path){"/jf/Users/fixture/Views"->"""{"Items":[{"Id":"movies","Name":"Movies","CollectionType":"movies"},{"Id":"music","Name":"Music","CollectionType":"music"}]}""";else->{Assert.assertEquals("/jf/Users/fixture/Items",path);Assert.assertEquals("BoxSet",params["IncludeItemTypes"]);"""{"TotalRecordCount":1,"Items":[{"Id":"set-1","Name":"Example collection","ChildCount":2}]}"""}}}}}
        val catalog=JellyfinCollections.catalog();Assert.assertEquals("movies",catalog.libraries.single().id);Assert.assertEquals(2,catalog.collections.single().count);Assert.assertTrue(catalog.issues.isEmpty());served.get(7,TimeUnit.SECONDS);Assert.assertEquals(2,seen.size);Unit
    }
    @Test fun selectedMovieVersionReadsItsOwnWatchedStateRatherThanTheFirstProviderMatch()=runBlocking {
        val served=executor.submit{exchange{path,_->Assert.assertEquals("/jf/Users/fixture/Items/version-2",path);"""{"UserData":{"Played":true}}"""}}
        val media=MediaItem("tt1234567","tt1234567",name="Film")
        val vm=withContext(Dispatchers.Main.immediate){TitleViewModel(detailsLoader={_,_->TitleDetails(media)})}
        withContext(Dispatchers.Main.immediate){vm.load("movie","tt1234567",media.copy(jellyfinId="version-2"))}
        val states=withTimeout(6000){vm.remoteWatched.first{it.isNotEmpty()}}
        Assert.assertEquals(true,states["tt1234567"]);served.get(7,TimeUnit.SECONDS);Unit
    }
    @Test fun parentIdPersistsAcrossAllPagesAndVersionTechnicalMetadataMapsCorrectly()=runBlocking {
        val served=executor.submit{repeat(2){page->exchange{path,params->Assert.assertEquals("/jf/Users/fixture/Items",path);Assert.assertEquals("set-1",params["ParentId"]);Assert.assertEquals(page.toString(),params["StartIndex"])
            """{"TotalRecordCount":2,"Items":[{"Id":"version-$page","Name":"Film","Type":"Movie","PresentationUniqueKey":"film-key","ProviderIds":{"Imdb":"tt1234567","Tmdb":"42"},"MediaSources":[{"Container":"mkv","MediaStreams":[{"Type":"Video","Height":2160,"Codec":"hevc"}]}]}]}"""
        }}}
        val items=DirectJellyfin.library(NativeRuntime.settings,parentId="set-1");Assert.assertEquals(2,items.size);Assert.assertTrue("tmdb:42" in items.first().jellyfinProviderKeys.orEmpty());Assert.assertEquals("4K · HEVC · MKV",items.first().jellyfinVersionLabel);Assert.assertEquals(1,groupJellyfinVersions(items).size);served.get(7,TimeUnit.SECONDS);Unit
    }
}
