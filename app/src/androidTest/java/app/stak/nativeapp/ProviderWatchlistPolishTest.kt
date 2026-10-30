package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.screens.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import org.junit.*
import java.io.File

class ProviderWatchlistPolishTest {
    @get:Rule val compose=createComposeRule()
    private var previous:SettingsStore?=null;private lateinit var prefix:String;private var image:File?=null
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup(){previous=runCatching{NativeRuntime.settings}.getOrNull();prefix="polish128-${java.util.UUID.randomUUID()}-";NativeRuntime.settings=SettingsStore(context,prefix).apply{reducedMotion=true}}
    @After fun cleanup(){previous?.let{NativeRuntime.settings=it};image?.delete();listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}}
    @Composable private fun Frame(content:@Composable ()->Unit){StakTheme{CompositionLocalProvider(LocalReducedMotion provides true){Column(Modifier.fillMaxSize().background(StakBackground).padding(16.dp),content={content()})}}}
    private fun capture(name:String){val bitmap=compose.onRoot().captureToImage().asAndroidBitmap();val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: context.getExternalFilesDir(null)!!.path,"polish-1.2.8");dir.mkdirs();File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun serviceArtworkFillsBothSidesOfItsCardAndKeepsSelection(){
        val bitmap=Bitmap.createBitmap(240,240,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff24b76a.toInt());val file=File(context.cacheDir,"$prefix.png");file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};image=file
        compose.setContent{Frame{DiscoveryServiceStrip(listOf(DiscoveryOption("fixture","Fixture service",file.absolutePath)),"fixture",true,{},{})}}
        compose.waitUntil(5000){runCatching{val b=compose.onNodeWithContentDescription("Fixture service").captureToImage().asAndroidBitmap();android.graphics.Color.green(b.getPixel(5,b.height/2))>130&&android.graphics.Color.green(b.getPixel(b.width-6,b.height/2))>130}.getOrDefault(false)}
        compose.onNodeWithContentDescription("Fixture service").assertIsSelected();capture("full-card-service-artwork")
    }
    @Test fun watchlistDotoCountersReflectRealCountsAndUpdates(){
        var movies by mutableStateOf(3);var series by mutableStateOf(4)
        compose.setContent{Frame{StakPageHeader("Watchlist");Spacer(Modifier.height(8.dp));WatchlistSummary(movies,series)}}
        compose.onNodeWithContentDescription("Movies count").assertTextEquals("3","MOVIES");compose.onNodeWithContentDescription("Series count").assertTextEquals("4","SERIES");capture("watchlist-doto-summary")
        compose.runOnIdle{movies=120;series=0}
        compose.onNodeWithContentDescription("Movies count").assertTextEquals("120","MOVIES");compose.onNodeWithContentDescription("Series count").assertTextEquals("0","SERIES")
    }
}
