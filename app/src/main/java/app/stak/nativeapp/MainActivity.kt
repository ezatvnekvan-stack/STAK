package app.stak.nativeapp

import androidx.compose.foundation.shape.RoundedCornerShape
import android.os.Bundle
import dev.chrisbanes.haze.*
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import app.stak.nativeapp.ui.components.recordGlassSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.stak.nativeapp.ui.components.GlassBackdrop
import okhttp3.HttpUrl.Companion.toHttpUrl
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import app.stak.nativeapp.ui.components.LocalReducedMotion
import app.stak.nativeapp.ui.components.StakMotion
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stak.nativeapp.data.ApiClient
import app.stak.nativeapp.data.DatabaseProvider
import app.stak.nativeapp.data.MediaItem
import app.stak.nativeapp.data.sameIdentity
import app.stak.nativeapp.data.NativeRuntime
import app.stak.nativeapp.data.SettingsStore
import app.stak.nativeapp.data.TrackingRepository
import app.stak.nativeapp.data.db.TrackingEntity
import app.stak.nativeapp.ui.components.AmbientBackground
import app.stak.nativeapp.ui.components.rememberReducedMotion
import app.stak.nativeapp.ui.components.vibrate
import app.stak.nativeapp.ui.components.stakHaptic
import app.stak.nativeapp.ui.screens.AccountScreen
import app.stak.nativeapp.ui.screens.AccountViewModel
import app.stak.nativeapp.ui.screens.DiscoverScreen
import app.stak.nativeapp.ui.screens.DiscoverViewModel
import app.stak.nativeapp.ui.screens.HomeScreen
import app.stak.nativeapp.ui.screens.HomeViewModel
import app.stak.nativeapp.ui.screens.JellyfinViewModel
import app.stak.nativeapp.ui.screens.LibraryScreen
import app.stak.nativeapp.ui.screens.TitleScreen
import app.stak.nativeapp.ui.screens.TorBoxScreen
import app.stak.nativeapp.ui.screens.TrackingScreen
import app.stak.nativeapp.ui.screens.TrackingSettings
import app.stak.nativeapp.ui.screens.TrackingViewModel
import app.stak.nativeapp.ui.screens.WatchlistScreen
import app.stak.nativeapp.ui.screens.WatchlistViewModel
import app.stak.nativeapp.ui.theme.Lucide
import app.stak.nativeapp.ui.theme.StakAccent
import app.stak.nativeapp.ui.theme.StakBackground
import app.stak.nativeapp.ui.theme.StakBorder
import app.stak.nativeapp.ui.theme.StakSurface
import app.stak.nativeapp.ui.theme.StakText
import app.stak.nativeapp.ui.theme.StakTextFaint
import app.stak.nativeapp.ui.theme.StakTextMuted
import app.stak.nativeapp.ui.theme.StakTheme
import app.stak.nativeapp.ui.theme.SpaceMonoFont
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onResume(){
        super.onResume()
        window.decorView.post{app.stak.nativeapp.performance.requestSmoothDisplay(window)}
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        val cloudRestored=intent.getBooleanExtra("stak_cloud_restored",false)
        intent.removeExtra("stak_cloud_restored")
        super.onCreate(if(cloudRestored)null else savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        NativeRuntime.settings = SettingsStore(this)
        app.stak.nativeapp.notifications.ReminderNotifications.ensureChannel(this)
        app.stak.nativeapp.notifications.ReminderScheduler.apply(this, NativeRuntime.settings.dailyReminder)
        lifecycleScope.launch {
            runCatching { app.stak.nativeapp.data.removeRetiredAcquisition(applicationContext) }
        }
        app.stak.nativeapp.data.PublicMetadataCache.directory = java.io.File(cacheDir, "public-metadata")
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(this).okHttpClient {
            okhttp3.OkHttpClient.Builder().addNetworkInterceptor { chain ->
                val request = chain.request()
                val settings = NativeRuntime.settings
                val server = runCatching { okhttp3.HttpUrl.Companion.run { settings.jellyfinUrl?.toHttpUrl() } }.getOrNull()
                val matches = server != null && request.url.scheme == server.scheme && request.url.host == server.host && request.url.port == server.port && request.url.encodedPath.startsWith(server.encodedPath.trimEnd('/') + "/Items/")
                val builder = request.newBuilder().removeHeader("X-Emby-Token").removeHeader("X-Emby-Authorization").removeHeader("Authorization")
                if (matches) settings.jellyfinToken?.let { token -> app.stak.nativeapp.data.DirectJellyfin.authorizationHeaders(token).forEach { (name, value) -> builder.header(name, value) } }
                chain.proceed(builder.build())
            }.build()
        }.memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(.20).build() }
            .diskCache { coil.disk.DiskCache.Builder().directory(java.io.File(cacheDir,"artwork-v2")).maxSizeBytes(160L*1024*1024).build() }
            .crossfade(120).build())
        setContent { StakTheme { StakApp() } }
    }
}

