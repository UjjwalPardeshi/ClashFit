package com.clashfit.coach

import android.util.Log
import com.clashfit.core.model.CoachOutput
import com.clashfit.core.model.CoachSource
import com.clashfit.core.model.SetTelemetry
import com.clashfit.engine.coach.CoachFor

/**
 * Wraps engine.coach.CoachFor with the LLM as a seam.
 * Templates always win on failure: timeout, validation failure, OOM, or model unavailable.
 * No state, no logging of rejections (handled by CoachFor internally).
 */
class CoachEngine(
    private val llm: LlmEngine? = null,
) {
    private val tag = "ClashFit/coach"
    private val coachFor = CoachFor(
        llm = if (llm != null) { telemetry ->
            llm.generate(telemetry)
        } else null,
        timeoutMs = 5_000L,
    )

    /**
     * Generate a coach output for the given telemetry.
     * Returns immediately with a usable line (LLM or template).
     * Never null, never empty.
     */
    suspend fun speakFor(telemetry: SetTelemetry): CoachOutput {
        return try {
            coachFor.speakFor(telemetry)
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error in CoachEngine", e)
            // Fallback: return a minimal template-like output
            CoachOutput(
                coachLine = "Ready for the next set.",
                bossLine = "Again.",
                source = CoachSource.TEMPLATE,
            )
        }
    }
}
