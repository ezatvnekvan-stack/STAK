package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.*

@Composable fun TorznabSourcesSettings(settings:SettingsStore=NativeRuntime.settings,candidate:TorznabIndexer?=null,onConsumed:()->Unit={}){
    val store=remember(settings){TorznabStore(settings)};val loaded=remember(store){runCatching{store.read()}};var entries by remember{mutableStateOf(loaded.getOrDefault(emptyList()))};val scope=rememberCoroutineScope();var saving by remember{mutableStateOf(false)};var editor by remember{mutableStateOf<TorznabIndexer?>(null)}
    var remove by remember{mutableStateOf<TorznabIndexer?>(null)};var error by remember{mutableStateOf<String?>(if(loaded.isFailure)"Could not read saved indexers. Existing settings have been preserved."else null)}
    fun persist(next:List<TorznabIndexer>,done:()->Unit={}){saving=true;scope.launch{try{entries=withContext(Dispatchers.IO){store.save(next);store.read()};error=null;done()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){error="Could not save indexers. Please try again."}finally{saving=false}}}
    LaunchedEffect(candidate?.id){candidate?.let{editor=it;onConsumed()}}
    if(entries.isNotEmpty())Text("Indexers",fontFamily=DotoFont,fontSize=17.sp,color=StakText)
    entries.forEach{entry->Column(Modifier.fillMaxWidth().padding(vertical=6.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(entry.name,fontFamily=HankenFont,fontSize=16.sp,color=StakText);Text("Torznab",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)};Switch(entry.enabled,{enabled->persist(entries.map{if(it.id==entry.id)it.copy(enabled=enabled)else it})},enabled=!saving)}
        Row{StakButton("Configure",{editor=entry},variant=StakButtonVariant.Ghost,compact=true);StakButton("Remove",{remove=entry},variant=StakButtonVariant.Ghost,compact=true)}
    }}
    StakButton("Add Torznab indexer",{editor=TorznabIndexer(name="")},Modifier.fillMaxWidth(),icon=Lucide.Plus,enabled=!saving&&loaded.isSuccess&&entries.size<12)
    error?.let{Text(it,fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)}
    editor?.let{initial->TorznabIndexerEditor(initial,{editor=null}){value->entries=withContext(Dispatchers.IO){store.save(entries.filterNot{it.id==value.id}+value);store.read()};editor=null;error=null}}
    remove?.let{item->GlassAlertDialog(onDismissRequest={remove=null},title={Text("Remove ${item.name}?")},text={Text("This indexer will no longer be searched. Your TorBox files stay in place.",fontFamily=HankenFont,color=StakText)},confirmButton={StakButton("Remove",{persist(entries.filterNot{it.id==item.id}){remove=null}},variant=StakButtonVariant.Destructive)},dismissButton={StakButton("Cancel",{remove=null})})}
}
@Composable internal fun TorznabIndexerEditor(initial:TorznabIndexer,dismiss:()->Unit,save:suspend (TorznabIndexer)->Unit){
    var value by remember(initial.id){mutableStateOf(initial)};var busy by remember{mutableStateOf(false)};var saving by remember{mutableStateOf(false)};var notice by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope()
    GlassAlertDialog(onDismissRequest={if(!busy)dismiss()},title={Text("Torznab indexer",fontFamily=DotoFont,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold,fontSize=18.sp,color=StakText)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        StakTextField(value.name,{value=value.copy(name=it);notice=null},"Indexer name")
        StakTextField(value.url,{value=value.copy(url=it);notice=null},"Torznab API URL",secret=true)
        StakTextField(value.apiKey,{value=value.copy(apiKey=it);notice=null},"API key (optional)",secret=true)
        Text("AIOStreams · Jackett · Prowlarr · compatible Torznab APIs",fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)
        if(value.name.contains("aio",true)||value.url.contains("aiostream",true)||value.url.contains("api/v1/torznab",true))Text("AIOStreams API key: configuration UUID:password",fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)
        StakButton(if(busy&&!saving)"Testing…"else "Test connection",{busy=true;notice=null;val captured=value;scope.launch{try{val caps=TorznabClient().test(normalizeTorznabIndexer(captured));notice=listOfNotNull(if(caps.movie.available)"Movies"else null,if(caps.tv.available)"Series"else null,if(caps.generic.available)"Title search"else null).joinToString(" · ").ifBlank{"No supported searches advertised."}}catch(cancelled:CancellationException){throw cancelled}catch(error:Exception){notice=if(error is NabFailure)error.message else "Could not read capabilities. Check the full API URL, API key and connection."}finally{busy=false}}},enabled=!busy&&value.name.isNotBlank()&&value.url.isNotBlank(),compact=true)
        notice?.let{Text(it,fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)}
    }},confirmButton={StakButton(if(saving)"Saving…"else "Save indexer",{
        val normalized=try{normalizeTorznabIndexer(value)}catch(_:Exception){notice="Enter an indexer name and a full HTTP(S) Torznab API URL.";null}
        if(normalized!=null){busy=true;saving=true;notice=null;scope.launch{try{save(normalized)}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){notice="Could not save this indexer. Your entries are still here; please try again."}finally{saving=false;busy=false}}}
    },enabled=!busy&&value.name.isNotBlank()&&value.url.isNotBlank())},dismissButton={StakButton("Cancel",dismiss,enabled=!busy)})
}
