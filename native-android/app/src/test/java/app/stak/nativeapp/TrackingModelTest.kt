package app.stak.nativeapp

import org.junit.Assert.*
import org.junit.Test

class TrackingModelTest {
    @Test
    fun runtimeNeverInventsLengthsForMissingMetadata() {
        val movie = Media("tt0133093", "movie", "Test title")
        val unknown = viewingSummary(listOf(movie), listOf(null))
        assertEquals("—", unknown.runtimeValue)
        assertEquals("Runtime unknown", unknown.runtimeLabel)
        val known = viewingSummary(listOf(movie.copy(runtime = "1h 45m")), listOf(null))
        assertEquals(1.75, known.hours, 0.0001)
        assertEquals("Known runtime", known.runtimeLabel)
        assertEquals("0h", viewingSummary(emptyList(), emptyList()).runtimeValue)
        assertNull(runtimeHours("Not available"))
    }
}
