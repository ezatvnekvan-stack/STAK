@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.stak.nativeapp.ui.screens

import app.stak.nativeapp.ui.components.edgeToEdge
import app.stak.nativeapp.ui.components.MediaTypeSelector
import app.stak.nativeapp.data.WatchRules
import app.stak.nativeapp.data.followingMedia
import app.stak.nativeapp.data.followingUpcomingEvents

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import app.stak.nativeapp.ui.components.recordGlassSource
import app.stak.nativeapp.ui.components.stakItemMotion
import app.stak.nativeapp.ui.components.pressMotion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.stak.nativeapp.R
import app.stak.nativeapp.data.HomeFeed
import app.stak.nativeapp.data.MediaItem
import app.stak.nativeapp.ui.components.GlassCard
import app.stak.nativeapp.ui.components.RatingBadge
import app.stak.nativeapp.ui.components.ShimmerBox
import app.stak.nativeapp.ui.components.StakButton
import app.stak.nativeapp.ui.components.StakButtonVariant
import app.stak.nativeapp.ui.components.StakChip
import app.stak.nativeapp.ui.components.StakPosterCard
import app.stak.nativeapp.ui.components.StakProgress
import app.stak.nativeapp.ui.components.StakSectionHeader
import app.stak.nativeapp.ui.components.StakTextField
import app.stak.nativeapp.ui.components.stakSharedElement
import app.stak.nativeapp.ui.theme.DotoFont
import app.stak.nativeapp.ui.theme.HankenFont
import app.stak.nativeapp.ui.theme.SpaceMonoFont
import app.stak.nativeapp.ui.theme.StakAccent
import app.stak.nativeapp.ui.theme.StakBorder
import app.stak.nativeapp.ui.theme.StakSurface2
import app.stak.nativeapp.ui.theme.StakSuccess
import app.stak.nativeapp.ui.theme.StakText
import app.stak.nativeapp.ui.theme.StakTextFaint
import app.stak.nativeapp.ui.theme.StakTextMuted
import app.stak.nativeapp.ui.theme.StakTextSoft
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun HomeScreen(
    onOpenTitle: (MediaItem, String) -> Unit,
    onSearch: (String) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenWatchlist: () -> Unit,
    onBrowseSection: (String) -> Unit,
    continueWatching: List<MediaItem> = emptyList(),
    torboxConnected: Boolean = false,
    jellyfinConnected: Boolean = false,
    continueLoading: Boolean = false,
    continueError: String? = null,
    onRetryContinue: () -> Unit = {},
    watchlist: List<MediaItem> = emptyList(),
    sharedScope: SharedTransitionScope?,
    animatedScope: AnimatedVisibilityScope?,
    reducedMotion: Boolean,
    searchViewModel:LiveSearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    knownTitles:List<MediaItem> = emptyList(),
    viewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onOpenTrakt:()->Unit=onOpenAccount
) {
    val feed by viewModel.feed.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val settings = app.stak.nativeapp.data.NativeRuntime.settings
    val sections = app.stak.nativeapp.data.HomeSections.read(settings.homeRowsJson)
    LaunchedEffect(feed,watchlist,knownTitles){searchViewModel.knownTitles(knownTitles+watchlist+feed?.hero.orEmpty()+feed?.rows.orEmpty().flatMap{it.items})}

    val followingRevision by WatchRules.revision.collectAsState()
    val followingRules=remember(followingRevision){WatchRules.store.read().rules}
    val followingTitles=remember(followingRules){followingMedia(followingRules)}
    val comingFeed by viewModel.comingUp.collectAsState()
    val comingLoading by viewModel.comingLoading.collectAsState()
    val comingError by viewModel.comingError.collectAsState()
    var today by remember{mutableStateOf(java.time.LocalDate.now())}
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle){lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED){while(true){today=java.time.LocalDate.now();delay(60_000)}}}
    val comingEnabled=sections.any{it.id=="coming_up"&&it.enabled}
    val tmdbKey=settings.tmdbKey.orEmpty()
    LaunchedEffect(followingTitles,tmdbKey,today,comingEnabled){viewModel.refreshUpcoming(if(comingEnabled)followingTitles else emptyList(),today)}
    val comingEvents=remember(comingFeed,followingRules,today){comingFeed?.let{followingUpcomingEvents(it,followingRules,today)}.orEmpty()}
    val hasTrakt=!settings.traktClientId.isNullOrBlank()
    val watchIds = remember(watchlist) { watchlist.map { it.mediaKey }.toSet() }

    val homeScroll=androidx.compose.foundation.lazy.rememberLazyListState()
    val heroVisible by remember{androidx.compose.runtime.derivedStateOf{homeScroll.layoutInfo.visibleItemsInfo.any{it.key=="home-hero"}}}
    val pairStart=remember(sections){anticipatedPairStart(sections)}
    var anticipatedType by androidx.compose.runtime.saveable.rememberSaveable(pairStart){mutableStateOf(if(pairStart=="anticipated_series")"series"else "movie")}
    LazyColumn(
        state=homeScroll,
        modifier = Modifier.fillMaxSize().testTag("home-feed"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        if(feed?.hero.isNullOrEmpty())item(key="home-safe-inset"){Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))}
        if (feed == null) {
            item { RowSkeleton("Trending movies") }
        } else {
            val data = feed!!
            if (data.hero.isNotEmpty()) item(key="home-hero") {
                HeroCarousel(data.hero, { onOpenTitle(it, "hero-${it.mediaKey}") }, sharedScope, animatedScope, reducedMotion,autoPlay=heroVisible&&!homeScroll.isScrollInProgress,modifier=Modifier.edgeToEdge())
                Spacer(Modifier.height(22.dp))
            }
            if(data.hero.isEmpty()&&loading&&!settings.tmdbKey.isNullOrBlank())item(key="home-hero"){
                ShimmerBox(Modifier.fillMaxWidth().height(app.stak.nativeapp.ui.components.homeHeroHeight(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp).dp));Spacer(Modifier.height(22.dp))
            }
            if(!hasTrakt&&sections.any{it.enabled&&it.id !in listOf("continue","watchlist","coming_up")&&it.category in listOf("trending","anticipated")})item(key="trakt-setup"){
                Row(Modifier.fillMaxWidth().padding(bottom=24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("More recommendations",fontFamily=SpaceMonoFont,fontSize=13.sp,color=StakText,modifier=Modifier.weight(1f))
                    StakButton("Set up",onOpenTrakt,compact=true)
                }
            }
            sections.filter { it.enabled }.forEach { section ->
                if(pairStart!=null && section.id in setOf("anticipated_movie","anticipated_series")){
                    if(section.id==pairStart && hasTrakt)item(key="anticipated-pair"){
                        val selectedId="anticipated_$anticipatedType"
                        val row=data.rows.find{it.id==selectedId}
                        Column{
                            StakSectionHeader("Most anticipated",actionLabel="See all",onAction={onBrowseSection(selectedId)})
                            app.stak.nativeapp.ui.components.MediaTypeSelector(listOf("movie" to "Movies","series" to "Series"),anticipatedType,{anticipatedType=it})
                            Spacer(Modifier.height(12.dp))
                            if(loading&&row?.items.isNullOrEmpty()&&row?.error==null)RowSkeleton("")
                            else HomeShelf("",row?.items.orEmpty(),"home-$selectedId",onOpenTitle,{onBrowseSection(selectedId)},sharedScope,animatedScope,error=row?.error,onRetry={viewModel.refresh()},watchIds=watchIds,showHeading=false)
                        }
                    }
                }else when (section.id) {
                    "continue" -> item {
                        if (continueWatching.isEmpty() && !jellyfinConnected) JellyfinConnectRow(onOpenAccount)
                        else if(continueWatching.isEmpty()&&(continueLoading||continueError==null)) {
                            StakSectionHeader("Continue watching")
                            Spacer(Modifier.height(12.dp))
                            Text(if(continueLoading)"Loading from Jellyfin…" else "Nothing to resume in Jellyfin.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
                            StakButton("Refresh",onRetryContinue)
                        }
                        else if(continueWatching.isNotEmpty()) {
                            StakSectionHeader("Continue watching", actionLabel = "See all", onAction = onOpenLibrary)
                            Spacer(Modifier.height(12.dp))
                            ContinueRail(continueWatching) { onOpenTitle(it, "continue-${it.mediaKey}") }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                    "coming_up" -> if(comingEvents.isNotEmpty())item(key="coming-up"){
                        ComingUpShelf(comingEvents,today,comingLoading,comingError ?: comingFeed?.issues?.takeIf{it.isNotEmpty()}?.let{"Some release dates could not load."},{viewModel.refreshUpcoming(followingTitles,today,true)}){onOpenTitle(it,"coming-${it.mediaKey}")}
                    }
                    "watchlist" -> if (watchlist.isNotEmpty()) item {
                        HomeShelf("Your watchlist", watchlist, "home-watchlist", onOpenTitle, onOpenWatchlist, sharedScope, animatedScope, showProgress = false, watchIds = watchIds)
                    }
                    else -> data.rows.find { it.id == section.id }?.let { row ->
                        if(loading && row.items.isEmpty() && row.error==null)item(key=row.id){RowSkeleton(row.title)}
                        else if ((hasTrakt||row.provider!="Trakt")&&(row.items.isNotEmpty() || row.error != null)) item(key = row.id) {
                            HomeShelf(row.title, row.items, "home-${row.id}", onOpenTitle, { onBrowseSection(row.id) }, sharedScope, animatedScope, error = row.error, onRetry = { viewModel.refresh() }, watchIds = watchIds, numbered=row.id.startsWith("trending_"),featuredLead=row.id=="trending_movie")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeShelf(
    title: String,
    items: List<MediaItem>,
    keyPrefix: String,
    onOpenTitle: (MediaItem, String) -> Unit,
    onSeeAll: () -> Unit,
    sharedScope: SharedTransitionScope?,
    animatedScope: AnimatedVisibilityScope?,
    showProgress: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {},
    watchIds: Set<String> = emptySet(),
    numbered:Boolean=false,
    featuredLead:Boolean=false,
    showHeading:Boolean=true
) {
    Column {
        if(showHeading){StakSectionHeader(title = title, actionLabel = "See all", onAction = onSeeAll);Spacer(Modifier.height(14.dp))}
        if (error != null) {
            Text(error, fontFamily = HankenFont, fontSize = 12.sp, color = StakTextMuted)
            StakButton("Retry", onRetry)
        }
        if(featuredLead&&items.isNotEmpty()){
            val lead=items.first();val key="$keyPrefix-${lead.mediaKey}"
            HomeSpotlight(lead,{onOpenTitle(lead,key)},sharedScope,animatedScope,key)
            if(items.size>1)Spacer(Modifier.height(16.dp))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(if(featuredLead)items.drop(1)else items, key = { _,item -> "${item.type}:${item.mediaKey}" },contentType={_,_->"poster"}) { index,item ->
                val elementKey = "$keyPrefix-${item.mediaKey}"
                StakPosterCard(
                    item = item,
                    onClick = { onOpenTitle(item, elementKey) },
                    showProgress = showProgress,
                    reserveTitleLines = true,
                    rank=if(numbered)index+(if(featuredLead)2 else 1) else null,
                    inWatchlist = item.mediaKey in watchIds,
                    modifier = stakItemMotion()
                        .width(if(numbered)144.dp else 120.dp),
                    artworkModifier=Modifier.stakSharedElement(sharedScope, animatedScope, elementKey)
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun JellyfinConnectRow(onOpenLibrary: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "Connect Jellyfin to resume watching.",
            fontFamily = HankenFont,
            fontSize = 12.sp,
            lineHeight = 16.8.sp,
            color = StakTextMuted,
            modifier = Modifier
        )
        StakButton(
            label = "Connect Jellyfin",
            onClick = onOpenLibrary,
            variant = StakButtonVariant.Outlined,
            compact = false
        )
    }
}

@Composable
private fun HeroSkeleton() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(394.dp)
            .clip(RoundedCornerShape(24.dp))
    ) {
        ShimmerBox(Modifier.fillMaxSize(), corner = 24)
    }
}

@Composable
private fun RowSkeleton(title: String) {
    Column {
        StakSectionHeader(title)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(4) {
                Column(modifier = Modifier.width(126.dp)) {
                    ShimmerBox(Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                    Spacer(Modifier.height(8.dp))
                    ShimmerBox(Modifier.fillMaxWidth(0.8f).height(10.dp), corner = 4)
                }
            }
        }
    }
}

@Composable
private fun JellyfinSummary(onOpenLibrary: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StakSurface2)
            .border(1.dp, StakBorder, RoundedCornerShape(14.dp))
            .clickable { onOpenLibrary() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(StakAccent))
        Spacer(Modifier.width(8.dp))
        Text(
            "JELLYFIN LIBRARY",
            fontFamily = SpaceMonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 0.8.sp,
            color = StakTextMuted
        )
        Spacer(Modifier.weight(1f))
        Text(
            "MANAGE →",
            fontFamily = SpaceMonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 8.sp,
            letterSpacing = 0.6.sp,
            color = StakTextFaint
        )
    }
}

private fun runtimeMinutes(runtime: String?): Int {
    if (runtime.isNullOrBlank()) return 0
    val hours = Regex("(\\d+)\\s*h").find(runtime)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    val minutes = Regex("(\\d+)\\s*m").find(runtime)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    return hours * 60 + minutes
}

@Composable
private fun TonightCard(pool: List<MediaItem>, onOpenTitle: (MediaItem) -> Unit) {
    var type by remember { mutableStateOf("any") }
    var short by remember { mutableStateOf(false) }
    var seed by remember { mutableIntStateOf(0) }
    val candidates = remember(pool, type, short) {
        pool.filter { item ->
            val matchesType = type == "any" || item.type == type
            val fits = !short || runtimeMinutes(item.runtime).let { it in 1..90 }
            matchesType && fits
        }
    }
    val pick = if (candidates.isEmpty()) null else candidates[seed.mod(candidates.size)]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF151313), Color(0xFF101010))
                )
            )
            .border(1.dp, StakBorder, RoundedCornerShape(22.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tonight", fontFamily = DotoFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = StakText)
        }
        Spacer(Modifier.height(8.dp))
        MediaTypeSelector(listOf("any" to "Any", "movie" to "Movies", "series" to "Series"), type, { type = it })
        StakChip("≤ 90", selected = short, onClick = { short = !short })
        Spacer(Modifier.height(12.dp))
        if (pick == null) {
            Text(
                "No matching suggestion. Try another filter or shuffle.",
                fontFamily = HankenFont,
                fontSize = 12.sp,
                color = StakTextMuted
            )
        } else {
            Row {
                AsyncImage(
                    model = pick.poster,
                    contentDescription = pick.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(82.dp)
                        .height(123.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .clickable { onOpenTitle(pick) }
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        pick.name,
                        fontFamily = DotoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        lineHeight = 24.sp,
                        color = StakText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(pick.year, pick.runtime, pick.genres.firstOrNull()).joinToString("  ·  "),
                        fontFamily = SpaceMonoFont,
                        fontSize = 9.sp,
                        color = StakTextMuted,
                        maxLines = 1
                    )
                    if (!pick.overview.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            pick.overview,
                            fontFamily = HankenFont,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = StakTextSoft,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StakButton(
                            label = "Open",
                            onClick = { onOpenTitle(pick) },
                            variant = app.stak.nativeapp.ui.components.StakButtonVariant.Primary,
                            compact = true
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x14FFFFFF))
                                .border(1.dp, StakBorder, CircleShape)
                                .clickable { seed++ },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = StakTextMuted, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

