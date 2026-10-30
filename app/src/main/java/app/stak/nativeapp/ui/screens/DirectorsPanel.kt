package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DirectorsPanel(onOpenTitle: (MediaItem, String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var people by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var titles by remember { mutableStateOf(emptyList<MediaItem>()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(expanded, query) {
        if (expanded) {
            delay(300)
            error = null
            try { people = DirectServices.directors(query) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Configure TMDb in Metadata sources to browse directors." }
        }
    }
    Column {
        Row(Modifier.fillMaxWidth().height(44.dp).clickable { expanded = !expanded }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("DIRECTORS", fontFamily = SpaceMonoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = StakText)
            Text(if (expanded) "−" else "+", fontFamily = SpaceMonoFont, fontSize = 12.sp, color = StakTextMuted)
        }
        if (expanded) {
            StakTextField(query, { query = it }, "Search directors")
            error?.let { Text(it, fontFamily = HankenFont, fontSize = 12.sp, lineHeight = 18.sp, color = StakTextMuted) }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(people, key = { it.first }) { (id, name) ->
                    StakButton(name, {
                        scope.launch {
                            try { titles = DirectServices.filmography(id) }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { error = "Could not load this filmography. Please retry." }
                        }
                    })
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(titles, key = { "${it.type}:${it.mediaKey}" }) { item -> StakPosterCard(item, { onOpenTitle(item, "director-${item.mediaKey}") }, Modifier.width(120.dp)) }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("TMDb / JustWatch · US", fontFamily = SpaceMonoFont, fontSize = 10.sp, color = StakTextFaint)
    }
}
