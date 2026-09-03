package com.clashfit.engine.coach

import com.clashfit.core.model.CombatState
import com.clashfit.core.model.CoachOutput
import com.clashfit.core.model.CoachSource
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.FatigueState
import com.clashfit.core.model.RepRecord
import com.clashfit.core.model.SetTelemetry
import com.clashfit.core.model.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking

class CoachTest {

    @Test
    fun `telemetry summarises a set into the model payload`() {
        val reps = (0..7).map { i ->
            RepRecord(
                repIndex = i,
                exerciseId = "squat",
                family = com.clashfit.core.model.Family.REP_CYCLE,
                tStartMs = i * 1000L,
                tEndMs = (i + 1) * 1000L,
                formScore = 0.85f,
                depth = 0.9f,
                rom = 0.92f,
                tempo = 0.80f,
                alignment = 0.88f,
                reason = "depth",
                verdict = Verdict.CLEAN,
                concentricVelocity = 0.5f,
                fatigue = FatigueState(0f, FatigueBand.FRESH, emptyMap(), 0, false),
                damage = 50,
                combo = 1f,
                depthCm = 42f,
            )
        }
        val combatState = CombatState(
            bossId = "boss1",
            bossName = "TestBoss",
            hp = 500,
            maxHp = 1000,
            phaseLabel = "FIGHTING",
            phaseModifier = 1f,
            reps = 0,
            totalDamage = 0,
            dead = false,
            comboStreak = 0,
            comboMultiplier = 1f,
            lastDamage = null,
            staggered = false,
            mercyActive = false,
        )
        val tel = TelemetrySummariser.summarise(reps, combatState, "squat", 1, 45)
        assertEquals("squat", tel.exercise)
        assertEquals(8, tel.reps)
        assertTrue(tel.formMean > 0)
        assertNotNull(tel.fatigueBand)
        assertNotNull(tel.worstRep)
        assertNotNull(tel.bestRep)
        assertTrue(tel.trend in listOf(SetTelemetry.Trend.IMPROVING, SetTelemetry.Trend.FLAT, SetTelemetry.Trend.DECLINING))
        assertEquals(50, tel.bossHpPct)
    }

    @Test
    fun `telemetry depth is measured in real centimetres`() {
        val reps = (0..5).map { i ->
            RepRecord(
                repIndex = i,
                exerciseId = "squat",
                family = com.clashfit.core.model.Family.REP_CYCLE,
                tStartMs = i * 1000L,
                tEndMs = (i + 1) * 1000L,
                formScore = 0.85f,
                depth = 0.9f,
                rom = 0.92f,
                tempo = 0.80f,
                alignment = 0.88f,
                reason = "depth",
                verdict = Verdict.CLEAN,
                concentricVelocity = 0.5f,
                fatigue = FatigueState(0f, FatigueBand.FRESH, emptyMap(), 0, false),
                damage = 50,
                combo = 1f,
                depthCm = 42f + i,
            )
        }
        val tel = TelemetrySummariser.summarise(reps, null, "squat", 1, 45)
        assertNotNull(tel.depthCm)
        assertTrue(tel.depthCm!! > 5 && tel.depthCm!! < 120)
    }

    @Test
    fun `templates never leak an unresolved placeholder`() {
        val bands = listOf(FatigueBand.FRESH, FatigueBand.WORKING, FatigueBand.FADING, FatigueBand.GASSED)
        val reasons = listOf("depth", "rom", "tempo", "alignment")
        val trends = listOf(SetTelemetry.Trend.IMPROVING, SetTelemetry.Trend.FLAT, SetTelemetry.Trend.DECLINING)

        for (band in bands) {
            for (reason in reasons) {
                for (trend in trends) {
                    for (holes in listOf(
                        emptyMap<String, Any?>(),
                        mapOf("depthCm" to null, "depthDropCm" to null)
                    )) {
                        val tel = SetTelemetry(
                            exercise = "squat",
                            reps = 9,
                            formMean = 0.7f,
                            formFirst3 = 0.8f,
                            formLast3 = 0.6f,
                            formMeanPct = 70,
                            formFirst3Pct = 80,
                            formLast3Pct = 60,
                            depthCm = if (holes.containsKey("depthCm")) null else 42,
                            depthDropCm = if (holes.containsKey("depthDropCm")) null else 4,
                            velocityLossPct = 30,
                            romLossPct = 18,
                            fatigueBand = band,
                            bestRep = SetTelemetry.RepRef(2, 0.9f),
                            worstRep = SetTelemetry.RepRef(8, 0.4f, reason),
                            comboMax = 1.6f,
                            comboReps = 5,
                            bossHpPct = 55,
                            sessionSetIndex = 2,
                            restSec = 45,
                            trend = trend,
                        )
                        val out = TemplateBank.templateFor(tel)
                        assertFalse(out.coachLine.contains("{"), "coach leaked placeholder: ${out.coachLine}")
                        assertFalse(out.coachLine.contains("}"), "coach leaked placeholder: ${out.coachLine}")
                        assertFalse(out.bossLine.contains("{"), "boss leaked placeholder: ${out.bossLine}")
                        assertFalse(out.bossLine.contains("}"), "boss leaked placeholder: ${out.bossLine}")
                        assertTrue(out.coachLine.isNotEmpty(), "empty coach line")
                        assertTrue(out.bossLine.isNotEmpty(), "empty boss line")
                    }
                }
            }
        }
    }

