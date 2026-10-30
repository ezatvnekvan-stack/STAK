package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.*
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.*

@Composable internal fun TraktSettingsCard(settings:SettingsStore=NativeRuntime.settings){
    var draft by remember{mutableStateOf("")};var saved by remember{mutableStateOf(!settings.traktClientId.isNullOrBlank())};var busy by remember{mutableStateOf(false)};var notice by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope();val uri=LocalUriHandler.current
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("TRAKT",fontFamily=DotoFont,fontSize=16.sp,color=StakText)
        Text(if(saved)"Client ID saved"else "Trending · Most anticipated",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
        StakTextField(draft,{draft=it;notice=null},"Trakt Client ID",secret=true,enabled=!busy)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            StakButton(if(busy)"Verifying…"else "Save & verify",{
                val value=draft.trim();busy=true;notice=null
                scope.launch{try{withContext(Dispatchers.IO){TraktCatalog({value},artwork={it}).list("movie","trending",limit=1);settings.saveTraktClientIdChecked(value)};draft="";saved=true;notice="Trakt verified and saved."}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){notice="Could not verify Trakt. Check the Client ID and connection; your previous setting was kept."}finally{busy=false}}
            },enabled=!busy&&draft.isNotBlank(),compact=true)
            if(saved)StakButton("Remove",{settings.traktClientId=null;saved=false;notice=null},enabled=!busy,compact=true,variant=StakButtonVariant.Ghost)
        }
        StakButton("Get Client ID ↗",{uri.openUri("https://trakt.tv/oauth/applications")},compact=true,variant=StakButtonVariant.Ghost)
        notice?.let{Text(it,fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)}
    }
}
