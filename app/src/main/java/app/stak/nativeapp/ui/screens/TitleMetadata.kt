@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.MediaItem
import app.stak.nativeapp.data.TitleDetails
import app.stak.nativeapp.ui.components.CertificationBadge
import app.stak.nativeapp.ui.theme.*

/** Genres first, then one compact wrapping facts/rating line for films and series. */
@Composable internal fun TitleMetadata(media:MediaItem,details:TitleDetails?,modifier:Modifier=Modifier){
    val runtime=media.runtime?.takeIf{it.isNotBlank()}?.let(::runtimeMinutes)
    val seasons=if(media.isSeries&&runtime==null)details?.seasons?.count{it>0}?.takeIf{it>0}?.let{"$it ${if(it==1)"season"else "seasons"}"}else null
    val facts=listOfNotNull(media.year?.takeIf{it.isNotBlank()},runtime?:seasons)
    val certification=(details?.certification?:media.certification)?.takeIf{it.isNotBlank()}
    Column(modifier.fillMaxWidth().testTag("title-metadata"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(media.genres.isNotEmpty())Text(media.genres.take(3).joinToString(" · "),Modifier.testTag("title-genres"),fontFamily=HankenFont,fontSize=12.sp,lineHeight=19.sp,color=StakTextMuted,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center)
        FlowRow(Modifier.fillMaxWidth().testTag("title-facts"),horizontalArrangement=Arrangement.spacedBy(12.dp,Alignment.CenterHorizontally),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(facts.isNotEmpty()||certification!=null)Row(
                horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                if(facts.isNotEmpty())Text(facts.joinToString(" · "),fontFamily=HankenFont,fontSize=13.sp,lineHeight=20.sp,color=StakTextSoft,textAlign=TextAlign.Center)
                certification?.let{Box(Modifier.testTag("title-certification")){CertificationBadge(it)}}
            }
            if(details!=null)Row(Modifier.heightIn(min=20.dp).testTag("title-rating-slot"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                if(facts.isNotEmpty()||certification!=null)Box(Modifier.width(1.dp).height(12.dp).background(Color.White.copy(alpha=.18f)))
                TitleScoreRow(details)
            }
        }
    }
}
