package app.stak.nativeapp
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import org.junit.*
import java.io.File

class CachedSourcesUiTest {
 @get:Rule val compose=createComposeRule();private var previous:SettingsStore?=null;private lateinit var prefix:String
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 private val movie=MediaItem("tt1234567",name="Test film",imdbId="tt1234567")
 private fun hash(n:Int)=n.toString(16).padStart(40,'0')
 @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="cached137-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{torboxKey="synthetic-cached-only";reducedMotion=true}}
 @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
 @Composable private fun Frame(vm:TitleViewModel){StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(StakBackground)){FindSourcesSheet(movie,emptyList(),vm,{},{},{})}}}}
 private fun capture(name:String){val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:context.getExternalFilesDir(null)!!.path,"cached-1.3.7");dir.mkdirs();File(dir,"$name.png").outputStream().use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
 @Test fun onlyConfirmedSourcesAreShownAndUnverifiedEscapeIsGone(){
  val good=SourceRelease("Confirmed 2160p REMUX",resolution="2160p",infoHash=hash(1),remux=true,indexer="TorrentLeech")
  val vm=TitleViewModel(sourceLoader={_,_,_->listOf(good,SourceRelease("Not cached",infoHash=hash(2)),SourceRelease("Playback URL",streamUrl="https://example.invalid/movie.mp4",infoHash=null))},cacheLoader={_,_->setOf(hash(1))})
  compose.setContent{Frame(vm)};compose.waitUntil(5000){!vm.cacheLoading.value&&vm.cached.value.isNotEmpty()}
  Assert.assertEquals(listOf(good),vm.sources.value);compose.onNodeWithContentDescription("Expand sources").performClick()
  compose.onNodeWithText("Confirmed 2160p REMUX").assertExists();compose.onNodeWithText("Not cached").assertDoesNotExist();compose.onNodeWithText("SHOW UNVERIFIED RELEASES").assertDoesNotExist();capture("cached-only-results")
 }
 @Test fun failureNeverEnablesAddingAnUnverifiedSource(){
  var adds=0;val source=SourceRelease("Unverified",infoHash=hash(1));val vm=TitleViewModel(sourceLoader={_,_,_->listOf(source)},cacheLoader={_,_->throw java.io.IOException("offline")},addLoader={_,_->adds++;"1"})
  compose.setContent{Frame(vm)};compose.waitUntil(5000){vm.cacheError.value!=null};compose.runOnIdle{vm.addSource(source)};Assert.assertEquals(0,adds);Assert.assertTrue(vm.sources.value.isEmpty());capture("cache-failure-closed")
 }
 @Test fun nextMetadataBatchIsExplicitAndRetainsEarlierResults(){
  var fetched=0
  val sources=(1..10).map{SourceRelease("Private release $it",torznabIndexerId="tl",torrentFileUrl="https://indexer.invalid/$it.torrent",sourceToken="$it",infoHash=null)}
  val vm=TitleViewModel(sourceLoader={_,_,_->sources},cacheLoader={h,_->h.toSet()},materialLoader={s,_->fetched++;val h=hash(s.sourceToken!!.toInt());PreparedSource(s.copy(infoHash=h),NabMaterial.Magnet(parseMagnetInput(h)))})
  compose.setContent{Frame(vm)};compose.waitUntil(5000){!vm.cacheLoading.value&&vm.sources.value.size==8};Assert.assertEquals(8,fetched);Assert.assertEquals(2,vm.remainingSources.value)
  compose.onNodeWithContentDescription("Expand sources").performClick();compose.onNodeWithText("CHECK NEXT 2 RELEASES").performScrollTo().performClick();compose.waitUntil(5000){vm.sources.value.size==10};Assert.assertEquals(10,fetched);Assert.assertEquals(0,vm.remainingSources.value);capture("metadata-more-complete")
 }
 @Test fun accountChangeBlocksOldCachedButtons(){
  var adds=0;val source=SourceRelease("Cached",infoHash=hash(1));val vm=TitleViewModel(sourceLoader={_,_,_->listOf(source)},cacheLoader={h,_->h.toSet()},addLoader={_,_->adds++;"1"})
  compose.setContent{Frame(vm)};compose.waitUntil(5000){vm.cached.value.isNotEmpty()};compose.runOnIdle{NativeRuntime.settings.torboxKey="other-account";vm.addSource(source)};Assert.assertEquals(0,adds)
 }
 @Test fun torznabHeaderAndFailedMetadataCheckStayAccurate(){
  StreamAddonStore(NativeRuntime.settings).save(emptyList());TorznabStore(NativeRuntime.settings).save(listOf(TorznabIndexer(name="TorrentLeech",url="https://indexer.invalid/api",apiKey="synthetic")))
  val source=SourceRelease("Private",infoHash=null,torznabIndexerId="tl",torrentFileUrl="https://indexer.invalid/one.torrent")
  val vm=TitleViewModel(sourceLoader={_,_,_->listOf(source)},cacheLoader={_,_->throw java.io.IOException("offline")},materialLoader={s,_->val h=hash(1);PreparedSource(s.copy(infoHash=h),NabMaterial.Magnet(parseMagnetInput(h)))})
  compose.setContent{Frame(vm)};compose.waitUntil(5000){vm.cacheError.value!=null};Assert.assertEquals(0,vm.remainingSources.value);compose.onNodeWithText("TorrentLeech").assertExists();compose.onNodeWithText("No sources enabled").assertDoesNotExist();compose.onNodeWithText("CHECK NEXT 1 RELEASE").assertDoesNotExist()
 }
}
