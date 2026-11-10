package app.stak.nativeapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.ui.theme.*

/** App-drawn monochrome format marks, not downloaded logo images. */
@Composable fun FormatBadge(label:String,modifier:Modifier=Modifier){
    val dolby=label=="Dolby Vision"||label=="Atmos"
    Row(modifier.heightIn(min=25.dp).background(Color(0xFF202020),RoundedCornerShape(5.dp)).border(1.dp,Color(0xFF424242),RoundedCornerShape(5.dp)).padding(horizontal=7.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
        if(dolby)Canvas(Modifier.width(11.dp).height(8.dp)){
            drawArc(StakTextSoft,-90f,180f,true,Offset.Zero,Size(size.width*.46f,size.height))
            drawArc(StakTextSoft,90f,180f,true,Offset(size.width*.54f,0f),Size(size.width*.46f,size.height))
        }
        Text(label.uppercase(),fontFamily=DotoFont,fontWeight=FontWeight.Bold,fontSize=10.sp,lineHeight=13.sp,color=StakTextSoft)
    }
}