    @Test
    fun `templates cite numbers that are actually in the telemetry`() {
        val tel = SetTelemetry(
            exercise = "squat",
            reps = 10,
            formMean = 0.75f,
            formFirst3 = 0.85f,
            formLast3 = 0.65f,
            formMeanPct = 75,
            formFirst3Pct = 85,
            formLast3Pct = 65,
            depthCm = 42,
            depthDropCm = 4,
            velocityLossPct = 30,
            romLossPct = 18,
            fatigueBand = FatigueBand.WORKING,
            bestRep = SetTelemetry.RepRef(2, 0.9f),
            worstRep = SetTelemetry.RepRef(8, 0.4f, "depth"),
            comboMax = 1.6f,
            comboReps = 5,
            bossHpPct = 55,
            sessionSetIndex = 2,
            restSec = 45,
            trend = SetTelemetry.Trend.DECLINING,
        )
        val out = TemplateBank.templateFor(tel)
        assertTrue(OutputValidator.validateOutput(out.coachLine, tel).ok, "coach failed validation: ${out.coachLine}")
        assertTrue(OutputValidator.validateOutput(out.bossLine, tel).ok, "boss failed validation: ${out.bossLine}")
    }

    @Test
    fun `validator rejects hallucinated numbers, blocklist, length, sentence count`() {
        val tel = SetTelemetry(
            exercise = "squat",
            reps = 9,
            formMean = 0f,
            formFirst3 = 0f,
            formLast3 = 0f,
            formMeanPct = 0,
            formFirst3Pct = 0,
            formLast3Pct = 0,
            depthCm = null,
            depthDropCm = null,
            velocityLossPct = 30,
            romLossPct = 0,
            fatigueBand = FatigueBand.FRESH,
            bestRep = null,
            worstRep = null,
            comboMax = 0f,
            comboReps = 0,
            bossHpPct = 100,
            sessionSetIndex = 1,
            restSec = 45,
            trend = SetTelemetry.Trend.FLAT,
        )
        assertFalse(OutputValidator.validateOutput("You did 47 reps.", tel).ok, "hallucinated number allowed")
        assertFalse(OutputValidator.validateOutput("You look fat.", tel).ok, "blocklist term allowed")
        assertFalse(OutputValidator.validateOutput("a".repeat(200), tel).ok, "over-long allowed")
        assertFalse(OutputValidator.validateOutput("One. Two. Three. Four.", tel).ok, "four sentences allowed")
        assertFalse(OutputValidator.validateOutput("   ", tel).ok, "empty allowed")
        assertTrue(OutputValidator.validateOutput("You did 9 reps and lost 30 percent.", tel).ok, "valid line rejected")
    }

    @Test
    fun `coachFor falls back silently when the model times out`() = runBlocking {
        val tel = SetTelemetry(
            exercise = "squat",
            reps = 6,
            formMean = 0f,
            formFirst3 = 0f,
            formLast3 = 0f,
            formMeanPct = 0,
            formFirst3Pct = 0,
            formLast3Pct = 0,
            depthCm = null,
            depthDropCm = null,
            velocityLossPct = 0,
            romLossPct = 0,
            fatigueBand = FatigueBand.FRESH,
            bestRep = null,
            worstRep = null,
            comboMax = 0f,
            comboReps = 0,
            bossHpPct = 100,
            sessionSetIndex = 1,
            restSec = 45,
            trend = SetTelemetry.Trend.FLAT,
        )
        val slow = suspend { _: SetTelemetry -> null }
        val out = CoachFor(slow, 20L).speakFor(tel)
        assertEquals(CoachSource.TEMPLATE, out.source)
        assertTrue(out.coachLine.isNotEmpty(), "no fallback line")
    }

