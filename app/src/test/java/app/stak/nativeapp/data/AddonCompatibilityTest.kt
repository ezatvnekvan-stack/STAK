package app.stak.nativeapp.data

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class AddonCompatibilityTest{
    @Test fun aggregatorFilenameProvidesQualityWhenDescriptionIsJustHumanReadable(){
        val release=parseSourceRelease(JsonParser.parseString("""{"infoHash":"${"a".repeat(40)}","description":"A film worth watching","behaviorHints":{"filename":"Film.2160p.UHD.REMUX.DV.Atmos.HUN.mkv"}}""").asJsonObject)!!
        assertEquals("2160p",release.resolution);assertTrue(release.remux);assertTrue(release.dolbyVision);assertTrue(release.atmos);assertEquals(listOf("HUN"),release.languages)
    }
    @Test fun explicitMagnetIsNormalizedIntoAddableTorrentIdentity(){
        val release=parseSourceRelease(JsonParser.parseString("""{"url":"magnet:?xt=urn:btih:${"A".repeat(40)}","description":"Film.1080p.REMUX"}""").asJsonObject)!!
        assertEquals("a".repeat(40),release.infoHash);assertNull(release.streamUrl)
    }
    @Test fun playbackUrlIsNeverGuessedToBeATorrentIdentity(){
        val release=parseSourceRelease(JsonParser.parseString("""{"url":"https://example.org/private/${"a".repeat(40)}","description":"Film"}""").asJsonObject)!!
        assertNull(release.infoHash)
    }
}
