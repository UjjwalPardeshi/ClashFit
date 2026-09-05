package com.clashfit.engine.core

import com.clashfit.core.model.Landmarks
import com.clashfit.core.model.Side
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.sqrt

/**
 * The rep counter from fitmon (github.com/UjjwalPardeshi/fitmon, client_side/script.js), ported as
 * it is. No smoothing, no dwell, no minimum duration: a stage flips to REST when the signal passes
 * the rest threshold, and a rep counts the moment the signal passes the count threshold while the
 * stage is REST, at most once per debounce. The thresholds are fitmon's own numbers, and the
 * landmarks arrive unfiltered, so a rep registers the instant the joint passes the line.
 *
 * "Wrist above the shoulder" is measured along the torso (hips to shoulders), so it means the same
 * thing whichever way the landmark frame's y axis points and however the phone is tilted.
 */
class StageCounter(val config: Config) {
    enum class Signal { ANGLE, WRIST_HEIGHT }
    enum class Sides { EACH, BOTH, EITHER, BEST }
    enum class Check { NONE, REST, COUNT }
    enum class Stage { NONE, REST, MOVED }

    /** `op` is "<" or ">"; the condition holds when the signal is on that side of `value`. */
    data class Condition(val op: String, val value: Float) {
        fun holds(v: Float): Boolean = v.isFinite() && if (op == "<") v < value else v > value
    }

    data class Config(
        val angle: Triple<String, String, String>,
        val signal: Signal = Signal.ANGLE,
        val sides: Sides = Sides.BEST,
        val rest: Condition,
        val count: Condition,
        val wristAboveShoulderAt: Check = Check.NONE,
        val debounceMs: Long = 800L,
    ) {
        companion object {
            /** Reads `"counter": "STAGE"` plus the `stage` block of an exercise's detector; null when the exercise is not stage-counted. */
            fun from(det: JsonObject): Config? {
                if (det["counter"]?.jsonPrimitive?.content != "STAGE") return null
                val pa = det["primaryAngle"]?.jsonObject ?: return null
                val angle = Triple(pa.getValue("a").jsonPrimitive.content, pa.getValue("b").jsonPrimitive.content, pa.getValue("c").jsonPrimitive.content)
                val st = det["stage"]?.jsonObject ?: return null
                fun cond(key: String): Condition {
                    val arr = st.getValue(key).jsonArray
                    return Condition(arr[0].jsonPrimitive.content, arr[1].jsonPrimitive.floatOrNull ?: 0f)
                }
                return Config(
                    angle = angle,
                    signal = st["signal"]?.jsonPrimitive?.content?.let { Signal.valueOf(it) } ?: Signal.ANGLE,
                    sides = st["sides"]?.jsonPrimitive?.content?.let { Sides.valueOf(it) } ?: Sides.BEST,
                    rest = cond("rest"),
                    count = cond("count"),
                    wristAboveShoulderAt = st["wristAboveShoulderAt"]?.jsonPrimitive?.content?.let { Check.valueOf(it) } ?: Check.NONE,
                    debounceMs = st["debounceMs"]?.jsonPrimitive?.longOrNull ?: 800L,
                )
            }
        }
    }

    /** One counted rep: from the last frame at rest to the frame that counted, with the angle's extremes between. */
    data class Rep(val side: Side?, val tStartMs: Long, val tEndMs: Long, val thetaMin: Float, val thetaMax: Float, val tDeepestMs: Long)

    private class Track {
        var stage = Stage.NONE
        var restAt: Long? = null           // the last frame at rest, where the rep starts
        var lastCountMs: Long? = null      // null, not a sentinel: tMs minus Long.MIN_VALUE overflows
        var min = Float.NaN; var max = Float.NaN
        var tMin = 0L; var tMax = 0L
        fun reset() { stage = Stage.NONE; restAt = null; lastCountMs = null; min = Float.NaN; max = Float.NaN }
    }

    private val left = Track()
    private val right = Track()
    private val one = Track()

    /** The last joint angle per side, for the overlay. */
    val angles: MutableMap<Side, Float> = mutableMapOf(Side.LEFT to Float.NaN, Side.RIGHT to Float.NaN)

    /** The frame at which a count was refused by the wrist-height check, for a cue. */
    var lastMissMs: Long? = null
        private set

    fun stage(side: Side): Stage = when (config.sides) {
        Sides.EACH -> (if (side == Side.LEFT) left else right).stage
        else -> one.stage
    }

    fun reset() { left.reset(); right.reset(); one.reset(); lastMissMs = null; angles[Side.LEFT] = Float.NaN; angles[Side.RIGHT] = Float.NaN }

    /** Feed one frame of raw world landmarks. `best` is the better-seen side, used by BEST. Returns the reps counted on this frame. */
    fun onFrame(world: Landmarks, tMs: Long, best: Side): List<Rep> {
        val aL = jointAngle(world, Side.LEFT); val aR = jointAngle(world, Side.RIGHT)
        angles[Side.LEFT] = aL; angles[Side.RIGHT] = aR
        val sL = signal(world, Side.LEFT, aL); val sR = signal(world, Side.RIGHT, aR)
        val upL = wristAbove(world, Side.LEFT); val upR = wristAbove(world, Side.RIGHT)
        val out = ArrayList<Rep>(2)
        when (config.sides) {
            Sides.EACH -> {
                step(left, config.rest.holds(sL), config.count.holds(sL), upL, aL, tMs, Side.LEFT)?.let { out += it }
                step(right, config.rest.holds(sR), config.count.holds(sR), upR, aR, tMs, Side.RIGHT)?.let { out += it }
            }
            Sides.BOTH -> step(one, config.rest.holds(sL) && config.rest.holds(sR), config.count.holds(sL) && config.count.holds(sR), upL && upR, mean(aL, aR), tMs, null)?.let { out += it }
            // One rep whichever arm does it, and both arms together are still one rep.
            Sides.EITHER -> step(one, config.rest.holds(sL) || config.rest.holds(sR), config.count.holds(sL) || config.count.holds(sR), upL || upR, leading(aL, aR), tMs, null)?.let { out += it }
            Sides.BEST -> {
                val s = if (best == Side.LEFT) sL else sR
                val up = if (best == Side.LEFT) upL else upR
                val a = if (best == Side.LEFT) aL else aR
                step(one, config.rest.holds(s), config.count.holds(s), up, a, tMs, best)?.let { out += it }
            }
        }
        return out
    }

