@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

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
import app.stak.nativeapp.ui.components.StakButton
import app.stak.nativeapp.ui.components.StakChip
import app.stak.nativeapp.ui.components.StakProgress
import app.stak.nativeapp.ui.components.stakSharedElement
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
    onOpenTorbox: () -> Unit = {},
    syncNotice:String? = null,
    viewModel: TitleViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val detail by viewModel.detail.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val selectedSeason by viewModel.season.collectAsState()
    val selectedEpisode by viewModel.episode.collectAsState()
    var synopsisExpanded by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var trailerOpen by remember(item.mediaKey){mutableStateOf(false)}
    var sourceFilter by remember { mutableStateOf("All") }

    LaunchedEffect(item.type, item.mediaKey,item.jellyfinId) {
        viewModel.load(item.type, item.mediaKey,item)
    }

    val data = detail
    val media = data?.item?.copy(jellyfinId = item.jellyfinId, progress = item.progress, watched = item.watched, positionMs=item.positionMs, durationMs=item.durationMs, jellyfinUserDataKnown=item.jellyfinUserDataKnown) ?: item
    val synced by app.stak.nativeapp.data.MediaLiveSync.state.collectAsState()
    LaunchedEffect(synced.lastSuccessAt,media.mediaKey){if(synced.lastSuccessAt>0&&data!=null){kotlinx.coroutines.delay(1500);viewModel.refreshWatched(media)}}
    val remoteWatched by viewModel.remoteWatched.collectAsState()
    val titleWatched = remoteWatched[media.mediaKey] ?: titleStates[media.mediaKey] ?: titleStates[media.id] ?: (isWatched || media.watched || app.stak.nativeapp.ui.components.LocalPosterActions.current?.isWatched?.invoke(media)==true || media.mediaKey in watchedIds || media.id in watchedIds)
    val episodes = data?.episodes.orEmpty().map { ep -> ep.copy(watched = remoteWatched["${media.mediaKey}:${ep.season}:${ep.episode}"] ?: episodeStates["${media.mediaKey}:${ep.season}:${ep.episode}"] ?: episodeStates["${media.id}:${ep.season}:${ep.episode}"] ?: ep.watched) }
    LaunchedEffect(data?.item?.mediaKey,item.season,item.episode) {data?.episodes?.firstOrNull{it.season==item.season&&it.episode==item.episode}?.let{viewModel.selectEpisode(it)}}
    val activeEpisode = episodes.firstOrNull { it.season == selectedEpisode?.season && it.episode == selectedEpisode?.episode }
    var episodeDialog by remember(media.mediaKey) { mutableStateOf<Episode?>(null) }
    data class SeasonOperation(val media:MediaItem,val season:Int,val episodes:List<Episode>,val played:Boolean)
    var seasonOperation by remember(media.mediaKey){mutableStateOf<SeasonOperation?>(null)}
    val seasonEpisodes=episodes.filter{it.season==selectedSeason}
    val seasonWatched=seasonEpisodes.isNotEmpty()&&seasonEpisodes.all{it.watched}
    val seriesWatched=episodes.isNotEmpty()&&episodes.all{it.watched}
    val selectedWatched = if (media.isSeries) activeEpisode?.watched == true else titleWatched
    val sources by viewModel.sources.collectAsState()
    val sourceLoading by viewModel.sourceLoading.collectAsState()
    val sourceError by viewModel.sourceError.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val toast: (String) -> Unit = { message ->
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }
    val filteredSources = remember(sources, sourceFilter) {
        if (sourceFilter == "All") sources else sources.filter { it.resolution == sourceFilter }
    }
    val gallery = remember(media.mediaKey, data?.gallery) {
        (data?.gallery.orEmpty() + listOfNotNull(media.background))
            .distinct()
            .take(6)
    }
    val topInset = 0.dp // StakApp already reserves the system status bar.

    val scroll=androidx.compose.foundation.lazy.rememberLazyListState()
    val chromeHaze=remember{HazeState()}
    val backgroundLayer=androidx.compose.ui.graphics.rememberGraphicsLayer()
    val backgroundSource=remember(backgroundLayer){app.stak.nativeapp.ui.components.GlassSource(backgroundLayer,preBlurred=true,static=true,hazeState=chromeHaze)}
    val backgroundView=androidx.compose.ui.platform.LocalView.current
    val stickyThreshold=with(androidx.compose.ui.platform.LocalDensity.current){200.dp.roundToPx()}
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
            DetailHeader(media.name,chromeHaze,onBack)
        }
        LazyColumn(
            state=scroll,modifier = Modifier.fillMaxWidth().weight(1f).testTag("detail-content"),
            contentPadding = PaddingValues(bottom = 24.dp)
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
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    val metadataNotice=data?.notice ?: if(loading) "Loading metadata…" else viewModel.error.collectAsState().value.orEmpty()
                    if(metadataNotice.isNotBlank())Text(
                        metadataNotice,
                        fontFamily = HankenFont,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = StakTextMuted
                    )
                    if (!data?.notice.isNullOrBlank() || viewModel.error.collectAsState().value != null) StakButton("Retry metadata", { viewModel.load(media.type, media.mediaKey) })
                    Spacer(Modifier.height(12.dp))
                    val connected=app.stak.nativeapp.data.NativeRuntime.settings.jellyfinConnected
                    val available=connected&&(remoteWatched.isNotEmpty()||media.jellyfinId!=null||app.stak.nativeapp.ui.components.LocalPosterActions.current?.isInJellyfin?.invoke(media)==true)
                    TitleManagementPanel(media,available,connected,isWatchlisted,watchlistBusy,data?.trailerUrl!=null,selectedWatched,
                        activeEpisode?.let{"S${it.season.toString().padStart(2,'0')} E${it.episode.toString().padStart(2,'0')}"},selectedSeason,seasonWatched,
                        seasonEpisodes.count{it.watched},seasonEpisodes.size,!media.isSeries||activeEpisode!=null,syncNotice,
                        onTrailer={trailerOpen=true},onWatchlist={onToggleWatchlist(media)},
                        onWatched={},
                        onSeason={},showSeasonProgress=false)
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 4.dp)
                    ) {
                        Text(
                            "SYNOPSIS",
                            fontFamily = DotoFont,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = StakText
                        )
                        Spacer(Modifier.height(9.dp))
                        Text(
                            media.overview ?: "No synopsis available yet.",
                            fontFamily = HankenFont,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = Color(0xFFD4D4D2),
                            maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (media.overview.orEmpty().length > 220) Text(
                            if (synopsisExpanded) "Read less" else "Read more",
                            fontFamily = SpaceMonoFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = Color(0xFFE5E5E5),
                            textDecoration = TextDecoration.None,
                            modifier = Modifier
                                .clickable { synopsisExpanded = !synopsisExpanded }
                                .heightIn(min=44.dp).padding(vertical = 12.dp)
                        )
                    }
                }
            }

            if (media.isSeries && !data?.seasons.isNullOrEmpty()) {
                item(key="episodes",contentType="episodes") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "EPISODES",
                            fontFamily = DotoFont,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            lineHeight = 20.3.sp,
                            color = StakText
                        )
                        Spacer(Modifier.height(12.dp))
                        val seasons = data?.seasons.orEmpty().ifEmpty { listOf(1) }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(seasons,key={it}) { season ->
                                StakChip("S${season.toString().padStart(2,'0')}", selected = selectedSeason == season, onClick = { viewModel.selectSeason(season) })
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if(seasonEpisodes.isNotEmpty())TitleSeasonProgress(selectedSeason,seasonEpisodes.count{it.watched},seasonEpisodes.size,showSeasonLabel=false)
                        else Text("0 episodes",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
                        Spacer(Modifier.height(10.dp))
                    }
                    EpisodeRail(
                        episodes = episodes.filter { it.season == selectedSeason },
                        selected = selectedEpisode ?: episodes.firstOrNull(),
                        onSelect = { viewModel.selectEpisode(it); episodeDialog = it }
                    )
                    if (episodes.none { it.season == selectedSeason }) Text("No episodes are available for this season. Retry metadata if the season could not load.", fontFamily = HankenFont, fontSize = 12.sp, color = StakTextMuted, modifier = Modifier.padding(16.dp))
                }
            }

            item(key="extras",contentType="extras") { TitleExtras(data, media, onOpenTitle) }

        }

        DetailSourceDock(chromeHaze){viewModel.setPackMode(media.isSeries);showSources=true}
        }

    }

    }

    episodeDialog?.let { requested ->
        val fresh = episodes.firstOrNull { it.season == requested.season && it.episode == requested.episode } ?: requested
        EpisodeDetailsSheet(media, fresh, onDismiss = { episodeDialog = null }, onToggleWatched = {}, onFindSources = { episodeDialog = null; viewModel.setPackMode(false); showSources = true })
    }

    if(trailerOpen)data?.trailerUrl?.let{TrailerDialog(media.name,it,{trailerOpen=false})}

    if (showSources) FindSourcesSheet(media, episodes, viewModel, onDismiss = { showSources = false }, onOpenSettings = onOpenSourceSettings, onOpenLibrary = onOpenTorbox)

}

