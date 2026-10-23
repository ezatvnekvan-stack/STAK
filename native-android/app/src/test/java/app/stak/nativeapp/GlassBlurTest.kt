package app.stak.nativeapp

import org.junit.Assert.*
import org.junit.Test

class GlassBlurTest {
    @Test fun uniformArtworkKeepsItsColorAlphaAndEdges() {
        val pixels = IntArray(15) { 0x80446688.toInt() }
        assertArrayEquals(pixels, blurGlassPixels(pixels, 5, 3, 8))
    }
    @Test fun brightDetailSpreadsSymmetricallyWithoutWrappingRows() {
        val pixels = IntArray(81) { 0xff000000.toInt() }.apply { this[40] = -1 }
        val result = blurGlassPixels(pixels, 9, 9, 1)
        assertTrue((result[40] and 255) in 1..254)
        assertEquals(result[39], result[41])
        assertEquals(result[31], result[49])
        assertEquals(0xff000000.toInt(), result[0])
        assertTrue(result.all { it ushr 24 == 255 })
    }
}
