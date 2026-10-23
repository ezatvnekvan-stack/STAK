package app.stak.nativeapp

import androidx.compose.runtime.staticCompositionLocalOf

data class PosterState(val watchlisted: Set<String> = emptySet(), val watched: Set<String> = emptySet())
val LocalPosterState = staticCompositionLocalOf { PosterState() }
val LocalStakViewModel = staticCompositionLocalOf<StakViewModel?> { null }

/** Decode saved JSON once when collections change, rather than once per composed poster. */
fun posterState(saved: List<SavedMedia>) = PosterState(
    saved.filter { it.collection == "watchlist" }.map { it.mediaKey }.toSet(),
    saved.filter { it.collection == "tracking" && org.json.JSONObject(it.json).optBoolean("played") }
        .map { it.mediaKey }.toSet(),
)
