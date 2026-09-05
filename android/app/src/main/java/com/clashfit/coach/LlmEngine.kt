package com.clashfit.coach

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.clashfit.core.model.CoachOutput
import com.clashfit.core.model.CoachSource
import com.clashfit.core.model.SetTelemetry
import com.clashfit.core.util.Clock
import com.clashfit.engine.coach.OutputValidator
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.resume

/** Status of the LLM engine: LOADING (25s cap), READY (model loaded), OFFLINE (no model), THROTTLED (thermal). */
enum class LlmStatus { LOADING, READY, OFFLINE, THROTTLED }

/**
 * Gemma 3n, on the phone.
 *
 * Loads `files/models/gemma-3n-e2b-int4.task` if it is there and speaks for the coach and the boss
 * between sets; if it is not there the template bank speaks and nothing else changes. Never runs
 * two generations at once — a 2B model on a phone is one conversation at a time.
 *
 * This used to reach MediaPipe by reflection, against method names guessed from "standard patterns"
 * — `session.generate(String)`, an `LlmInference(options)` constructor — none of which exist in the
 * library that is actually on the classpath. Every path threw NoSuchMethodException, every catch
 * returned null, and the coach fell back to templates with the model sitting loaded in memory.
 * The library is a compile-time dependency; there was never a reason not to call it. These are the
 * real calls, checked against the decompiled 0.10.35 classes: `createFromOptions`, `addQueryChunk`,
 * `addImage`, `generateResponseAsync`.
 *
 * [complete] is the general entry: a prompt, optionally a picture, one answer. The set-end coach,
 * the referee's eyes and the chat all go through it, so there is one timeout, one lock and one
 * place where the model can be swapped.
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

    /** True once a generation has come back from the model in this process — the badge's evidence. */
    val ready: Boolean get() = _status.value == LlmStatus.READY

    private var inference: LlmInference? = null
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
            val loaded = withTimeoutOrNull(timeoutMs) { withContext(Dispatchers.IO) { loadInference() } }
            if (loaded != null) {
                inference = loaded
                _status.value = LlmStatus.READY
                Log.i(tag, "Gemma loaded in ${clock.nowMs() - startMs}ms")
            } else {
                Log.w(tag, "Gemma did not load within ${timeoutMs}ms; templates speak")
                _status.value = LlmStatus.OFFLINE
            }
        } catch (e: CancellationException) {
            releaseInference()
            throw e
        } catch (e: OutOfMemoryError) {
            // The instance may already hold hundreds of megabytes of native model memory. Dropping
            // the reference without closing it leaks that memory for the life of the process, and
            // this path runs exactly when memory is already short.
            Log.e(tag, "OOM loading Gemma, switching to templates", e)
            releaseInference()
            _status.value = LlmStatus.OFFLINE
        } catch (e: Exception) {
            Log.e(tag, "Error loading Gemma", e)
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
        held?.let { runCatching { it.close() }.onFailure { e -> Log.w(tag, "Closing the Gemma handle failed", e) } }
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
        if (!ready) return null
        val telemetryJson = json.encodeToString(telemetry)
        val coachLine = complete(
            prompt = "$COACH_PERSONA\n\nTelemetry:\n$telemetryJson",
            maxTokens = config.coach.maxTokens, temperature = config.coach.temperature, topK = config.coach.topK,
        ) ?: return null
        val coachCheck = OutputValidator.validateOutput(coachLine, telemetry)
        if (!coachCheck.ok) { Log.w(tag, "Coach line rejected: ${coachCheck.why}"); return null }

        val bossLine = complete(
            prompt = "$BOSS_PERSONA\n\nTelemetry:\n$telemetryJson",
            maxTokens = config.boss.maxTokens, temperature = config.boss.temperature, topK = config.boss.topK,
        ) ?: return null
        val bossCheck = OutputValidator.validateOutput(bossLine, telemetry)
        if (!bossCheck.ok) { Log.w(tag, "Boss line rejected: ${bossCheck.why}"); return null }

        return CoachOutput(coachLine = coachLine, bossLine = bossLine, source = CoachSource.LLM)
    }

    /**
     * One prompt, one answer, on the phone.
     *
     * @param image a picture for the model to look at, or null. It is handed to the session and
     *   never written anywhere; the caller owns the bitmap.
     * @return the model's text, trimmed, or null if the model is not loaded, timed out, or failed.
     */
    suspend fun complete(
        prompt: String,
        image: Bitmap? = null,
        maxTokens: Int = 96,
        temperature: Float = 0.4f,
        topK: Int = 30,
        timeoutMs: Long = 8_000L,
    ): String? {
        val llm = inference ?: return null
        if (!ready) return null
        return generationLock.withLock {
            var session: LlmInferenceSession? = null
            try {
                withTimeoutOrNull(timeoutMs) {
                    session = withContext(Dispatchers.IO) {
                        LlmInferenceSession.createFromOptions(
                            llm,
                            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                                .setTemperature(temperature)
                                .setTopK(topK)
                                .setGraphOptions(GraphOptions.builder().setEnableVisionModality(image != null).build())
                                .build(),
                        ).also { s ->
                            s.addQueryChunk(prompt)
                            if (image != null) s.addImage(BitmapImageBuilder(image).build())
                        }
                    }
                    session?.let { awaitResponse(it) }?.trim()?.takeIf { it.isNotEmpty() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                Log.e(tag, "OOM during generation; releasing Gemma", e)
                shutDown()
                null
            } catch (e: Exception) {
                Log.e(tag, "Gemma generation failed", e)
                null
            } finally {
                // A session holds KV-cache memory; a timed-out one must be cancelled AND closed.
                session?.let { s -> runCatching { s.cancelGenerateResponseAsync() }; runCatching { s.close() } }
            }
        }
    }

    /** The async generate as a suspend: cancelling the coroutine cancels the model. */
    private suspend fun awaitResponse(session: LlmInferenceSession): String? =
        suspendCancellableCoroutine { cont ->
            val future = session.generateResponseAsync()
            future.addListener(
                {
                    val result = runCatching { future.get() }
                    if (cont.isActive) cont.resume(result.getOrNull())
                },
                Runnable::run,
            )
            cont.invokeOnCancellation { runCatching { session.cancelGenerateResponseAsync() } }
        }

    private fun loadInference(): LlmInference? {
        val path = modelPath?.absolutePath ?: return null
        return try {
            LlmInference.createFromOptions(
                context,
                LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(path)
                    .setMaxTokens(MAX_CONTEXT_TOKENS)
                    // One picture per session is what the referee's eyes and the chat's setup check
                    // need; the option has to be set at load time or addImage throws.
                    .setMaxNumImages(1)
                    .setPreferredBackend(LlmInference.Backend.DEFAULT)
                    .build(),
            )
        } catch (e: Exception) {
            Log.e(tag, "LlmInference.createFromOptions failed", e)
            null
        }
    }

    private companion object {
        /** Prompt plus answer. The fact sheet the chat builds is capped well under this. */
        const val MAX_CONTEXT_TOKENS = 1024

        const val COACH_PERSONA = "Persona: COACH.\n\nVoice: a competent training partner who has watched the whole set. Warm, terse, specific.\n\nAlways cite one concrete number from the telemetry.\nIf fatigue_band is FADING or GASSED, prescribe a rest in seconds. Never tell them to stop.\nIf the trend is improving, say what improved.\n\nOutput exactly one line."
        const val BOSS_PERSONA = "Persona: THE PACEMAKER — a mechanical construct that mirrors the player's tempo.\n\nTaunt in character. Reference the same fact the coach used, from the machine's point of view.\nMock their tempo, their depth, or their resolve. Never their body or their fitness level.\n\nOutput exactly one line."
    }
}

private const val MODEL_NAME = "models/gemma-3n-e2b-int4.task"
