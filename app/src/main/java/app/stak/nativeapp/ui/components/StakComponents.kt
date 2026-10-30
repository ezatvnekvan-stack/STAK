@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.stak.nativeapp.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.stak.nativeapp.data.MediaItem
import app.stak.nativeapp.ui.theme.DotoFont
import app.stak.nativeapp.ui.theme.HankenFont
import app.stak.nativeapp.ui.theme.SpaceMonoFont
import app.stak.nativeapp.ui.theme.StakAccent
import app.stak.nativeapp.ui.theme.StakAccentSoft
import app.stak.nativeapp.ui.theme.StakBorder
import app.stak.nativeapp.ui.theme.StakBorderSoft
import app.stak.nativeapp.ui.theme.StakGlass
import app.stak.nativeapp.ui.theme.StakGlassStrong
import app.stak.nativeapp.ui.theme.StakGold
import app.stak.nativeapp.ui.theme.StakSurface
import app.stak.nativeapp.ui.theme.StakSurface2
import app.stak.nativeapp.ui.theme.StakSurface3
import app.stak.nativeapp.ui.theme.StakText
import app.stak.nativeapp.ui.theme.StakTextFaint
import app.stak.nativeapp.ui.theme.StakTextMuted
import app.stak.nativeapp.ui.theme.StakTextSoft

enum class StakButtonVariant { Primary, Outlined, Ghost, Destructive }



@Composable
fun StakBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    StakIconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = StakText, modifier = Modifier.size(18.dp))
    }
}


@Composable
fun StakToast(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(StakGlassStrong)
            .border(1.dp, StakBorder, CircleShape)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(StakAccent))
        Spacer(Modifier.width(8.dp))
        Text(text, fontFamily = SpaceMonoFont, fontSize = 10.sp, color = StakTextSoft)
    }
}


@Composable
fun StakSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title.uppercase(),
            fontFamily = DotoFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            lineHeight = 21.75.sp,
            letterSpacing = 0.675.sp,
            color = StakText
        )
        if (actionLabel != null && onAction != null) {
            Row(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    actionLabel.uppercase(),
                    fontFamily = SpaceMonoFont,
                    fontWeight = FontWeight.Normal,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = StakTextMuted
                )
                if (!actionLabel.endsWith("→")) {
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = StakTextMuted,
                    modifier = Modifier.size(12.dp)
                )
                }
            }
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(StakGlass)
            .border(1.dp, StakBorder, RoundedCornerShape(12.dp)),
        content = content
    )
}

@Composable
fun StakProgress(value:Float,modifier:Modifier=Modifier,height:Int=6,color:Color=app.stak.nativeapp.ui.theme.StakAccentInk,track:Color=Color(0xFF292929),glow:Boolean=false) {
    val progress=if(value.isFinite())value.coerceIn(0f,1f) else 0f
    val animated=animateFloatAsState(progress,if(LocalReducedMotion.current)snap() else tween(StakMotion.Standard,easing=StakMotion.Ease),label="progress")
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(height.dp).then(Modifier.semantics{progressBarRangeInfo=androidx.compose.ui.semantics.ProgressBarRangeInfo(progress,0f..1f)})) {
        val gap=2.dp.toPx();val count=(size.width/10.dp.toPx()).toInt().coerceIn(6,40)
        val width=((size.width-gap*(count-1))/count).coerceAtLeast(0f);val radius=androidx.compose.ui.geometry.CornerRadius.Zero
        repeat(count){index->
            val left=index*(width+gap)
            drawRoundRect(track,androidx.compose.ui.geometry.Offset(left,0f),androidx.compose.ui.geometry.Size(width,size.height),radius)
            val fraction=(animated.value*count-index).coerceIn(0f,1f)
            if(fraction>0)drawRoundRect(color,androidx.compose.ui.geometry.Offset(left,0f),androidx.compose.ui.geometry.Size(width*fraction,size.height),radius)
        }
    }
}

