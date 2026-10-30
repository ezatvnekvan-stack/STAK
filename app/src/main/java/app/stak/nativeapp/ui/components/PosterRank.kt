package app.stak.nativeapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import app.stak.nativeapp.ui.theme.DotoFont

@Composable internal fun PosterRank(rank:Int,modifier:Modifier=Modifier){
    Text(rank.toString(),modifier.heightIn(min=52.dp).padding(horizontal=2.dp).semantics{contentDescription="Rank $rank"},
        fontFamily=DotoFont,fontWeight=FontWeight.Black,fontSize=42.sp,lineHeight=50.sp,color=Color(0xFFF2F1ED),maxLines=1,softWrap=false)
}