    private fun step(t: Track, restHolds: Boolean, countHolds: Boolean, above: Boolean, angle: Float, tMs: Long, side: Side?): Rep? {
        if (angle.isFinite() && t.restAt != null) {
            if (angle < t.min) { t.min = angle; t.tMin = tMs }
            if (angle > t.max) { t.max = angle; t.tMax = tMs }
        }
        // The start position can be left as well as entered. fitmon only ever latched "up", so an
        // overhead set followed by curls at your sides counted the curls: once the arm comes down
        // the start position is gone, and nothing counts until it is taken up again.
        if (config.wristAboveShoulderAt == Check.REST && !above && t.stage == Stage.REST) {
            t.stage = Stage.NONE
            t.restAt = null
        }
        // Not while the rep is still being held. Under EITHER the two arms are read separately, so
        // one arm hanging at your side satisfies rest for the whole set; without this the counter
        // re-arms under that arm and fires again every debounce while the other is simply held
        // curled. For the single-signal modes rest and count are opposite sides of one line and
        // cannot both hold, so this costs them nothing.
        if (restHolds && !countHolds && (config.wristAboveShoulderAt != Check.REST || above)) {
            // Resting: the rep starts from the last frame here, so a long pause is not part of it.
            t.stage = Stage.REST
            t.restAt = tMs
            t.min = angle; t.max = angle; t.tMin = tMs; t.tMax = tMs
        }
        val debounced = t.lastCountMs?.let { tMs - it > config.debounceMs } ?: true
        if (countHolds && t.stage == Stage.REST && debounced) {
            if (config.wristAboveShoulderAt == Check.COUNT && !above) { lastMissMs = tMs; return null }
            t.stage = Stage.MOVED
            t.lastCountMs = tMs
            val start = t.restAt ?: tMs
            val deepest = if (config.count.op == "<") t.tMin else t.tMax
            return Rep(side, start, tMs, t.min, t.max, deepest)
        }
        return null
    }

    private fun signal(world: Landmarks, side: Side, angle: Float): Float = when (config.signal) {
        Signal.ANGLE -> angle
        Signal.WRIST_HEIGHT -> wristHeight(world, side)
    }

    /**
     * The interior angle at the middle joint, from the metric 3D world landmarks. fitmon measured
     * this on the flat picture; the true angle agrees with it for the camera view each exercise is
     * framed for, and unlike a flat reading it does not collapse when a limb bends toward the lens.
     */
    private fun jointAngle(world: Landmarks, side: Side): Float {
        val ai = Geometry.idx(config.angle.first, side); val bi = Geometry.idx(config.angle.second, side); val ci = Geometry.idx(config.angle.third, side)
        if (ai < 0 || bi < 0 || ci < 0 || ai >= world.size || bi >= world.size || ci >= world.size) return Float.NaN
        return Geometry.angle3(world[ai], world[bi], world[ci])
    }

    /** How far the wrist sits above the shoulder along the torso, in metres; negative is below. */
    private fun wristHeight(world: Landmarks, side: Side): Float {
        if (world.size < 25) return Float.NaN
        val sh = world[Geometry.idx("SHOULDER", side)]; val wr = world[Geometry.idx("WRIST", side)]
        val shL = world[LM.LEFT_SHOULDER]; val shR = world[LM.RIGHT_SHOULDER]; val hpL = world[LM.LEFT_HIP]; val hpR = world[LM.RIGHT_HIP]
        val ux = (shL.x + shR.x - hpL.x - hpR.x) / 2f; val uy = (shL.y + shR.y - hpL.y - hpR.y) / 2f; val uz = (shL.z + shR.z - hpL.z - hpR.z) / 2f
        val n = sqrt(ux * ux + uy * uy + uz * uz)
        if (n < 1e-6f) return Float.NaN
        return ((wr.x - sh.x) * ux + (wr.y - sh.y) * uy + (wr.z - sh.z) * uz) / n
    }

    private fun wristAbove(world: Landmarks, side: Side): Boolean = wristHeight(world, side).let { it.isFinite() && it > ABOVE_MARGIN_M }

    /**
     * The arm nearer the counting threshold, which is the one that did the work. Averaging a curled
     * arm with a hanging one would record a one-armed rep as half as deep as it really was, and the
     * form score would dock it for a depth the player actually reached.
     */
    private fun leading(a: Float, b: Float): Float = when {
        !a.isFinite() -> b
        !b.isFinite() -> a
        config.count.op == "<" -> minOf(a, b)
        else -> maxOf(a, b)
    }

    private fun mean(a: Float, b: Float): Float = when {
        a.isFinite() && b.isFinite() -> (a + b) / 2f
        a.isFinite() -> a
        else -> b
    }

    private companion object {
        /** A wrist level with the shoulder is not above it: a centimetre of clearance along the torso. */
        const val ABOVE_MARGIN_M = 0.01f
    }
}
