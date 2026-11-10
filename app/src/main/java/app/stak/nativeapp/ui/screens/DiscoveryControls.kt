@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.stak.nativeapp.ui.screens
import androidx.compose.foundation.shape.RoundedCornerShape
import app.stak.nativeapp.ui.components.recordGlassSource

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.components.*
import app.stak.nativeapp.ui.theme.*
import coil.compose.AsyncImage
import java.util.Locale

@Composable
fun DiscoverySelect(label: String, value: String, options: List<Pair<String,String>>, onChange: (String) -> Unit, modifier: Modifier = Modifier, fieldHeight: Int = 48, labelSize: Int = 13, enabled: Boolean = true, accessibilityLabel:String?=null, selectedText:String?=null) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label.isNotBlank()) { Text(label, fontFamily = HankenFont, fontSize = labelSize.sp, color = if (labelSize < 13) StakTextMuted else StakTextSoft); Spacer(Modifier.height(7.dp)) }
        Box {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha=.025f)).border(1.dp,Color.White.copy(alpha=.30f),RoundedCornerShape(10.dp)).clickable(enabled = enabled) { open = true }.semantics { contentDescription = accessibilityLabel ?: label.ifBlank { "Sort" }; role=androidx.compose.ui.semantics.Role.Button }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(selectedText ?: options.find { it.first == value }?.second ?: value, fontFamily = HankenFont, fontSize = 14.sp, color = StakText, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp)); Icon(Lucide.ChevronDown, null, tint = StakTextMuted, modifier = Modifier.size(18.dp))
            }
            GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                options.forEach { (id,name) -> DropdownMenuItem(text = { Text(name, fontFamily = HankenFont, fontSize = 14.sp, color = if (id == value) StakText else StakTextMuted) }, onClick = { open = false; onChange(id) }) }
            }
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label.uppercase(), fontFamily = SpaceMonoFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = .96.sp, color = StakText)
    Spacer(Modifier.height(12.dp))
}

@Composable
fun DiscoveryFilterSheet(type: String, value: DiscoveryFilters, genres: List<DiscoveryOption>, onDismiss: () -> Unit, onApply: (DiscoveryFilters) -> Unit) {
    var draft by remember { mutableStateOf(value) }
    var languageQuery by remember { mutableStateOf("") }
    val languages = remember(languageQuery, draft.language) {
        val all = (Locale.getISOLanguages().toList() + listOf(draft.language).filter { it.isNotBlank() }).distinct().map { code -> code to "${Locale(code).getDisplayLanguage(Locale.ENGLISH)} · ${code.uppercase()}" }.sortedBy { it.second }
        all.filter { it.first == draft.language || it.second.contains(languageQuery.trim(), true) || it.first.contains(languageQuery.trim(), true) }
    }
    val validation = draft.validationError()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.Transparent, contentColor = StakText, dragHandle = null, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        app.stak.nativeapp.ui.components.SmoothDialogWindow()
        val parentGlass=app.stak.nativeapp.ui.components.LocalGlassSource.current
        val sheetLayer=androidx.compose.ui.graphics.rememberGraphicsLayer()
        val sheetSource=remember(sheetLayer){app.stak.nativeapp.ui.components.GlassSource(sheetLayer,crossWindow=true)}
        val sheetView=androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.CompositionLocalProvider(app.stak.nativeapp.ui.components.LocalGlassSource provides sheetSource) {
        app.stak.nativeapp.ui.components.GlassSurface(source=parentGlass,modifier=Modifier.recordGlassSource(sheetSource,sheetView)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f)) {
            SheetHeading("Filters", null, onDismiss)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp)) {
                SectionLabel("Content")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DiscoverySelect("Genre", draft.genre, listOf("" to "All genres") + genres.map { it.id to it.name }, { draft = draft.copy(genre = it) }, Modifier.weight(1f))
                    val ratings = if (type == "series") listOf("TV-Y","TV-Y7","TV-G","TV-PG","TV-14","TV-MA") else listOf("G","PG","PG-13","R","NC-17")
                    DiscoverySelect("US age rating", draft.certification, listOf("" to "Any") + ratings.map { it to it }, { draft = draft.copy(certification = it) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Text("Find a language", fontFamily = HankenFont, fontSize = 13.sp, color = StakTextSoft)
                Spacer(Modifier.height(7.dp))
                StakTextField(languageQuery, { languageQuery = it }, "Language name or code", modifier = Modifier.semantics { contentDescription = "Find a language" })
                Spacer(Modifier.height(12.dp))
                DiscoverySelect("Original language", draft.language, listOf("" to "Any language") + languages, { draft = draft.copy(language = it) })
                if (languages.isEmpty()) Text("No matching language. Try a name or two-letter code.", fontFamily = HankenFont, fontSize = 12.sp, color = StakTextMuted)
                Spacer(Modifier.height(24.dp)); SectionLabel("Release")
                val decade = draft.yearFrom.takeIf { it.toIntOrNull()?.plus(9) == draft.yearTo.toIntOrNull() }.orEmpty()
                DiscoverySelect("Decade", decade, listOf("" to "Any / custom range") + (2020 downTo 1900 step 10).map { it.toString() to "${it}s" }, { year -> draft = draft.copy(yearFrom = year, yearTo = year.toIntOrNull()?.plus(9)?.toString().orEmpty()) })
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("From year", fontFamily = HankenFont, fontSize = 13.sp, color = StakTextSoft); Spacer(Modifier.height(7.dp))
                        StakTextField(draft.yearFrom, { draft = draft.copy(yearFrom = it) }, "", modifier = Modifier.semantics { contentDescription = "From year" }, keyboardType = KeyboardType.Number)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("To year", fontFamily = HankenFont, fontSize = 13.sp, color = StakTextSoft); Spacer(Modifier.height(7.dp))
                        StakTextField(draft.yearTo, { draft = draft.copy(yearTo = it) }, "", modifier = Modifier.semantics { contentDescription = "To year" }, keyboardType = KeyboardType.Number)
                    }
                }
                Spacer(Modifier.height(24.dp)); SectionLabel("Ratings")
                DiscoverySelect("Minimum IMDb rating", draft.imdbRating, listOf("" to "Any") + listOf("6","7","8","9").map { it to "$it+" }, { draft = draft.copy(imdbRating = it) })
                Spacer(Modifier.height(8.dp))
                Text("Uses your OMDb quota. Unrated titles are excluded; ratings are cached for 24 hours.", fontFamily = HankenFont, fontSize = 12.sp, lineHeight = 18.sp, color = StakTextMuted)
                Spacer(Modifier.height(24.dp)); SectionLabel("Your library")
                FilterSwitch("Hide watched", draft.hideWatched) { draft = draft.copy(hideWatched = it) }
                FilterSwitch("Hide known library titles", draft.hideLibrary) { draft = draft.copy(hideLibrary = it) }
                if (validation != null) { Spacer(Modifier.height(12.dp)); Text(validation, fontFamily = HankenFont, fontSize = 13.sp, color = Color(0xFFFF7A80)) }
            }
            Row(Modifier.fillMaxWidth().border(1.dp, StakBorder).padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StakButton("Reset filters", { draft = DiscoveryFilters() }, Modifier.weight(1f).heightIn(min = 48.dp))
                StakButton("Show results", { onApply(draft) }, Modifier.weight(1f).heightIn(min = 48.dp), variant = StakButtonVariant.Primary, enabled = validation == null)
            }
        }
        }
        }
    }
}