@Composable
private fun TitleHero(
    media: MediaItem,
    details: TitleDetails?,
    onBack: () -> Unit,
    topInset: androidx.compose.ui.unit.Dp,
    sharedKey: String,
    sharedScope: SharedTransitionScope?,
    animatedScope: AnimatedVisibilityScope?,
    parallax:()->Float={0f}
) {
    val heroLayer=androidx.compose.ui.graphics.rememberGraphicsLayer()
    // The hero layer only exists so the back button can sample the artwork. Recording it
    // every frame kept re-rendering the hero offscreen while the (continuously animated)
    // dot matrix redrew the page. Now it is recorded once per image and parallax offset.
    val heroSource=remember(heroLayer){app.stak.nativeapp.ui.components.GlassSource(heroLayer,static=true)}
    val heroView=androidx.compose.ui.platform.LocalView.current
    var heroLoaded by remember(media.background,media.poster){mutableStateOf(false)}
    val reducedHero=app.stak.nativeapp.ui.components.LocalReducedMotion.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 320.dp)
            .stakSharedElement(sharedScope, animatedScope, sharedKey)
    ) {
        Box(Modifier.matchParentSize().recordGlassSource(heroSource,heroView){if(heroLoaded)parallax() else Float.MIN_VALUE}.graphicsLayer{compositingStrategy=CompositingStrategy.Offscreen}.drawWithCache{
            val mask=Brush.verticalGradient(0f to Color.White,.38f to Color.White,.60f to Color.White.copy(alpha=.7f),.82f to Color.White.copy(alpha=.15f),1f to Color.Transparent)
            onDrawWithContent{drawContent();drawRect(mask,blendMode=BlendMode.DstIn)}
        }) {
        AsyncImage(
            model = media.background ?: media.poster,
            contentDescription = media.name,
            contentScale = ContentScale.Crop,
            onSuccess = { heroLoaded = true },
            modifier = Modifier.matchParentSize().graphicsLayer {translationY=if(reducedHero)0f else parallax();scaleX=1.08f;scaleY=1.08f}.alpha(.94f)
        )
        }
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.Transparent, .35f to Color.Transparent, .65f to Color(0x75000000), 1f to Color.Transparent)
            )
        )
        app.stak.nativeapp.ui.components.MatteSurface(
            source=heroSource,modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = topInset + 12.dp)
                .size(44.dp)
                .clip(CircleShape)
                .clickable { onBack() }
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color(0xFFE5E5E5),
                modifier = Modifier.align(Alignment.Center).size(17.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 180.dp, bottom = 12.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            var logoFailed by remember(details?.logo) { mutableStateOf(false) }
            if (details?.logo != null && !logoFailed) AsyncImage(details.logo, media.name, onError = { logoFailed = true }, contentScale = ContentScale.Fit, modifier = Modifier.width(215.dp).heightIn(min = 36.dp, max = 62.dp)) else Text(
                media.name,
                fontFamily = DotoFont,
                fontWeight = FontWeight.Black,
                fontSize = 25.sp,
                lineHeight = 30.sp,
                letterSpacing = 0.618.sp,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(media.year, details?.status?.takeIf { it.isNotBlank() }, media.runtime?.takeIf { it.isNotBlank() }?.let { runtimeMinutes(it) }, details?.certification?.takeIf { it.isNotBlank() }).joinToString(" · "),
                fontFamily = HankenFont,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = Color(0xFFE5E5E5)
            )
            Spacer(Modifier.height(8.dp))
            TitleScoreRow(details)
            Spacer(Modifier.height(8.dp))
            if (media.genres.isNotEmpty()) {
                Text(
                    media.genres.joinToString(" · "),
                    fontFamily = HankenFont,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = Color(0xFFE5E5E5),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun runtimeMinutes(raw: String): String {
    val hours = Regex("(\\d+)\\s*h", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val minutes = Regex("(\\d+)\\s*m", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val total = hours * 60 + minutes
    return if (total > 0) "$total min" else raw
}

@Composable
private fun TitleAction(    label: String,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    accent: Boolean = false,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null
) {
    val press=app.stak.nativeapp.ui.components.rememberPressMotion(enabled)
    Row(
        modifier = Modifier.pressMotion(press)
            .widthIn(min = width)
            .then(if (enabled) Modifier else Modifier.alpha(0.4f))
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (accent) StakAccent.copy(alpha = .80f) else Color.White.copy(alpha = .40f), RoundedCornerShape(12.dp))
            .clickable(interactionSource=press.source,indication=androidx.compose.foundation.LocalIndication.current,enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label.uppercase(),
            fontFamily = SpaceMonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = if (accent) StakAccent else Color(0xFFE5E5E5),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EpisodeRail(
    episodes: List<Episode>,
    selected: Episode?,
    onSelect: (Episode) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val cardWidth = (maxWidth - 40.dp) / 2
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(episodes,key={"${it.season}:${it.episode}"},contentType={"episode"}) { episode ->
            val active = selected?.season == episode.season && selected.episode == episode.episode
            Column(
                modifier = Modifier
                    .width(cardWidth)
                    .clip(RoundedCornerShape(16.dp))
                    .background(app.stak.nativeapp.ui.components.LocalTitleSurface.current)
                    .border(1.dp, StakBorderSoft, RoundedCornerShape(16.dp))
                    .clickable { onSelect(episode) }

            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(StakSurface)
                ) {
                    AsyncImage(
                        model = episode.image,
                        contentDescription = episode.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Text("E${episode.episode.toString().padStart(2,'0')}",fontFamily=SpaceMonoFont,fontSize=9.sp,color=Color.White,modifier=Modifier.align(Alignment.TopStart).padding(7.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha=.62f)).padding(horizontal=7.dp,vertical=4.dp))
                    if (episode.watched) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(0xE0181818)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Watched", tint = Color.White, modifier = Modifier.size(11.dp))
                        }
                    }
                    if (episode.progress > 0f && !episode.watched) {
                        StakProgress(episode.progress, modifier = Modifier.align(Alignment.BottomCenter), height = 3)
                    }
                }
                Column(Modifier.padding(horizontal=10.dp,vertical=10.dp)) {
                Text(
                    episode.name,
                    fontFamily = HankenFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = StakText,
                    minLines = 2, maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    listOfNotNull(episode.runtime,episode.released?.take(10)?.let{raw->runCatching{val date=java.time.LocalDate.parse(raw);date.format(java.time.format.DateTimeFormatter.ofPattern(if(date.year==java.time.LocalDate.now().year)"MMM d"else "MMM d, yyyy",java.util.Locale.ENGLISH))}.getOrDefault(raw)}).joinToString(" · "),
                    fontFamily = SpaceMonoFont,
                    fontSize = 9.sp,
                    color = StakTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                }
            }
        }
    }
}
}

@Composable
private fun SourceRow(source: SourceRelease, onGet: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(app.stak.nativeapp.ui.components.LocalTitleSurface.current)
            .border(1.dp, StakBorderSoft, RoundedCornerShape(14.dp))
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                source.name,
                fontFamily = HankenFont,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = StakTextSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SourceTag(source.resolution)
                if (source.hdr) SourceTag("HDR")
                if (source.atmos) SourceTag("ATMOS")
                if (source.remux) SourceTag("REMUX")
                if (source.webdl) SourceTag("WEB-DL")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(source.size, "${source.seeders} seeders", source.indexer).joinToString("  ·  "),
                fontFamily = SpaceMonoFont,
                fontSize = 8.sp,
                color = StakTextFaint
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (source.cached) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x2210B981))
                        .border(1.dp, Color(0x6610B981), CircleShape)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text("CACHED", fontFamily = SpaceMonoFont, fontWeight = FontWeight.Bold, fontSize = 8.sp, color = Color(0xFF4ADE80))
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(StakAccent)
                    .clickable { onGet() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("GET", fontFamily = SpaceMonoFont, fontWeight = FontWeight.Bold, fontSize = 8.sp, color = app.stak.nativeapp.ui.theme.StakOnAccent)
            }
        }
    }
}

@Composable
private fun SourceTag(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(StakAccentSoft)
            .border(1.dp, Color(0x44D71921), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(label, fontFamily = SpaceMonoFont, fontWeight = FontWeight.Bold, fontSize = 7.sp, color = StakAccent)
    }
}


