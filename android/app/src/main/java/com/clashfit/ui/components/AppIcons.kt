package com.clashfit.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The glyphs the app needs, drawn here so it owns them and carries no icon font. All on a
 * 24-unit grid; fills are single-colour so any Icon tint applies cleanly, strokes are 2px.
 */
object AppIcons {

    private fun filled(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply { path(fill = SolidColor(Color.Black)) { block() } }.build()

    private fun stroked(name: String, width: Float = 2f, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) { block() }
        }.build()

    /** The brand bolt. Same silhouette as the launcher mark, at 24. */
    val Bolt: ImageVector by lazy {
        filled("bolt") {
            moveTo(13.5f, 2f); lineTo(4f, 13.5f); lineTo(10.2f, 13.5f)
            lineTo(8.6f, 22f); lineTo(20f, 9.5f); lineTo(13.4f, 9.5f); close()
        }
    }

    /** Four squares: the library grid. */
    val Grid: ImageVector by lazy {
        filled("grid") {
            moveTo(3f, 3f); lineTo(10.5f, 3f); lineTo(10.5f, 10.5f); lineTo(3f, 10.5f); close()
            moveTo(13.5f, 3f); lineTo(21f, 3f); lineTo(21f, 10.5f); lineTo(13.5f, 10.5f); close()
            moveTo(3f, 13.5f); lineTo(10.5f, 13.5f); lineTo(10.5f, 21f); lineTo(3f, 21f); close()
            moveTo(13.5f, 13.5f); lineTo(21f, 13.5f); lineTo(21f, 21f); lineTo(13.5f, 21f); close()
        }
    }

    /** Three bars, rising. */
    val Chart: ImageVector by lazy {
        filled("chart") {
            moveTo(3f, 13f); lineTo(7.5f, 13f); lineTo(7.5f, 21f); lineTo(3f, 21f); close()
            moveTo(9.75f, 8f); lineTo(14.25f, 8f); lineTo(14.25f, 21f); lineTo(9.75f, 21f); close()
            moveTo(16.5f, 3f); lineTo(21f, 3f); lineTo(21f, 21f); lineTo(16.5f, 21f); close()
        }
    }

    /** Head and shoulders. */
    val Person: ImageVector by lazy {
        filled("person") {
            moveTo(12f, 3f)
            curveTo(14.5f, 3f, 16.5f, 5f, 16.5f, 7.5f); curveTo(16.5f, 10f, 14.5f, 12f, 12f, 12f)
            curveTo(9.5f, 12f, 7.5f, 10f, 7.5f, 7.5f); curveTo(7.5f, 5f, 9.5f, 3f, 12f, 3f); close()
            moveTo(3f, 21f); curveTo(3f, 16.5f, 7f, 14f, 12f, 14f); curveTo(17f, 14f, 21f, 16.5f, 21f, 21f); close()
        }
    }

    /** Two people: friends. */
    val People: ImageVector by lazy {
        filled("people") {
            moveTo(9f, 4f); curveTo(11f, 4f, 12.5f, 5.6f, 12.5f, 7.5f); curveTo(12.5f, 9.4f, 11f, 11f, 9f, 11f)
            curveTo(7f, 11f, 5.5f, 9.4f, 5.5f, 7.5f); curveTo(5.5f, 5.6f, 7f, 4f, 9f, 4f); close()
            moveTo(2f, 20f); curveTo(2f, 15.5f, 5f, 13f, 9f, 13f); curveTo(13f, 13f, 16f, 15.5f, 16f, 20f); close()
            moveTo(16.5f, 6f); curveTo(18.2f, 6f, 19.5f, 7.3f, 19.5f, 9f); curveTo(19.5f, 10.7f, 18.2f, 12f, 16.5f, 12f)
            curveTo(15.9f, 12f, 15.4f, 11.9f, 14.9f, 11.6f); curveTo(15.4f, 10.9f, 15.5f, 9.3f, 15.5f, 7.5f)
            curveTo(15.5f, 7f, 15.4f, 6.5f, 15.3f, 6.2f); curveTo(15.7f, 6.1f, 16.1f, 6f, 16.5f, 6f); close()
            moveTo(17.5f, 20f); curveTo(17.5f, 17.5f, 16.8f, 15.4f, 15.6f, 13.9f); curveTo(16f, 13.9f, 16.2f, 13.8f, 16.5f, 13.8f)
            curveTo(19.5f, 13.8f, 22f, 16f, 22f, 20f); close()
        }
    }

