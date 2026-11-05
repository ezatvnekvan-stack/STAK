@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class,androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import coil.compose.AsyncImage

@Composable fun LibraryScreen(
    onOpenTitle:(MediaItem,String)->Unit,onOpenTorBox:()->Unit,connected:Boolean,onOpenAccount:()->Unit,
    viewModel:JellyfinViewModel,sharedScope:SharedTransitionScope?,animatedScope:AnimatedVisibilityScope?,browser:JellyfinBrowserViewModel=androidx.lifecycle.viewmodel.compose.viewModel(),onMediaCenter:()->Unit={},scanModel:LibraryScanViewModel=androidx.lifecycle.viewmodel.compose.viewModel(),onOpenAutoAdd:()->Unit={}
) {
    val items by viewModel.items.collectAsState();val loading by viewModel.loading.collectAsState();val error by viewModel.error.collectAsState();val total by viewModel.total.collectAsState()
    val filters by viewModel.filters.collectAsState();var options by remember{mutableStateOf(false)}
    LaunchedEffect(connected){viewModel.refresh()}
    val parent by browser.selected.collectAsState();val parentItems by browser.items.collectAsState();val parentBusy by browser.busy.collectAsState();val parentError by browser.error.collectAsState()
    var catalogOpen by remember{mutableStateOf(false)};var versions by remember{mutableStateOf<MediaItem?>(null)}
    val activeScope=if(connected)jellyfinScope() else "disconnected"
    LaunchedEffect(activeScope){browser.account(activeScope);catalogOpen=false;versions=null}
    val raw=if(parent==null)items else parentItems
    val grouped=remember(raw){groupJellyfinVersions(raw)}
    val shown=remember(grouped,filters,connected){if(connected)filterJellyfinLibrary(grouped,filters) else emptyList()}
    val displayLoading=if(parent==null)loading else parentBusy;val displayError=if(parent==null)error else parentError
    val grid=rememberLazyGridState()
    LaunchedEffect(filters,parent?.id){grid.scrollToItem(0)}
    val refresh:()->Unit={viewModel.refresh();parent?.let{browser.select(it)}}
    LibraryRefresh(displayLoading,refresh,connected){
    LazyVerticalGrid(columns=GridCells.Fixed(if(filters.layout=="list")1 else 3),state=grid,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,22.dp,16.dp,120.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item(span={GridItemSpan(maxLineSpan)}) {
            Column {
                LibraryHeader(onMediaCenter){close->
                    DropdownMenuItem(text={Text("Refresh library")},onClick={close();refresh()})
                    DropdownMenuItem(text={Text("Connection settings")},onClick={close();onOpenAccount()})
                }
                Spacer(Modifier.height(10.dp));StakTabs(listOf("jellyfin" to "Jellyfin","torbox" to "TorBox","autoadd" to "Series"),"jellyfin",{if(it=="torbox")onOpenTorBox() else if(it=="autoadd")onOpenAutoAdd()},Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                if(connected) {
                    LibrarySearch(filters.query,{viewModel.setFilters(filters.copy(query=it))},{options=true})
                    Spacer(Modifier.height(10.dp))
                    LibraryScanControl(connected,refresh,scanModel,leading={LibraryChoice(parent?.name ?: "All libraries",connected,{catalogOpen=true},Modifier.weight(1f))})
                    Spacer(Modifier.height(8.dp));StakSegmented(listOf("all" to "All","movie" to "Movies","series" to "Series"),filters.type,{viewModel.setFilters(filters.copy(type=it))},Modifier.fillMaxWidth(),fillWidth=true)
                    Spacer(Modifier.height(10.dp));Text("${shown.size} titles"+(if(displayLoading) " · Loading${total?.let{" $it server items"} ?: "…"}" else ""),fontFamily=SpaceMonoFont,fontSize=11.sp,lineHeight=15.sp,color=StakTextMuted)
                    if(displayLoading) {Spacer(Modifier.height(8.dp));StakLoadingProgress(Modifier.fillMaxWidth())}
                    if(displayError!=null){Spacer(Modifier.height(10.dp));Text(displayError.orEmpty(),fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted);StakButton("Retry library",{if(parent==null)viewModel.refresh() else browser.select(parent)})}
                } else {
                    Spacer(Modifier.height(24.dp));Text("Your library awaits",fontFamily=DotoFont,fontWeight=FontWeight.Black,fontSize=22.sp,color=StakText)
                    Spacer(Modifier.height(10.dp));Text("Connect your Jellyfin server to browse your personal collection.",fontFamily=HankenFont,fontSize=14.sp,color=StakTextMuted)
                    Spacer(Modifier.height(12.dp));StakButton("Connect Jellyfin",onOpenAccount)
                }
            }
        }
        if(connected&&shown.isEmpty()&&!displayLoading&&displayError==null)item(span={GridItemSpan(maxLineSpan)}) {
            Column {
                Text(if(raw.isEmpty())"Nothing here yet" else "No matching titles",fontFamily=DotoFont,fontWeight=FontWeight.Bold,fontSize=20.sp,color=StakText)
                Spacer(Modifier.height(8.dp));Text(if(raw.isEmpty())"Jellyfin returned an empty library." else "Try another search or clear the filters.",fontFamily=HankenFont,fontSize=13.sp,color=StakTextMuted)
                if(raw.isNotEmpty())StakButton("Clear filters",{viewModel.setFilters(LibraryFilters())})
            }
        }
        items(shown,key={it.jellyfinId ?: it.id},contentType={filters.layout}) { item ->
            val key="library-all-${item.jellyfinId ?: item.id}"
            Column(stakItemMotion()) {
                if(filters.layout=="list")LibraryMediaRow(item,{onOpenTitle(item,key)})
                else StakPosterCard(item,{onOpenTitle(item,key)},compactTitle=true,showLibraryBadge=false,artworkModifier=Modifier.stakSharedElement(sharedScope,animatedScope,key))
                if(item.jellyfinVersionIds.orEmpty().size>1)TextButton(onClick={versions=item},contentPadding=PaddingValues(0.dp),modifier=Modifier.heightIn(min=44.dp)){Text("${item.jellyfinVersionIds.orEmpty().size} versions",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)}
            }
        }
    }
    }
    if(catalogOpen)JellyfinCollectionSheet(browser,{catalogOpen=false})
    versions?.let{item->JellyfinVersionsDialog(item,raw.filter{it.jellyfinId in item.jellyfinVersionIds.orEmpty()},{onOpenTitle(it,"library-version-${it.jellyfinId ?: it.id}")},{versions=null})}
    if(options)LibraryFilterDialog(filters,{viewModel.setFilters(it)},{options=false})
}

@Composable internal fun LibraryMediaRow(item:MediaItem,onClick:()->Unit) {
    var menu by remember(item.mediaKey){mutableStateOf(false)}
    Box(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).glassChrome(12.dp).border(1.dp,Color(0x24FFFFFF),RoundedCornerShape(12.dp)).combinedClickable(onClick=onClick,onLongClick={menu=true}).padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        LibraryArtwork(item.poster,item.name,Modifier.width(44.dp).height(66.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=19.sp,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(listOfNotNull(item.year,if(item.isSeries)"Series" else "Movie").joinToString(" · "),fontFamily=SpaceMonoFont,fontSize=11.sp,lineHeight=16.sp,color=StakTextMuted)
            Text(if(item.watched)"Watched" else if(item.progress>0)"${(item.progress*100).toInt()}% watched" else "Unwatched",fontFamily=SpaceMonoFont,fontSize=11.sp,lineHeight=15.sp,color=StakTextMuted)
        }
        if(item.watched)Icon(Lucide.Check,"Watched",tint=StakText,modifier=Modifier.size(17.dp))
    }
    PosterContextMenu(item,menu,{menu=false},onClick)
    }
}

@Composable internal fun LibraryArtwork(url:String?,name:String,modifier:Modifier=Modifier) {
    Box(modifier.clip(RoundedCornerShape(7.dp)).background(StakSurface),contentAlignment=Alignment.Center) {
        Icon(Lucide.Film,null,tint=StakTextMuted,modifier=Modifier.size(20.dp))
        StakArtwork(url,name,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
    }
}
