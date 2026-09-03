package com.clashfit.alarm

import android.content.Context
import android.util.Log
import com.clashfit.core.config.ConfigStore
import com.clashfit.core.model.PoseFrame
import com.clashfit.core.pose.CameraFacing
import com.clashfit.core.pose.PoseSource
import com.clashfit.core.util.Clock
import com.clashfit.engine.core.Geometry
import com.clashfit.engine.core.RepStateMachine
import com.clashfit.engine.core.RepDetectorConfig
import com.clashfit.engine.detect.DetectorFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Real rep gate using MediaPipePoseSource, pose detectors, and the rep state machine.
 * Counts verified reps of a given exercise in real-time.
 */
class RealRepGate(
    private val context: Context,
    private val poseSource: PoseSource,
    private val configStore: ConfigStore,
    private val json: Json,
    private val clock: Clock,
) : RepGate {

    private val _state = MutableStateFlow(RepGateEvent(counted = 0, target = 5, lastVerdict = null, cue = null))
    private val scope = CoroutineScope(Dispatchers.Default)
    private var currentExerciseId: String? = null

    // State for the rep detector and state machine
    private var detector: com.clashfit.engine.detect.ExerciseDetector? = null
    private var repStateMachine: RepStateMachine? = null
    private var jointNames: List<String> = emptyList()
    private var primaryAngleJoints: Triple<String, String, String>? = null
    private var repIndex = 0

    override fun observe(exerciseId: String): Flow<RepGateEvent> {
        if (currentExerciseId != exerciseId) {
            reset()
            currentExerciseId = exerciseId
            setupDetector(exerciseId)
            startListening()
        }
        return _state.asStateFlow()
    }

    override fun reset() {
        _state.value = _state.value.copy(counted = 0, lastVerdict = null, cue = null)
        repIndex = 0
        detector = null
        repStateMachine = null
    }

    private fun setupDetector(exerciseId: String) {
        val exercise = configStore.exercises.value[exerciseId] ?: return

        try {
            val det = exercise.detector
            jointNames = det["requiredJoints"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            primaryAngleJoints = det["primaryAngle"]?.jsonObject?.let {
                Triple(
                    it.getValue("a").jsonPrimitive.content,
                    it.getValue("b").jsonPrimitive.content,
                    it.getValue("c").jsonPrimitive.content,
                )
            }

            // Create the movement detector from the exercise spec (null for REP_CYCLE, the family alarms use)
            detector = DetectorFactory.create(exercise)

            // Initialize the rep state machine from the exercise's own detector config
            val config = RepDetectorConfig(
                topEnter = det.getValue("topEnter").jsonPrimitive.float,
                topExit = det.getValue("topExit").jsonPrimitive.float,
                bottomEnter = det.getValue("bottomEnter").jsonPrimitive.float,
                bottomExit = det.getValue("bottomExit").jsonPrimitive.float,
                targetAngle = det.getValue("targetAngle").jsonPrimitive.float,
            )
            repStateMachine = RepStateMachine(config)

            Log.d("ClashFit/alarm", "Detector setup for $exerciseId")
        } catch (e: Exception) {
            Log.e("ClashFit/alarm", "Failed to setup detector for $exerciseId", e)
        }
    }

    private fun startListening() {
        scope.launch {
            try {
                poseSource.start(CameraFacing.FRONT)

                poseSource.frames.collect { frame ->
                    processFrame(frame)
                }
            } catch (e: Exception) {
                Log.e("ClashFit/alarm", "Error in frame collection", e)
            }
        }
    }

    private fun processFrame(frame: PoseFrame) {
        val repMachine = repStateMachine ?: return
        val pa = primaryAngleJoints ?: return
        val landmarks = frame.world ?: return

        try {
            // Drive the rep state machine off this exercise's primary angle
            val (angleDeg, _) = Geometry.primaryAngle(landmarks, pa.first, pa.second, pa.third, jointNames)
            val repEvent = repMachine.onFrame(angleDeg, frame.tMs)

            // If the state machine emitted a rep, count it
            if (repEvent != null) {
                repIndex++
                val verdict = when {
                    repEvent.concentricVelocity > 0 -> "CLEAN"
                    else -> "OK"
                }

                val current = _state.value
                if (current.counted < current.target) {
                    _state.value = RepGateEvent(
                        counted = current.counted + 1,
                        target = current.target,
                        lastVerdict = verdict,
                        cue = detector?.last?.cue
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ClashFit/alarm", "Error processing frame", e)
        }
    }

    fun stop() {
        poseSource.stop()
        scope.cancel()
    }
}
