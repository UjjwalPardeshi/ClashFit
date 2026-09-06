package com.clashfit.engine.summary

/**
 * The breathing patterns this app teaches, and the honest sentence about each one.
 *
 * Eight, chosen to cover what a person actually needs rather than to look plentiful: something to
 * settle you, something to put you to sleep, something for the heart's own rhythm, and the two
 * techniques taught in pulmonary rehabilitation when the problem is the lungs themselves. Every
 * timing here is the one the technique is conventionally taught with, not a rounded guess.
 *
 * Three rules this file keeps.
 *
 * **No medical claims.** Not one of these treats, cures or diagnoses anything, and [whyItWorks]
 * says what the pattern does to your breathing, never what it does to your disease. "May help you
 * feel calmer" is allowed. "Lowers blood pressure" is not, even where a study exists, because this
 * is a fitness app and a person reading it is not a participant in that study.
 *
 * **The evidence is described as it is.** Some of these are heavily studied and some are
 * traditional practices with modest modern support. [evidence] says which, in plain words, because
 * a person deciding how much to trust a breathing exercise deserves to know.
 *
 * **The warnings are specific.** Not a blanket disclaimer that everybody scrolls past, but the one
 * or two situations that genuinely apply to that pattern — a long hold is a poor idea in
 * pregnancy, a technique designed to make you drowsy is a poor idea at the wheel.
 */
data class BreathingPattern(
    val id: String,
    val name: String,
    /** Two to four words under the name, so a list can be read at a glance. */
    val rhythm: String,
    val goal: BreathingGoal,
    val steps: List<BreathStep>,
    /** A sensible session length in cycles, used to pick the default duration. */
    val defaultCycles: Int,
    /**
     * How many breaths one pass through the pattern actually is.
     *
     * One for almost everything. Two for alternate nostril, where a round is in-left, out-right,
     * in-right, out-left — two whole breaths, not one. Still one for the physiological sigh, whose
     * two inhales are a single breath taken in two sips. Without this, breaths per minute is
     * computed off the cycle length and quietly says half the truth for one pattern and double it
     * for another.
     */
    val breathsPerCycle: Int = 1,
    val whyItWorks: String,
    val whenToUse: String,
    /** Posture or hand position needed before starting. Empty when there is nothing to set up. */
    val setup: String,
    /** Who should skip this, or stop. Never empty: every pattern has at least one real caution. */
    val caution: String,
    val evidence: String,
) {
    val cycleSec: Float get() = steps.sumOf { it.seconds.toDouble() }.toFloat()

    val breathsPerMinute: Float get() = if (cycleSec > 0f) 60f * breathsPerCycle / cycleSec else 0f

    fun session(cycles: Int): BreathingSession = BreathingSession(steps, cycles)
}

/** What you reached for this pattern to do. Also how the list is grouped. */
enum class BreathingGoal(val label: String, val blurb: String) {
    CALM("Settle down", "For after a hard set, or a hard hour"),
    SLEEP("Get to sleep", "For when you are already in bed"),
    LUNGS("Train your lungs", "The two techniques taught in pulmonary rehab"),
}

object BreathingPatterns {

    private fun inhale(sec: Float, route: BreathRoute, cue: String) =
        BreathStep(BreathPhase.IN, sec, route, cue)

    private fun exhale(sec: Float, route: BreathRoute, cue: String) =
        BreathStep(BreathPhase.OUT, sec, route, cue)

    private fun holdFull(sec: Float, cue: String) =
        BreathStep(BreathPhase.HOLD_IN, sec, BreathRoute.EITHER, cue)

    private fun holdEmpty(sec: Float, cue: String) =
        BreathStep(BreathPhase.HOLD_OUT, sec, BreathRoute.EITHER, cue)

    val BOX = BreathingPattern(
        id = "box",
        name = "Box breathing",
        rhythm = "4 · 4 · 4 · 4",
        goal = BreathingGoal.CALM,
        steps = listOf(
            inhale(4f, BreathRoute.NOSE, "Breathe in slowly through your nose"),
            holdFull(4f, "Hold, keep your shoulders soft"),
            exhale(4f, BreathRoute.NOSE, "Let it out, slow and even"),
            holdEmpty(4f, "Stay empty, then begin again"),
        ),
        defaultCycles = 8,
        whyItWorks = "Four equal counts stretch each breath to about sixteen seconds and give your " +
            "attention one simple repeating thing to follow instead of whatever it was doing.",
        whenToUse = "Between sets, or any moment you want to come down without lying down.",
        setup = "Sit upright with your back supported and both feet flat. Let your shoulders drop.",
        caution = "The holds are the part that adds strain. Skip them, or skip this pattern, if " +
            "you are pregnant, have blood pressure that is not under control, or a heart or lung " +
            "condition. Ask a doctor first if you have epilepsy. If an empty hold feels like air " +
            "hunger, use a pattern without holds. Stop if you feel light-headed.",
        evidence = "Widely taught in military and emergency-service stress training. The calming " +
            "effect of slow paced breathing is well supported; the square shape specifically is a " +
            "teaching device rather than a finding.",
    )

