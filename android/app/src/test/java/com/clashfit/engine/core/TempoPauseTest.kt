package com.clashfit.engine.core

import com.clashfit.core.model.RepEvent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The pause half of the tempo score has to discriminate, because bouncing is what it exists to
 * catch.
 *
 * It used to return a flat 0.6 for any pause below target. A rep with no pause at all and a rep
 * that paused for all but a hundredth of a second scored identically, which made the sub-score
 * blind exactly where the fault it names actually lives. Nothing pinned that behaviour, which is
 * why it survived: the eccentric term beside it always scaled, and nobody compared them.
 */
class TempoPauseTest {

    private fun rep(pauseSec: Float, eccSec: Float = 1f) = RepEvent(
        repIndex = 1, exerciseId = "squat", tStartMs = 0, tEndMs = 2000,
        thetaMin = 70f, thetaMax = 170f, uMin = 0f, uMax = 1f, uTopRef = 1f, uTarget = 0.1f,
        tEccSec = eccSec, tBottomSec = pauseSec, tConSec = 0.5f,
        concentricVelocity = 0.8f, gapSec = 0f, validFrameRatio = 1f,
    )

    @Test
    fun `a longer pause always scores at least as well as a shorter one`() {
        val steps = listOf(0f, 0.02f, 0.05f, 0.08f, 0.11f, 0.12f, 0.30f)
        val scores = steps.map { FormScorer.tempo(rep(it)) }
        scores.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue(b >= a, "pause ${steps[i]}s scored $a but ${steps[i + 1]}s scored $b")
        }
    }

    @Test
    fun `no pause at all scores strictly worse than most of a pause`() {
        val none = FormScorer.tempo(rep(0f))
        val nearly = FormScorer.tempo(rep(0.11f))
        assertTrue(
            nearly > none,
            "a bounce ($none) must not score the same as an almost-complete pause ($nearly)",
        )
    }

    @Test
    fun `meeting the pause target is full marks for that half`() {
        val met = FormScorer.tempo(rep(0.12f, eccSec = 1f))
        val over = FormScorer.tempo(rep(0.90f, eccSec = 1f))
        assertEquals(met, over, 1e-4f, "pausing longer than the target adds nothing further")
        assertEquals(1f, met, 1e-4f, "a full eccentric and a met pause is a perfect tempo")
    }

    @Test
    fun `the score stays inside nought to one for absurd input`() {
        listOf(-5f, 0f, 1_000f).forEach { pause ->
            listOf(-5f, 0f, 1_000f).forEach { ecc ->
                val v = FormScorer.tempo(rep(pause, ecc))
                assertTrue(v in 0f..1f, "tempo was $v for pause=$pause ecc=$ecc")
            }
        }
    }
}
