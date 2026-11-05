package app.stak.nativeapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import java.util.Locale

@Composable internal fun JournalSummary(insights:ViewingInsights){
    val runtime=when{
        insights.knownRuntimeTitles==0&&insights.movies+insights.episodes>0->"—"
        else->"%.1fh".format(Locale.ENGLISH,insights.knownHours)+(if(insights.runtimeIncomplete)"+" else "")
    }
    Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
        SummaryValue(insights.movies.toString(),"Movies",Modifier.weight(1f))
        SummaryValue(insights.episodes.toString(),"Episodes",Modifier.weight(1f))
        SummaryValue(runtime,"Estimated runtime",Modifier.weight(1.3f))
    }
}
@Composable internal fun SummaryValue(value:String,label:String,modifier:Modifier){
    Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)){
        NothingNumber(value,style=androidx.compose.ui.text.TextStyle(fontFamily=DotoFont,fontWeight=FontWeight.Black,fontSize=27.sp,color=StakText))
        Text(label,fontFamily=HankenFont,fontSize=12.sp,color=StakTextSoft)
    }
}
@Composable internal fun TrackingNote(title:String,body:String){Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
    Text(title,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,color=StakText)
    Text(body,fontFamily=HankenFont,fontSize=13.sp,color=StakTextSoft)
}}
@Composable internal fun TrackingSection(title:String){Text(title,fontFamily=DotoFont,fontWeight=FontWeight.Bold,fontSize=17.sp,color=StakText,modifier=Modifier.padding(top=16.dp,bottom=2.dp))}

@Composable internal fun HistoryCard(group:HistoryGroup,onOpen:(MediaItem)->Unit,onEdit:(JournalEntry)->Unit,modifier:Modifier){
    var expanded by rememberSaveable(group.id){mutableStateOf(false)}
    val first=group.first
    Column(modifier.fillMaxWidth()){
        Row(Modifier.fillMaxWidth().clickable{if(group.groupedEpisodes)expanded=!expanded else onOpen(first.media)}.padding(vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
            LibraryArtwork(first.media.poster,first.media.name,Modifier.width(36.dp).height(54.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Text(first.media.name,fontFamily=HankenFont,fontWeight=FontWeight.SemiBold,fontSize=15.sp,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(group.subtitle,fontFamily=HankenFont,fontSize=12.sp,color=StakTextMuted)
            }
            if(group.groupedEpisodes)IconButton(onClick={expanded=!expanded}){
                Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,if(expanded)"Collapse episodes of ${first.media.name}" else "Expand episodes of ${first.media.name}",tint=StakTextMuted)
            }else IconButton(onClick={onEdit(first)}){Icon(Icons.Default.Info,"Viewing details for ${first.media.name}",tint=StakTextMuted,modifier=Modifier.size(17.dp))}
        }
        NothingExpand(expanded){Column(Modifier.padding(start=48.dp)){
            TextButton(onClick={onOpen(journalSeriesTitle(first.media))}){Text("View title",fontFamily=HankenFont,fontSize=12.sp,color=StakTextSoft)}
            group.seasons.forEach{season->HistorySeasonRow(first.media,season,onOpen,onEdit)}
        }}
        HorizontalDivider(color=Color(0x20FFFFFF))
    }
}
@Composable private fun HistorySeasonRow(media:MediaItem,season:HistorySeason,onOpen:(MediaItem)->Unit,onEdit:(JournalEntry)->Unit){
    var expanded by rememberSaveable(journalSeriesIdentity(media),season.number){mutableStateOf(false)}
    Column{
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable{expanded=!expanded}.semantics{contentDescription="${if(expanded)"Collapse"else "Expand"} season ${season.number} of ${media.name}"},verticalAlignment=Alignment.CenterVertically){
            Text("Season ${season.number}",fontFamily=HankenFont,fontSize=13.sp,color=StakText,modifier=Modifier.weight(1f))
            Text("${season.entries.size}",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
            Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,null,tint=StakTextMuted,modifier=Modifier.padding(horizontal=12.dp).size(18.dp))
        }
        NothingExpand(expanded){Column{season.entries.forEach{entry->
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable{onOpen(entry.media)}.padding(start=12.dp),verticalAlignment=Alignment.CenterVertically){
                Text("S${entry.media.season} E${entry.media.episode}",fontFamily=SpaceMonoFont,fontSize=12.sp,color=StakTextSoft,modifier=Modifier.weight(1f))
                IconButton(onClick={onEdit(entry)}){Icon(Icons.Default.Info,"Viewing details for ${entry.media.name} S${entry.media.season} E${entry.media.episode}",tint=StakTextMuted,modifier=Modifier.size(16.dp))}
            }
        }}}
    }
}

@Composable internal fun TopSeriesRow(row:SeriesActivity,onOpen:(MediaItem)->Unit){
    Column{
        Row(Modifier.fillMaxWidth().clickable{onOpen(journalSeriesTitle(row.media))}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            LibraryArtwork(row.media.poster,row.media.name,Modifier.width(30.dp).height(45.dp))
            Text(row.media.name,fontFamily=HankenFont,fontSize=14.sp,color=StakText,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("${row.episodes} ${if(row.episodes==1)"episode"else "episodes"}",fontFamily=HankenFont,fontSize=11.sp,color=StakTextMuted)
        }
        HorizontalDivider(color=Color(0x13FFFFFF))
    }
}
@Composable internal fun TrackingGenres(genres:List<GenreShare>){
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)){
        TrackingSection("Top genres")
        val peak=genres.maxOf{it.count}.coerceAtLeast(1)
        genres.forEach{genre->Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(genre.genre,fontFamily=HankenFont,fontSize=13.sp,color=StakTextSoft);Text(genre.count.toString(),fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)}
            Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x15FFFFFF),RoundedCornerShape(2.dp))){Box(Modifier.fillMaxWidth(genre.count.toFloat()/peak).fillMaxHeight().background(Color(0xB3FFFFFF),RoundedCornerShape(2.dp)))}
        }}
    }
}
