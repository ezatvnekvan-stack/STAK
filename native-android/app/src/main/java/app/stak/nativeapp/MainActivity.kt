@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.stak.nativeapp

import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument

val DotFont = FontFamily(Font(R.font.doto, FontWeight.Bold))
val BodyFont = FontFamily(Font(R.font.hanken_grotesk))
val MonoFont = FontFamily(Font(R.font.space_mono))
val LocalShared = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalDestination = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { StakApp() }
    }
}

@Composable
fun StakApp(vm: StakViewModel = viewModel()) {
    val context = LocalContext.current
    val saved by vm.saved.collectAsStateWithLifecycle()
    val dark by vm.dark.collectAsStateWithLifecycle()
    val reduced by vm.reduced.collectAsStateWithLifecycle()
    val haptics by vm.haptics.collectAsStateWithLifecycle()
    var systemReduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { systemReduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val policy = MotionPreferences(reduced || systemReduced, haptics)
    val colors = if (dark) darkColorScheme(primary = Color(0xfff2f2f2), onPrimary = Color(0xff111111), background = Color(0xff0a0a0a), surface = Color(0xff111111), surfaceContainer = Color(0xff171717), surfaceContainerHighest = Color(0xff303030), onSurface = Color(0xffeeeeee), onSurfaceVariant = Color(0xffaaaaaa), outlineVariant = Color(0xff303030))
    else lightColorScheme(primary = Color(0xff171717), onPrimary = Color.White, background = Color(0xfff7f7f5), surface = Color.White, surfaceContainer = Color(0xffefefed), surfaceContainerHighest = Color(0xffddddda), onSurface = Color(0xff111111), onSurfaceVariant = Color(0xff555555), outlineVariant = Color(0xffddddda))
    MaterialTheme(colorScheme = colors, typography = Typography(
        displaySmall = TextStyle(fontFamily = DotFont, fontSize = 36.sp, lineHeight = 42.sp),
        headlineLarge = TextStyle(fontFamily = DotFont, fontSize = 32.sp, lineHeight = 38.sp),
        headlineSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 24.sp),
        titleLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 16.sp),
        bodyLarge = TextStyle(fontFamily = BodyFont, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = BodyFont, fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontFamily = BodyFont, fontSize = 12.sp),
        labelLarge = TextStyle(fontFamily = MonoFont, fontSize = 11.sp, letterSpacing = .5.sp),
        labelMedium = TextStyle(fontFamily = MonoFont, fontSize = 10.sp),
        labelSmall = TextStyle(fontFamily = MonoFont, fontSize = 9.sp)
    )) {
        CompositionLocalProvider(LocalStakMotion provides policy) {
            val nav = rememberNavController()
            val snackbar = remember { SnackbarHostState() }
            val entry by nav.currentBackStackEntryAsState()
            val route = entry?.destination?.route ?: "home"
            val bare = route.startsWith("title/")
            val duration = if (policy.reduced) 0 else 240
            LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
            LaunchedEffect(Unit) {
                val activity = context as? ComponentActivity
                if (activity?.intent?.getStringExtra("destination") == "watchlist") nav.navigate("watchlist")
            }
            fun open(media: Media, source: String) {
                vm.repository.remember(media)
                nav.navigate("title/${android.net.Uri.encode(media.key)}/${android.net.Uri.encode(source)}") { launchSingleTop = true }
            }
            Scaffold(containerColor = colors.background,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!bare) {
                        val tabs = listOf("home", "discover", "watchlist", "tracking", "library", "settings")
                        val selected = tabs.indexOf(route).coerceAtLeast(0)
                        Surface(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), shape = RoundedCornerShape(28.dp), color = colors.surfaceContainer, border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)) {
                            MotionNavigation(selected) { index ->
                                nav.navigate(tabs[index]) { popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true }
                            }
                        }
                    }
                }) { padding ->
                SharedTransitionLayout(Modifier.fillMaxSize().padding(padding)) {
                    CompositionLocalProvider(LocalShared provides this) {
                        NavHost(nav, "home", enterTransition = { fadeIn(tween(duration)) + slideInHorizontally(tween(duration)) { it / 35 } },
                            exitTransition = { fadeOut(tween(duration)) }, popEnterTransition = { fadeIn(tween(duration)) },
                            popExitTransition = { fadeOut(tween(duration)) + slideOutHorizontally(tween(duration)) { it / 35 } }) {
                            composable("home") { CompositionLocalProvider(LocalDestination provides this) { HomeScreen(vm, saved, ::open) { nav.navigate("discover") } } }
                            composable("discover") { CompositionLocalProvider(LocalDestination provides this) { DiscoverScreen(vm, ::open) } }
                            composable("watchlist") { CompositionLocalProvider(LocalDestination provides this) { CollectionScreen("Watchlist", saved.filter { it.collection == "watchlist" }.map { it.media() }, "watchlist", ::open) } }
                            composable("tracking") { CompositionLocalProvider(LocalDestination provides this) { TrackingScreen(vm, saved, ::open) } }
                            composable("library") { CompositionLocalProvider(LocalDestination provides this) { LibraryScreen(vm, ::open) } }
                            composable("settings") { SettingsScreen(vm) }
                            composable("title/{key}/{source}", arguments = listOf(navArgument("key") { type = NavType.StringType }, navArgument("source") { type = NavType.StringType })) { backStack ->
                                CompositionLocalProvider(LocalDestination provides this) {
                                    val key = backStack.arguments?.getString("key").orEmpty()
                                    DetailScreen(vm, key, backStack.arguments?.getString("source").orEmpty(), saved, snackbar) { nav.popBackStack() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MotionNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val icons = listOf(Icons.Outlined.Home, Icons.Outlined.Explore, Icons.Outlined.BookmarkBorder, Icons.Outlined.BarChart, Icons.Outlined.Storage, Icons.Outlined.Tune)
    val labels = listOf("Home", "Discover", "Watchlist", "Tracking", "Library", "Settings")
    BoxWithConstraints(Modifier.fillMaxWidth().height(66.dp)) {
        val width = maxWidth / labels.size
        val x = animateDpAsState(width * selected, if (LocalStakMotion.current.reduced) snap() else spring(dampingRatio = .95f, stiffness = 650f), label = "Navigation glide")
        Box(Modifier.offset { IntOffset(x.value.roundToPx(), 0) }.width(width).fillMaxHeight().padding(4.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(24.dp)))
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                NavigationBarItem(selected == index, { onSelect(index) }, icon = { Icon(icons[index], label, Modifier.size(20.dp)) },
                    label = { Text(label, fontSize = 8.sp, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent), modifier = Modifier.weight(1f))
            }
        }
    }
}
