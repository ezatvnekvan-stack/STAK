package app.stak.nativeapp.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SeriesCenterTest{
    private val today=LocalDate.of(2026,11,4)
    private val show=MediaItem("jellyfin:one",imdbId="tt1234567",type="series",name="Fixture",jellyfinId="one",poster="poster")
    private fun rule()=WatchRuleModel("rule",seriesImdb="tt1234567",seriesName="Fixture",enabled=true)
    private fun episode(n:Int,date:String?="2026-11-01")=Episode(1,n,"Episode $n",released=date)
    private fun item(n:Int,height:Int=2160)=show.copy(jellyfinId="ep$n-$height",jellyfinSeriesId="one",season=1,episode=n,jellyfinVideoHeight=height,jellyfinHdr="HDR10")
    private fun watcher(n:Int,status:String)=WatchEpisode(1,n,status,"",0,0,"2026-11-01")
    @Test fun joinsLibraryAndRulesByIdentityAndKeepsLibraryOnlySeries(){
        val rows=seriesCenterEntries(listOf(show,show.copy(id="another",imdbId="tt7654321",name="Library only",jellyfinId="two")),listOf(rule()))
        assertEquals(2,rows.size);assertEquals("poster",rows.first{it.rule!=null}.media.poster);assertEquals("one",rows.first{it.rule!=null}.media.jellyfinId)
    }
    @Test fun similarlyNamedSeriesNeverMergeByName(){assertEquals(2,seriesCenterEntries(listOf(show),listOf(rule().copy(seriesImdb="tt9999999"))).size)}
    @Test fun ruleOnlySeriesAppearsWithoutEnablingAnyRule(){val rule=rule().copy(enabled=false);assertEquals(rule,seriesCenterEntries(emptyList(),listOf(rule)).single().rule)}
    @Test fun duplicateLibraryVersionsDoNotDuplicateTheSeries(){assertEquals(1,seriesCenterEntries(listOf(show,show.copy(id="version",jellyfinId="version")),emptyList()).size)}
    @Test fun libraryOverridesStaleWatcherAndKeepsMultipleQualities(){
        val rows=seriesCenterEpisodes(SeriesCenterSnapshot(listOf(episode(1)),listOf(item(1),item(1,1080)),true),listOf(watcher(1,"connection_unavailable")),today)
        assertEquals("available",rows.single().status);assertEquals(2,rows.single().versions.size);assertTrue(rows.single().quality.contains("4K"));assertTrue(rows.single().quality.contains("1080p"))
    }
    @Test fun missingRequiresSuccessfulLibraryVerification(){
        val snapshot=SeriesCenterSnapshot(listOf(episode(1)))
        assertEquals("unknown",seriesCenterEpisodes(snapshot,emptyList(),today).single().status)
        assertEquals("missing",seriesCenterEpisodes(snapshot.copy(libraryVerified=true),emptyList(),today).single().status)
    }
    @Test fun unknownReleaseNeverBecomesMissingOrUnreleased(){assertEquals("unknown_release",seriesCenterEpisodes(SeriesCenterSnapshot(listOf(episode(1,null)),libraryVerified=true),emptyList(),today).single().status)}
    @Test fun futureReleasesAreNotMissingAndDateDoesNotHideExistingMedia(){
        val snapshot=SeriesCenterSnapshot(listOf(episode(1,"2026-12-01")),libraryVerified=true)
        assertEquals("unreleased",seriesCenterEpisodes(snapshot,emptyList(),today).single().status)
        assertEquals("available",seriesCenterEpisodes(snapshot.copy(library=listOf(item(1))),emptyList(),today).single().status)
    }
    @Test fun watcherAvailabilityAloneIsReportedRatherThanClaimedAsVerified(){assertEquals("reported_available",seriesCenterEpisodes(SeriesCenterSnapshot(),listOf(watcher(1,"available_jellyfin")),today).single().status)}
    @Test fun preservesEpisodesKnownOnlyToWatcherOrLibraryAndSortsSeasons(){
        val rows=seriesCenterEpisodes(SeriesCenterSnapshot(listOf(Episode(2,1,"Season two")),listOf(item(3)),true),listOf(watcher(2,"waiting_torbox_cache")),today)
        assertEquals(listOf(1 to 2,1 to 3,2 to 1),rows.map{it.episode.season to it.episode.episode})
        assertEquals("waiting_torbox_cache",rows.first().status)
    }
}
