package app.stak.nativeapp.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class WatchingModelsTest {
    private val today=LocalDate.of(2026,10,29)
    private val show=MediaItem("show",type="series",name="Series")
    private val movie=MediaItem("movie",name="Movie")
    private fun p(status:String="plan",seen:Boolean=false,complete:Boolean=false):Pair<SeriesProgress,List<JournalEntry>>{
        val states=if(seen)listOf(JournalEntry(show.copy(season=1,episode=1),1,updatedAt=1))else emptyList()
        val eps=listOf(Episode(1,1,"One",released="2026-10-27"))+if(complete)emptyList()else listOf(Episode(1,2,"Two",released="2026-10-28"))
        return seriesProgress(SeriesPlan(show,status,eps,completeMetadata=true),states,today) to states
    }
    @Test fun unstartedWatchlistDoesNotFillWatching(){val (p,s)=p();assertTrue(watchingRows(listOf(p),s,emptyList(),emptyList()).isEmpty())}
    @Test fun startedSeriesShowsFirstMissingEpisode(){val (p,s)=p(seen=true);assertEquals(2,watchingRows(listOf(p),s,emptyList(),emptyList()).single().episode?.episode)}
    @Test fun pausedAndDroppedSeriesAreExcluded(){listOf("on_hold","dropped").forEach{status->val(p,s)=p(status,true);assertTrue(watchingRows(listOf(p),s,emptyList(),emptyList()).isEmpty())}}
    @Test fun completedSeriesDoesNotCrowdWatching(){val(p,s)=p("watching",true,true);assertTrue(watchingRows(listOf(p),s,emptyList(),emptyList()).isEmpty())}
    @Test fun explicitSeriesCanBeFollowedBeforeFirstWatch(){val(p,s)=p("watching");assertEquals(1,watchingRows(listOf(p),s,emptyList(),emptyList()).size)}
    @Test fun movieResumeHasOnlyRealPlaybackData(){val m=movie.copy(positionMs=1_800_000,durationMs=5_400_000);val row=watchingRows(emptyList(),emptyList(),listOf(m),emptyList()).single();assertEquals("60 min left · 33%",movieResumeLabel(row.resume))}
    @Test fun manualMovieGetsNoInventedPosition(){val row=watchingRows(emptyList(),emptyList(),emptyList(),listOf(WatchingPin(movie.copy(progress=.8f)))).single();assertNull(row.resume);assertEquals("Watching",movieResumeLabel(row.resume))}
    @Test fun watchedMovieIsRemovedDespiteStaleResume(){val state=JournalEntry(movie,10,updatedAt=10);assertTrue(watchingRows(emptyList(),listOf(state),listOf(movie.copy(positionMs=1)),emptyList()).isEmpty())}
    @Test fun manualRewatchCanStartAfterAnEarlierViewing(){val state=JournalEntry(movie,10,updatedAt=10);assertEquals(1,watchingRows(emptyList(),listOf(state),emptyList(),listOf(WatchingPin(movie,updatedAt=20))).size)}
    @Test fun explicitRemovalHidesResumeWithoutChangingServerProgress(){val resume=movie.copy(positionMs=1000);assertTrue(watchingRows(emptyList(),emptyList(),listOf(resume),listOf(WatchingPin(movie,false))).isEmpty());assertEquals(1000,resume.positionMs)}
    @Test fun manualAndJellyfinMovieAliasesDeduplicate(){val m=movie.copy(imdbId="tt1234567");val resume=m.copy(id="jellyfin:file",jellyfinId="file",positionMs=1000);assertEquals(1,watchingRows(emptyList(),emptyList(),listOf(resume),listOf(WatchingPin(m))).size)}
    @Test fun realSeriesResumeChoosesTheResumedEpisode(){val(p,s)=p(seen=true);val resume=show.copy(season=1,episode=4,positionMs=1000);assertEquals(4,watchingRows(listOf(p),s,listOf(resume),emptyList()).single().episode?.episode)}
    @Test fun monthSummaryUsesCalendarMonthInsteadOfLastThirtyDays(){
        fun row(id:String,date:LocalDate)=JournalEntry(movie.copy(id=id),date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        assertEquals(1,filterJournal(listOf(row("old",LocalDate.of(2026,9,30)),row("new",LocalDate.of(2026,10,1))),JournalPreferences(period="this_month"),today).size)
    }
    @Test fun finishingExplicitlyAddedSeriesAlsoClearsWatching(){val(p,s)=p("watching",true,true);assertTrue(watchingRows(listOf(p),s,emptyList(),listOf(WatchingPin(show))).isEmpty())}
    @Test fun actualResumedRewatchCanAppearForACompletedSeries(){val(p,s)=p("watching",true,true);val resume=show.copy(season=1,episode=1,positionMs=1000);assertEquals(1,watchingRows(listOf(p),s,listOf(resume),emptyList()).size)}
}
