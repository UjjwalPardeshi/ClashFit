package com.clashfit.coach

import android.graphics.Bitmap
import android.util.Log
import com.clashfit.core.model.RepRecord
import kotlinx.coroutines.CancellationException

/**
 * The referee looks at the worst rep.
 *
 * Everything else in this app measures. Depth is a number, alignment is a number, and the coach
 * says the number back to you — which is honest and complete and still leaves the player asking
 * "yes, but what am I doing wrong?" A number cannot answer that. A picture can.
 *
 * So at the end of a set, the frame from the bottom of the worst rep goes to Gemma, on the phone,
 * with that rep's own measurements beside it. The model is asked to name what it sees, once, and to
 * agree with the numbers rather than invent new ones. What comes back is the difference between
 * "alignment 61%" and "your left knee is falling inward at the bottom".
 *
 * The frame never leaves the device and is never written to storage. It is lifted from the camera's
 * own ring, handed to a local model, and recycled — see
 * [com.clashfit.perception.vision.FrameSource]. There is deliberately no cloud rung here: the
 * coach's words may go to a cloud model when the player opts in, but a picture of them never does.
 */
class RefereeEyes(private val llm: LlmEngine) {

    private val tag = "ClashFit/referee"

    /** Whether a critique is possible at all right now: no model, no eyes. */
    val available: Boolean get() = llm.ready

    /**
     * One sentence about what the picture shows, or null.
     *
     * Null on every failure — no model, a timeout, or an answer that broke the rules. The summary
     * then shows the numbers it always showed. The bitmap is not recycled here; the caller owns it.
     */
    suspend fun critique(frame: Bitmap, rep: RepRecord, exerciseName: String): String? {
        if (!llm.ready) return null
        return try {
            val answer = llm.complete(
                prompt = prompt(rep, exerciseName),
                image = frame,
                maxTokens = 48,
                temperature = 0.3f,
                topK = 20,
                // Vision costs more than text on a 2B model, and this runs while the player is
                // resting, so it can afford to be slower than a coach line — but not slower than
                // the rest it is running inside.
                timeoutMs = 12_000L,
            ) ?: return null
            clean(answer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(tag, "critique failed", e)
            null
        }
    }

    /**
     * The prompt builder and the answer cleaner are pure, so they live here and can be tested
     * without a model, a phone or a photograph.
     */
    companion object {

        /**
         * The question.
         *
         * The measurements go in with the picture on purpose. Asked cold, a small model describes a
         * person in a room; asked with "alignment scored 61 and that was the worst part of this
         * rep", it looks for the thing that would explain the number — which is the thing the
         * player needs to hear. It is also the guard against invention: the model is told what is
         * already known, and told not to contradict it.
         */
        internal fun prompt(rep: RepRecord, exerciseName: String): String {
            val worst = worstPart(rep)
            return buildString {
                appendLine("You are a movement referee looking at one photograph.")
                appendLine("The photograph is the lowest point of a ${exerciseName.lowercase()} rep, taken by the phone's own camera.")
                appendLine()
                appendLine("This rep was already measured. Do not contradict these numbers and do not repeat them:")
                appendLine("- depth ${pct(rep.depth)} of this player's own full range")
                appendLine("- range of motion ${pct(rep.rom)}")
                appendLine("- tempo ${pct(rep.tempo)}")
                appendLine("- joint alignment ${pct(rep.alignment)}")
                appendLine("- the weakest part was $worst")
                appendLine()
                appendLine("Say, in one sentence, what you can SEE in the photograph that explains why $worst scored lowest.")
                appendLine("Name a body part and what it is doing. Be specific and physical.")
                appendLine("If the photograph is too dark, blurred, or does not show a person, reply exactly: NO VIEW")
                appendLine("Never mention the player's body shape, weight or fitness. No exclamation marks. One sentence.")
            }
        }

        /**
         * What the model said, or nothing.
         *
         * A small model asked for one sentence sometimes returns three, or opens with "Sure!", or
         * hedges into uselessness. The first sentence is taken, the refusal is honoured, and
         * anything empty or absurdly long is dropped rather than put in front of a person.
         */
        internal fun clean(raw: String): String? {
            var s = raw.trim().removePrefix("\"").removeSuffix("\"").trim()
            if (s.isEmpty()) return null
            if (s.uppercase().startsWith("NO VIEW")) return null
            val end = s.indexOfFirst { it == '.' || it == '!' || it == '?' }
            if (end > 0) s = s.substring(0, end + 1)
            s = s.replace('!', '.').trim()
            if (s.length < 12 || s.length > 180) return null
            return s
        }

        private fun pct(v: Float): String =
            if (v.isNaN()) "not measured" else "${(v.coerceIn(0f, 1f) * 100).toInt()}%"

        /** The sub-score the scorer itself blamed, in words a person would use. */
        private fun worstPart(rep: RepRecord): String = when (rep.reason.lowercase()) {
            "depth" -> "depth"
            "rom" -> "range of motion"
            "tempo" -> "tempo"
            "alignment" -> "joint alignment"
            else -> listOf(
                "depth" to rep.depth, "range of motion" to rep.rom,
                "tempo" to rep.tempo, "joint alignment" to rep.alignment,
            ).filterNot { it.second.isNaN() }.minByOrNull { it.second }?.first ?: "form"
        }
    }
}
