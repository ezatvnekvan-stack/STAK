@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package app.stak.nativeapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

@Composable
fun ListsScreen(onOpenTitle:(MediaItem,String)->Unit,sharedScope:SharedTransitionScope?,animatedScope:AnimatedVisibilityScope?,watchIds:Set<String>,viewModel:ListsViewModel=androidx.lifecycle.viewmodel.compose.viewModel()){
    val state by viewModel.state.collectAsState()
    val previews by viewModel.previews.collectAsState()
    val browserGrid=rememberLazyGridState()
    val titlesGrid=rememberLazyGridState()
    val grid=if(state.selected==null)browserGrid else titlesGrid
    val current=state.selected
    BackHandler(current!=null){viewModel.back()}
    val scope=rememberCoroutineScope()
    LazyVerticalGrid(columns=GridCells.Fixed(if(current==null)1 else 3),state=grid,
        modifier=Modifier.fillMaxSize().testTag("public-lists"),
        contentPadding=PaddingValues(16.dp,8.dp,16.dp,120.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item(key="list-controls",span={GridItemSpan(maxLineSpan)}){
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                if(current==null){
                    StakTextField(state.query,viewModel::query,"Search community lists",leading={Icon(Lucide.Search,null,tint=StakTextMuted,modifier=Modifier.size(18.dp))})
                    StakTabs(listOf("popular" to "Popular","trending" to "Trending","saved" to "Saved"),state.category,{category->if(category!=state.category){viewModel.category(category);scope.launch{browserGrid.scrollToItem(0)}}})
                    
                }else{
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        StakButton("Back",viewModel::back,compact=true)
                        Spacer(Modifier.weight(1f))
                        StakButton(if(state.saved.any{it.id==current.id})"Saved"else "Save list",{viewModel.toggleSaved(current)},compact=true,icon=Lucide.Bookmark)
                    }
                    Text(current.name,fontFamily=DotoFont,fontWeight=FontWeight.Bold,fontSize=24.sp,color=StakText)
                    Text("${current.owner} · ${current.count} items",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
                    current.description?.takeIf{it.isNotBlank()}?.let{description->
                        var expanded by androidx.compose.runtime.saveable.rememberSaveable(current.id){mutableStateOf(false)}
                        Text(description,fontFamily=HankenFont,fontSize=13.sp,lineHeight=20.sp,color=StakTextSoft,maxLines=if(expanded)Int.MAX_VALUE else 3,overflow=TextOverflow.Ellipsis)
                        if(description.length>140)StakButton(if(expanded)"Read less"else "Read more",{expanded=!expanded},compact=true,variant=StakButtonVariant.Ghost)
                    }
                }
            }
        }
        if(state.error!=null)item(span={GridItemSpan(maxLineSpan)}){InlineRetry(state.error.orEmpty()){viewModel.reload(more=state.page>1)}}
        if(current==null){
            items(state.lists,key={"list:${it.id}"},contentType={"community-list"}){list->
                LaunchedEffect(list.id){kotlinx.coroutines.delay(180);viewModel.preview(list)}
                PublicListCard(list,previews[list.id].orEmpty(),state.saved.any{it.id==list.id},{viewModel.open(list);scope.launch{titlesGrid.scrollToItem(0)}},{viewModel.toggleSaved(list)})
            }
        }else{
            items(state.titles,key={"${it.type}:${it.mediaKey}"},contentType={"poster"}){item->
                var media by remember(item){mutableStateOf(item)}
                LaunchedEffect(item.id){kotlinx.coroutines.delay(80);media=viewModel.artwork(item)}
                val key="public-list-${current.id}-${item.type}-${item.mediaKey}"
                StakPosterCard(media,{onOpenTitle(media,key)},artworkModifier=Modifier.stakSharedElement(sharedScope,animatedScope,key),compactTitle=true,titleSize=13,yearSize=10,inWatchlist=item.mediaKey in watchIds||item.id in watchIds)
            }
        }
        if(state.loading)item(span={GridItemSpan(maxLineSpan)}){Text("Loading…",Modifier.padding(16.dp),fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)}
        if(!state.loading&&state.error==null&&(if(current==null)state.lists.isEmpty()else state.titles.isEmpty()))item(span={GridItemSpan(maxLineSpan)}){
            Text(if(state.category=="saved"&&current==null)"Save a list to keep it here."else "No matching lists or titles.",Modifier.padding(vertical=24.dp),fontFamily=HankenFont,color=StakTextMuted)
        }
        if(state.page<state.pages)item(span={GridItemSpan(maxLineSpan)}){StakButton("Load more",{viewModel.reload(more=true)},Modifier.fillMaxWidth(),enabled=!state.loading)}
    }
}

@Composable internal fun PublicListCard(list:PublicTraktList,previews:List<MediaItem>,saved:Boolean,onOpen:()->Unit,onSave:()->Unit){
    val shape=RoundedCornerShape(20.dp)
    val press=rememberPressMotion()
    Column(Modifier.fillMaxWidth().pressMotion(press).clip(shape).background(Color.White.copy(alpha=.025f))
        .border(1.dp,Color.White.copy(alpha=.18f),shape).clickable(interactionSource=press.source,indication=androidx.compose.foundation.LocalIndication.current,onClick=onOpen)){
        Row(Modifier.fillMaxWidth().height(104.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)){
            repeat(3){index->Box(Modifier.weight(1f).fillMaxHeight().background(Color.White.copy(alpha=.035f))){
                val artwork=previews.getOrNull(index)?.poster
                if(artwork==null)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("STAK",fontFamily=DotoFont,fontSize=18.sp,color=StakTextFaint)}
                else StakArtwork(artwork,null,Modifier.fillMaxSize())
            }}
        }
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Text(list.name,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=17.sp,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text("${list.owner} · ${list.count} items",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            StakIconButton(onSave){Icon(Lucide.Bookmark,if(saved)"Unsave ${list.name}"else "Save ${list.name}",tint=if(saved)StakAccent else StakTextMuted,modifier=Modifier.size(19.dp))}
        }
    }
}
