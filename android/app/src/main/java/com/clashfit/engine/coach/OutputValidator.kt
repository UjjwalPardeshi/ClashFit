package com.clashfit.engine.coach

import com.clashfit.core.model.SetTelemetry
import java.util.Locale

/** Validation result with rejection reason. */
data class ValidationResult(
    val ok: Boolean,
    val why: String? = null, // "empty", "too long", "more than two sentences", "blocklist term", "hallucinated number N"
)

/** Validates generated coach output before displaying to the player. */
object OutputValidator {
    private val BLOCK = Regex("\\b(weight|fat|skinny|lazy|calorie|calories|diet|obese|fail(ed|ure)?)\\b", RegexOption.IGNORE_CASE)

    /**
     * Validates the generated text against safety rules.
     * Returns a result with ok=true if the text passes all checks, false otherwise.
     * Rejection is silent — the template fires and the player sees no difference.
     */
    fun validateOutput(text: String?, telemetry: SetTelemetry): ValidationResult {
        if (text == null || text.isBlank()) {
            return ValidationResult(false, "empty")
        }
        val s = text.trim()
        if (s.length > 180) {
            return ValidationResult(false, "too long")
        }
        val sentenceCount = s.split(Regex("[.!?]+(?=\\s|$)")).count { it.isNotBlank() }
        if (sentenceCount > 2) {
            return ValidationResult(false, "more than two sentences")
        }
        if (BLOCK.containsMatchIn(s)) {
            return ValidationResult(false, "blocklist term")
        }

        // Extract all numbers from telemetry object
        val allowedNumbers = extractNumbers(telemetry).toSet()

        // Check for hallucinated numbers
        val textNumbers = Regex("\\d+").findAll(s).map { it.value.toIntOrNull() }.filterNotNull()
        for (n in textNumbers) {
            if (!allowedNumbers.contains(n)) {
                return ValidationResult(false, "hallucinated number $n")
            }
        }

        return ValidationResult(true)
    }

    private fun extractNumbers(t: SetTelemetry): List<Int> {
        val numbers = mutableListOf<Int>()

        // Add all integer fields (non-null, including zero)
        listOf(
            t.reps, t.formMeanPct, t.formFirst3Pct, t.formLast3Pct,
            t.depthCm, t.depthDropCm, t.velocityLossPct, t.romLossPct,
            t.bestRep?.index, t.worstRep?.index, t.comboReps, t.bossHpPct,
            t.sessionSetIndex,
        ).forEach { v ->
            if (v != null) numbers.add(v)
        }

        // Add float fields extracted as individual digits (JSON repr)
        // e.g., 0.75 → 0, 75; 1.6 → 1, 6
        listOfNotNull(
            t.formMean, t.formFirst3, t.formLast3, t.bestRep?.form, t.worstRep?.form, t.comboMax
        ).forEach { f ->
            val s = String.format(Locale.US, "%.2f", f)
            s.split(Regex("[^0-9]")).filter { it.isNotEmpty() }.forEach { digit ->
                digit.toIntOrNull()?.let { numbers.add(it) }
            }
        }

        return numbers
    }
}