    /** A cup: the leaderboard. */
    val Trophy: ImageVector by lazy {
        filled("trophy") {
            moveTo(6f, 3f); lineTo(18f, 3f); lineTo(18f, 5f); lineTo(21f, 5f); lineTo(21f, 8f)
            curveTo(21f, 10.5f, 19.3f, 12.4f, 17f, 12.9f); curveTo(16.2f, 14.6f, 14.7f, 15.7f, 13f, 15.9f)
            lineTo(13f, 18f); lineTo(16f, 18f); lineTo(16f, 21f); lineTo(8f, 21f); lineTo(8f, 18f); lineTo(11f, 18f)
            lineTo(11f, 15.9f); curveTo(9.3f, 15.7f, 7.8f, 14.6f, 7f, 12.9f); curveTo(4.7f, 12.4f, 3f, 10.5f, 3f, 8f)
            lineTo(3f, 5f); lineTo(6f, 5f); close()
            moveTo(5f, 7f); lineTo(5f, 8f); curveTo(5f, 9.2f, 5.6f, 10.2f, 6.5f, 10.7f); lineTo(6f, 7f); close()
            moveTo(19f, 7f); lineTo(18f, 7f); lineTo(17.5f, 10.7f); curveTo(18.4f, 10.2f, 19f, 9.2f, 19f, 8f); close()
        }
    }

    /** A flame: the streak. */
    val Flame: ImageVector by lazy {
        filled("flame") {
            moveTo(12f, 2f); curveTo(12f, 6f, 7f, 8f, 7f, 13.5f); curveTo(7f, 17f, 9.2f, 20f, 12f, 21.5f)
            curveTo(14.8f, 20f, 17f, 17f, 17f, 13.5f); curveTo(17f, 10.5f, 15.5f, 8.5f, 14.5f, 7f)
            curveTo(14.2f, 9f, 13.2f, 10f, 12.5f, 10f); curveTo(13.5f, 7f, 13f, 4f, 12f, 2f); close()
            moveTo(12f, 13f); curveTo(13.3f, 14.2f, 14f, 15.3f, 14f, 16.5f); curveTo(14f, 17.9f, 13.1f, 19f, 12f, 19.4f)
            curveTo(10.9f, 19f, 10f, 17.9f, 10f, 16.5f); curveTo(10f, 15.3f, 10.7f, 14.2f, 12f, 13f); close()
        }
    }

    /** A five-point star: badges. */
    val Star: ImageVector by lazy {
        filled("star") {
            moveTo(12f, 2.5f); lineTo(14.9f, 8.6f); lineTo(21.5f, 9.4f); lineTo(16.6f, 14f); lineTo(17.9f, 20.6f)
            lineTo(12f, 17.4f); lineTo(6.1f, 20.6f); lineTo(7.4f, 14f); lineTo(2.5f, 9.4f); lineTo(9.1f, 8.6f); close()
        }
    }

    /** A camera: the referee. */
    val Camera: ImageVector by lazy {
        filled("camera") {
            moveTo(9f, 4f); lineTo(15f, 4f); lineTo(16.8f, 6.5f); lineTo(20f, 6.5f); curveTo(21.1f, 6.5f, 22f, 7.4f, 22f, 8.5f)
            lineTo(22f, 18f); curveTo(22f, 19.1f, 21.1f, 20f, 20f, 20f); lineTo(4f, 20f); curveTo(2.9f, 20f, 2f, 19.1f, 2f, 18f)
            lineTo(2f, 8.5f); curveTo(2f, 7.4f, 2.9f, 6.5f, 4f, 6.5f); lineTo(7.2f, 6.5f); close()
            moveTo(12f, 9f); curveTo(9.8f, 9f, 8f, 10.8f, 8f, 13f); curveTo(8f, 15.2f, 9.8f, 17f, 12f, 17f)
            curveTo(14.2f, 17f, 16f, 15.2f, 16f, 13f); curveTo(16f, 10.8f, 14.2f, 9f, 12f, 9f); close()
        }
    }

