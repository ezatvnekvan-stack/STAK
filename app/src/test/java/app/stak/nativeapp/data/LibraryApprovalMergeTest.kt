package app.stak.nativeapp.data

import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class LibraryApprovalMergeTest {
    private val account="a".repeat(64)
    private fun remote(id:String="123",approved:Boolean=true,scope:String=account)=JsonObject().apply {
        addProperty("id",id);addProperty("scope",scope);addProperty("approved",approved)
        addProperty("name","VisionQuest");addProperty("type","series");addProperty("hash","b".repeat(40))
        addProperty("state",if(approved)"published" else "revoked")
    }
    @Test fun importsWatcherApprovalAsAlreadyAcknowledged(){
        val result=mergeFilterApprovals(ApprovalDocument(),account,"filter",listOf(remote()),42)
        val row=result.entries.single()
        assertEquals("123",row.id);assertEquals("filter",row.sentTo);assertEquals("published",row.state)
        assertEquals("VisionQuest",row.name);assertEquals("series",row.type);assertEquals(42L,row.at)
        assertTrue(row.approved)
    }
    @Test fun repeatedPullIsIdempotent(){
        val first=mergeFilterApprovals(ApprovalDocument(),account,"filter",listOf(remote()),42)
        assertEquals(first,mergeFilterApprovals(first,account,"filter",listOf(remote()),99))
    }
    @Test fun pendingLocalRemovalWins(){
        val pending=LibraryApproval("123",account,approved=false,sentTo=null)
        val doc=ApprovalDocument(entries=listOf(pending))
        assertEquals(doc,mergeFilterApprovals(doc,account,"filter",listOf(remote())))
    }
    @Test fun pendingLocalTypeChangeWins(){
        val pending=LibraryApproval("123",account,type="movie",sentTo=null)
        val doc=ApprovalDocument(entries=listOf(pending))
        assertEquals(doc,mergeFilterApprovals(doc,account,"filter",listOf(remote())))
    }
    @Test fun pullsRemoteRevocationWithoutReapproval(){
        val row=LibraryApproval("123",account,sentTo="filter")
        val result=mergeFilterApprovals(ApprovalDocument(entries=listOf(row)),account,"filter",listOf(remote(approved=false)))
        assertFalse(result.entries.single().approved);assertEquals("revoked",result.entries.single().state)
        assertEquals("filter",result.entries.single().sentTo)
    }
    @Test fun foreignScopeAndDuplicateIdsAreRejected(){
        assertTrue(mergeFilterApprovals(ApprovalDocument(),account,"filter",listOf(remote(scope="c".repeat(64)))).entries.isEmpty())
        assertTrue(mergeFilterApprovals(ApprovalDocument(),account,"filter",listOf(remote(),remote())).entries.isEmpty())
    }
    @Test fun rejectsInvalidDecisionHashAndId(){
        val invalid=listOf(remote().apply{addProperty("approved","true")},remote().apply{addProperty("hash","invalid")},remote(id="bad"))
        invalid.forEach{assertTrue(mergeFilterApprovals(ApprovalDocument(),account,"filter",listOf(it)).entries.isEmpty())}
    }
    @Test fun missingRemoteRowsNeverEraseLocalHistory(){
        val doc=ApprovalDocument(entries=listOf(LibraryApproval("123",account,sentTo="filter")))
        assertEquals(doc,mergeFilterApprovals(doc,account,"filter",emptyList()))
    }
    @Test fun preservesOtherAccountsAndEndpointPendingRows(){
        val foreign=LibraryApproval("123","c".repeat(64),sentTo="filter")
        val otherEndpoint=LibraryApproval("123",account,sentTo="old-filter")
        val doc=ApprovalDocument(entries=listOf(foreign,otherEndpoint))
        assertEquals(doc,mergeFilterApprovals(doc,account,"filter",listOf(remote())))
    }

    @Test fun verifiedMovieMetadataIncludesTitleYearAndProvider(){
        val identity=approvalMedia(MediaItem("tt1234567",imdbId="tt1234567",name="A Film",year="2026"))!!
        assertEquals("tt1234567",identity.imdb);assertEquals(2026,identity.year);assertEquals("A Film",identity.title)
    }
    @Test fun invalidProviderIdIsNotInvented(){
        val identity=approvalMedia(MediaItem("jellyfin:local",name="Unknown",year="unknown"))!!
        assertNull(identity.imdb);assertNull(identity.tmdb);assertNull(identity.year)
    }
    @Test fun pullsVerifiedServerMetadata(){
        val row=remote().apply{add("media",com.google.gson.JsonParser.parseString("""{"title":"VisionQuest","imdb":"tt23112594","year":2026}"""))}
        val result=mergeFilterApprovals(ApprovalDocument(),account,"filter",listOf(row))
        assertEquals("tt23112594",result.entries.single().media?.imdb)
        assertEquals(2026,result.entries.single().media?.year)
    }
}
