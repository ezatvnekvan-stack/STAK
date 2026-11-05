package app.stak.nativeapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import app.stak.nativeapp.R
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

@Composable fun SetupScreen(settings:SettingsStore,controller:ConnectionSettingsController,onFinish:()->Unit){
    var accountOpen by remember{mutableStateOf(false)}
    if(accountOpen){CloudAccountScreen(onBack={accountOpen=false});return}
    var step by rememberSaveable{mutableIntStateOf(0)}
    var section by rememberSaveable{mutableStateOf<String?>(null)}
    val connections by controller.state.collectAsState()
    val reduced=LocalReducedMotion.current
    BackHandler(step>0||section!=null){if(section!=null)section=null else step--}
    Box(Modifier.fillMaxSize().background(StakBackground)){
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x302B485D),Color.Transparent),radius=1250f)))
        if(section!=null){
            Box(Modifier.fillMaxSize().statusBarsPadding()){
                if(section=="sources")SourcesSettings(onBack={section=null},settings=settings)
                else ProviderSettings(section!!,controller,onBack={section=null},backLabel="Back to setup")
            }
        }else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal=24.dp)){
            Row(Modifier.fillMaxWidth().height(64.dp),verticalAlignment=Alignment.CenterVertically){
                Text("STAK",fontFamily=DotoFont,fontWeight=FontWeight.Black,fontSize=22.sp,color=StakText)
                Spacer(Modifier.weight(1f))
                if(step<3)StakButton("Set up later",onFinish,variant=StakButtonVariant.Ghost,compact=true)
            }
            Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                repeat(4){i->
                    val color by animateColorAsState(if(i<=step)Color.White else Color(0x24FFFFFF),if(reduced)snap() else tween(240),label="setup-progress")
                    Box(Modifier.weight(1f).height(2.dp).background(color,RoundedCornerShape(2.dp)))
                }
            }
            AnimatedContent(step,modifier=Modifier.weight(1f),transitionSpec={
                (fadeIn(tween(if(reduced)0 else 260))+slideInHorizontally(tween(if(reduced)0 else 260)){it/15}) togetherWith fadeOut(tween(if(reduced)0 else 140))
            },label="setup-step"){page->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top=24.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                    Text("0${page+1} / 04",fontFamily=SpaceMonoFont,fontSize=10.sp,letterSpacing=2.sp,color=StakTextMuted)
                    Text(listOf("Your cinema.\nConnected.","Bring your\nlibrary.","Choose your\nsources.","Make it\nyours.")[page],fontFamily=HankenFont,fontWeight=FontWeight.Bold,fontSize=38.sp,lineHeight=42.sp,letterSpacing=(-1.3).sp,color=StakText)
                    Spacer(Modifier.height(4.dp))
                    when(page){
                        0->{
                            StakButton("Sign in",{accountOpen=true},variant=StakButtonVariant.Ghost)
                            StakLottie(StakAnimation.Welcome,Modifier.padding(vertical=20.dp).size(96.dp))
                            SetupCopy("Discover a film. Find the best source. Keep your Jellyfin library and viewing history together.")
                            SetupCopy("Connect only what you use. You can change everything later in Settings.")
                        }
                        1->{
                            ServiceRow("Jellyfin","Library, Continue Watching and watched state",Lucide.MonitorPlay,if(connections.jellyfinConnected)"Ready" else "Optional"){section="jellyfin"}
                            ServiceRow("TorBox","Add releases and manage your files",Lucide.Database,if(connections.torboxSaved)"Saved" else "Optional"){section="account"}
                            ServiceRow("Metadata & ratings","TMDb discovery · IMDb ratings",Lucide.Search,if(connections.tmdbSaved)"Saved" else "Optional"){section="metadata"}
                            SetupCopy("Already connected? Your existing credentials and library stay in place.")
                        }
                        2->{
                            val addons=remember(section){StreamAddonStore(settings).all()}
                            ServiceRow("Search sources",addons.filter{it.enabled}.joinToString(" · "){it.name}.ifBlank{"No sources enabled"},Lucide.Radio){section="sources"}
                            SetupCopy("Use Torrentio, connect your AIOStreams configuration, or use both. STAK searches your enabled addons from Find sources.")
                            SetupCopy("AIOStreams needs its configured Stremio manifest link. You can paste it in the next screen.")
                        }
                        3->{
                            var ambient by remember{mutableStateOf(settings.ambientEnabled)}
                            var motion by remember{mutableStateOf(settings.reducedMotion)}
                            SetupOption("Cinematic atmosphere","Soft artwork-inspired lighting",ambient){ambient=it;settings.ambientEnabled=it}
                            SetupOption("Reduced motion","Keep transitions quiet",motion){motion=it;settings.reducedMotion=it}
                            Spacer(Modifier.height(12.dp))
                            SetupCopy("You're ready. Explore titles, save your next watch and keep your library close.")
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                if(step>0)StakButton("Back",{step--},modifier=Modifier.height(50.dp))
                StakButton(if(step==3)"Open STAK" else if(step==0)"Get started" else "Continue",{if(step==3)onFinish() else step++},modifier=Modifier.weight(1f).height(50.dp),variant=StakButtonVariant.Primary,icon=Lucide.ChevronRight)
            }
        }
    }
}
@Composable private fun SetupCopy(text:String){Text(text,fontFamily=HankenFont,fontSize=15.sp,lineHeight=23.sp,color=StakTextMuted)}
@Composable private fun SetupOption(title:String,detail:String,checked:Boolean,onChange:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,fontFamily=HankenFont,fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=StakText);Text(detail,fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)};Switch(checked,onChange)}
}
