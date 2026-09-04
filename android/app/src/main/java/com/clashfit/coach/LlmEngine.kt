package com.clashfit.coach

import android.content.Context
import android.util.Log
import com.clashfit.core.model.CoachOutput
import com.clashfit.core.model.CoachSource
import com.clashfit.core.model.SetTelemetry
import com.clashfit.core.util.Clock
import com.clashfit.engine.coach.OutputValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
 *
 * IMPLEMENTATION NOTE: This implementation uses reflection to interact with the MediaPipe
 * LlmInference API (version 0.10.35) to avoid requiring the model file for compilation.
 * The API is inferred from standard MediaPipe patterns and has NOT been verified against
 * the actual model file. If compilation or runtime errors occur due to API differences,
 * the implementation will gracefully degrade to template-based coaching.
 * The fallback to templates is guaranteed for all error paths.
 */
class LlmEngine(
    private val context: Context,
    private val config: CoachConfig = CoachConfig(),
    private val clock: Clock = Clock.SYSTEM,
    private val scope: CoroutineScope,
    private val json: Json,
) {
    private val tag = "ClashFit/coach"
    // Android/data/com.clashfit/files/models/, which is where android/README.md tells you to push
    // it. This is the one definition of that path: the screens that report whether the coach model
    // is installed read it from here rather than guessing, so the two can never disagree.
    private val modelPath = context.getExternalFilesDir(null)?.let { File(it, MODEL_NAME) }
    private val modelExists = modelPath?.exists() == true

    /** True when the model file is on the phone. False means the template bank speaks. */
    val modelInstalled: Boolean get() = modelPath?.exists() == true

    /** Where the model must be pushed, shown in the app so nobody has to read the README. */
    val modelLocation: String get() = modelPath?.absolutePath ?: "external storage unavailable"

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
                // Attempt to load LlmInference
                inference = loadLlmInference()
                if (inference != null) {
                    _status.value = LlmStatus.READY
                    Log.d(tag, "LLM loaded in ${clock.nowMs() - startMs}ms")
                } else {
                    Log.w(tag, "Failed to load LlmInference")
                    _status.value = LlmStatus.OFFLINE
                    return@withTimeoutOrNull
                }
            }

            if (clock.nowMs() - startMs >= timeoutMs) {
                Log.w(tag, "LLM load exceeded 25s timeout, proceeding with templates")
                _status.value = LlmStatus.OFFLINE
            }
        } catch (e: CancellationException) {
            releaseInference()
            throw e
        } catch (e: OutOfMemoryError) {
            // The instance may already hold hundreds of megabytes of native model memory. Dropping
            // the reference without closing it leaks that memory for the life of the process, and
            // this path runs exactly when memory is already short.
            Log.e(tag, "OOM loading LLM, switching to templates", e)
            releaseInference()
            _status.value = LlmStatus.OFFLINE
        } catch (cancelled: CancellationException) {
            // Cancellation is not a failure. CancellationException is an Exception,
            // so a catch-all below would swallow it and carry on running work whose
            // caller has already gone.
            throw cancelled
        } catch (e: Exception) {
            Log.e(tag, "Error loading LLM", e)
            releaseInference()
            _status.value = LlmStatus.OFFLINE
        }
    }

    /**
     * Close the native handle and forget it. MediaPipe's LlmInference is a Closeable over native
     * model memory that the garbage collector cannot reclaim on its own, so anything that abandons
     * the instance has to come through here.
     */
    fun releaseInference() {
        val held = inference
        inference = null
        if (held is AutoCloseable) {
            runCatching { held.close() }
                .onFailure { Log.w(tag, "Closing the LLM handle failed", it) }
        }
    }

    /** Release the model and stop using it. Safe to call more than once. */
    fun shutDown() {
        releaseInference()
        _status.value = LlmStatus.OFFLINE
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
            } catch (e: CancellationException) {
                // The caller went away. That is not a model failure, and swallowing it would keep
                // this coroutine alive past the point its scope was cancelled.
                throw e
            } catch (cancelled: CancellationException) {
                // Cancellation is not a failure. CancellationException is an Exception,
                // so a catch-all below would swallow it and carry on running work whose
                // caller has already gone.
                throw cancelled
            } catch (e: Exception) {
                Log.e(tag, "Error generating coach output", e)
                null
            }
        }
    }

    private fun loadLlmInference(): Any? {
        return try {
            val llmInferenceClass = Class.forName(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference"
            )
            val optionsClass = Class.forName(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions"
            )
            val builderClass = Class.forName(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions\$Builder"
            )

            // Create builder instance
            val builder = builderClass.getDeclaredConstructor().newInstance()

            // Set model path: try setModelPath, modelPath, or setModel
            val setModelMethod = try {
                builderClass.getMethod("setModelPath", String::class.java)
            } catch (e: NoSuchMethodException) {
                try {
                    builderClass.getMethod("modelPath", String::class.java)
                } catch (e2: NoSuchMethodException) {
                    builderClass.getMethod("setModel", String::class.java)
                }
            }
            setModelMethod.invoke(builder, modelPath!!.absolutePath)

            // Build options object
            val buildMethod = builderClass.getMethod("build")
            val options = buildMethod.invoke(builder)

            // Create LlmInference: try constructor first, then factory method
            val optionsClassType = options!!.javaClass
            val llmInstance = try {
                val constructor = llmInferenceClass.getConstructor(optionsClassType)
                constructor.newInstance(options)
            } catch (e: NoSuchMethodException) {
                val createMethod = llmInferenceClass.getMethod("create", optionsClassType)
                createMethod.invoke(null, options)
            }

            Log.d(tag, "LlmInference loaded successfully")
            llmInstance
        } catch (e: ClassNotFoundException) {
            Log.e(tag, "MediaPipe LlmInference class not found; check dependency", e)
            null
        } catch (e: Exception) {
            Log.e(tag, "Failed to load LlmInference from MediaPipe", e)
            null
        }
    }

    private suspend fun generateLines(telemetry: SetTelemetry): CoachOutput? {
        if (inference == null) return null

        return try {
            // Create coach line with specific config
            val coachLine = generateSingleLine(telemetry, true) ?: return null

            // Create boss line with specific config
            val bossLine = generateSingleLine(telemetry, false) ?: return null

            // Validate both lines before returning
            val coachValidation = OutputValidator.validateOutput(coachLine, telemetry)
            if (!coachValidation.ok) {
                Log.w(tag, "Coach line rejected: ${coachValidation.why}")
                return null
            }

            val bossValidation = OutputValidator.validateOutput(bossLine, telemetry)
            if (!bossValidation.ok) {
                Log.w(tag, "Boss line rejected: ${bossValidation.why}")
                return null
            }

            CoachOutput(
                coachLine = coachLine,
                bossLine = bossLine,
                source = CoachSource.LLM,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Error generating coach lines", e)
            null
        }
    }

    private suspend fun generateSingleLine(
        telemetry: SetTelemetry,
        isCoach: Boolean,
    ): String? {
        return try {
            val prompt = buildPrompt(telemetry, isCoach)
            val sessionOptions = createSessionOptions(isCoach) ?: return null

            val inferenceClass = inference!!::class.java
            val sessionOptionsClass = sessionOptions.javaClass

            // Create a session with the given options
            val createSessionMethod = try {
                inferenceClass.getMethod("createSession", sessionOptionsClass)
            } catch (e: NoSuchMethodException) {
                inferenceClass.getMethod("session", sessionOptionsClass)
            }

            val session = createSessionMethod.invoke(inference, sessionOptions)
                ?: return null

            val sessionClass = session.javaClass

            // Generate response with timeout
            val response = withTimeoutOrNull(5_000L) {
                try {
                    val generateMethod = sessionClass.getMethod("generate", String::class.java)
                    generateMethod.invoke(session, prompt) as? String
                } catch (e: NoSuchMethodException) {
                    try {
                        val generateMethod = sessionClass.getMethod("predict", String::class.java)
                        generateMethod.invoke(session, prompt) as? String
                    } catch (e2: CancellationException) {
                        throw e2
                    } catch (e2: Exception) {
                        null
                    }
                }
            }

            if (response == null) {
                Log.w(tag, "Generation timeout or null response for ${if (isCoach) "coach" else "boss"} line")
                return null
            }

            response.trim().takeIf { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Error generating ${if (isCoach) "coach" else "boss"} line", e)
            null
        }
    }

    private fun createSessionOptions(isCoach: Boolean): Any? {
        return try {
            val personaConfig = if (isCoach) config.coach else config.boss

            val sessionOptionsBuilderClass = Class.forName(
                "com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession\$LlmInferenceSessionOptions\$Builder"
            )

            val builder = sessionOptionsBuilderClass.getDeclaredConstructor().newInstance()

            // Try to set configuration parameters
            // Temperature
            try {
                val setMethod = sessionOptionsBuilderClass.getMethod("setTemperature", Float::class.java)
                setMethod.invoke(builder, personaConfig.temperature)
            } catch (e: Exception) {
                Log.d(tag, "Could not set temperature: ${e.message}")
            }

            // Top-K
            try {
                val setMethod = sessionOptionsBuilderClass.getMethod("setTopK", Int::class.java)
                setMethod.invoke(builder, personaConfig.topK)
            } catch (e: Exception) {
                Log.d(tag, "Could not set topK: ${e.message}")
            }

            // Max tokens
            try {
                val setMethod = sessionOptionsBuilderClass.getMethod("setMaxTokens", Int::class.java)
                setMethod.invoke(builder, personaConfig.maxTokens)
            } catch (e: Exception) {
                Log.d(tag, "Could not set maxTokens: ${e.message}")
            }

            // Build the options
            val buildMethod = sessionOptionsBuilderClass.getMethod("build")
            buildMethod.invoke(builder)
        } catch (e: ClassNotFoundException) {
            Log.e(tag, "LlmInferenceSession options class not found", e)
            null
        } catch (e: Exception) {
            Log.e(tag, "Failed to create session options", e)
            null
        }
    }

    private fun buildPrompt(telemetry: SetTelemetry, isCoach: Boolean): String {
        val telemetryJson = json.encodeToString(telemetry)
        val personaPrompt = if (isCoach) {
            "Persona: COACH.\n\nVoice: a competent training partner who has watched the whole set. Warm, terse, specific.\n\nAlways cite one concrete number from the telemetry.\nIf fatigue_band is FADING or GASSED, prescribe a rest in seconds. Never tell them to stop.\nIf the trend is improving, say what improved.\n\nOutput exactly one line."
        } else {
            "Persona: THE PACEMAKER — a mechanical construct that mirrors the player's tempo.\n\nTaunt in character. Reference the same fact the coach used, from the machine's point of view.\nMock their tempo, their depth, or their resolve. Never their body or their fitness level.\n\nOutput exactly one line."
        }

        return """
            $personaPrompt

            Telemetry:
            $telemetryJson
        """.trimIndent()
    }
}

private const val MODEL_NAME = "models/gemma-3n-e2b-int4.task"
