package app.stak.nativeapp

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.currentStateAsState
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import org.json.*

@Composable
fun RemoteScreen(vm: StakViewModel) {
    var sessions by remember { mutableStateOf(emptyList<JSONObject>()) }
    var selected by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val lifecycle by
        androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycle) {
        if (!lifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@LaunchedEffect
        while (true) {
            delay(15000)
            if (!busy) refresh++
        }
    }
    LaunchedEffect(refresh) {
        if (
            vm.repository.credentials.get("jellyfin_url").isBlank() ||
                vm.repository.credentials.get("jellyfin_token").isBlank()
        ) {
            sessions = emptyList()
            error = "Connect Jellyfin in Settings first."
            return@LaunchedEffect
        }
        try {
            sessions = vm.repository.jellyfin("/Sessions").optJSONArray("items").objects()
            error = ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message.orEmpty()
        }
    }
    val active =
        sessions.firstOrNull { it.optString("Id") == selected }
            ?: if (selected.isBlank())
                sessions.firstOrNull { it.optBoolean("SupportsRemoteControl") }
            else null
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PageHeading("Jellyfin Remote")
        Surface(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Rule),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .74f),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("Active devices", Modifier.weight(1f))
                    StakIconButton(StakIcons.RefreshCw, "Refresh devices") { refresh++ }
                }
                if (error.isNotBlank())
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Choice(
                    "Jellyfin playback device",
                    active?.optString("Id") ?: selected,
                    listOf("" to "No device selected") +
                        sessions.map {
                            it.optString("Id") to
                                "${it.optString("DeviceName", it.optString("Client"))} · ${it.optString("UserName", "User")}"
                        },
                    { selected = it },
                )
                if (selected.isNotBlank() && active == null && error.isBlank())
                    Text(
                        "The selected device is no longer connected. Refresh or choose another device.",
                        fontSize = 12.sp,
                        color = Muted,
                    )
                Text(
                    active?.optJSONObject("NowPlayingItem")?.optString("Name") ?: "Nothing playing",
                    fontSize = 14.sp,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(
                            "previous" to StakIcons.SkipBack,
                            "play" to StakIcons.Play,
                            "pause" to StakIcons.Pause,
                            "next" to StakIcons.SkipForward,
                            "stop" to StakIcons.Square,
                        )
                        .forEach { (action, icon) ->
                            StakIconButton(
                                icon,
                                action,
                                enabled =
                                    active?.optBoolean("SupportsRemoteControl") == true &&
                                        !busy &&
                                        error.isBlank(),
                            ) {
                                vm.operation {
                                    busy = true
                                    try {
                                        val id = active?.optString("Id").orEmpty()
                                        require(Regex("[a-zA-Z0-9-]+").matches(id))
                                        val command =
                                            mapOf(
                                                    "previous" to "PreviousTrack",
                                                    "play" to "Unpause",
                                                    "pause" to "Pause",
                                                    "next" to "NextTrack",
                                                    "stop" to "Stop",
                                                )
                                                .getValue(action)
                                        vm.repository.jellyfin(
                                            "/Sessions/$id/Playing/$command",
                                            JSONObject(),
                                        )
                                        refresh++
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        }
                }
                Text(
                    "Playback commands depend on the connected Jellyfin client's remote-control support.",
                    fontSize = 12.sp,
                    color = Muted,
                )
            }
        }
    }
}

@Composable
fun PersonScreen(vm: StakViewModel, id: Int, onOpen: (Media, String) -> Unit, onBack: () -> Unit) {
    var person by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var biography by remember { mutableStateOf(false) }
    LaunchedEffect(id, retry) {
        try {
            person =
                vm.repository.tmdb(
                    "/person/$id",
                    mapOf("append_to_response" to "movie_credits,tv_credits"),
                )
            error = ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message.orEmpty()
        }
    }
    val titles =
        remember(person) {
            listOf("movie" to "movie_credits", "series" to "tv_credits")
                .flatMap { (type, key) ->
                    val credits = person?.optJSONObject(key)
                    (credits?.optJSONArray("cast").objects() +
                            credits?.optJSONArray("crew").objects())
                        .filter { it.optInt("id") > 0 }
                        .map { vm.repository.remember(tmdbMedia(it, type)) }
                }
                .distinctBy { it.key }
        }
    LazyVerticalGrid(
        GridCells.Fixed(3),
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 112.dp),
    ) {
        item(span = { GridItemSpan(3) }) { MotionButton("← Back", onClick = onBack) }
        if (error.isNotBlank()) item(span = { GridItemSpan(3) }) { EmptyState(error) { retry++ } }
        if (person == null && error.isBlank())
            item(span = { GridItemSpan(3) }) { DotStatus(label = "Loading person") }
        person?.let { p ->
            item(span = { GridItemSpan(3) }) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (p.optString("profile_path").isNotBlank())
                        AsyncImage(
                            "https://image.tmdb.org/t/p/w185${p.optString("profile_path")}",
                            null,
                            Modifier.width(110.dp).aspectRatio(.75f),
                            contentScale = ContentScale.Crop,
                        )
                    Text(
                        p.optString("name"),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
            }
            if (p.optString("biography").isNotBlank())
                item(span = { GridItemSpan(3) }) {
                    Column(
                        Modifier.fillMaxWidth()
                            .border(1.dp, Rule, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            "Biography",
                            Modifier.clickable { biography = !biography },
                            fontSize = 14.sp,
                        )
                        if (biography)
                            Text(
                                p.optString("biography"),
                                Modifier.padding(top = 12.dp),
                                fontSize = 14.sp,
                                lineHeight = 24.sp,
                                color = Muted,
                            )
                    }
                }
            item(span = { GridItemSpan(3) }) { Text("Films & series", fontSize = 18.sp) }
        }
        items(titles, key = { it.key }) { PosterCard(it, "person-$id", onOpen = onOpen) }
    }
}
