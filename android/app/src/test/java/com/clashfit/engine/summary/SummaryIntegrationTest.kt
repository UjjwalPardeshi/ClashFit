package com.clashfit.engine.summary

import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.FatigueState
import com.clashfit.core.model.RepRecord
import com.clashfit.core.model.Verdict
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests covering the complete workflow of the summary package:
 * session statistics, progression, breathing, preflight, and clinic scoring.
 */
class SummaryIntegrationTest {

    private fun mkRep(
        repIndex: Int,
        formScore: Float = 0.85f,
        damage: Int = 80,
        velocityLoss: Float = 0.05f,
        romLoss: Float = 0.03f,
    ): RepRecord {
        return RepRecord(
            repIndex = repIndex,
            exerciseId = "squat",
            family = com.clashfit.core.model.Family.REP_CYCLE,
            tStartMs = repIndex * 5000L,
            tEndMs = repIndex * 5000L + 3000L,
            formScore = formScore,
            depth = 0.85f,
            rom = 0.90f,
            tempo = 0.80f,
            alignment = 0.95f,
            reason = "good",
            verdict = Verdict.CLEAN,
            concentricVelocity = 120f,
            fatigue = FatigueState(
                value = 0.05f * repIndex,
                band = if (repIndex <= 3) FatigueBand.FRESH else if (repIndex <= 6) FatigueBand.WORKING else FatigueBand.FADING,
                losses = mapOf("velocity" to velocityLoss, "rom" to romLoss),
                baselineReps = 3,
                hasBaseline = true,
            ),
            damage = damage,
            combo = 1.0f + 0.1f * repIndex,
            depthCm = 40f + repIndex,
        )
    }

    @Test
    fun `integration - complete session workflow`() {
        val reps = (1..8).map { mkRep(it, formScore = 0.85f - it * 0.02f) }

        // Compute session stats
        val stats = computeSessionStats(reps, "squat")
        assertEquals(8, stats.reps)
        assertEquals("squat", stats.exercise)
        assertTrue(stats.formMean < 0.80f)

        // Export to CSV and JSON. The CSV is per-rep telemetry only — the exercise names the
        // file, not a column — so what is checked here is the header and one row per rep.
        val csv = exportCsv(reps)
        assertTrue(csv.startsWith("rep,form,verdict"))
        assertEquals(9, csv.trim().split("\n").size) // header + 8 reps
        assertTrue(csv.contains("CLEAN"))

        val json = exportJson(reps)
        assertTrue(json.contains("repIndex"))
        assertTrue(json.contains("FADING")) // Last few reps in fading band
    }

    @Test
    fun `integration - progression with session updates`() {
        val prog = Progression()
        val base = java.time.Instant.parse("2026-08-03T10:00:00Z").toEpochMilli()

        // First session: 8 reps, good form
        prog.updateStreak(base)
        prog.updateBests("squat", 8, 0.85f)

        // Next day: 10 reps, better form
        prog.updateStreak(base + 86_400_000L)
        prog.updateBests("squat", 10, 0.87f)

        // Check bests
        val bests = prog.getBests("squat")
        assertEquals(10, bests.reps)
        assertEquals(0.87f, bests.formScore)

        // Check streak
        assertEquals(2, prog.getStreak().current)
        assertEquals("2 day streak", prog.streakLabel())
    }

    @Test
    fun `integration - breathing and recovery`() {
        val breathing = BreathingSession(inSec = 4f, holdInSec = 4f, outSec = 4f, holdOutSec = 4f, cycles = 1)
        breathing.start(0)

        // Run through full cycle
        val tick1 = breathing.tick(1000)
        assertEquals(BreathPhase.IN, tick1.phase)

        val tick2 = breathing.tick(16000)
        assertTrue(tick2.done)

        // Recovery calculation
        val recovery = recoveryFraction(1, 1, true)
        assertEquals(0.35f, recovery)
    }

