package app.stak.nativeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.ui.theme.*
import java.util.Locale

/** Monochrome native wordmark, deliberately separate from the rating digits. */
@Composable private fun ImdbMark(){
    Box(Modifier.border(1.dp,StakText.copy(alpha=.72f),RoundedCornerShape(4.dp)).padding(horizontal=4.dp,vertical=1.dp),contentAlignment=Alignment.Center){
        Text("IMDb",fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=10.sp,lineHeight=14.sp,letterSpacing=(-.55).sp,color=StakText,maxLines=1)
    }
}

@Composable fun RatingValue(source:String,value:String,modifier:Modifier=Modifier){
    if(source!="IMDb")return
    Row(modifier.semantics(mergeDescendants=true){contentDescription="IMDb $value"}.testTag("imdb-rating"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
        ImdbMark()
        Box(Modifier.size(3.dp).background(Color(0xFFD71921),CircleShape).testTag("imdb-accent-dot"))
        Text(value,fontFamily=SpaceMonoFont,fontWeight=FontWeight.Normal,fontSize=14.sp,lineHeight=18.sp,color=StakText,maxLines=1)
    }
}

@Composable fun RatingBadge(value:Double?,modifier:Modifier=Modifier,source:String="IMDb"){
    if(source!="IMDb"||value==null||!value.isFinite()||value<=0||value>10)return
    RatingValue("IMDb",String.format(Locale.US,"%.1f",value),modifier)
}

/** Legacy signature retained; IMDb remains the only displayed rating. */
@Composable fun RatingStrip(imdb:Double?,rt:String?,mc:String?){RatingBadge(imdb)}
