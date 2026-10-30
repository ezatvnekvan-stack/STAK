package app.stak.nativeapp.data

import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.nio.file.Files

class VerifiedRatingCacheTest {
    private lateinit var root: File
    @Before fun setup() { root=Files.createTempDirectory("verified-ratings-test").toFile(); PublicMetadataCache.directory=root.resolve("public") }
    @After fun cleanup() { PublicMetadataCache.directory=null; root.walkBottomUp().forEach { it.delete() } }
    @Test fun ratingPersistsWithoutProviderKeyOrRawResponse() {
        val now=System.currentTimeMillis()
        VerifiedRatingCache.write("hashed-scope","tt1234567",now,8.2)
        val cached=VerifiedRatingCache.read("hashed-scope","tt1234567")!!
        assertEquals(8.2,cached.second!!,.001)
        val contents=root.walk().filter { it.isFile }.map { it.readText() }.joinToString()
        assertFalse(contents.contains("apikey"));assertFalse(contents.contains("Authorization"))
        assertTrue(VerifiedRatingCache.read("other-scope","tt1234567")==null)
    }
    @Test fun unratedResultIsCachedButExpiredRatingIsNotUsed() {
        val now=System.currentTimeMillis()
        VerifiedRatingCache.write("scope","tt1234567",now,null)
        assertNotNull(VerifiedRatingCache.read("scope","tt1234567"));assertNull(VerifiedRatingCache.read("scope","tt1234567")!!.second)
        VerifiedRatingCache.write("scope","tt1234567",now-86_400_001L,9.0)
        assertNull(VerifiedRatingCache.read("scope","tt1234567"))
    }
    @Test fun quotaCooldownIsScopedAndSurvivesProcessCacheLoss() {
        val until=System.currentTimeMillis()+86_400_000L
        VerifiedRatingCache.markQuota("scope",until)
        assertEquals(until,VerifiedRatingCache.quotaUntil("scope"));assertEquals(0L,VerifiedRatingCache.quotaUntil("other"))
    }
}
