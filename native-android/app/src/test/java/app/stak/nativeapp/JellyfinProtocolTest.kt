package app.stak.nativeapp

import org.junit.Assert.*
import org.junit.Test

class JellyfinProtocolTest {
    @Test fun loginAuthorizationIncludesRequiredClientIdentityBeforeTokenExists() {
        val header = jellyfinAuthorization()
        assertTrue(header.startsWith("MediaBrowser "))
        for (field in listOf("Client=", "Device=", "DeviceId=", "Version=")) assertTrue(header.contains(field))
        assertFalse(header.contains("Token="))
    }

    @Test fun sessionHeaderEscapesQuotedValuesAndRejectsControlCharacters() {
        assertTrue(jellyfinAuthorization("test-token").contains("Token=\"test-token\""))
        assertThrows(IllegalArgumentException::class.java) { jellyfinAuthorization("test\r\nsecret") }
        assertThrows(IllegalArgumentException::class.java) { jellyfinAuthorization("test\tsecret") }
    }

    @Test fun browserLinksNormalizeToServerRootAndPreserveReverseProxyBasePath() {
        assertEquals("https://example.invalid", normalizeJellyfinAddress("https://example.invalid/web/index.html#!/login.html"))
        assertEquals("https://example.invalid/jellyfin", normalizeJellyfinAddress(" https://example.invalid/jellyfin/web/index.html?lang=en#!/login.html "))
        assertEquals("http://192.0.2.1:8096/jellyfin", normalizeJellyfinAddress("http://192.0.2.1:8096/jellyfin/"))
    }

    @Test fun ambiguousAddressesAndCredentialsAreRejected() {
        for (address in listOf("example.invalid", "https://name:secret@example.invalid", "https://example.invalid/jellyfin?api_key=secret", "https://example.invalid/#secret"))
            assertThrows(IllegalArgumentException::class.java) { normalizeJellyfinAddress(address) }
    }
}
