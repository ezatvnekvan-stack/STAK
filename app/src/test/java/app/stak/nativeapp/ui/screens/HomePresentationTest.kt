package app.stak.nativeapp.ui.screens

import app.stak.nativeapp.data.HomeSection
import app.stak.nativeapp.data.MediaItem
import org.junit.Assert.*
import org.junit.Test

class HomePresentationTest {
    @Test fun nextUpInformationIsOneCompactBottomLine(){assertEquals("NEXT UP · S01 E01",continueInformation(MediaItem("episode",name="Show",type="series",nextUp=true,season=1,episode=1)))}
    @Test fun resumeIncludesEpisodeAndRemainingTimeWithoutInventingADuration(){
        assertEquals("IN PROGRESS · S02 E03 · 20 min left",continueInformation(MediaItem("episode",name="Show",type="series",season=2,episode=3,durationMs=30*60000L,positionMs=10*60000L)))
        assertEquals("IN PROGRESS",continueInformation(MediaItem("movie",name="Film")))
    }
    private val movies=HomeSection("anticipated_movie","Movies")
    private val series=HomeSection("anticipated_series","Series","series")
    @Test fun adjacentAnticipatedRowsShareOneBlock(){assertEquals(movies.id,anticipatedPairStart(listOf(movies,series)))}
    @Test fun reverseCustomOrderKeepsTheBlockAtItsFirstSection(){assertEquals(series.id,anticipatedPairStart(listOf(series,movies)))}
    @Test fun separatedCustomSectionsAreNeverMovedTogether(){assertNull(anticipatedPairStart(listOf(movies,HomeSection("watchlist","Watchlist"),series)))}
    @Test fun aDisabledSectionIsNeverReintroducedByGrouping(){assertNull(anticipatedPairStart(listOf(movies,series.copy(enabled=false))))}
}
