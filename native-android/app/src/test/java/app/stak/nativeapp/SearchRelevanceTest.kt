package app.stak.nativeapp

import org.junit.Assert.*
import org.junit.Test

class SearchRelevanceTest {
    private fun title(name: String, year: String = "", id: String = "tt1") =
        Media(id, "movie", name, year = year)

    @Test
    fun originalSearchPrioritizesExactNumericTitlesOverYearInterpretation() {
        assertTrue(
            searchRelevance(title("Blade Runner 2049", "2017"), "Blade Runner 2049") <
                searchRelevance(title("Blade Runner", "2049"), "Blade Runner 2049")
        )
        assertTrue(
            searchRelevance(title("Class of 1984", "1982"), "Class of 1984") <
                searchRelevance(title("Class of", "1984"), "Class of 1984")
        )
    }

    @Test
    fun accentsImdbIdentityAndYearRetainOriginalOrdering() {
        assertTrue(
            searchRelevance(title("Amélie"), "amelie") <
                searchRelevance(title("Amelie returns"), "amelie")
        )
        assertTrue(
            searchRelevance(title("The Matrix", id = "tt0133093"), "tt0133093") <
                searchRelevance(title("A different title"), "tt0133093")
        )
        assertTrue(
            searchRelevance(title("Dune", "2021"), "Dune 2021") <
                searchRelevance(title("Dune", "1984"), "Dune 2021")
        )
    }
}
