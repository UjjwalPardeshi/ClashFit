package com.clashfit.perception

import android.util.Log
import com.clashfit.core.model.Landmark
import com.clashfit.core.model.PoseFrame
import com.clashfit.core.pose.CameraFacing
import com.clashfit.core.pose.PoseSource
import com.clashfit.core.util.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Replay pose frames from a JSON Lines trace file. Supports both real timing (replaying at recorded speed)
 * and as-fast-as-possible mode (no delay between frames).
 */
class TracePoseSource(
    /**
     * Reads the trace, called once when the replay starts.
     *
     * A lambda rather than a String because the caller used to read 903 KB from assets inside a
     * `remember` block — on the main thread, during composition, with no guard if the asset were
     * missing. Deferring it means the read happens on IO inside the replay coroutine, where a
     * failure can be caught and reported as an empty replay instead of a crash.
     */
    private val traceJsonLines: () -> String,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val replayRealTiming: Boolean = true,
) : PoseSource {

    private val TAG = "ClashFit/perception"
    // Recreated on every start(): a finished replay closes its channel so collectors complete.
    private var frameChannel = Channel<PoseFrame>(capacity = 1)
    override val frames: Flow<PoseFrame> get() = frameChannel.receiveAsFlow()

    private val _fps = MutableStateFlow(0f)
    override val fps: StateFlow<Float> = _fps.asStateFlow()

    private val _facing = MutableStateFlow(CameraFacing.FRONT)
    override val facing: StateFlow<CameraFacing> = _facing.asStateFlow()

    private var replayJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true }

    private data class TraceFrame(val t: Long, val lm: List<List<Float>?>)
    private data class TraceHeader(val type: String, val v: Int, val keep: List<Int>, val frames: Int)

    override fun start(facing: CameraFacing) {
        _facing.value = facing
        replayJob?.cancel()
        if (frameChannel.isClosedForSend) frameChannel = Channel(capacity = 1)
        val channel = frameChannel
        replayJob = scope.launch {
            try { replayTrace() } finally { channel.close() }
        }
    }

    override fun stop() {
        replayJob?.cancel()
    }

    override fun setLowPower(enabled: Boolean) {
        // Trace replay doesn't have power mode
    }

    private suspend fun replayTrace() {
        try {
            // Off the main thread, and guarded. A missing or unreadable trace ends the replay
            // quietly instead of taking the app down with it.
            val text = withContext(Dispatchers.IO) {
                runCatching { traceJsonLines() }
                    .onFailure { Log.e(TAG, "Could not read the trace", it) }
                    .getOrNull()
            } ?: return
            val lines = text.split('\n').filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                Log.e(TAG, "Empty trace")
                return
            }

            // Parse header
            val headerJson = json.parseToJsonElement(lines[0]).jsonObject
            val keep = headerJson["keep"]?.jsonArray?.map { it.jsonPrimitive.content.toInt() }
                ?: listOf(11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32)

            val frames = mutableListOf<TraceFrame>()
            for (i in 1 until lines.size) {
                try {
                    val frameJson = json.parseToJsonElement(lines[i]).jsonObject
                    val t = frameJson["t"]?.jsonPrimitive?.content?.toLong() ?: 0L
                    val lmArray = frameJson["lm"]?.jsonArray?.mapNotNull { elem ->
                        // elem can be null or an array of floats
                        try {
                            val array = elem.jsonArray
                            array.map { it.jsonPrimitive.content.toFloat() }
                        } catch (e: Exception) {
                            null
                        }
                    } ?: emptyList()
                    frames.add(TraceFrame(t, lmArray))
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping malformed frame at line $i: ${e.message}")
                }
            }

            if (frames.isEmpty()) {
                Log.e(TAG, "No valid frames in trace")
                return
            }

            // Replay frames
            var lastFrameTime = 0L
            var frameCount = 0L
            var lastFpsUpdateMs = clock.nowMs()

            for ((idx, frame) in frames.withIndex()) {
                if (replayJob?.isCancelled == true) break

                // Reconstruct full 33-landmark array from sparse representation
                val fullLandmarks = Array(33) { Landmark(0f, 0f, 0f, 0f) }
                keep.forEachIndexed { i, idx ->
                    val lm = frame.lm.getOrNull(i)
                    if (lm != null && lm.size >= 4) {
                        fullLandmarks[idx] = Landmark(lm[0], lm[1], lm[2], lm[3])
                    }
                }

                val poseFrame = PoseFrame(
                    world = fullLandmarks.toList(),
                    image = fullLandmarks.toList(),
                    tMs = frame.t
                )

                frameChannel.send(poseFrame)

                // Update FPS
                frameCount++
                if (frameCount % 10 == 0L) {
                    val now = clock.nowMs()
                    val elapsed = now - lastFpsUpdateMs
                    if (elapsed > 0) {
                        val fps = (10 * 1000f) / elapsed
                        _fps.value = fps
                        lastFpsUpdateMs = now
                    }
                }

                // Handle timing
                if (replayRealTiming && idx < frames.size - 1) {
                    val nextFrameTime = frames[idx + 1].t
                    val delayMs = (nextFrameTime - frame.t).coerceAtLeast(1)
                    delay(delayMs)
                }
            }

            Log.i(TAG, "Trace replay complete: $frameCount frames")
        } catch (e: Exception) {
            Log.e(TAG, "Error replaying trace", e)
        }
    }
}
