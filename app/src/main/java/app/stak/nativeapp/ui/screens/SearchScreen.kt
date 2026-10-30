@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

@Composable
fun SearchScreen(viewModel: DiscoverViewModel, onOpenTitle: (MediaItem, String) -> Unit, watchIds: Set<String>, sharedScope: SharedTransitionScope?, animatedScope: AnimatedVisibilityScope?) {
    val query by viewModel.query.collectAsState(); val items by viewModel.items.collectAsState()
    val loading by viewModel.loading.collectAsState(); val error by viewModel.error.collectAsState()
    val page by viewModel.page.collectAsState(); val pages by viewModel.totalPages.collectAsState()
    val filters by viewModel.filters.collectAsState(); val genres by viewModel.genres.collectAsState()
    val notice by viewModel.notice.collectAsState(); val ratingErrors by viewModel.ratingErrors.collectAsState()
    val partial by viewModel.partialErrors.collectAsState(); val loadingMore by viewModel.loadingMore.collectAsState()
    val browsing by viewModel.browsing.collectAsState()
    var showFilters by remember { mutableStateOf(false) }; var suggesting by remember { mutableStateOf(false) }
    val chips = filters.chips(genres)
    LazyVerticalGrid(columns = GridCells.Fixed(4), contentPadding = PaddingValues(16.dp,20.dp,16.dp,120.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "search-header", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StakTextField(query, { suggesting = true; viewModel.setQuery(it) }, "Movie, series or IMDb ID", modifier = Modifier.weight(1f), leading = { Icon(Lucide.Search,null,tint = StakTextFaint,modifier = Modifier.size(16.dp)) }, trailing = if(query.isNotBlank()) {{ Box(Modifier.size(28.dp).clickable { suggesting=false;viewModel.clearSearch() },contentAlignment=Alignment.Center) { Icon(Lucide.X,"Clear search",tint=StakTextMuted,modifier=Modifier.size(14.dp)) } }} else null)
                    StakIconButton({ showFilters = true }) { Icon(Lucide.Sliders,"Filters",tint=StakText,modifier=Modifier.size(17.dp)); if(chips.isNotEmpty()) Box(Modifier.align(Alignment.TopEnd).size(6.dp).background(StakAccent,CircleShape)) }
                }
                Spacer(Modifier.height(16.dp))
                if(suggesting && query.isNotBlank() && items.isNotEmpty()) {
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        items(items.take(3),key={"${it.type}:${it.mediaKey}"}) { item ->
                            Text(item.name,fontFamily=HankenFont,fontSize=13.sp,color=StakText,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=304.dp).heightIn(min=40.dp).border(1.dp,StakBorder,CircleShape).background(Color(0xFF111111),CircleShape).clickable{suggesting=false;onOpenTitle(item,"suggest-${item.mediaKey}")}.padding(horizontal=12.dp,vertical=10.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                if(notice!=null) { Text(notice.orEmpty(),fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted);Spacer(Modifier.height(12.dp)) }
                if(chips.isNotEmpty()) {
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { items(chips,key={it.first}) { (id,label)->StakButton("$label ×",{viewModel.clearFilter(id)},compact=true) } }
                    Spacer(Modifier.height(12.dp))
                }
                if(browsing) Text(if(query.isBlank()) "BROWSE · ${items.size}" else "RESULTS: “$query” · ${items.size}",fontFamily=SpaceMonoFont,fontWeight=FontWeight.Bold,fontSize=10.sp,letterSpacing=1.1.sp,color=StakTextMuted)
            }
        }
        if(error!=null) item(span={GridItemSpan(maxLineSpan)}) { InlineRetry(error.orEmpty()) { if(items.isEmpty())viewModel.load() else viewModel.loadMore() } }
        if(partial.isNotEmpty()) item(span={GridItemSpan(maxLineSpan)}) { InlineRetry(partial.joinToString(" · "),"Retry missing results") { viewModel.load() } }
        if(ratingErrors>0) item(span={GridItemSpan(maxLineSpan)}) { InlineRetry("Some ratings could not be checked.","Retry ratings") { viewModel.load() } }
        if(loading && items.isEmpty()) items(8) { ShimmerBox(Modifier.fillMaxWidth().aspectRatio(2f/3f)) }
        else {
            items(items,key={"${it.type}:${it.mediaKey}"},contentType={"poster"}) { item ->
                val key="search-${item.type}-${item.mediaKey}"
                StakPosterCard(item,{suggesting=false;onOpenTitle(item,key)},stakItemMotion().stakSharedElement(sharedScope,animatedScope,key),inWatchlist=item.mediaKey in watchIds || item.id in watchIds)
            }
            if(browsing && !loading && error==null && items.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) {
                StakEmpty("No results","Try another term or loosen the filters.",actionLabel="Clear filters",onAction={viewModel.clearSearch()})
            }
        }
        if(page<pages) item(span={GridItemSpan(maxLineSpan)}) { StakButton(if(loadingMore)"Loading…" else "Load more",{viewModel.loadMore()},Modifier.fillMaxWidth(),enabled=!loadingMore && !loading) }
    }
    if(showFilters) SearchFilterSheet(filters,genres,{showFilters=false}) { if(viewModel.applyFilters(it))showFilters=false }
}
