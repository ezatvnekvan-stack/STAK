package app.stak.nativeapp.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Reduced motion follows the system accessibility setting unless the user has
 * explicitly overridden it in Appearance.
 */
@Composable
fun rememberReducedMotion(override: Boolean? = null): Boolean {
    val context = LocalContext.current
    val system = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    return override == true || system
}
