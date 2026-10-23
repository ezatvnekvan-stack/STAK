package app.stak.nativeapp

import org.junit.Assert.*
import org.junit.Test

class SnapshotReadCacheTest {
    @Test fun unchangedReadsAvoidRepeatedDecryptionButChangesAndDeletesInvalidate() {
        val cache = SnapshotReadCache()
        var decodes = 0
        repeat(100) { assertEquals("first", cache.read("user:a", "cipher1") { decodes++; "first" }) }
        assertEquals(1, decodes)
        assertEquals("second", cache.read("user:a", "cipher2") { decodes++; "second" })
        assertEquals("", cache.read("user:a", null) { error("Deleted data must never decode") })
        assertEquals("fresh", cache.read("user:a", "cipher2") { decodes++; "fresh" })
        assertEquals(3, decodes)
    }
    @Test fun scopedReadsDoNotCrossAccountsAndFailuresRemainRetryable() {
        val cache = SnapshotReadCache()
        assertEquals("A", cache.read("user:a", "cipher") { "A" })
        assertEquals("B", cache.read("user:b", "cipher") { "B" })
        assertTrue(runCatching { cache.read("user:c", "cipher") { error("Locked keystore") } }.isFailure)
        assertEquals("retried", cache.read("user:c", "cipher") { "retried" })
        cache.clear()
        assertEquals("new", cache.read("user:a", "cipher") { "new" })
    }
}
