package app.stak.nativeapp.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import app.stak.nativeapp.ui.theme.*

@Composable fun MatteSurface(modifier:Modifier=Modifier,source:GlassSource?=null,content:@Composable BoxScope.()->Unit){
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha=.12f)).border(1.dp,Color.White.copy(alpha=.24f),RoundedCornerShape(12.dp)),content=content)
}
@Composable fun StakButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,variant:StakButtonVariant=StakButtonVariant.Outlined,icon:androidx.compose.ui.graphics.vector.ImageVector?=null,enabled:Boolean=true,compact:Boolean=false,iconTint:Color?=null){
    val press=rememberPressMotion(enabled)
    val bg=if(variant==StakButtonVariant.Primary)Color(0xF0101114) else Color.Transparent
    val pressed by press.source.collectIsPressedAsState()
    val strokeTarget=when(variant){StakButtonVariant.Primary->StakAccent.copy(alpha=.85f);StakButtonVariant.Ghost->Color.Transparent;StakButtonVariant.Destructive->Color(0xFFAD555B);else->Color.White.copy(alpha=.40f)}
    val stroke by animateColorAsState(if(pressed&&enabled&&variant!=StakButtonVariant.Ghost) Color.White.copy(alpha=.9f) else strokeTarget,if(LocalReducedMotion.current)snap() else tween(120),label="button-edge")
    val fg=when(variant){StakButtonVariant.Primary->Color.White;StakButtonVariant.Destructive->Color(0xFFFF969C);else->StakText}
    val glyphInk by animateColorAsState(iconTint?:fg,if(LocalReducedMotion.current)snap()else tween(StakMotion.Quick),label="button-glyph")
    Row(modifier.pressMotion(press).alpha(if(enabled)1f else .4f).heightIn(min=44.dp).clip(RoundedCornerShape(11.dp)).background(bg).border(1.dp,stroke,RoundedCornerShape(11.dp)).clickable(interactionSource=press.source,indication=LocalIndication.current,enabled=enabled,onClick=onClick).padding(horizontal=if(compact)12.dp else 16.dp,vertical=8.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
        icon?.let{Icon(it,null,tint=glyphInk,modifier=Modifier.size(16.dp));Spacer(Modifier.width(7.dp))}
        Text(label.uppercase(),fontFamily=SpaceMonoFont,fontWeight=FontWeight.Bold,fontSize=11.sp,lineHeight=17.sp,color=fg,maxLines=1)
    }
}
@Composable fun StakIconButton(onClick:()->Unit,modifier:Modifier=Modifier,content:@Composable BoxScope.()->Unit){
    val press=rememberPressMotion()
    Box(modifier.pressMotion(press).size(44.dp).clip(RoundedCornerShape(9.dp)).clickable(interactionSource=press.source,indication=LocalIndication.current,onClick=onClick),contentAlignment=Alignment.Center,content=content)
}
@Composable fun StakChip(label:String,selected:Boolean,onClick:()->Unit,modifier:Modifier=Modifier,leading:(@Composable ()->Unit)?=null){
    val press=rememberPressMotion()
    val stroke by animateColorAsState(if(selected)Color.White.copy(alpha=.80f) else Color.White.copy(alpha=.30f),if(LocalReducedMotion.current)snap() else tween(StakMotion.Quick),label="chip")
    Box(modifier.heightIn(min=44.dp).pressMotion(press).clip(RoundedCornerShape(10.dp)).selectable(selected=selected,role=Role.RadioButton,interactionSource=press.source,indication=LocalIndication.current,onClick=onClick),contentAlignment=Alignment.Center){
        Row(Modifier.clip(RoundedCornerShape(9.dp)).border(1.dp,stroke,RoundedCornerShape(9.dp)).padding(horizontal=12.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically){leading?.let{it();Spacer(Modifier.width(6.dp))};Text(label.uppercase(),fontFamily=SpaceMonoFont,fontWeight=FontWeight.Bold,fontSize=11.sp,color=if(selected)Color.White else StakTextMuted,maxLines=1)}
    }
}
@Composable fun StakPageHeader(title:String,subtitle:String?=null,modifier:Modifier=Modifier,trailing:(@Composable ()->Unit)?=null,subtitleMono:Boolean=false,drawDivider:Boolean=true){
    Row(modifier.fillMaxWidth().heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),fontFamily=DotoFont,fontWeight=FontWeight.Black,fontSize=28.sp,lineHeight=34.sp,letterSpacing=(-.8).sp,color=StakText);trailing?.invoke()}
}
/** Outline filters: 32dp visual selection with at least 44dp touch targets. */
@Composable fun StakSegmented(options:List<Pair<String,String>>,selected:String,onSelect:(String)->Unit,modifier:Modifier=Modifier,fillWidth:Boolean=false,fontSize:Int=12,innerHeight:Int=48,chipRadius:Int=12,gap:Int=4,pad:Int=4,chipPaddingHorizontal:Int=16,chipPaddingVertical:Int=9){
    BoxWithConstraints(modifier.heightIn(min=44.dp)){
        val cell=if(fillWidth && maxWidth.value.isFinite())(maxWidth-4.dp*(options.size-1))/options.size.coerceAtLeast(1) else 0.dp
        val position=animateDpAsState((cell+4.dp)*options.indexOfFirst{it.first==selected}.coerceAtLeast(0),if(LocalReducedMotion.current)snap() else spring(dampingRatio=.9f,stiffness=500f),label="filter-selection")
        if(fillWidth)Box(Modifier.align(Alignment.CenterStart).width(cell).height(32.dp).graphicsLayer{translationX=position.value.toPx()}.clip(RoundedCornerShape(9.dp)).border(1.dp,Color.White.copy(alpha=.75f),RoundedCornerShape(9.dp)))
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically){options.forEach{(id,label)->
            val press=rememberPressMotion()
            Box((if(fillWidth)Modifier.weight(1f) else Modifier).height(44.dp).pressMotion(press).clip(RoundedCornerShape(9.dp)).selectable(selected=selected==id,role=Role.RadioButton,interactionSource=press.source,indication=LocalIndication.current){onSelect(id)},contentAlignment=Alignment.Center){
                if(!fillWidth&&selected==id)Box(Modifier.matchParentSize().padding(vertical=6.dp).clip(RoundedCornerShape(9.dp)).border(1.dp,Color.White.copy(alpha=.75f),RoundedCornerShape(9.dp)))
                Text(label.uppercase(),Modifier.padding(horizontal=if(fillWidth)6.dp else 12.dp,vertical=5.dp),fontFamily=SpaceMonoFont,fontWeight=FontWeight.Bold,fontSize=(if(label.length>12)10 else fontSize.coerceIn(11,12)).sp,lineHeight=17.sp,color=if(id==selected)StakText else StakTextMuted,maxLines=1)
            }
        }}
    }
}
/** Page navigation is text with an animated underline, not another filter tray. */
@Composable fun StakTabs(options:List<Pair<String,String>>,selected:String,onSelect:(String)->Unit,modifier:Modifier=Modifier){
    BoxWithConstraints(modifier.fillMaxWidth().height(44.dp)){
        val cell=maxWidth/options.size.coerceAtLeast(1)
        val position=animateDpAsState(cell*options.indexOfFirst{it.first==selected}.coerceAtLeast(0)+(cell-28.dp)/2,if(LocalReducedMotion.current)snap() else tween(StakMotion.Standard,easing=StakMotion.Ease),label="page-underline")
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
        Box(Modifier.align(Alignment.BottomStart).width(28.dp).height(2.dp).graphicsLayer{translationX=position.value.toPx()}.clip(CircleShape).background(StakText))
        Row{options.forEach{(id,label)->Box(Modifier.weight(1f).height(44.dp).selectable(selected=id==selected,role=Role.Tab,onClick={onSelect(id)}),contentAlignment=Alignment.Center){Text(label.uppercase(),fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=13.sp,color=if(id==selected)StakText else StakTextMuted)}}}
    }
}
