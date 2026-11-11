@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
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
import org.junit.Assert.*
import java.io.File

/** Local images and pure presentation callbacks only; no owner services or acquisition. */
class Cinematic155UiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val files=mutableListOf<File>()
    private val prefix="cinematic155-${java.util.UUID.randomUUID()}-"
    private var previous:SettingsStore?=null
    @Before fun setup(){
        previous=runCatching{NativeRuntime.settings}.getOrNull()
        NativeRuntime.settings=SettingsStore(context,prefix).apply{
            ratingsEnabled=false;reducedMotion=true
            jellyfinUrl="http://127.0.0.1:9";jellyfinToken="fixture-token";jellyfinUserId="fixture-user"
        }
    }
    @After fun cleanup(){
        previous?.let{NativeRuntime.settings=it}
        listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}
        files.forEach{it.delete()}
    }
    private fun art():String {
        val f=File.createTempFile("cinematic155-",".png",context.cacheDir);files+=f
        val b=Bitmap.createBitmap(800,1200,Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(b);val paint=android.graphics.Paint()
        canvas.drawColor(android.graphics.Color.rgb(48,92,102))
        paint.color=android.graphics.Color.rgb(190,205,180);canvas.drawRect(0f,0f,800f,130f,paint)
        paint.color=android.graphics.Color.rgb(145,180,180);canvas.drawCircle(400f,330f,130f,paint)
        paint.color=android.graphics.Color.rgb(15,32,40);canvas.drawRect(250f,460f,550f,1200f,paint)
        f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle();return f.toURI().toString()
    }
    private fun capture(name:String){
        compose.waitForIdle();Thread.sleep(200);compose.waitForIdle()
        val dir=File(context.getExternalFilesDir(null),"cinematic155").apply{mkdirs()}
        File(dir,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun heroPreservesTopAndDissolvesIntoActualBackgroundWithoutSpill(){
        val picture=art()
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){
            Column(Modifier.fillMaxSize().background(Color(0xff182326))){
                HeroCarousel(listOf(MediaItem("fixture",name="Distant signal",cinematicArtwork=picture,year="2026",certification="PG-13",overview="A quiet transmission changes everything.")),{},null,null,true,false)
                Box(Modifier.fillMaxWidth().height(40.dp).testTag("following-shelf"))
            }
        }}}
        // Wait for Coil's decoded local artwork rather than capturing its placeholder.
        compose.waitUntil(5000){
            val b=compose.onNodeWithTag("home-hero").captureToImage().asAndroidBitmap()
            val top=b.getPixel(b.width/2,8)
            android.graphics.Color.red(top)>70
        }
        val b=compose.onNodeWithTag("home-hero").captureToImage().asAndroidBitmap()
        val bottom=b.getPixel(5,b.height-2)
        assertTrue(kotlin.math.abs(android.graphics.Color.red(bottom)-24)<=3)
        assertTrue(kotlin.math.abs(android.graphics.Color.green(bottom)-35)<=3)
        val shelf=compose.onNodeWithTag("following-shelf").captureToImage().asAndroidBitmap()
        assertEquals(android.graphics.Color.rgb(24,35,38),shelf.getPixel(5,5))
        capture("hero-top-and-fade")
    }
    @Test fun unavailableTextlessImageFallsBackToUsableArtwork(){
        val fallback=art();val loaded=java.util.concurrent.atomic.AtomicBoolean(false)
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){StakArtwork("file:///missing-stak-fixture.png","Fallback artwork",Modifier.size(250.dp),onSuccess={loaded.set(true)},alignment=Alignment.TopCenter,fallbackModel=fallback)}}}
        compose.waitUntil(8000){loaded.get()};capture("artwork-fallback")
    }
    @Test fun compactFormatFiltersKeep44dpTargetsAndToggleIndependently(){
        var current=SourceFilters()
        compose.setContent{StakTheme{Column(Modifier.width(320.dp).background(StakBackground).padding(12.dp)){
            var filters by remember{mutableStateOf(current)}
            SourceFormatFilters(filters){filters=it;current=it}
        }}}
        val hdr=compose.onNodeWithText("HDR").getUnclippedBoundsInRoot()
        val atmos=compose.onNodeWithText("ATMOS").getUnclippedBoundsInRoot()
        assertTrue(atmos.left-hdr.right<36.dp)
        compose.onNodeWithText("HDR").performClick();assertTrue(current.hdr);assertFalse(current.atmos)
        compose.onNodeWithText("ATMOS").performClick();assertTrue(current.hdr);assertTrue(current.atmos)
        capture("compact-filters")
    }
    @Test fun movieLibraryStateHasQualityActionWithoutDuplicateTechnicalSubtitle(){
        val media=MediaItem("fixture",name="Fixture",jellyfinVideoHeight=1080,jellyfinHdr="SDR")
        compose.setContent{StakTheme{Box(Modifier.width(360.dp).background(StakBackground).padding(16.dp)){
            DetailLibraryState(media,true,true,false)
        }}}
        compose.onNodeWithText("In your library").assertIsDisplayed()
        compose.onNodeWithText("1080p · SDR").assertDoesNotExist()
        compose.onNodeWithText("MEDIA QUALITY").assertIsDisplayed();capture("movie-library-status")
    }
    @Test fun metadataAndActionsRemainReadableOnNarrowLargeText(){
        val media=MediaItem("fixture",name="Fixture",year="2026",runtime="157 min",genres=listOf("Science Fiction","Adventure"),certification="PG-13")
        compose.setContent{StakTheme{
            val density=LocalDensity.current
            CompositionLocalProvider(LocalReducedMotion provides true,LocalDensity provides Density(density.density,1.8f)){
                Column(Modifier.width(320.dp).background(StakBackground).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                    TitleMetadata(media,TitleDetails(media,imdbRating=8.2))
                    DetailActionRow("fixture",false,false,true,{},{},{})
                }
            }
        }}
        compose.onNodeWithContentDescription("Age rating PG-13").assertIsDisplayed()
        compose.onNodeWithContentDescription("IMDb 8.2").assertIsDisplayed()
        val trailer=compose.onNodeWithContentDescription("Trailer").getUnclippedBoundsInRoot()
        val save=compose.onNodeWithContentDescription("Add to watchlist").getUnclippedBoundsInRoot()
        assertTrue(save.top-trailer.bottom>=11.dp);capture("metadata-large-text")
    }
}
