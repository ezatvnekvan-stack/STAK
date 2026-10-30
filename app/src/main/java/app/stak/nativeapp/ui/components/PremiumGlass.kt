package app.stak.nativeapp.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One material palette; inline glass only samples an independent background layer. */
object GlassMaterial {
    val Control=Color(0xFF14171C)
    val Hairline=Color(0x0FFFFFFF)
    val Sheen=Color.Transparent
    val Scrim=Color(0xD4080808)
    val Selected=Color(0x28FFFFFF)
}
val LocalInlineGlassSource=staticCompositionLocalOf<GlassSource?>{null}
@Composable fun InlineGlass(modifier:Modifier=Modifier,content:@Composable BoxScope.()->Unit){
    MatteSurface(modifier,content=content)
}
fun Modifier.glassChrome(radius:Dp=14.dp,fill:Color=GlassMaterial.Control)=drawWithCache{
    val outline=RoundedCornerShape(radius).createOutline(size,layoutDirection,this)
    val sheen=Brush.verticalGradient(listOf(GlassMaterial.Sheen,Color.Transparent,Color(0x09000000)))
    val stroke=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
    onDrawBehind{
        drawOutline(outline,fill)
        drawOutline(outline,sheen)
        drawOutline(outline,GlassMaterial.Hairline,style=stroke)
    }
}
@Composable fun rememberSoftBlur():RenderEffect? {
    val radius=with(androidx.compose.ui.platform.LocalDensity.current){18.dp.toPx()}
    return remember(radius){if(android.os.Build.VERSION.SDK_INT>=31)android.graphics.RenderEffect.createBlurEffect(radius,radius,android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() else null}
}
