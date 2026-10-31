package app.stak.nativeapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import org.json.*

fun Repository.hashListKey(): String {
    val scope = accountScope
    return "hash_lists_$scope"
}

fun Repository.hashLists(): List<JSONObject> =
    runCatching { JSONArray(preferences.getString(hashListKey(), "[]")).objects() }
        .getOrDefault(emptyList())

fun Repository.removeHashList(id: String) {
    preferences
        .edit()
        .putString(
            hashListKey(),
            JSONArray(hashLists().filter { it.optString("id") != id }).toString(),
        )
        .apply()
}

fun Repository.saveHashList(title: String, torrents: List<Torrent>): JSONObject {
    require(torrents.size in 1..1000) { "Choose 1–1000 torrents to save a list." }
    val valid = torrents.filter { Regex("[a-fA-F0-9]{40}|[a-zA-Z2-7]{32}").matches(it.hash) }
    require(valid.isNotEmpty()) { "No valid torrent hashes selected." }
    val list =
        JSONObject()
            .put(
                "id",
                "local-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(8)}",
            )
            .put("title", title.ifBlank { "Hash list · ${valid.size} torrents" }.take(160))
            .put(
                "items",
                JSONArray(
                    valid.map {
                        JSONObject()
                            .put("hash", it.hash.lowercase())
                            .put("name", it.name.take(500))
                            .put("size", it.size)
                    }
                ),
            )
            .put("created_at", Instant.now().toString())
            .put("local_only", true)
    preferences
        .edit()
        .putString(hashListKey(), JSONArray(listOf(list) + hashLists()).toString())
        .apply()
    return list
}

fun magnetList(list: JSONObject): String =
    "# ${list.optString("title", "STAK hash list")}\n" +
        list
            .optJSONArray("items")
            .objects()
            .filter { Regex("[a-fA-F0-9]{40}|[a-zA-Z2-7]{32}").matches(it.optString("hash")) }
            .joinToString("\n") {
                "magnet:?xt=urn:btih:${it.optString("hash")}&dn=${android.net.Uri.encode(it.optString("name"))}"
            }

private fun copyList(context: Context, list: JSONObject) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
        ClipData.newPlainText(list.optString("title"), magnetList(list))
    )
}

private fun exportList(context: Context, list: JSONObject) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, list.optString("title"))
                .putExtra(Intent.EXTRA_TEXT, magnetList(list)),
            null,
        )
    )
}

@Composable
fun HashListsScreen(vm: StakViewModel) {
    var lists by remember { mutableStateOf(vm.repository.hashLists()) }
    var limits by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val navigate = LocalNavigate.current
    LazyColumn(
        Modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PageHeading("Share", eyebrow = "Hash lists") }
        item {
            Column(Modifier.clickable { limits = !limits }) {
                Text("Sharing limits", fontSize = 12.sp, lineHeight = 18.sp, color = Muted)
                if (limits)
                    Text(
                        "Lists stay on this device. Export magnet links to share; public hosted links require a server.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Muted,
                    )
            }
        }
        item { Label("My lists") }
        if (vm.repository.credentials.get("torbox_token").isBlank())
            item {
                OriginalEmpty(
                    "No key",
                    "Your lists are tied to your TorBox key. Shared links can be opened without a key.",
                    "Add key",
                ) {
                    navigate("settings?section=account")
                }
            }
        else if (lists.isEmpty())
            item {
                OriginalEmpty(
                    "No lists",
                    "Select torrents in the Library, then tap the share button.",
                )
            }
        items(lists, key = { it.optString("id") }) { list ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .74f),
                border = BorderStroke(1.dp, Rule),
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        Modifier.weight(1f).clickable {
                            navigate("share/${android.net.Uri.encode(list.optString("id"))}")
                        },
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            list.optString("title"),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${list.optJSONArray("items")?.length() ?: 0} torrents · ${list.optString("created_at").take(10)}",
                            fontFamily = MonoFont,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            color = Muted,
                        )
                    }
                    IconButton({ exportList(context, list) }) {
                        Icon(StakIcons.Share2, "Export magnets", Modifier.size(15.dp))
                    }
                    IconButton({
                        vm.repository.removeHashList(list.optString("id"))
                        lists = vm.repository.hashLists()
                    }) {
                        Icon(
                            StakIcons.Trash2,
                            "Delete list",
                            Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HashListScreen(vm: StakViewModel, id: String) {
    val list = remember(id) { vm.repository.hashLists().firstOrNull { it.optString("id") == id } }
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    if (list == null) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
            OriginalEmpty(
                "Not found",
                "List not found on this device. Offline lists are not public links.",
            )
        }
        return
    }
    val items = list.optJSONArray("items").objects()
    LazyColumn(
        Modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeading(
                list.optString("title"),
                eyebrow = "${items.size} torrents · ${list.optString("created_at").take(10)}",
            )
        }
        item {
            MotionButton("Add all", enabled = !busy, icon = StakIcons.Plus) {
                vm.operation {
                    busy = true
                    var added = 0
                    try {
                        val existing = vm.repository.torrents().map { it.hash.lowercase() }.toSet()
                        items
                            .filter { it.optString("hash").lowercase() !in existing }
                            .forEach {
                                vm.repository.addMagnet(
                                    "magnet:?xt=urn:btih:${it.optString("hash")}"
                                )
                                added++
                            }
                        vm.messages.emit("$added torrent(s) added")
                    } finally {
                        busy = false
                    }
                }
            }
        }
        items(items, key = { it.optString("hash") }) { item ->
            Row(
                Modifier.fillMaxWidth()
                    .border(1.dp, Rule, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.optString("name", item.optString("hash")),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Text(
                        "${item.optLong("size") / 1_048_576} MB",
                        fontFamily = MonoFont,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        color = Muted,
                    )
                }
                IconButton(
                    {
                        vm.operation {
                            vm.repository.addMagnet("magnet:?xt=urn:btih:${item.optString("hash")}")
                        }
                    },
                    enabled = !busy,
                ) {
                    Icon(StakIcons.Plus, "Add torrent", Modifier.size(18.dp))
                }
                IconButton({
                    copyList(
                        context,
                        JSONObject()
                            .put("title", list.optString("title"))
                            .put("items", JSONArray().put(item)),
                    )
                }) {
                    Icon(StakIcons.Copy, "Copy magnet", Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
fun SaveHashListDialog(vm: StakViewModel, torrents: List<Torrent>, dismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var created by remember { mutableStateOf<JSONObject?>(null) }
    val context = LocalContext.current
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(12.dp).fillMaxWidth().widthIn(max = 520.dp),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Rule),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row {
                    Text(
                        if (created == null) "Hash list" else "Done",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    IconButton(dismiss) { Icon(StakIcons.X, "Close dialog", Modifier.size(18.dp)) }
                }
                Text(
                    "${torrents.size} selected torrents — save a list on this device.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Muted,
                )
                val list = created
                if (list == null) {
                    OriginalField(title, { title = it }, "List name (optional)")
                    MotionButton("Save list", Modifier.fillMaxWidth(), primary = true) {
                        vm.operation { created = vm.repository.saveHashList(title, torrents) }
                    }
                } else {
                    Text(
                        "Saved locally — export the magnet links to share",
                        fontFamily = MonoFont,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MotionButton("Copy magnets", Modifier.weight(1f), icon = StakIcons.Copy) {
                            copyList(context, list)
                        }
                        MotionButton(
                            "Export magnets",
                            Modifier.weight(1f),
                            icon = StakIcons.Share2,
                        ) {
                            exportList(context, list)
                        }
                    }
                }
            }
        }
    }
}
