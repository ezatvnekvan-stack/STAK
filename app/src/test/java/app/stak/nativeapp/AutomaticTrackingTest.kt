package app.stak.nativeapp
import app.stak.nativeapp.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AutomaticTrackingTest {
    private val movie=MediaItem("m",name="Movie",jellyfinId="jf",jellyfinUserDataKnown=true)
    private val scope="server-user"
    @Test fun manualRowsAndOtherUsersNeverBecomeServerState(){assertTrue(serverTrackingStates(scope,listOf(JournalEntry(movie,null,true,"manual",scope=scope),JournalEntry(movie,null,true,"Jellyfin",scope="other"))).isEmpty())}
    @Test fun newServerUnwatchBeatsAnOlderLibrarySnapshot(){val states=serverTrackingStates(scope,listOf(JournalEntry(movie,null,false,"Jellyfin",20,scope)),listOf(movie.copy(watched=true)),10);assertFalse(states.single().played)}
    @Test fun freshLibraryStateReplacesOldHistoryState(){val states=serverTrackingStates(scope,listOf(JournalEntry(movie,null,true,"Jellyfin",10,scope)),listOf(movie.copy(watched=false)),20);assertFalse(states.single().played)}
    @Test fun disconnectedProfilesNeverUseManualState(){assertTrue(serverTrackingStates("local",emptyList(),listOf(movie.copy(watched=true)),20).isEmpty())}
    @Test fun manualHistoryIsArchivedAndServerEchoesDeduplicate(){val stamp=100L;val event=WatchEvent("old",movie,stamp,"manual",scope);val observed=JournalEntry(movie,stamp,true,"Jellyfin",20,scope);assertEquals("Jellyfin",serverViewingHistory(scope,listOf(event),listOf(observed)).single().source);assertEquals(1,serverViewingHistory(scope,listOf(event.copy(id="jf-event",source="Jellyfin")),listOf(observed)).size)}
    @Test fun historicalViewingsSurviveCurrentUnwatchedState(){val event=WatchEvent("past",movie,100,"Jellyfin",scope);assertEquals(1,serverViewingHistory(scope,listOf(event),listOf(JournalEntry(movie,null,false,"Jellyfin",20,scope))).size)}
    @Test fun pinsAndOldPausedStatusDoNotHideRealResumes(){val show=movie.copy(id="show",jellyfinId="episode",jellyfinSeriesId="show-jf",type="series",season=1,episode=2,positionMs=60_000);val p=SeriesProgress(SeriesPlan(journalSeriesTitle(show),"dropped"),emptyList(),emptySet(),null,null,0,false,"dropped");assertEquals(1,automaticWatchingRows(listOf(p),emptyList(),listOf(show)).size)}
    @Test fun serverNextUpAppearsWithoutInventedPlaybackPosition(){val show=movie.copy(type="series",jellyfinSeriesId="show",season=1,episode=2,nextUp=true);val row=automaticWatchingRows(emptyList(),emptyList(),listOf(show)).single();assertNull(row.resume);assertEquals(2,row.episode?.episode)}
    @Test fun timelinesUseActualDatesIgnoreUnknownAndFuture(){val today=LocalDate.of(2026,10,29);val rows=listOf(JournalEntry(movie,today.atTime(12,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()),JournalEntry(movie,null),JournalEntry(movie,today.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()));assertEquals(7,trackingTimeline(rows,"week",today).size);assertEquals(1,trackingTimeline(rows,"week",today).sumOf{it.count});assertEquals(29,trackingTimeline(rows,"this_month",today).size);assertEquals(12,trackingTimeline(rows,"all",today).size)}
    @Test fun trackingWritesAreBlockedWithoutBlockingManagement(){listOf("POST","DELETE","PATCH").forEach{method->assertFalse(jellyfinTrackingMutationAllowed("/Users/u/PlayedItems/x",method));assertFalse(jellyfinTrackingMutationAllowed("/UserPlayedItems/x",method));assertFalse(jellyfinTrackingMutationAllowed("/Users/u/Items/x/UserData",method))};assertTrue(jellyfinTrackingMutationAllowed("/Users/u/Items/x","GET"));assertTrue(jellyfinTrackingMutationAllowed("/Library/Refresh","POST"));assertTrue(jellyfinTrackingMutationAllowed("/Sessions/s/Playing/Pause","POST"));assertTrue(jellyfinTrackingMutationAllowed("/Users/u/FavoriteItems/x","POST"))}
    @Test fun earlierManualPlanAloneDoesNotStartWatching(){val p=SeriesProgress(SeriesPlan(movie.copy(type="series"),"watching"),emptyList(),emptySet(),null,null,0,false,"watching");assertTrue(automaticWatchingRows(listOf(p),emptyList(),emptyList()).isEmpty())}
    @Test fun staleNextUpDisappearsAfterServerCompletion(){val item=movie.copy(type="series",season=1,episode=2,nextUp=true);assertTrue(automaticWatchingRows(emptyList(),listOf(JournalEntry(item,null,true,"Jellyfin",scope=scope)),listOf(item)).isEmpty())}

}
