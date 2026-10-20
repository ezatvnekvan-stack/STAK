package app.stak.nativeapp

/** Original trackingModel.js counts known title lengths, never an assumed movie length. */
data class ViewingSummary(
    val movies: Int,
    val episodes: Int,
    val knownRuntime: Int,
    val total: Int,
    val hours: Double,
) {
    val runtimeLabel
        get() =
            when {
                total == 0 -> "Est. runtime"
                knownRuntime == 0 -> "Runtime unknown"
                knownRuntime < total -> "Known runtime"
                else -> "Est. runtime"
            }

    val runtimeValue
        get() =
            when {
                total == 0 -> "0h"
                knownRuntime == 0 -> "—"
                else -> "%.1fh".format(java.util.Locale.US, hours)
            }
}

fun runtimeHours(value: String): Double? {
    val text = value.trim().lowercase(java.util.Locale.ROOT)
    val hours =
        Regex("([0-9]+(?:\\.[0-9]+)?)\\s*h").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
    val minutes =
        Regex("([0-9]+(?:\\.[0-9]+)?)\\s*m").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
    val result =
        if (hours != null || minutes != null) (hours ?: 0.0) + (minutes ?: 0.0) / 60.0
        else text.toDoubleOrNull()?.div(60)
    return result?.takeIf { it.isFinite() && it > 0 }
}

fun viewingSummary(movies: List<Media>, episodeTitles: List<Media?>): ViewingSummary {
    val durations =
        movies.map { runtimeHours(it.runtime) } +
            episodeTitles.map { it?.runtime?.let(::runtimeHours) }
    return ViewingSummary(
        movies.size,
        episodeTitles.size,
        durations.count { it != null },
        durations.size,
        durations.filterNotNull().sum(),
    )
}
