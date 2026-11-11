package app.stak.nativeapp.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

import com.google.gson.JsonObject

/** Cinematic surfaces use independent artwork; never replace the catalogue/grid poster.
 * A neutral language tag is the provider's best textless hint, not OCR verification.
 */
internal object CinematicArtwork {
    fun original(url: String?): String? = url?.replace(
        Regex("^https://image\\.tmdb\\.org/t/p/(?:w\\d+|h\\d+|original)/"),
        "https://image.tmdb.org/t/p/original/")

    private fun best(rows: List<JsonObject>): JsonObject? = rows
        .filter { it.text("file_path")?.startsWith('/') == true }
        .maxWithOrNull(compareBy<JsonObject>(
            { (it.number("width") ?: 0.0) * (it.number("height") ?: 0.0) },
            { it.number("vote_average") ?: 0.0 }, { it.number("vote_count") ?: 0.0 }))

    private fun image(row: JsonObject?): String? = row?.text("file_path")?.let {
        "https://image.tmdb.org/t/p/original$it"
    }

    fun choose(images: JsonObject?, fallback: String?): String? {
        // Portrait art suits the tall Home/Details canvas without enlarging a landscape thumbnail.
        val posters = images?.objects("posters").orEmpty().filter { it.text("iso_639_1").isNullOrBlank() }
        val backdrops = images?.objects("backdrops").orEmpty().filter { it.text("iso_639_1").isNullOrBlank() }
        return image(best(posters)) ?: image(best(backdrops)) ?: original(fallback)
    }

    fun logo(images: JsonObject?, fallback: String? = null): String? {
        val logos = images?.objects("logos").orEmpty()
        return image(best(logos.filter { it.text("iso_639_1") == "en" }))
            ?: image(best(logos.filter { it.text("iso_639_1").isNullOrBlank() })) ?: original(fallback)
    }

    fun englishBackdrop(images: JsonObject?): String? = image(best(
        images?.objects("backdrops").orEmpty().filter { it.text("iso_639_1") == "en" }))

    fun neutralBackdrop(images: JsonObject?,fallback: String?): String? = image(best(
        images?.objects("backdrops").orEmpty().filter { it.text("iso_639_1").isNullOrBlank() })) ?: original(fallback)
}

internal data class ContinueArtwork(val image:String?,val logo:String?=null,val embeddedTitle:Boolean=false)

/** Continue Watching alone replaces an episode still with its explicit parent show's art. */
internal val MediaItem.continueBackground: String?
    get() {
        val image = CinematicArtwork.original(background ?: poster)
        val parent = jellyfinSeriesId?.takeIf { isSeries && it.matches(Regex("[A-Za-z0-9-]+")) }
            ?: return image
        val url = image?.toHttpUrlOrNull() ?: return image
        val parts = url.pathSegments
        val index = parts.indexOfLast { it.equals("Items", ignoreCase = true) }
        if (index < 0 || parts.getOrNull(index + 1) != jellyfinId ||
            parts.drop(index + 2) != listOf("Images", "Primary")) return image
        return url.newBuilder().setPathSegment(index + 1, parent)
            .setPathSegment(index + 3, "Backdrop").addPathSegment("0")
            .apply { listOf("maxWidth", "maxHeight", "width", "height", "quality", "tag")
                .forEach { removeAllQueryParameters(it) } }.build().toString()
    }

/** Never reuse a resized TMDb cache entry on a cinematic surface. */
internal val MediaItem.cinematicImage: String?
    get() = CinematicArtwork.original(cinematicArtwork ?: background ?: poster)

internal val MediaItem.cinematicImageFallback: String?
    get() = CinematicArtwork.original(background ?: poster)?.takeIf { it != cinematicImage }
