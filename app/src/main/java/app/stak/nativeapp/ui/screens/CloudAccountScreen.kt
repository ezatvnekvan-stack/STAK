package app.stak.nativeapp.ui.screens

import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

private fun savedLabel(stamp:Long):String=if(stamp<=0)"Waiting for first save"else "Saved "+java.time.Instant.ofEpochMilli(stamp).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MMM d · HH:mm",java.util.Locale.ENGLISH))

@Composable internal fun CloudAccountEntry(onOpen:()->Unit,controller:CloudAccountController=CloudAccountController.get(LocalContext.current)){
    val state by controller.state.collectAsState()
    val shape=RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().nothingSignal(state.savedAt).clip(shape).background(Color.White.copy(alpha=.025f)).border(1.dp,StakBorderSoft,shape).clickable(onClick=onOpen).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
            Text(state.username?:"STAK account",fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=15.sp,color=StakText)
            Text(if(state.username==null)"Sign in"else if(state.busy)"Saving…"else savedLabel(state.savedAt),fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)
        }
        if(state.username!=null)Box(Modifier.size(5.dp).background(if(state.savedAt>0&&state.message==null)Color(0xff10b981)else StakAccent,RoundedCornerShape(5.dp)))
        Icon(Lucide.ChevronRight,null,tint=StakTextMuted,modifier=Modifier.size(18.dp))
    }
}

@Composable fun CloudAccountScreen(onBack:()->Unit,controller:CloudAccountController=CloudAccountController.get(LocalContext.current)){
    val state by controller.state.collectAsState()
    var create by remember{mutableStateOf(false)};var username by remember{mutableStateOf("")};var password by remember{mutableStateOf("")};var forceForm by remember{mutableStateOf(false)}
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var retrying by remember{mutableStateOf(false)}
    val restored={
        generateSequence(context){(it as? ContextWrapper)?.baseContext?.takeUnless{base->base===it}}.filterIsInstance<Activity>().firstOrNull()?.let{activity->
            (activity as? androidx.activity.ComponentActivity)?.viewModelStore?.clear()
            activity.intent.putExtra("stak_cloud_restored",true)
            activity.recreate()
        };Unit
    }
    BackHandler(onBack=onBack)
    LaunchedEffect(state.username,state.busy){if(state.username!=null&&!state.busy&&state.message==null){password="";forceForm=false}}
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(start=16.dp,end=16.dp,top=22.dp,bottom=120.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){StakIconButton(onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back to settings",tint=StakText)};StakPageHeader("STAK account")}
        Spacer(Modifier.height(12.dp))
        if(state.username!=null&&!forceForm){
            Text(state.username!!,fontFamily=DotoFont,fontWeight=FontWeight.Black,fontSize=26.sp,color=StakText)
            Text(savedLabel(state.savedAt),fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
            if(state.busy)StakLoadingProgress()
            state.message?.let{message->
                Text(message,fontFamily=HankenFont,fontSize=13.sp,lineHeight=20.sp,color=StakTextSoft)
                StakButton(if(retrying)"Retrying…"else "Retry backup",{scope.launch{retrying=true;try{controller.syncNow()}finally{retrying=false}}},enabled=!state.busy&&!retrying)
                StakButton("Sign in again",{username=state.username.orEmpty();forceForm=true},variant=StakButtonVariant.Ghost,enabled=!state.busy)
            }
            StakButton("Sign out",controller::signOut,variant=StakButtonVariant.Ghost,enabled=!state.busy)
        }else{
            StakTabs(listOf("signin" to "Sign in","create" to "Create account"),if(create)"create"else "signin",{if(!state.busy)create=it=="create"})
            StakTextField(username,{username=it},"Username",enabled=!state.busy)
            StakTextField(password,{password=it},"Password",secret=true,keyboardType=androidx.compose.ui.text.input.KeyboardType.Password,enabled=!state.busy,onSubmit={if(state.configured&&!state.busy&&username.isNotBlank()&&password.isNotBlank())controller.signIn(username,password,create,restored)})
            StakButton(if(state.busy)"Please wait…"else if(create)"Create account"else "Sign in",{controller.signIn(username,password,create,restored)},Modifier.fillMaxWidth().testTag("cloud-submit"),variant=StakButtonVariant.Primary,enabled=state.configured&&!state.busy&&username.isNotBlank()&&password.isNotBlank())
            if(state.busy)StakLoadingProgress()
            if(!state.configured)Text("Account service is not set up yet.",fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
            state.message?.let{Text(it,fontFamily=HankenFont,fontSize=13.sp,lineHeight=19.sp,color=StakTextSoft)}
        }
    }
}
