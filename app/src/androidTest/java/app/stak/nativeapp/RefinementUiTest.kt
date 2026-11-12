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
import app.stak.nativeapp.ui.theme.*
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import org.junit.*
import java.io.File

class RefinementUiTest{
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null
    private lateinit var prefix:String
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun before(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="refine131-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{ratingsEnabled=false;reducedMotion=true;torboxKey="synthetic-only"}}
    @After fun after(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)};context.cacheDir.listFiles().orEmpty().filter{it.name.startsWith(prefix)}.forEach{it.delete()}}
    @Composable private fun Frame(content:@Composable ()->Unit){StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(Color(0xff090a0b)).statusBarsPadding().testTag("root")){content()}}}}
    private fun capture(name:String){compose.waitForIdle();Thread.sleep(180);val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:context.getExternalFilesDir(null)!!.path,"refine131-ui").apply{mkdirs()};InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let{b->File(dir,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}}
    private fun art(name:String,color:Int):String{val f=File(context.cacheDir,"$prefix$name.png");val b=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);b.eraseColor(color);val c=android.graphics.Canvas(b);val p=android.graphics.Paint().apply{this.color=android.graphics.Color.WHITE;textSize=74f};c.drawText(name,45f,280f,p);f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};return f.toURI().toString()}
    @Test fun trailerIsLabelledAndImdbIsTheOnlyRating(){
        var trailers=0
        compose.setContent{Frame{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
            TitleScoreRow(TitleDetails(MediaItem("film",name="Film"),imdbRating=8.4,tmdbRating=7.1,rottenTomatoes="99%",metacritic="97"))
            DetailActionRow("film",false,false,true,{}, {trailers++},{})
        }}}
        compose.onNodeWithText("TRAILER").assertIsDisplayed().performClick();Assert.assertEquals(1,trailers)
        compose.onNodeWithContentDescription("IMDb 8.4").assertIsDisplayed();compose.onNodeWithText("TMDb",substring=true).assertDoesNotExist();compose.onNodeWithContentDescription("RT 99%").assertDoesNotExist()
        capture("labelled-trailer-imdb")
    }
    @Test fun continueCardsAreEqualCompactLandscapesWithAlignedCopy(){
        val one=MediaItem("first",name="The Batman",background=art("BATMAN",0xff293a51.toInt()),durationMs=120*60000L,positionMs=30*60000L,progress=.25f)
        val two=MediaItem("second",name="The Leftovers",type="series",background=art("LEFTOVERS",0xff475544.toInt()),nextUp=true,season=1,episode=1)
        compose.setContent{Frame{Column(Modifier.fillMaxWidth().padding(16.dp)){ContinueRail(listOf(one,two),onOpenTitle={})}}}
        val nodes=compose.onAllNodesWithTag("continue-card")
        val a=nodes[0].getUnclippedBoundsInRoot();val b=nodes[1].getUnclippedBoundsInRoot()
        Assert.assertEquals((a.bottom-a.top).value,(b.bottom-b.top).value,.5f)
        val image=compose.onAllNodesWithTag("continue-image",useUnmergedTree=true)[0].getUnclippedBoundsInRoot()
        Assert.assertEquals(148f,(image.bottom-image.top).value,.5f)
        compose.onNodeWithText("IN PROGRESS · 90 min left").assertIsDisplayed();compose.onNodeWithText("25%").assertIsDisplayed();capture("continue-landscapes")
    }
    @Test fun bothFeaturedSurfacesRenderARealTitleLogo(){
        val film=MediaItem("tt1234567",imdbId="tt1234567",name="Northern lights",year="2024",logo=art("NORTHERN LIGHTS",0xff172233.toInt()),background=art("CINEMA",0xff233746.toInt()))
        compose.setContent{Frame{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            HomeSpotlight(film,{},null,null,"spot")
            Box(Modifier.weight(1f)){DiscoverExplore(DiscoverDeck(listOf(DiscoverShelf("top",listOf(film),false),DiscoverShelf("trending",loading=false),DiscoverShelf("hidden",loading=false))),ListsState(),emptyMap(),emptyList(),{_,_->},{},{},{},{},{},{},{},{})}
        }}}
        compose.onAllNodesWithTag("title-logo-tt1234567",useUnmergedTree=true).assertCountEquals(2);capture("spotlight-logo")
    }
    @Test fun sourceVerificationStartsBeforeOtherProvidersFinishAndReopenIsCached(){
        val gate=CompletableDeferred<Unit>();var searches=0;var checks=0;var prepared=0
        val hash="1".padStart(40,'0')
        val raw=SourceRelease("Private cached release",torznabIndexerId="fixture",torrentFileUrl="https://fixture.invalid/file",sourceToken="one")
        lateinit var vm:TitleViewModel
        compose.runOnUiThread{vm=TitleViewModel(progressiveSearch={_,partial->searches++;partial(AddonSearchResult(listOf(raw)));gate.await();AddonSearchResult(listOf(raw))},cacheLoader={h,_->checks++;h.toSet()},materialLoader={s,_->prepared++;PreparedSource(s.copy(infoHash=hash),NabMaterial.Magnet(parseMagnetInput(hash)))})
            vm.loadSources("movie","tt1234567")}
        compose.waitUntil(5000){vm.sources.value.isNotEmpty()}
        Assert.assertTrue(vm.sourceLoading.value);Assert.assertFalse(gate.isCompleted);Assert.assertEquals(1,prepared)
        gate.complete(Unit);compose.waitUntil(5000){!vm.cacheLoading.value&&!vm.sourceLoading.value}
        Assert.assertEquals(1,vm.sources.value.size)
        compose.runOnUiThread{vm.loadSources("movie","tt1234567")};compose.waitForIdle();Assert.assertEquals(1,searches)
        val before=checks
        compose.runOnUiThread{vm.loadSources("movie","tt1234567",force=true)}
        compose.waitUntil(5000){searches==2&&!vm.cacheLoading.value};Assert.assertTrue(checks>before)
    }
    @Test fun automaticMetadataBudgetStaysEightAcrossManyPartialBatches(){
        var prepared=0;lateinit var vm:TitleViewModel
        val rows=(1..20).map{n->SourceRelease("Release $n",torznabIndexerId="fixture",torrentFileUrl="https://fixture.invalid/$n",sourceToken="$n")}
        compose.runOnUiThread{vm=TitleViewModel(progressiveSearch={_,partial->rows.chunked(2).forEach{partial(AddonSearchResult(it));delay(5)};AddonSearchResult(rows)},cacheLoader={h,_->h.toSet()},materialLoader={s,_->prepared++;val h=s.sourceToken!!.padStart(40,'0');PreparedSource(s.copy(infoHash=h),NabMaterial.Magnet(parseMagnetInput(h)))})
            vm.loadSources("movie","tt1234567")}
        compose.waitUntil(5000){!vm.cacheLoading.value&&prepared>0};Assert.assertEquals(8,prepared);Assert.assertEquals(12,vm.remainingSources.value)
    }
    @Test fun imdbFallbackEnrichesWithoutAnOmdbKey()=runBlocking{
        NativeRuntime.settings.ratingsEnabled=true
        val repo=MediaRepository(imdbFallback=ImdbRating{_,_,_->JsonParser.parseString("""{"meta":{"id":"tt1234567","imdbRating":"8.4"}}""").asJsonObject})
        val result=repo.enrichRatings(TitleDetails(MediaItem("tt1234567",imdbId="tt1234567",name="Film"),tmdbRating=7.1))
        Assert.assertEquals(8.4,result.imdbRating!!,.001);Assert.assertNull(result.rottenTomatoes);Assert.assertNull(result.metacritic)
    }

    @Test fun homeBrandsTheFirstTrendingMovieAfterTheFeedArrives(){
        val film=MediaItem("tt1234567",name="Film",imdbId="tt1234567")
        lateinit var vm:HomeViewModel
        compose.runOnUiThread{vm=HomeViewModel(feedLoader={HomeFeed(rows=listOf(HomeRow("trending_movie","Trending",listOf(film))))},brandLoader={it.copy(logo="file:///fixture-logo.png")})}
        compose.waitUntil(5000){vm.feed.value?.rows?.firstOrNull()?.items?.firstOrNull()?.logo!=null}
        Assert.assertEquals("tt1234567",vm.feed.value!!.rows.first().items.first().mediaKey)
    }
}
