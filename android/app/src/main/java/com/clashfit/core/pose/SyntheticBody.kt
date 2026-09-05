package com.clashfit.core.pose

import com.clashfit.core.model.Landmark
import com.clashfit.core.model.Landmarks
import com.clashfit.core.model.PoseFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A side-on figure built from angles. World landmarks are metric and hip-centred like MediaPipe's;
 * the knee bends in the y-z plane so the knee stays over the ankle laterally, which is what a
 * real side-on squat looks like to the alignment scorer. Used by the JVM tests and by the
 * camera-free demo source.
 */
object SyntheticBody {
    private const val LEG = 0.45f
    private const val ARM = 0.30f

    /**
     * 33 world landmarks. `kneeDeg` is the hip–knee–ankle angle; 170 is standing, 90 is deep.
     *
     * Arms hang from the shoulders. `shoulderAbductionDeg` swings the upper arm out to the side in
     * the frontal plane (0 hanging, 90 level with the shoulder, 180 straight overhead) and
     * `shoulderElevationDeg` swings it forward in the sagittal plane the same way; `elbowDeg` is the
     * shoulder–elbow–wrist angle (170 straight, 40 curled). The forearm folds forward from a hanging
     * arm and behind the head from an overhead one, as a real elbow does. Per-side values override
     * the shared ones, so alternating curls and one-arm raises can be posed.
     */
    fun world(
        kneeDeg: Float,
        elbowDeg: Float = 170f,
        visibility: Float = 1f,
        leftElbowDeg: Float? = null,
        rightElbowDeg: Float? = null,
        shoulderAbductionDeg: Float = 0f,
        shoulderElevationDeg: Float = 0f,
        leftShoulderAbductionDeg: Float? = null,
        rightShoulderAbductionDeg: Float? = null,
        leftShoulderElevationDeg: Float? = null,
        rightShoulderElevationDeg: Float? = null,
    ): Landmarks {
        val k = Math.toRadians(kneeDeg.toDouble())
        val lms = MutableList(33) { Landmark(0f, 1.05f, 0f, visibility) }
        fun set(i: Int, x: Float, y: Float, z: Float) { lms[i] = Landmark(x, y, z, visibility) }
        for ((side, sx) in listOf(11 to -0.15f, 12 to 0.15f)) {
            val left = side == 11
            val hip = if (left) 23 else 24
            val knee = if (left) 25 else 26
            val ankle = if (left) 27 else 28
            val shoulder = side
            val elbow = if (left) 13 else 14
            val wrist = if (left) 15 else 16
            val heel = if (left) 29 else 30
            val foot = if (left) 31 else 32
            val hx = sx * 0.6f
            set(hip, hx, 0.50f, 0f)
            set(knee, hx, 0.00f, 0f)
            // knee→ankle makes angle kneeDeg with knee→hip (straight up).
            set(ankle, hx, (LEG * cos(k)).toFloat(), (LEG * sin(k)).toFloat())
            set(heel, hx, (LEG * cos(k)).toFloat() - 0.03f, (LEG * sin(k)).toFloat() - 0.02f)
            set(foot, hx, (LEG * cos(k)).toFloat() - 0.03f, (LEG * sin(k)).toFloat() + 0.10f)
            set(shoulder, sx, 0.95f, 0f)

            val e = Math.toRadians((if (left) leftElbowDeg ?: elbowDeg else rightElbowDeg ?: elbowDeg).toDouble())
            val a = Math.toRadians((if (left) leftShoulderAbductionDeg ?: shoulderAbductionDeg else rightShoulderAbductionDeg ?: shoulderAbductionDeg).toDouble())
            val f = Math.toRadians((if (left) leftShoulderElevationDeg ?: shoulderElevationDeg else rightShoulderElevationDeg ?: shoulderElevationDeg).toDouble())
            // Upper-arm direction: straight down, swung out (abduction) and forward (elevation).
            val out = if (left) -1.0 else 1.0
            val dx = out * sin(a); val dy = -cos(a) * cos(f); val dz = cos(a) * sin(f)
            val ex = sx + (ARM * dx).toFloat(); val ey = 0.95f + (ARM * dy).toFloat(); val ez = 0.02f + (ARM * dz).toFloat()
            set(elbow, ex, ey, ez)
            // The forearm bends in the plane perpendicular to the upper arm: forward for a hanging
            // arm, behind the head for an overhead one (cross product with the body's x axis).
            var px = 0.0; var py = dz; var pz = -dy
            val pn = sqrt(py * py + pz * pz)
            if (pn < 1e-6) { px = 0.0; py = 0.0; pz = 1.0 } else { py /= pn; pz /= pn }
            val wx = -dx * cos(e) + px * sin(e); val wy = -dy * cos(e) + py * sin(e); val wz = -dz * cos(e) + pz * sin(e)
            set(wrist, ex + (ARM * wx).toFloat(), ey + (ARM * wy).toFloat(), ez + (ARM * wz).toFloat())
        }
        // Head cluster.
        for (i in 0..10) set(i, if (i % 2 == 0) 0.03f else -0.03f, 1.12f, 0.02f)
        for (i in 17..22) { val l = i % 2 == 1; set(i, if (l) -0.15f else 0.15f, 0.95f - ARM * 2, 0.05f) }
        return lms
    }

