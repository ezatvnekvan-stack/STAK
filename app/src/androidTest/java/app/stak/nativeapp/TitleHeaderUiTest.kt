package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import org.junit.*
import java.io.File

class TitleHeaderUiTest{
 @get:Rule val compose=createComposeRule()
 @Composable private fun Frame(content:@Composable ()->Unit){StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(StakBackground).statusBarsPadding()){content()}}}}
 private fun capture(name:String){compose.waitForIdle();Thread.sleep(200);val inst=InstrumentationRegistry.getInstrumentation();val folder=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:inst.targetContext.getExternalFilesDir(null)!!.path,"header132").apply{mkdirs()};File(folder,"$name.png").outputStream().use{inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}}
 private fun movie()=MediaItem("tt0468569",name="The Dark Knight",year="2008",runtime="152 min",genres=listOf("Action","Thriller","Crime"))
 @Test fun movieMetadataAndScoreAreCenteredAndActionsKeepTwelveDpGap(){
  var trailers=0;var saves=0;val film=movie()
  compose.setContent{Frame{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(22.dp)){
   TitleMetadata(film,TitleDetails(film,certification="PG-13",imdbRating=9.1))
   DetailActionRow(film.id,false,false,true,{}, {trailers++},{saves++})
  }}}
  compose.onNodeWithText("2008 · 152 min").assertIsDisplayed();compose.onNodeWithText("PG-13").assertIsDisplayed();compose.onNodeWithText("Action · Thriller · Crime").assertIsDisplayed()
  compose.onNodeWithContentDescription("IMDb 9.1").assertIsDisplayed();compose.onNodeWithText("/ 10").assertDoesNotExist();compose.onNodeWithText("/10").assertDoesNotExist()
  val facts=compose.onNodeWithTag("title-facts").getUnclippedBoundsInRoot();val rating=compose.onNodeWithTag("title-rating-slot").getUnclippedBoundsInRoot();Assert.assertTrue(rating.top>=facts.bottom);Assert.assertEquals((facts.left.value+facts.right.value)/2f,(rating.left.value+rating.right.value)/2f,1f)
  val trailer=compose.onNodeWithContentDescription("Trailer").getUnclippedBoundsInRoot();val save=compose.onNodeWithContentDescription("Add to watchlist").getUnclippedBoundsInRoot();Assert.assertTrue("Trailer and watchlist need a visible gap",(save.left-trailer.right).value>=11.5f)
  val dot=compose.onNodeWithTag("imdb-accent-dot",useUnmergedTree=true).getUnclippedBoundsInRoot();Assert.assertEquals(3f,(dot.right-dot.left).value,.5f)
  compose.onNodeWithContentDescription("Trailer").performClick();compose.onNodeWithContentDescription("Add to watchlist").performClick();Assert.assertEquals(1,trailers);Assert.assertEquals(1,saves);capture("movie-metadata-actions")
 }
 @Test fun seriesUsesItsRuntimeAndCertificationInTheSameMetadataDesign(){
  val show=movie().copy(id="tt1234567",type="series",name="Series fixture",year="2024",runtime="57 min",genres=listOf("Drama","Mystery"))
  compose.setContent{Frame{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(22.dp)){
   TitleMetadata(show,TitleDetails(show,certification="TV-MA",imdbRating=8.3,seasons=listOf(1,2)))
   DetailActionRow(show.id,true,false,true,{},{},{})
  }}}
  compose.onNodeWithText("2024 · 57 min").assertIsDisplayed();compose.onNodeWithText("TV-MA").assertIsDisplayed();compose.onNodeWithContentDescription("IMDb 8.3").assertIsDisplayed();compose.onNodeWithText("2 seasons").assertDoesNotExist();capture("series-metadata-actions")
 }
 @Test fun narrowLargeTextKeepsButtonsSeparateAndTheScoreReadable(){
  val film=movie();var trailers=0;var saves=0
  compose.setContent{Frame{val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,1.4f)){
   Column(Modifier.width(304.dp).padding(12.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
    TitleMetadata(film,TitleDetails(film,certification="PG-13",imdbRating=9.1))
    DetailActionRow(film.id,false,false,true,{}, {trailers++},{saves++})
   }
  }}}
  val source=compose.onNodeWithTag("detail-source-button").getUnclippedBoundsInRoot();val trailer=compose.onNodeWithContentDescription("Trailer").getUnclippedBoundsInRoot();val save=compose.onNodeWithContentDescription("Add to watchlist").getUnclippedBoundsInRoot()
  Assert.assertTrue((trailer.top-source.bottom).value>=11.5f);Assert.assertTrue((save.left-trailer.right).value>=11.5f)
  compose.onNodeWithContentDescription("IMDb 9.1").assertIsDisplayed();compose.onNodeWithContentDescription("Trailer").performClick();compose.onNodeWithContentDescription("Add to watchlist").performClick();Assert.assertEquals(1,trailers);Assert.assertEquals(1,saves);capture("narrow-large-type")
 }
 @Test fun unknownRatingDoesNotInventAScoreOrDisplayTheAccent(){
  val film=movie();compose.setContent{Frame{Box(Modifier.padding(16.dp)){TitleMetadata(film,TitleDetails(film,imdbRating=0.0,tmdbRating=8.5))}}}
  compose.onNodeWithText("IMDb rating unavailable").assertIsDisplayed();compose.onNodeWithTag("imdb-rating").assertDoesNotExist();compose.onNodeWithTag("imdb-accent-dot",useUnmergedTree=true).assertDoesNotExist();compose.onNodeWithText("8.5").assertDoesNotExist()
 }
}
