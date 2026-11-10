@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

@Composable fun WatchlistScreen(onOpenTitle:(MediaItem,String)->Unit,onBrowseDiscover:()->Unit,viewModel:WatchlistViewModel,sharedScope:SharedTransitionScope?,animatedScope:AnimatedVisibilityScope?,watchedIds:Set<String> = emptySet()) {
    val all by viewModel.items.collectAsState();val prefs by viewModel.preferences.collectAsState();val busy by viewModel.busy.collectAsState();val error by viewModel.error.collectAsState()
    val cached by viewModel.cached.collectAsState();val checking by viewModel.checking.collectAsState();val failures by viewModel.cacheFailures.collectAsState();val ratingsLoading by viewModel.ratingsLoading.collectAsState()
    val torboxKey=NativeRuntime.settings.torboxKey.orEmpty();val effective=prefs.copy(cachedOnly=prefs.cachedOnly&&torboxKey.isNotBlank())
    var filters by remember{mutableStateOf(false)};var remove by remember{mutableStateOf<MediaItem?>(null)}
    val identities=all.map{"${it.type}:${it.mediaKey}"}
    LaunchedEffect(identities,torboxKey,effective.cachedOnly){viewModel.checkCache(all,torboxKey,effective.cachedOnly)}
    LaunchedEffect(identities,prefs.sort){if(prefs.sort=="rating")viewModel.loadRatings(all)}
    val shown=remember(all,effective,watchedIds,cached){filterSavedTitles(all,effective,watchedIds,cached)}
    val grid=rememberLazyGridState()
    LazyVerticalGrid(columns=GridCells.Fixed(if(prefs.layout=="list")1 else 3),state=grid,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,22.dp,16.dp,120.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item(span={GridItemSpan(maxLineSpan)}) {
            Column {
                StakPageHeader("Watchlist",trailing={IconButton(onClick={viewModel.refresh();viewModel.checkCache(all,torboxKey,effective.cachedOnly);if(prefs.sort=="rating")viewModel.loadRatings(all)}){Icon(Lucide.Refresh,"Refresh watchlist",tint=StakText,modifier=Modifier.size(17.dp))}})
                Spacer(Modifier.height(8.dp));WatchlistSummary(all.count{!it.isSeries},all.count{it.isSeries})
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    StakTextField(prefs.query,{viewModel.preferences(prefs.copy(query=it))},"Search watchlist",Modifier.weight(1f),leading={Icon(Lucide.Search,null,modifier=Modifier.size(16.dp),tint=StakTextMuted)})
                    IconButton(onClick={filters=true}){Icon(Lucide.Sliders,"Watchlist filters",tint=StakText)}
                }
                Spacer(Modifier.height(12.dp));MediaTypeSelector(listOf("all" to "All","movie" to "Movies","series" to "Series"),prefs.type,{viewModel.preferences(prefs.copy(type=it))},Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment=Alignment.CenterVertically){Text("${shown.size} titles · ${when(prefs.sort){"year"->"Newest year";"rating"->"IMDb rating";"name"->"Title A–Z";else->"Date added"}}",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted,modifier=Modifier.weight(1f));listOf("grid" to Lucide.Grid,"list" to Lucide.List).forEach{(layout,icon)->IconButton(onClick={viewModel.preferences(prefs.copy(layout=layout))}){Icon(icon,if(layout=="grid")"Poster grid" else "List view",tint=if(prefs.layout==layout)StakText else StakTextMuted,modifier=Modifier.size(18.dp))}}}
                if(prefs.hideWatched)Text("Unwatched only",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
                if(effective.cachedOnly)Text("Cache filter active",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
                if(checking)Text("Checking cached sources…",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
                if(ratingsLoading)Text("Loading IMDb ratings…",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
                if(failures>0){Text("Some cache checks failed. Unknown titles are excluded.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted);StakButton("Retry cache checks",{viewModel.checkCache(all,torboxKey,effective.cachedOnly)})}
                error?.let{InlineRetry(it){viewModel.refresh()}}
            }
        }
        if(shown.isEmpty()&&!checking)item(span={GridItemSpan(maxLineSpan)}) {
            Column {StakEmpty(if(all.isEmpty())"Your next watch starts here" else "No matching titles",if(all.isEmpty())"Save a movie or series from Discover to keep it here." else "Try another search or clear the filters.");StakButton(if(all.isEmpty())"Browse Discover" else "Clear filters",{if(all.isEmpty())onBrowseDiscover() else viewModel.preferences(WatchlistPreferences())})}
        }
        items(shown,key={"${it.type}:${it.mediaKey}"},contentType={prefs.layout}){item->
            val key="watchlist-${item.type}-${item.mediaKey}";val display=item.copy(watched=item.mediaKey in watchedIds)
            Box(stakItemMotion()) {
                if(prefs.layout=="list")LibraryMediaRow(display,{onOpenTitle(item,key)})
                else StakPosterCard(display,{onOpenTitle(item,key)},compactTitle=true,artworkModifier=Modifier.stakSharedElement(sharedScope,animatedScope,key))
            }
        }
    }
    if(filters)GlassAlertDialog(onDismissRequest={filters=false},containerColor=StakSurface,title={Text("Watchlist filters",fontFamily=DotoFont)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        DiscoverySelect("Sort by",prefs.sort,listOf("added" to "Date added","year" to "Year","rating" to "IMDb rating","name" to "Name"),{viewModel.preferences(prefs.copy(sort=it))})
        Row(Modifier.fillMaxWidth().clickable{viewModel.preferences(prefs.copy(hideWatched=!prefs.hideWatched))},verticalAlignment=Alignment.CenterVertically){Text("Hide watched",fontFamily=HankenFont,fontSize=14.sp,modifier=Modifier.weight(1f));Switch(prefs.hideWatched,{viewModel.preferences(prefs.copy(hideWatched=it))})}
        Row(verticalAlignment=Alignment.CenterVertically){Text("Has a cached source",fontFamily=HankenFont,fontSize=14.sp,modifier=Modifier.weight(1f));Switch(effective.cachedOnly,{viewModel.preferences(prefs.copy(cachedOnly=it))},enabled=torboxKey.isNotBlank())}
        Text(if(torboxKey.isBlank())"Connect TorBox to check cached sources." else "Series checks sample season 1, episode 1. They do not confirm that the whole series is cached.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
        if(prefs.sort=="rating"&&NativeRuntime.settings.omdbKey.isNullOrBlank())Text("Configure OMDb for verified IMDb ratings.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
    }},confirmButton={StakButton("Done",{filters=false})},dismissButton={StakButton("Reset",{viewModel.preferences(WatchlistPreferences())})})
    remove?.let{item->GlassAlertDialog(onDismissRequest={remove=null},containerColor=StakSurface,title={Text("Remove from watchlist?")},text={Text(item.name)},confirmButton={StakButton("Remove",{viewModel.remove(item);remove=null})},dismissButton={StakButton("Cancel",{remove=null})})}
}
