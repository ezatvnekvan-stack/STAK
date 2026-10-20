package app.stak.nativeapp

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Rive
import kotlinx.coroutines.delay

private const val THEME_MACHINE = "Main State Machine"
private var riveInitialized = false

/** Original bundled art, state machine, input and 1400 ms playback policy. No WebView. */
@Composable
fun ThemeMotionPreview(dark: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = LocalStakMotion.current.reduced
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    Box(
        modifier
            .size(94.dp, 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Rule, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (dark) StakIcons.Moon else StakIcons.Sun, null, Modifier.size(24.dp), tint = Muted)
        if (!reduced && active) OriginalRiveThemeArt(dark, Modifier.matchParentSize())
        // Overlay owns input: the Rive illustration must not independently toggle itself.
        Box(
            Modifier.matchParentSize()
                .semantics {
                    contentDescription = "Switch to ${if(dark) "light" else "dark"} theme"
                }
                .clickable(role = Role.Button, onClick = onToggle)
        )
    }
}

@Composable
private fun OriginalRiveThemeArt(dark: Boolean, modifier: Modifier) {
    var view by remember { mutableStateOf<RiveAnimationView?>(null) }
    var failed by remember { mutableStateOf(false) }
    var lastDark by remember { mutableStateOf(false) }
    if (!failed)
        AndroidView(
            modifier = modifier,
            factory = { context ->
                try {
                    if (!riveInitialized) {
                        Rive.init(context.applicationContext)
                        riveInitialized = true
                    }
                    RiveAnimationView.Builder(context)
                        .setResource(R.raw.theme_switch)
                        .setStateMachineName(THEME_MACHINE)
                        .setAutoplay(false)
                        .setShouldLoadCDNAssets(false)
                        .setTouchPassThrough(true)
                        .setFit(Fit.CONTAIN)
                        .build()
                        .also { art ->
                            art.isOpaque = false
                            art.setLayerPaint(
                                Paint().apply {
                                    colorFilter =
                                        ColorMatrixColorFilter(
                                            ColorMatrix().apply { setSaturation(0f) }
                                        )
                                }
                            )
                            view = art
                        }
                } catch (_: Exception) {
                    failed = true
                    android.view.View(context)
                } catch (_: UnsatisfiedLinkError) {
                    failed = true
                    android.view.View(context)
                }
            },
            onRelease = { released ->
                (released as? RiveAnimationView)?.pause()
                view = null
            },
        )
    LaunchedEffect(view, dark) {
        val art = view ?: return@LaunchedEffect
        // Resource setup occurs synchronously when the AndroidView attaches to its window.
        withFrameNanos {}
        try {
            art.play(THEME_MACHINE, isStateMachine = true)
            if (dark != lastDark) {
                art.fireState(THEME_MACHINE, "Click")
                lastDark = dark
            }
            delay(1400)
        } catch (_: IllegalStateException) {
            failed = true
        } finally {
            art.pause()
        }
    }
}
