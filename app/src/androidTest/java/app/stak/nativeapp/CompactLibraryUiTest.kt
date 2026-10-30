@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.StakTheme
import org.junit.*
import java.io.File

class CompactLibraryUiTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null
    private lateinit var prefix:String
    private val art=mutableListOf<File>()
    @Before fun setup(){
        previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="compact-library-${java.util.UUID.randomUUID()}-"
        NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix)
        NativeRuntime.settings.apply{jellyfinUrl="http://127.0.0.1:9";jellyfinToken="synthetic-only";jellyfinUserId="fixture";torboxKey="synthetic-only";hasOnboarded=true;reducedMotion=true}
    }
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)};art.forEach{it.delete()}}
    private fun poster(text:String,color:Int):String{
        val file=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"$prefix$text.png");art+=file
        val bitmap=Bitmap.createBitmap(240,360,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);canvas.drawColor(color)
        val paint=Paint().apply{this.color=android.graphics.Color.WHITE;textSize=27f;isAntiAlias=true}
        canvas.drawText(text,16f,300f,paint);file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle();return file.absolutePath
    }
    private fun capture(name:String){
        compose.waitForIdle();Thread.sleep(600);val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"library-1.3.9");folder.mkdirs()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot();File(folder,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private val scanGateway=object:LibraryScanGateway{
        var requests=0
        override suspend fun allowed()=true
        override suspend fun request(){requests++}
        override suspend fun snapshot()=LibraryScanSnapshot("Idle")
    }
    @Test fun jellyfinChoiceAndScanStayCompactAndCollectionsRemainAccessible(){
        val movie=MediaItem("j1",name="Arrival",year="2016",jellyfinId="j1",poster=poster("ARRIVAL",0xff24353d.toInt()))
        val series=MediaItem("j2",name="Silo",type="series",year="2023",jellyfinId="j2",poster=poster("SILO",0xff38312b.toInt()))
        val vm=JellyfinViewModel(libraryLoader={listOf(movie,series)},resumeLoader={emptyList()})
        val collection=JellyfinCollection("one","Favorites","collection",1)
        val browser=JellyfinBrowserViewModel(catalogLoader={JellyfinCatalog(emptyList(),listOf(collection))},itemLoader={_,_->listOf(series)})
        val scanModel=LibraryScanViewModel(scanGateway)
        var zurg=0
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xff090b0e)).statusBarsPadding()){LibraryScreen({_,_->},{},true,{},vm,null,null,browser,{zurg++},scanModel)}}}
        compose.onNodeWithText("All libraries").assertIsDisplayed()
        compose.onNodeWithText("SCAN LIBRARY").assertIsDisplayed()
        compose.onNodeWithText("Arrival").assertIsDisplayed()
        val chooser=compose.onNodeWithContentDescription("Choose library").fetchSemanticsNode().boundsInRoot
        val scan=compose.onNodeWithText("SCAN LIBRARY").fetchSemanticsNode().boundsInRoot
        Assert.assertTrue("Chooser and scan share one row",kotlin.math.abs(chooser.center.y-scan.center.y)<10)
        Assert.assertTrue("Posters should begin within the upper half of the viewport",compose.onNodeWithContentDescription("Arrival").fetchSemanticsNode().boundsInRoot.top < with(compose.density){380.dp.toPx()})
        capture("jellyfin-compact")
        compose.onNodeWithContentDescription("Zurg browser").performClick();Assert.assertEquals(1,zurg)
        compose.onNodeWithContentDescription("Choose library").performClick();compose.onNodeWithText("Favorites").performClick()
        compose.waitUntil(5000){browser.selected.value?.id=="one"&&!browser.busy.value}
        compose.onNodeWithText("Arrival").assertDoesNotExist();compose.onNodeWithText("Silo").assertExists()
        compose.onNodeWithContentDescription("Choose library").performClick();compose.onNodeWithText("ALL TITLES").performClick()
        compose.onNodeWithText("Arrival").assertExists()
        compose.onNodeWithText("SCAN LIBRARY").performClick();compose.waitUntil(5000){scanGateway.requests==1}
        capture("jellyfin-scan")
    }
    @Test fun torboxLongPressSelectsWithoutOpeningMenuAndStatusCountsFilter(){
        val data=listOf(TorBoxTorrent("1","Arrival.2016.2160p.WEB-DL.HDR10",10000000000L,1f,true,"cached",emptyList()),TorBoxTorrent("2","Silo.S01.1080p.WEB-DL",2000000000L,.42f,false,"downloading",emptyList()),TorBoxTorrent("3","Unknown.release",1000000000L,0f,false,"error",emptyList()))
        val posters=mapOf("1" to poster("ARRIVAL",0xff24353d.toInt()),"2" to poster("SILO",0xff38312b.toInt()))
        var loads=0
        val vm=TorBoxViewModel(loader={_,_->loads++;data},match={MediaItem(it.id,name=if(it.id=="1")"Arrival"else if(it.id=="2")"Silo"else "Unknown",poster=posters[it.id])})
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xff090b0e)).statusBarsPadding()){TorBoxScreen({},{},"synthetic-only",{},vm)}}}
        compose.waitUntil(5000){vm.rows.value.size==3&&!vm.matching.value}
        compose.onNodeWithText("SCAN LIBRARY").assertDoesNotExist()
        compose.onNodeWithText("ALL 3").assertIsDisplayed();compose.onNodeWithText("ACTIVE 1").assertIsDisplayed();compose.onNodeWithText("ERROR 1").assertIsDisplayed()
        compose.onNodeWithText(data[0].name).assertDoesNotExist()
        compose.onNodeWithTag("torrent-row-1").assertHeightIsAtLeast(80.dp)
        capture("torbox-compact")
        compose.onNodeWithText("READY 1").performClick();compose.onNodeWithText("Silo").assertDoesNotExist()
        compose.onNodeWithText("ALL 3").performClick()
        compose.onNodeWithText("Arrival").performTouchInput{longClick()}
        Assert.assertEquals(setOf("1"),vm.selected.value)
        compose.onNodeWithText("DELETE").assertDoesNotExist()
        compose.onNodeWithText("SELECT VISIBLE").performClick();Assert.assertEquals(3,vm.selected.value.size)
        capture("torbox-selection")
        compose.onNodeWithText("CANCEL SELECTION").performClick()
        val before=loads
        compose.onRoot().performTouchInput{swipe(androidx.compose.ui.geometry.Offset(5f,height*.2f),androidx.compose.ui.geometry.Offset(5f,height*.8f),700)}
        compose.waitUntil(5000){loads>before&&!vm.loading.value}
        compose.onNodeWithContentDescription("Library actions").performClick();compose.onNodeWithText("Refresh torrents").performClick()
        compose.waitUntil(5000){loads>=2&&!vm.loading.value}
        compose.onNodeWithContentDescription("Library actions").performClick();compose.onNodeWithContentDescription("Add torrent").performClick()
        compose.onNodeWithText("ADD TO TORBOX").assertIsDisplayed()
    }
}
