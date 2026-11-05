@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import coil.compose.AsyncImage
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Duplicate end pages make last → first identical to every adjacent dissolve. */
@Composable internal fun HeroCarousel(
    items:List<MediaItem>,onOpenTitle:(MediaItem)->Unit,
    sharedScope:SharedTransitionScope?,animatedScope:AnimatedVisibilityScope?,
    reducedMotion:Boolean,autoPlay:Boolean=true
){
    if(items.isEmpty())return
    val count=items.size
    val reduced=reducedMotion||LocalReducedMotion.current
    val pager=rememberPagerState(initialPage=if(count>1)1 else 0,pageCount={if(count>1)count+2 else 1})
    fun itemAt(page:Int)=items[if(count>1)Math.floorMod(page-1,count)else 0]
    val index=if(count>1)Math.floorMod(pager.currentPage-1,count)else 0
    val current=items[index]
    val scope=rememberCoroutineScope()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val motion=LocalCinematicMotion.current
    val autoAllowed=autoPlay&&!reduced&&count>1&&lifecycle.isAtLeast(Lifecycle.State.RESUMED)&&(motion?.dialogs?:0)==0
    val ids=remember(items){items.map{it.mediaKey}}
    val palette=LocalCinematicPalette.current
    LaunchedEffect(current.mediaKey,current.background){palette?.value=current.background?:current.poster}
    LaunchedEffect(pager.settledPage,pager.isScrollInProgress,count){
        if(count>1&&!pager.isScrollInProgress)when(pager.settledPage){0->pager.scrollToPage(count);count+1->pager.scrollToPage(1)}
    }
    LaunchedEffect(autoAllowed,pager.settledPage,ids){
        if(autoAllowed){delay(8500);if(!pager.isScrollInProgress)pager.animateScrollToPage((pager.currentPage+1).coerceAtMost(count+1),animationSpec=tween(760,easing=StakMotion.Ease))}
    }
    val height=(LocalConfiguration.current.screenWidthDp*.64f).coerceIn(232f,278f).dp
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(20.dp)).background(Color(0xFF111518))
        .border(1.dp,Color.White.copy(alpha=.10f),RoundedCornerShape(20.dp)).testTag("home-hero").clickable { onOpenTitle(current) }
        .semantics{stateDescription="Featured ${index+1} of $count"}){
        HorizontalPager(pager,Modifier.fillMaxSize(),beyondViewportPageCount=1,key={page->"$page:${itemAt(page).mediaKey}"}){page->
            val item=itemAt(page)
            // Cancel the pager's layout translation: pictures blend in one canvas
            // instead of splitting the hero into two sliding halves.
            Box(Modifier.fillMaxSize().graphicsLayer{
                val offset=(pager.currentPage-page)+pager.currentPageOffsetFraction
                translationX=offset*size.width
                alpha=(1f-abs(offset)).coerceIn(0f,1f)
            }){
                val shared=if(page==pager.settledPage)Modifier.stakSharedElement(sharedScope,animatedScope,"hero-${item.mediaKey}")else Modifier
                StakArtwork(item.background?:item.poster,null,Modifier.fillMaxSize().then(shared).graphicsLayer{
                    val offset=(pager.currentPage-page)+pager.currentPageOffsetFraction
                    scaleX=if(reduced)1f else 1.035f+abs(offset)*.065f;scaleY=scaleX
                    translationX=if(reduced)0f else offset*size.width*.045f
                })
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent,.30f to Color.Black.copy(alpha=.04f),.70f to Color.Black.copy(alpha=.55f),1f to Color(0xF4080A0C))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha=.24f),Color.Transparent))))
        AnimatedContent(current.mediaKey,Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start=22.dp,end=22.dp,bottom=52.dp),
            transitionSpec={
                if(reduced)fadeIn(tween(100)) togetherWith fadeOut(tween(80))
                else (fadeIn(tween(360,90))+slideInVertically(tween(460,easing=StakMotion.Ease)){it/7}) togetherWith
                    (fadeOut(tween(150))+slideOutVertically(tween(220)){-it/10})
            },label="hero-title-dissolve"){key->
            val title=items.firstOrNull{it.mediaKey==key}?:current
            Column{
                var failed by remember(title.logo){mutableStateOf(false)}
                Box(Modifier.fillMaxWidth(.88f).height(56.dp),contentAlignment=Alignment.BottomStart){
                    if(title.logo!=null&&!failed)AsyncImage(title.logo,title.name,contentScale=ContentScale.Fit,alignment=Alignment.BottomStart,onError={failed=true},modifier=Modifier.fillMaxSize())
                    else Text(title.name,fontFamily=DotoFont,fontWeight=FontWeight.Black,fontSize=23.sp,lineHeight=28.sp,color=Color.White,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(12.dp))
                Text((listOfNotNull(title.year,title.runtime)+title.genres.take(2)).joinToString(" · "),fontFamily=HankenFont,fontSize=12.sp,color=Color(0xDDFFFFFF),maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
        if(count>1)Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(52.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
            items.forEachIndexed{position,_->
                val width=animateDpAsState(if(index==position)22.dp else 4.dp,if(reduced)snap()else tween(320,easing=StakMotion.Ease),label="hero-page-mark")
                Box(Modifier.size(44.dp).semantics{contentDescription="Show featured title ${position+1}";selected=index==position}.clickable{
                    scope.launch{pager.animateScrollToPage(position+1,animationSpec=tween(if(reduced)0 else 760,easing=StakMotion.Ease))}
                },contentAlignment=Alignment.Center){
                    val ink=if(index==position)StakAccent else Color.White.copy(alpha=.40f)
                    androidx.compose.foundation.Canvas(Modifier.width(24.dp).height(3.dp)){
                        val pixels=width.value.toPx()
                        drawRect(ink,topLeft=androidx.compose.ui.geometry.Offset((size.width-pixels)/2,0f),size=androidx.compose.ui.geometry.Size(pixels,size.height))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("%02d / %02d".format(index+1,count),Modifier.padding(end=10.dp),fontFamily=SpaceMonoFont,fontSize=10.sp,color=Color.White.copy(alpha=.65f))
        }
    }
}

@Composable internal fun HomeSpotlight(item:MediaItem,onOpen:()->Unit,sharedScope:SharedTransitionScope?,animatedScope:AnimatedVisibilityScope?,elementKey:String){
    val press=rememberPressMotion()
    var menu by remember(item.mediaKey){mutableStateOf(false)}
    Box(Modifier.fillMaxWidth().height(206.dp).pressMotion(press).clip(RoundedCornerShape(20.dp))
        .border(1.dp,Color.White.copy(alpha=.12f),RoundedCornerShape(20.dp))
        .combinedClickable(interactionSource=press.source,indication=androidx.compose.foundation.LocalIndication.current,onClick=onOpen,onLongClick={menu=true}).testTag("home-spotlight")){
        StakArtwork(item.background?:item.poster,null,Modifier.fillMaxSize().stakSharedElement(sharedScope,animatedScope,elementKey))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xF008090A)))))
        Row(Modifier.align(Alignment.BottomStart).padding(18.dp),verticalAlignment=Alignment.Bottom){
            Column(Modifier.weight(1f)){
                TitleBrand(item,Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp));Text((listOfNotNull(item.year)+item.genres.take(2)).joinToString(" · "),fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted,maxLines=1)
            }
            PosterRank(1)
        }
    }
    PosterContextMenu(item,menu,{menu=false},onOpen)
}

/** Only adjacent enabled sections are paired; custom order/visibility survives. */
internal fun anticipatedPairStart(sections:List<HomeSection>):String? = sections.filter{it.enabled}.zipWithNext().firstOrNull{(a,b)->
    setOf(a.id,b.id)==setOf("anticipated_movie","anticipated_series")
}?.first?.id
