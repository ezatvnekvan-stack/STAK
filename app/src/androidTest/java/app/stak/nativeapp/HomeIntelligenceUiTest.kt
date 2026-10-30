@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import com.google.gson.*
import kotlinx.coroutines.*
import org.junit.*
import java.time.LocalDate
import java.io.File

class HomeIntelligenceUiTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null;private lateinit var prefix:String
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="home123-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{reducedMotion=true;traktClientId="synthetic-client"}}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
    private fun capture(name:String){compose.waitForIdle();val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: context.getExternalFilesDir(null)!!.path,"home-1.2.3");dir.mkdirs();val image=compose.onRoot().captureToImage().asAndroidBitmap();File(dir,"$name.png").outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
    @Composable private fun Canvas(content:@Composable ()->Unit){StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Box(Modifier.fillMaxSize().background(StakBackground)){content()}}}}
    private val today get()=LocalDate.now()
    private fun media(id:Int,name:String,type:String="movie")=MediaItem("tmdb:$id",type=type,name=name,year="2026")
    @Test fun homeShowsNumberedTrendingAndBothAnticipatedRowsWithCorrectBrowseActions(){
        val movies=listOf(media(1,"First movie"),media(2,"Second movie"));val shows=listOf(media(3,"First show","series"))
        val feed=HomeFeed(rows=listOf(HomeRow("trending_movie","Trending movies",movies,provider="Trakt"),HomeRow("trending_series","Trending series",shows,provider="Trakt"),HomeRow("anticipated_movie","Most anticipated movies",movies,provider="Trakt"),HomeRow("anticipated_series","Most anticipated series",shows,provider="Trakt")))
        val vm=HomeViewModel(feedLoader={feed});var browsed=""
        compose.setContent{Canvas{HomeScreen({_,_->},{},{},{},{},{browsed=it},jellyfinConnected=true,sharedScope=null,animatedScope=null,reducedMotion=true,viewModel=vm)}}
        compose.onNodeWithText("TRENDING MOVIES").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Rank 2",useUnmergedTree=true).assertExists()
        capture("numbered-trending")
        compose.onNodeWithTag("home-feed").performScrollToNode(hasText("MOST ANTICIPATED MOVIES"))
        compose.onNodeWithText("MOST ANTICIPATED MOVIES").assertIsDisplayed()
        compose.onNodeWithTag("home-feed").performScrollToNode(hasText("MOST ANTICIPATED SERIES"))
        compose.onNodeWithText("MOST ANTICIPATED SERIES").assertIsDisplayed()
        capture("anticipated-rows")
    }
    @Test fun comingUpShowsThreeDayEpisodeAndOpensTheWatchlistedTitle(){
        val title=media(9,"Returning series","series");val event=ReleaseEvent(title,today.plusDays(3),"The next chapter",2,4)
        val vm=HomeViewModel(feedLoader={HomeFeed()},releaseLoader={_,_,_->ReleaseFeed(listOf(event),emptyList(),1)});var opened=""
        compose.setContent{Canvas{HomeScreen({item,_->opened=item.id},{},{},{},{},{},watchlist=listOf(title),jellyfinConnected=true,sharedScope=null,animatedScope=null,reducedMotion=true,viewModel=vm)}}
        compose.waitUntil(5000){vm.comingUp.value!=null}
        compose.onNodeWithText("IN 3 DAYS").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("S02 E04 · The next chapter").assertIsDisplayed();capture("coming-up-episode")
        compose.onNodeWithText("IN 3 DAYS").performClick();Assert.assertEquals(title.id,opened)
    }
    @Test fun movieCardClearlyLabelsDigitalDateInsteadOfLibraryAvailability(){
        compose.setContent{Canvas{ComingUpCard(ReleaseEvent(media(1,"Upcoming film"),today.plusDays(1),"Film",releaseLabel="US · Digital"),today){}}}
        compose.onNodeWithText("TOMORROW").assertIsDisplayed();compose.onNodeWithText("US · Digital").assertIsDisplayed();capture("coming-up-movie")
    }
    @Test fun removedWatchlistCannotLeaveAStaleUpcomingCard(){
        val pending=CompletableDeferred<Unit>();val title=media(9,"Removed show","series")
        val vm=HomeViewModel(feedLoader={HomeFeed()},releaseLoader={_,date,_->pending.await();ReleaseFeed(listOf(ReleaseEvent(title,date.plusDays(3),"Next",1,1)),emptyList(),1)})
        compose.runOnIdle{vm.refreshUpcoming(listOf(title),today);vm.refreshUpcoming(emptyList(),today);pending.complete(Unit)}
        compose.waitForIdle();Assert.assertNull(vm.comingUp.value);Assert.assertFalse(vm.comingLoading.value)
    }
    @Test fun missingTraktShowsOneSetupActionRatherThanFalseRankings(){
        NativeRuntime.settings.traktClientId=null
        val vm=HomeViewModel(feedLoader={HomeFeed(rows=listOf(HomeRow("trending_movie","Trending movies",emptyList(),"Missing key","Trakt")))})
        compose.setContent{Canvas{HomeScreen({_,_->},{},{},{},{},{},sharedScope=null,animatedScope=null,reducedMotion=true,viewModel=vm)}}
        compose.onNodeWithText("SET UP TRAKT").assertIsDisplayed();compose.onNodeWithText("Missing key").assertDoesNotExist();capture("trakt-setup")
    }
    @Test fun seeAllRepositoryUsesTraktAndKeepsItsPagination()=runBlocking{
        val calls=mutableListOf<String>()
        val trakt=TraktCatalog({"synthetic"},{path,params,_->calls+="$path:${params["page"]}";TraktPage(JsonParser.parseString("""[{"show":{"title":"Upcoming","ids":{"tmdb":9}}}]""").asJsonArray,4)},{it})
        val result=MediaRepository(trakt=trakt).discover("series","anticipated","",2)
        Assert.assertEquals("trakt",result.source);Assert.assertEquals(4,result.totalPages);Assert.assertEquals(listOf("/shows/anticipated:2"),calls)
    }
    @Test fun traktIdPersistsEncryptedWithoutChangingOtherSources(){
        val settings=NativeRuntime.settings;settings.tmdbKey="existing-tmdb";TorznabStore(settings).save(listOf(TorznabIndexer(name="Existing",url="https://indexer.test/api",apiKey="synthetic-nab")))
        settings.saveTraktClientIdChecked("synthetic-private-client-id")
        val fresh=SettingsStore(context,prefix);Assert.assertEquals("synthetic-private-client-id",fresh.traktClientId);Assert.assertEquals("existing-tmdb",fresh.tmdbKey);Assert.assertEquals(1,TorznabStore(fresh).all().size)
        val encrypted=File(context.applicationInfo.dataDir,"shared_prefs/${prefix}stak_secrets.xml").readText();Assert.assertFalse(encrypted.contains("synthetic-private-client-id"))
    }
}
