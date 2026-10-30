@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

@Composable internal fun JellyfinCollectionSheet(browser:JellyfinBrowserViewModel,onDismiss:()->Unit) {
    val catalog by browser.catalog.collectAsState();val busy by browser.catalogBusy.collectAsState()
    LaunchedEffect(Unit){browser.loadCatalog()}
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=StakSurface,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        app.stak.nativeapp.ui.components.SmoothDialogWindow()
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=620.dp),contentPadding=PaddingValues(16.dp,0.dp,16.dp,32.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item{Text("Libraries & collections",fontFamily=DotoFont,fontSize=22.sp,color=StakText)}
            item{StakButton("All titles",{browser.select(null);onDismiss()})}
            if(busy)item{Text("Loading library catalog…",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)}
            if(catalog?.issues?.isNotEmpty()==true)item{Text(catalog!!.issues.joinToString(" "),fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted);StakButton("Retry catalog",{browser.loadCatalog()})}
            if(!busy&&catalog?.libraries?.isNotEmpty()==true)item{Text("LIBRARIES",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)}
            items(catalog?.libraries.orEmpty(),key={"library:${it.id}"}){collection->CollectionChoice(collection){browser.select(collection);onDismiss()}}
            if(!busy&&catalog?.collections?.isNotEmpty()==true)item{Text("COLLECTIONS",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)}
            items(catalog?.collections.orEmpty(),key={"collection:${it.id}"}){collection->CollectionChoice(collection){browser.select(collection);onDismiss()}}
            if(!busy&&catalog!=null&&catalog!!.libraries.isEmpty()&&catalog!!.collections.isEmpty()&&catalog!!.issues.isEmpty())item{Text("No libraries or collections were returned by Jellyfin.",fontFamily=HankenFont,color=StakTextMuted)}
        }
    }
}
@Composable private fun CollectionChoice(value:JellyfinCollection,onClick:()->Unit) {
    ListItem(headlineContent={Text(value.name,fontFamily=HankenFont)},supportingContent={Text(if(value.kind=="library")"Library" else value.count?.let{"$it items"} ?: "Collection",fontFamily=SpaceMonoFont,fontSize=10.sp)},trailingContent={Icon(Lucide.ChevronRight,null)},modifier=Modifier.clickable(onClick=onClick),colors=ListItemDefaults.colors(containerColor=StakSurface,headlineColor=StakText,supportingColor=StakTextMuted))
}
@Composable internal fun JellyfinVersionsDialog(item:MediaItem,versions:List<MediaItem>,onOpenTitle:(MediaItem)->Unit,onDismiss:()->Unit) {
    GlassAlertDialog(onDismissRequest=onDismiss,containerColor=StakSurface,title={Text("${item.name} · Versions",fontFamily=DotoFont)},text={LazyColumn(Modifier.heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        items(versions,key={it.jellyfinId ?: it.id}){version->Column {
            LibraryMediaRow(version.copy(name=version.jellyfinVersionLabel ?: "Version ${version.jellyfinId?.take(8).orEmpty()}"),{onDismiss();onOpenTitle(version)})
            version.runtime?.let{Text(it,fontFamily=SpaceMonoFont,fontSize=9.sp,color=StakTextMuted)}
        }}
    }},confirmButton={StakButton("Close",onDismiss)})
}
