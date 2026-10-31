package app.stak.nativeapp

import org.junit.Assert.*
import org.junit.Test

class ArtworkPaletteTest {
    @Test
    fun matchesOriginalPaletteRulesAndFallback() {
        assertEquals(0xff6c7078.toInt(), paletteFromPixels(intArrayOf()))
        assertEquals(GRAPHITE_TONE, paletteFromPixels(intArrayOf(0xffffffff.toInt(), 0x00326414)))
        val green =
            paletteFromPixels(
                intArrayOf(0xff14b44b.toInt(), 0xff19af46.toInt(), 0xffe61e14.toInt())
            )
        val r = green ushr 16 and 255
        val g = green ushr 8 and 255
        val b = green and 255
        assertTrue(g > r && g > b && g <= 145)
    }

    @Test
    fun sameFixtureArtworkProducesOriginalRgb() {
        // The exact dominant RGB returned by the JS algorithm for #386a59.
        assertEquals(0xff426257.toInt(), paletteFromPixels(IntArray(768) { 0xff386a59.toInt() }))
    }
}
