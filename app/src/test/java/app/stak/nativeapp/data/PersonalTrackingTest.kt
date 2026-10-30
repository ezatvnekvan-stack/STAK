package app.stak.nativeapp.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.net.SocketTimeoutException

class PersonalTrackingTest {
    private val day=LocalDate.of(2026,10,29)
    private val show=MediaItem("show",type="series",name="Series")
    private val movie=MediaItem("movie",name="Movie",runtime="90 min")
    private fun ep(n:Int,date:LocalDate?=day)=Episode(1,n,"Episode $n",released=date?.toString())
    private fun seen(n:Int)=JournalEntry(show.copy(season=1,episode=n),null)
    @Test fun futureEpisodesDoNotInflateProgressDenominator(){
        val p=SeriesPlan(show,episodes=listOf(ep(1),ep(2,day.plusDays(2))),completeMetadata=true)
        val result=seriesProgress(p,listOf(seen(1)),day)
        assertEquals(1,result.released.size);assertEquals(1,result.watchedCount);assertTrue(result.complete);assertNull(result.next);assertEquals(2,result.upcoming?.episode)
    }
    @Test fun newReleasedEpisodeReturnsCompletedSeriesToWatching(){
        val p=SeriesPlan(show,episodes=listOf(ep(1),ep(2,day.plusDays(1))),completeMetadata=true)
        assertEquals("completed",seriesProgress(p,listOf(seen(1)),day).status)
        assertEquals("watching",seriesProgress(p,listOf(seen(1)),day.plusDays(1)).status)
    }
    @Test fun unknownDatesAreNotInventedAsReleased(){
        val p=SeriesPlan(show,episodes=listOf(ep(1),ep(2,null)),completeMetadata=true)
        val result=seriesProgress(p,listOf(seen(1)),day)
        assertEquals(1,result.unknownDates);assertFalse(result.complete);assertNull(result.next)
    }
    @Test fun partialLibraryEpisodesDoNotClaimWholeSeriesCompletion(){assertFalse(seriesProgress(SeriesPlan(show,episodes=listOf(ep(1)),completeMetadata=false),listOf(seen(1)),day).complete)}
    @Test fun holdAndDroppedRemainManualEvenWhenEpisodesComplete(){listOf("on_hold","dropped").forEach{status->assertEquals(status,seriesProgress(SeriesPlan(show,status,listOf(ep(1)),completeMetadata=true),listOf(seen(1)),day).status)}}
    @Test fun nextEpisodeUsesRealGapNotHighestEpisodePlusOne(){val p=SeriesPlan(show,episodes=listOf(ep(1),ep(2),ep(3)),completeMetadata=true);assertEquals(2,seriesProgress(p,listOf(seen(1),seen(3)),day).next?.episode)}
    @Test fun unknownEpisodesDoNotGetGuessedFromCount(){assertTrue(seriesProgress(SeriesPlan(show),listOf(seen(1)),day).released.isEmpty())}
    @Test fun futureWatchedDoesNotCompleteOtherMissingEpisodes(){assertEquals(0,seriesProgress(SeriesPlan(show,episodes=listOf(ep(1))),listOf(seen(4)),day).watchedCount)}
    @Test fun legacyRowsImportOnceAndRetainUnknownDates(){
        val row=trackingEntity(movie,null,true).copy(lastPlayed=null)
        val first=TrackerState().importRows(listOf(row),"local")
        assertNull(first.events.single().watchedAt);assertEquals(first,first.importRows(listOf(row),"local"))
    }
    @Test fun unwatchPreservesExistingViewingEvents(){
        val row=trackingEntity(movie,null,true,1000)
        val first=TrackerState().addManual(row,null)
        assertEquals(first.events,first.addManual(row.copy(played=false,lastPlayed=null),row).events)
    }
    @Test fun repeatedMarkWatchedIsIdempotent(){val row=trackingEntity(movie,null,true,1000);val first=TrackerState().addManual(row,null);assertEquals(1,first.addManual(row,row).events.size)}
    @Test fun repeatViewingHasSeparateEventAndCountsInStats(){
        val rows=listOf(WatchEvent("a",movie,1000,"manual","local"),WatchEvent("b",movie,2000,"manual","local")).map{it.journal()}
        assertEquals(2,individualWatches(rows).size);assertEquals(2,viewingInsights(rows).movies);assertEquals(180,viewingInsights(rows).knownMinutes)
    }
    @Test fun jellyfinSnapshotDoesNotCreateRepeatedViewings(){
        val row=JournalEntry(movie,1000,source="Jellyfin",scope="a")
        val state=TrackerState().observeRemote(listOf(row),"a")
        assertEquals(1,state.observeRemote(listOf(row),"a").events.size)
    }
    @Test fun newKnownLastPlayedDateCreatesOneNewObservation(){
        val row=JournalEntry(movie,1000,source="Jellyfin",scope="a")
        assertEquals(2,TrackerState().observeRemote(listOf(row),"a").observeRemote(listOf(row.copy(watchedAt=100000)),"a").events.size)
    }
    @Test fun remoteUnwatchPreservesHistoryAndUpdatesState(){val row=JournalEntry(movie,1000,scope="a");val state=TrackerState().observeRemote(listOf(row),"a").observeRemote(listOf(row.copy(played=false)),"a");assertEquals(1,state.events.size);assertFalse(state.remote.single().played)}
    @Test fun remoteAbsenceDoesNotEraseHistory(){val state=TrackerState().observeRemote(listOf(JournalEntry(movie,1000,scope="a")),"a");assertEquals(state,state.observeRemote(emptyList(),"a"))}
    @Test fun wrongAccountIsNotImported(){assertTrue(TrackerState().observeRemote(listOf(JournalEntry(movie,1000,scope="b")),"a").events.isEmpty())}
    @Test fun serverEchoOfManualWatchIsNotDoubleCounted(){val state=TrackerState(events=listOf(WatchEvent("manual",movie,1000,"manual","a")));assertEquals(1,state.observeRemote(listOf(JournalEntry(movie,1500,scope="a")),"a").events.size)}
    @Test fun suppressedRemoteViewingStaysRemoved(){val row=JournalEntry(movie,1000,scope="a");val state=TrackerState().observeRemote(listOf(row),"a");val deleted=state.copy(events=emptyList(),suppressed=listOf(state.events.single().id));assertTrue(deleted.observeRemote(listOf(row),"a").events.isEmpty())}
    @Test fun invalidJellyfinFieldsBecomeSupportedQueryParameters(){
        val q=jellyfinQuery(mapOf("Fields" to "ProviderIds,UserData,RunTimeTicks,Overview,PresentationUniqueKey"))
        assertEquals("ProviderIds,Overview",q["Fields"]);assertEquals("true",q["EnableUserData"])
    }
    @Test fun explicitUserDataPreferenceIsNotOverridden(){assertEquals("false",jellyfinQuery(mapOf("Fields" to "UserData","EnableUserData" to "false"))["EnableUserData"])}
    @Test fun diagnosticDoesNotLeakRawExceptionSecrets(){val text=jellyfinFailureMessage(java.io.IOException("https://host/?api_key=secret"));assertFalse(text.contains("secret"));assertTrue(jellyfinFailureMessage(JellyfinHttpFailure(401)).contains("Reconnect"));assertTrue(jellyfinFailureMessage(SocketTimeoutException()).contains("VPN"))}
}
