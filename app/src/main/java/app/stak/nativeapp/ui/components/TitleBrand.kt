package app.stak.nativeapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.stak.nativeapp.data.MediaItem
import app.stak.nativeapp.ui.theme.*
import coil.compose.AsyncImage

@Composable fun TitleBrand(item:MediaItem,modifier:Modifier=Modifier,height:Dp=56.dp){
    var failed by remember(item.logo){mutableStateOf(false)}
    Box(modifier.height(height),contentAlignment=Alignment.BottomStart){
    if(item.logo!=null&&!failed)AsyncImage(item.logo,item.name,contentScale=ContentScale.Fit,alignment=Alignment.BottomStart,onError={failed=true},modifier=Modifier.fillMaxSize().testTag("title-logo-${item.mediaKey}"))
    else Text(item.name,Modifier,fontFamily=HankenFont,fontWeight=FontWeight.Bold,fontSize=23.sp,lineHeight=28.sp,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}
