package com.clashfit.engine.summary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The catalogue is prose as much as it is numbers, and both can rot.
 *
 * These tests exist because the failure mode here is not a crash. It is a cue nobody can read at
 * arm's length, a caution that says nothing, a claim that promises more than a breathing exercise
 * can deliver, or a breaths-per-minute figure that is quietly half the truth. None of those would
 * ever throw.
 */
class BreathingPatternsTest {

    private val all = BreathingPatterns.ALL

    @Test
    fun `there are eight patterns and every id is distinct`() {
        assertEquals(8, all.size)
        assertEquals(all.size, all.map { it.id }.toSet().size, "two patterns share an id")
        assertEquals(all.size, all.map { it.name }.toSet().size, "two patterns share a name")
    }

    @Test
    fun `the goals cover every pattern and the picker shows all of them`() {
        val grouped = BreathingPatterns.BY_GOAL.flatMap { it.second }
        assertEquals(all.toSet(), grouped.toSet(), "a pattern is missing from the grouped picker")
        assertEquals(all.size, grouped.size, "a pattern appears twice in the grouped picker")
        // Both lung techniques are present, since they are the ones this app was asked for.
        assertEquals(2, all.count { it.goal == BreathingGoal.LUNGS })
    }

    @Test
    fun `the default is the one the other seven assume you can already do`() {
        assertTrue(BreathingPatterns.DEFAULT in all)
        assertEquals("diaphragmatic", BreathingPatterns.DEFAULT.id)
    }

    @Test
    fun `every cue is short enough to read at arm's length`() {
        all.forEach { p ->
            p.steps.forEach { step ->
                val words = step.cue.trim().split(Regex("\\s+")).size
                assertTrue(
                    words in 3..8,
                    "${p.id}: \"${step.cue}\" is $words words; a cue is read mid-breath",
                )
                assertTrue(step.cue.first().isUpperCase(), "${p.id}: \"${step.cue}\" should start a sentence")
            }
        }
    }

    @Test
    fun `no two cues inside one pattern differ only in the middle`() {
        // The failure this pins: alternate nostril once read "Close right, breathe in left" and
        // "Close right, breathe out left". At arm's length, eyes half closed, those are the same
        // line — the one word that distinguishes them is buried in the centre, with matching text
        // on both sides of it.
        //
        // A difference at the END is not the same problem and must not be flagged. "In — through
        // the left" and "In — through the right" are read left to right, and the word that matters
        // is the last one, which is where you want it. What makes a pair unreadable is being
        // sandwiched: the same opening AND the same ending, with the payload hidden between them.
        all.forEach { p ->
            val cues = p.steps.map { it.cue }
            cues.forEachIndexed { i, a ->
                cues.drop(i + 1).forEach { b ->
                    val sharedPrefix = a.commonPrefixWith(b).length
                    val sharedSuffix = a.commonSuffixWith(b).length
                    assertTrue(
                        sharedPrefix < 8 || sharedSuffix < 4,
                        "${p.id}: \"$a\" and \"$b\" match at both ends; " +
                            "the word that tells them apart is buried in the middle",
                    )
                }
            }
        }
    }

    @Test
    fun `every pattern says who should be careful, and means something by it`() {
        all.forEach { p ->
            assertTrue(p.caution.length > 40, "${p.id}: the caution is too short to be specific")
            assertTrue(p.whyItWorks.isNotBlank(), "${p.id}: no explanation")
            assertTrue(p.whenToUse.isNotBlank(), "${p.id}: no occasion")
            assertTrue(p.evidence.length > 40, "${p.id}: the evidence note says nothing")
        }
    }

    @Test
    fun `nothing in the catalogue makes a medical claim`() {
        // A fitness app that starts treating conditions has stopped being a fitness app. The words
        // below are the ones that would put it there; the honest register is "may help you feel".
        val forbidden = listOf(
            "cure", "cures", "treat", "treats", "treatment", "diagnos",
            "lowers blood pressure", "reduces blood pressure", "prevents", "heals",
            "remedy", "therapy for", "clinically proven",
        )
        all.forEach { p ->
            val prose = listOf(p.whyItWorks, p.whenToUse, p.evidence, p.rhythm)
                .joinToString(" ").lowercase()
            forbidden.forEach { word ->
                assertTrue(
                    !prose.contains(word),
                    "${p.id}: \"$word\" is a medical claim; this app treats nothing",
                )
            }
        }
    }

