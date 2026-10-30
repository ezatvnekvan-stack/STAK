package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

@SuppressLint("SetJavaScriptEnabled")
@Composable fun TrailerDialog(title:String,url:String,onDismiss:()->Unit,loadContent:(WebView,String,String)->Unit={view,html,origin->view.loadDataWithBaseURL(origin,html,"text/html","UTF-8",null)}){
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    val id=remember(url){youtubeTrailerId(url)}
    var browser by remember{mutableStateOf<WebView?>(null)}
    var fullView by remember{mutableStateOf<View?>(null)}
    var fullCallback by remember{mutableStateOf<WebChromeClient.CustomViewCallback?>(null)}
    var error by remember(url){mutableStateOf<String?>(null)}
    var loading by remember(url){mutableStateOf(true)}
    fun hideFullscreen(){(fullView?.parent as? ViewGroup)?.removeView(fullView);fullView=null;val callback=fullCallback;fullCallback=null;callback?.onCustomViewHidden();browser?.visibility=View.VISIBLE}
    fun load(){error=null;loading=true;val video=id ?: return;browser?.let{loadContent(it,trailerEmbedHtml(video,context.packageName),"https://${context.packageName}")}}
    DisposableEffect(owner,browser){
        val target=browser
        val observer=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_PAUSE->{target?.evaluateJavascript("document.getElementById('player')?.contentWindow.postMessage(JSON.stringify({event:'command',func:'pauseVideo',args:[]}), 'https://www.youtube.com');",null);target?.onPause()};Lifecycle.Event.ON_RESUME->target?.onResume();else->Unit}}
        owner.lifecycle.addObserver(observer)
        onDispose{owner.lifecycle.removeObserver(observer);target?.stopLoading();target?.onPause();target?.loadUrl("about:blank");(target?.parent as? ViewGroup)?.removeView(target);target?.destroy()}
    }
    Dialog(onDismissRequest={if(fullView!=null)hideFullscreen() else onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=fullView==null,decorFitsSystemWindows=false)){
        SmoothDialogWindow()
        val dialogView=LocalView.current
        DisposableEffect(dialogView){
            val window=(dialogView.parent as? DialogWindowProvider)?.window
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window?.setDimAmount(.55f);window?.setWindowAnimations(0)
            onDispose{}
        }
        val expanded=fullView!=null
        val card=if(expanded)Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding() else Modifier.widthIn(max=720.dp).fillMaxWidth().heightIn(max=LocalConfiguration.current.screenHeightDp.dp*.9f)
        GlassSurface(card.premiumEntrance().testTag("trailer-popup")){
        Column(Modifier.fillMaxWidth().then(if(expanded)Modifier.fillMaxHeight() else Modifier)){
            Row(Modifier.fillMaxWidth().padding(start=16.dp,end=8.dp,top=12.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text("TRAILER",fontFamily=SpaceMonoFont,fontSize=9.sp,letterSpacing=1.2.sp,color=StakTextMuted)
                    Text(title,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=17.sp,lineHeight=21.sp,color=Color.White,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
                StakIconButton({load()},Modifier.padding(start=8.dp)){Icon(Lucide.Refresh,"Retry trailer",tint=if(id!=null)Color.White else StakTextMuted,modifier=Modifier.size(17.dp))}
                StakIconButton({hideFullscreen();onDismiss()},Modifier.padding(start=8.dp)){Icon(Lucide.X,"Close trailer",tint=Color.White,modifier=Modifier.size(18.dp))}
            }
            if(loading&&id!=null)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp),color=StakAccent,trackColor=Color(0x15FFFFFF))
            if(id==null)Text("No supported trailer is available for this title.",Modifier.padding(20.dp),color=Color.White)
            error?.let{Text(it,Modifier.padding(20.dp),color=StakTextMuted)}
            val player=if(expanded)Modifier.fillMaxWidth().weight(1f) else Modifier.padding(horizontal=12.dp).fillMaxWidth().aspectRatio(16f/9f)
            if(id!=null)AndroidView(modifier=player.clip(RoundedCornerShape(12.dp)).background(Color.Black).testTag("trailer-player"),factory={ctx->
                val host=FrameLayout(ctx)
                val web=WebView(ctx).apply{
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.mediaPlaybackRequiresUserGesture=false
                    settings.allowFileAccess=false;settings.allowContentAccess=false;settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webViewClient=object:WebViewClient(){
                        override fun onPageStarted(view:WebView,url:String?,favicon:android.graphics.Bitmap?){loading=true}
                        override fun onPageFinished(view:WebView,url:String?){loading=false}
                        override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean{val target=request.url;val hostName=target.host.orEmpty();return target.scheme!="https"||!(hostName==context.packageName||hostName=="youtube.com"||hostName.endsWith(".youtube.com")||hostName=="youtube-nocookie.com"||hostName.endsWith(".youtube-nocookie.com"))}
                        override fun onReceivedError(view:WebView,request:WebResourceRequest,failure:WebResourceError){if(request.isForMainFrame)error="The trailer could not load. Check your connection and tap Retry."}
                    }
                    webChromeClient=object:WebChromeClient(){
                        override fun onShowCustomView(view:View,callback:CustomViewCallback){if(fullView!=null){callback.onCustomViewHidden();return};fullView=view;fullCallback=callback;visibility=View.INVISIBLE;host.addView(view,FrameLayout.LayoutParams(-1,-1))}
                        override fun onHideCustomView(){hideFullscreen()}
                    }
                }
                browser=web;host.addView(web,FrameLayout.LayoutParams(-1,-1));loadContent(web,trailerEmbedHtml(id,context.packageName),"https://${context.packageName}");host
            })
            if(id!=null&&!expanded)Text("YouTube",Modifier.padding(horizontal=16.dp,vertical=12.dp),fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)
        }
        }
    }
}