    val COHERENT = BreathingPattern(
        id = "coherent",
        name = "Coherent breathing",
        rhythm = "5 in · 5 out",
        goal = BreathingGoal.CALM,
        steps = listOf(
            inhale(5f, BreathRoute.NOSE, "Breathe in slowly and smoothly"),
            exhale(5f, BreathRoute.NOSE, "Breathe out, long and smooth"),
        ),
        defaultCycles = 30,
        whyItWorks = "At around six breaths a minute your breathing lines up with the slow rhythm " +
            "your heart rate already rises and falls on, and the two start to move together.",
        whenToUse = "When you want a rate to hold rather than a technique to learn. It rewards " +
            "minutes, not seconds.",
        setup = "Sit or lie down with your shoulders loose. Let the belly move rather than the chest.",
        caution = "Not while driving, cycling or in water. If you feel light-headed or tingly you " +
            "are breathing too deeply; make the breaths smaller, not slower.",
        evidence = "The most studied of the slow-breathing rates, usually under the name resonance " +
            "frequency breathing. Most of that work measures heart rate variability rather than " +
            "how people feel.",
    )

    val PHYSIOLOGICAL_SIGH = BreathingPattern(
        id = "physiological-sigh",
        name = "Physiological sigh",
        rhythm = "double in · long out",
        goal = BreathingGoal.CALM,
        steps = listOf(
            inhale(4f, BreathRoute.NOSE, "Breathe in slowly through your nose"),
            inhale(1f, BreathRoute.NOSE, "Sip in a little more air"),
            exhale(8f, BreathRoute.MOUTH, "Long slow sigh out your mouth"),
        ),
        defaultCycles = 12,
        whyItWorks = "The second short inhale reopens parts of the lung that the first one did not, " +
            "and the long sigh out is the part that does the settling. Your body already does this " +
            "on its own when you cry or sleep.",
        whenToUse = "The moment stress spikes. This is the fastest of the eight — a few of these " +
            "work, where the others need minutes.",
        setup = "Sit or lie down with your shoulders and jaw loose. Works standing too.",
        caution = "Not while driving, cycling or in water. Stop if the deep second inhale makes you " +
            "dizzy.",
        evidence = "The best supported of the quick techniques. A controlled trial in 2023 found " +
            "five minutes a day of cyclic sighing improved mood and lowered breathing rate more " +
            "than mindfulness meditation did over the same month.",
    )

    val ALTERNATE_NOSTRIL = BreathingPattern(
        id = "alternate-nostril",
        name = "Alternate nostril",
        rhythm = "left · right · left",
        goal = BreathingGoal.CALM,
        steps = listOf(
            // The phase word leads and the side follows, so two cues never differ only in the
            // middle. The pair "Close right, breathe in left" and "Close right, breathe out left"
            // are one short word apart in the centre of the line, which is exactly the thing a
            // drowsy reader at arm's length gets wrong. None of these says "still closed" either:
            // a cue that asks you to remember the previous phase is no use to someone who lost it.
            inhale(4f, BreathRoute.LEFT_NOSTRIL, "In — through the left"),
            exhale(4f, BreathRoute.RIGHT_NOSTRIL, "Out — through the right"),
            inhale(4f, BreathRoute.RIGHT_NOSTRIL, "In — through the right"),
            exhale(4f, BreathRoute.LEFT_NOSTRIL, "Out — through the left"),
        ),
        defaultCycles = 10,
        breathsPerCycle = 2,
        whyItWorks = "Closing one nostril at a time makes the breath slow and deliberate whether you " +
            "meant it to be or not, and gives your hands something to do while your attention " +
            "settles.",
        whenToUse = "Between two things that need a clear head. Best somewhere you do not mind " +
            "holding your face.",
        setup = "Sit upright. Rest your right thumb by your right nostril and your ring finger by " +
            "your left, so you can close either one.",
        caution = "Not while driving or in water — one hand is on your face. Skip it when a nostril " +
            "is blocked; do not force air through a congested side.",
        evidence = "A traditional yogic practice, nadi shodhana, with a long history and modest " +
            "modern evidence. Small studies report calming effects; they are small.",
    )

    val EXTENDED_EXHALE = BreathingPattern(
        id = "extended-exhale",
        name = "Extended exhale",
        rhythm = "4 in · 8 out",
        goal = BreathingGoal.SLEEP,
        steps = listOf(
            inhale(4f, BreathRoute.NOSE, "Breathe in gently through your nose"),
            exhale(8f, BreathRoute.EITHER, "Let the breath out, slow and long"),
        ),
        defaultCycles = 10,
        whyItWorks = "Making the out-breath about twice the in-breath is the single change that " +
            "shifts you toward rest, and it needs no holds and no counting past eight.",
        whenToUse = "In bed with the lights off. Start here rather than with four seven eight — " +
            "this is the same idea without the hold.",
        setup = "Lie on your back or side, arms loose, one hand on your belly so you can feel it rise.",
        caution = "This is meant to make you drowsy, so never while driving, cycling or near water. " +
            "If eight seconds leaves you gasping at the top, shorten it to six.",
        evidence = "The longer-exhale principle is well supported across the slow-breathing " +
            "literature. The exact four-to-eight ratio is a convenient teaching number.",
    )

