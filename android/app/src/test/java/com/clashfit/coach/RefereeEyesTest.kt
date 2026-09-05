package com.clashfit.coach

import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.FatigueState
import com.clashfit.core.model.Family
import com.clashfit.core.model.RepRecord
import com.clashfit.core.model.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the referee asks, and what it is willing to repeat.
 *
 * The model is looking at a photograph of somebody mid-squat. Asked cold it will describe a person
 * in a room; asked with the rep's own measurements it looks for the thing that explains the worst
 * one, which is the only answer worth showing. And whatever comes back has to be shown to a person,
 * so a refusal, a preamble or a three-paragraph essay must never reach the screen.
 */
class RefereeEyesTest {

    private fun rep(
        depth: Float = 0.9f, rom: Float = 0.9f, tempo: Float = 0.9f, alignment: Float = 0.9f,
        reason: String = "ok",
    ) = RepRecord(
        repIndex = 6, exerciseId = "squat", family = Family.REP_CYCLE,
        tStartMs = 0, tEndMs = 1800,
        formScore = 0.6f, depth = depth, rom = rom, tempo = tempo, alignment = alignment,
        reason = reason, verdict = Verdict.OK, concentricVelocity = 0.8f,
        fatigue = FatigueState(0.4f, FatigueBand.WORKING, emptyMap(), 10, true),
        damage = 40, combo = 1f,
    )

    // ── the question ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the prompt carries the measurements so the model explains rather than invents`() {
        val p = RefereeEyes.prompt(rep(alignment = 0.61f, reason = "alignment"), "Squat")
        assertTrue("names the exercise", p.contains("squat"))
        assertTrue("gives alignment as a percentage", p.contains("61%"))
        assertTrue("forbids contradiction", p.contains("Do not contradict"))
        assertTrue("asks about the weakest part", p.contains("joint alignment"))
        assertTrue("offers a way to refuse", p.contains("NO VIEW"))
    }

    @Test
    fun `the scorer's own blame is what gets asked about`() {
        assertTrue(RefereeEyes.prompt(rep(reason = "depth"), "Squat").contains("why depth scored lowest"))
        assertTrue(RefereeEyes.prompt(rep(reason = "tempo"), "Squat").contains("why tempo scored lowest"))
    }

    @Test
    fun `with no named reason the lowest sub-score is blamed`() {
        val p = RefereeEyes.prompt(rep(depth = 0.95f, rom = 0.9f, tempo = 0.42f, alignment = 0.88f, reason = "ok"), "Push-up")
        assertTrue("tempo was lowest", p.contains("why tempo scored lowest"))
    }

    @Test
    fun `a sub-score that does not apply is not offered as the culprit`() {
        // Holds have no tempo: NaN must never win a minBy and be blamed for the rep.
        val p = RefereeEyes.prompt(rep(depth = 0.5f, rom = 0.8f, tempo = Float.NaN, alignment = 0.9f, reason = "ok"), "Plank")
        assertTrue("depth was the lowest real score", p.contains("why depth scored lowest"))
        assertTrue("and NaN is reported honestly", p.contains("tempo not measured"))
    }

    // ── the answer ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `one clean sentence passes through`() {
        assertEquals(
            "Your left knee is collapsing inward at the bottom.",
            RefereeEyes.clean("Your left knee is collapsing inward at the bottom."),
        )
    }

    @Test
    fun `only the first sentence survives`() {
        assertEquals(
            "Your hips rise before your chest.",
            RefereeEyes.clean("Your hips rise before your chest. This is a common fault. Try slowing down."),
        )
    }

    @Test
    fun `a refusal shows nothing rather than a guess`() {
        assertNull(RefereeEyes.clean("NO VIEW"))
        assertNull(RefereeEyes.clean("NO VIEW — the frame is too dark to judge."))
    }

    @Test
    fun `quotes and excitement are stripped`() {
        assertEquals(
            "Your back is rounding near the floor.",
            RefereeEyes.clean("\"Your back is rounding near the floor!\""),
        )
    }

    @Test
    fun `nothing useless reaches the screen`() {
        assertNull("empty", RefereeEyes.clean("   "))
        assertNull("too short to say anything", RefereeEyes.clean("Knee."))
        assertNull("an essay with no full stop", RefereeEyes.clean("a".repeat(200)))
    }
}