@Composable
fun StakLoadingProgress(modifier:Modifier=Modifier) {
    val phase=if(LocalReducedMotion.current)androidx.compose.runtime.rememberUpdatedState(.7f) else rememberInfiniteTransition(label="segmented-loading").animateFloat(0f,1.4f,infiniteRepeatable(tween(1300,easing=LinearEasing),RepeatMode.Restart),label="loading-position")
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(4.dp).then(Modifier.semantics{progressBarRangeInfo=androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate})) {
        val gap=2.dp.toPx();val count=(size.width/10.dp.toPx()).toInt().coerceIn(6,40);val width=((size.width-gap*(count-1))/count).coerceAtLeast(0f)
        repeat(count){index->val distance=kotlin.math.abs(index.toFloat()/count-phase.value+.2f);val tint=if(distance<.18f)app.stak.nativeapp.ui.theme.StakAccentInk.copy(alpha=(1-distance/.18f).coerceIn(.25f,1f)) else Color(0xFF292929);drawRoundRect(tint,androidx.compose.ui.geometry.Offset(index*(width+gap),0f),androidx.compose.ui.geometry.Size(width,size.height),androidx.compose.ui.geometry.CornerRadius.Zero)}
    }
}

@Composable
fun StakTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    secret: Boolean = false,
    height: Int = 48,
    onSubmit: (() -> Unit)? = null,
    textStyle: TextStyle = TextStyle(fontFamily = HankenFont, fontSize = 14.sp, color = StakText),
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(12.dp)
    var fieldFocused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val fieldEdge by androidx.compose.animation.animateColorAsState(if(fieldFocused)Color.White.copy(alpha=.7f) else Color.Transparent,if(LocalReducedMotion.current)androidx.compose.animation.core.snap() else tween(160),label="field-focus")
    MatteSurface(modifier.fillMaxWidth().border(1.dp,fieldEdge,shape)) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = height.dp)
            .clip(shape)
            .padding(horizontal = if (leading != null) 20.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                enabled = enabled,
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                textStyle = textStyle,
                cursorBrush = Brush.verticalGradient(listOf(StakAccent, StakAccent)),
                keyboardOptions = KeyboardOptions(keyboardType = if (secret) androidx.compose.ui.text.input.KeyboardType.Password else keyboardType, imeAction = if (onSubmit != null) ImeAction.Search else ImeAction.Default),
                keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
                modifier = Modifier.fillMaxWidth().heightIn(min = height.dp).onFocusChanged{fieldFocused=it.isFocused},
                decorationBox = { innerField ->
                    Box(Modifier.fillMaxWidth().heightIn(min = height.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text(placeholder, fontFamily = HankenFont, fontSize = 14.sp, color = StakTextFaint, maxLines = 1)
                        innerField()
                    }
                }
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
    }
}

