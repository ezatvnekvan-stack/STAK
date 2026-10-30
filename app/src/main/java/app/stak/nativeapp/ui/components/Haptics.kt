package app.stak.nativeapp.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Semantic haptics.
 *
 * The previous version fired a fixed 12 ms vibration on every tap, which ignores
 * the system touch-feedback setting and feels identical whether you open a title
 * or add something to the watchlist. `performHapticFeedback` with a semantic
 * constant uses the platform's own patterns, respects the accessibility setting
 * and needs no vibration permission.
 */
object Haptic {
    const val Tap = HapticFeedbackConstants.CLOCK_TICK
    const val Toggle = HapticFeedbackConstants.KEYBOARD_TAP
    const val Confirm = HapticFeedbackConstants.CONFIRM
    const val LongPress = HapticFeedbackConstants.LONG_PRESS
}

fun View.stakHaptic(which: Int = HapticFeedbackConstants.CLOCK_TICK) {
    try {
        performHapticFeedback(which)
    } catch (_: Exception) {
        // Haptics are best-effort; never let them break an interaction.
    }
}

/** Legacy keyed vibration, kept for callers that have no View at hand. */
fun vibrate(context: Context, durationMs: Long = 12) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    } catch (_: SecurityException) {
        // Haptics are best-effort; a denied permission must never crash the UI.
    } catch (_: Exception) {
        // Some emulators expose no vibrator service.
    }
}
