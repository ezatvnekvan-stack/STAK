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

class LibraryActionsExperienceTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null;private lateinit var prefix:String
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="actions-ui-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix);NativeRuntime.settings.apply{torboxKey="synthetic-only";jellyfinUrl="http://127.0.0.1:1";jellyfinUserId="fixture";jellyfinToken="synthetic-only"}}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}}
    private val torrents=listOf(TorBoxTorrent("1","First torrent",100,.3f,false,"downloading",emptyList()),TorBoxTorrent("2","Second torrent",200,.4f,false,"downloading",emptyList()))
    private class Fake:TorBoxMutations {
        val controls=mutableListOf<Pair<String,TorrentOperation>>();val adds=mutableListOf<String>();var fail:String?=null;var unknown=false
        override suspend fun addMagnet(key:String,input:MagnetInput):String {adds+=input.identity;if(unknown)throw UnconfirmedTorrentMutation();if(input.identity==fail)throw java.io.IOException("Synthetic rejection");return "55"}
        override suspend fun upload(key:String,file:TorrentUpload):String="56"
        override suspend fun control(key:String,id:String,operation:TorrentOperation){controls+=id to operation;if(id==fail)throw java.io.IOException("Synthetic rejection")}
    }
    private fun vm(api:TorBoxMutations)=TorBoxViewModel(loader={_,page->page(torrents,null);torrents},match={null},mutations=api)
    private fun capture(name:String){compose.waitForIdle();val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.absolutePath,"preview-9");dir.mkdirs();File(dir,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun deleteRequiresExplicitNamedConfirmationAndCancelSendsNothing(){
        val api=Fake();val model=vm(api)
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xff090909))){TorBoxScreen({},{},"synthetic-only",{},model)}}}
        compose.onNodeWithContentDescription("Torrent actions for First torrent").performScrollTo().performClick();compose.onNodeWithText("DELETE").performClick()
        compose.onNodeWithText("Delete 1 torrent?").assertExists();compose.onNodeWithText("CANCEL").performClick();Assert.assertTrue(api.controls.isEmpty())
        compose.onNodeWithContentDescription("Torrent actions for First torrent").performScrollTo().performClick();compose.onNodeWithText("DELETE").performClick();compose.onNodeWithText("DELETE 1").performClick()
        compose.waitUntil(5000){api.controls.size==1&&!model.actionBusy.value};Assert.assertEquals("1" to TorrentOperation.Delete,api.controls.single());Assert.assertTrue(model.rows.value.none{it.torrent.id=="1"})
    }
    @Test fun bulkPartialFailureRetainsOnlyFailedIdsAndDoesNotResendSuccess(){
        val api=Fake().apply{fail="2"};val model=vm(api)
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xff090909))){TorBoxScreen({},{},"synthetic-only",{},model)}}}
        compose.onNodeWithText("First torrent").performTouchInput{longClick()};compose.onNodeWithText("SELECT VISIBLE").performClick();compose.onNodeWithText("Pause").performClick()
        compose.waitUntil(5000){api.controls.size==2&&!model.actionBusy.value};Assert.assertEquals(setOf("2"),model.selected.value);capture("torbox-actions")
        api.fail=null;compose.onNodeWithText("Pause").performClick();compose.waitUntil(5000){api.controls.size==3&&!model.actionBusy.value};Assert.assertEquals(listOf("1","2","2"),api.controls.map{it.first})
    }
    @Test fun addDialogPreservesOnlyFailedMagnetLines(){
        val api=Fake().apply{fail="b".repeat(40)};val model=vm(api)
        compose.setContent{StakTheme{TorBoxScreen({},{},"synthetic-only",{},model)}}
        compose.onNodeWithContentDescription("Library actions").performClick();compose.onNodeWithContentDescription("Add torrent").performClick()
        compose.onNodeWithText("magnet:?xt=urn:btih:…").performTextInput("a".repeat(40)+"\n"+"b".repeat(40))
        compose.onNodeWithText("ADD TO TORBOX").performClick();compose.waitUntil(5000){model.addResults.value.size==2&&!model.actionBusy.value}
        Assert.assertEquals(2,api.adds.size);compose.onNode(hasSetTextAction() and hasText("b".repeat(40))).assertTextEquals("b".repeat(40))
    }
    @Test fun unknownAddStopsTheBatchAndBlocksBlindResubmission(){
        val api=Fake().apply{unknown=true};val model=vm(api)
        compose.setContent{TorBoxScreen({},{},"synthetic-only",{},model)}
        compose.onNodeWithContentDescription("Library actions").performClick();compose.onNodeWithContentDescription("Add torrent").performClick();compose.onNodeWithText("magnet:?xt=urn:btih:…").performTextInput("a".repeat(40)+"\n"+"b".repeat(40));compose.onNodeWithText("ADD TO TORBOX").performClick()
        compose.waitUntil(5000){model.addResults.value.isNotEmpty()&&!model.actionBusy.value};Assert.assertEquals(1,api.adds.size);compose.onNodeWithText("ADD TO TORBOX").assertIsNotEnabled();compose.onNodeWithText("REFRESH LIBRARY").assertExists()
    }
    @Test fun changingAccountDuringMutationStopsUnsentTargetsAndIgnoresLateResult(){
        val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val calls=mutableListOf<String>()
        val api=object:TorBoxMutations {
            override suspend fun addMagnet(key:String,input:MagnetInput)="unused";override suspend fun upload(key:String,file:TorrentUpload)="unused"
            override suspend fun control(key:String,id:String,operation:TorrentOperation){calls+=id;started.complete(Unit);withContext(NonCancellable){release.await()}}
        }
        val model=vm(api);compose.setContent{Box(Modifier.fillMaxSize())};compose.runOnIdle{model.refresh("synthetic-only")};compose.waitUntil(5000){model.rows.value.size==2}
        compose.runOnIdle{model.control("synthetic-only",listOf("1","2"),TorrentOperation.Delete)};compose.waitUntil(5000){started.isCompleted}
        compose.runOnIdle{NativeRuntime.settings.torboxKey="other";model.refresh("other");release.complete(Unit)}
        compose.waitUntil(5000){!model.actionBusy.value&&!model.loading.value};compose.runOnIdle{Assert.assertEquals(listOf("1"),calls);Assert.assertEquals(2,model.rows.value.size);Assert.assertTrue(model.selected.value.isEmpty())}
    }
    @Test fun repeatedAddWhileARequestIsInFlightIsIgnored(){
        val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();var calls=0
        val api=object:TorBoxMutations {
            override suspend fun addMagnet(key:String,input:MagnetInput):String{calls++;started.complete(Unit);release.await();return "55"}
            override suspend fun upload(key:String,file:TorrentUpload)="unused"
            override suspend fun control(key:String,id:String,operation:TorrentOperation){}
        }
        val model=vm(api);compose.setContent{Box(Modifier.fillMaxSize())};compose.runOnIdle{model.refresh("synthetic-only")};compose.waitUntil(5000){model.rows.value.size==2}
        compose.runOnIdle{model.add("synthetic-only","a".repeat(40),null)};compose.waitUntil(5000){started.isCompleted}
        compose.runOnIdle{model.add("synthetic-only","a".repeat(40),null);Assert.assertEquals(1,calls);release.complete(Unit)}
        compose.waitUntil(5000){!model.actionBusy.value};Assert.assertEquals(1,calls)
    }
    @Test fun newCollectionAccountRejectsLateItemsFromThePreviousSelection(){
        val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val browser=JellyfinBrowserViewModel(catalogLoader={JellyfinCatalog(emptyList(),emptyList())},itemLoader={id,_->
            if(id=="old"){started.complete(Unit);withContext(NonCancellable){release.await()}}
            listOf(MediaItem("jellyfin:$id",name=id,jellyfinId=id))
        })
        compose.setContent{Box(Modifier.fillMaxSize())};compose.runOnIdle{browser.account("old-account");browser.select(JellyfinCollection("old","Old collection","collection"))};compose.waitUntil(5000){started.isCompleted}
        compose.runOnIdle{browser.account("new-account");browser.select(JellyfinCollection("new","New collection","collection"));release.complete(Unit)}
        compose.waitUntil(5000){!browser.busy.value&&browser.items.value.singleOrNull()?.jellyfinId=="new"}
        compose.runOnIdle{Assert.assertEquals("new",browser.selected.value?.id);Assert.assertEquals(listOf("new"),browser.items.value.map{it.jellyfinId})}
    }
    @Test fun collectionsAndVerifiedVersionsBrowseWithoutReplacingTheGlobalCatalog(){
        val one=MediaItem("jellyfin:one","tt1234567",name="Film",jellyfinId="one",jellyfinVersionLabel="4K · HEVC")
        val two=one.copy(id="jellyfin:two",jellyfinId="two",jellyfinVersionLabel="1080p · H264")
        val global=JellyfinViewModel(libraryLoader={listOf(one,two)},resumeLoader={emptyList()})
        val collection=JellyfinCollection("set-1","Example collection","collection",2)
        val browser=JellyfinBrowserViewModel(catalogLoader={JellyfinCatalog(emptyList(),listOf(collection))},itemLoader={id,page->Assert.assertEquals("set-1",id);page(listOf(two),1);listOf(two)})
        var opened:MediaItem?=null
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize().background(Color(0xff090909))){LibraryScreen({item,_->opened=item},{},true,{},global,null,null,browser)}}}
        compose.onNodeWithText("2 versions").performScrollTo().performClick();compose.onNodeWithText("4K · HEVC").assertExists();compose.onNodeWithText("1080p · H264").performClick();Assert.assertEquals("two",opened?.jellyfinId)
        compose.onNodeWithContentDescription("Library filters").performScrollTo().performClick();compose.onNodeWithContentDescription("Choose library").performClick();compose.onNodeWithText("Example collection").performClick()
        compose.waitUntil(5000){browser.items.value.size==1&&!browser.busy.value};Assert.assertEquals(2,global.items.value.size);compose.onNodeWithText("2 versions").assertDoesNotExist();capture("jellyfin-collection")
    }
}
