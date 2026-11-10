@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.StakTheme
import kotlinx.coroutines.delay
import org.junit.*
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** Local fixtures only: no real provider, account, torrent or watched mutation. */
class RemakeUiTest{
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null
    private lateinit var prefix:String
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun before(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="remake-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{reducedMotion=true;ratingsEnabled=false}}
    @After fun after(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)};context.cacheDir.listFiles().orEmpty().filter{it.name.startsWith(prefix)}.forEach{it.delete()}}
    @Composable private fun Frame(content:@Composable ()->Unit){StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(Color(0xff090a0b)).statusBarsPadding().testTag("root")){content()}}}}
    private fun image(name:String,color:Int):String{
        val f=File(context.cacheDir,"$prefix$name.png");val b=Bitmap.createBitmap(960,600,Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(b);canvas.drawColor(color);val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color=0x44ffffff;canvas.drawCircle(710f,180f,160f,p);p.color=0x66000000;canvas.drawRect(0f,320f,960f,600f,p)
        p.color=android.graphics.Color.WHITE;p.textSize=54f;canvas.drawText(name,40f,130f,p)
        f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};return f.toURI().toString()
    }
    private fun media(id:String,name:String,type:String="movie"):MediaItem{
        val art=image(name,if(type=="series")0xff39405d.toInt()else 0xff244c53.toInt())
        return MediaItem(id,imdbId=id,name=name,type=type,year="2024",runtime="117 min",poster=art,background=art,genres=listOf("Mystery","Drama"),overview="A carefully planned journey changes when an unexpected discovery brings two strangers together. ".repeat(4))
    }
    private fun capture(name:String){
        compose.waitForIdle();Thread.sleep(200)
        val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:context.getExternalFilesDir(null)!!.path,"remake-ui").apply{mkdirs()}
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let{b->File(dir,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
    }
    @Test fun discoveryShowsAnEditorialPageAndCollectionsWithoutTopLevelListTabs(){
        val film=media("tt2000001","After the rain");val other=media("tt2000002","Another evening")
        val deck=DiscoverDeck(listOf(DiscoverShelf("top",listOf(film),false),DiscoverShelf("trending",listOf(other,film),false),DiscoverShelf("hidden",listOf(film,other),false)))
        val list=PublicTraktList(1,"A quiet weekend","Fixture curator",2)
        var opened:String?=null;var genre:String?=null;var collection=false
        compose.setContent{Frame{DiscoverExplore(deck,ListsState(lists=listOf(list)),mapOf(1L to listOf(film,other)),listOf(DiscoveryOption("mystery","Mystery")),{item,_->opened=item.id},{},{genre=it.id},{collection=true},{collection=true},{},{},{},{})}}
        compose.onNodeWithText("FOR TONIGHT").assertIsDisplayed();compose.onNodeWithText("TITLES").assertDoesNotExist()
        compose.onNodeWithTag("discover-feature").performClick();Assert.assertEquals(film.id,opened)
        compose.onNodeWithText("Mystery",ignoreCase=true).performClick();Assert.assertEquals("mystery",genre)
        capture("discover-top")
        compose.onNodeWithTag("discover-explore").performScrollToNode(hasText("EXPLORE LISTS"))
        compose.onNodeWithText("A quiet weekend").assertIsDisplayed();capture("discover-collections")
        compose.onNodeWithText("A quiet weekend").performClick();Assert.assertTrue(collection)
    }
    @Test fun continueCardsStayConsistentAndRemainBelowTheViewport(){
        val short=media("tt2000003","Up").copy(nextUp=true,season=1,episode=2)
        val long=media("tt2000004","A very long film title that requires two lines").copy(progress=.42f)
        var clicked:String?=null
        compose.setContent{Frame{Column(Modifier.fillMaxWidth().padding(16.dp)){ContinueRail(listOf(short,long)){clicked=it.id}}}}
        val cards=compose.onAllNodesWithTag("continue-card")
        val first=cards[0].getUnclippedBoundsInRoot();val second=cards[1].getUnclippedBoundsInRoot()
        Assert.assertEquals((first.right-first.left).value,(second.right-second.left).value,.5f)
        val viewport=compose.onNodeWithTag("root").getUnclippedBoundsInRoot()
        Assert.assertTrue(second.right-second.left<viewport.right-viewport.left)
        cards[0].performClick();Assert.assertEquals(short.id,clicked);capture("continue-dynamic")
    }
    @Test fun movieActionsAreOneRowAndSourceSheetStillWorks(){
        val film=media("tt2000005","The last light");var saves=0;var watchedWrites=0
        lateinit var vm:TitleViewModel
        compose.runOnUiThread{vm=TitleViewModel(detailsLoader={_,_->TitleDetails(film,tmdbRating=8.2,certification="R")},sourceLoader={_,_,_->emptyList()})}
        compose.setContent{Frame{TitleScreen(film,{},false,false,{saves++},null,null,onMarkWatched={_,_->watchedWrites++},viewModel=vm)}}
        compose.waitUntil(5000){vm.detail.value?.tmdbRating!=null}
        compose.onNodeWithTag("detail-source-button").getUnclippedBoundsInRoot().let{Assert.assertTrue(it.right-it.left<=216.dp)}
        val source=compose.onNodeWithTag("detail-source-button").fetchSemanticsNode().boundsInRoot
        val save=compose.onNodeWithContentDescription("Add to watchlist").fetchSemanticsNode().boundsInRoot
        Assert.assertTrue(kotlin.math.abs(source.top-save.top)<2f)
        compose.onNodeWithContentDescription("Add to watchlist").performClick();Assert.assertEquals(1,saves)
        compose.onNodeWithText("Auto-add").assertDoesNotExist();compose.onNodeWithText("SYNOPSIS").assertDoesNotExist()
        Assert.assertEquals(0,watchedWrites);capture("movie-top")
        compose.onNodeWithTag("detail-source-button").performClick()
        compose.waitUntil(5000){compose.onAllNodesWithText("Configure TorBox",ignoreCase=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Configure TorBox",ignoreCase=true).assertIsDisplayed()
    }
    @Test fun seriesEpisodesAreVerticalAndSeasonSelectionRemainsReadOnly(){
        val show=media("tt2000006","Beyond the city","series")
        val episodes=listOf(Episode(1,1,"The arrival",image=show.background,runtime="48 min",watched=true),Episode(1,2,"A different path",image=show.background,runtime="52 min"),Episode(2,1,"A new beginning",image=show.background,runtime="50 min"))
        var writes=0;lateinit var vm:TitleViewModel
        compose.runOnUiThread{vm=TitleViewModel(detailsLoader={_,_->TitleDetails(show,seasons=listOf(1,2),episodes=episodes)},sourceLoader={_,_,_->emptyList()})}
        compose.setContent{Frame{TitleScreen(show,{},false,false,{},null,null,onMarkEpisode={_,_,_->writes++},onMarkSeason={_,_->writes++},viewModel=vm)}}
        compose.waitUntil(5000){vm.detail.value?.episodes?.size==3}
        compose.onNodeWithContentDescription("Auto-add settings").assertIsDisplayed();capture("series-top")
        compose.onNodeWithTag("detail-content").performScrollToNode(hasTestTag("detail-episode-1-2"))
        val first=compose.onNodeWithTag("detail-episode-1-1").fetchSemanticsNode().boundsInRoot
        val second=compose.onNodeWithTag("detail-episode-1-2").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("E02 · NEXT UP").assertExists()
        Assert.assertTrue(second.top>=first.bottom);Assert.assertEquals(first.left,second.left,1f);capture("series-episodes")
        compose.onNodeWithContentDescription("Choose season").performScrollTo().performClick()
        compose.onNodeWithText("Season 2").performClick()
        compose.onNodeWithText("A new beginning").assertExists();Assert.assertEquals(2,vm.season.value)
        compose.onNodeWithText("MARK WATCHED").assertDoesNotExist();Assert.assertEquals(0,writes)
    }
    @Test fun creditsAreSeparateFromTheMainPage(){
        val film=media("tt2000007","Small hours")
        val credits=listOf(TitlePerson("one","Alex Director","Director"),TitlePerson("two","Sam Writer","Writer"),TitlePerson("three","Jo Producer","Producer"))
        lateinit var vm:TitleViewModel
        compose.runOnUiThread{vm=TitleViewModel(detailsLoader={_,_->TitleDetails(film,crew=credits,gallery=listOf(film.background!!),related=listOf(film.copy(id="related",imdbId=null,name="Related film")))},sourceLoader={_,_,_->emptyList()})}
        compose.setContent{Frame{TitleScreen(film,{},false,false,{},null,null,viewModel=vm)}}
        compose.waitUntil(5000){vm.detail.value?.crew?.size==3}
        compose.onNodeWithTag("detail-content").performScrollToNode(hasText("ALL CREDITS"))
        compose.onNodeWithText("Jo Producer").assertDoesNotExist();compose.onNodeWithText("ALL CREDITS").performClick()
        compose.onNodeWithText("Jo Producer").assertIsDisplayed();capture("all-credits")
        compose.onNodeWithText("CLOSE").performClick();compose.onNodeWithText("Jo Producer").assertDoesNotExist()
        compose.onNodeWithTag("detail-content").performScrollToNode(hasText("SIMILAR TITLES"));capture("movie-extras")
    }
    @Test fun discoveryDeckCancelsOldRequestsAndKeepsPartialSuccess(){
        val calls=AtomicInteger();lateinit var vm:DiscoverDeckViewModel
        compose.runOnUiThread{vm=DiscoverDeckViewModel{type,category,_->calls.incrementAndGet();delay(if(type=="movie")500 else 30);if(category=="hidden")throw IOException("fixture offline");DiscoverResult(items=listOf(MediaItem("$type-$category",name=type,type=type)))};vm.load("movie","US")}
        compose.runOnUiThread{vm.load("series","US")}
        compose.waitUntil(5000){vm.state.value.shelves.none{it.loading}}
        Assert.assertTrue(vm.state.value.shelves.filter{it.id!="hidden"}.all{it.items.single().type=="series"})
        Assert.assertNotNull(vm.state.value.shelves.first{it.id=="hidden"}.error)
        val before=calls.get();compose.runOnUiThread{vm.load("series","US")};compose.waitForIdle();Assert.assertEquals(before,calls.get())
    }

    @Test fun continueEpisodeSurvivesInitialSeasonRestoration(){
        val show=media("tt2000008","Episode restore","series").copy(season=1,episode=2)
        lateinit var vm:TitleViewModel
        compose.runOnUiThread{vm=TitleViewModel(detailsLoader={_,_->TitleDetails(show,seasons=listOf(1),episodes=listOf(Episode(1,1,"First"),Episode(1,2,"Continue here")))},sourceLoader={_,_,_->emptyList()})}
        compose.setContent{Frame{TitleScreen(show,{},false,false,{},null,null,viewModel=vm)}}
        compose.waitUntil(5000){vm.detail.value?.episodes?.size==2};compose.waitForIdle()
        Assert.assertEquals(2,vm.episode.value?.episode)
    }

    @Test fun zeroTmdbScoreIsNotPresentedAsARating(){
        val film=MediaItem("unrated",name="Upcoming film")
        var detail by mutableStateOf(TitleDetails(film,tmdbRating=0.0))
        compose.setContent{Frame{TitleScoreRow(detail)}}
        compose.onNodeWithText("TMDb 0.0 / 10").assertDoesNotExist()
        compose.runOnIdle{detail=detail.copy(imdbRating=8.2,tmdbRating=7.1)}
        compose.onNodeWithContentDescription("IMDb 8.2").assertIsDisplayed();compose.onNodeWithText("TMDb",substring=true).assertDoesNotExist()
    }
}
