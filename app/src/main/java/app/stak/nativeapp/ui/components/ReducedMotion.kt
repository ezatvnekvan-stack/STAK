package app.stak.nativeapp.ui.components

import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/**
 * Reduced motion follows the system accessibility setting unless the user has
 * explicitly overridden it in Appearance.
 */
@Composable
fun rememberReducedMotion(override: Boolean? = null): Boolean {
    val context = LocalContext.current
    val resolver=context.contentResolver
    fun disabled()=Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    var system by remember(resolver){mutableStateOf(disabled())}
    DisposableEffect(resolver){
        val observer=object:android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())){
            override fun onChange(selfChange:Boolean){system=disabled()}
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose{resolver.unregisterContentObserver(observer)}
    }
    return override == true || system
}
