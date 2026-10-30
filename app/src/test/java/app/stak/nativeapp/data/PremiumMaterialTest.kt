package app.stak.nativeapp.data
import app.stak.nativeapp.ui.components.*
import org.junit.Assert.*
import org.junit.Test
class PremiumMaterialTest {
    @Test fun matrixOpacityStaysSubtleAndFadesAtEdges(){for(x in 0..10)for(y in 0..20)for(t in 0..10){val alpha=dotMatrixOpacity(x/10f,y/20f,t/10f);assertTrue(alpha in 0f..0.281f)};assertEquals(0f,dotMatrixOpacity(.5f,0f,.2f),0f);assertEquals(0f,dotMatrixOpacity(.5f,1f,.2f),0f)}
    @Test fun stoppedAndReducedClocksDoNotAdvance(){assertEquals(.35f,advanceDotPhase(.35f,1f,false),.0001f);assertEquals(.35f,advanceDotPhase(.35f,Float.NaN),.0001f)}
    @Test fun longFrameGapsAreClampedAndPhaseWraps(){assertTrue(advanceDotPhase(.5f,5f)<.504f);assertTrue(advanceDotPhase(.999f,.05f)<.01f);assertTrue(advanceDotPhase(Float.NaN,.016f).isFinite())}
    @Test fun secondaryTextHasContrastOverWhiteBehindGlass(){
        fun linear(v:Double)=if(v<=.04045)v/12.92 else Math.pow((v+.055)/1.055,2.4)
        val scrimAlpha=212.0/255;val sheenAlpha=5.0/255
        val background=(1-scrimAlpha+8.0/255*scrimAlpha)*(1-sheenAlpha)+sheenAlpha
        val ratio=(linear(160.0/255)+.05)/(linear(background)+.05)
        assertTrue("Small secondary text must remain legible over bright artwork",ratio>=4.5)
    }
}
