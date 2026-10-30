package app.stak.nativeapp.data
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TorrentMetadataResolverTest {
    @Test fun aLaterSeasonYearCanMatchTheUniqueEarlierSeries()=runBlocking {
        val resolver=TorrentMetadataResolver{_,_->listOf(MediaItem("series",type="series",name="Silo",year="2023",poster="https://example.invalid/poster"))}
        assertEquals("series",resolver.resolve("Silo.2024.S02.2160p.mkv")?.id)
    }
    @Test fun ambiguousRemakesWithoutYearDoNotGetAnArbitraryPoster()=runBlocking {
        val resolver=TorrentMetadataResolver{_,_->listOf(MediaItem("old",name="Dune",year="1984"),MediaItem("new",name="Dune",year="2021"))}
        assertNull(resolver.resolve("Dune.1080p.mkv"))
    }
    @Test fun genericTorrentFolderUsesItsActualMediaFileAndSkipsSamples()=runBlocking {
        val resolver=TorrentMetadataResolver{query,_->if(query=="Dune")listOf(MediaItem("dune",name="Dune",year="2021",poster="https://example.invalid/poster")) else emptyList()}
        val torrent=TorBoxTorrent("1","Unhelpful folder",100,1f,true,"cached",listOf(TorBoxFile("sample","sample.mkv",1),TorBoxFile("movie","Folder/Dune.2021.1080p.mkv",99)))
        assertEquals("dune",resolver.resolveTorrent(torrent)?.id)
    }
    @Test fun multiMovieBundlesDoNotBorrowTheFirstFilmsArtwork()=runBlocking {
        val resolver=TorrentMetadataResolver{_,_->emptyList()}
        assertNull(resolver.resolveTorrent(TorBoxTorrent("1","Collection",100,1f,true,"cached",listOf(TorBoxFile("a","Dune.2021.mkv",50),TorBoxFile("b","Alien.1979.mkv",50)))))
    }
    @Test fun movieAndSeriesNamesDiscardReleaseTechnicalTags() {
        assertEquals(TorrentTitleQuery("Dune Part Two","2024","movie",null),torrentTitleQuery("Dune.Part.Two.2024.2160p.WEB-DL.x265-GROUP.mkv"))
        assertEquals(TorrentTitleQuery("Silo","2023","series",null),torrentTitleQuery("Silo.2023.S01E02.1080p.WEB-DL.mkv"))
        assertEquals("1917",torrentTitleQuery("1917.2019.1080p.BluRay.mkv")?.title)
        assertEquals("1917",torrentTitleQuery("1917.1080p.BluRay.mkv")?.title)
    }
    @Test fun exactTitleAndYearAreRequiredAndSuccessfulMatchIsCached() = runBlocking {
        var calls=0
        val resolver = TorrentMetadataResolver { q, type -> calls++; assertEquals("Dune",q); assertEquals("movie",type); listOf(MediaItem("one",name="Dune Part Two",year="2021"),MediaItem("two",name="Dune",year="1984"),MediaItem("three",name="Dune",year="2021",poster="https://example.invalid/poster")) }
        assertEquals("three",resolver.resolve("Dune.2021.1080p.mkv")?.id)
        resolver.resolve("Dune.2021.1080p.mkv"); assertEquals(1,calls)
    }
    @Test fun unrelatedResultsAndProviderErrorsDoNotInventPosterMatches() = runBlocking {
        assertNull(TorrentMetadataResolver { _,_->listOf(MediaItem("one",name="Different title",year="2021")) }.resolve("Dune.2021.1080p.mkv"))
        assertNull(TorrentMetadataResolver { _,_->throw java.io.IOException("unavailable") }.resolve("Dune.2021.1080p.mkv"))
    }
    @Test fun seriesCannotMatchAMovieAndAliasesCanMatch() = runBlocking {
        val resolver=TorrentMetadataResolver { _,_->listOf(MediaItem("movie",type="movie",name="A Show",year="2020"),MediaItem("series",type="series",name="Original Name",year="2020",aliases=listOf("A Show"))) }
        assertEquals("series",resolver.resolve("A.Show.2020.S01E01.mkv")?.id)
    }
}
