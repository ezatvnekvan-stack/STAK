@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.stak.nativeapp.ui.screens
import androidx.compose.foundation.shape.RoundedCornerShape
import app.stak.nativeapp.ui.components.pressMotion
import app.stak.nativeapp.ui.components.GlassAlertDialog

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import dev.chrisbanes.haze.*
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.stak.nativeapp.ui.components.recordGlassSource
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.stak.nativeapp.data.TitleDetails
import app.stak.nativeapp.data.Episode
import app.stak.nativeapp.data.MediaItem
import app.stak.nativeapp.data.SourceRelease
import app.stak.nativeapp.ui.components.nothingSignal
import app.stak.nativeapp.data.WatchRules
import app.stak.nativeapp.ui.components.StakButton
import app.stak.nativeapp.ui.components.StakChip
import app.stak.nativeapp.ui.components.StakProgress
import app.stak.nativeapp.ui.components.stakSharedElement
import app.stak.nativeapp.ui.components.nothingReveal
import app.stak.nativeapp.ui.components.StakArtwork
import app.stak.nativeapp.ui.theme.DotoFont
import app.stak.nativeapp.ui.theme.HankenFont
import app.stak.nativeapp.ui.theme.Lucide
import app.stak.nativeapp.ui.theme.SpaceMonoFont
import app.stak.nativeapp.ui.theme.StakAccent
import app.stak.nativeapp.ui.theme.StakAccentSoft
import app.stak.nativeapp.ui.theme.StakBorderSoft
import app.stak.nativeapp.ui.theme.StakSurface
import app.stak.nativeapp.ui.theme.StakSurface2
import app.stak.nativeapp.ui.theme.StakText
import app.stak.nativeapp.ui.theme.StakTextFaint
import app.stak.nativeapp.ui.theme.StakTextMuted
import app.stak.nativeapp.ui.theme.StakTextSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleScreen(
    item: MediaItem,
    onBack: () -> Unit,
    isWatchlisted: Boolean,
    watchlistBusy: Boolean,
    onToggleWatchlist: (MediaItem) -> Unit,
    sharedScope: SharedTransitionScope?,
    animatedScope: AnimatedVisibilityScope?,
    sharedKey: String = "art-${item.mediaKey}",
    isWatched: Boolean = false,
    onMarkWatched: ((MediaItem,Boolean) -> Unit)? = null,
    titleStates: Map<String,Boolean> = emptyMap(),
    watchedIds: Set<String> = emptySet(),
    episodeStates: Map<String, Boolean> = emptyMap(),
    onMarkEpisode: (MediaItem, Episode, Boolean) -> Unit = { _, _, _ -> },
    onMarkSeason: (MediaItem, List<Episode>) -> Unit = { _, _ -> },
    onSetSeasonWatched:(MediaItem,List<Episode>,Boolean)->Unit={m,e,played->if(played)onMarkSeason(m,e)},
    onOpenTitle: (MediaItem) -> Unit = {},
    onOpenSourceSettings: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenTorbox: () -> Unit = {},
    onOpenSeriesCenter:((MediaItem)->Unit)? = null,
    syncNotice:String? = null,
    viewModel: TitleViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val detail by viewModel.detail.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val seasonLoading by viewModel.seasonLoading.collectAsState()
    val seasonError by viewModel.seasonError.collectAsState()
    val selectedSeason by viewModel.season.collectAsState()
    var restoredSeason by androidx.compose.runtime.saveable.rememberSaveable(item.mediaKey){mutableStateOf(item.season)}
    val availableSeasons=detail?.seasons.orEmpty()
    LaunchedEffect(availableSeasons){restoredSeason?.takeIf{it in availableSeasons&&it!=viewModel.season.value}?.let{viewModel.selectSeason(it)}}
    var synopsisExpanded by androidx.compose.runtime.saveable.rememberSaveable(item.mediaKey) { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var showAutoAdd by remember { mutableStateOf(false) }
    var trailerOpen by remember(item.mediaKey){mutableStateOf(false)}

    LaunchedEffect(item.type, item.mediaKey,item.jellyfinId) {
        viewModel.ensureLoaded(item.type, item.mediaKey,item)
    }

    val data = detail
    val media = data?.item?.copy(jellyfinId = item.jellyfinId, progress = item.progress, watched = item.watched, positionMs=item.positionMs, durationMs=item.durationMs, jellyfinUserDataKnown=item.jellyfinUserDataKnown,jellyfinVideoHeight=item.jellyfinVideoHeight,jellyfinHdr=item.jellyfinHdr) ?: item
    val synced by app.stak.nativeapp.data.MediaLiveSync.state.collectAsState()
    LaunchedEffect(synced.lastSuccessAt,media.mediaKey){if(synced.lastSuccessAt>0&&data!=null){kotlinx.coroutines.delay(1500);viewModel.refreshWatched(media)}}
    val remoteWatched by viewModel.remoteWatched.collectAsState()
    val titleWatched = remoteWatched[media.mediaKey] ?: titleStates[media.mediaKey] ?: titleStates[media.id] ?: (isWatched || media.watched || app.stak.nativeapp.ui.components.LocalPosterActions.current?.isWatched?.invoke(media)==true || media.mediaKey in watchedIds || media.id in watchedIds)
    val episodes = data?.episodes.orEmpty().map { ep -> ep.copy(watched = remoteWatched["${media.mediaKey}:${ep.season}:${ep.episode}"] ?: episodeStates["${media.mediaKey}:${ep.season}:${ep.episode}"] ?: episodeStates["${media.id}:${ep.season}:${ep.episode}"] ?: ep.watched) }
    LaunchedEffect(data?.item?.mediaKey,item.season,item.episode) {data?.episodes?.firstOrNull{it.season==item.season&&it.episode==item.episode}?.let{viewModel.selectEpisode(it)}}
    var episodeDialog by remember(media.mediaKey) { mutableStateOf<Episode?>(null) }
    val seasonEpisodes=episodes.filter{it.season==selectedSeason}
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val scroll=androidx.compose.foundation.lazy.rememberLazyListState()
    val chromeHaze=remember{HazeState()}
    val backgroundLayer=androidx.compose.ui.graphics.rememberGraphicsLayer()
    val backgroundStyle=app.stak.nativeapp.ui.components.LocalBackgroundStyle.current
    val backgroundSource=remember(backgroundLayer,backgroundStyle){app.stak.nativeapp.ui.components.GlassSource(backgroundLayer,preBlurred=true,static=backgroundStyle!=app.stak.nativeapp.data.BackgroundStyle.Glyph,hazeState=chromeHaze)}
    val backgroundView=androidx.compose.ui.platform.LocalView.current
    val artHeight=app.stak.nativeapp.ui.components.detailArtworkHeight(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp).dp
    val stickyThreshold=with(androidx.compose.ui.platform.LocalDensity.current){(artHeight-40.dp).roundToPx()}
    val showHeader by remember{androidx.compose.runtime.derivedStateOf{scroll.firstVisibleItemIndex>0||scroll.firstVisibleItemScrollOffset>stickyThreshold}}
    val artworkTint=app.stak.nativeapp.ui.components.rememberArtworkTint(media.background ?: media.poster)
    val fallbackMotion=app.stak.nativeapp.ui.components.rememberCinematicMotion()
    val cinemaMotion=app.stak.nativeapp.ui.components.LocalCinematicMotion.current ?: fallbackMotion
    val titlePalette=app.stak.nativeapp.ui.components.rememberCinemaPalette(artworkTint.value)
    val titleSurface=titlePalette.surface.copy(alpha=.68f)
    val atmosphere=androidx.compose.runtime.rememberUpdatedState(titlePalette.glow)
    androidx.compose.runtime.CompositionLocalProvider(app.stak.nativeapp.ui.components.LocalTitleSurface provides titleSurface,app.stak.nativeapp.ui.components.LocalInlineGlassSource provides backgroundSource,app.stak.nativeapp.ui.components.LocalCinematicMotion provides cinemaMotion) {
    Box(modifier = Modifier.fillMaxSize()) {
        app.stak.nativeapp.ui.components.ArtworkAtmosphere(atmosphere,Modifier.recordGlassSource(backgroundSource,backgroundView){titlePalette.glow},paused={scroll.isScrollInProgress||showSources||trailerOpen||episodeDialog!=null})
        Column(Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(visible=showHeader,enter=androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(if(app.stak.nativeapp.ui.components.LocalReducedMotion.current)0 else 160)),exit=androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(if(app.stak.nativeapp.ui.components.LocalReducedMotion.current)0 else 100))){
            Column(Modifier.statusBarsPadding()){DetailHeader(media.name,chromeHaze,onBack)}
        }
        LazyColumn(
            state=scroll,modifier = Modifier.fillMaxWidth().weight(1f).testTag("detail-content"),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            item(key="hero",contentType="hero") {
                TitleHero(
                    media = media,
                    details = data,
                    onBack = onBack,
                    topInset = topInset,
                    sharedKey = sharedKey,
                    sharedScope = sharedScope,
                    animatedScope = animatedScope,
                    parallax={if(scroll.firstVisibleItemIndex==0)scroll.firstVisibleItemScrollOffset*.10f else 0f}
                )
            }
            item(key="management",contentType="management") {
                Column(Modifier.padding(horizontal=16.dp).padding(top=8.dp).nothingReveal("title-actions:${media.mediaKey}",2),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    val metadataNotice=data?.notice?:if(loading)"Loading metadata…"else viewModel.error.collectAsState().value.orEmpty()
                    if(metadataNotice.isNotBlank())Text(metadataNotice,fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted)
                    if(!data?.notice.isNullOrBlank()||viewModel.error.collectAsState().value!=null)StakButton("Retry metadata",{viewModel.load(media.type,media.mediaKey,media)})
                    DetailActionRow(media.mediaKey,isWatchlisted,watchlistBusy,data?.trailerUrl!=null,
                        onSources={viewModel.setPackMode(media.isSeries);showSources=true},onTrailer={trailerOpen=true},onWatchlist={onToggleWatchlist(media)})
                    val connected=app.stak.nativeapp.data.NativeRuntime.settings.jellyfinConnected
                    val available=connected&&(remoteWatched.isNotEmpty()||media.jellyfinId!=null||app.stak.nativeapp.ui.components.LocalPosterActions.current?.isInJellyfin?.invoke(media)==true)
                    DetailLibraryState(media,available,connected,titleWatched)
                    if(media.isSeries){
                        val autoRevision by WatchRules.revision.collectAsState()
                        val rule=remember(autoRevision,media.mediaKey){WatchRules.seriesKeyOf(media)?.let{WatchRules.ruleFor(it)}}
                        DetailAutoAdd(rule){showAutoAdd=true}
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(media.overview?:"No synopsis available yet.",fontFamily=HankenFont,fontSize=14.sp,lineHeight=22.sp,color=StakTextSoft,maxLines=if(synopsisExpanded)Int.MAX_VALUE else 3,overflow=TextOverflow.Ellipsis)
                    if(media.overview.orEmpty().length>160)StakButton(if(synopsisExpanded)"Read less"else "Read more",{synopsisExpanded=!synopsisExpanded},compact=true,variant=app.stak.nativeapp.ui.components.StakButtonVariant.Ghost)
                }
            }
            if(media.isSeries&&!data?.seasons.isNullOrEmpty()){
                item(key="season-control",contentType="season-control"){
                    Column(Modifier.padding(horizontal=16.dp).padding(top=26.dp,bottom=8.dp)){
                        DetailSeasonSelector(data?.seasons.orEmpty(),selectedSeason,seasonEpisodes,seasonLoading){season->restoredSeason=season;viewModel.selectSeason(season)}
                        if(seasonError!=null)StakButton("Retry season",{viewModel.selectSeason(selectedSeason)})
                        if(!seasonLoading&&seasonError==null&&seasonEpisodes.isEmpty())Text("No episodes available for this season.",Modifier.padding(vertical=12.dp),fontFamily=HankenFont,fontSize=13.sp,color=StakTextMuted)
                    }
                }
                items(seasonEpisodes,key={"episode:${media.mediaKey}:${it.season}:${it.episode}"},contentType={"episode-row"}){episode->
                    Box(Modifier.padding(horizontal=16.dp).nothingReveal("season-entry:${media.mediaKey}:${episode.season}:${episode.episode}",1)){DetailEpisodeRow(episode,next=seasonEpisodes.any{it.watched}&&episode==seasonEpisodes.filterNot{it.watched}.minByOrNull{it.episode}){viewModel.selectEpisode(episode);episodeDialog=episode}}
                }
            }

            item(key="extras",contentType="extras") { TitleExtras(data, media, onOpenTitle) }

        }

        }

    }

    }

    episodeDialog?.let { requested ->
        val fresh = episodes.firstOrNull { it.season == requested.season && it.episode == requested.episode } ?: requested
        EpisodeDetailsSheet(media, fresh, onDismiss = { episodeDialog = null }, onToggleWatched = {}, onFindSources = { episodeDialog = null; viewModel.setPackMode(false); showSources = true })
    }

    if(trailerOpen)data?.trailerUrl?.let{TrailerDialog(media.name,it,{trailerOpen=false})}

    if (showSources) FindSourcesSheet(media, episodes, viewModel, onDismiss = { showSources = false }, onOpenSettings = onOpenSourceSettings, onOpenLibrary = onOpenTorbox)

    if (showAutoAdd) AutoAddSheet(media, data?.seasons.orEmpty().ifEmpty { listOf(1) }, onDismiss = { showAutoAdd = false }, onOpenSettings = onOpenSettings)

}

