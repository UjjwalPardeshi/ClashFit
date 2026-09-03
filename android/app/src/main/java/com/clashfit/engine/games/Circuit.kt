package com.clashfit.engine.games

/** One step in a circuit. */
data class CircuitStep(
    val exerciseId: String,
    val seconds: Int,
    val label: String,
)

/** A prescribed sequence of exercises across families. */
class Circuit(val steps: List<CircuitStep>) {
    var index: Int = 0
    var startedMs: Long? = null
    val results: MutableList<CircuitResult> = mutableListOf()

    /** Current step, or null if finished. */
    val current: CircuitStep? get() = steps.getOrNull(index)

    /** True if all steps completed. */
    val finished: Boolean get() = index >= steps.size

    /** Start the circuit. */
    fun start(tMs: Long) {
        startedMs = tMs
    }

    /** Milliseconds left in the current step. */
    fun leftMs(tMs: Long): Long {
        if (startedMs == null || current == null) return 0L
        return Math.max(0L, current!!.seconds * 1000L - (tMs - startedMs!!))
    }

    /** Advance to the next step and record the result. */
    fun advance(tMs: Long, result: CircuitResult = CircuitResult()): CircuitStep? {
        if (current != null) {
            results.add(result.copy(exerciseId = current!!.exerciseId, label = current!!.label))
        }
        index += 1
        startedMs = tMs
        return current
    }

    /** Current state. */
    fun state(): CircuitState {
        return CircuitState(
            step = index,
            total = steps.size,
            current = current,
            finished = finished,
            results = results.toList(),
        )
    }
}

/** Result from completing a circuit step. */
data class CircuitResult(
    val exerciseId: String = "",
    val seconds: Int = 0,
    val label: String = "",
    val reps: Int = 0,
)

/** Snapshot of circuit progress. */
data class CircuitState(
    val step: Int,
    val total: Int,
    val current: CircuitStep?,
    val finished: Boolean,
    val results: List<CircuitResult>,
)

/** Default circuit touching four movement families. */
val DEFAULT_CIRCUIT = listOf(
    CircuitStep(exerciseId = "squat", seconds = 45, label = "Squats"),
    CircuitStep(exerciseId = "plank", seconds = 45, label = "Plank"),
    CircuitStep(exerciseId = "high_knees", seconds = 40, label = "High knees"),
    CircuitStep(exerciseId = "jump_squat", seconds = 30, label = "Jump squats"),
    CircuitStep(exerciseId = "utkatasana", seconds = 30, label = "Chair pose"),
    CircuitStep(exerciseId = "chair_squat", seconds = 45, label = "Chair squats"),
)
