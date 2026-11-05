@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.MediaItem
import app.stak.nativeapp.data.TitleDetails
import app.stak.nativeapp.ui.theme.*

/** Shared movie/series metadata. Content can wrap without forcing the score offscreen. */
@Composable internal fun TitleMetadata(media:MediaItem,details:TitleDetails?,modifier:Modifier=Modifier){
    val fontScale=LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth().testTag("title-metadata")){
        val sideBySide=maxWidth.value/fontScale>=340f
        if(sideBySide){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
                MetadataFacts(media,details,Modifier.weight(1f))
                if(details!=null){
                    Box(Modifier.widthIn(max=132.dp).testTag("title-rating-slot")){TitleScoreRow(details)}
                }
            }
        }else Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            MetadataFacts(media,details,Modifier.fillMaxWidth())
            if(details!=null)Box(Modifier.testTag("title-rating-slot")){TitleScoreRow(details)}
        }
    }
}

@Composable private fun MetadataFacts(media:MediaItem,details:TitleDetails?,modifier:Modifier){
    val runtime=media.runtime?.takeIf{it.isNotBlank()}?.let(::runtimeMinutes)
    val fallback=if(media.isSeries&&runtime==null)details?.seasons?.count{it>0}?.takeIf{it>0}?.let{"$it ${if(it==1)"season"else "seasons"}"}else null
    val facts=listOfNotNull(media.year?.takeIf{it.isNotBlank()},runtime?:fallback)
    val certification=(details?.certification?:media.certification)?.takeIf{it.isNotBlank()}
    Column(modifier.testTag("title-facts"),verticalArrangement=Arrangement.spacedBy(7.dp)){
        if(facts.isNotEmpty()||certification!=null)FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(5.dp),modifier=Modifier.fillMaxWidth()){
            if(facts.isNotEmpty())Text(facts.joinToString(" · "),fontFamily=HankenFont,fontSize=12.sp,lineHeight=20.sp,color=StakTextSoft)
            certification?.let{Text(it,fontFamily=SpaceMonoFont,fontSize=10.sp,lineHeight=15.sp,color=StakTextSoft,modifier=Modifier.border(1.dp,StakTextMuted.copy(alpha=.65f),RoundedCornerShape(4.dp)).padding(horizontal=5.dp,vertical=1.dp).testTag("title-certification"))}
        }
        if(media.genres.isNotEmpty())Text(media.genres.take(3).joinToString(" · "),fontFamily=HankenFont,fontSize=12.sp,lineHeight=18.sp,color=StakTextMuted,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}
