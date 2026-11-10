@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

@Composable fun DiscoverScreen(
    onOpenTitle:(MediaItem,String)->Unit,sharedScope:SharedTransitionScope?,animatedScope:AnimatedVisibilityScope?,initialQuery:String,
    watchIds:Set<String> = emptySet(),viewModel:DiscoverViewModel=androidx.lifecycle.viewmodel.compose.viewModel(),
    deckModel:DiscoverDeckViewModel=androidx.lifecycle.viewmodel.compose.viewModel(),
    listsModel:ListsViewModel=androidx.lifecycle.viewmodel.compose.viewModel()
){
    val type by viewModel.type.collectAsState();val region by viewModel.region.collectAsState()
    val query by viewModel.query.collectAsState();val filters by viewModel.filters.collectAsState()
    val genres by viewModel.genres.collectAsState();val providers by viewModel.providers.collectAsState()
    val deck by deckModel.state.collectAsState();val lists by listsModel.state.collectAsState();val previews by listsModel.previews.collectAsState()
    var searchOpen by rememberSaveable{mutableStateOf(initialQuery.isNotBlank())}
    var requestedResults by rememberSaveable{mutableStateOf(false)}
    var collections by rememberSaveable{mutableStateOf(false)}
    var filterOpen by remember{mutableStateOf(false)}
    val states=rememberSaveableStateHolder()
    val results=query.isNotBlank()||filters!=DiscoveryFilters()||requestedResults
    val chips=filters.chips(genres,providers+streamingShortcuts(providers))
    val reduced=LocalReducedMotion.current
    fun explore(){requestedResults=false;searchOpen=false;viewModel.clearSearch()}
    fun browse(category:String){requestedResults=true;viewModel.setCategory(category)}
    LaunchedEffect(initialQuery){if(initialQuery.isNotBlank()){searchOpen=true;viewModel.setQuery(initialQuery)}}
    LaunchedEffect(type,region,results,collections){if(!results&&!collections)deckModel.load(type,region)}
    BackHandler(results||searchOpen||collections){if(collections){if(lists.selected!=null)listsModel.back()else collections=false}else explore()}
    Column(Modifier.fillMaxSize().testTag("discover-remake")){
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
            if(collections||results)StakIconButton({if(collections){if(lists.selected!=null)listsModel.back()else collections=false}else explore()}){Icon(Lucide.ChevronRight,"Back to discovery",Modifier.size(18.dp).graphicsLayer{rotationZ=180f},tint=StakText)}
            StakPageHeader(if(collections)"Collections"else "Discover",modifier=Modifier.weight(1f))
            if(!collections){
                StakIconButton({searchOpen=!searchOpen},Modifier.testTag("discover-search-toggle")){Icon(if(searchOpen)Lucide.X else Lucide.Search,if(searchOpen)"Close discovery search"else "Search Discover",Modifier.size(20.dp),tint=StakText)}
                StakIconButton({filterOpen=true}){Icon(Lucide.Sliders,"Discovery filters",Modifier.size(20.dp),tint=StakText);if(chips.isNotEmpty())Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(5.dp).background(StakAccent,RoundedCornerShape(5.dp)))}
            }
        }
        if(!collections){
            NothingExpand(searchOpen){Box(Modifier.padding(horizontal=16.dp).padding(bottom=8.dp)){
                StakTextField(query,viewModel::setQuery,"Movie, series or IMDb ID",height=48,leading={Icon(Lucide.Search,null,tint=StakTextMuted,modifier=Modifier.size(17.dp))},trailing=if(query.isNotBlank()){{StakIconButton({viewModel.setQuery("")}){Icon(Lucide.X,"Clear discovery search",tint=StakTextMuted,modifier=Modifier.size(16.dp))}}}else null)
            }}
            if(query.isBlank()){
                MediaTypeSelector(listOf("movie" to "Movies","series" to "Series"),type,viewModel::setType,Modifier.padding(horizontal=16.dp))
                Box(Modifier.padding(horizontal=16.dp,vertical=12.dp)){DiscoveryServices(viewModel)}
            }
        }
        AnimatedContent(if(collections)"lists"else if(results)"results"else "explore",modifier=Modifier.weight(1f),transitionSpec={fadeIn(tween(if(reduced)80 else 180)) togetherWith fadeOut(tween(if(reduced)60 else 110))},label="discover-mode"){mode->
            states.SaveableStateProvider(mode){when(mode){
                "lists"->ListsScreen(onOpenTitle,sharedScope,animatedScope,watchIds,listsModel)
                "results"->DiscoverResults(viewModel,onOpenTitle,sharedScope,animatedScope,watchIds,{requestedResults=false;viewModel.clearSearch()})
                else->DiscoverExplore(deck,lists,previews,genres,onOpenTitle,{browse(it)},{option->viewModel.applyFilters(filters.copy(genre=option.id))},{collections=true},{list->listsModel.open(list);collections=true},listsModel::toggleSaved,{list->listsModel.preview(list)},{deckModel.load(type,region,true)},{listsModel.reload()},sharedScope,animatedScope,watchIds)
            }}
        }
    }
    if(filterOpen)DiscoveryFilterSheet(type,filters,genres,{filterOpen=false}){if(viewModel.applyFilters(it))filterOpen=false}
}

