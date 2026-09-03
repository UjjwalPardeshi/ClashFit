package com.clashfit.coach

/**
 * Configuration for LLM generation: temperature, topK, and max tokens.
 * Split by persona to allow different generation parameters.
 */
data class CoachConfig(
    val coach: PersonaConfig = PersonaConfig(temperature = 0.35f, topK = 20, maxTokens = 60),
    val boss: PersonaConfig = PersonaConfig(temperature = 0.85f, topK = 40, maxTokens = 50),
) {
    data class PersonaConfig(
        val temperature: Float,
        val topK: Int,
        val maxTokens: Int,
    )
}
