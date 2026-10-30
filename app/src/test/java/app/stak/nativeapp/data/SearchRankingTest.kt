package app.stak.nativeapp.data

import org.junit.Assert.*
import org.junit.Test

class SearchRankingTest {
    @Test fun identityResolutionRecognizesLegacySavedIdsWithoutMergingDifferentKinds() {
        val legacy=MediaItem("tmdb:42",name="Example")
        assertTrue(legacy.sameIdentity(legacy.copy(imdbId="tt1234567")))
        assertFalse(legacy.sameIdentity(legacy.copy(type="series")))
    }
    @Test fun exactTitleWinsOverSequelsAndPreservesProviderOrderForTies() {
        val entries=listOf(MediaItem("3",name="Dune: Part Two"),MediaItem("1",name="Dune",year="2021"),MediaItem("2",name="Dune",year="1984"))
        assertEquals(listOf("1","2","3"),SearchRanking.rank(entries,"Dune").map{it.id})
    }
    @Test fun accentsAndOriginalNamesMatch() {
        val entries=listOf(MediaItem("1",name="A distant result"),MediaItem("2",name="English title",aliases=listOf("Amélie")))
        assertEquals("2",SearchRanking.rank(entries,"Amelie").first().id)
    }
    @Test fun numericWordsRemainPartOfAnExactTitle() {
        val title=MediaItem("1",name="Blade Runner 2049",year="2017")
        assertEquals(-.5,SearchRanking.relevance(title,"Blade Runner 2049"),.001)
    }
    @Test fun imdbIdentifierWinsAndYearBreaksOtherwiseEqualMatches() {
        assertEquals(-1.0,SearchRanking.relevance(MediaItem("tt1234567",name="Example"),"tt1234567"),.001)
        val a=MediaItem("1",name="Dune",year="1984");val b=MediaItem("2",name="Dune",year="2021")
        assertEquals("2",SearchRanking.rank(listOf(a,b),"Dune 2021").first().id)
    }
}