    val FOUR_SEVEN_EIGHT = BreathingPattern(
        id = "478",
        name = "Four seven eight",
        rhythm = "4 · 7 · 8",
        goal = BreathingGoal.SLEEP,
        steps = listOf(
            inhale(4f, BreathRoute.NOSE, "Breathe in quietly through your nose"),
            holdFull(7f, "Hold. Keep your shoulders soft"),
            exhale(8f, BreathRoute.MOUTH, "Long whoosh out through your mouth"),
        ),
        defaultCycles = 4,
        whyItWorks = "A long hold followed by a longer sigh out. Four rounds is the whole exercise; " +
            "it is meant to be short and is not improved by doing twenty.",
        whenToUse = "Lying down with the lights off, once extended exhale feels easy. The hold is " +
            "what makes this the stronger of the two.",
        setup = "Lie down, or sit with a straight back. Rest your tongue behind your top front teeth.",
        caution = "Sitting or lying only, never while driving or near water, because the " +
            "seven-second hold can leave you briefly light-headed. Skip the hold in pregnancy or " +
            "with uncontrolled blood pressure, and ask a doctor first if you have epilepsy. Four " +
            "rounds is the dose; more is not better.",
        evidence = "Popularised by Dr Andrew Weil. The slow-breathing effect is well supported; " +
            "these three particular numbers are his teaching device, not a trial result.",
    )

    val DIAPHRAGMATIC = BreathingPattern(
        id = "diaphragmatic",
        name = "Belly breathing",
        rhythm = "4 in · 6 out",
        goal = BreathingGoal.LUNGS,
        steps = listOf(
            inhale(4f, BreathRoute.NOSE, "Breathe in, let your belly rise"),
            exhale(6f, BreathRoute.PURSED_LIPS, "Out slowly, belly sinks back"),
        ),
        defaultCycles = 18,
        whyItWorks = "A hand on the belly gives you something to aim at, so the diaphragm does the " +
            "work instead of your neck and shoulders, and each breath moves more air for less effort.",
        whenToUse = "First, and for its own sake. This is the technique the other seven assume " +
        "you can already do, and the only one where the point is where the movement comes from " +
        "rather than how fast it goes.",
        setup = "Sit tall, or lie on your back with knees bent. One hand on your chest, one on your " +
            "belly. Only the lower hand should move.",
        caution = "Not while driving or near water. Do not force the belly out or squeeze it in — " +
            "the movement should be the breath's, not your muscles'.",
        evidence = "The foundation of breathing retraining in physiotherapy and pulmonary " +
            "rehabilitation, and taught before anything else in both.",
    )

    val PURSED_LIP = BreathingPattern(
        id = "pursed-lip",
        name = "Pursed-lip breathing",
        rhythm = "2 in · 4 out",
        goal = BreathingGoal.LUNGS,
        steps = listOf(
            inhale(2f, BreathRoute.NOSE, "Breathe in slowly through your nose"),
            exhale(4f, BreathRoute.PURSED_LIPS, "Blow out slowly, lips like a whistle"),
        ),
        defaultCycles = 20,
        whyItWorks = "Breathing out through narrowed lips slows the air on the way out and keeps a " +
            "little pressure behind it, so the airways stay open longer and more of the stale air " +
            "leaves.",
        whenToUse = "When you are out of breath and want it back — after stairs, or at the end of a " +
            "hard set.",
        setup = "Sit or stand tall, shoulders loose. Purse your lips as if you were about to whistle.",
        caution = "Stop and breathe normally if you feel dizzy or more breathless than when you " +
            "started. Never force the air out or try to empty your lungs completely.",
        evidence = "Standard practice in pulmonary rehabilitation for breathlessness, and one of " +
            "the few breathing techniques with a clear mechanical explanation.",
    )

    /** Every pattern, in the order a person should meet them. */
    val ALL: List<BreathingPattern> = listOf(
        BOX, COHERENT, PHYSIOLOGICAL_SIGH, ALTERNATE_NOSTRIL,
        EXTENDED_EXHALE, FOUR_SEVEN_EIGHT,
        DIAPHRAGMATIC, PURSED_LIP,
    )

    /** Grouped for the picker, in goal order, keeping the order within each group. */
    val BY_GOAL: List<Pair<BreathingGoal, List<BreathingPattern>>> =
        BreathingGoal.entries.map { goal -> goal to ALL.filter { it.goal == goal } }

    fun byId(id: String): BreathingPattern? = ALL.firstOrNull { it.id == id }

    /** What a first-time player is shown: the one the other seven assume you can already do. */
    val DEFAULT: BreathingPattern = DIAPHRAGMATIC

    /** Session lengths offered, in seconds. Chosen by time because cycle lengths are not comparable. */
    val DURATIONS_SEC: List<Int> = listOf(60, 180, 300)
}