@Composable
private fun FilterSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onChange(!value) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = HankenFont, fontSize = 15.sp, color = StakText, modifier = Modifier.weight(1f))
        Switch(value, onChange, modifier = Modifier.semantics { contentDescription = label })
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(StakBorder))
}

@Composable
private fun SheetHeading(title: String, subtitle: String?, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontFamily = DotoFont, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 31.2.sp, color = StakText, modifier = Modifier.weight(1f))
            IconButton(onDismiss, Modifier.size(48.dp)) { Icon(Lucide.X, "Close", tint = StakTextMuted, modifier = Modifier.size(18.dp)) }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(StakBorder))
}

fun streamingShortcuts(providers: List<DiscoveryOption>): List<DiscoveryOption> {
    val brands = listOf("Apple TV" to "^Apple TV(?:\\+| Plus)?$", "Netflix" to "^Netflix(?: Standard with Ads)?$", "HBO Max" to "^(?:HBO Max|Max)$", "Disney Plus" to "^Disney(?:\\+| Plus)$", "Hulu" to "^Hulu$", "Prime Video" to "^(?:Amazon )?Prime Video(?: with Ads)?$", "Paramount Plus" to "^Paramount(?:\\+| Plus)(?: with Showtime)?$")
    return brands.mapNotNull { (name, pattern) -> val matched = providers.filter { Regex(pattern, RegexOption.IGNORE_CASE).matches(it.name) }; if (matched.isEmpty()) null else DiscoveryOption(matched.map { it.id }.distinct().joinToString("|"), name, matched.first().logo) }
}

