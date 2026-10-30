package app.stak.nativeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import app.stak.nativeapp.ui.theme.*

/** One shared hierarchy for setup, connections and settings; no opaque button trays. */
@Composable fun ServiceRow(title:String,detail:String,icon:ImageVector,status:String?=null,onClick:()->Unit){
    val press=rememberPressMotion()
    Row(Modifier.fillMaxWidth().pressMotion(press).background(Color(0x600A0C10),RoundedCornerShape(16.dp))
        .border(1.dp,Color(0x20FFFFFF),RoundedCornerShape(16.dp))
        .clickable(interactionSource=press.source,indication=androidx.compose.foundation.LocalIndication.current,onClick=onClick)
        .padding(horizontal=16.dp,vertical=17.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
        Box(Modifier.size(38.dp),contentAlignment=Alignment.Center){Icon(icon,null,tint=StakText,modifier=Modifier.size(21.dp))}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(title,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=16.sp,color=StakText)
            Text(detail,fontFamily=HankenFont,fontSize=12.sp,lineHeight=17.sp,color=StakTextMuted)
        }
        status?.let{Text(it,fontFamily=SpaceMonoFont,fontSize=9.sp,color=if(it=="Ready"||it=="Saved")Color(0xFF9EDCC7) else StakTextMuted)}
        Icon(Lucide.ChevronRight,null,tint=StakTextMuted,modifier=Modifier.size(16.dp))
    }
}
