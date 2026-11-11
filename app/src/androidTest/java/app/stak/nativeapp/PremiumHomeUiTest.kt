@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.LocalReducedMotion
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.StakTheme
import org.junit.*
import java.io.File
import java.time.LocalDate

/** Isolated local artwork/catalog only; no owner accounts or acquisition calls. */
class PremiumHomeUiTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null
    private lateinit var prefix:String
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="premium-home-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{ratingsEnabled=false;traktClientId="fixture-client";reducedMotion=true}}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)};context.cacheDir.listFiles().orEmpty().filter{it.name.startsWith(prefix)}.forEach{it.delete()}}
    private fun picture(name:String,color:Int):String{
        val file=File(context.cacheDir,"$prefix$name.png");val bitmap=Bitmap.createBitmap(960,600,Bitmap.Config.ARGB_8888)
        val c=android.graphics.Canvas(bitmap);val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader=android.graphics.LinearGradient(0f,0f,960f,600f,color,android.graphics.Color.rgb(5,10,18),android.graphics.Shader.TileMode.CLAMP);c.drawRect(0f,0f,960f,600f,paint)
        paint.shader=null;paint.color=android.graphics.Color.argb(80,210,235,240);c.drawCircle(750f,190f,140f,paint)
        paint.color=android.graphics.Color.argb(80,1,8,15);c.drawRect(500f,230f,890f,600f,paint)
        paint.color=android.graphics.Color.WHITE;paint.textSize=62f;c.drawText(name.uppercase(),42f,150f,paint)
        file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle();return file.toURI().toString()
    }
    private fun title(id:String,name:String,type:String="movie",color:Int=android.graphics.Color.rgb(14,87,103)):MediaItem{
        val image=picture(id,color)
        return MediaItem(id,id,type,name,year="2026",runtime="112 min",overview="An isolated cinematic fixture.",poster=image,background=image,genres=listOf("Drama","Mystery"))
    }
    private fun capture(name:String){
        val base=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:context.getExternalFilesDir(null)!!.path
        val dir=File(base,"premium-home-ui").apply{mkdirs()}
        if(!compose.mainClock.autoAdvance)compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun heroDissolvesWrapsAndAlwaysOpensTheVisibleTitle(){
        val first=title("tt1000001","Northern lights");val second=title("tt1000002","After hours",color=android.graphics.Color.rgb(143,65,35))
        var opened:MediaItem?=null
        compose.mainClock.autoAdvance=false
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides false){Box(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().padding(16.dp)){HeroCarousel(listOf(first,second),{opened=it},null,null,false,autoPlay=false)}}}}
        compose.mainClock.advanceTimeBy(500);compose.waitForIdle();Thread.sleep(250)
        compose.onNodeWithTag("home-hero").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Featured 1 of 2"))
        compose.onNodeWithContentDescription("Show featured title 2").performClick()
        compose.mainClock.advanceTimeBy(320);capture("hero-dissolve-middle")
        compose.mainClock.advanceTimeBy(1400)
        compose.onNodeWithTag("home-hero").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Featured 2 of 2"))
        compose.onNodeWithText("VIEW DETAILS").assertDoesNotExist()
        compose.onNodeWithTag("home-hero").getUnclippedBoundsInRoot().let{
            Assert.assertEquals(context.resources.configuration.screenHeightDp*.5f,(it.bottom-it.top).value,2f)
        }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("After hours").assertIsDisplayed()
        opened=null;compose.onNodeWithTag("home-hero").performTouchInput{click(androidx.compose.ui.geometry.Offset(center.x,center.y*.40f))};Assert.assertEquals(second.mediaKey,opened?.mediaKey)
        capture("hero-second-settled")
        // The end duplicate normalizes to the first title without traversing all pages.
        compose.onNodeWithTag("home-hero").performTouchInput{swipeLeft()};compose.mainClock.advanceTimeBy(1800)
        compose.onNodeWithTag("home-hero").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Featured 1 of 2"))
        opened=null;compose.onNodeWithTag("home-hero").performTouchInput{click(androidx.compose.ui.geometry.Offset(center.x,center.y*.40f))};Assert.assertEquals(first.mediaKey,opened?.mediaKey)
    }
    @Test fun reducedMotionHeroDoesNotAutoAdvanceAndDotSelectionStillWorks(){
        val first=title("tt1000003","Quiet morning");val second=title("tt1000004","Nightfall")
        compose.mainClock.autoAdvance=false
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){HeroCarousel(listOf(first,second),{},null,null,true,autoPlay=true)}}}
        compose.mainClock.advanceTimeBy(30000)
        compose.onNodeWithTag("home-hero").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Featured 1 of 2"))
        compose.onNodeWithContentDescription("Show featured title 2").performClick();compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("home-hero").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Featured 2 of 2"))
    }
    @Test fun fullHomeHasNoHeaderAndCompactContinueAndPairedAnticipatedRows(){
        val film=title("tt1000010","Northern lights")
        val series=title("tt1000011","Echoes","series",android.graphics.Color.rgb(98,54,99))
        val films=(0..4).map{film.copy(id="tt100002$it",imdbId="tt100002$it",name="Film ${it+1}")}
        val feed=HomeFeed(hero=listOf(film,series),rows=listOf(
            HomeRow("trending_movie","Trending movies",films,provider="Trakt"),
            HomeRow("trending_series","Trending series",listOf(series),provider="Trakt"),
            HomeRow("anticipated_movie","Most anticipated movies",listOf(film.copy(name="Upcoming film")),provider="Trakt"),
            HomeRow("anticipated_series","Most anticipated series",listOf(series.copy(name="Upcoming series")),provider="Trakt")
        ))
        lateinit var vm:HomeViewModel;lateinit var search:LiveSearchViewModel
        compose.runOnUiThread{vm=HomeViewModel(feedLoader={feed},releaseLoader={_,date,_->ReleaseFeed(listOf(ReleaseEvent(series,date.plusDays(1),"Pilot",1,1,series.background)),emptyList(),1)});search=LiveSearchViewModel{emptyList()}}
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().statusBarsPadding()){HomeScreen(onOpenTitle={_,_->},onSearch={},onOpenAccount={},onOpenLibrary={},onOpenWatchlist={},onBrowseSection={},
            continueWatching=listOf(series.copy(nextUp=true,season=1,episode=2)),torboxConnected=true,jellyfinConnected=true,watchlist=listOf(film,series),
            sharedScope=null,animatedScope=null,reducedMotion=true,viewModel=vm,searchViewModel=search)}}}}
        compose.waitUntil(8000){compose.onAllNodesWithTag("home-hero").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Movie, series or IMDb ID").assertDoesNotExist()
        compose.onNodeWithTag("home-search-toggle").assertDoesNotExist()
        compose.waitForIdle();capture("home-top")
        compose.onNodeWithTag("home-feed").performScrollToNode(hasTestTag("continue-card"))
        val continueWidth=compose.onNodeWithTag("continue-card").fetchSemanticsNode().boundsInRoot.width
        Assert.assertTrue(continueWidth<compose.onRoot().fetchSemanticsNode().boundsInRoot.width*.80f)
        compose.onNodeWithTag("continue-card").getUnclippedBoundsInRoot().let{Assert.assertTrue(it.bottom-it.top<=190.dp)}
        capture("home-continue")
        compose.onNodeWithTag("home-feed").performScrollToNode(hasTestTag("home-spotlight"))
        compose.onNodeWithTag("home-spotlight").assertIsDisplayed();capture("home-trending")
        compose.onNodeWithTag("home-feed").performScrollToNode(hasText("MOST ANTICIPATED"))
        compose.onNodeWithText("Upcoming film").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("SERIES").performClick()
        compose.onNodeWithText("Upcoming series").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Trakt ↗").assertDoesNotExist();compose.onNodeWithText("JELLYFIN →").assertDoesNotExist()
        capture("home-anticipated")
    }
    @Test fun sourcesActionLeadsTheSecondaryRowAndOpensTheExistingSheet(){
        val movie=title("tt1000040","A quiet place")
        lateinit var vm:TitleViewModel
        compose.runOnUiThread{vm=TitleViewModel(detailsLoader={_,_->TitleDetails(movie)},sourceLoader={_,_,_->emptyList()})}
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){TitleScreen(movie,{},false,false,{},null,null,viewModel=vm)}}}
        compose.waitUntil(8000){vm.detail.value!=null}
        val action=compose.onNodeWithTag("detail-source-button").fetchSemanticsNode().boundsInRoot
        val secondary=compose.onNodeWithContentDescription("Add to watchlist").fetchSemanticsNode().boundsInRoot
        Assert.assertTrue(secondary.top-action.bottom>=11f)
        compose.onNodeWithTag("detail-source-dock").assertDoesNotExist();capture("details-primary-action")
        compose.onNodeWithTag("detail-source-button").performClick()
        compose.waitUntil(8000){compose.onAllNodesWithText("Configure TorBox",ignoreCase=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Configure TorBox",ignoreCase=true).assertExists()
    }
}