@Composable
fun StakPosterCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
    badge: String? = null,
    compactTitle: Boolean = false,
    reserveTitleLines: Boolean = false,
    inWatchlist: Boolean = false,
    showTypeSuffix: Boolean = false,
    titleSize: Int = 0,
    yearSize: Int = 0,
    yearColor: Color = Color(0xFF737373),
    rank:Int?=null,
    showLibraryBadge:Boolean=true
) {
    val press=rememberPressMotion()
    var posterMenu by androidx.compose.runtime.remember(item.mediaKey){androidx.compose.runtime.mutableStateOf(false)}
    val posterActions=LocalPosterActions.current
    val titleFontSize = if (titleSize > 0) titleSize.sp else (if (compactTitle) 12.sp else 13.sp)
    val titleLineHeight = titleFontSize * 1.4f
    val yearFontSize = if (yearSize > 0) yearSize.sp else 10.sp
    val yearLineHeight = if (yearSize > 0) (yearFontSize * 1.4f) else 15.sp
    val shape = RoundedCornerShape(12.dp)
    Box(modifier) {
    Column(modifier = Modifier.fillMaxWidth().pressMotion(press).combinedClickable(interactionSource=press.source,indication=LocalIndication.current,onClick=onClick,onLongClick={posterMenu=true})) {
        Box(Modifier.fillMaxWidth().padding(start=if(rank!=null)24.dp else 0.dp)){
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(StakSurface)
                .border(1.dp, Color(0x21FFFFFF), shape)
                .then(if(rank!=null)Modifier.testTag("ranked-artwork-${item.mediaKey}")else Modifier)
        ) {
            Icon(app.stak.nativeapp.ui.theme.Lucide.Film,null,tint=StakTextFaint,modifier=Modifier.align(Alignment.Center).size(24.dp))
            AsyncImage(
                model = item.poster ?: item.background,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            // Web posters draw a from-black/90 gradient over the lower half.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.90f))
                        )
                    )
            )
            if (item.watched) {
                PosterGlyphChip(
                    border = Color(0xFF10B981).copy(alpha = 0.60f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Icon(
                        app.stak.nativeapp.ui.theme.Lucide.CheckBold,
                        contentDescription = "Watched",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
            if (showProgress && item.progress > 0f && !item.watched) {
                StakProgress(item.progress,Modifier.align(Alignment.BottomCenter),height=4)
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC080808))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        badge.uppercase(),
                        fontFamily = SpaceMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        letterSpacing = 0.4.sp,
                        color = Color.White
                    )
                }
            }
            if (inWatchlist) {
                PosterGlyphChip(
                    border = StakBorder,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        app.stak.nativeapp.ui.theme.Lucide.BookmarkFilled,
                        contentDescription = "In watchlist",
                        tint = StakAccent,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
        rank?.let{PosterRank(it,Modifier.align(Alignment.BottomStart).offset(x=(-24).dp,y=4.dp))}
        }
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.padding(start=if(rank!=null)25.dp else 1.dp,end=1.dp)) {
            Text(
                item.name,
                fontFamily = HankenFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = titleFontSize,
                lineHeight = titleLineHeight,
                color = Color.White,
                minLines = 1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val base = item.releaseInfo ?: item.year
            val meta = if (showTypeSuffix && !base.isNullOrBlank()) {
                "$base · ${if (item.type == "series") "Series" else "Movie"}"
            } else {
                base
            }
            Row(Modifier.fillMaxWidth().padding(top=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text(meta.orEmpty(),fontFamily=SpaceMonoFont,fontSize=yearFontSize,lineHeight=yearLineHeight,color=yearColor,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                if(showLibraryBadge&&(posterActions?.isInJellyfin?.invoke(item) ?: (item.jellyfinId!=null))){Spacer(Modifier.width(6.dp));JellyfinBadge(compact=true)}
            }
        }
    }
    PosterContextMenu(item,posterMenu,{posterMenu=false},onClick)
    }
}

/** Web `.glyph` chip: pill radius, #111111/60 background, 1px border. */
@Composable
private fun PosterGlyphChip(
    border: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x99111111))
            .border(1.dp, border, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
fun StakEmpty(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(StakSurface2)
            .border(1.dp, StakBorder, RoundedCornerShape(20.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(StakSurface3)
                .border(1.dp, StakBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(StakAccent))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontFamily = DotoFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = StakText)
        Spacer(Modifier.height(6.dp))
        Text(text, fontFamily = HankenFont, fontSize = 12.sp, lineHeight = 17.sp, color = StakTextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            StakButton(actionLabel, onAction, variant = StakButtonVariant.Primary, compact = true)
        }
    }
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, corner: Int = 16) {
    val alpha=if(LocalReducedMotion.current)androidx.compose.runtime.rememberUpdatedState(.4f) else rememberInfiniteTransition(label="shimmer").animateFloat(.25f,.55f,infiniteRepeatable(tween(1100,easing=LinearEasing),RepeatMode.Reverse),label="alpha")
    Box(modifier.clip(RoundedCornerShape(corner.dp)).drawBehind{drawRect(StakSurface3.copy(alpha=alpha.value))})
}


@Composable
fun StakDivider(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(StakBorderSoft))
}
