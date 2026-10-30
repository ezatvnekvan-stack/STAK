package app.stak.nativeapp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Lucide outline glyphs used by the web navigation. Compose's material icons
 * have a different silhouette, so the exact 24x24 stroke paths are kept here.
 */
private fun lucide(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        paths.forEach { data ->
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()

private fun lucideFilled(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        paths.forEach { data ->
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = SolidColor(Color.Black),
                stroke = null
            )
        }
    }.build()

object Lucide {
    val SkipNext = lucide("SkipNext", "m5 4 10 8-10 8z", "M19 4v16")
    val Sun = lucide("Sun", "M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0", "M12 2v2 M12 20v2 M2 12h2 M20 12h2 M4.93 4.93l1.42 1.42 M17.65 17.65l1.42 1.42 M4.93 19.07l1.42-1.42 M17.65 6.35l1.42-1.42")
    val Volume = lucide("Volume", "m11 5-6 4H2v6h3l6 4z", "M15 8a6 6 0 0 1 0 8 M18 5a10 10 0 0 1 0 14")
    val Layers = lucide("Layers", "m12 3 10 5-10 5L2 8z", "m2 12 10 5 10-5 M2 16l10 5 10-5")
    val Zap = lucide("Zap", "m13 2-3 8H3l8 12 3-8h7z")
    val Sparkles = lucide("Sparkles", "m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5z", "M3 2v4 M1 4h4")
    val Eye = lucide("Eye", "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0", "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0")
    val EyeOff = lucide("EyeOff", "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49", "M14.084 14.158a3 3 0 0 1-4.242-4.242", "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143", "m2 2 20 20")
    /** Filled bookmark used by the poster "saved" marker on the web. */
    val BookmarkFilled: ImageVector = lucideFilled(
        "BookmarkFilled",
        "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"
    )
    /** Watched check rendered at strokeWidth 3 on the web poster card. */
    val CheckBold: ImageVector = ImageVector.Builder(
        name = "CheckBold",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M20 6 9 17l-5-5").toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 3f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }.build()
    val House: ImageVector = lucide(
        "House",
        "m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
        "M9 22V12h6v10"
    )
    val Compass: ImageVector = lucide(
        "Compass",
        "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z",
        "m16.24 7.76-2.12 6.36-6.36 2.12 2.12-6.36z"
    )
    val Bookmark: ImageVector = lucide(
        "Bookmark",
        "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"
    )
    val Tracking: ImageVector = lucide(
        "ChartNoAxesCombined",
        "M3 3v18h18",
        "M7 16v-5",
        "M11 16V7",
        "M15 16v-3",
        "M19 16V9"
    )
    val Library: ImageVector = lucide(
        "HardDrive",
        "M22 12H2",
        "M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z",
        "M6 16h.01",
        "M10 16h.01"
    )
    val Settings: ImageVector = lucide(
        "Settings2",
        "M20 7h-9",
        "M14 17H5",
        "M17 17a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
        "M7 10a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"
    )
    val Search: ImageVector = lucide(
        "Search",
        "M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16z",
        "m21 21-4.35-4.35"
    )
    val Sliders: ImageVector = lucide(
        "SlidersHorizontal",
        "M21 4H14",
        "M10 4H3",
        "M21 12h-7",
        "M10 12H3",
        "M21 20h-9",
        "M8 20H3",
        "M14 2v4",
        "M8 10v4",
        "M16 18v4"
    )
    val Refresh: ImageVector = lucide(
        "RefreshCw",
        "M3 12a9 9 0 0 1 15-6.7L21 8",
        "M21 3v5h-5",
        "M21 12a9 9 0 0 1-15 6.7L3 16",
        "M3 21v-5h5"
    )
    val ChevronDown: ImageVector = lucide("ChevronDown", "m6 9 6 6 6-6")
    val ChevronRight: ImageVector = lucide("ChevronRight", "m9 18 6-6-6-6")
    val Play: ImageVector = lucide("Play", "m6 3 14 9-14 9z")
    val Check: ImageVector = lucide("Check", "M20 6 9 17l-5-5")
    val X: ImageVector = lucide("X", "M18 6 6 18", "m6 6 12 12")
    val Plus: ImageVector = lucide("Plus", "M5 12h14", "M12 5v14")
    val Grid: ImageVector = lucide(
        "LayoutGrid",
        "M3 3h7v7H3z",
        "M14 3h7v7h-7z",
        "M14 14h7v7h-7z",
        "M3 14h7v7H3z"
    )
    val List: ImageVector = lucide(
        "List",
        "M8 6h13",
        "M8 12h13",
        "M8 18h13",
        "M3 6h.01",
        "M3 12h.01",
        "M3 18h.01"
    )
    val Key: ImageVector = lucide(
        "KeyRound",
        "M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z",
        "M16.5 7.5h.01"
    )
    val Database: ImageVector = lucide(
        "Database",
        "M3 5a9 3 0 1 0 18 0a9 3 0 1 0-18 0",
        "M3 5v14a9 3 0 0 0 18 0V5",
        "M3 12a9 3 0 0 0 18 0"
    )
    // Lucide 0.516.0, ISC license; same geometry as the React source.
    val Radio = lucide("Radio", "M16.247 7.761a6 6 0 0 1 0 8.478", "M19.075 4.933a10 10 0 0 1 0 14.134", "M4.925 19.067a10 10 0 0 1 0-14.134", "M7.753 16.239a6 6 0 0 1 0-8.478", "M14 12a2 2 0 1 0-4 0a2 2 0 1 0 4 0")
    val MonitorPlay = lucide("MonitorPlay", "M10 7.75a.75.75 0 0 1 1.142-.638l3.664 2.249a.75.75 0 0 1 0 1.278l-3.664 2.25a.75.75 0 0 1-1.142-.64z", "M12 17v4", "M8 21h8", "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z")
    val Globe: ImageVector = lucide(
        "Globe",
        "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z",
        "M2 12h20",
        "M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"
    )
    val Monitor: ImageVector = lucide(
        "Monitor",
        "M2 3h20v14H2z",
        "M8 21h8",
        "M12 17v4"
    )
    val Palette: ImageVector = lucide(
        "Palette",
        "M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z",
        "M13.5 6.5h.01",
        "M17.5 10.5h.01",
        "M8.5 7.5h.01",
        "M6.5 12.5h.01"
    )
    val Dashboard: ImageVector = lucide(
        "LayoutDashboard",
        "M3 3h7v9H3z",
        "M14 3h7v5h-7z",
        "M14 12h7v9h-7z",
        "M3 16h7v5H3z"
    )
    val Shield: ImageVector = lucide(
        "Shield",
        "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"
    )
    val Film: ImageVector = lucide(
        "Film",
        "M2 3h20v18H2z",
        "M7 3v18",
        "M17 3v18",
        "M2 9h5",
        "M2 15h5",
        "M17 9h5",
        "M17 15h5"
    )
    val AlertTriangle: ImageVector = lucide(
        "TriangleAlert",
        "m21.73 18-8-14a2 2 0 0 0-3.46 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3z",
        "M12 9v4",
        "M12 17h.01"
    )
}
