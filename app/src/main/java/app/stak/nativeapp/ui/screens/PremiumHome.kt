@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,androidx.compose.animation.ExperimentalSharedTransitionApi::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
import androidx.compose.ui.draw.drawWithCache
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
    reducedMotion:Boolean,autoPlay:Boolean=true,modifier:Modifier=Modifier
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
    LaunchedEffect(current.mediaKey,current.cinematicImage){palette?.value=current.cinematicImage}
    LaunchedEffect(pager.settledPage,pager.isScrollInProgress,count){
        if(count>1&&!pager.isScrollInProgress)when(pager.settledPage){0->pager.scrollToPage(count);count+1->pager.scrollToPage(1)}
    }
    LaunchedEffect(autoAllowed,pager.settledPage,ids){
        if(autoAllowed){delay(8500);if(!pager.isScrollInProgress)pager.animateScrollToPage((pager.currentPage+1).coerceAtMost(count+1),animationSpec=tween(760,easing=StakMotion.Ease))}
    }
    val height=homeHeroHeight(LocalConfiguration.current.screenHeightDp,androidx.compose.ui.platform.LocalDensity.current.fontScale).dp
    Box(modifier.fillMaxWidth().height(height).testTag("home-hero").clickable { onOpenTitle(current) }
        .semantics{stateDescription="Featured ${index+1} of $count"}){
        Box(Modifier.fillMaxSize().graphicsLayer{compositingStrategy=CompositingStrategy.Offscreen;clip=true}.drawWithCache{
            val mask=Brush.verticalGradient(0f to Color.White,.30f to Color.White,.50f to Color.White.copy(alpha=.94f),.68f to Color.White.copy(alpha=.70f),.84f to Color.White.copy(alpha=.30f),1f to Color.Transparent)
            onDrawWithContent{drawContent();drawRect(mask,blendMode=BlendMode.DstIn)}
        }){
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
                StakArtwork(item.cinematicImage,null,Modifier.fillMaxSize().then(shared).graphicsLayer{
                    val offset=(pager.currentPage-page)+pager.currentPageOffsetFraction
                    transformOrigin=TransformOrigin(.5f,0f)
                    scaleX=if(reduced)1f else 1f+abs(offset)*.02f;scaleY=scaleX
                    translationX=if(reduced)0f else offset*size.width*.045f
                },alignment=Alignment.TopCenter,fallbackModel=item.cinematicImageFallback)
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha=.56f),.22f to Color.Transparent,.48f to Color.Black.copy(alpha=.08f),.75f to Color.Black.copy(alpha=.42f),1f to Color.Black.copy(alpha=.58f))))
        }
        Text("TRENDING TODAY",Modifier.align(Alignment.TopStart).statusBarsPadding()
            .padding(start=16.dp,end=16.dp,top=22.dp).testTag("hero-trending-label"),
            fontFamily=DotoFont,fontSize=11.sp,letterSpacing=1.2.sp,color=StakTextSoft,fontWeight=FontWeight.Bold)
        AnimatedContent(current,Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start=28.dp,end=28.dp,bottom=46.dp),
            contentKey={it.mediaKey},
            transitionSpec={
                if(reduced)fadeIn(tween(100)) togetherWith fadeOut(tween(80))
                else (fadeIn(tween(360,100))+slideInVertically(tween(460,easing=StakMotion.Ease)){it/9}) togetherWith
                    (fadeOut(tween(150))+slideOutVertically(tween(200)){-it/12})
            },label="hero-title-dissolve"){title->
            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(9.dp)){
                var failed by remember(title.logo){mutableStateOf(false)}
                Box(Modifier.widthIn(max=288.dp).fillMaxWidth().height(62.dp).testTag("home-hero-brand"),contentAlignment=Alignment.Center){
                    if(title.logo!=null&&!failed)AsyncImage(CinematicArtwork.original(title.logo),title.name,contentScale=ContentScale.Fit,alignment=Alignment.Center,onError={failed=true},modifier=Modifier.fillMaxSize())
                    else Text(title.name,fontFamily=DotoFont,fontWeight=FontWeight.Black,fontSize=26.sp,lineHeight=31.sp,color=Color.White,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterHorizontally),verticalArrangement=Arrangement.spacedBy(5.dp)){
                    Text((listOfNotNull(title.year,title.runtime)+title.genres.take(1)).joinToString(" · "),fontFamily=HankenFont,fontSize=12.sp,lineHeight=19.sp,color=Color(0xE8FFFFFF),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    title.certification?.takeIf{it.isNotBlank()}?.let{CertificationBadge(it,Modifier.padding(top=1.dp))}
                }
            }
        }
        if(count>1)androidx.compose.foundation.lazy.LazyRow(
            Modifier.align(Alignment.BottomCenter).widthIn(max=320.dp).fillMaxWidth().height(44.dp),
            horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
            items(count){position->
                val width=animateDpAsState(if(index==position)24.dp else 12.dp,if(reduced)snap()else tween(260,easing=StakMotion.Ease),label="hero-page-mark")
                Box(Modifier.size(44.dp).semantics{contentDescription="Show featured title ${position+1}";selected=index==position}.clickable{
                    scope.launch{pager.animateScrollToPage(position+1,animationSpec=tween(if(reduced)0 else 760,easing=StakMotion.Ease))}
                },contentAlignment=Alignment.Center){
                    val ink=if(index==position)StakText else Color.White.copy(alpha=.26f)
                    androidx.compose.foundation.Canvas(Modifier.width(26.dp).height(2.dp)){
                        val pixels=width.value.toPx()
                        drawRoundRect(ink,topLeft=androidx.compose.ui.geometry.Offset((size.width-pixels)/2,0f),size=androidx.compose.ui.geometry.Size(pixels,size.height),cornerRadius=androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                    }
                }
            }
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
