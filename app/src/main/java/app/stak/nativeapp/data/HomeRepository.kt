package app.stak.nativeapp.data

/**
 * Kept as a thin compatibility shim. New code uses [MediaRepository].
 */
class HomeRepository {
    private val delegate = MediaRepository()
    suspend fun hero(): List<MediaItem> = delegate.home().hero
}
