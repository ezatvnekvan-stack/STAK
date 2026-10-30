package app.stak.nativeapp.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the baked ambient softness: it replaced a per-frame screen blur. */
class BoxBlurTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) =
        ((a and 255) shl 24) or ((r and 255) shl 16) or ((g and 255) shl 8) or (b and 255)

    private fun channel(color: Int, shift: Int) = (color shr shift) and 255

    @Test
    fun uniformImageStaysUniform() {
        val pixels = IntArray(16 * 16) { argb(255, 12, 40, 90) }
        boxBlurArgb(pixels, 16, 16, radius = 3)
        assertTrue(pixels.all { it == argb(255, 12, 40, 90) })
    }

    @Test
    fun singleBrightPixelSpreadsAndKeepsEnergy() {
        val width = 15
        val height = 15
        val pixels = IntArray(width * height) { argb(255, 0, 0, 0) }
        pixels[7 * width + 7] = argb(255, 255, 255, 255)
        boxBlurArgb(pixels, width, height, radius = 2, passes = 1)

        val centre = pixels[7 * width + 7]
        val neighbour = pixels[7 * width + 8]
        assertTrue("centre should dim but stay brightest", channel(centre, 16) in 1..254)
        assertTrue("the value must bleed into neighbours", channel(neighbour, 16) > 0)
        assertTrue(channel(centre, 16) >= channel(neighbour, 16))

        val total = pixels.sumOf { channel(it, 16) }
        assertTrue("energy should be roughly preserved, was $total", total in 200..320)
    }

    @Test
    fun alphaIsBlurredToo() {
        val pixels = IntArray(9) { argb(0, 0, 0, 0) }
        pixels[4] = argb(255, 20, 20, 20)
        boxBlurArgb(pixels, 3, 3, radius = 1, passes = 1)
        assertTrue(pixels[4] != argb(0, 0, 0, 0))
        assertTrue("alpha must not saturate to 255", channel(pixels[0], 24) < 255)
    }

    @Test
    fun invalidInputIsIgnored() {
        val pixels = intArrayOf(1, 2, 3, 4)
        val copy = pixels.copyOf()
        boxBlurArgb(pixels, 0, 0, radius = 2)
        boxBlurArgb(pixels, 2, 2, radius = 0)
        boxBlurArgb(pixels, 100, 100, radius = 2)
        assertEquals(copy.toList(), pixels.toList())
    }

    @Test
    fun edgesClampInsteadOfWrapping() {
        // A bright left column must not darken the right edge through wrap-around.
        val width = 8
        val height = 4
        val pixels = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            pixels[y * width + x] = if (x == 0) argb(255, 255, 255, 255) else argb(255, 0, 0, 0)
        }
        boxBlurArgb(pixels, width, height, radius = 2, passes = 1)
        assertEquals(0, channel(pixels[0 * width + (width - 1)], 16))
    }
}
