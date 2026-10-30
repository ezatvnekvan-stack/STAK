package app.stak.nativeapp.data

import app.stak.nativeapp.performance.*
import app.stak.nativeapp.ui.components.artworkTint
import org.junit.Assert.*
import org.junit.Test

class SmoothPerformanceTest {
    private fun mode(id:Int,hz:Float,w:Int=1080,h:Int=2400)=DisplayChoice(id,w,h,hz)
    @Test fun exactNinetyIsPreferredAtCurrentResolution(){assertEquals(2,preferredSmoothMode(listOf(mode(1,60f),mode(2,90f),mode(3,120f)),1080,2400)?.id)}
    @Test fun higherModeIsUsedWhenNinetyIsUnavailable(){assertEquals(3,preferredSmoothMode(listOf(mode(1,60f),mode(3,120f)),1080,2400)?.id)}
    @Test fun displayResolutionIsNeverChangedToChaseRefresh(){assertEquals(1,preferredSmoothMode(listOf(mode(1,60f),mode(2,90f,720,1600)),1080,2400)?.id)}
    @Test fun noSupportedResolutionAndInvalidRatesAreSafe(){assertNull(preferredSmoothMode(listOf(mode(1,Float.NaN),mode(2,0f)),1080,2400));assertNull(preferredSmoothMode(listOf(mode(1,90f)),720,1600))}
    @Test fun warmArtworkKeepsAWarmPalette(){val tint=artworkTint(IntArray(100){0xffd07523.toInt()});assertTrue(((tint shr 16)and 255)>(tint and 255)*2)}
    @Test fun coolArtworkKeepsACoolPalette(){val tint=artworkTint(IntArray(100){0xff205cbe.toInt()});assertTrue((tint and 255)>((tint shr 16)and 255)*2)}
    @Test fun blackWhiteAndTransparentPixelsDoNotOverwhelmArtworkColor(){val pixels=IntArray(1000){when{it<450->0xff000000.toInt();it<900->0xffffffff.toInt();it<950->0x00ddaa55;else->0xffb6422a.toInt()}};val tint=artworkTint(pixels);assertTrue(((tint shr 16)and 255)>(tint and 255)*2)}
    @Test fun missingColorUsesNeutralFallback(){assertEquals(0xff566273.toInt(),artworkTint(intArrayOf(0xff000000.toInt(),0xffffffff.toInt(),0x00ffaa00)));assertEquals(0xff566273.toInt(),artworkTint(intArrayOf()))}
}
