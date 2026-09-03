package com.clashfit.coach

import com.clashfit.core.model.CoachSource
import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.SetTelemetry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoachEngineTest {

    @Test
    fun `speakFor returns usable output from template when no LLM`() = runBlocking {
        val engine = CoachEngine(llm = null)
        val telemetry = SetTelemetry(
            exercise = "squat",
            reps = 5,
            formMean = 0.85f,
            formFirst3 = 0.85f,
            formLast3 = 0.80f,
            formMeanPct = 85,
            formFirst3Pct = 85,
            formLast3Pct = 80,
            depthCm = null,
            depthDropCm = null,
            velocityLossPct = 5,
            romLossPct = 0,
            fatigueBand = FatigueBand.WORKING,
            bestRep = SetTelemetry.RepRef(0, 0.90f),
            worstRep = SetTelemetry.RepRef(3, 0.70f, "depth"),
            comboMax = 1.0f,
            comboReps = 0,
            bossHpPct = 80,
            sessionSetIndex = 1,
            restSec = 45,
            trend = SetTelemetry.Trend.DECLINING,
        )

        val output = engine.speakFor(telemetry)

        assertNotNull(output.coachLine)
        assertNotNull(output.bossLine)
        assertTrue(output.coachLine.isNotEmpty(), "Coach line should not be empty")
        assertTrue(output.bossLine.isNotEmpty(), "Boss line should not be empty")
        assertTrue(output.source == CoachSource.TEMPLATE, "Should use template when no LLM")
    }

    @Test
    fun `speakFor handles zero reps gracefully`() = runBlocking {
        val engine = CoachEngine(llm = null)
        val telemetry = SetTelemetry(
            exercise = "squat",
            reps = 0,
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

        val output = engine.speakFor(telemetry)

        assertNotNull(output.coachLine)
        assertNotNull(output.bossLine)
        assertTrue(output.coachLine.isNotEmpty())
    }

    @Test
    fun `speakFor handles different fatigue bands`() = runBlocking {
        val engine = CoachEngine(llm = null)

        for (band in listOf(FatigueBand.FRESH, FatigueBand.WORKING, FatigueBand.FADING, FatigueBand.GASSED)) {
            val telemetry = SetTelemetry(
                exercise = "squat",
                reps = 8,
                formMean = 0.75f,
                formFirst3 = 0.80f,
                formLast3 = 0.70f,
                formMeanPct = 75,
                formFirst3Pct = 80,
                formLast3Pct = 70,
                depthCm = null,
                depthDropCm = null,
                velocityLossPct = 10,
                romLossPct = 5,
                fatigueBand = band,
                bestRep = SetTelemetry.RepRef(0, 0.85f),
                worstRep = SetTelemetry.RepRef(5, 0.65f, "tempo"),
                comboMax = 1.0f,
                comboReps = 0,
                bossHpPct = 60,
                sessionSetIndex = 2,
                restSec = 45,
                trend = SetTelemetry.Trend.FLAT,
            )

            val output = engine.speakFor(telemetry)

            assertNotNull(output.coachLine, "Coach line should not be null for band $band")
            assertTrue(output.coachLine.isNotEmpty(), "Coach line should not be empty for band $band")
        }
    }

    @Test
    fun `speakFor produces different boss lines for variety`() = runBlocking {
        val engine = CoachEngine(llm = null)
        val bosses = mutableSetOf<String>()

        for (i in 1..10) {
            val telemetry = SetTelemetry(
                exercise = "squat",
                reps = i,
                formMean = 0.80f,
                formFirst3 = 0.80f,
                formLast3 = 0.75f,
                formMeanPct = 80,
                formFirst3Pct = 80,
                formLast3Pct = 75,
                depthCm = null,
                depthDropCm = null,
                velocityLossPct = 8,
                romLossPct = 0,
                fatigueBand = FatigueBand.WORKING,
                bestRep = SetTelemetry.RepRef(0, 0.85f),
                worstRep = SetTelemetry.RepRef(i - 1, 0.75f, "depth"),
                comboMax = 1.0f,
                comboReps = i - 1,
                bossHpPct = 70,
                sessionSetIndex = i,
                restSec = 45,
                trend = SetTelemetry.Trend.FLAT,
            )

            val output = engine.speakFor(telemetry)
            bosses.add(output.bossLine)
        }

        // Boss lines should have some variety across different set indices
        assertTrue(bosses.size > 1, "Boss lines should vary across sets")
    }
}