    /** Normalised image landmarks whose bounding box has the given height, centred in frame. */
    fun image(boxHeight: Float = 0.75f, visibility: Float = 1f): Landmarks {
        val top = 0.5f - boxHeight / 2f; val bottom = 0.5f + boxHeight / 2f
        return List(33) { i ->
            val y = when (i) { in 0..10 -> top; 11, 12 -> top + boxHeight * 0.18f; 23, 24 -> top + boxHeight * 0.5f
                25, 26 -> top + boxHeight * 0.75f; 27, 28, 29, 30, 31, 32 -> bottom; else -> top + boxHeight * 0.4f }
            Landmark(0.5f + (if (i % 2 == 0) 0.03f else -0.03f), y, 0f, visibility)
        }
    }

    /** One scripted squat: knee angle over time from `tStart`, as (tMs, kneeDeg) samples at `stepMs`. */
    fun squatRep(
        // A correct squat turns at 65-72, so the default is one. It used to stop at 85, which is
        // a half squat under the shipped rule and counted nothing.
        tStart: Long, stepMs: Long = 33, top: Float = 170f, bottom: Float = 68f,
        descendMs: Long = 1200, bottomMs: Long = 250, ascendMs: Long = 700,
    ): List<Pair<Long, Float>> {
        val out = ArrayList<Pair<Long, Float>>()
        var t = tStart
        while (t < tStart + descendMs) { out += t to (top - (top - bottom) * ((t - tStart).toFloat() / descendMs)); t += stepMs }
        val b0 = t
        while (t < b0 + bottomMs) { out += t to bottom; t += stepMs }
        val a0 = t
        while (t < a0 + ascendMs) { out += t to (bottom + (top - bottom) * ((t - a0).toFloat() / ascendMs)); t += stepMs }
        out += t to top
        return out
    }
}

/**
 * A `PoseSource` with no camera: plays an endless scripted squat set at 30 fps. The emulator
 * demo and the preflight self-test run on this; the engine cannot tell the difference.
 */
class SyntheticPoseSource(private val scope: CoroutineScope, private val kneeTop: Float = 170f, private val kneeBottom: Float = 85f) : PoseSource {
    private val _frames = MutableSharedFlow<PoseFrame>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val frames: Flow<PoseFrame> = _frames
    override val fps = MutableStateFlow(30f)
    override val facing = MutableStateFlow(CameraFacing.FRONT)
    private var job: Job? = null
    private var lowPower = false

    override fun start(facing: CameraFacing) {
        this.facing.value = facing
        job?.cancel()
        job = scope.launch {
            var t = 0L
            val image = SyntheticBody.image()
            // Stand still to calibrate, then rep forever with a rest gap.
            repeat(75) { _frames.tryEmit(PoseFrame(SyntheticBody.world(kneeTop), image, t)); t += 33; delay(33) }
            while (true) {
                for ((tt, deg) in SyntheticBody.squatRep(t)) {
                    _frames.tryEmit(PoseFrame(SyntheticBody.world(deg), image, tt)); t = tt
                    delay(if (lowPower) 200 else 33)
                }
                repeat(30) { t += 33; _frames.tryEmit(PoseFrame(SyntheticBody.world(kneeTop), image, t)); delay(33) }
            }
        }
    }

    override fun stop() { job?.cancel(); job = null }
    override fun setLowPower(enabled: Boolean) { lowPower = enabled; fps.value = if (enabled) 5f else 30f }
}
