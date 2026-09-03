package com.clashfit.engine.core

import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.Family
import com.clashfit.core.model.RepRecord
import com.clashfit.core.model.Verdict
import com.clashfit.core.model.FatigueState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.ok

class AsymmetryTest {
    private fun createRep(index: Int, asymmetry: RepAsymmetry?): RepRecord {
        return RepRecord(
            repIndex = index,
            exerciseId = "squat",
            family = Family.REP_CYCLE,
            tStartMs = index.toLong() * 2000,
            tEndMs = (index + 1).toLong() * 2000,
            formScore = 0.8f,
            depth = 0.8f,
            rom = 0.8f,
            tempo = 0.8f,
            alignment = 0.8f,
            reason = "clean",
            verdict = Verdict.CLEAN,
            concentricVelocity = 1.0f,
            fatigue = FatigueState.FRESH,
            damage = 0,
            combo = 1f,
            asymmetry = asymmetry,
        )
    }

    @Test
    fun `symmetric set reports no lean`() {
        val tr = AsymmetryTracker()
        // Feed symmetric frames that move together through range
        for (i in 0 until 30) {
            val progress = i.toFloat() / 30
            val leftAngle = 170f - (80f * progress)
            val rightAngle = 170f - (80f * progress)
            tr.onFrame(leftAngle, rightAngle, i * 33L)
        }
        val result = tr.forRep(0, 1000)
        assertNotNull(result)
        val lsi = result.lsi
        val deficit = result.deficitPct
        ok(deficit < 5, "saw a $deficit% deficit on a symmetric set")
    }

    @Test
    fun `guarded limb is detected and correct side is named`() {
        val tr = AsymmetryTracker()
        // Right side travels 30% less range (guarded)
        val lRange = 80f  // 170 - 90
        val rRange = 56f  // 170 - (170 - 80 * 0.3) = 170 - 146
        for (i in 0 until 30) {
            val progress = i.toFloat() / 30
            val leftAngle = 170f - (lRange * progress)
            val rightAngle = 170f - (rRange * progress)
            tr.onFrame(leftAngle, rightAngle, i * 33L)
        }
        val result = tr.forRep(0, 1000)
        assertNotNull(result)
        assertEquals("right", result.weakerSide, "named the wrong side")
        ok(result.deficitPct > 15, "deficit only ${result.deficitPct}% for a 30% guard")
    }

    @Test
    fun `scales with how much limb is guarded`() {
        fun run(rightBias: Float): Float {
            val tr = AsymmetryTracker()
            val lRange = 80f
            val rRange = 80f * (1f - rightBias)
            for (i in 0 until 30) {
                val progress = i.toFloat() / 30
                val leftAngle = 170f - (lRange * progress)
                val rightAngle = 170f - (rRange * progress)
                tr.onFrame(leftAngle, rightAngle, i * 33L)
            }
            return tr.forRep(0, 1000)?.deficitPct ?: 0f
        }
        val light = run(0.12f)
        val heavy = run(0.35f)
        ok(heavy > light + 8, "light $light% vs heavy $heavy% — not responsive")
    }

    @Test
    fun `refuses to speak when cannot see both sides`() {
        val tr = AsymmetryTracker()
        for (i in 0 until 4) {
            tr.onFrame(170f - i, 170f - i, i * 33L)  // too few frames
        }
        assertEquals(null, tr.forRep(0, 200), "reported a ratio from four frames")

        val tr2 = AsymmetryTracker()
        for (i in 0 until 40) {
            tr2.onFrame(Float.NaN, 170f, i * 33L)  // one side invisible
        }
        assertEquals(null, tr2.forRep(0, 2000), "reported a ratio with one side missing")
    }

    @Test
    fun `never diagnoses or clears anyone`() {
        val phrases = listOf(
            describeAsymmetry(AsymmetrySummary(0, false, "test")),
            describeAsymmetry(AsymmetrySummary(10, true, meanLsi = 97f, deficitPct = 3f, weakerSide = "left", consistency = 0.5f, consistent = false)),
            describeAsymmetry(AsymmetrySummary(10, true, meanLsi = 78f, deficitPct = 22f, weakerSide = "left", consistency = 0.9f, consistent = true)),
        )
        val forbidden = Regex("injur|risk|diagnos|cleared|abnormal", RegexOption.IGNORE_CASE)
        for (phrase in phrases) {
            ok(!forbidden.containsMatchIn(phrase), "clinical claim: $phrase")
        }
        ok(phrases[2].contains("physiotherapist", ignoreCase = true), "deficit should point at professional")
    }

    @Test
    fun `reaches coach as observation not verdict`() {
        val reps = mutableListOf<RepRecord>()
        val tr = AsymmetryTracker()

        // Generate 8 reps with right side guarded
        for (repIdx in 0 until 8) {
            val rRange = 56f  // Right travels 30% less
            val lRange = 80f
            val startMs = repIdx.toLong() * 2000
            val endMs = (repIdx + 1).toLong() * 2000

            for (i in 0 until 30) {
                val progress = i.toFloat() / 30
                val leftAngle = 170f - (lRange * progress)
                val rightAngle = 170f - (rRange * progress)
                tr.onFrame(leftAngle, rightAngle, startMs + i * 33)
            }

            val asymmetry = tr.forRep(startMs, endMs)
            reps.add(createRep(repIdx, asymmetry))
        }

        val summary = summariseAsymmetry(reps)
        ok(summary.enough, "not enough usable reps")
        ok(summary.consistent, "lean not consistent")
        assertEquals("right", summary.weakerSide)
        ok(summary.deficitPct > 10, "deficit too small")
    }
}
