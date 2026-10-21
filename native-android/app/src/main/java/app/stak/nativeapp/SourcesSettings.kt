package app.stak.nativeapp

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException

@Composable
fun SourcesSettings(vm: StakViewModel) {
    val indexers by vm.indexers.items.collectAsStateWithLifecycle()
    var kind by remember { mutableStateOf("torznab") }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Indexer?>(null) }
    val motion = LocalStakMotion.current

    fun draft() = Indexer(name = name, kind = kind, url = url, apiKey = apiKey)
    fun run(action: String) {
        vm.operation {
            busy = action
            feedback = ""
            try {
                if (action == "test") feedback = "Connected: ${vm.repository.testIndexer(draft())}"
                else {
                    vm.indexers.add(draft())
                    name = ""
                    url = ""
                    apiKey = ""
                    feedback = "Indexer added"
                }
                failed = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                failed = true
                feedback = failure.message ?: "Could not save this indexer."
            } finally {
                busy = ""
            }
        }
    }

    Column(
        Modifier.testTag("sources-settings"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .8f),
            border = BorderStroke(1.dp, Rule),
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "INDEXERS",
                        Modifier.weight(1f),
                        fontFamily = SectionFont,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        letterSpacing = .7.sp,
                    )
                    Label(
                        "${indexers.count { it.enabled } + if(LocalOptions.current["torrentio"] != "false") 1 else 0} ACTIVE"
                    )
                }
                Spacer(Modifier.height(12.dp))
                SourceSettingRow(
                    "Torrentio",
                    "built-in · torrentio.strem.fun",
                    LocalOptions.current["torrentio"] != "false",
                    "source-torrentio",
                    onChange = { vm.option("torrentio", it.toString()) },
                )
                indexers.forEachIndexed { index, item ->
                    HorizontalDivider(color = Rule)
                    SourceSettingRow(
                        item.name,
                        "${item.kind} · ${indexerUrl(item.url).let { "${it.scheme}://${it.host}${if(it.port !in listOf(80,443)) ":${it.port}" else ""}" }}",
                        item.enabled,
                        "indexer-row-$index",
                        onChange = { vm.indexers.toggle(item.id, it) },
                        onRemove = { removing = item },
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .8f),
            border = BorderStroke(1.dp, Rule),
        ) {
            Column(
                Modifier.padding(18.dp).testTag("indexer-form"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Label("Add a source")
                Text(
                    "Your sources. Your library.",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-.4).sp,
                )
                Text(
                    "Connect Jackett, Prowlarr or a Stremio addon. STAK searches enabled sources together.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Muted,
                )
                MotionTabs(
                    listOf("Torznab", "Stremio addon"),
                    if (kind == "torznab") 0 else 1,
                    height = 46.dp,
                ) {
                    if (busy.isBlank()) {
                        kind = if (it == 0) "torznab" else "stremio"
                        feedback = ""
                    }
                }
                OriginalField(
                    name,
                    { name = it },
                    "Source name",
                    Modifier.testTag("indexer-name-input"),
                    enabled = busy.isBlank(),
                )
                OriginalField(
                    url,
                    {
                        url = it
                        feedback = ""
                    },
                    "Endpoint URL",
                    Modifier.testTag("indexer-url-input"),
                    enabled = busy.isBlank(),
                )
                AnimatedVisibility(
                    kind == "torznab",
                    enter =
                        fadeIn(motionSpec(motion.reduced, 160)) +
                            expandVertically(motionSpec(motion.reduced, 220)),
                    exit =
                        fadeOut(motionSpec(motion.reduced, 100)) +
                            shrinkVertically(motionSpec(motion.reduced, 180)),
                ) {
                    OriginalField(
                        apiKey,
                        { apiKey = it },
                        "Indexer API key",
                        Modifier.testTag("indexer-apikey-input"),
                        secret = true,
                        reveal = true,
                        enabled = busy.isBlank(),
                    )
                }
                Text(
                    if (kind == "torznab")
                        "Jackett: http://IP:9117/api/v2.0/indexers/all/results/torznab\nProwlarr: http://IP:9696/1/api"
                    else "Use the addon’s manifest.json URL, including its configuration path.",
                    fontFamily = MonoFont,
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                    color = Muted,
                )
                if (url.trim().startsWith("http://", true))
                    Text(
                        "HTTP is suitable for a trusted local network. Use HTTPS for remote sources.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Muted,
                    )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MotionButton(
                        if (busy == "test") "Testing…" else "Test",
                        Modifier.weight(1f).testTag("indexer-test-button"),
                        enabled = busy.isBlank() && url.isNotBlank(),
                    ) {
                        run("test")
                    }
                    MotionButton(
                        if (busy == "add") "Adding…" else "Add source",
                        Modifier.weight(1f).testTag("indexer-add-button"),
                        tone = ButtonTone.Accent,
                        enabled = busy.isBlank() && url.isNotBlank(),
                    ) {
                        run("add")
                    }
                }
                if (busy.isNotBlank())
                    DotStatus(label = if (busy == "test") "Testing source" else "Saving source")
                if (feedback.isNotBlank())
                    Text(
                        feedback,
                        Modifier.testTag("indexer-feedback"),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = if (failed) MaterialTheme.colorScheme.error else Muted,
                    )
                Text(
                    "Saved on this device, encrypted with Android Keystore.",
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = Muted,
                )
            }
        }
    }
    removing?.let { item ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${item.name}?") },
            text = {
                Text("This removes the source from STAK. Your server and library stay intact.")
            },
            confirmButton = {
                TextButton({
                    vm.indexers.remove(item.id)
                    removing = null
                }) {
                    Text("Remove")
                }
            },
            dismissButton = { TextButton({ removing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SourceSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                fontFamily = MonoFont,
                fontSize = 10.sp,
                lineHeight = 16.sp,
                color = Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onRemove != null)
            IconButton(onRemove, Modifier.size(44.dp).testTag("$tag-delete")) {
                Icon(
                    StakIcons.Trash2,
                    "Remove $title",
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        StakSwitch(title, checked, Modifier.testTag("$tag-toggle"), onChange = onChange)
    }
}
