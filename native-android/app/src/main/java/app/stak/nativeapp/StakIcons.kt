package app.stak.nativeapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object StakIcons {
    val House by lazy {
        ImageVector.Builder("House", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString("M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString(
                            "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Compass by lazy {
        ImageVector.Builder("Compass", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString("M2.0,12.0a10.0,10.0 0 1,0 20.0,0a10.0,10.0 0 1,0 -20.0,0")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Bookmark by lazy {
        ImageVector.Builder("Bookmark", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString("m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val ChartNoAxesCombined by lazy {
        ImageVector.Builder("ChartNoAxesCombined", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("M12 16v5").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M16 14v7").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M20 10v11").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString(
                            "m22 3-8.646 8.646a.5.5 0 0 1-.708 0L9.354 8.354a.5.5 0 0 0-.707 0L2 15"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M4 18v3").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M8 14v7").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val HardDrive by lazy {
        ImageVector.Builder("HardDrive", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("M22,12L2,12").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString(
                            "M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M6,16L6.01,16").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M10,16L10.01,16").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Settings2 by lazy {
        ImageVector.Builder("Settings2", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("M14 17H5").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M19 7h-9").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString("M14.0,17.0a3.0,3.0 0 1,0 6.0,0a3.0,3.0 0 1,0 -6.0,0")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString("M4.0,7.0a3.0,3.0 0 1,0 6.0,0a3.0,3.0 0 1,0 -6.0,0")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val KeyRound by lazy {
        ImageVector.Builder("KeyRound", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString("M16.0,7.5a0.5,0.5 0 1,0 1.0,0a0.5,0.5 0 1,0 -1.0,0")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Database by lazy {
        ImageVector.Builder("Database", 24.dp, 24.dp, 24f, 24f)
            .apply {
                // The original Lucide SVG includes this ellipse before its two paths.
                addPath(
                    PathParser().parsePathString("M3 5a9 3 0 1 0 18 0a9 3 0 1 0-18 0").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )

                addPath(
                    PathParser().parsePathString("M3 5V19A9 3 0 0 0 21 19V5").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M3 12A9 3 0 0 0 21 12").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Radio by lazy {
        ImageVector.Builder("Radio", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("M16.247 7.761a6 6 0 0 1 0 8.478").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M19.075 4.933a10 10 0 0 1 0 14.134").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M4.925 19.067a10 10 0 0 1 0-14.134").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M7.753 16.239a6 6 0 0 1 0-8.478").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString("M10.0,12.0a2.0,2.0 0 1,0 4.0,0a2.0,2.0 0 1,0 -4.0,0")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val MonitorPlay by lazy {
        ImageVector.Builder("MonitorPlay", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M10 7.75a.75.75 0 0 1 1.142-.638l3.664 2.249a.75.75 0 0 1 0 1.278l-3.664 2.25a.75.75 0 0 1-1.142-.64z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M12 17v4").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M8 21h8").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString(
                            "M4.0,3.0h16.0q2.0,0 2.0,2.0v10.0q0,2.0 -2.0,2.0h-16.0q-2.0,0 -2.0,-2.0v-10.0q0,-2.0 2.0,-2.0Z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Palette by lazy {
        ImageVector.Builder("Palette", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString("M13.0,6.5a0.5,0.5 0 1,0 1.0,0a0.5,0.5 0 1,0 -1.0,0")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString("M17.0,10.5a0.5,0.5 0 1,0 1.0,0a0.5,0.5 0 1,0 -1.0,0")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString("M6.0,12.5a0.5,0.5 0 1,0 1.0,0a0.5,0.5 0 1,0 -1.0,0")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString("M8.0,7.5a0.5,0.5 0 1,0 1.0,0a0.5,0.5 0 1,0 -1.0,0")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Shield by lazy {
        ImageVector.Builder("Shield", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val LayoutGrid by lazy {
        ImageVector.Builder("LayoutGrid", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M4.0,3.0h5.0q1.0,0 1.0,1.0v5.0q0,1.0 -1.0,1.0h-5.0q-1.0,0 -1.0,-1.0v-5.0q0,-1.0 1.0,-1.0Z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString(
                            "M15.0,3.0h5.0q1.0,0 1.0,1.0v5.0q0,1.0 -1.0,1.0h-5.0q-1.0,0 -1.0,-1.0v-5.0q0,-1.0 1.0,-1.0Z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString(
                            "M15.0,14.0h5.0q1.0,0 1.0,1.0v5.0q0,1.0 -1.0,1.0h-5.0q-1.0,0 -1.0,-1.0v-5.0q0,-1.0 1.0,-1.0Z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString(
                            "M4.0,14.0h5.0q1.0,0 1.0,1.0v5.0q0,1.0 -1.0,1.0h-5.0q-1.0,0 -1.0,-1.0v-5.0q0,-1.0 1.0,-1.0Z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val ChevronRight by lazy {
        ImageVector.Builder("ChevronRight", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("m9 18 6-6-6-6").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val RefreshCw by lazy {
        ImageVector.Builder("RefreshCw", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString("M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M21 3v5h-5").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString("M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M8 16H3v5").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Search by lazy {
        ImageVector.Builder("Search", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("m21 21-4.34-4.34").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString("M3.0,11.0a8.0,8.0 0 1,0 16.0,0a8.0,8.0 0 1,0 -16.0,0")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val SlidersHorizontal by lazy {
        ImageVector.Builder("SlidersHorizontal", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("M21,4L14,4").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M10,4L3,4").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M21,12L12,12").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M8,12L3,12").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M21,20L16,20").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M12,20L3,20").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M14,2L14,6").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M8,10L8,14").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M16,18L16,22").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val List by lazy {
        ImageVector.Builder("List", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("M3 12h.01").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M3 18h.01").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M3 6h.01").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M8 12h13").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M8 18h13").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M8 6h13").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Film by lazy {
        ImageVector.Builder("Film", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M5.0,3.0h14.0q2.0,0 2.0,2.0v14.0q0,2.0 -2.0,2.0h-14.0q-2.0,0 -2.0,-2.0v-14.0q0,-2.0 2.0,-2.0Z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M7 3v18").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M3 7.5h4").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M3 12h18").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M3 16.5h4").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M17 3v18").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M17 7.5h4").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M17 16.5h4").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val ArrowLeft by lazy {
        ImageVector.Builder("ArrowLeft", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("m12 19-7-7 7-7").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M19 12H5").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Play by lazy {
        ImageVector.Builder("Play", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("M6 3 20 12 6 21 6 3Z").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Plus by lazy {
        ImageVector.Builder("Plus", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("M5 12h14").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M12 5v14").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val X by lazy {
        ImageVector.Builder("X", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("M18 6 6 18").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("m6 6 12 12").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Check by lazy {
        ImageVector.Builder("Check", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("M20 6 9 17l-5-5").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val ChevronDown by lazy {
        ImageVector.Builder("ChevronDown", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("m6 9 6 6 6-6").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Moon by lazy {
        ImageVector.Builder("Moon", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Sun by lazy {
        ImageVector.Builder("Sun", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString("M8.0,12.0a4.0,4.0 0 1,0 8.0,0a4.0,4.0 0 1,0 -8.0,0")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M12 2v2").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M12 20v2").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("m4.93 4.93 1.41 1.41").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("m17.66 17.66 1.41 1.41").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M2 12h2").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M20 12h2").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("m6.34 17.66-1.41 1.41").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("m19.07 4.93-1.41 1.41").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Info by lazy {
        ImageVector.Builder("Info", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString("M2.0,12.0a10.0,10.0 0 1,0 20.0,0a10.0,10.0 0 1,0 -20.0,0")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M12 16v-4").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M12 8h.01").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Calendar by lazy {
        ImageVector.Builder("Calendar", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("M8 2v4").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M16 2v4").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString(
                            "M5.0,4.0h14.0q2.0,0 2.0,2.0v14.0q0,2.0 -2.0,2.0h-14.0q-2.0,0 -2.0,-2.0v-14.0q0,-2.0 2.0,-2.0Z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M3 10h18").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Trash2 by lazy {
        ImageVector.Builder("Trash2", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("M3 6h18").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M10,11L10,17").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M14,11L14,17").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Folder by lazy {
        ImageVector.Builder("Folder", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Download by lazy {
        ImageVector.Builder("Download", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("M12 15V3").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("m7 10 5 5 5-5").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Pause by lazy {
        ImageVector.Builder("Pause", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M15.0,4.0h2.0q1.0,0 1.0,1.0v14.0q0,1.0 -1.0,1.0h-2.0q-1.0,0 -1.0,-1.0v-14.0q0,-1.0 1.0,-1.0Z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString(
                            "M7.0,4.0h2.0q1.0,0 1.0,1.0v14.0q0,1.0 -1.0,1.0h-2.0q-1.0,0 -1.0,-1.0v-14.0q0,-1.0 1.0,-1.0Z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val SkipBack by lazy {
        ImageVector.Builder("SkipBack", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("M19 20 9 12 19 4 19 20Z").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M5,19L5,5").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val SkipForward by lazy {
        ImageVector.Builder("SkipForward", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("M5 4 15 12 5 20 5 4Z").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M19,5L19,19").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Square by lazy {
        ImageVector.Builder("Square", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M5.0,3.0h14.0q2.0,0 2.0,2.0v14.0q0,2.0 -2.0,2.0h-14.0q-2.0,0 -2.0,-2.0v-14.0q0,-2.0 2.0,-2.0Z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Send by lazy {
        ImageVector.Builder("Send", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("m21.854 2.147-10.94 10.939").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Eye by lazy {
        ImageVector.Builder("Eye", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString("M9.0,12.0a3.0,3.0 0 1,0 6.0,0a3.0,3.0 0 1,0 -6.0,0")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val EyeOff by lazy {
        ImageVector.Builder("EyeOff", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M14.084 14.158a3 3 0 0 1-4.242-4.242").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString(
                            "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("m2 2 20 20").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val Users by lazy {
        ImageVector.Builder("Users", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString("M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M16 3.128a4 4 0 0 1 0 7.744").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M22 21v-2a4 4 0 0 0-3-3.87").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString("M5.0,7.0a4.0,4.0 0 1,0 8.0,0a4.0,4.0 0 1,0 -8.0,0")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val ArrowUp by lazy {
        ImageVector.Builder("ArrowUp", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("m5 12 7-7 7 7").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M12 19V5").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
    val ArrowDown by lazy {
        ImageVector.Builder("ArrowDown", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString("M12 5v14").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("m19 12-7 7-7-7").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }

    val Share2 by lazy {
        ImageVector.Builder("Share2", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString("M15.0,5.0a3.0,3.0 0 1,0 6.0,0a3.0,3.0 0 1,0 -6.0,0")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString("M3.0,12.0a3.0,3.0 0 1,0 6.0,0a3.0,3.0 0 1,0 -6.0,0")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString("M15.0,19.0a3.0,3.0 0 1,0 6.0,0a3.0,3.0 0 1,0 -6.0,0")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M8.59,13.51L15.42,17.49").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M15.41,6.51L8.59,10.49").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }

    val Copy by lazy {
        ImageVector.Builder("Copy", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M10.0,8.0h10.0q2.0,0 2.0,2.0v10.0q0,2.0 -2.0,2.0h-10.0q-2.0,0 -2.0,-2.0v-10.0q0,-2.0 2.0,-2.0Z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser()
                        .parsePathString("M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }

    val FileVideo by lazy {
        ImageVector.Builder("FileVideo", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("M14 2v4a2 2 0 0 0 2 2h4").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("m10 11 5 3-5 3v-6Z").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }

    val SquareCheck by lazy {
        ImageVector.Builder("SquareCheck", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M5.0,3.0h14.0q2.0,0 2.0,2.0v14.0q0,2.0 -2.0,2.0h-14.0q-2.0,0 -2.0,-2.0v-14.0q0,-2.0 2.0,-2.0Z"
                        )
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    PathParser().parsePathString("m9 12 2 2 4-4").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }

    // Exact shared mark from frontend/src/components/JellyfinLogo.jsx.
    val Jellyfin by lazy {
        ImageVector.Builder("Jellyfin", 64.dp, 64.dp, 64f, 64f)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString("M28 8Q32 2 36 8L60 50Q64 58 55 58H9Q0 58 4 50Z")
                        .toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 3f,
                )
                addPath(
                    PathParser().parsePathString("M30 25Q32 21 34 25L47 47H17Z").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 3f,
                )
                addPath(
                    PathParser().parsePathString("M32 34L38 44H26Z").toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 3f,
                )
            }
            .build()
    }
}
