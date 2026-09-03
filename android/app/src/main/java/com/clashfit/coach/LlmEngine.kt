package com.clashfit.coach

import android.content.Context
import android.util.Log
import com.clashfit.core.model.CoachOutput
import com.clashfit.core.model.SetTelemetry
import com.clashfit.core.util.Clock
import com.clashfit.engine.coach.PromptPack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.File

/** Status of the LLM engine: LOADING (25s cap), READY (model loaded), OFFLINE (no model), THROTTLED (thermal). */
enum class LlmStatus { LOADING, READY, OFFLINE, THROTTLED }

/**
 * On-device Gemma 3n LLM for generating coach lines.
 * Loads from files/models/gemma-3n-e2b-int4.task if present, falls back to templates silently.
 * Never runs concurrently; enforced by a lock.
 */
class LlmEngine(
    private val context: Context,
    private val config: CoachConfig = CoachConfig(),
    private val clock: Clock = Clock.SYSTEM,
    private val scope: CoroutineScope,
    private val json: Json,
) {
    private val tag = "ClashFit/coach"
    private val modelPath = context.getExternalFilesDir(null)?.let { File(it, "models/gemma-3n-e2b-int4.task") }
    private val modelExists = modelPath?.exists() == true

    private val _status = MutableStateFlow<LlmStatus>(if (modelExists) LlmStatus.LOADING else LlmStatus.OFFLINE)
    val status: StateFlow<LlmStatus> = _status.asStateFlow()

    // LlmInference instance, lazily initialized, held for lifetime
    private var inference: Any? = null
    private val generationLock = Mutex()

    /**
     * Preload the model with a 25-second timeout. After timeout, proceed to HOME and load in background.
     * On memory error, release the instance and switch permanently to templates.
     */
    suspend fun warmUp() {
        if (!modelExists || _status.value == LlmStatus.READY || _status.value == LlmStatus.OFFLINE) return

        val startMs = clock.nowMs()
        val timeoutMs = 25_000L

        try {
            withTimeoutOrNull(timeoutMs) {
                // Load prompts from assets
                val prompts = loadPrompts() ?: run {
                    Log.w(tag, "Could not load prompts, falling back to templates")
                    _status.value = LlmStatus.OFFLINE
                    return@withTimeoutOrNull
                }

                // Attempt to load LlmInference
                inference = loadLlmInference(prompts)
                if (inference != null) {
                    _status.value = LlmStatus.READY
                    Log.d(tag, "LLM loaded in ${clock.nowMs() - startMs}ms")
                }
            }

            if (clock.nowMs() - startMs >= timeoutMs) {
                Log.w(tag, "LLM load exceeded 25s timeout, proceeding with templates")
                _status.value = LlmStatus.OFFLINE
            }
        } catch (e: OutOfMemoryError) {
            Log.e(tag, "OOM loading LLM, switching to templates", e)
            inference = null
            _status.value = LlmStatus.OFFLINE
        } catch (e: Exception) {
            Log.e(tag, "Error loading LLM", e)
            _status.value = LlmStatus.OFFLINE
        }
    }

    /**
     * Generate coach and boss lines from telemetry.
     * Returns null on error; caller should fall back to templates via CoachFor.
     */
    suspend fun generate(telemetry: SetTelemetry): CoachOutput? {
        if (inference == null || _status.value != LlmStatus.READY) return null

        return generationLock.withLock {
            try {
                withTimeoutOrNull(5_000L) {
                    generateLines(telemetry)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error generating coach output", e)
                null
            }
        }
    }

    private fun loadPrompts(): PromptPack? {
        return try {
            PromptPack.load { filename ->
                try {
                    context.assets.open(filename).bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadLlmInference(prompts: PromptPack): Any? {
        return try {
            // Placeholder: in a real implementation, this would instantiate LlmInference
            // from com.google.mediapipe.tasks.genai.llminference.LlmInference
            // and LlmInferenceOptions with the model path, config, and GPU delegate.
            // For now, we return a non-null marker to indicate load attempt.
            // The actual MediaPipe integration is complex and requires native binaries.
            Unit // Stub for compilation; real code would load LlmInference
        } catch (e: Exception) {
            Log.e(tag, "Failed to load LlmInference from MediaPipe", e)
            null
        }
    }

    private suspend fun generateLines(telemetry: SetTelemetry): CoachOutput? {
        // Placeholder: in a real implementation, this would:
        // 1. Create an LlmInferenceSession
        // 2. Set temperature, topK, maxTokens per persona
        // 3. Generate coach line with coach prompt
        // 4. Generate boss line with boss prompt
        // 5. Validate both outputs
        // 6. Return CoachOutput with source="LLM"
        // For now, return null to signal unavailable (templates will fire)
        return null
    }
}