    @Test
    fun `integration - preflight checks for demo`() {
        val probe = PreflightProbe(
            running = true,
            fps = 30f,
            inferMs = 14f,
            poseConfig = PoseConfigProbe(version = 1, debugOverlay = false),
            exerciseCount = 20,
            isOnline = false,
            audioEnabled = true,
            audioState = "running",
            speechEnabled = true,
            hapticsAvailable = true,
            sessionCount = 5,
            currentStreakDays = 3,
            calibrationExists = true,
            hasTraces = true,
        )

        val results = preflight(probe)
        val summary = summariseChecks(results)
        assertEquals(0, summary.fail)
        assertTrue(summary.ok)
        assertEquals("READY", summary.verdict)
    }

    @Test
    fun `integration - clinic scoring`() {
        val scorer = ClinicScorer("sit_to_stand_30s")
        val history = listOf(
            ClinicTrendPoint(1000, 10),
            ClinicTrendPoint(2000, 12),
            ClinicTrendPoint(3000, 14),
        )
        val result = scorer.score(14, history)

        assertEquals("sit_to_stand_30s", result.protocol)
        assertEquals(14, result.rawCount)
        assertEquals(3, result.trend.size)
        assertFalse(result.hasNormComparison)
    }

    @Test
    fun `integration - ladder progression tracking`() {
        val prog = Progression()

        // Simulate trend data
        val trend = listOf(
            Progression.TrendPoint("squat", 0.70f),
            Progression.TrendPoint("squat", 0.75f),
            Progression.TrendPoint("squat", 0.80f),
        )

        val result = prog.ladderCheck("SQUAT", Ladders.SQUAT, "squat", window = 3, trend = trend)
        // Average form is 0.75, below promotion threshold 0.85
        assertFalse(result.promoted)
        assertEquals("squat", result.rung) // Stays at current rung
    }

    @Test
    fun `integration - session, progression, and stats together`() {
        // Simulate a complete app session workflow
        val prog = Progression()
        val base = java.time.Instant.parse("2026-08-03T10:00:00Z").toEpochMilli()

        // Session 1
        val reps1 = (1..5).map { mkRep(it, 0.85f) }
        val stats1 = computeSessionStats(reps1, "squat")
        prog.updateStreak(base)
        prog.updateBests("squat", stats1.reps, stats1.formMean)

        // Session 2 (next day)
        val reps2 = (1..6).map { mkRep(it, 0.87f) }
        val stats2 = computeSessionStats(reps2, "squat")
        prog.updateStreak(base + 86_400_000L)
        prog.updateBests("squat", stats2.reps, stats2.formMean)

        // Check final state
        assertEquals(2, prog.getStreak().current)
        assertEquals(6, prog.getBests("squat").reps)
        assertEquals(0.87f, prog.getBests("squat").formScore)

        // Export both sessions
        val csv1 = exportCsv(reps1)
        val csv2 = exportCsv(reps2)
        assertTrue(csv1.contains("CLEAN"))
        assertTrue(csv2.contains("CLEAN"))
    }

    @Test
    fun `integration - check all preflight items present`() {
        val probe = PreflightProbe(
            running = true,
            fps = 30f,
            inferMs = 14f,
            poseConfig = PoseConfigProbe(debugOverlay = false),
            exerciseCount = 20,
            isOnline = false,
            audioEnabled = true,
            audioState = "running",
            speechEnabled = true,
            hapticsAvailable = true,
            sessionCount = 5,
            currentStreakDays = 3,
            calibrationExists = true,
            hasTraces = true,
        )

        val results = preflight(probe)
        val ids = results.map { it.id }

        // All required checks
        assertTrue(ids.contains("camera"))
        assertTrue(ids.contains("infer"))
        assertTrue(ids.contains("config"))
        assertTrue(ids.contains("library"))
        assertTrue(ids.contains("offline"))
        assertTrue(ids.contains("audio"))
        assertTrue(ids.contains("speech"))
        assertTrue(ids.contains("haptics"))
        assertTrue(ids.contains("storage"))
        assertTrue(ids.contains("calibration"))
        assertTrue(ids.contains("fallback"))
    }

    @Test
    fun `integration - day key and week key parsing`() {
        val base = java.time.Instant.parse("2026-08-03T10:00:00Z").toEpochMilli()
        val dayStr = dayKey(base)
        val weekStr = weekKey(base)

        assertEquals("2026-08-03", dayStr)
        assertTrue(weekStr.matches(Regex("\\d{4}-W\\d{2}")))
    }
}
