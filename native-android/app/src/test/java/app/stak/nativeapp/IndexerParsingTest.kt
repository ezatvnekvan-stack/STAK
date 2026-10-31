package app.stak.nativeapp

import org.junit.Assert.*
import org.junit.Test

class IndexerParsingTest {
    @Test
    fun endpointsAllowLocalServersButRejectEmbeddedCredentialsAndOtherSchemes() {
        assertEquals(
            "http://192.168.1.2:9696/1/api",
            indexerUrl("http://192.168.1.2:9696/1/api").toString(),
        )
        for (value in
            listOf(
                "file:///tmp/test",
                "javascript:alert(1)",
                "https://user:secret@example.invalid/api",
            )) {
            assertTrue(runCatching { indexerUrl(value) }.isFailure)
        }
        assertEquals(
            "Torznab",
            validateIndexer(
                    Indexer(name = " ", kind = "torznab", url = "https://example.invalid/api")
                )
                .name,
        )
    }

    @Test
    fun hashesNormalizeBothSupportedFormats() {
        assertEquals(
            "a".repeat(40),
            normalizeTorrentHash("magnet:?xt=urn:btih:${"A".repeat(40)}&dn=Test"),
        )
        assertEquals("0".repeat(40), normalizeTorrentHash("A".repeat(32)))
        assertEquals("", normalizeTorrentHash("not-a-hash"))
    }

    @Test
    fun torznabParsesCdataEntitiesEnclosuresAndHashAttributesWithoutInventingHashes() {
        val result =
            parseTorznab(
                """<?xml version="1.0"?><rss xmlns:torznab="http://torznab.com/schemas/2015/feed"><channel>
          <item><title><![CDATA[Test & title]]></title><enclosure url="https://example.invalid/file.torrent" length="1073741824"/>
            <torznab:attr name="infohash" value="${"A".repeat(40)}"/><torznab:attr name="seeders" value="12"/></item>
          <item><title>Duplicate</title><link>magnet:?xt=urn:btih:${"a".repeat(40)}&amp;dn=Test</link></item>
          <item><title>Missing hash</title><link>https://example.invalid/file.torrent</link></item>
          <item><title>Base32</title><torznab:attr name="magneturl" value="magnet:?xt=urn:btih:${"A".repeat(32)}"/></item>
        </channel></rss>""",
                "Test source",
            )
        assertEquals(2, result.streams.size)
        assertEquals(1, result.missingHashes)
        val item = result.streams.first()
        assertEquals("Test & title", item.title)
        assertEquals("a".repeat(40), item.hash)
        assertEquals(1073741824L, item.size)
        assertEquals(12, item.seeders)
        assertEquals("Test source", item.source)
    }

    @Test
    fun torznabRejectsProviderErrorsAndExternalEntityDeclarations() {
        assertTrue(
            runCatching {
                    parseTorznab("<error code=\"100\" description=\"Invalid API key\"/>", "Test")
                }
                .isFailure
        )
        assertTrue(
            runCatching {
                    parseTorznab(
                        "<!DOCTYPE rss [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><rss>&xxe;</rss>",
                        "Test",
                    )
                }
                .isFailure
        )
    }
}
