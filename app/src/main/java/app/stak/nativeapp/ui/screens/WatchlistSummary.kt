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
    val text="$movies ${if(movies==1)"movie"else "movies"} · $series series"
    Text(text,Modifier.semantics{contentDescription="Watchlist totals: $text"}.then(Modifier),
        fontFamily=SpaceMonoFont,fontSize=11.sp,lineHeight=17.sp,color=StakTextMuted)
}