@Composable
private fun TitleHero(
    media:MediaItem,details:TitleDetails?,onBack:()->Unit,topInset:androidx.compose.ui.unit.Dp,
    sharedKey:String,sharedScope:SharedTransitionScope?,animatedScope:AnimatedVisibilityScope?,parallax:()->Float={0f}
){
    val reduced=app.stak.nativeapp.ui.components.LocalReducedMotion.current
    val imageHeight=app.stak.nativeapp.ui.components.detailArtworkHeight(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp).dp
    Column(Modifier.fillMaxWidth().testTag("detail-hero"),horizontalAlignment=Alignment.CenterHorizontally){
        Box(Modifier.fillMaxWidth().height(imageHeight).testTag("detail-hero-artwork")){
            Box(Modifier.matchParentSize().stakSharedElement(sharedScope,animatedScope,sharedKey)
                .graphicsLayer{compositingStrategy=CompositingStrategy.Offscreen}.drawWithCache{
                    val mask=Brush.verticalGradient(0f to Color.White,.48f to Color.White,.78f to Color.White.copy(alpha=.65f),1f to Color.Transparent)
                    onDrawWithContent{drawContent();drawRect(mask,blendMode=BlendMode.DstIn)}
                }){
                StakArtwork(media.background?:media.poster,null,Modifier.matchParentSize().graphicsLayer{
                    translationY=if(reduced)0f else parallax().coerceAtMost(32.dp.toPx())
                    scaleX=if(reduced)1f else 1.08f;scaleY=scaleX
                })
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha=.18f),.4f to Color.Transparent,1f to Color.Black.copy(alpha=.68f))))
            }
            app.stak.nativeapp.ui.components.StakIconButton(onBack,Modifier.align(Alignment.TopStart).padding(start=12.dp,top=topInset+8.dp)){
                Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back",tint=StakText,modifier=Modifier.size(21.dp))
            }
            val logo=details?.logo?:media.logo
            var logoFailed by remember(logo){mutableStateOf(false)}
            Box(Modifier.align(Alignment.BottomCenter).padding(horizontal=28.dp).padding(bottom=14.dp)
                .fillMaxWidth().heightIn(min=72.dp).nothingReveal("title-brand:${media.mediaKey}",1).testTag("detail-title-brand"),contentAlignment=Alignment.Center){
                if(logo!=null&&!logoFailed)AsyncImage(logo,media.name,onError={logoFailed=true},contentScale=ContentScale.Fit,alignment=Alignment.Center,modifier=Modifier.widthIn(max=300.dp).fillMaxWidth().height(80.dp))
                else Text(media.name,fontFamily=DotoFont,fontWeight=FontWeight.Black,fontSize=30.sp,lineHeight=36.sp,color=StakText,maxLines=3,overflow=TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        TitleMetadata(media,details,Modifier.padding(horizontal=20.dp).padding(top=2.dp,bottom=18.dp).nothingReveal("title-copy:${media.mediaKey}",2))
    }
}

internal fun runtimeMinutes(raw: String): String {
    val hours = Regex("(\\d+)\\s*h", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val minutes = Regex("(\\d+)\\s*m", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val total = hours * 60 + minutes
    return if (total > 0) "$total min" else raw
}

