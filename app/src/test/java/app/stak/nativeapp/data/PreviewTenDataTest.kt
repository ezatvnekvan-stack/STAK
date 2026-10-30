package app.stak.nativeapp.data

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PreviewTenDataTest {
    private fun json(raw:String)=JsonParser.parseString(raw)
    @Test fun fullTorrentSizeUsesCacheMetadataRatherThanTorrentioVideoSize() {
        val hash="a".repeat(40)
        val data=json("""{"$hash":{"size":32212254720,"files":[{"size":3221225472}]}}""")
        assertEquals(32212254720L,SourceRepository.confirmedTorrentSizes(data,setOf(hash))[hash])
    }
    @Test fun completeFileListsCanBeSummedButPartialOrUnrequestedDataIsRejected() {
        val hash="a".repeat(40);val other="b".repeat(40)
        val complete=json("""{"$hash":{"files":[{"size":100},{"size":200}]},"$other":{"size":999}}""")
        assertEquals(mapOf(hash to 300L),SourceRepository.confirmedTorrentSizes(complete,setOf(hash)))
        assertTrue(SourceRepository.confirmedTorrentSizes(json("""{"$hash":{"files":[{"size":100},{}]}}"""),setOf(hash)).isEmpty())
        assertTrue(SourceRepository.confirmedTorrentSizes(json("""{"$hash":{"cached":false,"size":999}}"""),setOf(hash)).isEmpty())
    }
    @Test fun maxSizeFiltersDoNotTreatOneEpisodeAsTheWholePack() {
        val hash="a".repeat(40);val source=SourceRelease("Show S01 2160p",infoHash=hash,sizeBytes=1073741824,torrentSizeBytes=30*1073741824L)
        assertTrue(filteredSources(listOf(source),setOf(hash),SourceFilters(maxGb=5),1,true).isEmpty())
        assertTrue(filteredSources(listOf(source.copy(torrentSizeBytes=null)),setOf(hash),SourceFilters(maxGb=5),1,true).isEmpty())
        assertEquals(1,filteredSources(listOf(source),setOf(hash),SourceFilters(maxGb=40),1,true).size)
    }
    @Test fun posterAvailabilityMatchesTypedProviderIdsAndNeverJustNames() {
        val server=MediaItem("jellyfin:one",name="Same title",jellyfinId="one",jellyfinProviderKeys=listOf("tmdb:42"))
        assertTrue(jellyfinIdentityKeys(server).intersect(jellyfinIdentityKeys(MediaItem("tmdb:42",name="Other display name"))).isNotEmpty())
        assertTrue(jellyfinIdentityKeys(server).intersect(jellyfinIdentityKeys(MediaItem("tmdb:42",type="series",name="Same title"))).isEmpty())
        assertTrue(jellyfinIdentityKeys(server).intersect(jellyfinIdentityKeys(MediaItem("unrelated",name="Same title"))).isEmpty())
    }
    @Test fun movieHeroLoadsLogoGenreRuntimeAndCertificationInOneRequestWithoutOmdb()=runBlocking {
        var calls=0
        val catalog=DirectCatalog({"synthetic"}){url,params,_->calls++;assertTrue(url.endsWith("/movie/42"));assertEquals("en-US",params["language"])
            json("""{"runtime":145,"genres":[{"name":"Action"},{"name":"Adventure"}],"images":{"logos":[{"file_path":"/logo.png","iso_639_1":"en"}]},"release_dates":{"results":[{"iso_3166_1":"US","release_dates":[{"certification":"PG-13"}]}]},"external_ids":{"imdb_id":"tt1234567"}}""").asJsonObject
        }
        val item=catalog.heroMetadata(MediaItem("tmdb:42",name="Movie",rating=8.8))
        assertEquals("https://image.tmdb.org/t/p/w500/logo.png",item.logo);assertEquals("145 min",item.runtime);assertEquals("PG-13",item.certification);assertEquals(listOf("Action","Adventure"),item.genres);assertNull(item.rating);assertEquals(1,calls)
    }
    @Test fun seriesHeroUsesEpisodeRuntimeAndUsTvCertificate()=runBlocking {
        val catalog=DirectCatalog({"synthetic"}){url,_,_->assertTrue(url.endsWith("/tv/42"));json("""{"episode_run_time":[],"last_episode_to_air":{"runtime":46},"content_ratings":{"results":[{"iso_3166_1":"US","rating":"TV-MA"}]}}""").asJsonObject}
        val item=catalog.heroMetadata(MediaItem("tmdb:42",type="series",name="Show"));assertEquals("46 min",item.runtime);assertEquals("TV-MA",item.certification);assertNull(item.rating)
    }
}
