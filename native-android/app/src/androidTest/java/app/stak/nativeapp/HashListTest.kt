package app.stak.nativeapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HashListTest {
    @Test
    fun localListsStayWithTheirAccountAndExportMagnets() {
        val repository = Repository(InstrumentationRegistry.getInstrumentation().targetContext)
        val credentials = repository.credentials
        val original = credentials.get("torbox_token")
        val keyA = "test-only-hash-list-account-a"
        val keyB = "test-only-hash-list-account-b"
        try {
            credentials.set("torbox_token", keyA)
            repository.hashLists().forEach { repository.removeHashList(it.optString("id")) }
            val list =
                repository.saveHashList(
                    "Test-only list",
                    listOf(
                        Torrent(1, "Test video", 1f, "Completed", emptyList(), "a".repeat(40), 1000)
                    ),
                )
            assertEquals(1, repository.hashLists().size)
            val exported = magnetList(list)
            assertTrue(exported.startsWith("# Test-only list\nmagnet:?xt=urn:btih:"))
            assertTrue(exported.contains("&dn=Test%20video"))
            assertFalse(exported.contains(keyA))
            credentials.set("torbox_token", keyB)
            assertTrue(repository.hashLists().isEmpty())
            credentials.set("torbox_token", keyA)
            assertEquals(list.optString("id"), repository.hashLists().single().optString("id"))
            repository.removeHashList(list.optString("id"))
            assertTrue(repository.hashLists().isEmpty())
        } finally {
            credentials.set("torbox_token", original)
            repository.db.close()
        }
    }
}
