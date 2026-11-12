package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import dev.chrisbanes.haze.HazeState
import org.junit.*
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomePolishUiTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null;private lateinit var prefix:String
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val files=mutableListOf<File>()
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="polish124-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{reducedMotion=true}}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)};files.forEach{it.delete()}}
    @Composable private fun Frame(content:@Composable ()->Unit){StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(StakBackground).padding(16.dp)){content()}}}}
    private fun capture(name:String):Bitmap{compose.waitForIdle();val bitmap=compose.onRoot().captureToImage().asAndroidBitmap();val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: context.getExternalFilesDir(null)!!.path,"home-1.2.4");dir.mkdirs();File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};return bitmap}
    private fun image(logo:Boolean=false):String {
        val bitmap=Bitmap.createBitmap(if(logo)700 else 960,if(logo)160 else 540,Bitmap.Config.ARGB_8888);val canvas=android.graphics.Canvas(bitmap);val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        if(logo){paint.color=android.graphics.Color.WHITE;paint.textSize=130f;paint.typeface=android.graphics.Typeface.create("sans-serif-condensed",android.graphics.Typeface.BOLD);canvas.drawText("NOVA",0f,135f,paint)}else{paint.shader=android.graphics.LinearGradient(0f,0f,960f,540f,intArrayOf(0xff19475d.toInt(),0xffaf743e.toInt()),null,android.graphics.Shader.TileMode.CLAMP);canvas.drawRect(0f,0f,960f,540f,paint);paint.shader=null;paint.color=0x60FFFFFF;for(x in 0..960 step 42)canvas.drawRect(x.toFloat(),0f,x+14f,540f,paint)}
        val file=File(context.cacheDir,"$prefix-${files.size}.png");file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};files+=file;return file.absolutePath
    }
    @Test fun comingUpKeepsSmallLogoBesideTheDateBlock(){
        val today=LocalDate.now();val title=MediaItem("fixture",name="Nova",background=image(),logo=image(true))
        compose.setContent{Frame{ComingUpCard(ReleaseEvent(title,today.plusDays(6),"New chapter",1,7),today){}}}
        compose.waitUntil(5000){runCatching{val p=compose.onNodeWithTag("coming-up-logo",useUnmergedTree=true).captureToImage().toPixelMap();var count=0;for(y in 0 until p.height step 2)for(x in 0 until p.width step 2)if(p[x,y].red>.8f&&p[x,y].green>.8f)count++;count>80}.getOrDefault(false)}
        compose.onNodeWithText("Nova").assertDoesNotExist();compose.onNodeWithTag("coming-up-today-dot",useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithText(today.plusDays(6).format(DateTimeFormatter.ofPattern("dd MMM",Locale.ENGLISH)).uppercase(Locale.ENGLISH)).assertIsDisplayed()
        val date=compose.onNodeWithTag("coming-up-date",useUnmergedTree=true).getUnclippedBoundsInRoot()
        val logo=compose.onNodeWithTag("coming-up-logo",useUnmergedTree=true).getUnclippedBoundsInRoot()
        Assert.assertTrue(logo.left>date.right);Assert.assertTrue(logo.bottom-logo.top<=32.dp);capture("coming-up-date-logo")
    }
    @Test fun failedLogoKeepsTitleReadable(){val title=MediaItem("fixture",name="Fallback title",logo="file:///definitely-missing-stak-logo.png");compose.setContent{Frame{ComingUpCard(ReleaseEvent(title,LocalDate.now(),"Film"),LocalDate.now()){}}};compose.waitUntil(5000){compose.onAllNodesWithText("Fallback title").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText("Fallback title").assertIsDisplayed();capture("coming-up-logo-fallback")}
    @Test fun rankStraddlesPosterEdgeAndFirstAndTenthAreNotClipped(){
        val art=image()
        compose.setContent{Frame{LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){item{StakPosterCard(MediaItem("one",name="First",poster=art),{},Modifier.width(144.dp).testTag("card-one"),rank=1)};item{StakPosterCard(MediaItem("ten",name="Tenth",poster=art),{},Modifier.width(144.dp),rank=10)}}}}
        compose.waitForIdle();val rank=compose.onNodeWithContentDescription("Rank 1",useUnmergedTree=true).getUnclippedBoundsInRoot();val poster=compose.onNodeWithTag("ranked-artwork-one",useUnmergedTree=true).getUnclippedBoundsInRoot();val card=compose.onNodeWithTag("card-one").getUnclippedBoundsInRoot()
        Assert.assertTrue(rank.left<poster.left);Assert.assertTrue(rank.right>poster.left);Assert.assertTrue(rank.left>=card.left);compose.onNodeWithContentDescription("Rank 10",useUnmergedTree=true).assertIsDisplayed();capture("outside-poster-ranks")
    }
    @Test fun aSingleUpcomingCardFillsTheRailAndHasNoInventedAirtime(){
        val today=LocalDate.of(2026,11,12)
        val event=ReleaseEvent(MediaItem("one",name="Night Signal",background=image()),today,"The Courier",1,4)
        compose.setContent{Frame{ComingUpShelf(listOf(event),today,false,null,{}){}}}
        val rail=compose.onNodeWithTag("coming-up-rail").getUnclippedBoundsInRoot()
        val card=compose.onNodeWithTag("coming-up-card").getUnclippedBoundsInRoot()
        Assert.assertEquals((rail.right-rail.left).value,(card.right-card.left).value,.5f)
        compose.onNodeWithText("TODAY").assertIsDisplayed();compose.onNodeWithText("S01 E04").assertIsDisplayed()
        compose.onNodeWithText("The Courier").assertIsDisplayed();compose.onNodeWithText("21:00").assertDoesNotExist()
        compose.onNodeWithTag("coming-up-today-dot",useUnmergedTree=true).assertExists();capture("coming-up-single")
    }
    @Test fun wideUpcomingCardsPeekAndTheRailScrollsToMore(){
        val today=LocalDate.now();val events=(1..3).map{number->ReleaseEvent(MediaItem("card-$number",name="Title $number"),today.plusDays(number.toLong()),"Film",releaseLabel="US · Digital")}
        var opened=""
        compose.setContent{Frame{ComingUpShelf(events,today,false,null,{}){opened=it.id}}}
        val rail=compose.onNodeWithTag("coming-up-rail").getUnclippedBoundsInRoot()
        val first=compose.onNodeWithTag("coming-up-item-${events[0].key}").getUnclippedBoundsInRoot()
        val second=compose.onNodeWithTag("coming-up-item-${events[1].key}").getUnclippedBoundsInRoot()
        Assert.assertTrue(first.left>=rail.left);Assert.assertTrue(second.left<rail.right);Assert.assertTrue(second.right>rail.right);Assert.assertTrue(first.right<second.left)
        compose.onNodeWithText("Title 1").assertIsDisplayed();capture("coming-up-next-peek")
        compose.onNodeWithText("Title 1").performClick();Assert.assertEquals("card-1",opened)
        compose.onNodeWithTag("coming-up-rail").performTouchInput{swipeLeft()}
        compose.onNodeWithText("Title 2").assertIsDisplayed()
    }
}
