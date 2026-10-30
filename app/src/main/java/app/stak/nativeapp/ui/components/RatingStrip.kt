package app.stak.nativeapp.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.ui.theme.DotoFont
import java.util.Locale

/** Dot cells keep the reference logos crisp at every screen density. */
@Composable private fun RatingLogo(source:String) {
    val imdb=source=="IMDb"
    val rows=when(source) {
        "RT" -> listOf("0000001100000","0000011110000","0001111111100","0011111111110","0111111111111","1111111111111","1111111111111","0111111111110","0011111111100","0000111110000")
        "MC" -> listOf("1111111111111","1111111111111","1100000000011","1101101101011","1101111111011","1101010101011","1101010101011","1101010101011","1100000000011","1111111111111")
        else -> listOf("00111111111111111111111100","01111111111111111111111110","11000000000000000000000011","11011101000101100111000011","11001001101101010110100011","11001001010101010111000011","11001001000101010110100011","11011101000101100111000011","11000000000000000000000011","01111111111111111111111110","00111111111111111111111100")
    }
    Canvas(Modifier.width(if(imdb)30.dp else 17.dp).height(18.dp)) {
        val cell=minOf(size.width/rows.maxOf{it.length},size.height/rows.size)
        val x=(size.width-rows.maxOf{it.length}*cell)/2;val y=(size.height-rows.size*cell)/2
        rows.forEachIndexed { j,row -> row.forEachIndexed { i,c -> if(c=='1') drawCircle(Color.White,cell*.37f,Offset(x+(i+.5f)*cell,y+(j+.5f)*cell)) } }
    }
}

@Composable fun RatingValue(source:String,value:String,modifier:Modifier=Modifier) {
    Row(modifier.semantics(mergeDescendants=true){contentDescription="$source $value"},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        RatingLogo(source)
        Text(value,fontFamily=DotoFont,fontWeight=FontWeight.Black,fontSize=15.sp,lineHeight=20.sp,color=Color.White,maxLines=1)
    }
}

@Composable fun RatingBadge(value:Double?,modifier:Modifier=Modifier,source:String="IMDb") {
    if(source!="IMDb"||value==null||!value.isFinite()||value<=0||value>10)return
    RatingValue("IMDb",String.format(Locale.US,"%.1f",value),modifier)
}

@Composable fun RatingStrip(imdb:Double?,rt:String?,mc:String?) {
    val tomato=rt?.removeSuffix("%")?.toDoubleOrNull()?.takeIf{it>0&&it<=100}
    val meta=mc?.toDoubleOrNull()?.takeIf{it>0&&it<=100}
    if(imdb==null&&tomato==null&&meta==null)return
    Row(Modifier.padding(vertical=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        val chip=Modifier.background(Color(0x24000000),RoundedCornerShape(7.dp)).border(1.dp,Color(0x12FFFFFF),RoundedCornerShape(7.dp)).padding(horizontal=7.dp,vertical=4.dp)
        RatingBadge(imdb,chip)
        if(tomato!=null)RatingValue("RT","${tomato.toInt()}%",chip)
        if(meta!=null)RatingValue("MC",meta.toInt().toString(),chip)
    }
}
