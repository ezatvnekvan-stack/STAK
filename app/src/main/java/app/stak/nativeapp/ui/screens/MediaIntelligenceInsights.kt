package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.StakProgress
import app.stak.nativeapp.ui.theme.*
import java.time.LocalDate

@Composable fun MediaIntelligenceInsights(entries:List<JournalEntry>,library:List<MediaItem>,account:String,today:LocalDate){
    val store=remember{IntelligenceStore.get()}
    val data by remember(store){store.flow}.collectAsState(initial=IntelligenceData())
    val months=remember(entries,today){monthActivity(entries,today)}
    val quality=remember(library){libraryQuality(library)};val growth=remember(library,today){libraryGrowth(library,today)}
    val genres=remember(entries){entries.filter{it.played}.flatMap{it.media.genres.orEmpty()}.groupingBy{it}.eachCount().entries.sortedByDescending{it.value}.take(5).map{InsightBucket(it.key,it.value)}}
    val series=remember(entries){entries.filter{it.played&&it.isEpisode}.groupBy{it.media.jellyfinSeriesId ?: it.media.imdbId ?: it.media.mediaKey}.values.sortedByDescending{it.size}.take(5).map{InsightBucket(it.first().media.name,it.size)}}
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
        InsightBars("Monthly activity",months,"Dated watched markers in the selected period; repeat plays may be absent.")
        if(genres.isNotEmpty())InsightBars("Genre distribution",genres,"Known genres in the selected watched records; titles may have multiple genres.")
        if(series.isNotEmpty())InsightBars("Most watched series",series,"Distinct watched episode records in the selected period.")
        if(library.isNotEmpty()){
            InsightBars("Library additions",growth,"Loaded library · Jellyfin DateCreated, not observed historical growth.")
            if(quality.isNotEmpty())InsightBars("Movie quality",quality,"Loaded movie items · scanned video height; unknown metadata stays unknown.")
            InsightNote("${completedAvailableSeries(library)} series with all available episodes watched","Jellyfin user counts for the loaded library. This does not imply every produced episode is available.")
        }
        val events=data.events.filter{it.scope==account}
        InsightNote("${events.size} local observations",if(data.recording)"Future events recorded on this device; repeated snapshots are deduplicated. Position changes are not measured watch time."else "Observation recording is paused. Stored events remain on this device.")
    }
}
@Composable private fun InsightNote(title:String,note:String){Column(Modifier.fillMaxWidth().background(Color(0x50111418),RoundedCornerShape(14.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,fontFamily=HankenFont,fontSize=14.sp,color=StakText);Text(note,fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)}}
@Composable private fun InsightBars(title:String,buckets:List<InsightBucket>,source:String){
    val peak=(buckets.maxOfOrNull{it.count} ?: 1).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().background(Color(0x50111418),RoundedCornerShape(14.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(title,fontFamily=HankenFont,fontSize=15.sp,color=StakText)
        buckets.forEach{row->Column(verticalArrangement=Arrangement.spacedBy(5.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(row.label,Modifier.weight(1f),fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted);Text(row.count.toString(),fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakText)};StakProgress(row.count.toFloat()/peak)}}
        Text(source,fontFamily=HankenFont,fontSize=10.sp,lineHeight=15.sp,color=StakTextMuted)
    }
}
