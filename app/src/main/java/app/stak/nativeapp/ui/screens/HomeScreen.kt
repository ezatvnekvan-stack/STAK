@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import app.stak.nativeapp.ui.components.recordGlassSource
import app.stak.nativeapp.ui.components.stakItemMotion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
    val search by searchViewModel.query.collectAsState()
    val searchItems by searchViewModel.items.collectAsState()
    val searchLoading by searchViewModel.loading.collectAsState()
    val searchError by searchViewModel.error.collectAsState()
    LaunchedEffect(feed,watchlist,knownTitles){searchViewModel.knownTitles(knownTitles+watchlist+feed?.hero.orEmpty()+feed?.rows.orEmpty().flatMap{it.items})}
    androidx.activity.compose.BackHandler(enabled=search.isNotBlank()){searchViewModel.setQuery("")}

    val comingFeed by viewModel.comingUp.collectAsState()
    val comingLoading by viewModel.comingLoading.collectAsState()
    val comingError by viewModel.comingError.collectAsState()
    var today by remember{mutableStateOf(java.time.LocalDate.now())}
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle){lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED){while(true){today=java.time.LocalDate.now();delay(60_000)}}}
    val comingEnabled=sections.any{it.id=="coming_up"&&it.enabled}
    val tmdbKey=settings.tmdbKey.orEmpty()
    LaunchedEffect(watchlist,tmdbKey,today,comingEnabled){viewModel.refreshUpcoming(if(comingEnabled)watchlist else emptyList(),today)}
    val comingEvents=remember(comingFeed,today){comingFeed?.let{app.stak.nativeapp.data.upcomingHomeEvents(it,today)}.orEmpty()}
    val hasTrakt=!settings.traktClientId.isNullOrBlank()
    val watchIds = remember(watchlist) { watchlist.map { it.mediaKey }.toSet() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("home-feed"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            HomeTopBar(onOpenAccount = onOpenAccount, torboxConnected = torboxConnected)
            Spacer(Modifier.height(12.dp))
            StakTextField(
                value = search,
                onValueChange = { searchViewModel.setQuery(it) },
                placeholder = "Movie, series or IMDb ID",
                trailing = {if(search.isNotEmpty())androidx.compose.material3.IconButton(onClick={searchViewModel.setQuery("")}){Icon(app.stak.nativeapp.ui.theme.Lucide.X,"Clear search",tint=StakTextMuted,modifier=Modifier.size(16.dp))}},
                height = 44,
                onSubmit = { if (search.isNotBlank()) onSearch(search.trim()) },
                leading = {
                    Icon(
                        app.stak.nativeapp.ui.theme.Lucide.Search,
                        contentDescription = null,
                        tint = StakTextFaint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
            Spacer(Modifier.height(16.dp))


        }

        if(search.isNotBlank()) {
            item {
                StakSectionHeader("Search results",actionLabel="See all",onAction={onSearch(search.trim())})
                Spacer(Modifier.height(12.dp))
                if(searchLoading)Text("Searching…",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
                searchError?.let{Text(it,fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted);StakButton("Retry search",{searchViewModel.retry()})}
                if(searchItems.isEmpty()&&!searchLoading&&searchError==null)Text(if(search.trim().length<2)"Keep typing to search movies and series." else "No matching titles. Try another title, year or IMDb ID.",fontFamily=HankenFont,fontSize=13.sp,color=StakTextMuted)
            }
            items(searchItems.chunked(3),key={row->row.joinToString("|"){"${it.type}:${it.mediaKey}"}},contentType={"search-row"}){row->
                Row(stakItemMotion().fillMaxWidth().padding(bottom=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    row.forEach{item->app.stak.nativeapp.ui.components.StakPosterCard(item,{onOpenTitle(item,"search-${item.mediaKey}")},Modifier.weight(1f),inWatchlist=item.mediaKey in watchIds)}
                    repeat(3-row.size){Spacer(Modifier.weight(1f))}
                }
            }
        } else if (feed == null || (loading && feed == null)) {
            item { RowSkeleton("Trending movies") }
        } else {
            val data = feed!!
            if (data.hero.isNotEmpty()) item {
                HeroCarousel(data.hero, { onOpenTitle(it, "hero-${it.mediaKey}") }, sharedScope, animatedScope, reducedMotion)
                Spacer(Modifier.height(32.dp))
            }
            if(!hasTrakt&&sections.any{it.enabled&&it.id !in listOf("continue","watchlist","coming_up")&&it.category in listOf("trending","anticipated")})item(key="trakt-setup"){
                Row(Modifier.fillMaxWidth().padding(bottom=24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("Trakt",fontFamily=SpaceMonoFont,fontSize=13.sp,color=StakText,modifier=Modifier.weight(1f))
                    StakButton("Set up Trakt",onOpenTrakt,compact=true)
                }
            }
            sections.filter { it.enabled }.forEach { section ->
                when (section.id) {
                    "continue" -> item {
                        if (continueWatching.isEmpty() && !jellyfinConnected) JellyfinConnectRow(onOpenAccount)
                        else if(continueWatching.isEmpty()&&(continueLoading||continueError==null)) {
                            StakSectionHeader("Continue watching")
                            Spacer(Modifier.height(12.dp))
                            Text(if(continueLoading)"Loading from Jellyfin…" else "Nothing to resume in Jellyfin.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
                            StakButton("Refresh",onRetryContinue)
                        }
                        else if(continueWatching.isNotEmpty()) {
                            StakSectionHeader("Continue watching", actionLabel = "Jellyfin →", onAction = onOpenLibrary)
                            Spacer(Modifier.height(12.dp))
                            ContinueRail(continueWatching) { onOpenTitle(it, "continue-${it.mediaKey}") }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                    "coming_up" -> if(watchlist.isNotEmpty()&&(comingEvents.isNotEmpty()||comingLoading||comingError!=null||comingFeed?.issues?.isNotEmpty()==true))item(key="coming-up"){
                        ComingUpShelf(comingEvents,today,comingLoading,comingError ?: comingFeed?.issues?.takeIf{it.isNotEmpty()}?.let{"Some release dates could not load."},{viewModel.refreshUpcoming(watchlist,today,true)}){onOpenTitle(it,"coming-${it.mediaKey}")}
                    }
                    "watchlist" -> if (watchlist.isNotEmpty()) item {
                        HomeShelf("Your watchlist", watchlist, "home-watchlist", onOpenTitle, onOpenWatchlist, sharedScope, animatedScope, showProgress = false, watchIds = watchIds)
                    }
                    else -> data.rows.find { it.id == section.id }?.let { row ->
                        if ((hasTrakt||row.provider!="Trakt")&&(row.items.isNotEmpty() || row.error != null)) item(key = row.id) {
                            HomeShelf(row.title, row.items, "home-${row.id}", onOpenTitle, { onBrowseSection(row.id) }, sharedScope, animatedScope, error = row.error, onRetry = { viewModel.refresh() }, watchIds = watchIds, numbered=row.id.startsWith("trending_"), provider=row.provider)
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
    provider:String?=null
) {
    Column {
        StakSectionHeader(title = title, actionLabel = "See all", onAction = onSeeAll)
        Spacer(Modifier.height(12.dp))
        if (error != null) {
            Text(error, fontFamily = HankenFont, fontSize = 12.sp, color = StakTextMuted)
            StakButton("Retry", onRetry)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(items, key = { _,item -> "${item.type}:${item.mediaKey}" },contentType={_,_->"poster"}) { index,item ->
                val elementKey = "$keyPrefix-${item.mediaKey}"
                StakPosterCard(
                    item = item,
                    onClick = { onOpenTitle(item, elementKey) },
                    showProgress = showProgress,
                    reserveTitleLines = false,
                    rank=if(numbered)index+1 else null,
                    inWatchlist = item.mediaKey in watchIds,
                    modifier = stakItemMotion()
                        .width(if(numbered)144.dp else 120.dp)
                        .stakSharedElement(sharedScope, animatedScope, elementKey)
                )
            }
        }
        if(provider=="Trakt"){
            val uri=androidx.compose.ui.platform.LocalUriHandler.current
            Text("Trakt ↗",fontFamily=SpaceMonoFont,fontSize=9.sp,color=StakTextMuted,modifier=Modifier.heightIn(min=32.dp).clickable{uri.openUri("https://trakt.tv")}.padding(top=8.dp))
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun HomeTopBar(onOpenAccount: () -> Unit, torboxConnected: Boolean) {
    Box(Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical=4.dp).height(48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_stak),
                contentDescription = "STAK",
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
            )
            Spacer(Modifier.width(14.dp))
            Text(
                "STAK",
                fontFamily = DotoFont,
                fontWeight = FontWeight.Black,
                fontSize = 25.sp,
                lineHeight = 25.sp,
                letterSpacing = (-1.5).sp,
                color = StakText
            )
        }
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(CircleShape)
                .border(1.dp, Color(0x1FFFFFFF), CircleShape)
                .clickable { onOpenAccount() }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (torboxConnected) StakSuccess else StakAccent))
            Spacer(Modifier.width(7.dp))
            Text(
                if (torboxConnected) "TorBox" else "No key",
                fontFamily = SpaceMonoFont,
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
                color = StakTextSoft
            )
        }
    }
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
private fun HeroCarousel(
    items: List<MediaItem>,
    onOpenTitle: (MediaItem) -> Unit,
    sharedScope: SharedTransitionScope?,
    animatedScope: AnimatedVisibilityScope?,
    reducedMotion: Boolean
) {
    val pager = rememberPagerState(pageCount = { items.size })
    val cinemaPalette=app.stak.nativeapp.ui.components.LocalCinematicPalette.current
    LaunchedEffect(pager.currentPage,items){cinemaPalette?.value=items.getOrNull(pager.currentPage)?.let{it.background ?: it.poster}}
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(items.size, reducedMotion) {
        if (items.size > 1 && !reducedMotion) while (true) {
            delay(9000)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % items.size,animationSpec=androidx.compose.animation.core.tween(520,easing=app.stak.nativeapp.ui.components.StakMotion.Ease))
        }
    }
    val width = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val height = (width * .65f).coerceIn(280f, 310f).dp
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(14.dp))) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val item = items[page]
            Box(Modifier.fillMaxSize().clickable { onOpenTitle(item) }.stakSharedElement(sharedScope, animatedScope, "hero-${item.mediaKey}")) {
                AsyncImage(item.background ?: item.poster, item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x18000000), .4f to Color(0x00000000), 1f to Color(0xE6000000))))
                Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 48.dp)) {
                    var logoFailed by remember(item.logo){mutableStateOf(false)}
                    if(item.logo!=null&&!logoFailed)AsyncImage(item.logo,item.name,contentScale=ContentScale.Fit,onError={logoFailed=true},alignment=Alignment.CenterStart,modifier=Modifier.width(210.dp).height(58.dp))
                    else Text(item.name,fontFamily=DotoFont,fontWeight=FontWeight.Black,fontSize=18.sp,lineHeight=24.sp,color=Color.White,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    if (item.genres.isNotEmpty()) {
                        Text(item.genres.take(2).joinToString(" · ").uppercase(), fontFamily = SpaceMonoFont, fontSize = 10.sp, letterSpacing = 1.2.sp, color = Color(0xCCFFFFFF))
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(listOfNotNull(item.year, item.runtime).joinToString(" · "), fontFamily = HankenFont, fontSize = 12.sp, color = Color.White)
                        item.certification?.let{Text(it,fontFamily=SpaceMonoFont,fontSize=10.sp,lineHeight=14.sp,color=Color.White,modifier=Modifier.border(1.dp,Color(0x60FFFFFF),RoundedCornerShape(4.dp)).padding(horizontal=5.dp,vertical=2.dp))}
                    }
                }
            }
        }
        if (items.size > 1) Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(44.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { index, item ->
                Box(Modifier.width(32.dp).height(44.dp).clickable { scope.launch { pager.animateScrollToPage(index,animationSpec=androidx.compose.animation.core.tween(if(reducedMotion)0 else 420,easing=app.stak.nativeapp.ui.components.StakMotion.Ease)) } }, contentAlignment = Alignment.Center) {
                    Box(Modifier.width(if (index == pager.currentPage) 20.dp else 4.dp).height(4.dp).background(if (index == pager.currentPage) Color.White else Color(0x80FFFFFF)))
                }
            }
            Spacer(Modifier.weight(1f))
            Text("%02d / %02d".format(pager.currentPage + 1, items.size), fontFamily = SpaceMonoFont, fontSize = 10.sp, color = Color(0xB3FFFFFF))
        }
    }
}

@Composable
private fun ContinueRail(items: List<MediaItem>, onOpenTitle: (MediaItem) -> Unit) {
    val width=((androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp-44)/2f).dp
    LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        items(items,key={it.jellyfinId ?: it.mediaKey}) { item ->
            var menu by remember(item.mediaKey){mutableStateOf(false)}
            Box(Modifier.width(width)) {
            Column(Modifier.width(width).clip(RoundedCornerShape(12.dp)).background(Color(0x80101010)).border(1.dp,Color(0x30FFFFFF),RoundedCornerShape(12.dp)).combinedClickable(onClick={onOpenTitle(item)},onLongClick={menu=true})) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f/9f)) {
                    val imageLayer=androidx.compose.ui.graphics.rememberGraphicsLayer()
                    val imageSource=remember(imageLayer){app.stak.nativeapp.ui.components.GlassSource(imageLayer)}
                    val imageView=androidx.compose.ui.platform.LocalView.current
                    Box(Modifier.fillMaxSize().recordGlassSource(imageSource,imageView)){AsyncImage(item.background ?: item.poster,item.name,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())}
                    app.stak.nativeapp.ui.components.MatteSurface(Modifier.align(Alignment.BottomStart).padding(8.dp),source=imageSource){
                    Row(Modifier.padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(if(item.nextUp) app.stak.nativeapp.ui.theme.Lucide.SkipNext else app.stak.nativeapp.ui.theme.Lucide.Play,null,tint=Color.White,modifier=Modifier.size(10.dp))
                        Spacer(Modifier.width(4.dp));Text(if(item.nextUp)"NEXT UP" else "RESUME",fontFamily=SpaceMonoFont,fontSize=8.sp,lineHeight=11.sp,color=Color.White)
                    }
                    }
                }
                Column(Modifier.padding(10.dp)) {
                    Text(item.name,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=12.sp,lineHeight=17.sp,color=StakText,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    Text(if(item.nextUp) "S${item.season ?: 1} E${item.episode ?: 1}" else if(item.durationMs>0) "${((item.durationMs-item.positionMs).coerceAtLeast(0)+59999)/60000} min left" else item.releaseInfo ?: item.year.orEmpty(),fontFamily=SpaceMonoFont,fontSize=10.sp,lineHeight=14.sp,color=StakTextMuted,maxLines=1)
                    if(!item.nextUp) {
                        Spacer(Modifier.height(10.dp));Row(verticalAlignment=Alignment.CenterVertically){StakProgress(item.progress,Modifier.weight(1f));Spacer(Modifier.width(8.dp));Text("${(item.progress*100).toInt()}%",fontFamily=SpaceMonoFont,fontSize=8.sp,lineHeight=11.sp,color=StakAccent)}
                    }
                }
            }
            app.stak.nativeapp.ui.components.PosterContextMenu(item,menu,{menu=false},{onOpenTitle(item)})
            }
        }
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("any" to "Any", "movie" to "Movies", "series" to "Series").forEach { (value, label) ->
                StakChip(label, selected = type == value, onClick = { type = value })
            }
            StakChip("≤ 90", selected = short, onClick = { short = !short })
        }
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

