package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.font.FontWeight
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

@Composable fun MediaQualityDialog(report:QualityReport,onDismiss:()->Unit,alternatives:List<QualityReport> = emptyList()){
    var expanded by remember(report){mutableStateOf(false)};var compare by remember(report){mutableStateOf<QualityReport?>(null)}
    val primary=compactQualityFacts(report);val compared=compare?.let{compactQualityFacts(it)}.orEmpty()
    val verified=report.facts.any{it.value!=null&&it.confidence==FactConfidence.VERIFIED}
    GlassAlertDialog(onDismissRequest=onDismiss,title={Text("Media quality",fontFamily=DotoFont,fontWeight=FontWeight.Bold)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(report.title,fontFamily=HankenFont,fontSize=14.sp,color=StakText,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(if(verified)"Jellyfin metadata"else "Filename estimates · size reported by provider",fontFamily=HankenFont,fontSize=10.sp,color=StakTextMuted)
        if(primary.isEmpty())Text("No technical details available.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
        primary.forEach{fact->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Text(fact.label,Modifier.weight(.27f),fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)
            Column(Modifier.weight(if(compare==null).73f else .365f)){
                Text(fact.value.orEmpty(),fontFamily=HankenFont,fontSize=12.sp,lineHeight=17.sp,color=StakText,maxLines=3,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if(!verified)Text(compactQualityConfidence(report,fact.key),fontFamily=SpaceMonoFont,fontSize=8.sp,color=StakTextMuted)
            }
            if(compare!=null)Text(compared.firstOrNull{it.key==fact.key}?.value ?: "Unknown",Modifier.weight(.365f),fontFamily=HankenFont,fontSize=11.sp,color=StakText)
        }}
        if(alternatives.isNotEmpty())DiscoverySelect("Compare with",alternatives.indexOf(compare).toString(),listOf("-1" to "No comparison")+alternatives.mapIndexed{i,value->i.toString() to value.title.take(70)},{index->compare=alternatives.getOrNull(index.toIntOrNull() ?: -1)})
        StakButton(if(expanded)"Less details"else "More details",{expanded=!expanded},variant=StakButtonVariant.Ghost,compact=true)
        if(expanded){report.facts.forEach{fact->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(fact.label,Modifier.weight(.4f),fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted);Column(Modifier.weight(.6f)){Text(fact.value ?: "Unknown",fontFamily=HankenFont,fontSize=12.sp,color=StakText);Text(fact.confidence.name,fontFamily=SpaceMonoFont,fontSize=8.sp,color=StakTextMuted)}}};compatibilityWarnings(report,readPlaybackDevice()).forEach{Text(it,fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)}}
    }},confirmButton={StakButton("Close",onDismiss)})
}

@Composable fun JellyfinQualityButton(media:MediaItem,modifier:Modifier=Modifier,available:Boolean=media.jellyfinId!=null||LocalPosterActions.current?.isInJellyfin?.invoke(media)==true){
    if(!available||!NativeRuntime.settings.jellyfinConnected)return
    var episodes by remember(media.mediaKey){mutableStateOf<List<com.google.gson.JsonObject>?>(null)}
    var episodeParent by remember(media.mediaKey){mutableStateOf<String?>(null)};var total by remember{mutableIntStateOf(0)}
    val scope=rememberCoroutineScope();var report by remember(media.mediaKey){mutableStateOf<QualityReport?>(null)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    Column(modifier){StakButton(if(busy)"Inspecting…" else "Media quality",{busy=true;error=null;scope.launch{try{val id=JellyfinPlayback.resolve(media);report=if(id==null){error="This title is not in your Jellyfin library.";null}else {val raw=JellyfinIntelligence.item(id);if(raw.text("Type") in setOf("Series","Season")){val list=JellyfinIntelligence.items(mapOf("ParentId" to id,"Recursive" to "true","IncludeItemTypes" to "Episode","SortBy" to "ParentIndexNumber,IndexNumber","Fields" to "MediaSources,MediaStreams,UserData,Path","Limit" to "100"));episodes=list.objects("Items");episodeParent=id;total=list.number("TotalRecordCount")?.toInt() ?: episodes.orEmpty().size;null}else verifiedQuality(raw)}}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}catch(_:Exception){error="Could not load media information. Check Jellyfin or retry."}finally{busy=false}}},enabled=!busy,variant=StakButtonVariant.Ghost,icon=Lucide.Film,compact=true);error?.let{Text(it,fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)}}
    episodes?.let{rows->GlassAlertDialog(onDismissRequest={episodes=null},title={Text("Choose episode",color=StakText,fontFamily=DotoFont)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(rows.isEmpty())Text("No episodes were returned for this user.",color=StakTextMuted,fontFamily=HankenFont)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=400.dp)){items(rows,key={it.text("Id").orEmpty()}){row->StakButton("S${row.number("ParentIndexNumber")?.toInt() ?: "?"} E${row.number("IndexNumber")?.toInt() ?: "?"} · ${row.text("Name").orEmpty()}",{episodes=null;report=verifiedQuality(row)},variant=StakButtonVariant.Ghost,compact=true)}}
        if(rows.size<total)StakButton("Load more",{busy=true;scope.launch{try{val next=JellyfinIntelligence.items(mapOf("ParentId" to episodeParent.orEmpty(),"Recursive" to "true","IncludeItemTypes" to "Episode","SortBy" to "ParentIndexNumber,IndexNumber","Fields" to "MediaSources,MediaStreams,UserData,Path","Limit" to "100","StartIndex" to rows.size.toString()));episodes=(rows+next.objects("Items")).distinctBy{it.text("Id")}}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}catch(_:Exception){error="Could not load more episodes."}finally{busy=false}}},enabled=!busy)
    }},confirmButton={StakButton("Close",{episodes=null})})}
    report?.let{MediaQualityDialog(it,{report=null})}
}
