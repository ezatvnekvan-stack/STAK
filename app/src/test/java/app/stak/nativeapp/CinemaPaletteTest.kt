package app.stak.nativeapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.stak.nativeapp.ui.components.cinemaPalette
import org.junit.Assert.*
import org.junit.Test

class CinemaPaletteTest {
    @Test fun palettesKeepReadableTextOnWarmAndCoolDarkSurfaces(){
        for(seed in listOf(Color.Red,Color.Blue,Color.Green,Color.White,Color.Black)){
            val p=cinemaPalette(seed)
            val contrast=(p.scheme.onSurface.luminance()+.05f)/(p.background.luminance()+.05f)
            assertTrue("Readable foreground contrast",contrast>=4.5f)
            assertTrue("Cinematic background must remain dark",p.background.luminance()<.025f)
        }
    }
    @Test fun distinctArtworkProducesDistinctSurfacesAndNearbySeedsReusePalette(){
        val warm=cinemaPalette(Color(0xFFCA642A));val cool=cinemaPalette(Color(0xFF2769CC))
        assertNotEquals(warm.surface,cool.surface)
        assertSame(cinemaPalette(Color(0xFFCA642A)),cinemaPalette(Color(0xFFCB652B)))
    }
}
