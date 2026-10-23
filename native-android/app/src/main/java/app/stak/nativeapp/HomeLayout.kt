package app.stak.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

val defaultNavigation = listOf("home", "discover", "watchlist", "tracking", "library", "settings")
val allNavigation = defaultNavigation + "torbox"

fun navigationOrder(options: Map<String, String>): List<String> {
    val encoded = options["nav_order"] ?: return defaultNavigation
    val saved =
        runCatching { JSONArray(encoded).strings() }
            .getOrElse {
                return defaultNavigation
            }
    return (saved.map { if (it == "account") "settings" else it }.filter { it in allNavigation } +
            listOf("home", "settings"))
        .distinct()
}

fun navigationLabel(id: String) = id.replaceFirstChar { it.titlecase() }.replace("Torbox", "TorBox")

fun movedItem(ids: List<String>, index: Int, delta: Int): List<String> {
    val target = index + delta
    if (index !in ids.indices || target !in ids.indices) return ids
    return ids.toMutableList().apply {
        val item = removeAt(index)
        add(target, item)
    }
}

data class HomeShelf(
    val id: String,
    val label: String,
    val type: String = "movie",
    val category: String = "popular",
    val genre: Int = 0,
    val defaultOn: Boolean = false,
) {
    fun enabled(options: Map<String, String>) =
        options["home_$id"]?.toBooleanStrictOrNull() ?: defaultOn
}

fun homeGenres(type: String): List<Pair<Int, String>> =
    if (type == "movie")
        listOf(
            28 to "Action",
            12 to "Adventure",
            16 to "Animation",
            35 to "Comedy",
            80 to "Crime",
            99 to "Documentary",
            18 to "Drama",
            10751 to "Family",
            14 to "Fantasy",
            36 to "History",
            27 to "Horror",
            10402 to "Music",
            9648 to "Mystery",
            10749 to "Romance",
            878 to "Science Fiction",
            10770 to "TV Movie",
            53 to "Thriller",
            10752 to "War",
            37 to "Western",
        )
    else
        listOf(
            10759 to "Action & Adventure",
            16 to "Animation",
            35 to "Comedy",
            80 to "Crime",
            99 to "Documentary",
            18 to "Drama",
            10751 to "Family",
            10762 to "Kids",
            9648 to "Mystery",
            10763 to "News",
            10764 to "Reality",
            10765 to "Sci-Fi & Fantasy",
            10766 to "Soap",
            10767 to "Talk",
            10768 to "War & Politics",
            37 to "Western",
        )

fun homeShelves(options: Map<String, String>, year: Int): List<HomeShelf> {
    val standard =
        listOf(
            HomeShelf("continue", "Continue watching", defaultOn = true),
            HomeShelf("upcoming", "New releases", defaultOn = true),
            HomeShelf("watchlist", "Your watchlist", defaultOn = true),
            HomeShelf("trending_movie", "Trending movies", category = "trending", defaultOn = true),
            HomeShelf("trending_series", "Trending series", "series", "trending", defaultOn = true),
            HomeShelf("top_movie", "Top rated movies", category = "top", defaultOn = true),
            HomeShelf(
                "new_movie",
                "Released in $year · movies",
                category = "year",
                defaultOn = true,
            ),
            HomeShelf("top_series", "Top rated series", "series", "top"),
            HomeShelf("new_series", "Released in $year · series", "series", "year"),
            HomeShelf("for_you", "For You · watchlist genres", category = "top"),
            HomeShelf("genre_trending", "Trending · watchlist genres", "series"),
            HomeShelf("recent", "Recently added to Jellyfin"),
            HomeShelf("available", "Available to watch"),
            HomeShelf("new_seasons", "Recently added series", "series"),
            HomeShelf("surprise", "Tonight · surprise me"),
        )
    val genres =
        runCatching {
                JSONArray(options["home_genres"] ?: "[]").objects().take(24).mapNotNull {
                    val type = it.optString("type")
                    val id = it.optInt("genre")
                    val name = homeGenres(type).firstOrNull { g -> g.first == id }?.second
                    if (type !in listOf("movie", "series") || name == null) null
                    else
                        HomeShelf(
                            "genre:$type:$id",
                            "$name ${if (type == "movie") "movies" else "series"}",
                            type,
                            genre = id,
                            defaultOn = true,
                        )
                }
            }
            .getOrDefault(emptyList())
    val specs = (standard + genres).distinctBy { it.id }
    val order =
        runCatching { JSONArray(options["home_order"] ?: "[]").strings() }.getOrDefault(emptyList())
    return (order + specs.map { it.id }).distinct().mapNotNull { id ->
        specs.firstOrNull { it.id == id }
    }
}

