package app.stak.nativeapp.data

import com.google.gson.JsonParser
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LibraryCollectionsTest {
    @Test fun loadsPastTheOldFiveHundredLimitAndReportsIncrementalProgress()=runBlocking {
        val offsets=mutableListOf<Int>();val progress=mutableListOf<Int>()
        val result=loadLibraryPages(identity={it.toString()},fetch={offset,limit->offsets+=offset;LibraryPage((offset until minOf(offset+limit,537)).toList(),537)},onPage={items,_->progress+=items.size})
        assertEquals(537,result.size);assertEquals(listOf(0,200,400),offsets);assertEquals(listOf(200,400,537),progress)
    }
    @Test fun knownTotalsContinueThroughAServerSidePageCap()=runBlocking {
        var calls=0
        val result=loadLibraryPages(identity={it.toString()},fetch={offset,_->calls++;LibraryPage((offset until minOf(offset+3,8)).toList(),8)})
        assertEquals((0..7).toList(),result);assertEquals(3,calls)
    }
    @Test fun unknownTotalsFetchFollowingPageAfterExactlyFullPage()=runBlocking {
        val offsets=mutableListOf<Int>()
        val result=loadLibraryPages(pageSize=2,identity={it.toString()},fetch={offset,_->offsets+=offset;LibraryPage(if(offset==0)listOf(1,2) else emptyList())})
        assertEquals(listOf(1,2),result);assertEquals(listOf(0,2),offsets)
    }
    @Test fun repeatedPageStopsInsteadOfLoopingForever()=runBlocking {
        val progress=mutableListOf<List<Int>>();var calls=0
        try {loadLibraryPages(pageSize=2,identity={it.toString()},fetch={_,_->calls++;LibraryPage(listOf(1,2),8)},onPage={items,_->progress+=listOf(items)});fail("Expected repeated-page rejection")}catch(_:IOException){}
        assertEquals(2,calls);assertEquals(listOf(listOf(1,2)),progress)
    }
    @Test fun rawOffsetAdvancesEvenWhenOverlappingIdsAreDeduplicated()=runBlocking {
        val offsets=mutableListOf<Int>()
        val result=loadLibraryPages(pageSize=2,identity={it.toString()},fetch={offset,_->offsets+=offset;LibraryPage(when(offset){0->listOf(1,2);2->listOf(2,3);else->listOf(4)},5)})
        assertEquals(listOf(1,2,3,4),result);assertEquals(listOf(0,2,4),offsets)
    }
    @Test fun incompleteKnownTotalIsAnErrorAndPublishedRowsRemainAvailable()=runBlocking {
        var published=emptyList<Int>()
        try {loadLibraryPages(identity={it.toString()},fetch={offset,_->LibraryPage(if(offset==0)listOf(1) else emptyList(),3)},onPage={items,_->published=items});fail("Expected incomplete error")}catch(_:IOException){}
        assertEquals(listOf(1),published)
    }
    @Test fun cancellationDoesNotRequestAnotherPage()=runBlocking {
        var calls=0
        val job=launch {
            loadLibraryPages(pageSize=1,identity={it.toString()},fetch={_,_->calls++;LibraryPage(listOf(calls))},onPage={_,_->currentCoroutineContext().cancel()})
        };job.join();assertTrue(job.isCancelled);assertEquals(1,calls)
    }
    private val titles=listOf(MediaItem("a",name="Árvíz",year="2020",watched=true,addedAt=10),MediaItem("b",type="series",name="Silo",year="2023",progress=.3f,addedAt=30),MediaItem("c",name="Dune",year="2021",imdbId="tt1160419",addedAt=20))
    @Test fun jellyfinSearchCombinesAccentInsensitiveTextTypeAndWatchedState() {
        assertEquals(listOf("a"),filterJellyfinLibrary(titles,LibraryFilters(query="arviz",status="watched")).map{it.id})
        assertEquals(listOf("b"),filterJellyfinLibrary(titles,LibraryFilters(type="series",status="progress")).map{it.id})
        assertEquals(listOf("c"),filterJellyfinLibrary(titles,LibraryFilters(query="tt1160419")).map{it.id})
    }
    @Test fun jellyfinSortingUsesActualAddedDateOrReleaseYear() {
        assertEquals(listOf("b","c","a"),filterJellyfinLibrary(titles,LibraryFilters(sort="newest")).map{it.id})
        assertEquals(listOf("b","c","a"),filterJellyfinLibrary(titles,LibraryFilters(sort="year")).map{it.id})
    }
    private fun torrent(id:String,done:Boolean=false,state:String="downloading",size:Long=1,created:Long=0)=TorBoxTorrent(id,"Torrent $id",size,.8f,done,state,listOf(TorBoxFile("file","Hidden.Name.mkv",5)),hash="hash-$id",createdAt=created)
    @Test fun torrentStatusDoesNotMistakeAStateStringOrFullProgressForDownloadFinished() {
        assertEquals("active",torBoxStatus(torrent("one",state="completed").copy(progress=1f)))
        assertEquals("done",torBoxStatus(torrent("one",done=true,state="uploading")))
        assertEquals("error",torBoxStatus(torrent("one",state="error")))
    }
    @Test fun torboxSearchIncludesFileNamesHashAndResolvedTitleWithStatusFilters() {
        val rows=listOf(TorBoxLibraryEntry(torrent("one",done=true),MediaItem("tt1",name="Resolved")),TorBoxLibraryEntry(torrent("two")))
        assertEquals(2,filterTorBoxLibrary(rows,LibraryFilters(query="hidden.name")).size)
        assertEquals("one",filterTorBoxLibrary(rows,LibraryFilters(query="HASH-ONE",status="done")).single().torrent.id)
        assertEquals("one",filterTorBoxLibrary(rows,LibraryFilters(query="resolved")).single().torrent.id)
        assertTrue(filterTorBoxLibrary(rows,LibraryFilters(query="resolved",status="active")).isEmpty())
    }
    @Test fun torboxSortUsesSizeAndCreatedDateWithoutMutatingInput() {
        val rows=listOf(TorBoxLibraryEntry(torrent("one",size=5,created=30)),TorBoxLibraryEntry(torrent("two",size=10,created=20)))
        assertEquals("two",filterTorBoxLibrary(rows,LibraryFilters(sort="size")).first().torrent.id)
        assertEquals("one",filterTorBoxLibrary(rows,LibraryFilters(sort="newest")).first().torrent.id)
        assertEquals("one",rows.first().torrent.id)
    }
    @Test fun torboxParsingPreservesFullFileNameNullableFieldsHashAndCreatedDate() {
        val item=parseTorBoxTorrent(JsonParser.parseString("""{"id":42,"name":"Pack","size":1024,"progress":1.4,"download_finished":true,"download_state":"uploading","hash":"abc","created_at":"2026-01-01T00:00:00Z","files":[{"id":1,"name":"Folder/Silo.S01E01.mkv","short_name":"Silo.S01E01.mkv","size":500}]}""").asJsonObject)
        assertEquals("Folder/Silo.S01E01.mkv",item.files.single().name);assertEquals("abc",item.hash);assertTrue(item.createdAt>0);assertEquals(1f,item.progress)
        assertTrue(parseTorBoxTorrent(JsonParser.parseString("""{"id":43,"files":null}""").asJsonObject).files.isEmpty())
    }
}
