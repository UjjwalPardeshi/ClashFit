package com.clashfit.engine.core

import com.clashfit.core.model.RepEvent

/** Hysteretic rep state machine driven by primary angle. */
enum class RepState { TOP, DESCENDING, BOTTOM, ASCENDING }

/** Configuration for the rep detector. */
data class RepDetectorConfig(
    val topEnter: Float,
    val topExit: Float,
    val bottomEnter: Float,
    val bottomExit: Float,
    val targetAngle: Float,
    val minDescendMs: Long = 200,
    val minBottomMs: Long = 120,
    val minRepMs: Long = 800,
    val maxRepMs: Long = 8000,
)

/** Finite state machine for detecting completed reps from a primary angle. */
class RepStateMachine(private val config: RepDetectorConfig) {
    // Signed direction: +1 if topEnter > bottomEnter (decreasing angle, squat/pushup)
    // -1 if increasing (calf raise/glute bridge)
    private val s = if (config.topEnter > config.bottomEnter) 1f else -1f
    private val uTopEnter = s * config.topEnter
    private val uTopExit = s * config.topExit
    private val uBottomEnter = s * config.bottomEnter
    private val uBottomExit = s * config.bottomExit
    private val uTarget = s * config.targetAngle

    var state = RepState.TOP
        private set
    private var repIndex = 0
    private var stateEnteredMs = 0L
    private var topRefU: Float? = null
    private var lastRepEndMs: Long? = null
    private var seenValid = false

    private var rep: PartialRep? = null

    private data class PartialRep(
        val tStartMs: Long,
        var uMin: Float,
        var uMax: Float,
        var tBottomStart: Long? = null,
        var tBottomEnd: Long? = null,
        var uAtBottomExit: Float = 0f,
        var frames: Int = 0,
        var validFrames: Int = 0,
    )

    /** Set the calibrated rest position in degrees. */
    fun setTopRef(angleDeg: Float) {
        topRefU = s * angleDeg
    }

    /** Reset to the initial state. */
    fun reset() {
        state = RepState.TOP
        repIndex = 0
        rep = null
        lastRepEndMs = null
        topRefU = null
        seenValid = false
    }

    /** Feed one frame of primary angle (in degrees, NaN if invalid). Returns a completed rep or null. */
    fun onFrame(angleDeg: Float, tMs: Long): RepEvent? {
        val valid = angleDeg.isFinite()
        rep?.let {
            it.frames++
            if (valid) it.validFrames++
        }
        if (!valid) return null

        val u = s * angleDeg
        if (!seenValid) {
            seenValid = true
            stateEnteredMs = tMs
        }
        if (topRefU == null) topRefU = u

        rep?.let {
            if (u < it.uMin) it.uMin = u
            if (u > it.uMax) it.uMax = u
        }

        val dwell = tMs - stateEnteredMs

        when (state) {
            RepState.TOP -> {
                if (u >= uTopEnter && u > topRefU!!) topRefU = u
                if (u < uTopExit) {
                    rep = PartialRep(tMs, u, u)
                    enter(RepState.DESCENDING, tMs)
                }
            }
            RepState.DESCENDING -> {
                if (u >= uTopEnter) {
                    rep = null
                    enter(RepState.TOP, tMs)
                } else if (u <= uBottomEnter && dwell >= config.minDescendMs) {
                    rep?.tBottomStart = tMs
                    enter(RepState.BOTTOM, tMs)
                }
            }
            RepState.BOTTOM -> {
                if (u > uBottomExit && dwell >= config.minBottomMs) {
                    rep?.tBottomEnd = tMs
                    rep?.uAtBottomExit = u
                    enter(RepState.ASCENDING, tMs)
                }
            }
            RepState.ASCENDING -> {
                if (u <= uBottomEnter) {
                    enter(RepState.BOTTOM, tMs)
                } else if (u >= uTopEnter) {
                    val result = complete(tMs, u)
                    enter(RepState.TOP, tMs)
                    rep = null
                    return result
                }
            }
        }
        return null
    }

    private fun enter(next: RepState, tMs: Long) {
        state = next
        stateEnteredMs = tMs
    }

    private fun complete(tMs: Long, u: Float): RepEvent? {
        val r = rep ?: return null
        val dur = tMs - r.tStartMs

        // Validity guards
        if (dur < config.minRepMs || dur > config.maxRepMs) return null
        val bs = r.tBottomStart ?: return null
        val be = r.tBottomEnd ?: return null
        val ratio = if (r.frames > 0) r.validFrames.toFloat() / r.frames else 0f
        if (ratio < 0.9f) return null

        val tEcc = (bs - r.tStartMs) / 1000f
        val tBottom = (be - bs) / 1000f
        val tCon = (tMs - be) / 1000f
        val vel = if (tCon > 0f) kotlin.math.abs(u - r.uAtBottomExit) / tCon else 0f

        repIndex++
        val gap = lastRepEndMs?.let { (r.tStartMs - it) / 1000f } ?: 0f
        lastRepEndMs = tMs

        return RepEvent(
            repIndex = repIndex,
            exerciseId = "",
            tStartMs = r.tStartMs,
            tEndMs = tMs,
            thetaMin = s * (if (s > 0) r.uMin else r.uMax),
            thetaMax = s * (if (s > 0) r.uMax else r.uMin),
            uMin = r.uMin,
            uMax = r.uMax,
            uTopRef = topRefU!!,
            uTarget = uTarget,
            tEccSec = tEcc,
            tBottomSec = tBottom,
            tConSec = tCon,
            concentricVelocity = vel,
            gapSec = gap,
            validFrameRatio = ratio,
        )
    }
}
