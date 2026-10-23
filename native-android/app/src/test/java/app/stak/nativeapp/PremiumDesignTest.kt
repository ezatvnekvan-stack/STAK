package app.stak.nativeapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class PremiumDesignTest {
    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)

    @Test fun bodyAndSecondaryTextMeetContrastTargetsInBothThemes() {
        for (dark in listOf(true, false)) {
            val colors = premiumColors(dark)
            for (surface in listOf(colors.background, colors.surface, colors.surfaceContainerLow, colors.surfaceContainer, colors.surfaceContainerHigh)) {
                assertTrue("Primary text contrast on $surface", contrast(colors.onSurface, surface) >= 4.5f)
                assertTrue("Secondary text contrast on $surface", contrast(colors.onSurfaceVariant, surface) >= 4.5f)
            }
            assertTrue(contrast(colors.primary, colors.onPrimary) >= 7f)
            val destructiveSurface = colors.error.copy(alpha = .08f).compositeOver(colors.surface)
            assertTrue("Destructive action text contrast", contrast(colors.error, destructiveSurface) >= 4.5f)
        }
    }

    @Test fun amoledIsOptionalAndModalSurfacesStayOpaque() {
        assertEquals(Color.Black, premiumColors(true, true).background)
        assertNotEquals(Color.Black, premiumColors(true).background)
        assertEquals(1f, premiumColors(true).surface.alpha, 0f)
        assertEquals(1f, premiumColors(false).surface.alpha, 0f)
        assertNotEquals(premiumColors(true).background, premiumColors(true).surface)
    }

    @Test fun everyMainDestinationHasADeliberateHeadingAndDescription() {
        for (name in listOf("Discover", "Watchlist", "Tracking", "Library", "Settings", "TorBox account", "Jellyfin", "Jellyfin Remote", "Search sources", "Metadata & ratings", "Appearance", "Home & browsing", "Data & app", "Share")) {
            val caption = premiumPageCaption(name)
            assertFalse(caption.first.isBlank())
            assertFalse("Missing description for $name", caption.second.isBlank())
        }
    }
}
