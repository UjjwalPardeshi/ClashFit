package com.clashfit.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The handful of glyphs the shell needs, drawn here so the app owns them and carries no icon
 * font. All on a 24-unit grid, all a single fill, so a NavigationBarItem tints them cleanly.
 */
object AppIcons {

    /** The brand bolt. Same silhouette as the launcher mark, at 24. */
    val Bolt: ImageVector by lazy {
        ImageVector.Builder("bolt", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(13.5f, 2f); lineTo(4f, 13.5f); lineTo(10.2f, 13.5f)
                lineTo(8.6f, 22f); lineTo(20f, 9.5f); lineTo(13.4f, 9.5f); close()
            }
        }.build()
    }

    /** Four squares: the library grid. */
    val Grid: ImageVector by lazy {
        ImageVector.Builder("grid", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 3f); lineTo(10.5f, 3f); lineTo(10.5f, 10.5f); lineTo(3f, 10.5f); close()
                moveTo(13.5f, 3f); lineTo(21f, 3f); lineTo(21f, 10.5f); lineTo(13.5f, 10.5f); close()
                moveTo(3f, 13.5f); lineTo(10.5f, 13.5f); lineTo(10.5f, 21f); lineTo(3f, 21f); close()
                moveTo(13.5f, 13.5f); lineTo(21f, 13.5f); lineTo(21f, 21f); lineTo(13.5f, 21f); close()
            }
        }.build()
    }

    /** Three bars, rising. */
    val Chart: ImageVector by lazy {
        ImageVector.Builder("chart", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 13f); lineTo(7.5f, 13f); lineTo(7.5f, 21f); lineTo(3f, 21f); close()
                moveTo(9.75f, 8f); lineTo(14.25f, 8f); lineTo(14.25f, 21f); lineTo(9.75f, 21f); close()
                moveTo(16.5f, 3f); lineTo(21f, 3f); lineTo(21f, 21f); lineTo(16.5f, 21f); close()
            }
        }.build()
    }

    /** Head and shoulders. */
    val Person: ImageVector by lazy {
        ImageVector.Builder("person", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                // head
                moveTo(12f, 3f)
                curveTo(14.5f, 3f, 16.5f, 5f, 16.5f, 7.5f)
                curveTo(16.5f, 10f, 14.5f, 12f, 12f, 12f)
                curveTo(9.5f, 12f, 7.5f, 10f, 7.5f, 7.5f)
                curveTo(7.5f, 5f, 9.5f, 3f, 12f, 3f); close()
                // shoulders
                moveTo(3f, 21f)
                curveTo(3f, 16.5f, 7f, 14f, 12f, 14f)
                curveTo(17f, 14f, 21f, 16.5f, 21f, 21f); close()
            }
        }.build()
    }

    val Back: ImageVector by lazy {
        ImageVector.Builder("back", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter,
            ) {
                moveTo(20f, 12f); lineTo(5f, 12f)
                moveTo(11f, 5f); lineTo(4f, 12f); lineTo(11f, 19f)
            }
        }.build()
    }

    val Close: ImageVector by lazy {
        ImageVector.Builder("close", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Square,
            ) {
                moveTo(5f, 5f); lineTo(19f, 19f)
                moveTo(19f, 5f); lineTo(5f, 19f)
            }
        }.build()
    }

    /** A right-pointing chevron for list rows. */
    val Chevron: ImageVector by lazy {
        ImageVector.Builder("chevron", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter,
            ) {
                moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f)
            }
        }.build()
    }
}
