package com.clashfit.perception

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.Landmarks
import com.clashfit.core.model.Side
import com.clashfit.engine.core.Geometry
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The BlazePose points, drawn the way fitmon draws them: a red dot on every landmark the model is
 * sure of, blue lines along the twelve body connections, and nothing else. The joints of the angle
 * this exercise is counted from are drawn larger, with the angle's value beside the vertex, so you
 * can see the number the counter is reading while you move.
 *
 * `landmarks` are image-space and already mirrored to match the preview, which fills the view and
 * centre-crops; the same mapping places the dots on the body.
 */
@Composable
fun ExercisePoints(
    landmarks: Landmarks?,
    spec: ExerciseSpec?,
    angleLeft: Float,
    angleRight: Float,
    modifier: Modifier = Modifier,
    sourceAspect: Float? = null,
) {
    val measurer = rememberTextMeasurer()
    val vertices = remember(spec) { verticesOf(spec) }
    Canvas(modifier) {
        val lm = landmarks ?: return@Canvas
        if (lm.size < 33) return@Canvas

        // PreviewView fills the view and crops the overflow; reproduce that or the dots sit off the body.
        val aspect = sourceAspect
        val spanX: Float; val spanY: Float; val offsetX: Float; val offsetY: Float
        if (aspect == null || aspect <= 0f) {
            spanX = size.width; spanY = size.height; offsetX = 0f; offsetY = 0f
        } else {
            val fill = maxOf(size.width / aspect, size.height)
            spanX = aspect * fill; spanY = fill
            offsetX = (size.width - spanX) / 2f; offsetY = (size.height - spanY) / 2f
        }
        fun pt(i: Int) = Offset(offsetX + lm[i].x * spanX, offsetY + lm[i].y * spanY)
        fun seen(i: Int) = i in lm.indices && lm[i].visibility > 0.5f

        for ((a, b) in CONNECTIONS) {
            if (!seen(a) || !seen(b)) continue
            drawLine(LINE, pt(a), pt(b), strokeWidth = 6f)
        }
        for (i in lm.indices) {
            if (!seen(i)) continue
            drawCircle(DOT, radius = 7f, center = pt(i))
        }
        for ((side, joints) in vertices) {
            val (a, b, c) = joints
            val deg = if (side == Side.LEFT) angleLeft else angleRight
            if (!seen(a) || !seen(b) || !seen(c)) continue
            drawCircle(MEASURED, radius = 13f, center = pt(b))
            if (!deg.isFinite()) continue
            val layout = measurer.measure("${deg.toInt()}°", TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White))
            val p = pt(b)
            val at = Offset(p.x + 22f, p.y - layout.size.height / 2f)
            drawRoundRect(SHADE, topLeft = Offset(at.x - 9f, at.y - 5f), size = Size(layout.size.width + 18f, layout.size.height + 10f), cornerRadius = CornerRadius(9f, 9f))
            drawText(layout, topLeft = at)
        }
    }
}

// fitmon's exact connection list (client_side/script.js, drawPose).
private val CONNECTIONS = listOf(
    11 to 12, 12 to 24, 11 to 23, 23 to 24,
    11 to 13, 13 to 15, 12 to 14, 14 to 16,
    23 to 25, 25 to 27, 24 to 26, 26 to 28,
)
private val DOT = Color(0xFFFF3B30)        // fitmon draws every keypoint red
private val LINE = Color(0xFF2F6BFF)       // and every bone blue
private val MEASURED = Color(0xFFFFC400)   // the vertex of the angle being counted
private val SHADE = Color(0f, 0f, 0f, 0.6f)

/** The landmark triple of the counted angle, per side. */
private fun verticesOf(spec: ExerciseSpec?): List<Pair<Side, Triple<Int, Int, Int>>> {
    val pa = spec?.detector?.get("primaryAngle")?.jsonObject ?: return emptyList()
    val names = listOf("a", "b", "c").map { pa[it]?.jsonPrimitive?.content ?: return emptyList() }
    return Side.values().mapNotNull { side ->
        val idx = names.map { Geometry.idx(it, side) }
        if (idx.any { it < 0 }) null else side to Triple(idx[0], idx[1], idx[2])
    }
}
