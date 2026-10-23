package app.stak.nativeapp

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException

@Composable
fun TorBoxSettings(vm: StakViewModel) {
    val account by vm.torboxAccount.state.collectAsStateWithLifecycle()
    val savedKey = vm.repository.credentials.get("torbox_token")
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column {
        if (savedKey.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (account.error.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .74f),
                        border = BorderStroke(1.dp, Color(0xffd71921).copy(alpha = .6f)),
                    ) {
                        Text(
                            "Key error: ${account.error}",
                            Modifier.fillMaxWidth().padding(17.dp).testTag("account-key-error"),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = Color(0xffff4d55),
                        )
                    }
                } else {
                    val data = account.data
                    val stats =
                        listOf(
                            Triple("E-mail", (data?.opt("email") as? String) ?: "…", "email"),
                            Triple(
                                "Plan",
                                if (data == null) "…" else accountPlan(data.opt("plan")),
                                "plan",
                            ),
                            Triple(
                                "Expires",
                                accountDate(data?.opt("premium_expires_at")),
                                "expiry",
                            ),
                            Triple(
                                "Downloaded",
                                accountBytes(data?.opt("total_downloaded")),
                                "downloaded",
                            ),
                        )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        stats.chunked(2).forEach { row ->
                            Row(
                                Modifier.height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                row.forEach { stat ->
                                    AccountStat(stat, Modifier.weight(1f).fillMaxHeight())
                                }
                            }
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .74f),
                    border = BorderStroke(1.dp, Rule),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(17.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            AccountLabel("Active key")
                            Text(
                                savedKey.take(4) + "••••••••" + savedKey.takeLast(4),
                                Modifier.testTag("account-masked-key"),
                                fontFamily = MonoFont,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                letterSpacing = 0.sp,
                            )
                        }
                        MotionButton(
                            "Sign out",
                            Modifier.testTag("torbox-logout-button"),
                            icon = StakIcons.LogOut,
                            enabled = !busy,
                            tone = ButtonTone.Destructive,
                        ) {
                            vm.torboxAccount.signOut()
                            vm.operation { vm.messages.emit("Signed out") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        CapacitorSettingsPanel {
            Text(
                if (savedKey.isBlank()) "CONNECT" else "REPLACE KEY",
                fontFamily = SectionFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                lineHeight = 21.75.sp,
                letterSpacing = .675.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Stored on this device; encrypted with Android Keystore.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = Muted,
            )
            Spacer(Modifier.height(16.dp))
            OriginalField(
                input,
                { input = it },
                "TorBox API key",
                Modifier.testTag("torbox-api-key-input"),
                secret = true,
                reveal = true,
                enabled = !busy,
                visibilityLabel = "Toggle key visibility",
            )
            Spacer(Modifier.height(12.dp))
            MotionButton(
                if (busy) "Verifying…" else "Save & verify",
                Modifier.fillMaxWidth().testTag("torbox-save-key-button"),
                enabled = !busy && input.isNotBlank(),
                tone = ButtonTone.Accent,
            ) {
                vm.operation {
                    busy = true
                    try {
                        vm.torboxAccount.connect(input)
                        input = ""
                        vm.messages.emit("TorBox connected")
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        vm.messages.emit(
                            "Invalid key: ${failure.message ?: "Could not verify this key."}"
                        )
                    } finally {
                        busy = false
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.heightIn(min = 44.dp)
                    .clickable { openExternal(context, "https://torbox.app/settings") }
                    .testTag("torbox-get-key-link"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "GET A TORBOX KEY",
                    fontFamily = MonoFont,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 1.1.sp,
                    color = Muted,
                )
                Icon(StakIcons.ExternalLink, null, Modifier.size(12.dp), tint = Muted)
            }
        }
    }
}

@Composable
private fun AccountStat(
    stat: Triple<String, String, String>,
    modifier: Modifier,
) {
    Surface(
        modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .8f),
        border = BorderStroke(1.dp, Rule),
    ) {
        Column(Modifier.padding(17.dp)) {
            AccountLabel(stat.first)
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    stat.second,
                    Modifier.testTag("account-${stat.third}"),
                    fontFamily = MonoFont,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    letterSpacing = 0.sp,
                )
            }
        }
    }
}

@Composable
private fun AccountLabel(value: String) {
    Text(
        value.uppercase(java.util.Locale.ROOT),
        Modifier.padding(bottom = 10.dp).height(15.dp),
        fontFamily = MonoFont,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        letterSpacing = 1.1.sp,
        color = Muted,
    )
}
