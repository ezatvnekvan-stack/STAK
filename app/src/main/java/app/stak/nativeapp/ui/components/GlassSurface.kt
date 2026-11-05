@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.stak.nativeapp.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import android.os.Build
import dev.chrisbanes.haze.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider

class GlassSource(val layer:GraphicsLayer,val crossWindow:Boolean=false,val preBlurred:Boolean=false,val static:Boolean=false,val hazeState:HazeState?=null){var ownerView:android.view.View?=null;val origin=mutableStateOf(Offset.Zero);val requests=mutableIntStateOf(0);val generation=mutableLongStateOf(0);private var serial=0L;var recordedSize=androidx.compose.ui.unit.IntSize.Zero;var recordedKey:Any?=null;fun changed(){if(crossWindow){serial++;generation.longValue=serial}}}
val LocalGlassSource=staticCompositionLocalOf<GlassSource?>{null}
/**
 * Renders the content into [source]'s layer only while a glass surface asks for it.
 * A `static` source is recorded once per size (and per [key] change), so a backdrop
 * that only depends on one value - a gradient tint, say - costs a single texture draw
 * per frame instead of a full offscreen re-render of the screen.
 */
fun Modifier.recordGlassSource(source:GlassSource,view:android.view.View,key:(()->Any?)?=null):Modifier {
    source.ownerView=view
    return onGloballyPositioned{coordinates->val location=IntArray(2);view.getLocationOnScreen(location);source.origin.value=Offset(location[0].toFloat(),location[1].toFloat())+coordinates.positionInRoot()}.drawWithContent{
    if(source.requests.intValue==0){drawContent();return@drawWithContent}
    val size=androidx.compose.ui.unit.IntSize(this.size.width.toInt(),this.size.height.toInt())
    val currentKey=key?.invoke()
    if(size.width>0&&size.height>0&&(!source.static||source.recordedSize!=size||source.recordedKey!=currentKey)){
        source.layer.record{this@drawWithContent.drawContent()}
        source.recordedSize=size
        source.recordedKey=currentKey
    }
    drawLayer(source.layer)
    source.changed()
}.then(source.hazeState?.let{Modifier.haze(it)} ?: Modifier)
}
@Composable fun GlassSurface(modifier:Modifier=Modifier,source:GlassSource?=LocalGlassSource.current,content:@Composable BoxScope.()->Unit){
    val haze=source?.hazeState?.takeIf{source.ownerView==null||source.ownerView===LocalView.current}
    if(haze!=null){
        Box(modifier.clip(RoundedCornerShape(18.dp)).hazeChild(haze,stakHazeStyle()).border(1.dp,Brush.verticalGradient(listOf(Color(0x40FFFFFF),Color(0x18FFFFFF))),RoundedCornerShape(18.dp)),content=content)
        return
    }
    val shape=RoundedCornerShape(18.dp);val view=LocalView.current;var offset by remember{mutableStateOf(Offset.Zero)}
    val radius=with(LocalDensity.current){18.dp.toPx()}
    val blur=remember(radius){if(Build.VERSION.SDK_INT>=31)android.graphics.RenderEffect.createBlurEffect(radius,radius,android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() else null}
    DisposableEffect(source){source?.let{it.requests.intValue++};onDispose{source?.let{it.requests.intValue--}}}
    Box(modifier.clip(shape).onGloballyPositioned{coordinates->val location=IntArray(2);view.getLocationOnScreen(location);offset=Offset(location[0].toFloat(),location[1].toFloat())+coordinates.positionInRoot()}.border(1.dp,Brush.verticalGradient(listOf(Color(0x45FFFFFF),Color(0x1AFFFFFF),Color(0x28FFFFFF))),shape)){
        if(source!=null)Box(Modifier.matchParentSize().graphicsLayer{renderEffect=if(source.preBlurred)null else blur}.drawBehind{val relative=offset-source.origin.value;translate(-relative.x,-relative.y){if(source.generation.longValue>=0)drawLayer(source.layer)}})
        Box(Modifier.matchParentSize().background(if(source!=null&&blur!=null)GlassMaterial.Scrim else Color(0xEE101010)))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(GlassMaterial.Sheen,Color.Transparent))))
        content()
    }
}
/** Haze source and child share a window. Parent layers are replayed into that window before blurring. */
@Composable fun GlassDialog(onDismiss:()->Unit,content:@Composable ()->Unit){
    val parent=LocalGlassSource.current
    val useHaze=parent?.hazeState!=null
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=!useHaze)){
        SmoothDialogWindow()
        val view=LocalView.current
        DisposableEffect(view){val window=(view.parent as? DialogWindowProvider)?.window;window?.setBackgroundDrawableResource(android.R.color.transparent);window?.setDimAmount(if(useHaze)0f else .12f);window?.setWindowAnimations(0);onDispose{}}
        val layer=rememberGraphicsLayer();val source=remember(layer){GlassSource(layer,crossWindow=true,hazeState=if(useHaze)HazeState() else null)}
        if(useHaze&&parent!=null){
            DisposableEffect(parent){parent.requests.intValue++;onDispose{parent.requests.intValue--}}
            val replayLayer=rememberGraphicsLayer();val replay=remember(replayLayer){GlassSource(replayLayer,crossWindow=true,hazeState=HazeState())}
            var origin by remember{mutableStateOf(Offset.Zero)}
            Box(Modifier.fillMaxSize().onGloballyPositioned{coordinates->val location=IntArray(2);view.getLocationOnScreen(location);origin=Offset(location[0].toFloat(),location[1].toFloat())+coordinates.positionInRoot()}.clickable(remember{MutableInteractionSource()},indication=null,onClick=onDismiss),contentAlignment=Alignment.Center){
                Box(Modifier.matchParentSize().recordGlassSource(replay,view).drawBehind{
                    val relative=origin-parent.origin.value
                    if(parent.generation.longValue>=0)translate(-relative.x,-relative.y){drawLayer(parent.layer)}
                })
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha=.14f)))
                CompositionLocalProvider(LocalGlassSource provides source){
                    GlassSurface(Modifier.widthIn(max=560.dp).fillMaxWidth().padding(horizontal=28.dp).premiumEntrance().recordGlassSource(source,view).clickable(remember{MutableInteractionSource()},indication=null,onClick={}),source=replay){content()}
                }
            }
        }else CompositionLocalProvider(LocalGlassSource provides source){GlassSurface(Modifier.fillMaxWidth().premiumEntrance().recordGlassSource(source,view),source=parent){content()}}
    }
}
@Composable fun GlassAlertDialog(onDismissRequest:()->Unit,title:@Composable ()->Unit,text:@Composable ()->Unit,confirmButton:@Composable ()->Unit,dismissButton:(@Composable ()->Unit)?=null,containerColor:Color=Color.Transparent){
    GlassDialog(onDismissRequest){CompositionLocalProvider(
        LocalContentColor provides app.stak.nativeapp.ui.theme.StakText,
        androidx.compose.material3.LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily=app.stak.nativeapp.ui.theme.HankenFont,fontSize=14.sp,lineHeight=21.sp)
    ){
        Column(Modifier.fillMaxWidth().heightIn(max=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp*.85f).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(fontFamily=app.stak.nativeapp.ui.theme.DotoFont,fontSize=20.sp,lineHeight=26.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)){title()}
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){text()}
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,androidx.compose.ui.Alignment.End),verticalArrangement=Arrangement.spacedBy(4.dp)){dismissButton?.invoke();confirmButton()}
        }
    }}
}
@Composable fun GlassDropdownMenu(expanded:Boolean,onDismissRequest:()->Unit,modifier:Modifier=Modifier,containerColor:Color=Color.Transparent,content:@Composable ColumnScope.()->Unit){
    if(expanded)PauseCinematicMotion()
    DropdownMenu(expanded,onDismissRequest,modifier=modifier,containerColor=Color.Transparent,tonalElevation=0.dp,shadowElevation=0.dp){GlassSurface{Column(content=content)}}
}