@Composable private fun DiscoverResults(vm:DiscoverViewModel,onOpen:(MediaItem,String)->Unit,shared:SharedTransitionScope?,animated:AnimatedVisibilityScope?,watchIds:Set<String>,onReset:()->Unit){
    val titles by vm.items.collectAsState();val loading by vm.loading.collectAsState();val more by vm.loadingMore.collectAsState()
    val error by vm.error.collectAsState();val partial by vm.partialErrors.collectAsState();val page by vm.page.collectAsState();val pages by vm.totalPages.collectAsState()
    val filters by vm.filters.collectAsState();val genres by vm.genres.collectAsState();val providers by vm.providers.collectAsState();val category by vm.category.collectAsState()
    val chips=filters.chips(genres,providers+streamingShortcuts(providers))
    LazyVerticalGrid(GridCells.Fixed(3),state=rememberLazyGridState(),contentPadding=PaddingValues(16.dp,4.dp,16.dp,120.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(18.dp),modifier=Modifier.fillMaxSize().testTag("discover-results")){
        item(span={GridItemSpan(maxLineSpan)}){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){DiscoverCategoryPicker(category,vm::setCategory);Spacer(Modifier.weight(1f));DiscoverSortMenu(filters.sort){vm.applyFilters(filters.copy(sort=it))}}
            if(chips.isNotEmpty())LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(chips,key={it.first}){(id,label)->if(id=="certification")CertificationFilterChip(label){vm.clearFilter(id)}else StakButton("$label ×",{vm.clearFilter(id)},compact=true)};item{StakButton("Reset",onReset,compact=true,variant=StakButtonVariant.Ghost)}}
            Text("${titles.size} ${if(titles.size==1)"title"else "titles"}",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
            if(filters.imdbRating.isNotBlank())Text("Verified IMDb ratings · unrated titles excluded",fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)
        }}
        error?.let{item(span={GridItemSpan(maxLineSpan)}){InlineRetry(it){if(titles.isEmpty())vm.load()else vm.loadMore()}}}
        if(partial.isNotEmpty())item(span={GridItemSpan(maxLineSpan)}){InlineRetry("Some titles could not load."){vm.load()}}
        if(loading&&titles.isEmpty())items(9){ShimmerBox(Modifier.fillMaxWidth().aspectRatio(2f/3f))}
        items(titles,key={"${it.type}:${it.mediaKey}"},contentType={"poster"}){media->
            val key="discover-${media.type}-${media.mediaKey}"
            StakPosterCard(media,{onOpen(media,key)},artworkModifier=Modifier.stakSharedElement(shared,animated,key),compactTitle=true,titleSize=13,yearSize=11,inWatchlist=media.mediaKey in watchIds)
        }
        if(!loading&&titles.isEmpty()&&error==null)item(span={GridItemSpan(maxLineSpan)}){StakEmpty("No matching titles","Try another search or fewer filters.",actionLabel="Clear filters",onAction={vm.resetFilters()})}
        if(page<pages)item(span={GridItemSpan(maxLineSpan)}){StakButton(if(more)"Loading…"else "Load more",vm::loadMore,Modifier.fillMaxWidth(),enabled=!loading&&!more)}
    }
}

@Composable private fun DiscoverCategoryPicker(value:String,onChange:(String)->Unit){
    var open by remember{mutableStateOf(false)}
    Box{StakButton(discoveryCategories.firstOrNull{it.first==value}?.second?:"Popular",{open=true},compact=true,icon=Lucide.ChevronDown)
        GlassDropdownMenu(open,{open=false}){discoveryCategories.forEach{(id,label)->DropdownMenuItem(text={Text(label,fontFamily=HankenFont)},onClick={open=false;onChange(id)})}}
    }
}

