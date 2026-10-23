package app.stak.nativeapp

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
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

    CapacitorSettingsPanel(Modifier.testTag("sources-settings")) {
        SectionHeading("Indexers")
        Spacer(Modifier.height(10.dp))
        SourceSettingRow(
            "Torrentio", "built-in · torrentio.strem.fun",
            LocalOptions.current["torrentio"] != "false", "source-torrentio",
            onChange = { vm.option("torrentio", it.toString()) },
        )
        indexers.forEachIndexed { index, item ->
            SourceSettingRow(item.name,
                "${item.kind} · ${indexerUrl(item.url).let { "${it.scheme}://${it.host}${if (it.port !in listOf(80, 443)) ":${it.port}" else ""}" }}",
                item.enabled, "indexer-row-$index", onChange = { vm.indexers.toggle(item.id, it) }, onRemove = { removing = item })
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().cssDashedBorder(if (MaterialTheme.colorScheme.background.red < .5f) androidx.compose.ui.graphics.Color(0xff2a2a2a) else androidx.compose.ui.graphics.Color(0xffcbcbc7))
            .padding(13.dp).testTag("indexer-form"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((id, label) in listOf("torznab" to "Torznab", "stremio" to "Stremio addon")) {
                    MotionButton(label, tone = if (kind == id) ButtonTone.Selection else ButtonTone.Chip, enabled = busy.isBlank()) {
                        kind = id
                        feedback = ""
                    }
                }
            }
            OriginalField(name, { name = it }, "Name (e.g. Prowlarr)", Modifier.testTag("indexer-name-input"),
                enabled = busy.isBlank(), accessibilityLabel = "Source name")
            OriginalField(url, { url = it; feedback = "" }, "URL", Modifier.testTag("indexer-url-input"),
                enabled = busy.isBlank(), accessibilityLabel = "Endpoint URL")
            val hint = if (kind == "torznab") "Jackett: http://IP:9117/api/v2.0/indexers/all/results/torznab · Prowlarr: http://IP:9696/1/api"
                else "Stremio addon URL, e.g. https://torrentio.strem.fun/providers=yts,eztv/manifest.json"
            // CSS break-all permits a wrap between any two characters in these examples.
            Text(hint.toList().joinToString("\u200b"),
                Modifier.padding(horizontal = 8.dp).clearAndSetSemantics { text = AnnotatedString(hint) },
                fontFamily = MonoFont, fontSize = 10.sp, lineHeight = 16.25.sp, color = Muted)
            AnimatedVisibility(kind == "torznab", enter = fadeIn(motionSpec(motion.reduced, 160)) + expandVertically(motionSpec(motion.reduced, 220)),
                exit = fadeOut(motionSpec(motion.reduced, 100)) + shrinkVertically(motionSpec(motion.reduced, 180))) {
                OriginalField(apiKey, { apiKey = it }, "API key", Modifier.testTag("indexer-apikey-input"),
                    secret = true, enabled = busy.isBlank(), accessibilityLabel = "Indexer API key")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MotionButton(if (busy == "test") "Testing…" else "Test", Modifier.weight(1f).heightIn(min = 46.dp).testTag("indexer-test-button"),
                    enabled = busy.isBlank() && url.isNotBlank()) { run("test") }
                MotionButton(if (busy == "add") "Adding…" else "Add", Modifier.weight(1f).testTag("indexer-add-button"), tone = ButtonTone.Accent,
                    enabled = busy.isBlank() && url.isNotBlank()) { run("add") }
            }
            if (busy.isNotBlank()) DotStatus(label = if (busy == "test") "Testing source" else "Saving source")
            if (feedback.isNotBlank()) Text(feedback, Modifier.testTag("indexer-feedback"), fontSize = 12.sp, lineHeight = 18.sp,
                color = if (failed) MaterialTheme.colorScheme.error else Muted)
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
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 13.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(
                title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                fontFamily = MonoFont,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = Muted,
                maxLines = 1,
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
