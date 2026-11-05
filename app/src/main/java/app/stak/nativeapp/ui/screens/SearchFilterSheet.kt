@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*

@Composable
private fun SearchFilterGroup(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Text(title.uppercase(), fontFamily = SpaceMonoFont, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.1.sp, color = StakTextMuted)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    Spacer(Modifier.height(20.dp))
}

@Composable
fun SearchFilterSheet(value: DiscoveryFilters, genres: List<DiscoveryOption>, onDismiss: () -> Unit, onApply: (DiscoveryFilters) -> Unit) {
    var draft by remember { mutableStateOf(value) }
    val year = java.time.LocalDate.now().year
    val validation = draft.validationError()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF0D0D0D), contentColor = StakText) {
        app.stak.nativeapp.ui.components.SmoothDialogWindow()
        Column(Modifier.fillMaxWidth().heightIn(max = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * .86f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text("Filters", fontFamily = DotoFont, fontWeight = FontWeight.Black, fontSize = 24.sp, color = StakText)
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.height(24.dp))
            SearchFilterGroup("Sort") {
                listOf("" to "Popular", "primary_release_date.desc" to "Newest", "primary_release_date.asc" to "Oldest", "original_title.asc" to "A–Z").forEach { (id,label) -> StakChip(label, draft.sort == id, { draft = draft.copy(sort = id) }) }
            }
            SearchFilterGroup("Genre") {
                StakChip("Any", draft.genre.isBlank(), { draft = draft.copy(genre = "") })
                genres.distinctBy { it.name }.forEach { option -> StakChip(option.name, draft.genre == option.name || draft.genre == option.id, { draft = draft.copy(genre = option.name) }) }
            }
            SearchFilterGroup("Year") {
                listOf(Triple("This year",year,year),Triple("2020s",2020,year),Triple("2010s",2010,2019),Triple("2000s",2000,2009),Triple("90s",1990,1999),Triple("Older",1900,1989)).forEach { (label,from,to) ->
                    StakChip(label, draft.yearFrom == from.toString() && draft.yearTo == to.toString(), { draft = draft.copy(yearFrom = from.toString(), yearTo = to.toString()) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StakTextField(draft.yearFrom, { draft = draft.copy(yearFrom = it) }, "From", modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number)
                Text("–", fontFamily = HankenFont, color = StakTextMuted, modifier = Modifier.padding(top = 12.dp))
                StakTextField(draft.yearTo, { draft = draft.copy(yearTo = it) }, "To", modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number)
            }
            Spacer(Modifier.height(20.dp))
            SearchFilterGroup("Min IMDb rating") {
                listOf("","6","7","8","9").forEach { id -> StakChip(if (id.isBlank()) "Any" else "$id+", draft.imdbRating == id, { draft = draft.copy(imdbRating = id) }) }
            }
            if (validation != null) Text(validation, fontFamily = HankenFont, fontSize = 13.sp, color = Color(0xFFFF7A80))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StakButton("Reset", { draft = DiscoveryFilters() }, Modifier.weight(1f))
                StakButton("Show results", { onApply(draft) }, Modifier.weight(1f), variant = StakButtonVariant.Primary, enabled = validation == null)
            }
        }
    }
}
