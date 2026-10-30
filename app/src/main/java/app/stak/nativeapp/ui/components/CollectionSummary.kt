package app.stak.nativeapp.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import app.stak.nativeapp.ui.theme.*

@Composable fun CollectionSummary(values:List<Pair<String,String>>,modifier:Modifier=Modifier){
    Row(modifier.fillMaxWidth().padding(vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(28.dp)){
        values.forEach{(value,label)->Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
            Text(value,Modifier.animateContentSize(if(LocalReducedMotion.current)snap() else spring()),fontFamily=HankenFont,fontSize=19.sp,fontWeight=FontWeight.SemiBold,color=StakText)
            Text(label,fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
        }}
    }
}
