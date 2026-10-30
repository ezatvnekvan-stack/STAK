@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException

@Composable private fun ExtrasHeading(text: String) { Text(text.uppercase(), fontFamily = DotoFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = 1.4.sp, color = StakText); Spacer(Modifier.height(12.dp)) }

@Composable fun TitleScoreRow(details: TitleDetails?) {
    if (details == null) return
    RatingStrip(details.imdbRating,details.rottenTomatoes,details.metacritic)
}

@Composable fun TitleExtras(details: TitleDetails?, media: MediaItem, onOpenTitle: (MediaItem) -> Unit) {
    var selectedImage by remember(media.mediaKey) { mutableStateOf<String?>(null) }
    var person by remember(media.mediaKey) { mutableStateOf<TitlePerson?>(null) }
    var allCredits by remember(media.mediaKey) { mutableStateOf(false) }
    val gallery = (details?.gallery.orEmpty().ifEmpty { listOfNotNull(media.background, media.poster) }).distinct()
    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp)){
        if(!details?.cast.isNullOrEmpty()){
            Spacer(Modifier.height(24.dp));ExtrasHeading("Cast")
            LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                items(details!!.cast,key={"${it.id}:${it.role}"},contentType={"cast"}){actor->
                    Column(Modifier.width(76.dp).clickable{person=actor}){
                        AsyncImage(actor.photo,actor.name,contentScale=ContentScale.Crop,modifier=Modifier.width(76.dp).height(96.dp).clip(RoundedCornerShape(10.dp)).background(StakSurface))
                        Spacer(Modifier.height(7.dp))
                        Text(actor.name,fontFamily=HankenFont,fontSize=11.sp,lineHeight=14.sp,color=StakText,minLines=2,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text(actor.role,fontFamily=HankenFont,fontSize=10.sp,lineHeight=14.sp,color=StakTextMuted,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if(!details?.crew.isNullOrEmpty()){
            Spacer(Modifier.height(24.dp));ExtrasHeading("Creators")
            val shown=if(allCredits)details!!.crew else details!!.crew.take(2)
            shown.forEach{credit->
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable{person=credit}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
                    Text(credit.name,Modifier.weight(1f),fontFamily=HankenFont,fontSize=13.sp,lineHeight=17.sp,color=StakText,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(credit.role,Modifier.widthIn(max=140.dp),fontFamily=HankenFont,fontSize=11.sp,lineHeight=15.sp,color=StakTextMuted,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
                HorizontalDivider(color=Color.White.copy(alpha=.07f))
            }
            if(details.crew.size>2)TextButton(onClick={allCredits=!allCredits},modifier=Modifier.heightIn(min=44.dp)){
                Text(if(allCredits)"Show fewer"else "View all ${details.crew.size}",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextSoft)
                Spacer(Modifier.width(6.dp));Icon(Lucide.ChevronDown,null,tint=StakTextMuted,modifier=Modifier.size(13.dp).rotate(if(allCredits)180f else 0f))
            }
        }
        if(gallery.isNotEmpty()){
            Spacer(Modifier.height(24.dp));ExtrasHeading("Gallery")
            BoxWithConstraints{
                val width=(maxWidth-20.dp)/3
                LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    items(gallery,key={it},contentType={"gallery"}){picture->
                        AsyncImage(picture,"View artwork ${gallery.indexOf(picture)+1} for ${media.name}",contentScale=ContentScale.Crop,modifier=Modifier.width(width).aspectRatio(16f/9f).clip(RoundedCornerShape(10.dp)).background(StakSurface).clickable{selectedImage=picture})
                    }
                }
            }
        }
        listOf("Collection" to details?.collection.orEmpty(),"Related titles" to details?.related.orEmpty()).forEach{(heading,titles)->if(titles.isNotEmpty()){
            Spacer(Modifier.height(24.dp));ExtrasHeading(heading)
            BoxWithConstraints{val width=(maxWidth-24.dp)/3;LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                items(titles,key={"${it.type}:${it.id}"},contentType={"related"}){item->StakPosterCard(item,{onOpenTitle(item)},Modifier.width(width))}
            }}
        }}
    }
    selectedImage?.let { picture -> Dialog(onDismissRequest = { selectedImage = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SmoothDialogWindow()
        Box(Modifier.fillMaxSize().background(Color(0xF2080808))) {
            AsyncImage(picture, "${media.name} artwork", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(16.dp))
            StakButton("Close artwork", { selectedImage = null }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp))
        }
    } }
    person?.let { p -> PersonSheet(p, onDismiss = { person = null }, onOpenTitle = { person = null; onOpenTitle(it) }) }
}

@Composable private fun PersonSheet(person: TitlePerson, onDismiss: () -> Unit, onOpenTitle: (MediaItem) -> Unit) {
    var movies by remember(person.id) { mutableStateOf<List<MediaItem>>(emptyList()) }
    var error by remember(person.id) { mutableStateOf(false) }
    var loading by remember(person.id) { mutableStateOf(true) }
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(person.id, revision) {
        loading = true; error = false
        try { movies = DirectServices.filmography(person.id) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { error = true } finally { loading = false }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF101010)) {
        app.stak.nativeapp.ui.components.SmoothDialogWindow()
        Column(Modifier.fillMaxWidth().heightIn(max = 700.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(person.name, fontFamily = DotoFont, fontWeight = FontWeight.Black, fontSize = 28.sp, color = StakText)
            Text(person.role, fontFamily = HankenFont, fontSize = 13.sp, color = StakTextMuted)
            Spacer(Modifier.height(20.dp)); ExtrasHeading("Filmography")
            if (loading) Text("Loading filmography…", color = StakTextMuted)
            if (error) { Text("Filmography could not load.", color = StakTextMuted); StakButton("Retry", { revision++ }) }
            movies.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { title -> StakPosterCard(title, { onOpenTitle(title) }, Modifier.weight(1f)) }; repeat(2-row.size) { Spacer(Modifier.weight(1f)) } }; Spacer(Modifier.height(16.dp)) }
            if (!loading && !error && movies.isEmpty()) Text("No titles returned.", color = StakTextMuted)
            StakButton("Close", onDismiss)
        }
    }
}

@Composable fun EpisodeDetailsSheet(media: MediaItem, episode: Episode, onDismiss: () -> Unit, onToggleWatched: () -> Unit, onFindSources: () -> Unit) {
    var expanded by remember(episode.season,episode.episode) { mutableStateOf(false) }
    var rating by remember(episode.season,episode.episode) { mutableStateOf<Double?>(episode.imdbRating) }
    LaunchedEffect(media.mediaKey,episode.season,episode.episode) {
        try { if (media.imdbId != null) rating = MediaRepository().episodeRating(media.imdbId,episode) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF101010)) {
        app.stak.nativeapp.ui.components.SmoothDialogWindow()
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            episode.image?.let { AsyncImage(it, episode.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f)) }
            Spacer(Modifier.height(12.dp)); Text(episode.name, fontFamily = DotoFont, fontWeight = FontWeight.Black, fontSize = 24.sp, color = StakText)
            Text("${media.name} · S${episode.season} E${episode.episode}${episode.released?.let { " · ${it.take(10)}" }.orEmpty()}", fontFamily = HankenFont, fontSize = 12.sp, color = StakTextMuted)
            Spacer(Modifier.height(12.dp)); if (rating != null) RatingBadge(rating) else Text("IMDb episode rating unavailable", fontFamily = SpaceMonoFont, fontSize = 10.sp, color = StakTextMuted)
            Spacer(Modifier.height(12.dp)); Text(episode.overview ?: "No episode synopsis is available from the configured providers.", fontFamily = HankenFont, fontSize = 12.sp, lineHeight = 19.sp, color = StakTextSoft, maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
            if (episode.overview.orEmpty().length > 180) StakButton(if (expanded) "Less synopsis" else "Full synopsis", { expanded = !expanded }, variant = StakButtonVariant.Ghost)
            Spacer(Modifier.height(12.dp)); Text(if(episode.watched)"Watched on Jellyfin"else "Not watched on Jellyfin",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)
            Spacer(Modifier.height(16.dp)); StakButton("Find episode sources", onFindSources, enabled = media.mediaKey.matches(Regex("tt\\d+")), variant = StakButtonVariant.Primary, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp)); StakButton("Close", onDismiss)
        }
    }
}
