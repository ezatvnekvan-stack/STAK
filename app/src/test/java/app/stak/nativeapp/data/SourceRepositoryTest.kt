package app.stak.nativeapp.data
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class SourceRepositoryTest {
    private val hash="a".repeat(40)
    private fun json(raw:String)=JsonParser.parseString(raw).asJsonObject
    @Test fun torrentioMetadataMapsQualitySizeAndLanguageWithoutInventingCache() {
        val item=parseSourceRelease(json("""{"infoHash":"$hash","fileIdx":2,"name":"Torrentio 4K","title":"Film.2024.2160p.HDR.DV.Atmos.REMUX.HUN\n👤 42 💾 10.5 GB","behaviorHints":{"videoSize":123456}}"""))!!
        assertEquals("2160p",item.resolution);assertEquals(123456L,item.sizeBytes);assertEquals(42,item.seeders);assertEquals(2,item.fileIndex)
        assertTrue(item.hdr);assertTrue(item.dolbyVision);assertTrue(item.atmos);assertTrue(item.remux);assertTrue("HUN" in item.languages);assertFalse(item.cached)
    }
    @Test fun releaseIndexerComesFromTorrentioTrackerMetadata() {
        val item=parseSourceRelease(json("""{"infoHash":"$hash","title":"Film.2160p\n👤 939 💾 27.59 GB ⚙️ ThePirateBay"}"""))!!
        assertEquals("ThePirateBay",item.indexer);assertEquals(939,item.seeders)
    }
    @Test fun invalidIdentifiersAreRejectedAndSizeCanBeParsedFromTitle() {
        assertNull(parseSourceRelease(json("""{"infoHash":"wrong","url":"http://insecure.invalid"}""")))
        val item=parseSourceRelease(json("""{"infoHash":"$hash","title":"Film.1080p.WEB-DL\n💾 2 GB"}"""))!!
        assertEquals(2147483648L,item.sizeBytes);assertTrue(item.webdl)
    }
    @Test fun onlyConfirmedRequestedHashesAreTrusted() {
        val other="b".repeat(40)
        val data=JsonParser.parseString("""[{"hash":"$hash"},{"hash":"$other","cached":false},{"hash":"${"c".repeat(40)}"}]""")
        assertEquals(setOf(hash),SourceRepository.confirmedHashes(data,setOf(hash,other)))
        assertTrue(SourceRepository.confirmedHashes(JsonParser.parseString("[]"),setOf(hash)).isEmpty())
        assertTrue(SourceRepository.confirmedHashes(JsonParser.parseString("""{"$hash":{"cached":false}}"""),setOf(hash)).isEmpty())
    }
    @Test fun seasonPacksDoNotAccidentallyMatchSingleEpisodesOrOtherSeasons() {
        assertTrue(seasonPackMatches("Show.S01.COMPLETE.1080p",1))
        assertFalse(seasonPackMatches("Show.S01E01.1080p",1))
        assertFalse(seasonPackMatches("Show.S02.COMPLETE",1))
        assertTrue(seasonPackMatches("Show Season 2",2))
    }
    @Test fun filtersUseRealCacheAndUnknownSizesDoNotPassTheMaximum() {
        val one=SourceRelease("One",resolution="2160p",infoHash=hash,hdr=true,sizeBytes=1000,languages=listOf("HUN"),seeders=2)
        val two=SourceRelease("Two",resolution="1080p",infoHash="b".repeat(40),cached=true,seeders=10)
        assertEquals(listOf(one),filteredSources(listOf(one,two),setOf(hash),SourceFilters()))
        assertEquals(listOf(one),filteredSources(listOf(one,two),emptySet(),SourceFilters(cachedOnly=false,maxGb=5)))
        assertEquals(listOf(one,two),filteredSources(listOf(two,one),emptySet(),SourceFilters(cachedOnly=false,preferHun=true)))
        assertEquals(listOf(two,one),filteredSources(listOf(one,two),emptySet(),SourceFilters(cachedOnly=false,sort="seeders")))
    }
}
