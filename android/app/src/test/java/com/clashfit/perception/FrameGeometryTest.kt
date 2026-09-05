package com.clashfit.perception

import org.junit.Test
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The arithmetic that decides where the skeleton is drawn.
 *
 * Both halves of it were once missing from the camera: the frame was handed to the detector on its
 * side, and the overlay was told the sideways shape. The skeleton came out beside the player, in a
 * wide landscape box, which is exactly what these numbers describe when they are wrong.
 */
class FrameGeometryTest {
    /** The usual case: a phone held upright, whose sensor delivers a landscape frame. */
    private val quarterTurn = FrameGeometry.of(width = 1280, height = 720, rowStride = 1280 * 4, pixelStride = 4, rotationDeg = 90)

    @Test
    fun `a landscape buffer from an upright phone describes a portrait image`() {
        assertEquals(720, quarterTurn.width)
        assertEquals(1280, quarterTurn.height)
        assertEquals(0.5625f, quarterTurn.aspect, 1e-4f)
        assertTrue(quarterTurn.turned)
    }

    @Test
    fun `the aspect the overlay is given is the upright one, never the sensor's`() {
        // The overlay maps normalised landmarks through this number to undo the preview's crop.
        // Publishing 1280/720 here is what put the dots in a wide box beside the body.
        assertTrue(quarterTurn.aspect < 1f, "an upright phone shows a taller-than-wide image")
        assertEquals(1280f / 720f, FrameGeometry.of(1280, 720, 5120, 4, 0).aspect, 1e-4f)
    }

    @Test
    fun `a padded row is measured at its true stride and is not ready as it is`() {
        val padded = FrameGeometry.of(width = 1280, height = 720, rowStride = 5248, pixelStride = 4, rotationDeg = 0)
        assertEquals(1312, padded.bufferWidth, "the buffer stores 32 columns the image does not use")
        assertFalse(padded.readyAsIs, "copying this straight in would slide every row after the first")
        assertEquals(1280, padded.width)
    }

    @Test
    fun `a packed upright frame needs no work at all`() {
        val plain = FrameGeometry.of(width = 720, height = 1280, rowStride = 720 * 4, pixelStride = 4, rotationDeg = 0)
        assertTrue(plain.readyAsIs)
        assertEquals(720, plain.bufferWidth)
        assertEquals(0f, plain.translateX)
        assertEquals(0f, plain.translateY)
    }

    @Test
    fun `every rotation lands the image square inside the upright rectangle`() {
        for (rot in listOf(0, 90, 180, 270)) {
            val g = FrameGeometry.of(width = 1280, height = 720, rowStride = 1280 * 4, pixelStride = 4, rotationDeg = rot)
            val corners = listOf(0f to 0f, g.sourceWidth.toFloat() to 0f, 0f to g.sourceHeight.toFloat(), g.sourceWidth.toFloat() to g.sourceHeight.toFloat())
            val mapped = corners.map { (x, y) -> rotateThenTranslate(x, y, g) }
            assertEquals(0f, mapped.minOf { it.first }, 1e-3f, "rot $rot leaves the left edge at zero")
            assertEquals(0f, mapped.minOf { it.second }, 1e-3f, "rot $rot leaves the top edge at zero")
            assertEquals(g.width.toFloat(), mapped.maxOf { it.first }, 1e-3f, "rot $rot fills the width")
            assertEquals(g.height.toFloat(), mapped.maxOf { it.second }, 1e-3f, "rot $rot fills the height")
        }
    }

    @Test
    fun `padding columns fall outside the upright rectangle, where they are clipped`() {
        for (rot in listOf(0, 90, 180, 270)) {
            val g = FrameGeometry.of(width = 1280, height = 720, rowStride = 5248, pixelStride = 4, rotationDeg = rot)
            // A pixel in the padding, just past the last real column.
            val (x, y) = rotateThenTranslate(g.sourceWidth + 8f, 10f, g)
            assertTrue(x < 0f || x > g.width || y < 0f || y > g.height, "rot $rot must clip the padding, got ($x, $y)")
        }
    }

    @Test
    fun `a rotation that is not a quarter turn is snapped rather than trusted`() {
        assertEquals(90, FrameGeometry.of(1280, 720, 5120, 4, 91).rotationDeg)
        assertEquals(0, FrameGeometry.of(1280, 720, 5120, 4, 360).rotationDeg)
        assertEquals(270, FrameGeometry.of(1280, 720, 5120, 4, -90).rotationDeg)
    }

    @Test
    fun `a plane that reports no pixel stride falls back to the image width`() {
        val g = FrameGeometry.of(width = 640, height = 480, rowStride = 0, pixelStride = 0, rotationDeg = 0)
        assertEquals(640, g.bufferWidth)
        assertTrue(g.readyAsIs)
    }

    /** Android's Matrix, in the two calls the camera makes: postRotate then postTranslate. */
    private fun rotateThenTranslate(x: Float, y: Float, g: FrameGeometry): Pair<Float, Float> {
        val r = Math.toRadians(g.rotationDeg.toDouble())
        val c = cos(r).roundToInt().toFloat()
        val s = sin(r).roundToInt().toFloat()
        return (x * c - y * s) + g.translateX to (x * s + y * c) + g.translateY
    }
}
