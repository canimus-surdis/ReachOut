package com.canimussurdis.reachout.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's line icons, drawn here so no icon library is needed. Use them with Material's Icon(),
 * which colors them to match the text around them.
 */
object AppIcons {

    private fun line(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Menu = line("Menu", "M4 8h16M4 16h16")
    val Back = line("Back", "M19 12H5m6-6-6 6 6 6")
    val Chevron = line("Chevron", "m9 6 6 6-6 6")
    val Plus = line("Plus", "M12 5v14M5 12h14")
    val Minus = line("Minus", "M5 12h14")
    val ArrowLeft = line("ArrowLeft", "M19 12H5m6-6-6 6 6 6")
    val ArrowRight = line("ArrowRight", "M5 12h14m-6-6 6 6-6 6")
    val Check = line("Check", "m5 12.5 4.5 4.5L19 7.5")

    // Settings rows
    val People = line(
        "People",
        "M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z",
        "M2.5 20a6.5 6.5 0 0 1 13 0M16 4.5a3.5 3.5 0 0 1 0 6.5M18.5 14a6 6 0 0 1 3 6",
    )
    val Wheel = line("Wheel", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z", "M12 3v18M3 12h18M5.6 5.6l12.8 12.8M18.4 5.6 5.6 18.4")
    val Bell = line("Bell", "M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15zM10 21h4")
    val Palette = line(
        "Palette",
        "M12 3a9 9 0 1 0 0 18c1.2 0 1.8-.9 1.4-2-.5-1.3.4-2.5 1.8-2.5H18a3 3 0 0 0 3-3A9 9 0 0 0 12 3z",
        "M7.5 12h.01M9.5 7.5h.01M14.5 7.5h.01",
    )
    val Download = line("Download", "M12 3v12M7.5 10.5 12 15l4.5-4.5M4 17v2.5A1.5 1.5 0 0 0 5.5 21h13a1.5 1.5 0 0 0 1.5-1.5V17")
    val Flask = line("Flask", "M9 3h6M10 3v6l-5 9.5A1.7 1.7 0 0 0 6.5 21h11a1.7 1.7 0 0 0 1.5-2.5L14 9V3")
    val Book = line("Book", "M4 5.5A2.5 2.5 0 0 1 6.5 3H20v15H6.5A2.5 2.5 0 0 0 4 20.5zM4 20.5A2.5 2.5 0 0 0 6.5 23H20")
    val Play = line("Play", "M7 4.5v15l12-7.5z")
    val File = line("File", "M6 3h8l4 4v14H6z", "M14 3v4h4M9 12h6M9 16h6")
    val ListCheck = line("ListCheck", "M10 6h10M10 12h10M10 18h10", "m3.5 6 1.5 1.5L7.5 5M3.5 12l1.5 1.5L7.5 11M3.5 18l1.5 1.5L7.5 17")
    val Call = line(
        "Call",
        "M6.6 3.5h2.6l1.4 4-2 1.4a12 12 0 0 0 6.5 6.5l1.4-2 4 1.4v2.6a2 2 0 0 1-2.2 2A16.5 16.5 0 0 1 4.6 5.7a2 2 0 0 1 2-2.2z",
    )
    val Text = line(
        "Text",
        "M5 5h14a1.5 1.5 0 0 1 1.5 1.5v9A1.5 1.5 0 0 1 19 17H10l-4.5 3.5V17H5a1.5 1.5 0 0 1-1.5-1.5v-9A1.5 1.5 0 0 1 5 5z",
    )
    val Share = line("Share", "M12 15V4M7.5 8.5 12 4l4.5 4.5M5 13v5.5A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5V13")
    val Snooze = line(
        "Snooze",
        "M12 5.5a7.5 7.5 0 1 1 0 15 7.5 7.5 0 0 1 0-15z",
        "M12 9.5V13l2.5 1.5M5 4.5 3 6.5M19 4.5l2 2",
    )
}
