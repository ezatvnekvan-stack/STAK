package app.stak.nativeapp.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.stakSharedElement(
    scope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    key: String
): Modifier {
    val base = this
    return if (scope != null && animatedVisibilityScope != null && !LocalReducedMotion.current) {
        with(scope) {
            base.sharedElement(
                state = rememberSharedContentState(key = key),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = {_,_->androidx.compose.animation.core.tween(StakMotion.Standard,easing=StakMotion.Ease)}
            )
        }
    } else base
}
