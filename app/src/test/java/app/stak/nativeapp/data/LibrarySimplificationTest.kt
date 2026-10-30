package app.stak.nativeapp.data

import org.junit.Assert.*
import org.junit.Test
class LibrarySimplificationTest {
    private val queued=LibraryScanState(phase="queued",requested=true,baseline="old",allowed=true)
    @Test fun acceptedRequestCannotCompleteFromAnOldSuccessfulScan(){val next=reduceLibraryScan(queued,LibraryScanSnapshot("Idle",100f,"old","Completed"));assertEquals("queued",next.phase);assertNull(next.progress)}
    @Test fun realRunningPercentageIsShown(){val next=reduceLibraryScan(queued,LibraryScanSnapshot("Running",37f,"old","Completed"));assertEquals("running",next.phase);assertEquals(.37f,next.progress!!,.001f)}
    @Test fun noReportedPercentageStaysIndeterminate(){assertNull(reduceLibraryScan(queued,LibraryScanSnapshot("Running")).progress)}
    @Test fun newCompletedExecutionFinishesTheProgress(){val next=reduceLibraryScan(queued,LibraryScanSnapshot("Idle",null,"new","Completed"));assertEquals("completed",next.phase);assertEquals(1f,next.progress)}
    @Test fun externalScanCapturesItsPreviousExecutionBaseline(){val running=reduceLibraryScan(LibraryScanState(),LibraryScanSnapshot("Running",30f,"old","Completed"));assertEquals("old",running.baseline);assertEquals("queued",reduceLibraryScan(running,LibraryScanSnapshot("Idle",null,"old","Completed")).phase);assertEquals("completed",reduceLibraryScan(running,LibraryScanSnapshot("Idle",null,"new","Completed")).phase)}
    @Test fun failedOrCancelledScanDoesNotDisplay100Percent(){for(status in listOf("Failed","Cancelled","Aborted")){val state=reduceLibraryScan(queued,LibraryScanSnapshot("Idle",100f,"new",status));assertNotEquals("completed",state.phase);assertNull(state.progress)}}
    @Test fun malformedServerStateCannotCompleteAScan(){assertNotEquals("completed",reduceLibraryScan(queued,LibraryScanSnapshot("Unknown",100f,"new","Completed")).phase)}
    @Test fun compactReportKeepsOnlyImportantGroups(){val rows=compactQualityFacts(inferredQuality("Film.2160p.HDR10.HEVC.TrueHD.Atmos.mkv",1000));assertTrue(rows.size<=5);assertTrue(rows.any{it.key=="video"&&it.value!!.contains("2160p")});assertFalse(rows.any{it.key=="release"||it.key=="primaries"})}
    @Test fun mixedFileConfidenceIsExplicit(){val report=inferredQuality("Film.mkv",1000);assertTrue(compactQualityConfidence(report,"file").contains("REPORTED"));assertTrue(compactQualityConfidence(report,"file").contains("INFERRED"))}
    @Test fun fileManagementRejectsAmbiguousTorrentMatches(){val entry=DavEntry("https://dav/x","e.mkv","e.mkv",false,100);val torrent=TorBoxTorrent("1","A",100,1f,true,"cached",listOf(TorBoxFile("1","e.mkv",100)));assertEquals(torrent,matchingTorBoxOwner(entry,listOf(torrent)));assertNull(matchingTorBoxOwner(entry,listOf(torrent,torrent.copy(id="2"))))}
    @Test fun fileManagementUsesFileHrefWhenDisplayNameIsDifferent(){val entry=DavEntry("https://dav/x","folder/e.mkv","Pretty title",false,100);val torrent=TorBoxTorrent("1","A",100,1f,true,"cached",listOf(TorBoxFile("1","folder/e.mkv",100)));assertEquals(torrent,matchingTorBoxOwner(entry,listOf(torrent)))}
}
