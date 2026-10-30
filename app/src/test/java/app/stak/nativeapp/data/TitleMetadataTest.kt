package app.stak.nativeapp.data

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class TitleMetadataTest {
    private fun json(raw: String) = JsonParser.parseString(raw).asJsonObject
    @Test fun movieWithoutCollectionAcceptsExplicitJsonNullInOptionalMetadata() = runBlocking {
        val catalog=DirectCatalog(tmdbKey={"test"}){_,_,_->json("""{"id":745,"title":"The Sixth Sense","imdb_id":"tt0167404","belongs_to_collection":null,"images":null,"credits":null,"release_dates":null,"content_ratings":null,"recommendations":null,"similar":null,"external_ids":null}""")}
        val detail=catalog.details("movie","tmdb:745")
        assertEquals("The Sixth Sense",detail.item.name);assertTrue(detail.collection.isEmpty());assertTrue(detail.cast.isEmpty());assertNull(detail.notice)
    }
    @Test fun movieMapsRealCreditsArtworkCertificationAndCollection() = runBlocking {
        val catalog = DirectCatalog(tmdbKey = { "test-token" }) { url,_,_ ->
            when {
                url.endsWith("/movie/42") -> json("""{"id":42,"title":"A film","runtime":110,"external_ids":{"imdb_id":"tt1234567"},"status":"Released","genres":[{"name":"Drama"}],"vote_average":8.2,"credits":{"cast":[{"id":7,"name":"Actor","character":"Character","profile_path":"/actor.jpg"}],"crew":[{"id":8,"name":"Director","job":"Writer"},{"id":8,"name":"Director","job":"Director"}]},"images":{"logos":[{"iso_639_1":"de","file_path":"/wrong.png"},{"iso_639_1":"en","file_path":"/logo.png"}],"backdrops":[{"file_path":"/image.jpg"}]},"videos":{"results":[{"site":"YouTube","type":"Teaser","key":"notTrailer"},{"site":"YouTube","type":"Trailer","key":"realTrailer","official":true}]},"release_dates":{"results":[{"iso_3166_1":"US","release_dates":[{"certification":"PG-13"}]}]},"belongs_to_collection":{"id":9},"similar":{"results":[{"id":43,"title":"Related"}]}}""")
                url.endsWith("/collection/9") -> json("""{"parts":[{"id":44,"title":"Later","release_date":"2024-01-01"},{"id":42,"title":"Earlier","release_date":"2020-01-01"}]}""")
                else -> throw IOException("Unexpected request")
            }
        }
        val detail = catalog.details("movie", "tmdb:42")
        assertEquals("tt1234567",detail.item.imdbId); assertEquals("PG-13",detail.certification)
        assertEquals("Actor",detail.cast.single().name); assertTrue(detail.crew.single().role.contains("Director")); assertTrue(detail.crew.single().role.contains("Writer"))
        assertTrue(detail.logo!!.endsWith("/logo.png")); assertEquals("https://www.youtube.com/watch?v=realTrailer",detail.trailerUrl)
        assertEquals(listOf("Earlier","Later"),detail.collection.map { it.name }); assertEquals("Related",detail.related.single().name)
        assertTrue(detail.gallery.single().endsWith("/image.jpg")); assertNull(detail.imdbRating)
    }
    @Test fun failedSeasonPreservesOtherSeasonsAndExplainsPartialResult() = runBlocking {
        val catalog = DirectCatalog(tmdbKey = { "key" }) { url,_,_ -> when {
            url.endsWith("/tv/42") -> json("""{"id":42,"name":"Series","episode_run_time":[45],"seasons":[{"season_number":0},{"season_number":2},{"season_number":1}],"created_by":[{"id":8,"name":"Creator"}]}""")
            url.endsWith("/season/1") -> json("""{"episodes":[{"episode_number":2,"name":"Second","air_date":"2024-02-02"},{"episode_number":1,"name":"First"}]}""")
            else -> throw IOException("Season unavailable")
        } }
        val detail = catalog.details("series","tmdb:42")
        assertEquals(listOf(1,2),detail.seasons)
        assertEquals(2,detail.episodes.size); assertTrue(detail.episodes.all { it.season == 1 })
        assertTrue(detail.notice!!.contains("Season 2")); assertEquals("45 min",detail.item.runtime)
        assertEquals("Creator",detail.crew.single().role)
    }
    @Test fun cinemetaSortsAndDeduplicatesEpisodesAndExcludesSpecials() = runBlocking {
        val catalog = DirectCatalog(tmdbKey = { "" }) { _,_,_ -> json("""{"meta":{"id":"tt1234567","name":"Series","videos":[{"season":2,"episode":1,"name":"Later"},{"season":1,"episode":1,"name":"First","released":"2024-01-01"},{"season":1,"episode":1,"name":"Duplicate"},{"season":0,"episode":1,"name":"Special"}]}}""") }
        val detail = catalog.details("series","tt1234567")
        assertEquals(listOf(1,2),detail.seasons); assertEquals(listOf("First","Later"),detail.episodes.map { it.name })
        assertEquals("2024-01-01",detail.episodes.first().released)
    }
    @Test fun requestedCinemetaFailureFallsBackToTmdb() = runBlocking {
        val catalog = DirectCatalog(tmdbKey = { "key" }, preferCinemeta = { true }) { url,_,_ -> when {
            url.contains("cinemeta") -> throw IOException("Unavailable")
            url.contains("/find/") -> json("""{"movie_results":[{"id":42}]}""")
            else -> json("""{"id":42,"title":"TMDb fallback","imdb_id":"tt1234567"}""")
        } }
        val detail = catalog.details("movie","tt1234567")
        assertEquals("TMDb fallback",detail.item.name); assertTrue(detail.notice!!.contains("Using TMDb"))
    }
    @Test fun unavailableCollectionDoesNotDestroyMovieDetails() = runBlocking {
        val catalog = DirectCatalog(tmdbKey = { "key" }) { url,_,_ -> if (url.contains("/collection/")) throw IOException("unavailable") else json("""{"id":42,"title":"Film","belongs_to_collection":{"id":1}}""") }
        val detail = catalog.details("movie","tmdb:42")
        assertEquals("Film",detail.item.name); assertTrue(detail.collection.isEmpty()); assertTrue(detail.notice!!.contains("Collection"))
    }
    @Test fun failedIdentityResolutionFallsBackOnlyToRequestedImdbTitle() = runBlocking {
        val catalog = DirectCatalog(tmdbKey = { "key" }) { url,_,_ -> if (url.contains("/find/")) throw IOException("unavailable") else { assertTrue(url.endsWith("/tt1234567.json")); json("""{"meta":{"id":"tt1234567","name":"Requested"}}""") } }
        assertEquals("Requested",catalog.details("movie","tt1234567").item.name)
    }
}