@Composable internal fun DiscoverExplore(
    deck:DiscoverDeck,lists:ListsState,previews:Map<Long,List<MediaItem>>,genres:List<DiscoveryOption>,onOpen:(MediaItem,String)->Unit,
    onBrowse:(String)->Unit,onGenre:(DiscoveryOption)->Unit,onCollections:()->Unit,onList:(PublicTraktList)->Unit,onSave:(PublicTraktList)->Unit,
    preview:suspend(PublicTraktList)->Unit,onRetry:()->Unit,onRetryLists:()->Unit,shared:SharedTransitionScope?=null,animated:AnimatedVisibilityScope?=null,watchIds:Set<String> = emptySet()
){
    val top=deck.shelves.first{it.id=="top"};val featured=top.items.firstOrNull()
    LazyColumn(state=rememberLazyListState(),modifier=Modifier.fillMaxSize().testTag("discover-explore"),contentPadding=PaddingValues(16.dp,4.dp,16.dp,120.dp),verticalArrangement=Arrangement.spacedBy(26.dp)){
        item(key="tonight"){Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            DiscoverySection("FOR TONIGHT","Popular on streaming")
            if(top.items.isNotEmpty())BoxWithConstraints(Modifier.fillMaxWidth()){
                val cardWidth=if(top.items.size>1)maxWidth*.94f else maxWidth
                LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    items(top.items,key={it.mediaKey}){pick->
                        val key="discover-feature-${pick.mediaKey}"
                        Box(Modifier.width(cardWidth).height(206.dp).clip(RoundedCornerShape(20.dp)).background(StakSurface).clickable{onOpen(pick,key)}.testTag("discover-feature")){
                            StakArtwork(pick.background?:pick.poster,null,Modifier.fillMaxSize().stakSharedElement(shared,animated,key))
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xF008090B)))))
                            Column(Modifier.align(Alignment.BottomStart).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                                TitleBrand(pick,Modifier.widthIn(max=248.dp).fillMaxWidth())
                                Text((listOfNotNull(pick.year)+pick.genres.take(2)).joinToString(" · "),fontFamily=HankenFont,fontSize=12.sp,color=StakTextSoft,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }else if(top.loading)ShimmerBox(Modifier.fillMaxWidth().height(206.dp))
            else if(top.error!=null)InlineRetry(top.error,onClick=onRetry)
            else Text("No streaming picks could be confirmed for your region.",fontFamily=HankenFont,fontSize=13.sp,color=StakTextMuted)
        }}
        val moods=genres.filter{it.name.lowercase() in setOf("thriller","comedy","mystery","horror","science fiction","sci-fi & fantasy","drama")}.distinctBy{it.id}.take(6)
        if(moods.isNotEmpty())item(key="moods"){Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("FIND YOUR MOOD",fontFamily=DotoFont,fontWeight=FontWeight.Bold,fontSize=16.sp,color=StakText)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(moods,key={it.id}){genre->StakChip(genre.name,false,{onGenre(genre)})}}
        }}
        item(key="trending"){DiscoverShelfRow("TRENDING NOW",deck.shelves.first{it.id=="trending"},onOpen,{onBrowse("trending")},onRetry,shared,animated,watchIds)}
        item(key="collections"){Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            DiscoverySection("BROWSE COLLECTIONS","Community film lists",onCollections)
            val available=lists.lists.take(4)
            if(available.isNotEmpty())LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                items(available,key={it.id}){list->LaunchedEffect(list.id){preview(list)};Box(Modifier.width(264.dp)){PublicListCard(list,previews[list.id].orEmpty(),lists.saved.any{it.id==list.id},{onList(list)},{onSave(list)})}}
            }else if(lists.loading)ShimmerBox(Modifier.fillMaxWidth().height(160.dp))
            else if(lists.error!=null)InlineRetry(lists.error,onClick=onRetryLists)
            StakButton("Browse all lists",onCollections,compact=true,variant=StakButtonVariant.Ghost,icon=Lucide.ChevronRight)
        }}
        item(key="hidden"){DiscoverShelfRow("WORTH DISCOVERING",deck.shelves.first{it.id=="hidden"},onOpen,{onBrowse("hidden")},onRetry,shared,animated,watchIds)}
        item(key="browse-all"){StakButton("Browse all titles",{onBrowse("popular")},Modifier.fillMaxWidth(),icon=Lucide.ChevronRight)}
    }
}

@Composable private fun DiscoverySection(title:String,subtitle:String?=null,onMore:(()->Unit)?=null){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text(title,fontFamily=DotoFont,fontWeight=FontWeight.Bold,fontSize=16.sp,color=StakText)
        subtitle?.let{Text(it,fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)}
    };if(onMore!=null)StakIconButton(onMore){Icon(Lucide.ChevronRight,"Browse $title",tint=StakTextMuted,modifier=Modifier.size(19.dp))}}
}

@Composable private fun DiscoverShelfRow(title:String,shelf:DiscoverShelf,onOpen:(MediaItem,String)->Unit,onMore:()->Unit,onRetry:()->Unit,shared:SharedTransitionScope?,animated:AnimatedVisibilityScope?,watchIds:Set<String>){
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        DiscoverySection(title,onMore=onMore)
        if(shelf.items.isNotEmpty())LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){items(shelf.items,key={"${it.type}:${it.mediaKey}"}){media->
            val key="discover-${shelf.id}-${media.mediaKey}"
            StakPosterCard(media,{onOpen(media,key)},Modifier.width(116.dp),artworkModifier=Modifier.stakSharedElement(shared,animated,key),compactTitle=true,titleSize=13,yearSize=11,inWatchlist=media.mediaKey in watchIds)
        }}else if(shelf.loading)Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){repeat(3){ShimmerBox(Modifier.weight(1f).aspectRatio(2f/3f))}}
        if(shelf.error!=null)InlineRetry(shelf.error,onClick=onRetry)
        else if(!shelf.loading&&shelf.items.isEmpty())Text("No titles available in this selection.",fontFamily=HankenFont,fontSize=13.sp,color=StakTextMuted)
    }
}
