@file:OptIn(
    androidx.compose.ui.text.ExperimentalTextApi::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package app.stak.nativeapp

import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument

val DotFont =
    FontFamily(
        Font(
            R.font.doto,
            FontWeight.Black,
            variationSettings =
                androidx.compose.ui.text.font.FontVariation.Settings(
                    androidx.compose.ui.text.font.FontVariation.weight(900)
                ),
        )
    )
val SectionFont =
    FontFamily(
        Font(
            R.font.doto,
            FontWeight.ExtraBold,
            variationSettings =
                androidx.compose.ui.text.font.FontVariation.Settings(
                    androidx.compose.ui.text.font.FontVariation.weight(800)
                ),
        )
    )
val BodyFont =
    FontFamily(
        *listOf(
                FontWeight.Normal,
                FontWeight.Medium,
                FontWeight.SemiBold,
                FontWeight.Bold,
                FontWeight.ExtraBold,
                FontWeight.Black,
            )
            .map { weight ->
                Font(
                    R.font.hanken_grotesk,
                    weight,
                    variationSettings =
                        androidx.compose.ui.text.font.FontVariation.Settings(
                            androidx.compose.ui.text.font.FontVariation.weight(weight.weight)
                        ),
                )
            }
            .toTypedArray()
    )
val MonoFont = FontFamily(Font(R.font.space_mono), Font(R.font.space_mono_bold, FontWeight.Bold))
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
fun StakApp(vm: StakViewModel = viewModel(), requestedDestination: String? = null) {
    val context = LocalContext.current
    val saved by vm.saved.collectAsStateWithLifecycle()
    val savedDark by vm.dark.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
    val tone by vm.artworkTone.collectAsStateWithLifecycle()
    val dark =
        if (options["theme"] == "system") androidx.compose.foundation.isSystemInDarkTheme()
        else savedDark
    val reduced by vm.reduced.collectAsStateWithLifecycle()
    val haptics by vm.haptics.collectAsStateWithLifecycle()
    var systemReduced by remember {
        mutableStateOf(
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        )
    }
    DisposableEffect(context) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    systemReduced =
                        Settings.Global.getFloat(
                            context.contentResolver,
                            Settings.Global.ANIMATOR_DURATION_SCALE,
                            1f,
                        ) == 0f
                }
            }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val policy = MotionPreferences(reduced || systemReduced, haptics)
    val baseColors =
        if (dark)
            darkColorScheme(
                primary = Color(0xfff2f2f2),
                onPrimary = Color(0xff111111),
                background = if (options["amoled"] == "true") Color.Black else Color(0xff080808),
                surface = Color(0xff111111),
                surfaceContainer = Color(0xff171717),
                surfaceContainerHighest = Color(0xff303030),
                onSurface = Color.White,
                onSurfaceVariant = Color(0xffa3a3a3),
                outlineVariant = Color(0xff303030),
            )
        else
            lightColorScheme(
                primary = Color(0xff171717),
                onPrimary = Color.White,
                background = Color(0xfff4f4f2),
                surface = Color.White,
                surfaceContainer = Color(0xffefefed),
                surfaceContainerHighest = Color(0xffddddda),
                onSurface = Color(0xff111111),
                onSurfaceVariant = Color(0xff666666),
                outlineVariant = Color(0xffddddda),
            )
    val colors =
        baseColors.copy(
            primaryContainer = baseColors.surfaceContainerHighest,
            onPrimaryContainer = baseColors.onSurface,
            secondary = baseColors.primary,
            onSecondary = baseColors.onPrimary,
            secondaryContainer = baseColors.surfaceContainerHighest,
            onSecondaryContainer = baseColors.onSurface,
            tertiary = baseColors.primary,
            onTertiary = baseColors.onPrimary,
            tertiaryContainer = baseColors.surfaceContainerHighest,
            onTertiaryContainer = baseColors.onSurface,
            outline = if (dark) Color(0xff777777) else Color(0xff888888),
        )
    DisposableEffect(dark, context) {
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle =
                if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ),
            navigationBarStyle =
                if (dark) SystemBarStyle.dark(android.graphics.Color.BLACK)
                else
                    SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.BLACK),
        )
        onDispose {}
    }
    MaterialTheme(
        colorScheme = colors,
        typography =
            Typography(
                displaySmall =
                    TextStyle(
                        fontFamily = DotFont,
                        fontSize = 36.sp,
                        lineHeight = 42.sp,
                        letterSpacing = .72.sp,
                    ),
                headlineLarge =
                    TextStyle(
                        fontFamily = DotFont,
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp,
                        lineHeight = 35.2.sp,
                        letterSpacing = (-.045).em,
                    ),
                headlineSmall =
                    TextStyle(
                        fontFamily = DotFont,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        letterSpacing = .48.sp,
                    ),
                titleLarge =
                    TextStyle(
                        fontFamily = BodyFont,
                        letterSpacing = 0.sp,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                titleMedium =
                    TextStyle(
                        fontFamily = BodyFont,
                        letterSpacing = 0.sp,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                    ),
                bodyLarge =
                    TextStyle(
                        fontFamily = BodyFont,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        letterSpacing = 0.sp,
                    ),
                bodyMedium =
                    TextStyle(
                        fontFamily = BodyFont,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        letterSpacing = 0.sp,
                    ),
                bodySmall =
                    TextStyle(fontFamily = BodyFont, fontSize = 12.sp, letterSpacing = 0.sp),
                labelLarge =
                    TextStyle(fontFamily = MonoFont, fontSize = 10.sp, letterSpacing = .4.sp),
                labelMedium =
                    TextStyle(fontFamily = MonoFont, fontSize = 10.sp, letterSpacing = 0.sp),
                labelSmall = TextStyle(fontFamily = MonoFont, fontSize = 9.sp, letterSpacing = 0.sp),
            ),
    ) {
        CompositionLocalProvider(LocalStakMotion provides policy, LocalOptions provides options) {
            val nav = rememberNavController()
            val snackbar = remember { SnackbarHostState() }
            val entry by nav.currentBackStackEntryAsState()
            val route = entry?.destination?.route ?: "home"
            val bare = route.startsWith("title/")
            val duration = if (policy.reduced) 0 else 220
            LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
            LaunchedEffect(requestedDestination) {
                if (!requestedDestination.isNullOrBlank())
                    nav.navigate(requestedDestination) { launchSingleTop = true }
            }
            LaunchedEffect(Unit) {
                val activity = context as? ComponentActivity
                if (activity?.intent?.getStringExtra("destination") == "watchlist")
                    nav.navigate("watchlist")
            }
            fun open(media: Media, source: String) {
                vm.repository.remember(media)
                nav.navigate(
                    "title/${android.net.Uri.encode(media.key)}/${android.net.Uri.encode(source)}"
                ) {
                    launchSingleTop = true
                }
            }
            CompositionLocalProvider(
                LocalNavigate provides
                    { destination ->
                        nav.navigate(destination) { launchSingleTop = true }
                    }
            ) {
                Box(Modifier.fillMaxSize().background(colors.background)) {
                    Ambient(Modifier.systemBarsPadding(), cinematic = bare, tone = Color(tone))
                    Scaffold(
                        containerColor = Color.Transparent,
                        contentColor = colors.onSurface,
                        snackbarHost = { SnackbarHost(snackbar) },
                        contentWindowInsets =
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            ),
                    ) { padding ->
                        SharedTransitionLayout(Modifier.fillMaxSize().padding(padding)) {
                            CompositionLocalProvider(LocalShared provides this) {
                                NavHost(
                                    nav,
                                    "home",
                                    enterTransition = {
                                        fadeIn(
                                            tween(duration, easing = StakEase),
                                            initialAlpha = .35f,
                                        ) +
                                            slideInVertically(tween(duration, easing = StakEase)) {
                                                8
                                            }
                                    },
                                    exitTransition = { fadeOut(tween(duration)) },
                                    popEnterTransition = { fadeIn(tween(duration)) },
                                    popExitTransition = {
                                        fadeOut(tween(duration, easing = StakEase))
                                    },
                                ) {
                                    composable(
                                        "home?q={q}",
                                        arguments =
                                            listOf(
                                                navArgument("q") {
                                                    type = NavType.StringType
                                                    defaultValue = ""
                                                }
                                            ),
                                    ) {
                                        CompositionLocalProvider(LocalDestination provides this) {
                                            HomeScreen(
                                                vm,
                                                saved,
                                                ::open,
                                                it.arguments?.getString("q").orEmpty(),
                                            )
                                        }
                                    }
                                    composable("discover") {
                                        CompositionLocalProvider(LocalDestination provides this) {
                                            DiscoverScreen(vm, ::open)
                                        }
                                    }
                                    composable("watchlist") {
                                        CompositionLocalProvider(LocalDestination provides this) {
                                            WatchlistScreen(vm, saved, ::open)
                                        }
                                    }
                                    composable(
                                        "tracking?view={view}",
                                        arguments =
                                            listOf(navArgument("view") { defaultValue = "" }),
                                    ) { destination ->
                                        CompositionLocalProvider(LocalDestination provides this) {
                                            TrackingScreen(
                                                vm,
                                                saved,
                                                ::open,
                                                if (
                                                    destination.arguments?.getString("view") ==
                                                        "calendar"
                                                )
                                                    1
                                                else 0,
                                            )
                                        }
                                    }
                                    composable("library") {
                                        CompositionLocalProvider(LocalDestination provides this) {
                                            LibraryScreen(vm, ::open)
                                        }
                                    }
                                    composable(
                                        "settings?section={section}",
                                        arguments =
                                            listOf(navArgument("section") { defaultValue = "" }),
                                    ) { destination ->
                                        SettingsScreen(
                                            vm,
                                            destination.arguments?.getString("section").orEmpty(),
                                        )
                                    }
                                    composable("torbox") { LibraryScreen(vm, ::open, 1) }
                                    composable("shares") { HashListsScreen(vm) }
                                    composable("share/{id}") { entry ->
                                        HashListScreen(
                                            vm,
                                            entry.arguments?.getString("id").orEmpty(),
                                        )
                                    }
                                    composable("remote") { RemoteScreen(vm) }
                                    composable(
                                        "person/{id}",
                                        arguments =
                                            listOf(navArgument("id") { type = NavType.IntType }),
                                    ) { destination ->
                                        PersonScreen(
                                            vm,
                                            destination.arguments?.getInt("id") ?: 0,
                                            ::open,
                                        ) {
                                            nav.popBackStack()
                                        }
                                    }
                                    composable(
                                        "title/{key}/{source}",
                                        arguments =
                                            listOf(
                                                navArgument("key") { type = NavType.StringType },
                                                navArgument("source") { type = NavType.StringType },
                                            ),
                                    ) { backStack ->
                                        CompositionLocalProvider(LocalDestination provides this) {
                                            val key =
                                                backStack.arguments?.getString("key").orEmpty()
                                            DetailScreen(
                                                vm,
                                                key,
                                                backStack.arguments?.getString("source").orEmpty(),
                                                saved,
                                                snackbar,
                                            ) {
                                                nav.popBackStack()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (!bare) {
                        val tabs =
                            listOf(
                                "home",
                                "discover",
                                "watchlist",
                                "tracking",
                                "library",
                                "settings",
                            )
                        val selected =
                            if (route == "torbox") 4
                            else tabs.indexOfFirst { route.startsWith(it) }.coerceAtLeast(0)
                        Surface(
                            Modifier.align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                                .widthIn(max = 576.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = colors.surface.copy(alpha = .97f),
                            border =
                                androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    colors.onSurface.copy(alpha = .12f),
                                ),
                        ) {
                            MotionNavigation(selected) { index ->
                                nav.navigate(tabs[index]) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
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
    val icons =
        listOf(
            StakIcons.House,
            StakIcons.Compass,
            StakIcons.Bookmark,
            StakIcons.ChartNoAxesCombined,
            StakIcons.HardDrive,
            StakIcons.Settings2,
        )
    val labels = listOf("Home", "Discover", "Watchlist", "Tracking", "Library", "Settings")
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(64.25.dp).padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        val width = maxWidth / labels.size
        val x =
            animateDpAsState(
                width * selected + (width - 36.dp) / 2,
                if (LocalStakMotion.current.reduced) snap()
                else spring(dampingRatio = 1.108f, stiffness = 600f),
                label = "Navigation selection",
            )
        Box(
            Modifier.offset { IntOffset(x.value.roundToPx(), 6.dp.roundToPx()) }
                .size(36.dp, 30.dp)
                .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(12.dp))
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                Column(
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .clickable(role = androidx.compose.ui.semantics.Role.Tab) {
                            onSelect(index)
                        }
                        .testTag("nav-${label.lowercase(java.util.Locale.ROOT)}")
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(Modifier.size(36.dp, 28.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            icons[index],
                            label,
                            Modifier.size(19.dp),
                            tint =
                                if (index == selected) MaterialTheme.colorScheme.background
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        label.uppercase(java.util.Locale.ROOT),
                        fontFamily = MonoFont,
                        fontSize = 9.sp,
                        lineHeight = 11.25.sp,
                        letterSpacing = (-.36).sp,
                        maxLines = 1,
                        color =
                            if (index == selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
