package app.stak.nativeapp.ui.components

import org.junit.Assert.*
import org.junit.Test

class CinematicGeometryTest {
    @Test fun phoneHomeLeavesRoomForTheFollowingShelf() {
        for (height in listOf(720, 800, 900)) assertTrue(homeHeroHeight(height) / height in .40f.. .46f)
    }
    @Test fun largeTextHasRoomWithoutBecomingFullScreen() {
        assertTrue(homeHeroHeight(800, 1.6f) > homeHeroHeight(800))
        assertTrue(homeHeroHeight(800, 2f) <= 440f)
    }
    @Test fun landscapeAndTabletGeometryRemainBounded() {
        assertEquals(300f, homeHeroHeight(320), .01f)
        assertEquals(440f, homeHeroHeight(1600), .01f)
        assertEquals(280f, detailArtworkHeight(320), .01f)
        assertEquals(480f, detailArtworkHeight(1600), .01f)
    }
}