sealed interface Dest {
    data object Home : Dest
    data object Discover : Dest
    data object Search : Dest
    data object Watchlist : Dest
    data object Library : Dest
    data object Tracking : Dest
    data object SeriesTracking : Dest
    data class SeriesCenter(val item:MediaItem) : Dest
    data object Account : Dest
    data object MediaCenter : Dest
    data object TorBox : Dest
    data class Title(val item: MediaItem, val sharedKey: String = "art-${item.mediaKey}") : Dest
}

internal data class NavTab(val dest: Dest, val label: String, val icon: ImageVector, val id: String)

private val navTabs = listOf(
    NavTab(Dest.Home, "Home", Lucide.House, "home"),
    NavTab(Dest.Discover, "Discover", Lucide.Compass, "discover"),
    NavTab(Dest.Watchlist, "Watchlist", Lucide.Bookmark, "watchlist"),
    NavTab(Dest.Tracking, "Tracking", Lucide.Tracking, "tracking"),
    NavTab(Dest.Library, "Library", Lucide.Library, "library"),
    NavTab(Dest.Account, "Settings", Lucide.Settings, "account"),
    NavTab(Dest.TorBox, "TorBox", Lucide.Database, "torbox")
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun StakApp() {
    val context = LocalContext.current
    val settings = remember(context) { SettingsStore(context) }
    NativeRuntime.settings = settings
    val cloud=remember(context){app.stak.nativeapp.data.CloudAccountController.get(context)}
    var recoveryReady by remember{mutableStateOf(false)}
    var recoveryError by remember{mutableStateOf(false)}
    var recoveryAttempt by remember{mutableIntStateOf(0)}
    LaunchedEffect(cloud,recoveryAttempt){
        recoveryError=false
        try{cloud.recoverPending();cloud.start();recoveryReady=true}
        catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
        catch(_:Exception){recoveryError=true}
    }
    if(!recoveryReady){
        Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center){
            Text(if(recoveryError)"Could not finish restoring your data."else "Opening STAK…",fontFamily=app.stak.nativeapp.ui.theme.HankenFont,color=StakText)
            if(recoveryError)app.stak.nativeapp.ui.components.StakButton("Retry",{recoveryAttempt++})
        }
        return
    }
    remember { StakAccent = Color(settings.accentColor.toInt()) }

    val hostContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val accountViewModel: AccountViewModel = viewModel { AccountViewModel(
        settings,
        onDailyReminder = { enabled ->
            app.stak.nativeapp.notifications.ReminderScheduler.apply(hostContext, enabled, reschedule = true)
        },
        onTestReminder = { app.stak.nativeapp.notifications.ReminderScheduler.sendTest(hostContext) }
    ) }
    var setup by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(!settings.setupComplete) }
    if(setup){
        androidx.compose.runtime.CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion(settings.reducedMotion)) {
            app.stak.nativeapp.ui.screens.SetupScreen(settings,accountViewModel.services,onFinish={settings.finishSetup();accountViewModel.setReducedMotion(settings.reducedMotion);accountViewModel.setAmbient(settings.ambientEnabled);setup=false})
        }
        return
    }
    val homeViewModel: HomeViewModel = viewModel()
    val discoverViewModel: DiscoverViewModel = viewModel()
    val searchResultsViewModel: DiscoverViewModel = viewModel(key="global-search")
    val watchlistViewModel: WatchlistViewModel = viewModel {
        WatchlistViewModel(DatabaseProvider.get(context).watchlistDao())
    }
    val jellyfinViewModel: JellyfinViewModel = viewModel()
    val trackingViewModel: TrackingViewModel = viewModel()
    val personalTracker = remember(context) {app.stak.nativeapp.data.TrackerStore(DatabaseProvider.get(context))}
    val trackingRepository = remember(context) {
        TrackingRepository(DatabaseProvider.get(context).trackingDao(),personalTracker)
    }
    val scope = rememberCoroutineScope()

    val watchlist by watchlistViewModel.items.collectAsState()
    val watchlistBusy by watchlistViewModel.busy.collectAsState()

    val navigation by accountViewModel.navigation.collectAsState()
    val visibleTabs = navigation.mapNotNull { id -> navTabs.find { it.id == id } }
    val torboxKey by accountViewModel.torboxKey.collectAsState()
    val jellyfinUrl by accountViewModel.jellyfinUrl.collectAsState()
    val jellyfinConnected by accountViewModel.jellyfinConnected.collectAsState()
    val reducedOverride by accountViewModel.reducedMotion.collectAsState()
    val haptics by accountViewModel.haptics.collectAsState()
    val ambient by accountViewModel.ambient.collectAsState()
    val backgroundStyle by accountViewModel.backgroundStyle.collectAsState()
    val allLocalTracking by trackingRepository.observe().collectAsState(initial = emptyList())
    val journalScope=if(settings.jellyfinConnected)app.stak.nativeapp.data.jellyfinScope(settings) else "local"
    LaunchedEffect(personalTracker,journalScope){trackingViewModel.attach(personalTracker,journalScope)}
    LaunchedEffect(allLocalTracking){trackingViewModel.importLegacy(allLocalTracking)}
    val jellyfinItems by jellyfinViewModel.items.collectAsState()
    val jellyfinResume by jellyfinViewModel.resume.collectAsState()
    val observedPersonal by trackingViewModel.personal.collectAsState()
    val observedRemote by trackingViewModel.remote.collectAsState()
    val libraryObservedAt=remember(jellyfinItems){System.currentTimeMillis()}
    val resumeObservedAt=remember(jellyfinResume){System.currentTimeMillis()}
    val serverStates=remember(observedPersonal.remote,observedRemote,jellyfinItems,jellyfinResume,journalScope){app.stak.nativeapp.data.serverTrackingStates(journalScope,observedPersonal.remote+observedRemote+app.stak.nativeapp.data.serverTrackingStates(journalScope,emptyList(),jellyfinResume,resumeObservedAt)+app.stak.nativeapp.data.serverTrackingStates(journalScope,emptyList(),jellyfinItems,libraryObservedAt))}
    val localTracking=remember(serverStates){serverStates.map{entry->app.stak.nativeapp.data.trackingEntity(entry.media,null,entry.played,entry.watchedAt?:0,entry.scope).copy(progress=entry.media.progress,lastPlayed=entry.watchedAt,updatedAt=entry.updatedAt)}}
    val jellyfinResumeLoading by jellyfinViewModel.resumeLoading.collectAsState()
    val jellyfinResumeError by jellyfinViewModel.resumeError.collectAsState()
    val jellyfinSyncMessage by jellyfinViewModel.syncMessage.collectAsState()
    val credentialRevision by accountViewModel.credentialRevision.collectAsState()
    LaunchedEffect(credentialRevision){jellyfinViewModel.refresh();trackingViewModel.refresh();runCatching{app.stak.nativeapp.data.LibraryApprovals.sync()}}
    app.stak.nativeapp.ui.screens.MediaSyncHost(jellyfinViewModel,trackingViewModel,credentialRevision)
    LaunchedEffect(localTracking, jellyfinItems) {
        discoverViewModel.setKnownTitles(localTracking.filter { it.played && it.type != "episode" }.map { it.id }.toSet(), jellyfinItems.map { it.mediaKey }.toSet())
        searchResultsViewModel.setKnownTitles(localTracking.filter { it.played && it.type != "episode" }.map { it.id }.toSet(), jellyfinItems.map { it.mediaKey }.toSet())
    }
    val localItems = remember(localTracking) {
        localTracking.filter { it.type != "episode" && (it.played||it.progress>0f) }.map { entry ->
            MediaItem(
                id = entry.id,
                imdbId = entry.id,
                type = entry.type,
                name = entry.name,
                progress = entry.progress,
                watched = entry.played
            )
        }
    }
    val reduced = rememberReducedMotion(reducedOverride)
    val duration = if (reduced) 100 else StakMotion.Standard
    val screenStates=rememberSaveableStateHolder()
    var stack by remember { mutableStateOf(listOf<Dest>(Dest.Home)) }
    var direction by remember { mutableIntStateOf(1) }
    var searchSeed by remember { mutableStateOf("") }
    var accountSection by remember { mutableStateOf<String?>(null) }
    val current = stack.last()
    val rootView=androidx.compose.ui.platform.LocalView.current

    fun tap() {
        if (haptics) rootView.stakHaptic(app.stak.nativeapp.ui.components.Haptic.Tap)
    }

    fun push(dest: Dest) {
        direction = 1
        stack = stack + dest
    }

    fun pop() {
        if (stack.size > 1) {
            direction = -1
            stack = stack.dropLast(1)
        }
    }

    fun switchTab(dest: Dest) {
        if (current == dest) return
        val from=visibleTabs.indexOfFirst{it.dest==current}
        val to=visibleTabs.indexOfFirst{it.dest==dest}
        direction=if(from>=0&&to>=0&&to<from)-1 else 1
        stack = listOf(dest)
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    // Poster state used to be resolved by scanning Jellyfin, tracking and watchlist for
    // every visible poster on every recomposition. The index answers each poster with
    // hash lookups; it is rebuilt only when one of those collections actually changes.
    val posterIndex=remember(jellyfinItems,localTracking,watchlist){
        app.stak.nativeapp.data.PosterIdentityIndex(jellyfinItems,localTracking,watchlist)
    }
    val settingsJellyfinConnected=settings.jellyfinConnected
    // Derived watched sets were rebuilt on every recomposition of StakApp (which fires on
    // every sync, watchlist and navigation change). Cache them per tracking emit instead.
    val playedIds=remember(localTracking){localTracking.filter{it.played}.map{it.id}.toSet()}
    val watchedIds=remember(localTracking){localTracking.filter{it.played&&it.type!="episode"}.map{it.id}.toSet()}
    val titleStates=remember(localTracking){localTracking.filter{it.type!="episode"}.associate{it.id to it.played}}
    val episodeStates=remember(localTracking){localTracking.filter{it.type=="episode"}.associate{it.id to it.played}}
    val unwatchedIds=remember(localTracking){localTracking.filter{!it.played&&it.type!="episode"}.map{it.id}.toSet()}
    val watchIds=remember(watchlist){watchlist.map{it.mediaKey}.toSet()}
    val libraryWatchedIds=remember(jellyfinItems,watchedIds,unwatchedIds){
        (jellyfinItems.asSequence().filter{it.watched}.map{it.mediaKey}.toSet()+watchedIds)-unwatchedIds
    }
    // PosterActions lives in a static CompositionLocal: handing it a new instance on
    // every recomposition rebuilt the whole screen tree. Remember it so unrelated
    // state changes (navigation, sync, toasts) stop recomposing every poster.
    val posterActions=remember(posterIndex,jellyfinItems,settingsJellyfinConnected,settings,haptics,rootView){
    app.stak.nativeapp.ui.components.PosterActions(
        isWatchlisted=posterIndex::isWatchlisted,
        isWatched=posterIndex::isWatched,
        isInJellyfin={media->settingsJellyfinConnected&&posterIndex.isInJellyfin(media)},
        toggleWatchlist={media->
            if(haptics)rootView.stakHaptic(if(posterIndex.isWatchlisted(media))app.stak.nativeapp.ui.components.Haptic.Tap else app.stak.nativeapp.ui.components.Haptic.Confirm)
            watchlistViewModel.toggle(media)
        },

    )
    }
    val backdrop=rememberGraphicsLayer()
    val glassSource=remember(backdrop){app.stak.nativeapp.ui.components.GlassSource(backdrop,crossWindow=true,hazeState=HazeState()).also{it.ownerView=rootView}}
    val glyphSignals=remember{app.stak.nativeapp.ui.components.GlyphSignals()}
    val glyphLayout=when(current){
        Dest.Home -> app.stak.nativeapp.ui.components.GlyphLayout.Home
        is Dest.Title -> app.stak.nativeapp.ui.components.GlyphLayout.Detail
        else -> app.stak.nativeapp.ui.components.GlyphLayout.General
    }
    val cinemaMotion=app.stak.nativeapp.ui.components.rememberCinematicMotion()
    val cinemaPalette=remember{mutableStateOf<String?>(null)}
    val ambientLayer=rememberGraphicsLayer()
    // Keep the original static glass capture. Glyph records only its small vector
    // background when a glass consumer requests it, including brief light signals.
    val inlineSource=remember(ambientLayer,ambient,backgroundStyle){app.stak.nativeapp.ui.components.GlassSource(ambientLayer,preBlurred=true,static=backgroundStyle!=app.stak.nativeapp.data.BackgroundStyle.Glyph)}
    // Predictive back: while the system back gesture runs, the screen recedes with
    // the gesture instead of waiting for the finger to lift.
    var backProgress by remember { mutableFloatStateOf(0f) }
    // The recede layer is only attached during a gesture: a permanent graphicsLayer
    // around the whole app costs a full-screen render layer on every frame.
    var backGestureActive by remember { mutableStateOf(false) }
    if (stack.size > 1) {
        androidx.activity.compose.PredictiveBackHandler(true) { progress ->
            backGestureActive = true
            try {
                progress.collect { event -> backProgress = event.progress }
                pop()
            } finally {
                backProgress = 0f
                backGestureActive = false
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(app.stak.nativeapp.ui.components.LocalPosterActions provides posterActions, LocalReducedMotion provides reduced,app.stak.nativeapp.ui.components.LocalGlassSource provides glassSource,app.stak.nativeapp.ui.components.LocalInlineGlassSource provides inlineSource,app.stak.nativeapp.ui.components.LocalCinematicMotion provides cinemaMotion,app.stak.nativeapp.ui.components.LocalCinematicPalette provides cinemaPalette,app.stak.nativeapp.ui.components.LocalCinematicHome provides (current==Dest.Home),app.stak.nativeapp.ui.components.LocalCinematicEnabled provides ambient,app.stak.nativeapp.ui.components.LocalBackgroundStyle provides backgroundStyle,app.stak.nativeapp.ui.components.LocalGlyphLayout provides glyphLayout,app.stak.nativeapp.ui.components.LocalGlyphRoute provides current,app.stak.nativeapp.ui.components.LocalGlyphSignals provides glyphSignals) {
    Box(modifier = Modifier.fillMaxSize().nestedScroll(cinemaMotion.connection).background(StakBackground).then(
        if (!reduced && backGestureActive) Modifier.graphicsLayer {
            // Follow the system back gesture: the screen recedes while the finger drags.
            val ease = backProgress * backProgress
            scaleX = 1f - .04f * ease
            scaleY = scaleX
            translationX = 18.dp.toPx() * ease
        } else Modifier
    )) {
        Box(Modifier.fillMaxSize().onGloballyPositioned{coordinates->val location=IntArray(2);rootView.getLocationOnScreen(location);glassSource.origin.value=androidx.compose.ui.geometry.Offset(location[0].toFloat(),location[1].toFloat())+coordinates.positionInRoot()}.drawWithContent {
            if(glassSource.requests.intValue==0)drawContent() else {
                backdrop.record { this@drawWithContent.drawContent() }
                drawLayer(backdrop)
                glassSource.changed()
            }
        }.haze(glassSource.hazeState!!)) {
        Box(Modifier.fillMaxSize().recordGlassSource(inlineSource,rootView).background(StakBackground)) {
            if(backgroundStyle==app.stak.nativeapp.data.BackgroundStyle.Glyph)Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()){app.stak.nativeapp.ui.components.GlyphBackground()}
            else if(ambient)Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()){AmbientBackground()}
        }
        SharedTransitionLayout {
            val sharedScope = this
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    if (direction > 0) {
                        (slideInHorizontally(tween(duration,easing=StakMotion.Ease)) { if(reduced)0 else it / 12 } + fadeIn(tween(duration,easing=StakMotion.Ease))) togetherWith
                            (slideOutHorizontally(tween(duration,easing=StakMotion.Ease)) { if(reduced)0 else -it / 12 } + fadeOut(tween(duration,easing=StakMotion.Ease)))
                    } else {
                        (slideInHorizontally(tween(duration,easing=StakMotion.Ease)) { if(reduced)0 else -it / 12 } + fadeIn(tween(duration,easing=StakMotion.Ease))) togetherWith
                            (slideOutHorizontally(tween(duration,easing=StakMotion.Ease)) { if(reduced)0 else it / 12 } + fadeOut(tween(duration,easing=StakMotion.Ease)))
                    }
                },
                label = "screen"
            ) { dest ->
                val animatedScope = this
                val stateKey=if(dest is Dest.Title)"title:${dest.item.type}:${dest.item.mediaKey}:${dest.sharedKey}" else if(dest is Dest.SeriesCenter)"series-center:${dest.item.mediaKey}" else dest.javaClass.simpleName
                screenStates.SaveableStateProvider(stateKey) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if(dest==Dest.Home||dest is Dest.Title)Modifier else Modifier.statusBarsPadding())
                ) {
                    when (dest) {
                        Dest.Home -> HomeScreen(
                            onOpenTitle = { item, key ->
                                tap()
                                push(Dest.Title(item, key))
                            },
                            onSearch = { query ->
                                searchSeed = query
                                searchResultsViewModel.search(query)
                                push(Dest.Search)
                            },
                            onOpenAccount = { switchTab(Dest.Account) },
                            onOpenLibrary = { switchTab(Dest.Library) },
                            onOpenWatchlist = { switchTab(Dest.Watchlist) },
                            onBrowseSection = { id ->
                                app.stak.nativeapp.data.HomeSections.defaults().find { it.id == id }?.let { section ->
                                    searchSeed = ""
                                    searchResultsViewModel.browse(section)
                                    push(Dest.Search)
                                }
                            },
                            continueWatching = jellyfinResume,
                            jellyfinConnected = jellyfinConnected,
                            continueLoading = jellyfinResumeLoading,
                            continueError = jellyfinResumeError,
                            onRetryContinue = {jellyfinViewModel.refresh()},
                            torboxConnected = torboxKey.isNotBlank(),
                            watchlist = watchlist,
                            knownTitles = jellyfinItems,
                            sharedScope = sharedScope,
                            animatedScope = animatedScope,
                            reducedMotion = reduced,
                            viewModel = homeViewModel,
                            onOpenTrakt = { accountSection = "metadata"; switchTab(Dest.Account) }
                        ).also { LaunchedEffect(Unit) { homeViewModel.refresh() } }

                        Dest.Search -> app.stak.nativeapp.ui.screens.SearchScreen(
                            viewModel = searchResultsViewModel,
                            onBack = { pop() },
                            onOpenTitle = { item, key -> push(Dest.Title(item, key)) },
                            watchIds = watchIds,
                            sharedScope = sharedScope,
                            animatedScope = animatedScope
                        )

                        Dest.Discover -> DiscoverScreen(
                            onOpenTitle = { item, key ->
                                tap()
                                push(Dest.Title(item, key))
                            },
                            sharedScope = sharedScope,
                            animatedScope = animatedScope,
                            initialQuery = "",
                            watchIds = watchIds,
                            viewModel = discoverViewModel
                        )

                        Dest.Watchlist -> WatchlistScreen(
                            onOpenTitle = { item, key ->
                                tap()
                                push(Dest.Title(item, key))
                            },
                            onBrowseDiscover = { switchTab(Dest.Discover) },
                            viewModel = watchlistViewModel,
                            watchedIds = libraryWatchedIds,
                            sharedScope = sharedScope,
                            animatedScope = animatedScope
                        )

                        Dest.Library -> LibraryScreen(
                            onOpenTitle = { item, key ->
                                tap()
                                push(Dest.Title(item, key))
                            },
                            onOpenTorBox = { switchTab(Dest.TorBox) },
                            onOpenAutoAdd = { switchTab(Dest.SeriesTracking) },
                            onMediaCenter = {push(Dest.MediaCenter)},
                            connected = jellyfinConnected,
                            viewModel = jellyfinViewModel,
                            onOpenAccount = { switchTab(Dest.Account) },
                            sharedScope = sharedScope,
                            animatedScope = animatedScope
                        )

                        Dest.Tracking -> TrackingScreen(
                            onOpenTitle = { item -> push(Dest.Title(item)) },
                            viewModel = trackingViewModel,
                            syncNotice = null,
                            onRetrySync = {jellyfinViewModel.refresh();trackingViewModel.refresh()},
                            onPlanToWatch = {media->if(watchlist.none{it.sameIdentity(media)})watchlistViewModel.toggle(media)},
                            localEntries = localTracking,
                            followed = watchlist,
                            continueWatching = jellyfinResume,
                            knownLibrary = jellyfinItems,
                            onOpenAccount = {accountSection="jellyfin";switchTab(Dest.Account)}
                        )

                        Dest.SeriesTracking -> app.stak.nativeapp.ui.screens.AutoAddOverviewScreen(onLibrary={switchTab(Dest.Library)},onTorBox={switchTab(Dest.TorBox)},onSettings={accountSection="tracking";switchTab(Dest.Account)},onMediaCenter={push(Dest.MediaCenter)},onOpenTitle={push(Dest.Title(it))},knownSeries=jellyfinItems)

                        is Dest.SeriesCenter -> app.stak.nativeapp.ui.screens.SeriesCenterScreen(
                            onLibrary={switchTab(Dest.Library)},onTorBox={switchTab(Dest.TorBox)},
                            onSettings={accountSection="tracking";switchTab(Dest.Account)},
                            onMediaCenter={push(Dest.MediaCenter)},onOpenTitle={push(Dest.Title(it))},knownSeries=jellyfinItems,
                            onSourceSettings={accountSection="account";switchTab(Dest.Account)},onRefreshLibrary={jellyfinViewModel.refresh()},initialSeries=(dest as? Dest.SeriesCenter)?.item,onExit={pop()},
                            libraryBusy=jellyfinViewModel.loading.collectAsState().value,libraryIssue=jellyfinViewModel.error.collectAsState().value)

                        Dest.TorBox -> TorBoxScreen(
                            onOpenTitle = { item -> push(Dest.Title(item)) },
                            onOpenLibrary = { switchTab(Dest.Library) },
                            onOpenAutoAdd = { switchTab(Dest.SeriesTracking) },
                            torboxKey = torboxKey,
                            onOpenAccount = { accountSection = "account"; switchTab(Dest.Account) },
                            onMediaCenter = {push(Dest.MediaCenter)}
                        )

                        Dest.Account -> AccountScreen(viewModel = accountViewModel, initialSection = accountSection, onSetup={setup=true},onMediaCenter={push(Dest.MediaCenter)},onOpenAutoAdd={switchTab(Dest.SeriesTracking)})
                        Dest.MediaCenter -> app.stak.nativeapp.ui.screens.MediaCenterScreen(onBack={pop()},connections=accountViewModel.services)

                        is Dest.Title -> {
                            val watched = dest.item.mediaKey in playedIds
                            TitleScreen(
                                item = dest.item,
                                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(key="detail:$journalScope:${dest.item.type}:${dest.item.mediaKey}"),
                                onBack = { pop() },
                                isWatchlisted = posterIndex.isWatchlisted(dest.item),
                                watchlistBusy = watchlistBusy,
                                onToggleWatchlist = { media ->
                                    tap()
                                    watchlistViewModel.toggle(media)
                                },
                                sharedScope = sharedScope,
                                animatedScope = animatedScope,
                                sharedKey = dest.sharedKey,
                                isWatched = watched,
                                watchedIds = watchedIds,
                                titleStates = titleStates,
                                episodeStates = episodeStates,
                                onOpenTitle = { push(Dest.Title(it)) },
                                onOpenSourceSettings = {accountSection="account";switchTab(Dest.Account)},
                                onOpenSettings = {accountSection="tracking";switchTab(Dest.Account)},
                                onOpenTorbox = {push(Dest.TorBox)},
                                onOpenSeriesCenter = {push(Dest.SeriesCenter(it))},
                                syncNotice = null,

                            )
                        }
                    }
                }
                }
            }
        }

        }
        AnimatedVisibility(
            visible = current !is Dest.Title && current != Dest.MediaCenter,
            enter = fadeIn(tween(if(reduced)0 else 180)) + slideInVertically(tween(if(reduced)0 else 240,easing=StakMotion.Ease)) { it / 2 },
            exit = fadeOut(tween(if(reduced)0 else 120)) + slideOutVertically(tween(if(reduced)0 else 180,easing=StakMotion.Ease)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            StakBottomBar(
                current = current,
                backdrop = backdrop,
                tabs = visibleTabs,
                onSelect = { tab ->
                    tap()
                    if (tab == Dest.Account) accountSection = null
                    // Returning to Discover preserves its query, filters and scroll position.
                    switchTab(tab)
                }
            )
        }
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()).background(Color(0xFF08090B)))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()).background(Color(0xFF08090B)))
        if (backGestureActive) {
            // Drawn, not composed: the scrim follows the gesture without recomposing the app.
            Box(Modifier.fillMaxSize().drawBehind {
                drawRect(Color.Black, alpha = .26f * backProgress)
            })
        }
    }
}

}

