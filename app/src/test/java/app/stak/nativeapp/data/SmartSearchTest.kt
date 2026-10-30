package app.stak.nativeapp.data
import org.junit.Assert.*
import org.junit.Test
class SmartSearchTest {
    @Test fun accentPunctuationAndWordPrefixesMatch(){
        val item=MediaItem("1",name="Árvíztűrő: Spider-Man",year="2026")
        assertTrue(searchScore(item,"arvizturo spider man")>0)
        assertTrue(searchScore(item,"spid arviz")>0)
        assertEquals(0,searchScore(item,"unrelated"))
    }
    @Test fun oneCharacterTyposMatchButShortNoiseDoesNot(){
        val item=MediaItem("1",name="Carrie")
        assertTrue(searchScore(item,"carie")>0)
        assertEquals(0,searchScore(item,"cat"))
        assertEquals(0,searchScore(item,"borre"))
    }
    @Test fun yearAndImdbUrlsDisambiguateRemakes(){
        val old=MediaItem("1","tt0074285",name="Carrie",year="1976")
        val new=MediaItem("2",name="Carrie",year="2013")
        assertEquals(listOf(new),rankedSearch(listOf(old,new),"Carrie (2013)"))
        assertEquals("tt0074285",providerSearchQuery("https://www.imdb.com/title/tt0074285/?ref_=a"))
        assertEquals(1000,searchScore(old,"tt0074285"))
    }
    @Test fun titleNumbersAndNonLatinNamesRemainSearchable(){
        assertTrue(searchScore(MediaItem("1",name="Blade Runner 2049",year="2017"),"Blade Runner 2049")>0)
        assertTrue(searchScore(MediaItem("2",name="進撃の巨人"),"進撃")>0)
    }
    @Test fun exactTitlesWinAndProviderMatchesKeepStableOrder(){
        val similar=MediaItem("1",name="Carrie Returns");val exact=MediaItem("2",name="Carrie");val other=MediaItem("3",name="Other")
        assertEquals(listOf(exact,similar),rankedSearch(listOf(similar,exact,other),"Carrie"))
        assertEquals(listOf(exact,similar,other),rankedSearch(listOf(similar,exact,other),"Carrie",true))
    }
}
