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

    /**
     * Where in the movement a rep lands.
     *
     * CROSSING is fitmon's rule and is right whenever the counting end is the end of the rep: you
     * stand up out of a squat and the squat is over. RETURN is for a movement whose rep is not
     * finished at the top — a lateral raise is only a rep once the arms come back down — and it is
     * also the only way a ceiling can mean "do not go past here", because how far you went is not
     * known until you turn around.
     *
     * Getting this backwards is not subtle: a squat judged on RETURN lands each rep at the start of
     * the next one, and the last rep of every set is never counted at all.
     */
    enum class CountAt { CROSSING, RETURN }

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
        /**
         * How far past the counting threshold the movement may go and still be that movement.
         *
         * Without one, a rep counts the instant the signal crosses `count`, which is fitmon's rule
         * and right for a curl: there is no such thing as curling too far. A lateral raise is not
         * like that. Past about 110 degrees the shoulder stops abducting and starts pressing, and
         * counting it would reward the wrong movement. With a ceiling set the rep is judged over
         * the whole cycle instead: it counts on the way back down, and only if the turning point
         * stayed inside the band.
         */
        val ceiling: Float? = null,
        /**
         * Which way the arm has to be pointing, as a condition on [lateralFraction] — 1 is
         * straight out to the side, 0 is straight forward, negative is across the body.
         *
         * The hip–shoulder–elbow angle cannot tell a lateral raise from a front raise: the arm is
         * a hundred degrees away from the torso either way, and until this existed every front
         * raise was counted as a lateral one. `[">", 0.5]` asks for out to the side and `["<", 0.5]`
         * for out in front, which is the same guard read in the other direction.
         */
        val plane: Condition? = null,
        val countAt: CountAt = CountAt.CROSSING,
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
                fun optCond(key: String): Condition? = if (st[key] == null) null else cond(key)
                return Config(
                    angle = angle,
                    signal = st["signal"]?.jsonPrimitive?.content?.let { Signal.valueOf(it) } ?: Signal.ANGLE,
                    sides = st["sides"]?.jsonPrimitive?.content?.let { Sides.valueOf(it) } ?: Sides.BEST,
                    rest = cond("rest"),
                    count = cond("count"),
                    wristAboveShoulderAt = st["wristAboveShoulderAt"]?.jsonPrimitive?.content?.let { Check.valueOf(it) } ?: Check.NONE,
                    ceiling = st["ceiling"]?.jsonPrimitive?.floatOrNull,
                    plane = optCond("plane"),
                    countAt = st["countAt"]?.jsonPrimitive?.content?.let { CountAt.valueOf(it) } ?: CountAt.CROSSING,
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
        var reached = false                // the signal has been past `count` since the last rest
        var brokeCeiling = false           // and it went past `ceiling` while it was there
        fun reset() { stage = Stage.NONE; restAt = null; lastCountMs = null; min = Float.NaN; max = Float.NaN; reached = false; brokeCeiling = false }
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

    /** What one frame says about one movement: the four tests a rep has to pass, and the angle to record. */
    private data class Read(
        val restHolds: Boolean,
        val countHolds: Boolean,
        val above: Boolean,
        val over: Boolean,
        val inPlane: Boolean,
        val angle: Float,
    )

    /** Feed one frame of raw world landmarks. `best` is the better-seen side, used by BEST. Returns the reps counted on this frame. */
    fun onFrame(world: Landmarks, tMs: Long, best: Side): List<Rep> {
        val aL = jointAngle(world, Side.LEFT); val aR = jointAngle(world, Side.RIGHT)
        angles[Side.LEFT] = aL; angles[Side.RIGHT] = aR
        val sL = signal(world, Side.LEFT, aL); val sR = signal(world, Side.RIGHT, aR)
        val upL = wristAbove(world, Side.LEFT); val upR = wristAbove(world, Side.RIGHT)
        val overL = overCeiling(sL); val overR = overCeiling(sR)
        val plL = inPlane(world, Side.LEFT); val plR = inPlane(world, Side.RIGHT)
        val out = ArrayList<Rep>(2)
        when (config.sides) {
            Sides.EACH -> {
                step(left, Read(config.rest.holds(sL), config.count.holds(sL), upL, overL, plL, aL), tMs, Side.LEFT)?.let { out += it }
                step(right, Read(config.rest.holds(sR), config.count.holds(sR), upR, overR, plR, aR), tMs, Side.RIGHT)?.let { out += it }
            }
            // Either arm going too far spoils the rep, and both have to be doing the same movement.
            Sides.BOTH -> step(one, Read(config.rest.holds(sL) && config.rest.holds(sR), config.count.holds(sL) && config.count.holds(sR), upL && upR, overL || overR, plL && plR, mean(aL, aR)), tMs, null)?.let { out += it }
            // One rep whichever arm does it, and both arms together are still one rep.
            Sides.EITHER -> step(one, Read(config.rest.holds(sL) || config.rest.holds(sR), config.count.holds(sL) || config.count.holds(sR), upL || upR, overL || overR, plL || plR, leading(aL, aR)), tMs, null)?.let { out += it }
            Sides.BEST -> {
                val s = if (best == Side.LEFT) sL else sR
                val read = Read(
                    config.rest.holds(s), config.count.holds(s),
                    if (best == Side.LEFT) upL else upR, overCeiling(s),
                    if (best == Side.LEFT) plL else plR,
                    if (best == Side.LEFT) aL else aR,
                )
                step(one, read, tMs, best)?.let { out += it }
            }
        }
        return out
    }

    /** True when the signal has gone further than the movement allows, in the counting direction. */
    private fun overCeiling(signal: Float): Boolean {
        val c = config.ceiling ?: return false
        if (!signal.isFinite()) return false
        return if (config.count.op == "<") signal < c else signal > c
    }

    private fun step(t: Track, r: Read, tMs: Long, side: Side?): Rep? {
        val restHolds = r.restHolds; val countHolds = r.countHolds; val above = r.above; val over = r.over; val angle = r.angle
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

        // Judged over the whole cycle: going too far is only visible at the turning point, so the
        // rep lands when the movement comes back to rest, which is also the moment the player has
        // actually finished it.
        if (config.countAt == CountAt.RETURN) {
            if (countHolds && r.inPlane && t.stage == Stage.REST) t.reached = true
            if (over && t.stage == Stage.REST) t.brokeCeiling = true
            if (restHolds) {
                var rep: Rep? = null
                if (t.reached && !t.brokeCeiling && debounced) {
                    t.lastCountMs = tMs
                    val deepest = if (config.count.op == "<") t.tMin else t.tMax
                    rep = Rep(side, t.restAt ?: tMs, tMs, t.min, t.max, deepest)
                }
                t.stage = Stage.REST
                t.restAt = tMs
                t.reached = false
                t.brokeCeiling = false
                // Always, not only after a rep: a cycle refused for going too far must not leave
                // its turning point behind to be reported as the next rep's depth.
                t.min = angle; t.max = angle; t.tMin = tMs; t.tMax = tMs
                return rep
            }
            return null
        }

        if (countHolds && t.stage == Stage.REST && debounced) {
            if (config.wristAboveShoulderAt == Check.COUNT && !above) { lastMissMs = tMs; return null }
            // The right movement in the wrong plane is a different exercise, not a bad rep.
            if (!r.inPlane) { lastMissMs = tMs; return null }
            // The ceiling on a movement that ends where it counts: the reading at the counting
            // frame has to be one the joint can actually produce. It refuses a rep built on a bad
            // frame rather than a rep performed wrongly, which is the only sense a ceiling can have
            // when the rep is over the instant it crosses the line.
            if (over) { lastMissMs = tMs; return null }
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

    /** Whether the arm is pointing the way this exercise requires. Always true when no plane is configured. */
    private fun inPlane(world: Landmarks, side: Side): Boolean {
        val c = config.plane ?: return true
        return c.holds(lateralFraction(world, side))
    }

    /**
     * How much of the upper arm points straight out to the side, as a cosine: 1 is horizontally
     * abducted away from the body, 0 is straight forward (or straight up, or hanging), negative is
     * across the midline.
     *
     * Sideways is the line between the shoulders with the torso's own lean taken out of it, so it
     * still means sideways when the player leans, turns a little, or holds the phone crooked. The
     * upper arm is used rather than the wrist because it is the segment the exercise's angle is
     * measured on, and it does not move when the elbow bends.
     */
    private fun lateralFraction(world: Landmarks, side: Side): Float {
        if (world.size < 25) return Float.NaN
        val si = Geometry.idx("SHOULDER", side); val ei = Geometry.idx("ELBOW", side)
        if (si < 0 || ei < 0) return Float.NaN
        val shL = world[LM.LEFT_SHOULDER]; val shR = world[LM.RIGHT_SHOULDER]
        val hpL = world[LM.LEFT_HIP]; val hpR = world[LM.RIGHT_HIP]
        var ux = (shL.x + shR.x - hpL.x - hpR.x) / 2f
        var uy = (shL.y + shR.y - hpL.y - hpR.y) / 2f
        var uz = (shL.z + shR.z - hpL.z - hpR.z) / 2f
        val un = sqrt(ux * ux + uy * uy + uz * uz)
        if (un < 1e-6f) return Float.NaN
        ux /= un; uy /= un; uz /= un
        // Shoulder to shoulder, pointing away from the midline on this side, made perpendicular to the torso.
        val sgn = if (side == Side.LEFT) 1f else -1f
        var lx = sgn * (shL.x - shR.x); var ly = sgn * (shL.y - shR.y); var lz = sgn * (shL.z - shR.z)
        val d = lx * ux + ly * uy + lz * uz
        lx -= d * ux; ly -= d * uy; lz -= d * uz
        val ln = sqrt(lx * lx + ly * ly + lz * lz)
        if (ln < 1e-6f) return Float.NaN
        val sh = world[si]; val el = world[ei]
        val ax = el.x - sh.x; val ay = el.y - sh.y; val az = el.z - sh.z
        val an = sqrt(ax * ax + ay * ay + az * az)
        if (an < 1e-6f) return Float.NaN
        return ((ax * lx + ay * ly + az * lz) / (an * ln)).coerceIn(-1f, 1f)
    }

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