@Composable
internal fun StakBottomBar(current: Dest, backdrop: androidx.compose.ui.graphics.layer.GraphicsLayer, tabs: List<NavTab>, onSelect: (Dest) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(22.dp))) {
    GlassBackdrop(backdrop,Modifier.matchParentSize())
    val shape = RoundedCornerShape(22.dp)
    val activeDestination=tabs.find{it.dest==current}?.dest ?: if(current==Dest.TorBox||current==Dest.SeriesTracking||current is Dest.SeriesCenter)Dest.Library else current
    val activeIndex=tabs.indexOfFirst{it.dest==activeDestination}
    val cell=(maxWidth-8.dp)/tabs.size.coerceAtLeast(1)
    val indicator=androidx.compose.animation.core.animateDpAsState(4.dp+cell*activeIndex.coerceAtLeast(0)+(cell-4.dp)/2,if(LocalReducedMotion.current)androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(dampingRatio=.9f,stiffness=600f),label="navigation-dot")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(Color.Transparent)
            .border(1.dp, Color(0x29FFFFFF), shape)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val active = activeDestination == tab.dest
            val lift=androidx.compose.animation.core.animateFloatAsState(if(active)1f else 0f,if(LocalReducedMotion.current)androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(dampingRatio=.72f,stiffness=650f),label="navigation-icon-lift")
            val activeTint by animateColorAsState(if(active)Color.White else Color(0xFFA3A3A3),tween(if(LocalReducedMotion.current)0 else StakMotion.Quick),label="tab-ink")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(62.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(selected=active,role=androidx.compose.ui.semantics.Role.Tab){onSelect(tab.dest)}
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)
            ) {
                Box(
                    modifier = Modifier
                        .offset(y = (-2).dp)
                        .size(width = 36.dp, height = 30.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = activeTint,
                        modifier = Modifier.size(19.dp).graphicsLayer{translationY=-2.dp.toPx()*lift.value;scaleX=1f+.08f*lift.value;scaleY=scaleX}
                    )
                }
                Text(
                    tab.label.uppercase(),
                    fontFamily = SpaceMonoFont,
                    fontWeight = FontWeight.Normal,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    letterSpacing = (-0.36).sp,
                    color = activeTint,
                    maxLines = 1
                )
            }
        }
    }
    if(activeIndex>=0)Box(Modifier.offset(y=34.dp).size(4.dp).graphicsLayer{translationX=indicator.value.toPx()}.clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFE31C2F)).testTag("navigation-active-dot"))
}
}
