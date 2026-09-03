package com.clashfit.alarm

import android.content.Context
import android.util.Log
import com.clashfit.core.config.ConfigStore
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.Landmarks
import com.clashfit.core.model.PoseFrame
import com.clashfit.core.model.RepEvent
import com.clashfit.core.pose.PoseSource
import com.clashfit.core.util.Clock
import com.clashfit.engine.core.Geometry
import com.clashfit.engine.core.LandmarkFilter
import com.clashfit.engine.core.RepDetectorConfig
import com.clashfit.engine.core.RepStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * The real rep gate: pose frames in, verified reps of one exercise out.
 *
 * It owns no camera. The screen starts and stops the [PoseSource]; this class only collects the
 * frames it emits, filters the landmarks, drives the exercise's own [RepStateMachine] off its
 * primary angle, and counts a rep every time the machine completes one. `target` is the alarm's
 * configured rep count — the alarm keeps ringing until [RepGateEvent.counted] reaches it.
 *
 * Alarms are restricted to the REP_CYCLE family (see AlarmEditScreen), which is exactly the family
 * the angle state machine handles.
 */
class RealRepGate(
    private val context: Context,
    private val poseSource: PoseSource,
    private val configStore: ConfigStore,
    private val json: Json,
    private val clock: Clock,
    target: Int = 5,
) : RepGate {

    /** Never zero: an alarm you can dismiss with no reps is not an alarm. */
    private val target: Int = target.coerceAtLeast(1)

    private val _state = MutableStateFlow(RepGateEvent(counted = 0, target = this.target, lastVerdict = null, cue = null))

    /** Image-space landmarks of the last frame, for the on-screen skeleton. Null when no pose. */
    private val _skeleton = MutableStateFlow<Landmarks?>(null)
    val skeleton: StateFlow<Landmarks?> = _skeleton.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private var currentExerciseId: String? = null
    private var spec: ExerciseSpec? = null
    private var repStateMachine: RepStateMachine? = null
    private var filter: LandmarkFilter? = null
    private var jointNames: List<String> = emptyList()
    private var primaryAngleJoints: Triple<String, String, String>? = null
    private var lastPoseMs: Long = 0L

    /**
     * Counts reps of [exerciseId]. Safe to call on every recomposition: the detector is built once
     * per exercise and the frame collector is only started once.
     */
    override fun observe(exerciseId: String): Flow<RepGateEvent> {
        if (currentExerciseId != exerciseId) {
            currentExerciseId = exerciseId
            job?.cancel()
            job = null
            spec = null
            repStateMachine = null
            clearCount()
        }
        start(exerciseId)
        return _state.asStateFlow()
    }

    /** Back to zero reps of the same exercise. Used by snooze; the detector stays wired up. */
    override fun reset() {
        repStateMachine?.reset()
        filter?.reset()
        clearCount()
    }

    /** Stops counting. The camera belongs to the caller, so it is left alone. */
    fun stop() {
        job?.cancel()
        job = null
    }

    private fun clearCount() {
        _state.value = RepGateEvent(counted = 0, target = target, lastVerdict = null, cue = null)
    }

    private fun start(exerciseId: String) {
        if (job?.isActive == true) return
        job = scope.launch {
            val exercise = awaitExercise(exerciseId)
            if (exercise == null) {
                Log.e(TAG, "No exercise config for $exerciseId; the gate cannot count reps")
                return@launch
            }
            if (!setupDetector(exercise)) return@launch
            poseSource.frames.collect { frame -> processFrame(frame) }
        }
    }

    /**
     * The ring activity can start cold, before the config store's first reload has landed, so wait
     * for the exercise to appear and force a read as a last resort.
     */
    private suspend fun awaitExercise(exerciseId: String): ExerciseSpec? {
        configStore.exercises.value[exerciseId]?.let { return it }
        val loaded = withTimeoutOrNull(CONFIG_WAIT_MS) {
            configStore.exercises.first { it.containsKey(exerciseId) }[exerciseId]
        }
        if (loaded != null) return loaded
        return withContext(Dispatchers.IO) {
            runCatching { configStore.reloadBlocking() }
                .onFailure { Log.e(TAG, "Config reload failed", it) }
            configStore.exercises.value[exerciseId]
        }
    }

    private fun setupDetector(exercise: ExerciseSpec): Boolean {
        val det = exercise.detector
        return try {
            jointNames = det["requiredJoints"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            primaryAngleJoints = det["primaryAngle"]?.jsonObject?.let {
                Triple(
                    it.getValue("a").jsonPrimitive.content,
                    it.getValue("b").jsonPrimitive.content,
                    it.getValue("c").jsonPrimitive.content,
                )
            } ?: error("exercise ${exercise.id} declares no primaryAngle")

            repStateMachine = RepStateMachine(repConfig(det))
            val f = configStore.pose.value.filter
            filter = LandmarkFilter(f.minCutoff, f.beta, f.dCutoff)
            spec = exercise
            Log.d(TAG, "Rep gate armed for ${exercise.id}, target $target")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set up the detector for ${exercise.id}", e)
            false
        }
    }

    /** Every threshold comes from the exercise's own config record; nothing here is a constant. */
    private fun repConfig(d: JsonObject): RepDetectorConfig = RepDetectorConfig(
        topEnter = d.getValue("topEnter").jsonPrimitive.float,
        topExit = d.getValue("topExit").jsonPrimitive.float,
        bottomEnter = d.getValue("bottomEnter").jsonPrimitive.float,
        bottomExit = d.getValue("bottomExit").jsonPrimitive.float,
        targetAngle = d.getValue("targetAngle").jsonPrimitive.float,
        minDescendMs = d["minDescendMs"]?.jsonPrimitive?.long ?: 200,
        minBottomMs = d["minBottomMs"]?.jsonPrimitive?.long ?: 120,
        minRepMs = d["minRepMs"]?.jsonPrimitive?.long ?: 800,
        maxRepMs = d["maxRepMs"]?.jsonPrimitive?.long ?: 8000,
    )

    private fun processFrame(frame: PoseFrame) {
        _skeleton.value = frame.image

        val machine = repStateMachine ?: return
        val pa = primaryAngleJoints ?: return
        val world = frame.world

        if (world.isNullOrEmpty()) {
            nudgeIfLost(frame.tMs)
            return
        }

        try {
            val lms = if (world.size == LANDMARK_COUNT) filter?.apply(world, frame.tMs) ?: world else world
            val threshold = configStore.pose.value.visibilityThreshold
            val result = Geometry.primaryAngle(lms, pa.first, pa.second, pa.third, jointNames, threshold)
            val angleDeg = result.angle

            if (angleDeg.isNaN()) nudgeIfLost(frame.tMs) else lastPoseMs = frame.tMs

            // A NaN angle is still fed in: the machine counts it as an invalid frame and refuses
            // to complete a rep it could not see all the way through.
            machine.onFrame(angleDeg, frame.tMs)?.let { count(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
        }
    }

    private fun count(rep: RepEvent) {
        val current = _state.value
        if (current.counted >= current.target) return
        _state.value = RepGateEvent(
            counted = current.counted + 1,
            target = current.target,
            lastVerdict = verdictOf(rep),
            cue = cueFor(rep),
        )
    }

    /**
     * A completed rep already cleared the exercise's range-of-motion and timing guards, so it
     * always counts; the verdict only says how deep it was against the configured target angle.
     */
    private fun verdictOf(rep: RepEvent): String = when {
        depthOf(rep) >= CLEAN_DEPTH -> "CLEAN"
        depthOf(rep) >= OK_DEPTH -> "OK"
        else -> "SHALLOW"
    }

    /** 1.0 when the rep reached the configured target angle, 0.0 when it never left the top. */
    private fun depthOf(rep: RepEvent): Float {
        val span = rep.uTopRef - rep.uTarget
        if (span <= 0f) return 1f
        return ((rep.uTopRef - rep.uMin) / span).coerceIn(0f, 1f)
    }

    /** Cues are the exercise's own words, from its config record. */
    private fun cueFor(rep: RepEvent): String? {
        val cues = spec?.cues ?: return null
        val tempo = spec?.form?.tempo?.eccentricTargetSec
        return when {
            depthOf(rep) < OK_DEPTH -> cues["tooHigh"]
            tempo != null && rep.tEccSec > 0f && rep.tEccSec < tempo * FAST_ECC_RATIO -> cues["tooFast"]
            else -> null
        }
    }

    /** No usable pose for a while: say so on the ring screen rather than sit there silently. */
    private fun nudgeIfLost(tMs: Long) {
        if (lastPoseMs == 0L) lastPoseMs = tMs
        if (tMs - lastPoseMs < LOST_POSE_MS) return
        val cues = spec?.cues ?: return
        val cue = cues["framing"] ?: cues["enter"] ?: return
        val current = _state.value
        if (current.cue != cue) _state.value = current.copy(cue = cue)
    }

    private companion object {
        const val TAG = "ClashFit/alarm"
        const val LANDMARK_COUNT = 33
        const val CONFIG_WAIT_MS = 4_000L
        const val LOST_POSE_MS = 2_000L
        const val CLEAN_DEPTH = 0.95f
        const val OK_DEPTH = 0.75f
        const val FAST_ECC_RATIO = 0.5f
    }
}
