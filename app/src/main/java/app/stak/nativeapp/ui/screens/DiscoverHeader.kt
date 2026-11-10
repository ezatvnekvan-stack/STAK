package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

@Composable internal fun DiscoverHeading(activeCount:Int,onFilters:()->Unit){
    StakPageHeader("Discover",trailing={
        Row(Modifier.heightIn(min=44.dp).widthIn(min=44.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick=onFilters).semantics{contentDescription="Filters";stateDescription="$activeCount active filters"}.padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
            Icon(Lucide.Sliders,null,tint=StakText,modifier=Modifier.size(19.dp))
            if(activeCount>0)Text(activeCount.toString(),fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakText)
        }
    })
}

@Composable internal fun DiscoverBrowseHeader(
    query:String,type:String,category:String,filters:DiscoveryFilters,count:Int,chips:List<Pair<String,String>>,
    onQuery:(String)->Unit,onType:(String)->Unit,onCategory:(String)->Unit,onClear:(String)->Unit,onReset:()->Unit,onSort:(String)->Unit,
    services:@Composable ()->Unit
){
    Column(Modifier.fillMaxWidth().testTag("discover-browse-header")){
        StakTextField(query,onQuery,"Movie, series or IMDb ID",height=46,leading={Icon(Lucide.Search,null,tint=StakTextFaint,modifier=Modifier.size(16.dp))},trailing=if(query.isNotEmpty()){{Box(Modifier.size(44.dp).clickable{onQuery("")},contentAlignment=Alignment.Center){Icon(Lucide.X,"Clear search",tint=StakTextMuted,modifier=Modifier.size(18.dp))}}}else null)
        if(query.isBlank()){
            Spacer(Modifier.height(6.dp));MediaTypeSelector(listOf("movie" to "Movies","series" to "Series"),type,onType,Modifier.fillMaxWidth())
        }
        val extraChips=chips.filter{it.first !in listOf("provider","sort")}
        if(extraChips.isNotEmpty()){
            Spacer(Modifier.height(4.dp))
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                items(extraChips,key={it.first}){(id,label)->
                    Box(Modifier.heightIn(min=44.dp).clickable{onClear(id)},contentAlignment=Alignment.Center){Text("$label ×",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakText,modifier=Modifier.border(1.dp,StakBorder,RoundedCornerShape(8.dp)).padding(horizontal=9.dp,vertical=6.dp))}
                }
                item{StakButton("Reset",onReset,compact=true,variant=StakButtonVariant.Ghost)}
            }
        }
        Spacer(Modifier.height(10.dp));services()
        Row(Modifier.fillMaxWidth().heightIn(min=44.dp).padding(top=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            if(query.isBlank())DiscoverCategoryMenu(category,onCategory)
            else Text("$count results",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
            DiscoverSortMenu(filters.sort,onSort)
        }
    }
}

@Composable internal fun DiscoverCategoryTabs(selected:String,onSelect:(String)->Unit){
    val primary=listOf("popular","trending","top","upcoming");val secondary=discoveryCategories.filter{it.first !in primary}
    var more by remember{mutableStateOf(false)}
    LazyRow(horizontalArrangement=Arrangement.spacedBy(18.dp),modifier=Modifier.fillMaxWidth().testTag("discover-category-tabs")){
        items(primary,key={it}){id->
            val active=selected==id
            Box(Modifier.height(44.dp).selectable(active,role=Role.Tab){onSelect(id)},contentAlignment=Alignment.Center){
                Text(discoveryCategories.first{it.first==id}.second,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=12.sp,color=if(active)StakText else StakTextMuted)
                if(active)Box(Modifier.align(Alignment.BottomCenter).width(22.dp).height(2.dp).background(StakText,CircleShape))
            }
        }
        item{
            Box{
                val active=selected !in primary
                Row(Modifier.height(44.dp).clip(RoundedCornerShape(10.dp)).border(1.dp,Color.White.copy(alpha=.3f),RoundedCornerShape(10.dp)).clickable{more=true}.semantics{contentDescription="More categories"}.padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    Text(if(active)secondary.firstOrNull{it.first==selected}?.second ?: "More"else "More",fontFamily=HankenFont,fontSize=12.sp,color=if(active)StakText else StakTextMuted)
                    Icon(Lucide.ChevronDown,null,tint=StakTextMuted,modifier=Modifier.size(12.dp))
                }
                GlassDropdownMenu(more,{more=false}){secondary.forEach{(id,label)->DropdownMenuItem(text={Text(label,fontFamily=HankenFont,color=if(id==selected)StakText else StakTextMuted)},onClick={more=false;onSelect(id)})}}
            }
        }
    }
}

@Composable internal fun DiscoverSortMenu(value:String,onSelect:(String)->Unit){
    var open by remember{mutableStateOf(false)}
    Box{
        Row(Modifier.heightIn(min=44.dp).clip(RoundedCornerShape(10.dp)).border(1.dp,Color.White.copy(alpha=.3f),RoundedCornerShape(10.dp)).clickable{open=true}.semantics{contentDescription="Sort"}.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
            Text(discoverySorts.filterNot{it.first=="vote_average.desc"}.firstOrNull{it.first==value}?.second ?: "Recommended",fontFamily=HankenFont,fontSize=12.sp,color=StakText)
            Icon(Lucide.ChevronDown,null,tint=StakTextMuted,modifier=Modifier.size(13.dp))
        }
        GlassDropdownMenu(open,{open=false}){discoverySorts.filterNot{it.first=="vote_average.desc"}.forEach{(id,label)->DropdownMenuItem(text={Text(label,fontFamily=HankenFont,color=if(id==value)StakText else StakTextMuted)},onClick={open=false;onSelect(id)})}}
    }
}

@Composable private fun DiscoverCategoryMenu(value:String,onSelect:(String)->Unit){
    var open by remember{mutableStateOf(false)}
    Box(Modifier.testTag("discover-category-tabs")){
        StakButton(discoveryCategories.firstOrNull{it.first==value}?.second?:"Popular",{open=true},compact=true,icon=Lucide.ChevronDown)
        GlassDropdownMenu(open,{open=false}){discoveryCategories.forEach{(id,label)->DropdownMenuItem(text={Text(label,fontFamily=HankenFont,color=if(id==value)StakText else StakTextMuted)},onClick={open=false;onSelect(id)})}}
    }
}
