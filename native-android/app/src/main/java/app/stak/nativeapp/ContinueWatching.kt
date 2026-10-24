package app.stak.nativeapp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.math.ceil

data class ContinueFeed(val items: List<Media>, val warning: String = "")

fun continueRemainingMinutes(media: Media): Long? {
    val duration = media.runtime.removeSuffix("m").toLongOrNull() ?: return null
    if (duration <= 0) return null
    val played = if (media.playbackTicks > 0) media.playbackTicks / 600_000_000.0 else duration * media.progress.toDouble() / 100
    return ceil(duration - played).toLong().coerceAtLeast(0)
}

fun combineContinueItems(resume: List<Media>, next: List<Media>): List<Media> {
    val resumedSeries = resume.map { it.seriesName }.filter { it.isNotBlank() }.toSet()
    return (resume + next.filter { it.seriesName.isBlank() || it.seriesName !in resumedSeries })
        .distinctBy { it.jellyfinId.ifBlank { it.key } }
}

suspend fun Repository.continueFeed(): ContinueFeed = supervisorScope {
    suspend fun outcome(load: suspend () -> List<Media>): Result<List<Media>> = try { Result.success(load()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { Result.failure(failure) }
    val resume = async { outcome { library(true) } }
    val next = async { outcome {
        val session = jellyfinConnection()
        val url = (session.url + "/Shows/NextUp").toHttpUrl().newBuilder()
            .addQueryParameter("UserId", session.user).addQueryParameter("Limit", "20")
            .addQueryParameter("Fields", "Overview,ProviderIds,Genres").build()
        val root = request(url.toString(), session.token, jellyfin = true)
        requireSameConnection(session)
        root.optJSONArray("Items").objects().map { remember(jellyfinMedia(it, session)) }
    } }
    val first = resume.await(); val second = next.await()
    if (first.isFailure && second.isFailure) throw requireNotNull(first.exceptionOrNull())
    ContinueFeed(combineContinueItems(first.getOrDefault(emptyList()), second.getOrDefault(emptyList())),
        if (first.isFailure || second.isFailure) "Some Continue Watching results could not load." else "")
}

@Composable
fun ContinueWatchingRail(media: List<Media>, onOpen: (Media, String) -> Unit) {
    val navigate = LocalNavigate.current
    Column(Modifier.padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionHeading("Continue watching", Modifier.weight(1f))
            MotionButton("Jellyfin →", tone = ButtonTone.Link, horizontalPadding = 0.dp) { navigate("library") }
        }
        BoxWithConstraints {
            val width = (maxWidth - 12.dp) / 2
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(media, key = { it.jellyfinId.ifBlank { it.key } }) { title ->
                    val resume = title.playbackTicks > 0 || title.progress > 0
                    val progress = animateFloatAsState(title.progress / 100, motionSpec(LocalStakMotion.current.reduced), label = "Resume progress")
                    GlassPanel(Modifier.width(width).clip(RoundedCornerShape(18.dp)).clickable { onOpen(title, "home-continue") }
                        .testTag("continue-${title.key}"), radius = 18.dp) {
                        Column {
                            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                                Artwork(title, Modifier.fillMaxSize(), backdrop = true)
                                Text(if (resume) "RESUME" else "NEXT UP", Modifier.align(Alignment.BottomStart)
                                    .padding(8.dp).background(Color.Black.copy(alpha = .76f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 9.dp, vertical = 5.dp), color = Color.White, fontFamily = MonoFont, fontSize = 8.sp)
                            }
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(title.seriesName.ifBlank { title.name }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (resume) continueRemainingMinutes(title)?.let { "$it min left" } ?: "Resume watching"
                                    else if (title.episodeSeason > 0) "S${title.episodeSeason} E${title.episodeNumber}" else "Next up",
                                    fontFamily = MonoFont, fontSize = 11.sp, color = Muted)
                                if (resume) {
                                    Spacer(Modifier.height(4.dp))
                                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(Rule)) {
                                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value.coerceIn(0f, 1f))
                                            .background(MaterialTheme.colorScheme.onSurface))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
