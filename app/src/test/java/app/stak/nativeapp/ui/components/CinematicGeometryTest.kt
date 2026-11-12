package app.stak.nativeapp.ui.components
import org.junit.Assert.*
import org.junit.Test
class CinematicGeometryTest {
 @Test fun heroIsTenPercentTallerThanTheHalfScreenLayout(){
  for(height in listOf(640,800,900,1200))assertEquals(height*.55f,homeHeroHeight(height),.01f)
 }
 @Test fun largeTextAndLandscapeRemainBounded(){
  assertTrue(homeHeroHeight(900,1.6f)>homeHeroHeight(900))
  assertTrue(homeHeroHeight(320,2f)<=320*.7f)
  assertTrue(homeHeroHeight(900,2f)<=900*.7f)
 }
 @Test fun detailsAreTwentyPercentTallerWithTabletBounds(){
  assertEquals(460.8f,detailArtworkHeight(800),.1f)
  assertEquals(336f,detailArtworkHeight(320),.01f)
  assertEquals(576f,detailArtworkHeight(1600),.01f)
 }
 @Test fun continueRailShowsExactlyOneAndAHalfCardsAfterTheGap(){
  for(width in listOf(280f,360f,500f))assertEquals(width,continueCardWidth(width)*1.5f+12f,.01f)
 }
}
