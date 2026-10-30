package app.stak.nativeapp.data

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PreferencesAndCacheTest {
    @Test fun navigationMigratesHistoryAndPreservesMandatoryDestinations() {
        assertEquals(listOf("tracking", "torbox", "home", "account"), NavigationPreferences.normalize(listOf("history", "progress", "torbox", "unknown")))
    }

    @Test fun corruptNavigationKeepsAppNavigable() {
        assertEquals(NavigationPreferences.defaults, NavigationPreferences.read("[invalid"))
        assertEquals(listOf("home", "account"), NavigationPreferences.read("[]"))
    }

    @Test fun cacheOnlyStoresPublicCinemetaAndRejectsAccountUrls() {
        val directory = Files.createTempDirectory("stak-public-cache").toFile()
        try {
            PublicMetadataCache.directory = directory
            val value = JsonParser.parseString("""{"metas":[]}""").asJsonObject
            PublicMetadataCache.write("https://api.torbox.app/v1/api/user/me", value)
            PublicMetadataCache.write("https://api.themoviedb.org/3/movie/popular?api_key=test", value)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            val url = "https://v3-cinemeta.strem.io/catalog/movie/top.json"
            PublicMetadataCache.write(url, value)
            assertEquals(value, PublicMetadataCache.read(url))
            assertEquals(1, directory.listFiles().orEmpty().size)
            directory.listFiles()!!.single().setLastModified(System.currentTimeMillis() - 86_400_001)
            assertNull(PublicMetadataCache.read(url))
        } finally {
            PublicMetadataCache.directory = null
            directory.listFiles().orEmpty().forEach { it.delete() }
            directory.delete()
        }
    }
}
