package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import app.stak.nativeapp.ui.theme.*

@Composable internal fun WatchlistSummary(movies:Int,series:Int){
    Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically){
        WatchlistCount(movies,"Movies",Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(20.dp).background(StakBorder))
        WatchlistCount(series,"Series",Modifier.weight(1f).padding(start=20.dp))
    }
}
@Composable private fun WatchlistCount(count:Int,label:String,modifier:Modifier){
    Row(modifier.semantics(mergeDescendants=true){contentDescription="$label count"},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
        Text(count.toString(),fontFamily=DotoFont,fontWeight=FontWeight.Black,fontSize=27.sp,lineHeight=33.sp,color=StakText)
        Text(label.uppercase(java.util.Locale.ENGLISH),fontFamily=SpaceMonoFont,fontWeight=FontWeight.Normal,fontSize=9.sp,lineHeight=13.sp,letterSpacing=1.sp,color=StakTextMuted)
    }
}
