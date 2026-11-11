@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class Cinematic156UiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix="cinematic156-${java.util.UUID.randomUUID()}-"
    private var previous:SettingsStore?=null
    private val files=mutableListOf<File>()
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();NativeRuntime.settings=SettingsStore(context,prefix).apply{ratingsEnabled=false;reducedMotion=true}}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)};files.forEach{it.delete()}}
    private fun art():String {
        val f=File.createTempFile(prefix,".png",context.cacheDir);files+=f
        val b=Bitmap.createBitmap(1600,900,Bitmap.Config.ARGB_8888)
        val c=android.graphics.Canvas(b);val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.shader=android.graphics.LinearGradient(0f,0f,1600f,900f,0xff305b73.toInt(),0xff09151b.toInt(),android.graphics.Shader.TileMode.CLAMP);c.drawPaint(p)
        p.shader=null;p.color=0xffa2b5bd.toInt();c.drawCircle(1200f,300f,180f,p)
        p.color=0xff07141b.toInt();c.drawRect(1000f,450f,1400f,900f,p)
        // Neutral background; branding is a separate transparent image.
        f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle();return f.toURI().toString()
    }
    private fun logo():String {
        val f=File.createTempFile(prefix,".png",context.cacheDir);files+=f
        val b=Bitmap.createBitmap(800,150,Bitmap.Config.ARGB_8888)
        val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.WHITE;textSize=76f;typeface=android.graphics.Typeface.DEFAULT_BOLD}
        android.graphics.Canvas(b).drawText("NIGHT SIGNAL",0f,105f,p)
        f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle();return f.toURI().toString()
    }
    private fun capture(name:String){
        compose.waitForIdle();Thread.sleep(250);compose.waitForIdle()
        val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:context.getExternalFilesDir(null)!!.path,"cinematic156").apply{mkdirs()}
        File(dir,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun originalContinueCardsUseSeparateLogoAndKeepShortProgress(){
        val image=art();val mark=logo();var opened=""
        val first=MediaItem("fixture-one",name="Night Signal",type="series",season=2,episode=3,background=image,progress=.4f,positionMs=600000,durationMs=2400000)
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Column(Modifier.fillMaxWidth().background(StakBackground).padding(16.dp)){
            ContinueRail(listOf(first,first.copy(id="fixture-two",name="Second",nextUp=true)),artworkLoader={ContinueArtwork(image,mark)}){opened=it.id}
        }}}}
        val cards=compose.onAllNodesWithTag("continue-card")
        val firstBounds=cards[0].getUnclippedBoundsInRoot();val second=cards[1].getUnclippedBoundsInRoot()
        val rail=compose.onNodeWithTag("continue-rail").getUnclippedBoundsInRoot()
        assertEquals(148f,firstBounds.height.value,.5f)
        assertTrue(firstBounds.width<rail.width*.85f);assertTrue(second.left<rail.right)
        val progress=compose.onNodeWithTag("continue-progress",useUnmergedTree=true).getUnclippedBoundsInRoot()
        assertTrue(progress.width<firstBounds.width*.4f)
        val info=compose.onNodeWithText("30 min left",useUnmergedTree=true).getUnclippedBoundsInRoot()
        val brand=compose.onAllNodesWithTag("continue-logo",useUnmergedTree=true)[0].getUnclippedBoundsInRoot()
        assertTrue(brand.bottom<info.top);assertTrue(info.bottom<progress.top);assertTrue(progress.bottom<firstBounds.bottom)
        compose.onNodeWithText("NEXT UP").assertIsDisplayed();compose.onNodeWithText("IN PROGRESS").assertIsDisplayed()
        compose.onNodeWithTag("continue-panel-footer",useUnmergedTree=true).assertDoesNotExist()
        compose.waitUntil(5000){compose.onAllNodesWithText(first.name,useUnmergedTree=true).fetchSemanticsNodes().isEmpty()}
        cards[0].performClick();assertEquals(first.id,opened);capture("continue-restored-logo")
    }
    @Test fun sourceFilterHasSingleLargeSelectableSurface(){
        var selected by mutableStateOf(false)
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Row(Modifier.background(StakBackground).padding(16.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){
            SourceChip("1080P",selected,{selected=!selected},count=78)
            SourceChip("REMUX",false,{})
        }}}}
        compose.onNodeWithText("1080P").performClick();assertTrue(selected)
        compose.onNode(isSelected()).assertHeightIsAtLeast(44.dp);capture("source-selected-filters")
    }
    @Test fun narrowLargeTextContinueCardSeparatesFallbackTitleAndEpisodeInfo(){
        val item=MediaItem("large-font",name="A long fallback title",type="series",season=2,episode=3,progress=.4f,positionMs=600000,durationMs=2400000)
        compose.setContent{StakTheme{
            val d=LocalDensity.current
            CompositionLocalProvider(LocalReducedMotion provides true,LocalDensity provides Density(d.density,1.8f)){
                Box(Modifier.width(320.dp).background(StakBackground).padding(16.dp)){
                    ContinueRail(listOf(item),artworkLoader={ContinueArtwork(null)}){}
                }
            }
        }}
        val title=compose.onNodeWithText(item.name,useUnmergedTree=true).getUnclippedBoundsInRoot()
        val info=compose.onNodeWithText("30 min left",useUnmergedTree=true).getUnclippedBoundsInRoot()
        val imageBounds=compose.onNodeWithTag("continue-image",useUnmergedTree=true).getUnclippedBoundsInRoot()
        assertTrue(title.bottom+4.dp<=info.top);assertTrue(info.bottom<imageBounds.bottom)
        capture("continue-large-text")
    }
    @Test fun recommendationAddsNoTextRowAndSizeLivesInUpperRight(){
        val release=SourceRelease("Night.Signal.S01.2160p.REMUX.DV.HDR",resolution="2160p",remux=true,dolbyVision=true,hdr=true,torrentSizeBytes=83234567890)
        compose.setContent{StakTheme{Box(Modifier.width(360.dp).background(StakBackground).padding(12.dp)){
            SourceReleaseCard(release,true,false,false,{},{},true,true,1,8,{})
        }}}
        compose.onNodeWithText("Recommended").assertDoesNotExist()
        val badges=compose.onNodeWithTag("source-badge-line",useUnmergedTree=true).getUnclippedBoundsInRoot()
        val size=compose.onNodeWithTag("source-size",useUnmergedTree=true).getUnclippedBoundsInRoot()
        assertTrue(size.left>badges.left);assertTrue(kotlin.math.abs(size.top.value-badges.top.value)<2)
        compose.onNodeWithText("ADD").assertDoesNotExist();compose.onNodeWithContentDescription("Add cached release").assertIsDisplayed()
        capture("source-compact-card")
    }
    @Test fun sourcePlusBecomesCheckOnlyAfterAcknowledgementAndOpensFiles(){
        var added by mutableStateOf(false);var adding by mutableStateOf(false);var allowed by mutableStateOf(false);var adds=0;var files=0
        compose.setContent{StakTheme{CompositionLocalProvider(LocalReducedMotion provides false){Box(Modifier.background(StakBackground).padding(20.dp)){
            SourceAcquireButton(added,adding,allowed,{adds++;adding=true},{files++})
        }}}}
        compose.onNodeWithContentDescription("Add cached release").assertIsNotEnabled()
        compose.runOnIdle{allowed=true};compose.onNodeWithContentDescription("Add cached release").performClick()
        assertEquals(1,adds);compose.onNodeWithContentDescription("Adding release").assertIsNotEnabled()
        compose.runOnIdle{added=true;adding=false};compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Open added release files").performClick();assertEquals(1,files);assertEquals(1,adds)
        capture("source-confirmed-check")
    }
    @Test fun sortSelectorChangesOrderChoiceAndLibraryPickerStaysInFilters(){
        var filters by mutableStateOf(LibraryFilters());var open by mutableStateOf(false);var chose=false
        compose.setContent{StakTheme{Column(Modifier.background(StakBackground).padding(16.dp)){
            LibrarySortChoice(filters.sort,{filters=filters.copy(sort=it)})
            StakButton("Filters",{open=true})
            if(open)LibraryFilterDialog(filters,{filters=it},{open=false},libraryName="All libraries",onChooseLibrary={open=false;chose=true})
        }}}
        compose.onNodeWithContentDescription("Sort library").performClick();compose.onNodeWithText("Title Z–A").performClick()
        assertEquals("name_desc",filters.sort)
        compose.onNodeWithText("All libraries").assertDoesNotExist()
        compose.onNodeWithText("FILTERS").performClick();compose.onNodeWithContentDescription("Choose library").performClick();assertTrue(chose)
        capture("library-sort")
    }
    @Test fun watchlistTotalsStayOneCompactLineAndReflectUpdates(){
        var count by mutableIntStateOf(3)
        compose.setContent{StakTheme{Column(Modifier.background(StakBackground).padding(16.dp)){
            WatchlistSummary(count,1)
        }}}
        assertTrue(compose.onNodeWithText("3 movies · 1 series").getUnclippedBoundsInRoot().height<=24.dp)
        compose.runOnIdle{count=120};compose.onNodeWithText("120 movies · 1 series").assertIsDisplayed();capture("watchlist-compact-counts")
    }
}
