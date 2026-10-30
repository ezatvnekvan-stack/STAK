package app.stak.nativeapp.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The matrix precomputes its per-dot wave bias and vertical envelope once per
 * size. These tests pin the factored form to the original per-dot formula so the
 * optimisation cannot drift the visible pattern.
 */
class DotMatrixTest {

    @Test
    fun factoredOpacityMatchesTheOriginalFormula() {
        for (xi in 0..12) for (yi in 0..24) for (pi in 0..10) {
            val x = xi / 12f
            val y = yi / 24f
            val phase = pi / 10f
            val expected = dotMatrixOpacity(x, y, phase)
            val actual = dotWaveAlpha(x * .28f + y * .72f, dotFade(y), phase)
            assertEquals("x=$x y=$y phase=$phase", expected, actual, 0f)
        }
    }

    @Test
    fun opacityStaysInsideTheDocumentedRange() {
        for (yi in 0..40) for (pi in 0..20) {
            val alpha = dotMatrixOpacity(.37f, yi / 40f, pi / 20f)
            assertTrue("alpha=$alpha", alpha in 0f..DOT_MAX_ALPHA)
        }
    }

    @Test
    fun theMatrixIsInvisibleAboveAndBelowTheArtwork() {
        assertEquals(0f, dotMatrixOpacity(.5f, .1f, .2f), 0f)
        assertEquals(0f, dotMatrixOpacity(.5f, 1f, .2f), 0f)
        assertTrue(dotMatrixOpacity(.5f, .45f, .2f) > 0f)
    }

    @Test
    fun theWaveWrapsSmoothly() {
        // A dot on the crest keeps its brightness when the phase wraps around.
        val before = dotWaveAlpha(.5f, 1f, .999f)
        val after = dotWaveAlpha(.5f, 1f, .001f)
        assertEquals(before, after, .02f)
    }
}
