package app.stak.nativeapp

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class ArtworkHeaderTest {
    @Test
    fun redirectedArtworkNeverCarriesJellyfinCredentialsOutsideItsServer() {
        val server = "https://media.example/jellyfin"
        assertTrue(
            allowsPrivateArtworkHeader(
                "https://media.example/jellyfin/Items/1/Images/Primary".toHttpUrl(),
                server,
            )
        )
        for (url in
            listOf(
                "https://other.example/jellyfin/Items/1",
                "https://media.example.evil/jellyfin/Items/1",
                "https://media.example/jellyfin-other/Items/1",
                "http://media.example/jellyfin/Items/1",
                "https://media.example:8443/jellyfin/Items/1",
            )) {
            assertFalse(allowsPrivateArtworkHeader(url.toHttpUrl(), server))
        }
    }
}
