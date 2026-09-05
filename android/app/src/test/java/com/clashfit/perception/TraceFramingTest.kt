package com.clashfit.perception

import com.clashfit.core.model.Landmark
import com.clashfit.core.model.Landmarks
import com.clashfit.engine.core.Geometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A replayed set has to pass the framing check, or the replay never starts.
 *
 * The recorded trace stores world landmarks: metres, centred on the hip, y running from about
 * -0.45 at the head to +0.84 at the feet. Image landmarks are the fraction of the camera frame a
 * joint sits at, always between 0 and 1. Feeding the first in as the second made the framing check
 * measure a body 1.29 frames tall — "too close", forever, so calibration never completed and the
 * screen sat on "step back" until somebody gave up.
 *
 * That matters beyond tidiness: the demo script names trace replay as the escape when the room's
 * light beats the camera. This test is the proof the escape opens.
 */
class TraceFramingTest {

    /** The shipped framing band, from config/pose.json. */
    private val minBox = 0.55f
    private val maxBox = 0.92f

    @Test
    fun `the shipped trace, as world landmarks, fails the framing check`() {
        val frames = traceFrames()
        val worst = frames.maxOf { Geometry.boxHeight(it) }
        assertTrue(
            "world landmarks measure $worst frames tall, which is why replay never started",
            worst > maxBox,
        )
    }

    @Test
    fun `normalised into image space, every frame sits inside the band`() {
        val frames = traceFrames()
        val toImage = TraceGeometry.imageTransform(frames)
        frames.forEachIndexed { i, lms ->
            val h = Geometry.boxHeight(lms.map(toImage).filter { it.visibility > 0f })
            assertTrue("frame $i measured $h, outside $minBox..$maxBox", h in minBox..maxBox)
        }
    }

    @Test
    fun `the transform is one map, not one per frame`() {
        // A per-frame fit would rescale the body every time it moved, so a squat would look like
        // someone standing still while the room shrank. The body must genuinely get shorter on
        // screen as it goes down.
        val frames = traceFrames()
        val toImage = TraceGeometry.imageTransform(frames)
        val heights = frames.map { f -> Geometry.boxHeight(f.map(toImage).filter { it.visibility > 0f }) }
        assertTrue("the body's on-screen height must vary as it squats", heights.max() - heights.min() > 0.02f)
    }

    @Test
    fun `nothing lands outside the frame`() {
        val frames = traceFrames()
        val toImage = TraceGeometry.imageTransform(frames)
        frames.forEach { f ->
            f.map(toImage).filter { it.visibility > 0f }.forEach { lm ->
                assertTrue("x ${lm.x} outside 0..1", lm.x in 0f..1f)
                assertTrue("y ${lm.y} outside 0..1", lm.y in 0f..1f)
            }
        }
    }

    @Test
    fun `a joint the recorder never saw is left where it is`() {
        val toImage = TraceGeometry.imageTransform(traceFrames())
        val unseen = Landmark(0f, 0f, 0f, 0f)
        assertEquals(unseen, toImage(unseen))
    }

    // ── the shipped trace, read the way the app reads it ──────────────────────────────────────

    private fun traceFrames(): List<Landmarks> {
        val file = listOf(
            File("src/main/assets/traces/synthetic-f3-to-failure.jsonl"),
            File("app/src/main/assets/traces/synthetic-f3-to-failure.jsonl"),
        ).firstOrNull { it.isFile } ?: throw AssertionError("shipped trace not found from ${File(".").absolutePath}")

        val lines = file.readLines().filter { it.isNotBlank() }
        val keep = Regex(""""keep":\s*\[([^]]*)]""").find(lines.first())!!
            .groupValues[1].split(',').map { it.trim().toInt() }

        return lines.drop(1).mapNotNull { line ->
            val arrays = Regex("""\[([-\d.eE]+,[-\d.eE]+,[-\d.eE]+,[-\d.eE]+)]""")
                .findAll(line).map { m -> m.groupValues[1].split(',').map { it.trim().toFloat() } }.toList()
            if (arrays.isEmpty()) return@mapNotNull null
            val full = MutableList(33) { Landmark(0f, 0f, 0f, 0f) }
            keep.forEachIndexed { i, joint ->
                arrays.getOrNull(i)?.let { v -> full[joint] = Landmark(v[0], v[1], v[2], v[3]) }
            }
            full.toList()
        }
    }
}
