package app.stak.nativeapp.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.stak.nativeapp.R
import com.airbnb.lottie.compose.*

enum class StakAnimation(val asset:Int,val description:String){
    Welcome(R.raw.stak_welcome,"STAK"),Search(R.raw.stak_search,"Searching"),Success(R.raw.stak_success,"Added successfully")
}
@Composable fun StakLottie(animation:StakAnimation,modifier:Modifier=Modifier,loop:Boolean=false){
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animation.asset))
    val reduced=LocalReducedMotion.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val progress by animateLottieCompositionAsState(composition,isPlaying=!reduced&&lifecycle.isAtLeast(Lifecycle.State.RESUMED),iterations=if(loop)LottieConstants.IterateForever else 1,restartOnPlay=false)
    LottieAnimation(composition,progress={if(reduced)if(animation==StakAnimation.Search).35f else 1f else progress},modifier=modifier.clearAndSetSemantics{contentDescription=animation.description})
}
