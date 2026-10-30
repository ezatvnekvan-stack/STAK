package app.stak.nativeapp.data
import org.junit.Assert.*
import org.junit.Test
class TrailerEmbedTest {
    @Test fun normalYoutubeLinksResolveTheSameVideo(){val id="AbCdEf12_-3";assertEquals(id,youtubeTrailerId("https://www.youtube.com/watch?v=$id&feature=share"));assertEquals(id,youtubeTrailerId("https://youtu.be/$id"));assertEquals(id,youtubeTrailerId("https://www.youtube.com/embed/$id"))}
    @Test fun untrustedSchemesHostsAndCredentialsAreRejected(){for(url in listOf("javascript:alert(1)","http://www.youtube.com/watch?v=AbCdEf12_-3","https://youtube.com.evil.test/watch?v=AbCdEf12_-3","https://user@youtube.com/watch?v=AbCdEf12_-3","file:///tmp/video"))assertNull(youtubeTrailerId(url))}
    @Test fun invalidIdsCannotBeInjectedIntoHtml(){assertNull(youtubeTrailerId("https://www.youtube.com/watch?v=bad%22id"));assertNull(youtubeTrailerId("https://youtu.be/short"));assertNull(youtubeTrailerId("not a URL"))}
    @Test fun playerIdentifiesTheEmbeddingAppAndEnablesInlineFullscreenPlayback(){val html=trailerEmbedHtml("AbCdEf12_-3","app.stak.nativeapp.parity");assertTrue(html.contains("origin=https%3A%2F%2Fapp.stak.nativeapp.parity"));assertTrue(html.contains("strict-origin-when-cross-origin"));assertTrue(html.contains("enablejsapi=1"));assertTrue(html.contains("playsinline=1"));assertTrue(html.contains("allowfullscreen"))}
    @Test(expected=IllegalArgumentException::class) fun invalidPackageCannotInjectHtml(){trailerEmbedHtml("AbCdEf12_-3","bad<origin>")}
}
