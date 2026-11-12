@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import org.junit.*
import java.io.File
import java.time.LocalDate

/** Local images and explicit state only: never connects to an owner's services. */
class Home159UiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefix:String
    private var previous:SettingsStore?=null
    private val files=mutableListOf<File>()
    private val today=LocalDate.of(2026,11,12)
    @Before fun setup(){
        previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="home159-${java.util.UUID.randomUUID()}-"
        NativeRuntime.settings=SettingsStore(context,prefix).apply{reducedMotion=true}
    }
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)};files.forEach{it.delete()}}
    private fun art():String {
        val b=Bitmap.createBitmap(900,1200,Bitmap.Config.ARGB_8888)
        val c=android.graphics.Canvas(b);val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.shader=android.graphics.LinearGradient(0f,0f,900f,1200f,0xff297c9a.toInt(),0xffa86732.toInt(),android.graphics.Shader.TileMode.CLAMP);c.drawRect(0f,0f,900f,1200f,p)
        p.shader=null;p.color=0x70d2e7e9;c.drawCircle(650f,300f,170f,p)
        p.color=0xff172a34.toInt();c.drawOval(375f,400f,580f,620f,p);c.drawRoundRect(300f,580f,660f,1300f,100f,100f,p)
        val f=File(context.cacheDir,"$prefix${files.size}.png");f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle();files+=f;return f.toURI().toString()
    }
    private fun logo():String {
        val b=Bitmap.createBitmap(900,160,Bitmap.Config.ARGB_8888)
        val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.WHITE;textSize=120f;typeface=android.graphics.Typeface.create("sans-serif-condensed",android.graphics.Typeface.BOLD)}
        android.graphics.Canvas(b).drawText("NIGHT SIGNAL",0f,130f,p)
        val f=File(context.cacheDir,"$prefix-logo.png");f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle();files+=f;return f.absolutePath
    }
    private fun capture(name:String){
        compose.waitForIdle()
        val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:context.getExternalFilesDir(null)!!.path,"home1510").apply{mkdirs()}
        File(dir,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Composable private fun Frame(large:Boolean=false,content:@Composable ()->Unit){
        val density=LocalDensity.current
        StakTheme{CompositionLocalProvider(LocalReducedMotion provides true,LocalDensity provides Density(density.density,if(large)1.6f else 1f)){
            Box(Modifier.fillMaxSize().background(StakBackground).statusBarsPadding()){content()}
        }}
    }
    @Test fun heroHasNoTrendingHeadingAndFlowsIntoContinueWatching(){
        val image=art();val film=MediaItem("fixture",name="Night Signal",background=image,year="2026",runtime="112 min",genres=listOf("Drama"))
        var opened=""
        compose.setContent{Frame{LazyColumn(Modifier.fillMaxSize().testTag("fixture-feed")){
            item{HeroCarousel(listOf(film),{opened=it.id},null,null,true,autoPlay=false)}
            item{Column(Modifier.padding(horizontal=16.dp).padding(top=6.dp)){StakSectionHeader("Continue watching");Spacer(Modifier.height(12.dp));ContinueRail(listOf(film.copy(progress=.4f,durationMs=100*60000L,positionMs=40*60000L)),artworkLoader={ContinueArtwork(image)}){}}}
        }}}
        compose.onNodeWithText("TRENDING TODAY").assertDoesNotExist()
        compose.onNodeWithText("01 / 06").assertDoesNotExist()
        val hero=compose.onNodeWithTag("home-hero").getUnclippedBoundsInRoot()
        val section=compose.onNodeWithText("CONTINUE WATCHING").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("hero-trending-label",useUnmergedTree=true).assertDoesNotExist()
        Assert.assertTrue(section.top-hero.bottom<24.dp)
        capture("hero-and-continue")
        compose.onNodeWithTag("home-hero").performClick();Assert.assertEquals(film.id,opened)
    }
    @Test fun compactContinueUsesSmallRealLogoAndBottomStatus(){
        val image=art();val row=MediaItem("fixture",name="The Leftovers",type="series",background=image,logo=logo(),nextUp=true,season=1,episode=1)
        compose.setContent{Frame{Column(Modifier.padding(16.dp)){ContinueRail(listOf(row),artworkLoader={ContinueArtwork(image,row.logo)}){}}}}
        compose.onNodeWithText("NEXT UP · S01 E01").assertIsDisplayed()
        compose.onNodeWithText("NEXT UP",substring=false).assertDoesNotExist()
        compose.onNodeWithTag("continue-logo",useUnmergedTree=true).assertIsDisplayed()
        compose.waitUntil(5000){runCatching{
            val pixels=compose.onNodeWithTag("continue-logo",useUnmergedTree=true).captureToImage().toPixelMap()
            var bright=0
            for(y in 0 until pixels.height step 2)for(x in 0 until pixels.width step 2)
                if(pixels[x,y].red>.8f&&pixels[x,y].green>.8f&&pixels[x,y].blue>.8f)bright++
            bright>40
        }.getOrDefault(false)}
        compose.onNodeWithText("The Leftovers").assertDoesNotExist()
        val card=compose.onNodeWithTag("continue-card").getUnclippedBoundsInRoot()
        val mark=compose.onNodeWithTag("continue-logo",useUnmergedTree=true).getUnclippedBoundsInRoot()
        val info=compose.onNodeWithTag("continue-info",useUnmergedTree=true).getUnclippedBoundsInRoot()
        Assert.assertTrue(mark.right-mark.left<(card.right-card.left)*.7f);Assert.assertTrue(mark.bottom-mark.top<=24.dp);Assert.assertTrue(info.top>card.top+(card.bottom-card.top)*.55f)
        capture("continue-small-logo")
    }
    @Test fun upcomingDateAndLongEpisodeRemainReadableAtLargeFont(){
        val event=ReleaseEvent(MediaItem("fixture",name="A Longer Returning Series",background=art()),today,"The Courier Returns",1,4)
        compose.setContent{Frame(large=true){Column(Modifier.padding(16.dp)){ComingUpShelf(listOf(event),today,false,null,{}){}}}}
        compose.onNodeWithText("TODAY").assertIsDisplayed();compose.onNodeWithText("12 NOV").assertIsDisplayed()
        compose.onNodeWithText("S01 E04").assertIsDisplayed();compose.onNodeWithText("The Courier Returns").assertIsDisplayed()
        val card=compose.onNodeWithTag("coming-up-card").getUnclippedBoundsInRoot()
        val title=compose.onNodeWithText("The Courier Returns",useUnmergedTree=true).getUnclippedBoundsInRoot()
        val date=compose.onNodeWithTag("coming-up-date",useUnmergedTree=true).getUnclippedBoundsInRoot()
        val rail=compose.onNodeWithTag("coming-up-rail").getUnclippedBoundsInRoot()
        Assert.assertTrue(date.bottom<card.top);Assert.assertTrue(card.right-card.left<(rail.right-rail.left)*.8f);Assert.assertTrue(title.bottom<card.bottom);capture("coming-up-large-font")
    }
}
