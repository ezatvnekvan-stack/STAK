package app.stak.nativeapp

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AddonFactsTest {
    @Test fun torrentioFactsMatchTheReferenceWithoutReplacingProviderIdentity() {
        val stream = parseAddonStream(JSONObject().put("infoHash", "a".repeat(40)).put("name", "Torrentio 4K")
            .put("title", "Show.S01.2160p.WEB-DL\n👤 40 💾 26.96 GB ⚙️ ThePirateBay"), "Torrentio")!!
        assertEquals("Torrentio", stream.source)
        assertEquals("ThePirateBay", stream.tracker)
        assertEquals(40, stream.seeders)
        assertEquals(28_948_079_575L, stream.size)
        assertEquals("file", stream.sizeScope)
    }
    @Test fun missingFactsStayUnknownAndExplicitVideoSizeIsRetained() {
        val json = JSONObject().put("infoHash", "b".repeat(40)).put("behaviorHints", JSONObject().put("filename", "Movie.1080p").put("videoSize", 1234))
        val stream = parseAddonStream(json, "Addon")!!
        assertEquals("Movie.1080p", stream.title)
        assertEquals(1234L, stream.size)
        assertNull(stream.seeders)
        assertEquals("", stream.tracker)
        assertNull(parseAddonStream(JSONObject().put("infoHash", "invalid"), "Addon"))
    }
}
