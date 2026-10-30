package app.stak.nativeapp.data

/**
 * Kept as a thin compatibility shim. New code uses [MediaRepository].
 */
class DiscoverRepository {
    private val delegate = MediaRepository()
    suspend fun discover(type: String): List<MediaItem> =
        delegate.discover(type, "trending", "", 1).items
}
