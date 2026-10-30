@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.StakTheme
import kotlinx.coroutines.*
import org.junit.*
import java.io.File

class LibraryExperienceTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null;private lateinit var prefix:String;private val posters=mutableListOf<File>()
    @Before fun setup(){
        previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="library-ui-${java.util.UUID.randomUUID()}-"
        NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix)
        NativeRuntime.settings.apply{jellyfinUrl="http://127.0.0.1:1";jellyfinToken="synthetic-only";jellyfinUserId="fixture-user";hasOnboarded=true}
    }
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)};posters.forEach{it.delete()}}
    private fun poster(label:String,color:Int):String {
        val file=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"$prefix$label.png");posters+=file
        val bitmap=Bitmap.createBitmap(240,360,Bitmap.Config.ARGB_8888);val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(color)
        val paint=android.graphics.Paint().apply{this.color=android.graphics.Color.argb(70,255,255,255)}
        canvas.drawCircle(120f,125f,82f,paint);paint.color=android.graphics.Color.WHITE;paint.textSize=26f;canvas.drawText(label,18f,300f,paint)
        file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle();return file.absolutePath
    }
    private fun libraryItems()=listOf(MediaItem("jellyfin:1",name="Dune",year="2021",jellyfinId="1",watched=true,poster=poster("DUNE",0xff34292d.toInt())),MediaItem("jellyfin:2",type="series",name="Silo",year="2023",jellyfinId="2",progress=.4f,poster=poster("SILO",0xff273234.toInt())),MediaItem("jellyfin:3",name="Arrival",year="2016",jellyfinId="3",poster=poster("ARRIVAL",0xff333125.toInt())))
    private fun capture(name:String) {
        compose.waitForIdle()
        val folder=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.absolutePath,"preview-7");folder.mkdirs()
        File(folder,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun jellyfinSearchAndTypeFiltersWorkOnLoadedLibrary(){
        val items=libraryItems();val vm=JellyfinViewModel(libraryLoader={page->page(items,items.size);items},resumeLoader={emptyList()})
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xff090909))){LibraryScreen({_,_->},{},true,{},vm,null,null)}}}
        compose.onNodeWithText("Dune").assertExists();compose.onNodeWithText("Silo").assertExists();capture("jellyfin-grid")
        compose.onNodeWithText("SERIES").performClick();compose.onNodeWithText("Dune").assertDoesNotExist();compose.onNodeWithText("Silo").assertExists()
        compose.onNodeWithText("ALL").performClick();compose.onNodeWithText("Search titles or IMDb ID").performTextInput("arriv")
        compose.onNodeWithText("Arrival").assertExists();compose.onNodeWithText("Dune").assertDoesNotExist()
    }
    @Test fun libraryOptionsSwitchToListAndWatchedFilterPersistsOnRefresh(){
        val items=libraryItems();val vm=JellyfinViewModel(libraryLoader={items},resumeLoader={emptyList()})
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xff090909))){LibraryScreen({_,_->},{},true,{},vm,null,null)}}}
        compose.onNodeWithContentDescription("Library filters").performClick()
        compose.onNodeWithText("Poster grid").performClick();compose.onNodeWithText("List").performClick()
        compose.onNodeWithText("All titles").performClick();compose.onNode(hasText("Watched") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("DONE").performClick()
        compose.onNodeWithText("Dune").assertExists();compose.onNodeWithText("Silo").assertDoesNotExist();capture("jellyfin-list")
        compose.onNodeWithContentDescription("Library actions").performClick();compose.onNodeWithText("Refresh library").performClick()
        compose.waitUntil(5000){!vm.loading.value};Assert.assertEquals("list",vm.filters.value.layout);Assert.assertEquals("watched",vm.filters.value.status)
    }
    @Test fun failedLaterPageKeepsLoadedJellyfinRowsVisible(){
        val items=libraryItems();val vm=JellyfinViewModel(libraryLoader={page->page(items,900);throw java.io.IOException("Synthetic second-page failure")},resumeLoader={emptyList()})
        compose.setContent{StakTheme{LibraryScreen({_,_->},{},true,{},vm,null,null)}}
        compose.waitUntil(5000){vm.error.value!=null}
        compose.onNodeWithText("Dune").assertExists();compose.onNodeWithText("Retry library",ignoreCase=true).assertExists();Assert.assertEquals(3,vm.items.value.size)
    }
    private fun torrents()=listOf(TorBoxTorrent("1","Dune.2021.2160p.WEB-DL",10_000_000_000,1f,true,"uploading",listOf(TorBoxFile("1","Dune.2021.mkv",9_000_000_000)),createdAt=20),TorBoxTorrent("2","Silo.S01.1080p.WEB-DL",20_000_000_000,.42f,false,"downloading",listOf(TorBoxFile("2","Silo.S01E01.mkv",1_000_000_000)),createdAt=10))
    @Test fun torboxSearchStatusSortingAndFileSheetUseResolvedMetadata(){
        val torrents=torrents();val items=libraryItems();val vm=TorBoxViewModel(loader={_,page->page(torrents,null);torrents},match={torrent->items.first{it.jellyfinId==torrent.id}.copy(imdbId="tt1234567")})
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xff090909))){TorBoxScreen({},{},"synthetic-only",{},vm)}}}
        compose.waitUntil(5000){vm.rows.value.size==2&&!vm.matching.value}
        compose.onNodeWithText("Dune").assertExists();compose.onNodeWithText("Silo").assertExists();capture("torbox-library")
        compose.onNodeWithText("READY 1").performClick();compose.onNodeWithText("Silo").assertDoesNotExist()
        compose.onNodeWithText("Dune").performClick();compose.onNodeWithText("Dune.2021.mkv").assertExists();compose.onNodeWithText("Copy download link").assertExists();compose.onNodeWithText("CLOSE").performClick()
        compose.onNodeWithText("ALL 2").performClick();compose.onNodeWithText("Search names, files or hash").performTextInput("S01E01")
        compose.onNodeWithText("Silo").assertExists();compose.onNodeWithText("Dune").assertDoesNotExist()
        compose.onNodeWithContentDescription("Library filters").performClick();compose.onNodeWithContentDescription("Sort by").performClick();compose.onNodeWithText("Largest first").performClick();compose.onNodeWithText("DONE").performClick()
        Assert.assertEquals("size",vm.filters.value.sort)
    }
    @Test fun torboxPartialFailurePreservesRowsAndMatchingContinues(){
        val torrents=torrents();val vm=TorBoxViewModel(loader={_,page->page(torrents,null);throw java.io.IOException("Synthetic failure")},match={MediaItem("tt123",name="Matched",poster="https://example.invalid/poster")})
        compose.setContent{TorBoxScreen({},{},"synthetic-only",{},vm)}
        compose.waitUntil(5000){vm.error.value!=null&&!vm.matching.value};Assert.assertEquals(2,vm.rows.value.size);Assert.assertTrue(vm.rows.value.all{it.metadata!=null})
        compose.onNodeWithText("Retry library",ignoreCase=true).assertExists()
    }
    @Test fun switchingTorboxAccountClearsRowsFiltersAndRejectsLateOldResponse(){
        val release=CompletableDeferred<Unit>();val started=CompletableDeferred<Unit>()
        val vm=TorBoxViewModel(loader={key,_->if(key=="old"){started.complete(Unit);withContext(NonCancellable){release.await()};listOf(torrents()[0])} else listOf(torrents()[1])},match={null})
        compose.setContent{Box(Modifier.fillMaxSize())}
        compose.runOnIdle{vm.refresh("old")};compose.waitUntil(5000){started.isCompleted}
        compose.runOnIdle{vm.setFilters(LibraryFilters(query="old query"));vm.refresh("new");release.complete(Unit)}
        compose.waitUntil(5000){vm.rows.value.singleOrNull()?.torrent?.id=="2"&&!vm.loading.value}
        compose.runOnIdle{Assert.assertEquals("",vm.filters.value.query);Assert.assertEquals(listOf("2"),vm.rows.value.map{it.torrent.id})}
    }
}
