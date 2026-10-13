package com.brainpal.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Eigen lijn-iconen (24x24, streepdikte 2): leeg = niet actief, gevuld = actief. */
private fun icon(name: String, filled: Boolean, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            addPath(
                PathParser().parsePathString(d).toNodes(),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private const val BOOKMARK = "M19 21l-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z"
private val THUMB_UP = arrayOf(
    "M7 10v12",
    "M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z",
)
private val THUMB_DOWN = arrayOf(
    "M17 14V2",
    "M9 18.12 10 14H4.17a2 2 0 0 1-1.92-2.56l2.33-8A2 2 0 0 1 6.5 2H20a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.76a2 2 0 0 0-1.79 1.11L12 22a3.13 3.13 0 0 1-3-3.88Z",
)

object Ic {
    val Bookmark by lazy { icon("bookmark", false, BOOKMARK) }
    val BookmarkFilled by lazy { icon("bookmarkFilled", true, BOOKMARK) }
    val ThumbUp by lazy { icon("thumbUp", false, *THUMB_UP) }
    val ThumbUpFilled by lazy { icon("thumbUpFilled", true, *THUMB_UP) }
    val ThumbDown by lazy { icon("thumbDown", false, *THUMB_DOWN) }
    val ThumbDownFilled by lazy { icon("thumbDownFilled", true, *THUMB_DOWN) }
    val Refresh by lazy {
        icon("refresh", false,
            "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8", "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16", "M8 16H3v5")
    }
    val Back by lazy { icon("back", false, "M12 19l-7-7 7-7", "M19 12H5") }
    val External by lazy { icon("external", false, "M15 3h6v6", "M10 14L21 3", "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6") }
    val Feed by lazy {
        icon("feed", false,
            "M4 22h16a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2H8a2 2 0 0 0-2 2v16a2 2 0 0 1-2 2Zm0 0a2 2 0 0 1-2-2v-9c0-1.1.9-2 2-2h2",
            "M18 14h-8", "M15 18h-5", "M10 6h8v4h-8V6Z")
    }
    val Settings by lazy {
        icon("settings", false,
            "M21 4h-7", "M10 4H3", "M21 12h-9", "M8 12H3", "M21 20h-5", "M12 20H3", "M14 2v4", "M8 10v4", "M16 18v4")
    }
}
