package app.stak.nativeapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

val LocalReducedMotion=staticCompositionLocalOf{false}
object StakMotion {
    const val Quick=120
    const val Standard=220
    const val Atmosphere=500
    val Ease=FastOutSlowInEasing
}
data class PressMotion(val source:MutableInteractionSource,val scale:State<Float>)
@Composable fun rememberPressMotion(enabled:Boolean=true):PressMotion {
    val source=remember{MutableInteractionSource()}
    val pressed by source.collectIsPressedAsState()
    val scale=animateFloatAsState(if(pressed&&enabled&&!LocalReducedMotion.current).975f else 1f,if(LocalReducedMotion.current)snap() else spring(dampingRatio=.95f,stiffness=1000f),label="press")
    return PressMotion(source,scale)
}
fun Modifier.pressMotion(motion:PressMotion)=graphicsLayer{scaleX=motion.scale.value;scaleY=motion.scale.value}
@Composable fun LazyItemScope.stakItemMotion():Modifier {
    val reduced=LocalReducedMotion.current
    return Modifier.animateItem(fadeInSpec=if(reduced)null else tween(StakMotion.Quick),placementSpec=if(reduced)null else spring(dampingRatio=1f,stiffness=500f),fadeOutSpec=if(reduced)null else tween(120))
}
@Composable fun LazyGridItemScope.stakItemMotion():Modifier {
    val reduced=LocalReducedMotion.current
    return Modifier.animateItem(fadeInSpec=if(reduced)null else tween(StakMotion.Quick),placementSpec=if(reduced)null else spring(dampingRatio=1f,stiffness=500f),fadeOutSpec=if(reduced)null else tween(120))
}
@Composable fun SmoothDialogWindow(){
    PauseCinematicMotion()
    val view=LocalView.current
    DisposableEffect(view){val window=(view.parent as? DialogWindowProvider)?.window;window?.decorView?.post{app.stak.nativeapp.performance.requestSmoothDisplay(window);androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply{isAppearanceLightStatusBars=false;isAppearanceLightNavigationBars=false}};onDispose{}}
}

@Composable fun Modifier.premiumEntrance():Modifier {
    val reduced=LocalReducedMotion.current
    val amount=remember{Animatable(if(reduced)1f else 0f)}
    LaunchedEffect(reduced){if(reduced)amount.snapTo(1f) else amount.animateTo(1f,tween(StakMotion.Standard,easing=StakMotion.Ease))}
    return graphicsLayer{alpha=amount.value;translationY=12f*density*(1f-amount.value);scaleX=.975f+.025f*amount.value;scaleY=scaleX}
}
