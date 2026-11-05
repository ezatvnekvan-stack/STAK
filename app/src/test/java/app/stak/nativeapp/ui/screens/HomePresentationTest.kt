package app.stak.nativeapp.ui.screens

import app.stak.nativeapp.data.HomeSection
import org.junit.Assert.*
import org.junit.Test

class HomePresentationTest {
    private val movies=HomeSection("anticipated_movie","Movies")
    private val series=HomeSection("anticipated_series","Series","series")
    @Test fun adjacentAnticipatedRowsShareOneBlock(){assertEquals(movies.id,anticipatedPairStart(listOf(movies,series)))}
    @Test fun reverseCustomOrderKeepsTheBlockAtItsFirstSection(){assertEquals(series.id,anticipatedPairStart(listOf(series,movies)))}
    @Test fun separatedCustomSectionsAreNeverMovedTogether(){assertNull(anticipatedPairStart(listOf(movies,HomeSection("watchlist","Watchlist"),series)))}
    @Test fun aDisabledSectionIsNeverReintroducedByGrouping(){assertNull(anticipatedPairStart(listOf(movies,series.copy(enabled=false))))}
}
