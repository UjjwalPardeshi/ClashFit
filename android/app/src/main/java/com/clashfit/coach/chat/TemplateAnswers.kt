package com.clashfit.coach.chat

/**
 * The bottom rung: an answer built from the fact sheet itself, with no model at all.
 *
 * It matters more than it looks. On a phone with no weights installed and no cloud switch, this is
 * the coach — and it must never be the moment the demo goes quiet. So it reads the same fact sheet
 * the model would have read, finds the lines that bear on the question, and says them back. It
 * cannot be clever and does not try; what it can be is correct, which the model is not guaranteed
 * to be.
 *
 * Matching is on the words a player actually types, not on an intent classifier. "why is my depth
 * dropping", "depth?", "am i going deep enough" all reach the same lines.
 */
object TemplateAnswers {

    private data class Topic(val words: List<String>, val keys: List<String>, val lead: String)

    private val TOPICS = listOf(
        Topic(
            listOf("depth", "deep", "low", "bottom", "range", "rom"),
            listOf("depth", "range"),
            "On depth and range",
        ),
        Topic(
            listOf("tired", "fatigue", "gassed", "rest", "recover", "break"),
            listOf("fatigue", "rest", "speed lost"),
            "On fatigue",
        ),
        Topic(
            listOf("form", "clean", "quality", "score", "grade"),
            listOf("form", "best rep", "worst rep"),
            "On form",
        ),
        Topic(
            listOf("side", "left", "right", "asymmetry", "uneven", "imbalance", "weaker"),
            listOf("left-right"),
            "On your two sides",
        ),
        Topic(
            listOf("better", "improving", "progress", "trend", "worse", "getting"),
            listOf("trend", "form over the last", "form first", "form last"),
            "On the trend",
        ),
        Topic(
            listOf("streak", "consistent", "days", "habit"),
            listOf("streak", "sessions finished"),
            "On showing up",
        ),
        Topic(
            listOf("boss", "damage", "win", "fight", "hp", "health"),
            listOf("boss health", "reps this set"),
            "On the fight",
        ),
        Topic(
            listOf("combo", "streak of clean", "multiplier"),
            listOf("longest clean streak"),
            "On your combo",
        ),
    )

    /**
     * An answer drawn from [facts], or an honest admission that nothing here bears on the question.
     */
    fun answer(question: String, facts: List<String>): String {
        if (facts.isEmpty()) return "I have nothing measured yet. Finish a set and ask me again."
        val q = question.lowercase()

        val topic = TOPICS.firstOrNull { t -> t.words.any { q.contains(it) } }
        if (topic != null) {
            val hits = facts.filter { line -> topic.keys.any { line.startsWith(it) || line.contains(it) } }
            if (hits.isNotEmpty()) {
                return "${topic.lead}: ${hits.take(2).joinToString("; ")}."
            }
        }

        // Nothing matched. Say the two most useful lines rather than pretending to have understood.
        val headline = facts.firstOrNull { it.startsWith("form average") || it.startsWith("sessions finished") }
        val second = facts.firstOrNull { it.startsWith("reps this set") || it.startsWith("current streak") }
        val said = listOfNotNull(headline, second)
        return if (said.isEmpty()) {
            "I can only tell you what was measured, and nothing here answers that."
        } else {
            "I can only answer from what was measured. ${said.joinToString("; ")}."
        }
    }

    /**
     * The chips above the input.
     *
     * Six, because a blank chat box is the fastest way to make somebody put a phone down, and
     * because these are the questions the fact sheet can genuinely answer well.
     */
    fun starters(afterSet: Boolean): List<String> = if (afterSet) {
        listOf("How did that set go?", "Why did my depth drop?", "How tired am I?", "Am I even left to right?", "How long should I rest?", "What should I fix next set?")
    } else {
        listOf("Am I getting better?", "How is my streak?", "What is my best exercise?", "Where am I weakest?", "What should I train today?", "How much have I done this week?")
    }
}
