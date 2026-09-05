package com.clashfit.engine.coach

import com.clashfit.core.model.CoachOutput
import com.clashfit.core.model.CoachSource
import com.clashfit.core.model.SetTelemetry
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException

/** LLM seam with timeout. Always returns usable lines. */
class CoachFor(
    private val llm: (suspend (SetTelemetry) -> CoachOutput?)? = null,
    private val timeoutMs: Long = 5000L,
) {
    /**
     * Generates a coach output, using the LLM if available, or falling back to templates.
     * Timeout is wrapped in Promise.race semantics in Kotlin via withTimeoutOrNull.
     */
    suspend fun speakFor(telemetry: SetTelemetry): CoachOutput {
        if (llm == null) {
            return TemplateBank.templateFor(telemetry)
        }

        return try {
            val result = withTimeoutOrNull(timeoutMs) {
                llm(telemetry)
            }
            if (result != null) {
                // Validate both coach and boss lines
                val coachValid = OutputValidator.validateOutput(result.coachLine, telemetry).ok
                val bossValid = OutputValidator.validateOutput(result.bossLine, telemetry).ok
                if (coachValid && bossValid) {
                    return result
                }
            }
            // Fall through to template on timeout or validation failure
            TemplateBank.templateFor(telemetry)
        } catch (e: CancellationException) {
            // The session that asked for this line is gone. Falling back to a template here would
            // hand a line to nobody and keep a cancelled coroutine working.
            throw e
        } catch (e: Exception) {
            // Silently fall through to template on any error
            TemplateBank.templateFor(telemetry)
        }
    }
}
