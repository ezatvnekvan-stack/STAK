package app.stak.nativeapp.data

/**
 * Kept as a thin compatibility shim. New code uses [MediaRepository].
 */
class TitleRepository {
    private val delegate = MediaRepository()
    suspend fun details(type: String, id: String): TitleDetails =
        delegate.details(type, id)
}