@Composable
fun HomeSettings(vm: StakViewModel) {
    val options = LocalOptions.current
    val shelves = homeShelves(options, vm.repository.today.year)
    val nav = navigationOrder(options)
    var type by remember { mutableStateOf("movie") }
    var genre by remember { mutableStateOf("") }
    val edge = if (MaterialTheme.colorScheme.background.red < .5f) androidx.compose.ui.graphics.Color(0xff2a2a2a) else androidx.compose.ui.graphics.Color(0xffcbcbc7)
    fun remove(shelf: HomeShelf) {
        vm.option("home_genres", JSONArray(shelves.filter { it.genre > 0 && it.id != shelf.id }.map {
            JSONObject().put("type", it.type).put("genre", it.genre)
        }).toString())
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CapacitorSettingsPanel {
            SectionHeading("Taskbar")
            Spacer(Modifier.height(10.dp))
            Text("Choose tabs and their order. Home and Settings always stay available.", fontSize = 12.sp, lineHeight = 16.sp, color = Muted)
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (nav + allNavigation).distinct().forEach { id ->
                    val index = nav.indexOf(id)
                    val label = navigationLabel(id)
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = androidx.compose.ui.graphics.Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(1.dp, edge)) {
                        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val icon = when (id) {
                                "home" -> StakIcons.House
                                "discover" -> StakIcons.Compass
                                "watchlist" -> StakIcons.Bookmark
                                "tracking" -> StakIcons.ChartNoAxesCombined
                                "library" -> StakIcons.HardDrive
                                "torbox" -> StakIcons.Database
                                else -> StakIcons.Settings2
                            }
                            Icon(icon, null, Modifier.size(16.dp), tint = Muted)
                            Text(label, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 20.sp)
                            StakIconButton(StakIcons.ChevronUp, "Move $label tab up", enabled = index > 0, iconSize = 14.dp) {
                                vm.option("nav_order", JSONArray(movedItem(nav, index, -1)).toString())
                            }
                            StakIconButton(StakIcons.ChevronDown, "Move $label tab down", enabled = index in 0 until nav.lastIndex, iconSize = 14.dp) {
                                vm.option("nav_order", JSONArray(movedItem(nav, index, 1)).toString())
                            }
                            StakSwitch("$label tab", id in nav, enabled = id !in listOf("home", "settings"), onChange = {
                                vm.option("nav_order", JSONArray(if (it) nav + id else nav - id).toString())
                            })
                        }
                    }
                }
            }
        }
        CapacitorSettingsPanel {
            SectionHeading("Home screen")
            Spacer(Modifier.height(10.dp))
            shelves.forEachIndexed { index, shelf ->
                Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("home-layout-${shelf.id}"), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(shelf.label, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 20.sp, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        StakIconButton(StakIcons.ArrowUp, "Move ${shelf.label} up", enabled = index > 0, iconSize = 13.dp) {
                            vm.option("home_order", JSONArray(movedItem(shelves.map { it.id }, index, -1)).toString())
                        }
                        StakIconButton(StakIcons.ArrowDown, "Move ${shelf.label} down", enabled = index < shelves.lastIndex, iconSize = 13.dp) {
                            vm.option("home_order", JSONArray(movedItem(shelves.map { it.id }, index, 1)).toString())
                        }
                        if (shelf.genre > 0) StakIconButton(StakIcons.Trash2, "Remove ${shelf.label} shelf", iconSize = 13.dp) { remove(shelf) }
                        StakSwitch("Show ${shelf.label}", shelf.enabled(options), onChange = { vm.option("home_${shelf.id}", it.toString()) })
                    }
                    if (index < shelves.lastIndex) HorizontalDivider(color = Rule)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Choice("Genre media type", type, listOf("movie" to "Movies", "series" to "Series"), { type = it; genre = "" }, Modifier.width(105.dp))
                Choice("Genre for new shelf", genre, listOf("" to "Genre row…") + homeGenres(type).map { it.first.toString() to it.second }, { genre = it }, Modifier.weight(1f))
                StakIconButton(StakIcons.Plus, "Add genre shelf", enabled = genre.isNotBlank() && shelves.none { it.id == "genre:$type:$genre" } && shelves.count { it.genre > 0 } < 24,
                    primary = true, iconSize = 16.dp) {
                    val next = shelves.filter { it.genre > 0 }.map { JSONObject().put("type", it.type).put("genre", it.genre) } + JSONObject().put("type", type).put("genre", genre.toInt())
                    vm.option("home_genres", JSONArray(next).toString())
                    genre = ""
                }
            }
            Spacer(Modifier.height(12.dp))
            MotionButton("Reset to default") {
                vm.option("home_order", "[]")
                vm.option("home_genres", "[]")
                homeShelves(emptyMap(), vm.repository.today.year).forEach { vm.option("home_${it.id}", it.defaultOn.toString()) }
            }
        }
    }
}
