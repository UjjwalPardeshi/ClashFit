package com.clashfit.coach

import android.util.Log
import com.clashfit.core.model.CoachOutput
import com.clashfit.core.model.CoachSource
import com.clashfit.core.model.SetTelemetry
import com.clashfit.engine.coach.CoachFor
import com.clashfit.engine.coach.OutputValidator
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Who speaks for the coach, in order of preference.
 *
 * 1. Gemma, on the phone, if the weights are installed and loaded.
 * 2. The cloud model, if — and only if — the player switched Cloud coach on. Text only: the same
 *    23-number telemetry summary the on-device model gets, and nothing else can reach this rung.
 * 3. The template bank, which always has a line and never needs anything.
 *
 * Every rung is validated the same way before it is allowed to speak: two sentences, no invented
 * number, no blocklisted word. A rung that fails validation is simply skipped.
 */
class CoachEngine(
    private val llm: LlmEngine? = null,
    private val cloud: CloudCoach? = null,
    /** The player's Cloud coach switch, read at the moment a line is needed. */
    private val cloudAllowed: suspend () -> Boolean = { false },
    private val json: Json = Json,
) {
    private val tag = "ClashFit/coach"

    private val coachFor = CoachFor(
        llm = if (llm != null || cloud != null) suspend { telemetry -> firstVoice(telemetry) } else null,
        timeoutMs = 9_000L,
    )

    /**
     * Generate a coach output for the given telemetry.
     * Returns immediately with a usable line (LLM, cloud or template).
     * Never null, never empty.
     */
    suspend fun speakFor(telemetry: SetTelemetry): CoachOutput {
        return try {
            coachFor.speakFor(telemetry)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error in CoachEngine", e)
            CoachOutput(coachLine = "Ready for the next set.", bossLine = "Again.", source = CoachSource.TEMPLATE)
        }
    }

    /** Which voice would answer right now, for the badge on screen. */
    suspend fun activeSource(): CoachSource = when {
        llm?.ready == true -> CoachSource.LLM
        cloud?.isConfigured == true && cloudAllowed() -> CoachSource.CLOUD
        else -> CoachSource.TEMPLATE
    }

    private suspend fun firstVoice(telemetry: SetTelemetry): CoachOutput? {
        llm?.generate(telemetry)?.let { return it }
        val c = cloud ?: return null
        if (!c.isConfigured || !cloudAllowed()) return null

        val facts = "Telemetry:\n" + json.encodeToString(telemetry)
        val coachLine = c.complete(SYSTEM, "$COACH\n\n$facts", maxTokens = 60) ?: return null
        if (!OutputValidator.validateOutput(coachLine, telemetry).ok) { Log.w(tag, "cloud coach line rejected"); return null }
        val bossLine = c.complete(SYSTEM, "$BOSS\n\n$facts", maxTokens = 50) ?: return null
        if (!OutputValidator.validateOutput(bossLine, telemetry).ok) { Log.w(tag, "cloud boss line rejected"); return null }
        return CoachOutput(coachLine = coachLine, bossLine = bossLine, source = CoachSource.CLOUD)
    }

    companion object {
        /** The same rules the on-device prompts carry, in assets/prompts/system.txt. */
        const val SYSTEM = "You are the voice of a fitness combat game called ClashFit. You will be given measured " +
            "telemetry from one set of exercise. Everything you say must be grounded in those numbers. Never invent a " +
            "number. Never comment on the player's body, weight, appearance, or fitness level. Never apologise. Never " +
            "use exclamation marks. Never use emoji. Maximum two sentences per output. Plain English."
        const val COACH = "Persona: COACH. Voice: a competent training partner who has watched the whole set. Warm, " +
            "terse, specific. Always cite one concrete number from the telemetry. If fatigue_band is FADING or GASSED, " +
            "prescribe a rest in seconds. Never tell them to stop. If the trend is improving, say what improved. Output exactly one line."
        const val BOSS = "Persona: THE PACEMAKER, a mechanical construct that mirrors the player's tempo. Taunt in " +
            "character. Reference the same fact the coach would, from the machine's point of view. Mock their tempo, " +
            "their depth, or their resolve. Never their body or their fitness level. Output exactly one line."
    }
}
