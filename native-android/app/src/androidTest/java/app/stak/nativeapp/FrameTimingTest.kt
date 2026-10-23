package app.stak.nativeapp

import android.content.ContentValues
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.ImageLoader
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Choreographer/Window timing, without Compose's synthetic test frame clock.
 * Debug emulator timings are diagnostic, never a physical-device 90 Hz certification.
 */
@RunWith(AndroidJUnit4::class)
class FrameTimingTest {
    @Test fun recordRealFrameTimingForScrollAndNavigation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as android.app.Application
        val picture = instrumentation.context.assets.open("provider-fixture.png").use { it.readBytes() }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).code(200).message("FIXTURE")
                .protocol(Protocol.HTTP_1_1).body(
                    if (chain.request().url.host == "image.tmdb.org") picture.toResponseBody()
                    else """{"metas":[],"results":[],"streams":[]}""".toResponseBody()
                ).build()
        }.build()
        val vm = StakViewModel(app, Repository(app, client))
        listOf("tmdb_key", "torbox_token", "jellyfin_url", "jellyfin_token").forEach { vm.repository.credentials.set(it, "") }
        val titles = (1..90).map { Media("tt${1000000 + it}", "movie", "Frame fixture $it", year = "2026", poster = "https://image.tmdb.org/fixture/$it.png") }
        runBlocking(Dispatchers.IO) {
            vm.repository.db.clearAllTables()
            titles.forEach { vm.repository.dao.put(SavedMedia(it.key, "watchlist", it.json().toString())) }
        }
        Coil.setImageLoader(ImageLoader.Builder(app).okHttpClient(client).build())
        val thread = HandlerThread("stak-frame-reports").apply { start() }
        val frames = Collections.synchronizedList(mutableListOf<Long>())
        val navigation = Collections.synchronizedList(mutableListOf<Long>())
        val render = Collections.synchronizedList(mutableListOf<Long>())
        val layout = Collections.synchronizedList(mutableListOf<Long>())
        val draw = Collections.synchronizedList(mutableListOf<Long>())
        val delay = Collections.synchronizedList(mutableListOf<Long>())
        val gpu = Collections.synchronizedList(mutableListOf<Long>())
        val swap = Collections.synchronizedList(mutableListOf<Long>())
        val droppedReports = java.util.concurrent.atomic.AtomicInteger()
        var phase = frames
        val listener = Window.OnFrameMetricsAvailableListener { _, metric, dropped ->
            droppedReports.addAndGet(dropped)
            phase.add(metric.getMetric(FrameMetrics.TOTAL_DURATION))
            render.add(metric.getMetric(FrameMetrics.DRAW_DURATION) + metric.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
                + metric.getMetric(FrameMetrics.ANIMATION_DURATION) + metric.getMetric(FrameMetrics.INPUT_HANDLING_DURATION))
            layout.add(metric.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION))
            draw.add(metric.getMetric(FrameMetrics.DRAW_DURATION))
            delay.add(metric.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION))
            swap.add(metric.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION))
            if (android.os.Build.VERSION.SDK_INT >= 31) metric.getMetric(FrameMetrics.GPU_DURATION).takeIf { it >= 0 }?.let { gpu.add(it) }
        }
        val destination = mutableStateOf("watchlist")
        val variant = if (app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) "debug" else "preview"
        val report = JSONObject().put("buildType", variant).put("hardware", android.os.Build.HARDWARE)
            .put("note", "Diagnostic emulator timings; not a 90 Hz device benchmark")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    vm.appearance("reduced", false)
                    vm.option("theme", "dark")
                    activity.setContent { StakApp(vm, destination.value) }
                    @Suppress("DEPRECATION")
                    report.put("displayRefreshHz", activity.window.decorView.display.refreshRate)
                        .put("requestedRefreshHz", activity.window.attributes.preferredRefreshRate)
                        .put("hardwareAccelerated", activity.window.decorView.isHardwareAccelerated)
                    activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
                }
                instrumentation.waitForIdleSync()
                Thread.sleep(1500)
                frames.clear(); render.clear(); layout.clear(); draw.clear(); delay.clear(); gpu.clear(); swap.clear()
                droppedReports.set(0)
                val width = app.resources.displayMetrics.widthPixels
                val height = app.resources.displayMetrics.heightPixels
                repeat(8) { index ->
                    val from = if (index < 4) .78f else .3f
                    val to = if (index < 4) .3f else .78f
                    ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                        "input swipe ${width / 2} ${(height * from).toInt()} ${width / 2} ${(height * to).toInt()} 350"
                    )).use { it.readBytes() }
                }
                instrumentation.waitForIdleSync()
                // Drain queued listener callbacks before taking a phase snapshot.
                val drained = java.util.concurrent.CountDownLatch(1)
                Handler(thread.looper).post { phase = navigation; drained.countDown() }
                check(drained.await(5, java.util.concurrent.TimeUnit.SECONDS))
                listOf("settings", "watchlist", "discover", "watchlist", "settings", "watchlist").forEach { route ->
                    scenario.onActivity { destination.value = route }
                    Thread.sleep(450)
                }
                scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            }
            fun summary(values: List<Long>): JSONObject {
                val sorted = synchronized(values) { values.map { it / 1_000_000.0 }.sorted() }
                assertTrue("FrameMetrics must collect real frames", sorted.isNotEmpty())
                fun percentile(p: Double) = sorted[((sorted.size - 1) * p).toInt()]
                return JSONObject().put("frames", sorted.size).put("medianMs", percentile(.5))
                    .put("p95Ms", percentile(.95)).put("p99Ms", percentile(.99))
                    .put("over11_11Ms", sorted.count { it > 1000.0 / 90 })
                    .put("over16_67Ms", sorted.count { it > 1000.0 / 60 })
                    .put("frameDurationMs", JSONArray(sorted))
            }
            report.put("watchlistScroll", summary(frames)).put("navigation", summary(navigation))
                .put("uiWork", summary(render)).put("layout", summary(layout)).put("draw", summary(draw))
                .put("unknownDelay", summary(delay))
                .put("swapBuffers", summary(swap))
            if (gpu.isNotEmpty()) report.put("gpu", summary(gpu))
            report.put("droppedMetricReports", droppedReports.get())
            val resolver = app.contentResolver
            val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "frame-timing-$variant.json")
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/STAK-native-metrics")
            }))
            requireNotNull(resolver.openOutputStream(uri)).bufferedWriter().use { it.write(report.toString(2)) }
        } finally {
            thread.quitSafely()
            runBlocking(Dispatchers.IO) { vm.repository.db.clearAllTables() }
        }
    }
}
