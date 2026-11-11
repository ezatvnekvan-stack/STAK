package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.LibraryFilters
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

@Composable internal fun LibrarySearch(query:String,onQuery:(String)->Unit,onFilters:()->Unit,torbox:Boolean=false) {
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        StakTextField(query,onQuery,if(torbox)"Search names, files or hash" else "Search titles or IMDb ID",Modifier.weight(1f),height=46,leading={Icon(Lucide.Search,null,tint=StakTextMuted,modifier=Modifier.size(16.dp))})
        IconButton(onClick=onFilters,modifier=Modifier.size(44.dp)){Icon(Lucide.Sliders,"Library filters",tint=StakText)}
    }
}

@Composable internal fun LibraryFilterDialog(filters:LibraryFilters,onChange:(LibraryFilters)->Unit,onDismiss:()->Unit,torbox:Boolean=false,libraryName:String?=null,onChooseLibrary:(()->Unit)?=null) {
    GlassAlertDialog(onDismissRequest=onDismiss,containerColor=StakSurface,title={Text("Library filters",fontFamily=DotoFont)},text={Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
        DiscoverySelect("Sort by",filters.sort,if(torbox)listOf("newest" to "Newest","name" to "Name A–Z","size" to "Largest first") else librarySortOptions,{onChange(filters.copy(sort=it))})
        if(!torbox) {
            if(onChooseLibrary!=null)LibraryChoice(libraryName?:"All libraries",true,onChooseLibrary,Modifier.fillMaxWidth())
            DiscoverySelect("Watched status",filters.status,listOf("all" to "All titles","unwatched" to "Unwatched","watched" to "Watched","progress" to "In progress"),{onChange(filters.copy(status=it))})
            DiscoverySelect("View",filters.layout,listOf("grid" to "Poster grid","list" to "List"),{onChange(filters.copy(layout=it))})
        }
    }},confirmButton={StakButton("Done",onDismiss)},dismissButton={StakButton("Reset",{onChange(LibraryFilters(sort=if(torbox)"newest" else "name"))},variant=StakButtonVariant.Ghost)})
}
