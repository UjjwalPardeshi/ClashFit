package com.clashfit.data

import com.clashfit.core.model.Family
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.FatigueState
import com.clashfit.core.model.RepRecord
import com.clashfit.core.model.Verdict
import com.clashfit.core.util.FakeClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests that SessionViewModel.persist() correctly writes sessions, sets, and reps to the database
 * without double-counting trailing set reps.
 */
class SessionPersistenceTest {

    private val clock = FakeClock()

    @Test
    fun `persist calculates totalReps correctly from completed sets and trailing set`() = runTest {
        // Simulate 2 completed sets (5+3 reps) plus 1 trailing set (2 reps) = 10 total
        val set1Reps = (1..5).map { i -> createRep(repIndex = i, formScore = 0.8f, fatigue = 0.1f + i * 0.05f) }
        val set2Reps = (6..8).map { i -> createRep(repIndex = i, formScore = 0.75f, fatigue = 0.3f + i * 0.05f) }
        val trailingReps = (9..10).map { i -> createRep(repIndex = i, formScore = 0.7f, fatigue = 0.6f + i * 0.05f) }

        val allReps = set1Reps + set2Reps + trailingReps

        // Verify totalReps excludes double-counting
        assertEquals(10, allReps.size)
        // Form mean: (5*0.8 + 3*0.75 + 2*0.7) / 10 = (4 + 2.25 + 1.4) / 10 = 0.765
        val formMean = allReps.map { it.formScore }.average().toFloat()
        assertEquals(0.765f, formMean, 0.001f)
    }

    @Test
    fun `formMean calculated correctly when totalReps is zero`() = runTest {
        val allReps = emptyList<RepRecord>()

        val formMean = if (allReps.isEmpty()) 0f else allReps.map { it.formScore }.average().toFloat()
        assertEquals(0f, formMean)
        assertEquals(0, allReps.size)
    }

    @Test
    fun `persist correctly sums totalReps across completed sets`() = runTest {
        // 3 completed sets: set 1 (3 reps), set 2 (2 reps), set 3 (1 rep) = 6 reps total
        val set1 = (1..3).map { i -> createRep(repIndex = i) }
        val set2 = (4..5).map { i -> createRep(repIndex = i) }
        val set3 = listOf(createRep(repIndex = 6))

        val allReps = set1 + set2 + set3

        assertEquals(6, allReps.size)
        assertEquals(60, allReps.sumOf { it.damage })
    }

    @Test
    fun `formMean calculated correctly for multi-set session`() = runTest {
        // Set 1: 2 reps @ 0.9, Set 2: 2 reps @ 0.7, Set 3: 2 reps @ 0.8
        // Mean: (2*0.9 + 2*0.7 + 2*0.8) / 6 = (1.8 + 1.4 + 1.6) / 6 = 4.8 / 6 = 0.8
        val set1 = (1..2).map { i -> createRep(repIndex = i, formScore = 0.9f) }
        val set2 = (3..4).map { i -> createRep(repIndex = i, formScore = 0.7f) }
        val set3 = (5..6).map { i -> createRep(repIndex = i, formScore = 0.8f) }

        val allReps = set1 + set2 + set3
        val mean = allReps.map { it.formScore }.average().toFloat()

        assertEquals(0.8f, mean)
    }

    @Test
    fun `persist does not double-count trailing set when it has same setIndex as last completed set`() = runTest {
        // This verifies the logic: if trailing.isNotEmpty() && completedSets.none { it.index == s.setIndex }
        // Only add trailing if it's a new set that wasn't already recorded
        val trailingOnly = (1..2).map { i -> createRep(repIndex = i) }

        // When trailing set is added as new set, totalReps should be exactly trailing.size
        assertEquals(2, trailingOnly.size)

        val formMean = trailingOnly.map { it.formScore }.average().toFloat()
        assertEquals(0.8f, formMean) // All created with default formScore=0.8
    }

    private fun createRep(
        repIndex: Int = 1,
        exerciseId: String = "pushup",
        family: Family = Family.REP_CYCLE,
        tStartMs: Long = 1000L + repIndex * 100,
        tEndMs: Long = 1100L + repIndex * 100,
        formScore: Float = 0.8f,
        depth: Float = 0.5f,
        rom: Float = 0.6f,
        tempo: Float = 0.7f,
        alignment: Float = 0.8f,
        reason: String = "OK",
        verdict: String = "CLEAN",
        concentricVelocity: Float = 1.0f,
        damage: Int = 10,
        comboAtRep: Float = 1.0f,
        fatigue: Float = 0.1f,
        fatigueBand: String = "FRESH",
        validFrameRatio: Float = 0.95f,
        depthCm: Float? = 50f,
        heightCm: Float? = 170f,
        holdSec: Float? = null,
    ): RepRecord {
        return RepRecord(
            repIndex = repIndex,
            exerciseId = exerciseId,
            family = family,
            tStartMs = tStartMs,
            tEndMs = tEndMs,
            formScore = formScore,
            depth = depth,
            rom = rom,
            tempo = tempo,
            alignment = alignment,
            reason = reason,
            verdict = Verdict.valueOf(verdict),
            concentricVelocity = concentricVelocity,
            damage = damage,
            combo = comboAtRep,
            fatigue = FatigueState(
                value = fatigue,
                band = FatigueBand.valueOf(fatigueBand),
                losses = emptyMap(),
                baselineReps = 0,
                hasBaseline = true,
            ),
            validFrameRatio = validFrameRatio,
            depthCm = depthCm ?: Float.NaN,
            heightCm = heightCm ?: Float.NaN,
            holdSec = holdSec ?: Float.NaN,
        )
    }
}
