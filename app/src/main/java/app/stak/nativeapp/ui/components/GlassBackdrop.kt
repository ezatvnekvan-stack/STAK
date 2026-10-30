package app.stak.nativeapp.ui.components

import android.os.Build
import dev.chrisbanes.haze.hazeChild
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Only the recorded screen is blurred; navigation children stay outside this layer. */
@Composable fun GlassBackdrop(content:GraphicsLayer,modifier:Modifier=Modifier) {
    val haze=LocalGlassSource.current?.hazeState
    if(haze!=null){Box(modifier.hazeChild(haze,stakHazeStyle()));return}
    var position by remember { mutableStateOf(Offset.Zero) }
    val radius=with(LocalDensity.current){18.dp.toPx()}
    val effect=remember(radius){if(Build.VERSION.SDK_INT>=31)android.graphics.RenderEffect.createBlurEffect(radius,radius,android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() else null}
    Box(modifier.onGloballyPositioned{position=it.positionInRoot()}) {
        Box(Modifier.matchParentSize().graphicsLayer{renderEffect=effect}.drawBehind {
            translate(-position.x,-position.y){drawLayer(content)}
        })
        Box(Modifier.matchParentSize().background(if(effect!=null)GlassMaterial.Scrim else Color(0xE0080808)))
    }
}
