@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.delay
import org.junit.*
import org.junit.Assert.*
import java.time.*
import java.io.File
class DiaryDiscover151UiTest{
 @get:Rule val compose=createComposeRule()
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 private val prefix="diary151-${java.util.UUID.randomUUID()}"
 private var previous:SettingsStore?=null
 @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();NativeRuntime.settings=SettingsStore(context,prefix).apply{reducedMotion=true;ratingsEnabled=false}}
 @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)};context.cacheDir.listFiles().orEmpty().filter{it.name.startsWith(prefix)}.forEach{it.delete()}}
 @Composable private fun Frame(content:@Composable ()->Unit){StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(Color(0xff090a0b)).statusBarsPadding()){content()}}}}
 private fun art(name:String):String{val f=File(context.cacheDir,"$prefix-$name.png");val b=Bitmap.createBitmap(600,400,Bitmap.Config.ARGB_8888);val c=android.graphics.Canvas(b);c.drawColor(0xff1c3741.toInt());val p=android.graphics.Paint(3);p.color=0xff4b6673.toInt();c.drawCircle(440f,90f,190f,p);p.color=android.graphics.Color.WHITE;p.textSize=38f;c.drawText(name,22f,310f,p);f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};return f.toURI().toString()}
 private fun media(id:String)=MediaItem(id,name=id,poster=art(id),background=art(id),year="2026",runtime="104 min",genres=listOf("Mystery"))
 private fun shot(name:String){compose.waitForIdle();val i=InstrumentationRegistry.getInstrumentation();val d=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:context.getExternalFilesDir(null)!!.absolutePath,"diary151");d.mkdirs();i.uiAutomation.takeScreenshot().let{b->File(d,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}}
 @Test fun monthPickerAndStatsAreSeparateActions(){
  val month=YearMonth.of(2026,11);var picked=month;var stats=0
  compose.setContent{Frame{ActivityMonthHeader(month,listOf(month,month.minusMonths(1)),ViewingInsights(movies=2,episodes=3,knownMinutes=180),{picked=it},{stats++})}}
  compose.onNodeWithContentDescription("View statistics for November 2026").performClick();assertEquals(1,stats)
  compose.onNodeWithText("NOVEMBER 2026",substring=true).performClick();compose.onNodeWithText("October 2026").performClick();assertEquals(month.minusMonths(1),picked);assertEquals(1,stats)
  shot("month-selector")
 }
 @Test fun diaryEpisodesExpandInPlaceAndOpenExactEpisode(){
  val show=media("Fixture series").copy(type="series",season=1,episode=1);var opened:MediaItem?=null
  val a=JournalEntry(show,1,source="Jellyfin");val b=a.copy(media=show.copy(episode=2))
  compose.setContent{Frame{Column(Modifier.padding(16.dp)){ActivityMonthHeader(YearMonth.of(2026,11),emptyList(),ViewingInsights(episodes=2,knownMinutes=100),{},{})
   DiaryHistoryRow(HistoryGroup("group",listOf(a,b)),onOpen={opened=it})
  }}}
  compose.onNodeWithText("Fixture series").performClick();compose.onNodeWithText("S01 E02").performClick();assertEquals(2,opened?.episode)
  shot("diary")
 }
 @Test fun discoverShowsGenresAndNoRepeatedHeroInShelves(){
  val hero=media("Tonight pick");val next=media("Trending pick");val other=media("Hidden pick");var genre=""
  val deck=DiscoverDeck(listOf(DiscoverShelf("top",listOf(hero),false),DiscoverShelf("trending",listOf(hero,next),false),DiscoverShelf("hidden",listOf(next,other),false)))
  compose.setContent{Frame{DiscoverExplore(deck,ListsState(),emptyMap(),listOf(DiscoveryOption("mystery","Mystery")),{_,_->},{},{genre=it.id},{},{},{},{},{},{})}}
  compose.onNodeWithText("Mystery").performClick();assertEquals("mystery",genre)
  compose.onAllNodesWithText("Tonight pick").assertCountEquals(1);compose.onNodeWithText("GENRES").assertExists()
  compose.onNodeWithText("BROWSE ALL TITLES").assertDoesNotExist();shot("discover-top")
 }
 @Test fun listBookmarkDoesNotOpenList(){
  var opens=0;var saves=0;val list=PublicTraktList(1,"Weekend picks","Fixture",count=12)
  compose.setContent{Frame{Box(Modifier.width(280.dp).padding(12.dp)){PublicListCard(list,listOf(media("One"),media("Two")),false,{opens++},{saves++})}}}
  compose.onNodeWithContentDescription("Save Weekend picks").performClick();assertEquals(1,saves);assertEquals(0,opens)
  compose.onNodeWithText("Weekend picks").performClick();assertEquals(1,opens);shot("list-card")
 }
 @Test fun providerRequestsAreScopedAndStaleResultsCannotOverwrite(){
  val calls=java.util.Collections.synchronizedList(mutableListOf<String>());lateinit var vm:DiscoverDeckViewModel
  compose.runOnUiThread{vm=DiscoverDeckViewModel(providerFetch={_,category,_,provider->calls.add("$provider:$category");delay(if(provider=="8")200 else 10);DiscoverResult(items=listOf(MediaItem("$provider-$category",name=provider)))},fetch={_,_,_->error("must not use global fallback")});vm.load("movie","US",provider="8")}
  compose.runOnUiThread{vm.load("movie","US",provider="9")}
  compose.waitUntil(5000){vm.state.value.shelves.all{!it.loading}}
  assertTrue(vm.state.value.shelves.all{it.items.single().name=="9"});assertTrue(calls.containsAll(listOf("9:top","9:trending","9:hidden")))
 }
}
