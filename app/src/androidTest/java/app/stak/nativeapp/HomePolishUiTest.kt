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
    @Test fun comingUpUsesLoadedLogoAndGlassBadgeWithoutCornerDate(){
        val today=LocalDate.now();val title=MediaItem("fixture",name="Nova",background=image(),logo=image(true))
        compose.setContent{Frame{ComingUpCard(ReleaseEvent(title,today.plusDays(6),"New chapter",1,7),today){}}}
        compose.waitUntil(5000){runCatching{val p=compose.onNodeWithTag("coming-up-logo",useUnmergedTree=true).captureToImage().toPixelMap();var count=0;for(y in 0 until p.height step 2)for(x in 0 until p.width step 2)if(p[x,y].red>.8f&&p[x,y].green>.8f)count++;count>80}.getOrDefault(false)}
        compose.onNodeWithText("Nova").assertDoesNotExist();compose.onNodeWithTag("coming-up-red-dot",useUnmergedTree=true).assertExists();compose.onNodeWithText("IN 6 DAYS").assertIsDisplayed()
        compose.onNodeWithText(today.plusDays(6).format(DateTimeFormatter.ofPattern("MMM d",Locale.ENGLISH))).assertDoesNotExist();capture("coming-up-logo-glass")
    }
    @Test fun failedLogoKeepsTitleReadable(){val title=MediaItem("fixture",name="Fallback title",logo="file:///definitely-missing-stak-logo.png");compose.setContent{Frame{ComingUpCard(ReleaseEvent(title,LocalDate.now(),"Film"),LocalDate.now()){}}};compose.waitUntil(5000){compose.onAllNodesWithText("Fallback title").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText("Fallback title").assertIsDisplayed();capture("coming-up-logo-fallback")}
    @Test fun rankStraddlesPosterEdgeAndFirstAndTenthAreNotClipped(){
        val art=image()
        compose.setContent{Frame{LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){item{StakPosterCard(MediaItem("one",name="First",poster=art),{},Modifier.width(144.dp).testTag("card-one"),rank=1)};item{StakPosterCard(MediaItem("ten",name="Tenth",poster=art),{},Modifier.width(144.dp),rank=10)}}}}
        compose.waitForIdle();val rank=compose.onNodeWithContentDescription("Rank 1",useUnmergedTree=true).getUnclippedBoundsInRoot();val poster=compose.onNodeWithTag("ranked-artwork-one",useUnmergedTree=true).getUnclippedBoundsInRoot();val card=compose.onNodeWithTag("card-one").getUnclippedBoundsInRoot()
        Assert.assertTrue(rank.left<poster.left);Assert.assertTrue(rank.right>poster.left);Assert.assertTrue(rank.left>=card.left);compose.onNodeWithContentDescription("Rank 10",useUnmergedTree=true).assertIsDisplayed();capture("outside-poster-ranks")
    }
    @Test fun countdownGlassBlursItsSourceAndKeepsRedDot(){
        var tint by mutableStateOf(Color.White)
        compose.setContent{Frame{
            val layer=rememberGraphicsLayer();val view=LocalView.current;val source=remember(layer){GlassSource(layer,hazeState=HazeState())}
            Box(Modifier.fillMaxSize()){
                Box(Modifier.fillMaxSize().recordGlassSource(source,view).drawBehind{drawRect(Color.Black);var x=0f;while(x<size.width){drawRect(tint,androidx.compose.ui.geometry.Offset(x,0f),androidx.compose.ui.geometry.Size(4.dp.toPx(),size.height));x+=8.dp.toPx()}})
                ComingUpCountdown("In 6 days",source)
            }
        }}
        compose.waitForIdle();val white=compose.onNodeWithTag("coming-up-countdown").captureToImage().asAndroidBitmap();val y=white.height-6;val values=(12 until white.width-12).map{android.graphics.Color.red(white.getPixel(it,y))/255f};Assert.assertTrue("Glass must blur stripes",values.max()-values.min()<.16f);Assert.assertTrue(values.average()>.02)
        compose.runOnIdle{tint=Color.Blue};compose.waitForIdle();val blue=compose.onNodeWithTag("coming-up-countdown").captureToImage().asAndroidBitmap();val pixel=blue.getPixel(blue.width/2,blue.height-6);Assert.assertTrue(android.graphics.Color.blue(pixel)>android.graphics.Color.red(pixel)*2);compose.onNodeWithTag("coming-up-red-dot",useUnmergedTree=true).assertExists();capture("countdown-real-blur")
    }
    @Test fun twoUpcomingCardsFitFullyAndTheRailScrollsToMore(){
        val today=LocalDate.now();val events=(1..3).map{number->ReleaseEvent(MediaItem("card-$number",name="Title $number"),today.plusDays(number.toLong()),"Film",releaseLabel="US · Digital")}
        var opened=""
        compose.setContent{Frame{ComingUpShelf(events,today,false,null,{}){opened=it.id}}}
        val rail=compose.onNodeWithTag("coming-up-rail").getUnclippedBoundsInRoot()
        val first=compose.onNodeWithTag("coming-up-item-${events[0].key}").getUnclippedBoundsInRoot()
        val second=compose.onNodeWithTag("coming-up-item-${events[1].key}").getUnclippedBoundsInRoot()
        Assert.assertTrue(first.left>=rail.left);Assert.assertTrue(second.right<=rail.right+1.dp);Assert.assertTrue(first.right<second.left)
        compose.onNodeWithText("Title 1").assertIsDisplayed();compose.onNodeWithText("Title 2").assertIsDisplayed();capture("two-coming-up-cards")
        compose.onNodeWithText("Title 2").performClick();Assert.assertEquals("card-2",opened)
        compose.onNodeWithTag("coming-up-rail").performTouchInput{swipeLeft()}
        compose.onNodeWithText("Title 3").assertIsDisplayed()
    }
}
