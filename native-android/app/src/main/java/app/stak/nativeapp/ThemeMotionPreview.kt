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
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.Rive

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
                        Rive.init(context.applicationContext, RendererType.Canvas)
                        riveInitialized = true
                    }
                    RiveAnimationView.Builder(context)
                        .setResource(R.raw.theme_switch)
                        .setStateMachineName(THEME_MACHINE)
                        .setAutoplay(false)
                        .setShouldLoadCDNAssets(false)
                        .setTouchPassThrough(true)
                        .setFit(Fit.CONTAIN)
                        .setRendererType(RendererType.Canvas)
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
    DisposableEffect(view, dark) {
        val art = view
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val pause = Runnable { art?.pause() }
        val deadline = android.os.SystemClock.uptimeMillis() + 5000
        val start =
            object : Runnable {
                override fun run() {
                    if (art == null) return
                    // React waits for onLoad. On Android both the artboard and texture surface
                    // must exist before applying the state-machine input.
                    if (
                        !art.isAttachedToWindow ||
                            !art.isAvailable ||
                            art.controller.activeArtboard == null
                    ) {
                        if (android.os.SystemClock.uptimeMillis() < deadline)
                            art.postOnAnimation(this)
                        else {
                            android.util.Log.w(
                                "StakThemeMotion",
                                "Original theme art did not become ready",
                            )
                            failed = true
                        }
                        return
                    }
                    try {
                        art.play(THEME_MACHINE, isStateMachine = true)
                        if (dark != lastDark) {
                            art.fireState(THEME_MACHINE, "Click")
                            lastDark = dark
                        }
                        // This is native playback: its settling timer follows the real Android
                        // frame clock, independently of Compose's animation/test clock.
                        handler.postDelayed(pause, 1400)
                    } catch (failure: IllegalStateException) {
                        android.util.Log.w(
                            "StakThemeMotion",
                            "Original theme state machine could not play",
                            failure,
                        )
                        failed = true
                    }
                }
            }
        art?.post(start)
        onDispose {
            handler.removeCallbacks(pause)
            art?.removeCallbacks(start)
            art?.pause()
        }
    }
}
