package com.clashfit.core.pose

import com.clashfit.core.model.PoseFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class CameraFacing { FRONT, BACK }

/**
 * A sensor. Emits pose frames and nothing else; imports nothing from combat, coach, duel or ui.
 * The camera implementation and the trace-replay implementation are interchangeable behind this,
 * which is what lets the whole engine run on the JVM against a recorded set.
 */
interface PoseSource {
    /** Latest-wins stream of frames at the detector's rate. Null landmarks mean no pose. */
    val frames: Flow<PoseFrame>
    /** Measured detector throughput. */
    val fps: StateFlow<Float>
    val facing: StateFlow<CameraFacing>
    fun start(facing: CameraFacing = CameraFacing.FRONT)
    fun stop()
    /** Drop to a low rate between sets so the LLM has the GPU. */
    fun setLowPower(enabled: Boolean)
}
