package app.stak.nativeapp

import org.junit.Assert.*
import org.junit.Test

class HomeLayoutTest {
    @Test
    fun navigationFiltersUnknownTabsAndKeepsRequiredDestinations() {
        assertEquals(
            listOf("torbox", "tracking", "home", "settings"),
            navigationOrder(
                mapOf("nav_order" to "[\"torbox\",\"tracking\",\"tracking\",\"unknown\"]")
            ),
        )
        assertEquals(defaultNavigation, navigationOrder(mapOf("nav_order" to "broken")))
        assertEquals(
            listOf("settings", "home"),
            navigationOrder(mapOf("nav_order" to "[\"account\"]")),
        )
    }

    @Test
    fun genreShelvesKeepValidIdentityAndPersistOrderingAndVisibility() {
        val options =
            mapOf(
                "home_genres" to
                    "[{\"type\":\"movie\",\"genre\":18},{\"type\":\"unknown\",\"genre\":35}]",
                "home_order" to "[\"genre:movie:18\",\"watchlist\"]",
                "home_genre:movie:18" to "false",
            )
        val shelves = homeShelves(options, 2026)
        assertEquals("genre:movie:18", shelves.first().id)
        assertFalse(shelves.first().enabled(options))
        assertEquals(1, shelves.count { it.genre > 0 })
        assertEquals("watchlist", shelves[1].id)
        assertTrue(shelves.first { it.id == "new_movie" }.label.contains("2026"))
    }

    @Test
    fun movingShelvesNeverDropsOrDuplicatesDestinations() {
        assertEquals(listOf("b", "a", "c"), movedItem(listOf("a", "b", "c"), 0, 1))
        assertEquals(listOf("a", "b"), movedItem(listOf("a", "b"), 0, -1))
    }
}