    /** A shield: privacy. */
    val Shield: ImageVector by lazy {
        filled("shield") {
            moveTo(12f, 2f); lineTo(20f, 5.5f); lineTo(20f, 11f); curveTo(20f, 16.2f, 16.6f, 20.4f, 12f, 22f)
            curveTo(7.4f, 20.4f, 4f, 16.2f, 4f, 11f); lineTo(4f, 5.5f); close()
            moveTo(10.6f, 15.4f); lineTo(16f, 10f); lineTo(14.6f, 8.6f); lineTo(10.6f, 12.6f); lineTo(8.6f, 10.6f); lineTo(7.2f, 12f); close()
        }
    }

    /** A gear: settings. */
    val Gear: ImageVector by lazy {
        filled("gear") {
            moveTo(10.3f, 2f); lineTo(13.7f, 2f); lineTo(14.2f, 4.6f); curveTo(14.9f, 4.9f, 15.5f, 5.2f, 16.1f, 5.7f)
            lineTo(18.6f, 4.8f); lineTo(20.3f, 7.7f); lineTo(18.3f, 9.4f); curveTo(18.4f, 10.2f, 18.4f, 10.9f, 18.3f, 11.6f)
            lineTo(20.3f, 13.3f); lineTo(18.6f, 16.2f); lineTo(16.1f, 15.3f); curveTo(15.5f, 15.8f, 14.9f, 16.1f, 14.2f, 16.4f)
            lineTo(13.7f, 19f); lineTo(10.3f, 19f); lineTo(9.8f, 16.4f); curveTo(9.1f, 16.1f, 8.5f, 15.8f, 7.9f, 15.3f)
            lineTo(5.4f, 16.2f); lineTo(3.7f, 13.3f); lineTo(5.7f, 11.6f); curveTo(5.6f, 10.9f, 5.6f, 10.2f, 5.7f, 9.4f)
            lineTo(3.7f, 7.7f); lineTo(5.4f, 4.8f); lineTo(7.9f, 5.7f); curveTo(8.5f, 5.2f, 9.1f, 4.9f, 9.8f, 4.6f); close()
            moveTo(12f, 7.5f); curveTo(10.3f, 7.5f, 9f, 8.8f, 9f, 10.5f); curveTo(9f, 12.2f, 10.3f, 13.5f, 12f, 13.5f)
            curveTo(13.7f, 13.5f, 15f, 12.2f, 15f, 10.5f); curveTo(15f, 8.8f, 13.7f, 7.5f, 12f, 7.5f); close()
        }
    }

    /** A bell: alarms. */
    val Bell: ImageVector by lazy {
        filled("bell") {
            moveTo(12f, 2f); curveTo(13.1f, 2f, 14f, 2.9f, 14f, 4f); curveTo(17f, 5f, 18f, 7.5f, 18f, 11f); lineTo(18f, 15f)
            lineTo(20f, 17f); lineTo(20f, 18f); lineTo(4f, 18f); lineTo(4f, 17f); lineTo(6f, 15f); lineTo(6f, 11f)
            curveTo(6f, 7.5f, 7f, 5f, 10f, 4f); curveTo(10f, 2.9f, 10.9f, 2f, 12f, 2f); close()
            moveTo(9.5f, 19.5f); lineTo(14.5f, 19.5f); curveTo(14.5f, 20.9f, 13.4f, 22f, 12f, 22f); curveTo(10.6f, 22f, 9.5f, 20.9f, 9.5f, 19.5f); close()
        }
    }

    /** A heart: health tools. */
    val Heart: ImageVector by lazy {
        filled("heart") {
            moveTo(12f, 21f); lineTo(4.2f, 13.2f); curveTo(2.2f, 11.2f, 2.2f, 7.8f, 4.2f, 5.8f); curveTo(6.2f, 3.8f, 9.4f, 3.8f, 11.4f, 5.8f)
            lineTo(12f, 6.4f); lineTo(12.6f, 5.8f); curveTo(14.6f, 3.8f, 17.8f, 3.8f, 19.8f, 5.8f); curveTo(21.8f, 7.8f, 21.8f, 11.2f, 19.8f, 13.2f); close()
        }
    }

