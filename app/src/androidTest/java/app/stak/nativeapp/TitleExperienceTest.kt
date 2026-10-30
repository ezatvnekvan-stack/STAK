@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.data.db.*
import app.stak.nativeapp.ui.screens.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*

class TitleExperienceTest {
    @get:Rule val compose=createComposeRule()
    private val preferences=mutableSetOf<String>()
    private var previous:SettingsStore?=null
    @Before fun isolateCredentials() {
        previous=runCatching { NativeRuntime.settings }.getOrNull()
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val prefix="title-experience-test-${java.util.UUID.randomUUID()}-"
        preferences+=prefix+"stak_settings";preferences+=prefix+"stak_secrets"
        NativeRuntime.settings=SettingsStore(target,prefix)
    }
    @After fun restore() {
        previous?.let { NativeRuntime.settings=it }
        preferences.forEach { InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(it) }
    }
    @Test fun galleryOpensAndClosesAndCreditRolesRemainVisible() {
        val movie=MediaItem("tt1234567", "tt1234567", name="Test Movie")
        val detail=TitleDetails(movie,gallery=listOf("file:///nonexistent-test-image"),crew=listOf(TitlePerson("1","Director Name","Director · Writer")),cast=listOf(TitlePerson("2","Actor Name","Character")))
        compose.setContent { TitleExtras(detail,movie,{}) }
        compose.onNodeWithText("CREATORS").assertIsDisplayed()
        compose.onNodeWithText("Director · Writer").assertExists()
        compose.onNodeWithContentDescription("View artwork 1 for Test Movie").performClick()
        compose.onNodeWithText("CLOSE ARTWORK").assertIsDisplayed().performClick()
        compose.onNodeWithText("CLOSE ARTWORK").assertDoesNotExist()
        compose.onNodeWithText("Actor Name").assertExists()
    }
    @org.junit.Ignore("Manual watched mutations retired in 1.3.4; PassiveTracking tests verify read-only behavior")
    @Test fun episodeSelectionUpdatesWatchedStateAndSeasonRequiresConfirmation() {
        val show=MediaItem("tt1234567","tt1234567",type="series",name="Test Series")
        val episodes=listOf(Episode(1,1,"Pilot",overview="Episode synopsis"),Episode(1,2,"Second"),Episode(2,1,"Next season"))
        val vm=TitleViewModel(detailsLoader={_,_->TitleDetails(show,episodes=episodes,seasons=listOf(1,2))})
        var states by mutableStateOf<Map<String,Boolean>>(emptyMap())
        var seasonMarks=0
        compose.setContent { TitleScreen(show,{},false,false,{},null,null,viewModel=vm,episodeStates=states,onMarkEpisode={media,ep,played->states=states+("${media.mediaKey}:${ep.season}:${ep.episode}" to played)},onMarkSeason={_,eps->seasonMarks=eps.size}) }
        compose.onRoot().performTouchInput { swipeUp() }
        compose.onNode(hasClickAction() and hasText("1. Pilot")).performScrollTo().assertWidthIsAtLeast(170.dp).performTouchInput { click(androidx.compose.ui.geometry.Offset(center.x, 16.dp.toPx())) }
        compose.onNodeWithText("Episode synopsis").assertExists()
        compose.onNodeWithText("MARK WATCHED").performScrollTo().performClick()
        Assert.assertEquals(true,states["tt1234567:1:1"])
        compose.onNodeWithText("WATCHED · UNDO").performScrollTo().performClick()
        Assert.assertEquals(false,states["tt1234567:1:1"])
        compose.onNodeWithText("CLOSE",useUnmergedTree=false).performScrollTo().performClick()
        compose.onNodeWithText("WATCH SEASON").performScrollTo().performClick()
        Assert.assertEquals(0,seasonMarks)
        compose.onNodeWithText("MARK SEASON WATCHED").performClick()
        Assert.assertEquals(2,seasonMarks)
        compose.onNodeWithText("S02").performScrollTo().performClick()
        compose.onNodeWithText("1. Next season").performScrollTo().assertIsDisplayed()
    }
    @Test fun episodeStatesAndBatchSeasonWritesPersistIndependently() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
        try {
            val repository=TrackingRepository(db.trackingDao())
            repository.upsertAll(listOf(TrackingEntity("tt1234567:1:1","episode","Pilot",true,1f,1),TrackingEntity("tt1234567:1:2","episode","Second",true,1f,1)))
            repository.upsert(TrackingEntity("tt1234567:1:1","episode","Pilot",false,0f,2))
            val rows=repository.observe().first()
            Assert.assertEquals(2,rows.size)
            Assert.assertFalse(rows.first { it.id.endsWith(":1") }.played)
            Assert.assertTrue(rows.first { it.id.endsWith(":2") }.played)
        } finally { db.close() }
    }
    @Test fun changingEpisodeCannotDisplayThePreviousEpisodesSources() {
        compose.setContent { }
        val delayed=kotlinx.coroutines.CompletableDeferred<List<SourceRelease>>()
        val first=Episode(1,1,"First");val second=Episode(1,2,"Second")
        val show=MediaItem("tt1234567","tt1234567",type="series",name="Series")
        val vm=TitleViewModel(detailsLoader={_,_->TitleDetails(show,episodes=listOf(first,second),seasons=listOf(1))},sourceLoader={_,_,ep->if(ep?.episode==1)delayed.await() else listOf(SourceRelease("Second episode"))})
        compose.runOnIdle { vm.load("series",show.mediaKey) }
        compose.waitUntil(5000) { vm.detail.value!=null }
        compose.runOnIdle { vm.loadSources("series",show.mediaKey) }
        compose.waitUntil(5000) { vm.sourceLoading.value }
        compose.runOnIdle { vm.selectEpisode(second);vm.loadSources("series",show.mediaKey) }
        compose.waitUntil(5000) { vm.sources.value.isNotEmpty() }
        delayed.complete(listOf(SourceRelease("Wrong old episode")))
        compose.waitForIdle()
        Assert.assertEquals("Second episode",vm.sources.value.single().name)
    }
}
