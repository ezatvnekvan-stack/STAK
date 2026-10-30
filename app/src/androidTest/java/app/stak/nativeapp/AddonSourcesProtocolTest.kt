package app.stak.nativeapp

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.data.db.*
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.*

class AddonSourcesProtocolTest {
    @Test fun aioAndTorrentioResponsesAreNormalizedWithoutResolvingPlaybackUrls()=runBlocking {
        val requested=mutableListOf<String>()
        val client=AddonSourceClient(addons={listOf(StreamAddon("aio","AIOStreams","https://example.org/config-secret/manifest.json"),StreamAddon("torrentio","Torrentio","https://torrentio.strem.fun/manifest.json"))},request={url->
            requested+=url
            JsonParser.parseString("""{"streams":[{"infoHash":"${"a".repeat(40)}","description":"Example.Movie.2024.2160p.WEB-DL.HEVC.ENG","fileIdx":0},{"url":"https://unrelated.example/resolve-secret","description":"Example.Movie.2024.1080p.WEB-DL"}]}""").asJsonObject
        })
        val result=client.search("movie","tt123")
        Assert.assertEquals(2,requested.size);Assert.assertTrue(requested.all{it.endsWith("/stream/movie/tt123.json")});Assert.assertFalse(requested.any{it.contains("resolve-secret")})
        Assert.assertEquals(1,result.releases.count{it.infoHash!=null});Assert.assertTrue(result.releases.first().releaseText!!.contains("ENG"))
    }
    @Test fun badAddonDoesNotSilentlyEraseGoodResultsAndAllFailuresAreErrors()=runBlocking {
        val addons=listOf(StreamAddon("one","Good","https://good.example/manifest.json"),StreamAddon("two","Unavailable","https://bad.example/manifest.json"))
        val client=AddonSourceClient(addons={addons},request={url->if(url.contains("bad.example"))error("secret-url-must-not-escape")else JsonParser.parseString("""{"streams":[]}""").asJsonObject})
        Assert.assertEquals(listOf("Unavailable: search failed; check the addon configuration."),client.search("movie","tt123").warnings)
        val failed=AddonSourceClient(addons={addons},request={error("unavailable")})
        try{failed.search("movie","tt123");Assert.fail("Must fail when every addon failed")}catch(_:java.io.IOException){}
    }
    @Test fun sourceConfigurationIsEncryptedAndDoesNotIncludeTorBoxCredentials() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val prefix="addons-${java.util.UUID.randomUUID()}-"
        try{val settings=SettingsStore(context,prefix);settings.torboxKey="torbox-test-key";StreamAddonStore(settings).save(listOf(StreamAddon("a","AIO","https://example.org/private-config/manifest.json")));Assert.assertEquals("AIO",StreamAddonStore(SettingsStore(context,prefix)).all().single().name);Assert.assertNull(context.getSharedPreferences(prefix+"stak_settings",0).getString("stream_addons",null));Assert.assertFalse(settings.streamAddonsJson!!.contains("torbox-test-key"))}
        finally{listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
    }
    @Test fun disabledAddonsCannotIssueRequests()=runBlocking {
        var requests=0
        val client=AddonSourceClient(addons={listOf(StreamAddon("off","Disabled","https://example.org/manifest.json",false))},request={requests++;JsonParser.parseString("""{"streams":[]}""").asJsonObject})
        try{client.search("movie","tt123");Assert.fail("No source is enabled")}catch(_:IllegalArgumentException){}
        Assert.assertEquals(0,requests)
    }
    @Test fun playbackOnlyAddonExplainsWhyItsLinksCannotBeAdded()=runBlocking {
        val client=AddonSourceClient(addons={listOf(StreamAddon("aio","AIOStreams","https://example.org/manifest.json"))},request={JsonParser.parseString("""{"streams":[{"url":"https://example.org/playback","description":"Film.2160p"}]}""").asJsonObject})
        val result=client.search("movie","tt123")
        Assert.assertEquals(1,result.releases.size);Assert.assertTrue(result.warnings.single().contains("playback-only"));Assert.assertNull(result.releases.single().infoHash)
    }
}