    /** A running figure: the run tracker. */
    val Run: ImageVector by lazy {
        filled("run") {
            moveTo(14.5f, 2f); curveTo(15.6f, 2f, 16.5f, 2.9f, 16.5f, 4f); curveTo(16.5f, 5.1f, 15.6f, 6f, 14.5f, 6f)
            curveTo(13.4f, 6f, 12.5f, 5.1f, 12.5f, 4f); curveTo(12.5f, 2.9f, 13.4f, 2f, 14.5f, 2f); close()
            moveTo(11.2f, 7.2f); lineTo(14.4f, 7.2f); lineTo(16.6f, 10.2f); lineTo(20f, 11.4f); lineTo(19.4f, 13.3f); lineTo(15.4f, 12f)
            lineTo(14.2f, 10.6f); lineTo(13.2f, 13.8f); lineTo(16f, 16.4f); lineTo(16f, 22f); lineTo(14f, 22f); lineTo(14f, 17.4f)
            lineTo(11.4f, 15.2f); lineTo(9.8f, 19.2f); lineTo(5f, 21.2f); lineTo(4.2f, 19.4f); lineTo(8.2f, 17.8f); lineTo(11.2f, 10.4f)
            lineTo(9.2f, 11.2f); lineTo(8.2f, 14f); lineTo(6.3f, 13.4f); lineTo(7.6f, 9.6f); close()
        }
    }

    /** A tick. */
    val Check: ImageVector by lazy { stroked("check", 2.4f) { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) } }

    /** A plus. */
    val Plus: ImageVector by lazy { stroked("plus", 2.2f) { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) } }

    val Back: ImageVector by lazy {
        stroked("back", 2.2f) { moveTo(20f, 12f); lineTo(5f, 12f); moveTo(11f, 5f); lineTo(4f, 12f); lineTo(11f, 19f) }
    }

    val Close: ImageVector by lazy { stroked("close", 2.2f) { moveTo(5f, 5f); lineTo(19f, 19f); moveTo(19f, 5f); lineTo(5f, 19f) } }

    /** A right-pointing chevron for list rows. */
    /** A minus, the partner to [Plus] on the map's zoom control. */
    val Minus: ImageVector by lazy { stroked("minus", 2.2f) { moveTo(5f, 12f); lineTo(19f, 12f) } }

    /** Crosshairs: put the map back on me. */
    val Recentre: ImageVector by lazy {
        stroked("recentre", 1.9f) {
            moveTo(12f, 3f); lineTo(12f, 6.5f)
            moveTo(12f, 17.5f); lineTo(12f, 21f)
            moveTo(3f, 12f); lineTo(6.5f, 12f)
            moveTo(17.5f, 12f); lineTo(21f, 12f)
            moveTo(17f, 12f)
            arcTo(5f, 5f, 0f, true, true, 7f, 12f)
            arcTo(5f, 5f, 0f, true, true, 17f, 12f)
            close()
        }
    }

    val Chevron: ImageVector by lazy { stroked("chevron") { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) } }

    /** A magnifier. */
    val Search: ImageVector by lazy {
        ImageVector.Builder("search", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round) {
                moveTo(10.5f, 3.5f); curveTo(14.4f, 3.5f, 17.5f, 6.6f, 17.5f, 10.5f); curveTo(17.5f, 14.4f, 14.4f, 17.5f, 10.5f, 17.5f)
                curveTo(6.6f, 17.5f, 3.5f, 14.4f, 3.5f, 10.5f); curveTo(3.5f, 6.6f, 6.6f, 3.5f, 10.5f, 3.5f); close()
                moveTo(15.8f, 15.8f); lineTo(21f, 21f)
            }
        }.build()
    }

    /** Password visibility. */
    val Eye: ImageVector by lazy {
        ImageVector.Builder("eye", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineJoin = StrokeJoin.Round) {
                moveTo(2f, 12f); curveTo(4.5f, 7f, 8f, 5f, 12f, 5f); curveTo(16f, 5f, 19.5f, 7f, 22f, 12f)
                curveTo(19.5f, 17f, 16f, 19f, 12f, 19f); curveTo(8f, 19f, 4.5f, 17f, 2f, 12f); close()
                moveTo(12f, 9f); curveTo(13.7f, 9f, 15f, 10.3f, 15f, 12f); curveTo(15f, 13.7f, 13.7f, 15f, 12f, 15f)
                curveTo(10.3f, 15f, 9f, 13.7f, 9f, 12f); curveTo(9f, 10.3f, 10.3f, 9f, 12f, 9f); close()
            }
        }.build()
    }
}
