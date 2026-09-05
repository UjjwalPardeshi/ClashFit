package com.clashfit.coach.chat

import com.clashfit.core.model.SetTelemetry
import com.clashfit.data.SessionEntity
import com.clashfit.data.StreakEntity
import com.clashfit.meta.MetaState
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Everything the coach is allowed to know, as plain lines of text.
 *
 * A chat that answers from nothing is a party trick: it will invent a depth, congratulate you on a
 * streak you do not have, and be confidently wrong in front of a judge. This is the antidote. The
 * model is handed a short, literal list of measurements taken from this player's own database, and
 * told to answer only from it.
 *
 * Short on purpose. Gemma 3n E2B is a two-billion-parameter model with a small context, and the
 * whole prompt — rules, sheet, history, question — has to fit with room for an answer. Every line
 * here earns its place: the numbers a player actually asks about, and nothing else.
 *
 * There is no free text in a fact sheet. Nothing a player typed, no display name, no email. What
 * goes in is numbers and the words this app already prints on its own screens.
 */
object FactSheet {

    /** The set just finished, when the chat is opened from a fight. */
    fun forSet(t: SetTelemetry): List<String> = buildList {
        add("exercise: ${t.exercise}")
        add("set number: ${t.sessionSetIndex}")
        add("reps this set: ${t.reps}")
        add("form average: ${t.formMeanPct} out of 100")
        add("form first three reps: ${t.formFirst3Pct}")
        add("form last three reps: ${t.formLast3Pct}")
        t.depthCm?.let { add("depth: $it cm") }
        t.depthDropCm?.let { if (it != 0) add("depth lost across the set: $it cm") }
        add("speed lost across the set: ${t.velocityLossPct} percent")
        add("range lost across the set: ${t.romLossPct} percent")
        add("fatigue at the end: ${t.fatigueBand.name.lowercase()}")
        add("trend: ${t.trend.name.lowercase()}")
        t.bestRep?.let { add("best rep: number ${it.index} at ${(it.form * 100).roundToInt()} out of 100") }
        t.worstRep?.let { r ->
            add("worst rep: number ${r.index} at ${(r.form * 100).roundToInt()} out of 100" +
                (r.reason?.let { ", weakest part was $it" } ?: ""))
        }
        add("longest clean streak: ${t.comboReps} reps, multiplier ${"%.1f".format(t.comboMax)}")
        add("boss health left: ${t.bossHpPct} percent")
        add("rest prescribed: ${t.restSec} seconds")
        t.asymmetryPct?.let { pct ->
            val side = t.weakerSide?.lowercase()
            add("left-right difference: $pct percent" + (side?.let { ", weaker side $it" } ?: ""))
        }
    }

    /**
     * The player's recent history, when the chat is opened from the profile rather than a fight.
     *
     * Ten sessions, not fifty: a small model given a long table starts averaging things nobody
     * asked about. Ten is enough to answer "am I getting better" honestly.
     */
    fun forHistory(
        sessions: List<SessionEntity>,
        streak: StreakEntity?,
        meta: MetaState?,
        nowMs: Long,
    ): List<String> = buildList {
        val done = sessions.filter { it.endedAtMs != null }
        if (done.isEmpty()) {
            add("this player has not finished a session yet")
            return@buildList
        }
        add("sessions finished: ${done.size}")
        add("most recent first, up to ten:")
        done.take(10).forEach { s ->
            val days = TimeUnit.MILLISECONDS.toDays(nowMs - s.startedAtMs)
            val whenSaid = when (days) {
                0L -> "today"
                1L -> "yesterday"
                else -> "$days days ago"
            }
            add("- $whenSaid: ${s.exerciseId}, ${s.totalReps} reps, form ${(s.formMean * 100).roundToInt()}, peak fatigue ${s.peakBand.lowercase()}")
        }
        val recentForm = done.take(5).map { it.formMean }
        val olderForm = done.drop(5).take(5).map { it.formMean }
        if (recentForm.isNotEmpty() && olderForm.isNotEmpty()) {
            val r = (recentForm.average() * 100).roundToInt()
            val o = (olderForm.average() * 100).roundToInt()
            add("form over the last five sessions: $r, over the five before that: $o")
        }
        streak?.let {
            add("current streak: ${it.current} days, best ever: ${it.best} days")
        }
        meta?.let {
            add("level ${it.progress.level}, called ${it.progress.title}")
            add("weekly challenge ${it.weekly.challenge.title}: ${it.weekly.value} of ${it.weekly.challenge.target}")
        }
    }

    /**
     * The rules, verbatim, every turn.
     *
     * A 2B model does not remember a system prompt from six messages ago the way a frontier model
     * does, so these ride along with each question. "Say you do not know" is the most important
     * line in the app: a coach that guesses a number is worse than one that shrugs, because the
     * whole claim of this project is that everything on screen was measured.
     */
    const val RULES = """You are the coach in a fitness game. You are talking to the player.

You will be given FACTS: measurements taken from this player's own training, on their phone.

Rules:
- Answer only from the FACTS. Never state a number that is not in them.
- If the FACTS do not answer the question, say so plainly and say what you would need.
- Cite one concrete number from the FACTS in your answer.
- Two sentences at most. Plain English. No jargon they have not seen on screen.
- Never comment on their body, weight, appearance or fitness level.
- Never apologise, never use exclamation marks, never use emoji."""

    /** Rules, facts and the question, in the order a small model reads best. */
    fun prompt(facts: List<String>, question: String, history: List<ChatTurn> = emptyList()): String =
        buildString {
            appendLine(RULES)
            appendLine()
            appendLine("FACTS")
            facts.forEach { appendLine("- $it") }
            if (history.isNotEmpty()) {
                appendLine()
                appendLine("EARLIER IN THIS CONVERSATION")
                // Only the last few turns: the sheet matters more than the chat, and context is short.
                history.takeLast(4).forEach { turn ->
                    appendLine("${if (turn.fromPlayer) "Player" else "Coach"}: ${turn.text}")
                }
            }
            appendLine()
            appendLine("Player: $question")
            append("Coach:")
        }
}

/** One line of the conversation. [fromPlayer] false means the coach said it. */
data class ChatTurn(
    val text: String,
    val fromPlayer: Boolean,
    val source: com.clashfit.core.model.CoachSource? = null,
)