@Composable
fun DiscoveryServices(viewModel: DiscoverViewModel) {
    val providers by viewModel.providers.collectAsState(); val regions by viewModel.regions.collectAsState()
    val region by viewModel.region.collectAsState(); val filters by viewModel.filters.collectAsState()
    val loading by viewModel.servicesLoading.collectAsState(); val error by viewModel.servicesError.collectAsState()
    var sheet by remember { mutableStateOf(false) }; var query by remember { mutableStateOf("") }
    val shortcuts = remember(providers) { streamingShortcuts(providers) }
    val selected = (providers + shortcuts).find { it.id.split('|').toSet() == filters.provider.split('|').toSet() } ?: shortcuts.find { option -> filters.provider.isNotBlank() && option.id.split('|').any { it in filters.provider.split('|') } }
    Column {
        DiscoveryServiceStrip(shortcuts,filters.provider,!loading,{value->viewModel.applyFilters(filters.copy(provider=value))},{sheet=true})

        if(filters.provider.isNotBlank())Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text(selected?.name?:"Selected service",Modifier.weight(1f),fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextSoft)
            TextButton(onClick={viewModel.clearFilter("provider")}){Text("Clear",fontFamily=SpaceMonoFont,fontSize=11.sp,color=StakTextMuted)}
        }
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            InlineRetry(error.orEmpty(), "Retry services") { viewModel.refreshServices() }
            Spacer(Modifier.height(6.dp))
        } else if (loading) Text("Loading services…", fontFamily = HankenFont, fontSize = 12.sp, color = StakTextMuted)
        if (filters.provider.isNotBlank() && !loading && error == null && selected == null) Text("This service is unavailable for the current region or media type. Clear it or choose another service.", fontFamily = HankenFont, fontSize = 12.sp, lineHeight = 18.sp, color = StakTextMuted)
    }
    if (sheet) ModalBottomSheet(onDismissRequest = { sheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF101010), contentColor = StakText, dragHandle = null) {
        app.stak.nativeapp.ui.components.SmoothDialogWindow()
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f)) {
            SheetHeading("Streaming services", "Availability follows your saved region.") { sheet = false }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp)) {
                DiscoverySelect("Region", region, (regions + listOf(DiscoveryOption(region,region))).distinctBy { it.id }.map { it.id to "${it.name} · ${it.id}" }, { viewModel.setRegion(it) })
                Spacer(Modifier.height(12.dp))
                StakTextField(query, { query = it }, "Find a service")
                Spacer(Modifier.height(12.dp))
                ServiceRow(DiscoveryOption("", "All services"), filters.provider.isBlank()) { viewModel.applyFilters(filters.copy(provider = "")); sheet = false; query = "" }
                val matches = providers.filter { it.name.contains(query.trim(), true) }
                matches.forEach { option -> ServiceRow(option, option.id in filters.provider.split('|')) { viewModel.applyFilters(filters.copy(provider = option.id)); sheet = false; query = "" } }
                if (matches.isEmpty() && !loading) Text(if (error != null) "Services could not be loaded. Close this sheet and retry." else if (providers.isEmpty()) "No services available for this region and media type." else "No matching services. Try another name.", fontFamily = HankenFont, fontSize = 12.sp, lineHeight = 18.sp, color = StakTextMuted)
            }
        }
    }
}

@Composable
private fun ServiceRow(option: DiscoveryOption, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) Color(0x12FFFFFF) else Color.Transparent).clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (option.id.isNotBlank()) Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF181818)), contentAlignment = Alignment.Center) { if (option.logo != null) AsyncImage(option.logo, null, modifier = Modifier.fillMaxSize()) else Text(option.name.take(3), fontSize = 9.sp, color = StakTextMuted) }
        Text(option.name, fontFamily = HankenFont, fontSize = 15.sp, color = StakText, modifier = Modifier.weight(1f))
        if (selected) Icon(Lucide.Check, null, tint = StakAccent, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun InlineRetry(message: String, action: String = "Retry", onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(StakSurface.copy(alpha=.55f)).border(1.dp,StakBorderSoft,RoundedCornerShape(12.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Text(message,fontFamily=HankenFont,fontSize=13.sp,lineHeight=20.sp,color=StakTextSoft)
        StakButton(action,onClick,compact=true,variant=StakButtonVariant.Ghost)
    }
}

/** Provider artwork fills the clipped card; selection remains above the image. */
@Composable internal fun DiscoveryServiceStrip(options:List<DiscoveryOption>,selected:String,enabled:Boolean,onSelect:(String)->Unit,onMore:()->Unit){
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth().semantics{contentDescription="Streaming shortcuts"}){
        items(options,key={it.id}){option->
            val active=selected.isNotBlank()&&selected.split('|').all{it in option.id.split('|')}
            val shape=RoundedCornerShape(12.dp)
            Box(Modifier.size(48.dp).clip(shape).background(Color(0xFF111214)).border(1.dp,if(active)Color.White.copy(alpha=.85f)else Color.White.copy(alpha=.08f),shape).clickable(enabled=enabled){onSelect(if(active)""else option.id)}.semantics{contentDescription=option.name;this.selected=active},contentAlignment=Alignment.Center){
                if(option.logo!=null)AsyncImage(option.logo,null,contentScale=androidx.compose.ui.layout.ContentScale.Crop,modifier=Modifier.fillMaxSize())
                else Text(option.name,fontFamily=HankenFont,fontSize=9.sp,maxLines=2,overflow=TextOverflow.Ellipsis,color=StakText,modifier=Modifier.padding(horizontal=6.dp).offset(y=(-2).dp))
                if(active)Box(Modifier.align(Alignment.BottomCenter).padding(bottom=3.dp).size(5.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFE31C2F)))
            }
        }
        item{
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF111214)).border(1.dp,StakBorder,RoundedCornerShape(12.dp)).clickable(onClick=onMore).semantics{contentDescription="All services"},contentAlignment=Alignment.Center){Text("More",fontFamily=SpaceMonoFont,fontSize=10.sp,color=StakTextMuted)}
        }
    }
}