    @Test
    fun `breaths per minute counts breaths, not passes through the pattern`() {
        // Alternate nostril's round is in-left, out-right, in-right, out-left: sixteen seconds and
        // two whole breaths. Dividing sixty by the cycle length calls that 3.75, which is half the
        // truth, and it is the one pattern where cycle and breath are not the same thing.
        val nostril = BreathingPatterns.ALTERNATE_NOSTRIL
        assertEquals(16f, nostril.cycleSec, 0.01f)
        assertEquals(2, nostril.breathsPerCycle)
        assertEquals(7.5f, nostril.breathsPerMinute, 0.01f)

        // The sigh's two inhales are one breath taken in two sips, so it stays at one.
        val sigh = BreathingPatterns.PHYSIOLOGICAL_SIGH
        assertEquals(1, sigh.breathsPerCycle)
        assertEquals(13f, sigh.cycleSec, 0.01f)

        // Coherent breathing is named for its rate; if this is not six, the name is wrong.
        assertEquals(6f, BreathingPatterns.COHERENT.breathsPerMinute, 0.01f)
    }

    @Test
    fun `the physiological sigh really does inhale twice before it exhales`() {
        // The reason the engine stopped being four fixed slots. A session built on the old shape
        // could not express this pattern at all, which is why the app only ever had three.
        val steps = BreathingPatterns.PHYSIOLOGICAL_SIGH.steps
        assertEquals(BreathPhase.IN, steps[0].phase)
        assertEquals(BreathPhase.IN, steps[1].phase)
        assertEquals(BreathPhase.OUT, steps[2].phase)
        assertTrue(steps[1].seconds < steps[0].seconds, "the second sip is the shorter one")
    }

    @Test
    fun `a session walks two consecutive inhales as two separate phases`() {
        val session = BreathingPatterns.PHYSIOLOGICAL_SIGH.session(cycles = 1)
        session.start(0L)
        val first = session.tick(1_000L)      // 1s in: first inhale
        val second = session.tick(4_500L)     // 4.5s in: the sip
        val out = session.tick(6_000L)        // 6s in: the sigh

        assertEquals(BreathPhase.IN, first.phase)
        assertEquals(BreathPhase.IN, second.phase)
        assertEquals(0, first.stepIndex)
        assertEquals(1, second.stepIndex, "the sip must be its own step, not a continuation")
        assertTrue(second.changed, "moving from the inhale to the sip is a phase change")
        assertEquals(BreathPhase.OUT, out.phase)
    }

    @Test
    fun `alternate nostril names a side on every phase`() {
        val sides = setOf(BreathRoute.LEFT_NOSTRIL, BreathRoute.RIGHT_NOSTRIL)
        BreathingPatterns.ALTERNATE_NOSTRIL.steps.forEach { step ->
            assertTrue(step.route in sides, "\"${step.cue}\" does not say which nostril")
        }
    }

    @Test
    fun `holds never claim a route, because you are not breathing through anything`() {
        all.forEach { p ->
            p.steps.filter { it.phase == BreathPhase.HOLD_IN || it.phase == BreathPhase.HOLD_OUT }
                .forEach { assertEquals(BreathRoute.EITHER, it.route, "${p.id}: a hold named a route") }
        }
    }

    @Test
    fun `a session is chosen by time, and every pattern fills every offered length`() {
        BreathingPatterns.DURATIONS_SEC.forEach { seconds ->
            all.forEach { p ->
                val cycles = BreathingSession.cyclesForSeconds(p, seconds)
                assertTrue(cycles >= 1, "${p.id} at ${seconds}s rounded down to nothing")
                val actual = cycles * p.cycleSec
                assertTrue(
                    kotlin.math.abs(actual - seconds) <= p.cycleSec,
                    "${p.id} at ${seconds}s came out as ${actual}s, more than one cycle adrift",
                )
            }
        }
    }

    @Test
    fun `the one-minute option is short enough for the sigh to be used in the moment`() {
        // The sigh is the one pattern whose whole point is that a few of them work. Five minutes
        // of it is a practice; one minute is the thing you do after a hard set.
        val cycles = BreathingSession.cyclesForSeconds(BreathingPatterns.PHYSIOLOGICAL_SIGH, 60)
        assertTrue(cycles in 3..6, "one minute of sighing came out as $cycles rounds")
    }

    @Test
    fun `every pattern can be looked up by the id that will be written to the database`() {
        all.forEach { p ->
            assertNotNull(BreathingPatterns.byId(p.id), "${p.id} cannot be found again")
            assertTrue(
                p.id.all { it.isLowerCase() || it.isDigit() || it == '-' },
                "${p.id} is not a safe id for a stored row",
            )
        }
    }

    @Test
    fun `every pattern runs to completion and ends on its last phase`() {
        all.forEach { p ->
            val session = p.session(cycles = 2)
            session.start(0L)
            val total = (p.cycleSec * 2 * 1000).toLong()
            val before = session.tick(total - 200L)
            assertTrue(!before.done, "${p.id} finished early")
            val after = session.tick(total + 50L)
            assertTrue(after.done, "${p.id} never finished")
            assertEquals(2, after.cycles)
        }
    }
}
