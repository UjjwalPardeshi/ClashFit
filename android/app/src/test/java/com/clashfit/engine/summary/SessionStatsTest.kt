package com.clashfit.engine.summary

import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.FatigueState
import com.clashfit.core.model.RepRecord
import com.clashfit.core.model.Verdict
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionStatsTest {

    private fun mkRep(
        repIndex: Int,
        formScore: Float,
        depth: Float = 0.85f,
        rom: Float = 0.90f,
        tempo: Float = 0.80f,
        alignment: Float = 0.95f,
        verdict: Verdict = Verdict.CLEAN,
    ): RepRecord {
        return RepRecord(
            repIndex = repIndex,
            exerciseId = "squat",
            family = com.clashfit.core.model.Family.REP_CYCLE,
            tStartMs = repIndex * 5000L,
            tEndMs = repIndex * 5000L + 3000L,
            formScore = formScore,
            depth = depth,
            rom = rom,
            tempo = tempo,
            alignment = alignment,
            reason = "good",
            verdict = verdict,
            concentricVelocity = 120f,
            fatigue = FatigueState(
                value = 0.1f * repIndex,
                band = FatigueBand.FRESH,
                losses = mapOf("velocity" to 0.05f * repIndex, "rom" to 0.03f * repIndex),
                baselineReps = 3,
                hasBaseline = true,
            ),
            damage = 80,
            combo = 1.2f,
            depthCm = 40f + repIndex,
        )
    }

    @Test
    fun `session stats - empty reps`() {
        val stats = computeSessionStats(emptyList(), "squat")
        assertEquals(0, stats.reps)
        assertEquals(0f, stats.formMean)
        assertEquals(0, stats.velocityLossPct)
    }

    @Test
    fun `session stats - single rep`() {
        val reps = listOf(mkRep(1, 0.85f))
        val stats = computeSessionStats(reps, "squat")
        assertEquals(1, stats.reps)
        assertEquals(0.85f, stats.formMean)
        assertEquals(85, stats.formMeanPct)
    }

    @Test
    fun `session stats - form averages correctly`() {
        val reps = listOf(
            mkRep(1, 0.80f),
            mkRep(2, 0.90f),
            mkRep(3, 0.88f),
        )
        val stats = computeSessionStats(reps, "squat")
        assertEquals(3, stats.reps)
        val expected = (0.80f + 0.90f + 0.88f) / 3
        assertTrue(abs(stats.formMean - expected) < 0.01f)
    }

    @Test
    fun `session stats - first three and last three`() {
        val reps = (1..10).map { mkRep(it, 0.80f + it * 0.01f) }
        val stats = computeSessionStats(reps, "squat")
        assertEquals(10, stats.reps)

        // mkRep(it, 0.80f + it * 0.01f) over 1..10, so the scores run 0.81 .. 0.90.
        val first3 = (0.81f + 0.82f + 0.83f) / 3
        val last3 = (0.88f + 0.89f + 0.90f) / 3
        assertTrue(abs(stats.formFirst3 - first3) < 0.01f)
        assertTrue(abs(stats.formLast3 - last3) < 0.01f)
    }

    @Test
    fun `session stats - depth cm is tracked`() {
        val reps = listOf(
            mkRep(1, 0.85f).copy(depthCm = 42f),
            mkRep(2, 0.87f).copy(depthCm = 45f),
            mkRep(3, 0.86f).copy(depthCm = 43f),
        )
        val stats = computeSessionStats(reps, "squat")
        assertEquals(45, stats.depthCm) // max
    }

    @Test
    fun `session stats - fatigue series is built`() {
        val reps = listOf(
            mkRep(1, 0.85f),
            mkRep(2, 0.83f),
        )
        val stats = computeSessionStats(reps, "squat")
        assertEquals(2, stats.fatigueSeries.size)
        assertEquals(1, stats.fatigueSeries[0].repIndex)
        assertEquals(0.85f, stats.fatigueSeries[0].formScore)
    }

    @Test
    fun `csv export - header and rows`() {
        val reps = listOf(mkRep(1, 0.85f))
        val csv = exportCsv(reps)
        assertTrue(csv.contains("rep,form,verdict"))
        assertTrue(csv.contains("1,0.850,CLEAN"))
    }

    @Test
    fun `csv export - empty reps`() {
        val csv = exportCsv(emptyList())
        assertTrue(csv.contains("rep,form,verdict"))
        // Just the header
        val lines = csv.split("\n")
        assertEquals(2, lines.size) // header + empty line at end
    }

    @Test
    fun `csv export - multiple reps`() {
        val reps = listOf(
            mkRep(1, 0.85f),
            mkRep(2, 0.87f),
            mkRep(3, 0.83f),
        )
        val csv = exportCsv(reps)
        val lines = csv.trim().split("\n")
        assertEquals(4, lines.size) // header + 3 data rows
    }

    @Test
    fun `csv export - depth cm is optional`() {
        val reps = listOf(
            mkRep(1, 0.85f).copy(depthCm = 42f),
            mkRep(2, 0.87f).copy(depthCm = Float.NaN),
        )
        val csv = exportCsv(reps)
        assertTrue(csv.contains("42.0"))
        // NaN depth should be empty in CSV
    }

    @Test
    fun `json export - valid json structure`() {
        val reps = listOf(
            mkRep(1, 0.85f),
            mkRep(2, 0.87f),
        )
        val json = exportJson(reps)
        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]\n"))
        val open = json.count { it == '{' }
        val close = json.count { it == '}' }
        assertEquals(open, close)
        // Two rep objects, each carrying one nested fatigue object.
        assertEquals(2, Regex("\"repIndex\"").findAll(json).count())
        assertEquals(4, open)
    }

    @Test
    fun `json export - empty reps`() {
        val json = exportJson(emptyList())
        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]\n"))
        // Empty array
        assertEquals("[]\n", json)
    }

    @Test
    fun `json export - contains all rep fields`() {
        val reps = listOf(mkRep(1, 0.85f))
        val json = exportJson(reps)
        assertTrue(json.contains("\"repIndex\""))
        assertTrue(json.contains("\"formScore\""))
        assertTrue(json.contains("\"verdict\""))
        assertTrue(json.contains("\"damage\""))
        assertTrue(json.contains("\"combo\""))
    }

    @Test
    fun `session stats - velocity and rom loss from fatigue`() {
        val reps = listOf(
            mkRep(1, 0.85f).copy(
                fatigue = FatigueState(
                    value = 0.3f,
                    band = FatigueBand.WORKING,
                    losses = mapOf("velocity" to 0.12f, "rom" to 0.08f),
                    baselineReps = 3,
                    hasBaseline = true,
                )
            ),
        )
        val stats = computeSessionStats(reps, "squat")
        assertEquals(12, stats.velocityLossPct)
        assertEquals(8, stats.romLossPct)
    }
}
