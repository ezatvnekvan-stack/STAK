package app.stak.nativeapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

@Composable fun SourcesSettings(onBack:()->Unit,settings:SettingsStore=NativeRuntime.settings){
    val store=remember(settings){StreamAddonStore(settings)}
    var addons by remember{mutableStateOf(store.all())}
    var editor by remember{mutableStateOf<StreamAddon?>(null)}
    var torznabCandidate by remember{mutableStateOf<TorznabIndexer?>(null)}
    var name by remember{mutableStateOf("")}
    var url by remember{mutableStateOf("")}
    var error by remember{mutableStateOf<String?>(null)}
    var remove by remember{mutableStateOf<StreamAddon?>(null)}
    fun save(next:List<StreamAddon>){try{store.save(next);addons=store.all();error=null}catch(_:Exception){error="Could not save. Use a full HTTPS manifest.json link and a source name."}}
    fun edit(item:StreamAddon){editor=item;name=item.name;url=item.manifestUrl;error=null}
    BackHandler{if(editor!=null){editor=null;error=null}else onBack()}
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(start=16.dp,end=16.dp,top=18.dp,bottom=120.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){StakIconButton({if(editor!=null){editor=null;error=null}else onBack()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back",tint=StakText,modifier=Modifier.size(20.dp))};StakPageHeader(if(editor==null)"Search sources" else "Connect source")}
        if(editor==null){
            Text("These sources are used by Find Sources. Auto-add uses the sources configured on STAK Server.",fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted)
            Text("${addons.count{it.enabled}} addons enabled",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
            addons.forEach{addon->
                Column(Modifier.fillMaxWidth().background(Color(0x600A0C10),RoundedCornerShape(16.dp)).border(1.dp,Color(0x24FFFFFF),RoundedCornerShape(16.dp)).padding(16.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){Text(addon.name,fontFamily=HankenFont,fontWeight=FontWeight.Bold,fontSize=18.sp,color=StakText);Text(if(addon.enabled)"Used in Find sources" else "Disabled",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)}
                        Switch(addon.enabled,{checked->save(addons.map{if(it.id==addon.id)it.copy(enabled=checked)else it})},modifier=Modifier.semantics{contentDescription="Enable ${addon.name}"})
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        StakButton("Configure",{edit(addon)},variant=StakButtonVariant.Ghost,compact=true)
                        StakButton("Remove",{remove=addon},variant=StakButtonVariant.Ghost,compact=true)
                    }
                }
            }
            if(addons.none{it.enabled})Text("No Stremio addons enabled. You can use a Torznab indexer below.",fontFamily=HankenFont,fontSize=13.sp,lineHeight=20.sp,color=StakTextMuted)
            Spacer(Modifier.height(6.dp))
            if(addons.none{it.id=="torrentio"})StakButton("Add Torrentio",{edit(StreamAddon("torrentio","Torrentio","https://torrentio.strem.fun/manifest.json"))},Modifier.fillMaxWidth(),icon=Lucide.Plus)
            StakButton("Add AIOStreams",{edit(StreamAddon(name="AIOStreams"))},Modifier.fillMaxWidth(),icon=Lucide.Plus,enabled=addons.size<12)
            StakButton("Add another addon",{edit(StreamAddon(name=""))},Modifier.fillMaxWidth(),variant=StakButtonVariant.Ghost,enabled=addons.size<12)
            TorznabSourcesSettings(settings,torznabCandidate,{torznabCandidate=null})
        }else{
            Text(if(editor!!.name=="AIOStreams")"Paste your configured AIOStreams manifest link." else "Use the addon's Stremio manifest link.",fontFamily=HankenFont,fontSize=15.sp,lineHeight=22.sp,color=StakTextMuted)
            StakTextField(name,{name=it;error=null},"Source name",modifier=Modifier.semantics{contentDescription="Source name"})
            StakTextField(url,{url=it;error=null},"https://…/manifest.json",secret=true,modifier=Modifier.semantics{contentDescription="Source manifest URL"})
            Text("Configuration links can contain private tokens. STAK stores them encrypted on this device. To add releases to TorBox, enable torrent results in your addon; playback-only links cannot be imported.",fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted)
            if(url.contains("torznab",true))StakButton("Connect as Torznab",{torznabCandidate=TorznabIndexer(name=name.ifBlank{"AIOStreams"},url=url);editor=null},variant=StakButtonVariant.Ghost)
            StakButton("Save source",{
                try{
                    val item=editor!!.copy(name=name.trim(),manifestUrl=normalizeAddonManifest(url))
                    require(item.name.isNotBlank()&&item.name.length<=60)
                    val next=addons.filterNot{it.id==item.id}+item
                    store.save(next);addons=store.all();editor=null;error=null
                }catch(_:Exception){error="Use a manifest.json link for addons, or Add Torznab indexer for an indexer API URL."}
            },Modifier.fillMaxWidth(),variant=StakButtonVariant.Primary,enabled=name.isNotBlank()&&url.isNotBlank())
            StakButton("Cancel",{editor=null;error=null},Modifier.fillMaxWidth(),variant=StakButtonVariant.Ghost)
        }
        error?.let{Text(it,fontFamily=HankenFont,fontSize=13.sp,lineHeight=20.sp,color=Color(0xFFFF9298))}
    }
    remove?.let{item->GlassAlertDialog(onDismissRequest={remove=null},title={Text("Remove ${item.name}?")},text={Text("This source will no longer be searched. Your TorBox files stay in place.")},confirmButton={StakButton("Remove",{save(addons.filterNot{it.id==item.id});remove=null},variant=StakButtonVariant.Destructive)},dismissButton={StakButton("Cancel",{remove=null})})}
}
