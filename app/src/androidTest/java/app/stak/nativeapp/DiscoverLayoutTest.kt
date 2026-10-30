package app.stak.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import org.junit.*
import java.io.File

class DiscoverLayoutTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null;private lateinit var prefix:String
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val options=listOf(DiscoveryOption("350","Apple TV"),DiscoveryOption("8","Netflix"),DiscoveryOption("1899","HBO Max"),DiscoveryOption("337","Disney Plus"),DiscoveryOption("15","Hulu"),DiscoveryOption("9","Prime Video"))
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="discover127-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{reducedMotion=true}}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
    @Composable private fun Frame(content:@Composable ()->Unit){StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Column(Modifier.fillMaxSize().background(StakBackground).padding(16.dp),content={content()})}}}
    private fun capture(name:String){compose.waitForIdle();val bitmap=compose.onRoot().captureToImage().asAndroidBitmap();val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: context.getExternalFilesDir(null)!!.path,"discover-1.2.7");dir.mkdirs();File(dir,"$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun compactHeaderKeepsServiceCardsSquareFortyEightAndNoDuplicateProviderChip(){
        var filters by mutableStateOf(DiscoveryFilters(provider="8"));var opened=false
        compose.setContent{Frame{
            DiscoverHeading(filters.chips().size){opened=true}
            Spacer(Modifier.height(8.dp))
            DiscoverBrowseHeader("","movie","popular",filters,20,filters.chips(),{},{},{},{},{},{}){DiscoveryServiceStrip(options,filters.provider,true,{filters=filters.copy(provider=it)},{})}
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.testTag("first-poster-row")){(1..3).forEach{StakPosterCard(MediaItem("fixture-$it",name="Title $it",year="2026"),{},Modifier.weight(1f))}}
        }}
        val size=compose.onNodeWithContentDescription("Netflix").getUnclippedBoundsInRoot();Assert.assertEquals(48.dp,(size.right-size.left));Assert.assertEquals(48.dp,(size.bottom-size.top))
        val header=compose.onNodeWithTag("discover-browse-header").getUnclippedBoundsInRoot();Assert.assertTrue("Browse controls should remain compact",(header.bottom-header.top)<=270.dp)
        compose.onNodeWithText("Netflix ×").assertDoesNotExist();compose.onNodeWithContentDescription("Netflix").assertIsSelected();capture("compact-discover-header")
        compose.onNodeWithContentDescription("Filters").performClick();Assert.assertTrue(opened)
        compose.onNodeWithContentDescription("Netflix").performClick();Assert.assertEquals("",filters.provider)
    }
    @Test fun serviceRowScrollsToAllServicesAndLoadingDoesNotChangeSelection(){
        var selected="8";var more=false
        compose.setContent{Frame{DiscoveryServiceStrip(options,selected,false,{selected=it},{more=true})}}
        compose.onNodeWithContentDescription("Netflix").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Streaming shortcuts").performScrollToNode(hasContentDescription("All services"))
        compose.onNodeWithContentDescription("All services").performClick();Assert.assertTrue(more);Assert.assertEquals("8",selected)
    }
    @Test fun secondaryCategoriesAndSortRemainReachable(){
        var category by mutableStateOf("popular");var sort by mutableStateOf("")
        compose.setContent{Frame{DiscoverCategoryTabs(category){category=it};DiscoverSortMenu(sort){sort=it}}}
        compose.onNodeWithText("Trending").performClick();Assert.assertEquals("trending",category)
        compose.onNodeWithTag("discover-category-tabs").performScrollToNode(hasContentDescription("More categories"))
        compose.onNodeWithContentDescription("More categories").performClick();compose.onNodeWithText("Hidden gems").performClick();Assert.assertEquals("hidden",category)
        compose.onNodeWithContentDescription("Sort").performClick();compose.onNodeWithText("Newest first").performClick();Assert.assertEquals("primary_release_date.desc",sort)
        capture("category-sort-selection")
    }
    @Test fun searchAndNonProviderFilterChipsKeepTheirActions(){
        var query by mutableStateOf("");var filters by mutableStateOf(DiscoveryFilters(yearFrom="2020",yearTo="2026"))
        compose.setContent{Frame{DiscoverBrowseHeader(query,"movie","popular",filters,4,filters.chips(),{query=it},{},{},{filters=filters.without(it)},{filters=DiscoveryFilters()},{}){}}}
        compose.onNodeWithText("2020–2026 ×").performClick();Assert.assertEquals("",filters.yearFrom)
        compose.onNode(hasSetTextAction()).performTextInput("Dune");Assert.assertEquals("Dune",query)
        compose.onNodeWithContentDescription("Clear search").performClick();Assert.assertEquals("",query)
    }
}
