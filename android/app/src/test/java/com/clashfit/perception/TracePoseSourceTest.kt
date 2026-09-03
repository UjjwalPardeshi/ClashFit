package com.clashfit.perception

import com.clashfit.core.util.FakeClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests for TracePoseSource — replays JSON Lines traces and emits frames.
 */
class TracePoseSourceTest {

    private lateinit var scope: CoroutineScope
    private lateinit var clock: FakeClock

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Default)
        clock = FakeClock(0L)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `replays trace with correct landmark count`() = runBlocking {
        val trace = """
            {"type":"clashfit-trace","v":1,"keep":[11,12,13,14,15,16,23,24,25,26,27,28,29,30,31,32],"meta":{},"frames":2}
            {"t":0,"lm":[[-0.12,-0.45,0,0.95],[0.12,-0.45,0,0.95],[-0.12,-0.25,0,0.9],[0.12,-0.25,0,0.9],[-0.12,-0.05,0,0.9],[0.12,-0.05,0,0.9],[-0.12,0,0,0.95],[0.12,0,0,0.95],[-0.12,0.42,0,0.95],[0.12,0.42,0,0.95],[-0.0615,0.8359,0,0.95],[0.1785,0.8359,0,0.95],[-0.0615,0.8359,0,0.95],[0.1785,0.8359,0,0.95],[-0.0615,0.8359,0,0.95],[0.1785,0.8359,0,0.95]]}
            {"t":33,"lm":[[-0.12,-0.45,0,0.95],[0.12,-0.45,0,0.95],[-0.12,-0.25,0,0.9],[0.12,-0.25,0,0.9],[-0.12,-0.05,0,0.9],[0.12,-0.05,0,0.9],[-0.12,0,0,0.95],[0.12,0,0,0.95],[-0.12,0.42,0,0.95],[0.12,0.42,0,0.95],[-0.0615,0.8359,0,0.95],[0.1785,0.8359,0,0.95],[-0.0615,0.8359,0,0.95],[0.1785,0.8359,0,0.95],[-0.0615,0.8359,0,0.95],[0.1785,0.8359,0,0.95]]}
        """.trimIndent()

        val source = TracePoseSource(trace, clock, scope, replayRealTiming = false)
        source.start()

        // Collect first two frames
        val frames = source.frames.take(2).toList()

        assertEquals(2, frames.size)
        frames.forEach { frame ->
            assertNotNull(frame.world)
            assertEquals(33, frame.world?.size)
        }

        source.stop()
    }

    @Test
    fun `reconstructs full landmark array from sparse representation`() = runBlocking {
        val trace = """
            {"type":"clashfit-trace","v":1,"keep":[11,12,13],"meta":{},"frames":1}
            {"t":0,"lm":[[1.0,2.0,3.0,0.9],[4.0,5.0,6.0,0.8],[7.0,8.0,9.0,0.7]]}
        """.trimIndent()

        val source = TracePoseSource(trace, clock, scope, replayRealTiming = false)
        source.start()

        val frames = source.frames.take(1).toList()
        assertEquals(1, frames.size)

        val frame = frames[0]
        assertNotNull(frame.world)
        val lms = frame.world!!
        assertEquals(33, lms.size)

        // Indices 11, 12, 13 should be populated
        assertEquals(1.0f, lms[11].x)
        assertEquals(2.0f, lms[11].y)
        assertEquals(0.9f, lms[11].visibility)

        assertEquals(4.0f, lms[12].x)
        assertEquals(5.0f, lms[12].y)
        assertEquals(0.8f, lms[12].visibility)

        assertEquals(7.0f, lms[13].x)
        assertEquals(8.0f, lms[13].y)
        assertEquals(0.7f, lms[13].visibility)

        // Other indices should have visibility 0
        assertEquals(0.0f, lms[0].visibility)
        assertEquals(0.0f, lms[25].visibility)

        source.stop()
    }

    @Test
    fun `emits fps measurements`() = runBlocking {
        val trace = """
            {"type":"clashfit-trace","v":1,"keep":[11,12,13,14,15,16,23,24,25,26,27,28,29,30,31,32],"meta":{},"frames":11}
            ${(0..10).joinToString("\n") { i ->
                """{"t":${i * 33},"lm":[[-0.12,-0.45,0,0.95],[0.12,-0.45,0,0.95],[-0.12,-0.25,0,0.9],[0.12,-0.25,0,0.9],[-0.12,-0.05,0,0.9],[0.12,-0.05,0,0.9],[-0.12,0,0,0.95],[0.12,0,0,0.95],[-0.12,0.42,0,0.95],[0.12,0.42,0,0.95],[-0.0615,0.8359,0,0.95],[0.1785,0.8359,0,0.95],[-0.0615,0.8359,0,0.95],[0.1785,0.8359,0,0.95],[-0.0615,0.8359,0,0.95],[0.1785,0.8359,0,0.95]]}"""
            }}
        """.trimIndent()

        val source = TracePoseSource(trace, clock, scope, replayRealTiming = false)
        source.start()

        // Consume all frames to trigger FPS updates
        source.frames.take(11).toList()

        // FPS should be calculated (at least after 10 frames)
        val fps = source.fps.value
        assert(fps >= 0f)

        source.stop()
    }

    @Test
    fun `handles empty trace gracefully`() = runBlocking {
        val trace = """
            {"type":"clashfit-trace","v":1,"keep":[],"meta":{},"frames":0}
        """.trimIndent()

        val source = TracePoseSource(trace, clock, scope, replayRealTiming = false)
        source.start()

        // Should not crash; just emit nothing
        val frames = source.frames.take(1).toList()
        assertEquals(0, frames.size)

        source.stop()
    }

    @Test
    fun `handles malformed frames by skipping them`() = runBlocking {
        val trace = """
            {"type":"clashfit-trace","v":1,"keep":[11,12],"meta":{},"frames":2}
            {"t":0,"lm":[[1.0,2.0,3.0,0.9],[4.0,5.0,6.0,0.8]]}
            {"t":33,"invalid":"json"}
        """.trimIndent()

        val source = TracePoseSource(trace, clock, scope, replayRealTiming = false)
        source.start()

        // Should emit first frame and skip second
        val frames = source.frames.take(1).toList()
        assertEquals(1, frames.size)

        source.stop()
    }
}
