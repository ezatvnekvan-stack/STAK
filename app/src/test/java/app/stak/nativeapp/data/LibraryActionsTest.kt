package app.stak.nativeapp.data

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class LibraryActionsTest {
    private val hash="a".repeat(40)
    @Test fun hashesAndMagnetsHaveTheSameCanonicalIdentityAndTrackersArePreserved() {
        val magnet="magnet:?xt=urn:btih:${hash.uppercase()}&tr=https%3A%2F%2Ftracker.example.invalid%2Fannounce&dn=Example"
        assertEquals(hash,parseMagnetInput(magnet).identity);assertEquals(magnet,parseMagnetInput(magnet).magnet)
        assertEquals(hash,parseMagnetInput(hash.uppercase()).identity);assertEquals("0".repeat(40),parseMagnetInput("A".repeat(32)).identity)
    }
    @Test fun duplicateInputsAreSubmittedOnlyOnceAndBatchSizeIsBounded() {
        assertEquals(1,parseMagnetBatch("$hash\nmagnet:?xt=urn:btih:$hash").size)
        try{parseMagnetBatch((1..51).joinToString("\n"){hash});fail("Bounded batch")}catch(_:IllegalArgumentException){}
    }
    @Test fun nonMagnetUrlsBadHashesAndFragmentsAreRejected() {
        listOf("https://example.invalid/file.torrent","wrong","magnet:?dn=NoHash","magnet:?xt=urn:btih:$hash#fragment").forEach{value->try{parseMagnetInput(value);fail("Rejected invalid input")}catch(_:IllegalArgumentException){}}
    }
    @Test fun torrentFileBoundIsEnforcedEvenWhenTheProviderDoesNotReportSize() {
        var closed=false
        val stream=object:ByteArrayInputStream(ByteArray(MAX_TORRENT_BYTES+1)){override fun close(){closed=true;super.close()}}
        try{readTorrentUpload("large.torrent",stream);fail("Size bounded")}catch(_:IllegalArgumentException){}
        assertTrue(closed)
    }
    @Test fun fileExtensionAndDisplayNameAreValidatedBeforeUpload() {
        val bytes="d4:infodee".toByteArray()
        assertArrayEquals(bytes,readTorrentUpload("Example.TORRENT",ByteArrayInputStream(bytes)).bytes)
        listOf("bad.txt","../unsafe.torrent","bad\nname.torrent").forEach{name->try{validateTorrentUpload(TorrentUpload(name,bytes));fail("Invalid name")}catch(_:IllegalArgumentException){}}
    }
    private fun item(id:String,imdb:String?=null,type:String="movie",keys:List<String> = emptyList(),presentation:String?=null)=MediaItem("jellyfin:$id",imdb,type,"Same name",year="2026",jellyfinId=id,jellyfinProviderKeys=keys,jellyfinPresentationKey=presentation)
    @Test fun equivalentProviderIdsMergeTransitivelyAcrossRawVersions() {
        val rows=listOf(item("one",keys=listOf("imdb:tt1234567")),item("two",keys=listOf("imdb:tt1234567","tmdb:42")),item("three",keys=listOf("tmdb:42")))
        val grouped=groupJellyfinVersions(rows);assertEquals(1,grouped.size);assertEquals(3,grouped.single().jellyfinVersionIds.orEmpty().size)
    }
    @Test fun identicalNamesAndRemakesDoNotMergeWithoutVerifiedIdentity() {
        assertEquals(2,groupJellyfinVersions(listOf(item("one"),item("two"))).size)
        assertEquals(2,groupJellyfinVersions(listOf(item("one","tt1234567"),item("two","tt7654321"))).size)
        assertEquals(2,groupJellyfinVersions(listOf(item("one","tt1234567"),item("two","tt1234567",type="series"))).size)
    }
    @Test fun serverPresentationKeysMergeVersionsAndLatestResumeWinsOverPlayedCopy() {
        val rows=listOf(item("played",presentation="verified").copy(watched=true),item("older",presentation="verified").copy(positionMs=100,lastPlayedAt=10),item("latest",presentation="verified").copy(positionMs=200,lastPlayedAt=20))
        val grouped=groupJellyfinVersions(rows).single();assertEquals("latest",grouped.jellyfinId);assertFalse(grouped.watched);assertEquals(200L,grouped.positionMs)
    }
    @Test fun watchedCopyIsRepresentativeWhenNoVersionHasResumeProgress() {
        assertTrue(groupJellyfinVersions(listOf(item("one","tt1234567"),item("two","tt1234567").copy(watched=true))).single().watched)
    }
    @Test fun oldSavedMediaJsonRemainsCopyableWithNewVersionFieldsAbsent() {
        val old="""{"id":"tt1234567","imdbId":"tt1234567","type":"movie","name":"Old saved title","genres":[],"aliases":[]}"""
        val item=Gson().fromJson(old,MediaItem::class.java).copy(addedAt=123)
        assertEquals("Old saved title",item.name);assertTrue(item.jellyfinProviderKeys.orEmpty().isEmpty());assertEquals(1,groupJellyfinVersions(listOf(item)).size)
    }
}
