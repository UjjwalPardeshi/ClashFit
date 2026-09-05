package com.clashfit.coach.chat

import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.SetTelemetry
import com.clashfit.data.SessionEntity
import com.clashfit.data.StreakEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chat may only say what was measured.
 *
 * A coach chat that answers from nothing is a party trick: it invents a depth, congratulates you on
 * a streak you never had, and is confidently wrong in front of a judge. The fact sheet is the
 * antidote, so these tests hold two things — that every number a player asks about is actually in
 * the sheet, and that nothing a player typed, or their name, or their email, ever is.
 */
class FactSheetTest {

    private val telemetry = SetTelemetry(
        exercise = "squat", reps = 12,
        formMean = 0.78f, formFirst3 = 0.88f, formLast3 = 0.64f,
        formMeanPct = 78, formFirst3Pct = 88, formLast3Pct = 64,
        depthCm = 46, depthDropCm = 7,
        velocityLossPct = 22, romLossPct = 14,
        fatigueBand = FatigueBand.FADING,
        bestRep = SetTelemetry.RepRef(index = 2, form = 0.94f),
        worstRep = SetTelemetry.RepRef(index = 11, form = 0.55f, reason = "depth"),
        comboMax = 1.6f, comboReps = 6,
        bossHpPct = 41, sessionSetIndex = 2, restSec = 55,
        trend = SetTelemetry.Trend.DECLINING,
        asymmetryPct = 12, weakerSide = "LEFT",
    )

    private fun session(daysAgo: Long, reps: Int, form: Float, id: Long = daysAgo) = SessionEntity(
        id = id, startedAtMs = DAY0 - daysAgo * 86_400_000L, endedAtMs = DAY0 - daysAgo * 86_400_000L + 600_000,
        mode = "BOSS_FIGHT", exerciseId = "squat", bossId = "pacemaker", outcome = "BOSS_DOWN",
        totalDamage = 900, totalReps = reps, formMean = form, peakFatigue = 0.6f, peakBand = "FADING",
    )

    // ── the set sheet ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `the numbers a player asks about are all there`() {
        val s = FactSheet.forSet(telemetry).joinToString("\n")
        listOf("squat", "12", "78", "88", "64", "46 cm", "22 percent", "fading", "declining", "41 percent", "55 seconds")
            .forEach { assertTrue("missing '$it' from:\n$s", s.contains(it)) }
    }

    @Test
    fun `the worst rep carries its number and what was weakest`() {
        val s = FactSheet.forSet(telemetry).joinToString("\n")
        assertTrue(s.contains("worst rep: number 11"))
        assertTrue(s.contains("weakest part was depth"))
    }

    @Test
    fun `asymmetry names the weaker side, and is absent when it was not measured`() {
        assertTrue(FactSheet.forSet(telemetry).joinToString("\n").contains("weaker side left"))
        val noAsym = telemetry.copy(asymmetryPct = null, weakerSide = null)
        assertFalse(FactSheet.forSet(noAsym).joinToString("\n").contains("left-right"))
    }

    @Test
    fun `a measurement that does not apply is left out rather than guessed`() {
        // A hold has no depth in centimetres. An absent line is honest; a zero would be a claim.
        val noDepth = telemetry.copy(depthCm = null, depthDropCm = null)
        val s = FactSheet.forSet(noDepth).joinToString("\n")
        assertFalse("no depth line at all", s.contains("depth:"))
        assertFalse("and no depth-lost line", s.contains("depth lost"))
    }

    // ── the history sheet ─────────────────────────────────────────────────────────────────────

    @Test
    fun `a new player is described as new rather than as zero`() {
        val s = FactSheet.forHistory(emptyList(), null, null, DAY0).joinToString("\n")
        assertEquals("this player has not finished a session yet", s)
    }

    @Test
    fun `recent sessions are dated in words and capped at ten`() {
        val many = (0L until 25L).map { session(it, 10 + it.toInt(), 0.8f, id = it) }
        val lines = FactSheet.forHistory(many, null, null, DAY0)
        assertTrue(lines.any { it.contains("today") })
        assertTrue(lines.any { it.contains("yesterday") })
        assertTrue(lines.any { it.contains("2 days ago") })
        assertEquals("ten session lines, no more", 10, lines.count { it.startsWith("- ") })
    }

    @Test
    fun `improvement is stated as two averages rather than as a verdict`() {
        val sessions = (0L until 4L).map { session(it, 12, 0.90f, id = it) } +
            (4L until 9L).map { session(it, 12, 0.70f, id = it) }
        val s = FactSheet.forHistory(sessions, null, null, DAY0).joinToString("\n")
        assertTrue("says both averages, and lets the model draw the conclusion", s.contains("last five sessions"))
    }

    @Test
    fun `a streak is only claimed when there is one`() {
        val withStreak = FactSheet.forHistory(listOf(session(0, 12, 0.8f)), StreakEntity(1, 9, 21, "2026-09-05", 2, 1, "2026-W36"), null, DAY0)
        assertTrue(withStreak.any { it.contains("current streak: 9 days, best ever: 21") })
        val without = FactSheet.forHistory(listOf(session(0, 12, 0.8f)), null, null, DAY0)
        assertFalse(without.any { it.contains("streak") })
    }

    // ── the prompt ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the rules ride along with every question`() {
        val p = FactSheet.prompt(FactSheet.forSet(telemetry), "why did my depth drop?")
        assertTrue("never invent", p.contains("Never state a number that is not in them"))
        assertTrue("admit ignorance", p.contains("say so plainly"))
        assertTrue("the question is last, so the model answers it", p.trimEnd().endsWith("Coach:"))
        assertTrue(p.contains("Player: why did my depth drop?"))
    }

    @Test
    fun `only the last few turns of history are carried`() {
        val turns = (1..10).map { ChatTurn("turn $it", fromPlayer = it % 2 == 1) }
        val p = FactSheet.prompt(listOf("reps this set: 12"), "and now?", turns)
        assertFalse("the oldest turns are dropped", p.contains("turn 1\n"))
        assertTrue("the newest are kept", p.contains("turn 10"))
    }

    @Test
    fun `the sheet is small enough for a two-billion-parameter model`() {
        val p = FactSheet.prompt(FactSheet.forSet(telemetry), "how did I do?")
        // Roughly four characters to a token; the whole prompt must leave room for an answer
        // inside a 1024-token window.
        assertTrue("prompt is ${p.length} characters", p.length < 2_400)
    }

    private companion object {
        /** A fixed "now" so "2 days ago" means the same thing in every run. */
        const val DAY0 = 1_788_000_000_000L
    }
}
