@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

@Composable
fun DiscoverScreen(onOpenTitle: (MediaItem, String) -> Unit, sharedScope: SharedTransitionScope?, animatedScope: AnimatedVisibilityScope?, initialQuery: String, watchIds: Set<String> = emptySet(), viewModel: DiscoverViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val items by viewModel.items.collectAsState(); val loading by viewModel.loading.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState(); val error by viewModel.error.collectAsState()
    val type by viewModel.type.collectAsState(); val category by viewModel.category.collectAsState(); val query by viewModel.query.collectAsState()
    val partial by viewModel.partialErrors.collectAsState()
    val page by viewModel.page.collectAsState(); val pages by viewModel.totalPages.collectAsState()
    val filters by viewModel.filters.collectAsState(); val genres by viewModel.genres.collectAsState(); val providers by viewModel.providers.collectAsState()
    val notice by viewModel.notice.collectAsState(); val ratingErrors by viewModel.ratingErrors.collectAsState(); val verified by viewModel.verifiedImdb.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    val chips = filters.chips(genres, providers + streamingShortcuts(providers))
    val titleSize = (LocalConfiguration.current.screenWidthDp * .08f).coerceIn(32f,44f)
    LaunchedEffect(initialQuery) { if (initialQuery.isNotBlank() && query != initialQuery) viewModel.setQuery(initialQuery) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal=16.dp,vertical=8.dp)){DiscoverHeading(chips.size){sheet=true}}
    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp,8.dp,16.dp,120.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            DiscoverBrowseHeader(query,type,category,filters,items.size,chips,
                {viewModel.setQuery(it)},{viewModel.setType(it)},{viewModel.setCategory(it)},
                {viewModel.clearFilter(it)},{viewModel.resetFilters()},
                {viewModel.applyFilters(filters.copy(sort=it))}){DiscoveryServices(viewModel)}
        }

        if (error != null) item(span = { GridItemSpan(maxLineSpan) }) { InlineRetry(error.orEmpty()) { if (items.isEmpty()) viewModel.load() else viewModel.loadMore() } }
        if (partial.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { InlineRetry(partial.joinToString(" · "), "Retry missing results") { viewModel.load() } }
        if (verified) item(span = { GridItemSpan(maxLineSpan) }) { Text("IMDb ratings verified for loaded results. Unknown ratings are excluded.", fontFamily = HankenFont, fontSize = 10.sp, color = StakTextMuted) }
        if (ratingErrors > 0) item(span = { GridItemSpan(maxLineSpan) }) { InlineRetry("Some ratings could not be checked.", "Retry ratings") { viewModel.load() } }
        if (loading && items.isEmpty()) items(9) { Column { ShimmerBox(Modifier.fillMaxWidth().aspectRatio(2f/3f)); Spacer(Modifier.height(8.dp)); ShimmerBox(Modifier.fillMaxWidth(.8f).height(12.dp)) } }
        else {
            items(items, key = { "${it.type}:${it.mediaKey}" },contentType={"poster"}) { item ->
                val key = "discover-${item.type}-${item.mediaKey}"
                StakPosterCard(item, { onOpenTitle(item,key) }, stakItemMotion().stakSharedElement(sharedScope,animatedScope,key), compactTitle = true, titleSize = 13, yearSize = 10, inWatchlist = item.mediaKey in watchIds || item.id in watchIds)
            }
            if (items.isEmpty() && error == null) item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().border(1.dp, Color(0x29FFFFFF), RoundedCornerShape(18.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No matches yet", fontFamily = DotoFont, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = StakText)
                    Text("No matching titles in the loaded results. Try fewer filters or load the next page.", fontFamily = HankenFont, fontSize = 14.sp, color = StakTextMuted, modifier = Modifier.padding(vertical = 12.dp))
                    StakButton("Clear filters", { viewModel.resetFilters() })
                }
            }
        }
        if (page < pages) item(span = { GridItemSpan(maxLineSpan) }) { StakButton(if (loadingMore) "Loading…" else "Load more", { viewModel.loadMore() }, Modifier.fillMaxWidth(), enabled = !loadingMore && !loading) }
        if (notice != null) item(span = { GridItemSpan(maxLineSpan) }) { Text(notice.orEmpty(), fontFamily = HankenFont, fontSize = 10.sp, lineHeight = 15.sp, color = StakTextMuted) }
        if (filters.hideLibrary) item(span = { GridItemSpan(maxLineSpan) }) { Text("Availability checked against known library titles. Unknown titles remain visible.", fontFamily = HankenFont, fontSize = 10.sp, color = StakTextMuted) }
        item(span = { GridItemSpan(maxLineSpan) }) { Column { Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x29FFFFFF))); Spacer(Modifier.height(12.dp)); DirectorsPanel(onOpenTitle) } }
    }
    }
    if (sheet) DiscoveryFilterSheet(type, filters, genres, { sheet = false }) { if (viewModel.applyFilters(it)) sheet = false }
}