    @Test
    fun `coachFor falls back when the model hallucinates`() = runBlocking {
        val tel = SetTelemetry(
            exercise = "squat",
            reps = 6,
            formMean = 0f,
            formFirst3 = 0f,
            formLast3 = 0f,
            formMeanPct = 0,
            formFirst3Pct = 0,
            formLast3Pct = 0,
            depthCm = null,
            depthDropCm = null,
            velocityLossPct = 0,
            romLossPct = 0,
            fatigueBand = FatigueBand.FRESH,
            bestRep = null,
            worstRep = null,
            comboMax = 0f,
            comboReps = 0,
            bossHpPct = 100,
            sessionSetIndex = 1,
            restSec = 45,
            trend = SetTelemetry.Trend.FLAT,
        )
        val liar = suspend { _: SetTelemetry -> CoachOutput("You did 9999 perfect reps.", "ok", CoachSource.LLM) }
        val out = CoachFor(liar).speakFor(tel)
        assertEquals(CoachSource.TEMPLATE, out.source)
    }

    @Test
    fun `coachFor uses the model when its output is clean`() = runBlocking {
        val tel = SetTelemetry(
            exercise = "squat",
            reps = 6,
            formMean = 0f,
            formFirst3 = 0f,
            formLast3 = 0f,
            formMeanPct = 0,
            formFirst3Pct = 0,
            formLast3Pct = 0,
            depthCm = null,
            depthDropCm = null,
            velocityLossPct = 0,
            romLossPct = 0,
            fatigueBand = FatigueBand.FRESH,
            bestRep = null,
            worstRep = null,
            comboMax = 0f,
            comboReps = 0,
            bossHpPct = 100,
            sessionSetIndex = 1,
            restSec = 45,
            trend = SetTelemetry.Trend.FLAT,
        )
        val good = suspend { x: SetTelemetry -> CoachOutput("Solid set of ${x.reps}.", "Again.", CoachSource.LLM) }
        val out = CoachFor(good).speakFor(tel)
        assertEquals(CoachSource.LLM, out.source)
    }

    @Test
    fun `trend calculation with 5 reps uses ceil split`() {
        // With 5 reps: ceil(5/2) = 3, so first 3 and last 3 (overlap at index 2)
        // Test: first 3 reps have form 0.5, last 3 reps have form 0.8 → improving
        val reps = listOf(
            RepRecord(0, "squat", com.clashfit.core.model.Family.REP_CYCLE, 0L, 1000L, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, "depth", Verdict.SHALLOW, 0.5f, FatigueState(0f, FatigueBand.FRESH, emptyMap(), 0, false), 50, 1f),
            RepRecord(1, "squat", com.clashfit.core.model.Family.REP_CYCLE, 1000L, 2000L, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, "depth", Verdict.SHALLOW, 0.5f, FatigueState(0f, FatigueBand.FRESH, emptyMap(), 0, false), 50, 1f),
            RepRecord(2, "squat", com.clashfit.core.model.Family.REP_CYCLE, 2000L, 3000L, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, "depth", Verdict.SHALLOW, 0.5f, FatigueState(0f, FatigueBand.FRESH, emptyMap(), 0, false), 50, 1f),
            RepRecord(3, "squat", com.clashfit.core.model.Family.REP_CYCLE, 3000L, 4000L, 0.8f, 0.8f, 0.8f, 0.8f, 0.8f, "none", Verdict.OK, 0.8f, FatigueState(0f, FatigueBand.WORKING, emptyMap(), 3, true), 80, 1f),
            RepRecord(4, "squat", com.clashfit.core.model.Family.REP_CYCLE, 4000L, 5000L, 0.8f, 0.8f, 0.8f, 0.8f, 0.8f, "none", Verdict.OK, 0.8f, FatigueState(0f, FatigueBand.WORKING, emptyMap(), 3, true), 80, 1f),
        )
        val tel = TelemetrySummariser.summarise(reps, null, "squat", 1, 45)
        // Mean of first 3: (0.5 + 0.5 + 0.5) / 3 = 0.5
        // Mean of last 3: (0.5 + 0.8 + 0.8) / 3 = 0.7 (includes rep 2, the pivot)
        // Difference: 0.7 - 0.5 = 0.2 > 0.05 → IMPROVING
        assertEquals(SetTelemetry.Trend.IMPROVING, tel.trend, "5-rep trend should split with ceil, detecting improvement")
    }
}
