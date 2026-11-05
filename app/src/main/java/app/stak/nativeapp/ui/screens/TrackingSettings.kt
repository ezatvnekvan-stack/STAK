package app.stak.nativeapp.ui.screens
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Central place for everything stak-watch related:
 *  - watcher pairing (URL + token, test, save, change)
 *  - the list of all tracked series with their watcher state
 *  - pause / resume / turn off per series
 *
 * Opened from Settings -> "Auto-add".
 */
@Composable fun TrackingSettings(onBack:()->Unit,onOpenOverview:()->Unit={}) {
    val revision by WatchRules.revision.collectAsState()
    val issue by WatchRules.issue.collectAsState()
    val scope=rememberCoroutineScope()
    val connection=remember(revision){WatchRules.store.read().connection}
    val paired=remember(revision){connection.enabled&&connection.url.isNotBlank()&&connection.token.isNotBlank()}
    val rules=remember(revision){WatchRules.store.read().rules}
    val statuses=remember(revision){WatchRules.store.read().statuses.associateBy{it.ruleId}}

    var url by remember{mutableStateOf(connection.url)}
    var token by remember{mutableStateOf("")}
    var editingPairing by remember(revision){mutableStateOf(!paired)}
    var pairBusy by remember{mutableStateOf(false)}
    var pairMessage by remember{mutableStateOf<String?>(null)}
    var confirmOffKey by remember{mutableStateOf<String?>(null)}
    var refreshBusy by remember{mutableStateOf(false)}
    var libraryOpen by remember{mutableStateOf(false)}

    LaunchedEffect(Unit){if(paired)WatchRules.refresh()}

    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,12.dp,16.dp,120.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=onBack){Icon(Lucide.ChevronRight,"Back",tint=StakText,modifier=Modifier.size(20.dp).graphicsLayer{rotationZ=180f})}
                StakPageHeader("STAK Server",modifier=Modifier.weight(1f))
                IconButton(onClick={scope.launch{refreshBusy=true;try{WatchRules.refresh()}catch(_:Exception){};refreshBusy=false}},enabled=!refreshBusy){
                    Icon(Lucide.Refresh,"Refresh tracking status",tint=StakTextMuted,modifier=Modifier.size(20.dp))
                }
            }
            Text("One optional STAK Server for Auto-add and library organization.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
        }

        item {
            Text("Library organization",fontFamily=HankenFont,fontSize=14.sp,color=StakText)
            Text("Approved movies and series, organized for Jellyfin. Unified servers connect both features when paired below. Legacy connections remain supported.",fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted)
            StakButton("Manage library",{libraryOpen=true},compact=true)
        }
        // --- watcher pairing -------------------------------------------
        item {
            AutoSectionLabel(if(paired&&!editingPairing)"STAK Server" else "Connect STAK Server")
            if(paired&&!editingPairing) {
                Text(connection.url+" · token saved",fontFamily=SpaceMonoFont,fontSize=10.sp,lineHeight=14.sp,color=StakTextMuted)
                Spacer(Modifier.height(6.dp))
                StakButton("Change connection",{editingPairing=true;pairMessage=null;url=connection.url;token=""},compact=true)
            } else {
                Text(if(paired)"Enter the server address and its access token."
                    else "Self-hosted STAK Server on Linux handles Auto-add and library organization. Cached-only releases are used.",
                    fontFamily=HankenFont,fontSize=12.sp,lineHeight=17.sp,color=StakTextMuted)
                Spacer(Modifier.height(6.dp))
                StakTextField(url,{url=it},"http://192.168.0.5:8055",height=44)
                StakTextField(token,{token=it},"STAK server access token",secret=true,keyboardType=androidx.compose.ui.text.input.KeyboardType.Password,height=44)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(top=6.dp)) {
                    StakButton("Test connection",{
                        scope.launch {
                            pairBusy=true
                            pairMessage=try {
                                WatchRules.probe(WatcherConnection(url,token.ifBlank{connection.token}))
                                "STAK Server reachable. Save to finish connecting."
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                watchRuleError(error)
                            }
                            pairBusy=false
                        }
                    },enabled=!pairBusy,compact=true)
                    StakButton("Save",{
                        scope.launch {
                            if(token.isBlank()){pairMessage="Paste the STAK_WATCHER_TOKEN value.";return@launch}
                            pairBusy=true
                            try {
                                WatchRules.probe(WatcherConnection(url,token))
                                WatchRules.saveConnection(url,token)
                                val unified=LibraryApprovals.pairUnifiedWatcher(WatchRules.document().connection)
                                pairMessage=if(unified)"STAK Server paired for Auto-add and library organization." else "Connection and token verified."
                                editingPairing=false
                            }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (error: Exception) { pairMessage=watchRuleError(error) }
                            finally{pairBusy=false}
                        }
                    },enabled=!pairBusy,compact=true)
                }
                if(paired)Row(modifier=Modifier.padding(top=6.dp)){
                    StakButton("Keep current",{editingPairing=false;url=connection.url;token="";pairMessage=null},
                        compact=true,variant=StakButtonVariant.Ghost)
                }
                pairMessage?.let{Text(it,fontFamily=HankenFont,fontSize=12.sp,lineHeight=16.sp,color=StakTextMuted,modifier=Modifier.padding(top=4.dp))}
            }
            issue?.let{Text(it,fontFamily=HankenFont,fontSize=12.sp,lineHeight=17.sp,color=Color(0xFFFFB86B),modifier=Modifier.padding(top=6.dp))}
        }

        item {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                AutoSectionLabel("Your series")
                Text("Manage episode states, quality and automatic additions in Library → Auto-add.",fontFamily=HankenFont,fontSize=13.sp,lineHeight=20.sp,color=StakTextMuted)
                StakButton("Open Auto-add",onOpenOverview,compact=true,icon=Lucide.Radio)
                Text("Quality defaults",fontFamily=HankenFont,fontSize=14.sp,color=StakText)
                Text(if(WatchRules.store.read().qualityDefault==null)"Choose quality in a series and select Use quality as default. New series will use those settings." else "Your quality defaults will be offered for new series. Existing series keep their own settings.",fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted)
            }
        }
    }
    if(libraryOpen)LibraryAccessDialog{libraryOpen=false}
}

@Composable private fun AutoSectionLabel(label:String)=Text(label,fontFamily=SpaceMonoFont,fontSize=10.sp,lineHeight=14.sp,color=StakTextMuted)
