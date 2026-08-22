# Kotlin Core — Reference Implementation

**What this is.** The verified algorithms from the prototype, written in Kotlin, as pre-event
reference material — the same category as [CONFIG-PACK](CONFIG-PACK.md) and
[PROMPT-PACK](PROMPT-PACK.md).

**What this is not.** A module to copy. **Retype it at the event.** The event terms require
original work created during the event window, and the disclosure story stays clean only if what
lands in the event repository was typed there. Retyping a spec you already understand is three to
five times faster than writing it cold — that is the entire benefit, and it survives the rule.

**Source of truth is the JavaScript**, which is covered by 76 assertions. If the two ever
disagree, the JS is right and this document is stale.

Everything here is pure Kotlin with no Android dependency, so it unit-tests on the JVM in
milliseconds — write the tests first at the event, from [14-TEST-PLAN](../14-TEST-PLAN.md).

---

## 1. `core/model` — the shared vocabulary

```kotlin
enum class Family { REP_CYCLE, ISOMETRIC_HOLD, CADENCE, BALLISTIC, POSE_MATCH }
enum class FatigueBand { FRESH, WORKING, FADING, GASSED }
enum class Verdict { CLEAN, OK, SHALLOW }

data class Landmark(val x: Float, val y: Float, val z: Float, val visibility: Float)

data class RepEvent(
    val repIndex: Int,
    val exerciseId: String,
    val tStartMs: Long,
    val tEndMs: Long,
    val thetaMin: Float,
    val thetaMax: Float,
    val uMin: Float, val uMax: Float, val uTopRef: Float, val uTarget: Float,
    val tEccSec: Float, val tBottomSec: Float, val tConSec: Float,
    val concentricVelocity: Float,
    val gapSec: Float,
    val validFrameRatio: Float,
    val depthCm: Float = Float.NaN,
) {
    val durationMs: Long get() = tEndMs - tStartMs
}

data class FormScore(
    val depth: Float, val rom: Float, val tempo: Float, val alignment: Float,
    val formScore: Float, val reason: String,
) {
    fun verdict(clean: Float = 0.80f, ok: Float = 0.55f) = when {
        formScore >= clean -> Verdict.CLEAN
        formScore >= ok    -> Verdict.OK
        else               -> Verdict.SHALLOW
    }
}

data class FatigueState(
    val value: Float,
    val band: FatigueBand,
    val losses: Map<String, Float>,
    val baselineReps: Int,
    val hasBaseline: Boolean,
) {
    companion object {
        val FRESH = FatigueState(0f, FatigueBand.FRESH, emptyMap(), 0, false)
    }
}
```

---

## 2. `core/util/OneEuroFilter.kt`

One Euro rather than an EMA: it adapts its cutoff to speed, so landmarks are stable at the top
and bottom of a rep — where depth is measured — and responsive through the transitions.

```kotlin
class LowPass {
    private var y: Float? = null
    fun filter(x: Float, a: Float): Float {
        val prev = y
        val out = if (prev == null) x else a * x + (1 - a) * prev
        y = out
        return out
    }
    fun reset() { y = null }
}

class OneEuroFilter(
    private val minCutoff: Float = 1.0f,
    private val beta: Float = 0.007f,
    private val dCutoff: Float = 1.0f,
) {
    private val xf = LowPass()
    private val dxf = LowPass()
    private var prev: Float? = null
    private var tPrev: Long? = null

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * Math.PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    fun filter(value: Float, tMs: Long): Float {
        val last = tPrev
        if (last == null) {
            tPrev = tMs; prev = value
            return xf.filter(value, alpha(minCutoff, 1f / 30f))
        }
        var dt = (tMs - last) / 1000f
        if (dt <= 0f) dt = 1f / 30f          // duplicate or non-monotonic timestamps
        tPrev = tMs

        val dxRaw = (value - (prev ?: value)) / dt
        prev = value
        val dxHat = dxf.filter(dxRaw, alpha(dCutoff, dt))
        val cutoff = minCutoff + beta * kotlin.math.abs(dxHat)
        return xf.filter(value, alpha(cutoff, dt))
    }

    fun reset() { xf.reset(); dxf.reset(); prev = null; tPrev = null }
}

/** One filter per landmark per axis. Filter the INPUTS, never the derived angle — filtering the
 *  angle breaks geometric consistency. */
class LandmarkFilter(minCutoff: Float, beta: Float, dCutoff: Float, count: Int = 33) {
    private val fx = Array(count) { OneEuroFilter(minCutoff, beta, dCutoff) }
    private val fy = Array(count) { OneEuroFilter(minCutoff, beta, dCutoff) }
    private val fz = Array(count) { OneEuroFilter(minCutoff, beta, dCutoff) }

    fun apply(lms: List<Landmark>, tMs: Long): List<Landmark> =
        lms.mapIndexed { i, p ->
            Landmark(fx[i].filter(p.x, tMs), fy[i].filter(p.y, tMs), fz[i].filter(p.z, tMs), p.visibility)
        }

    fun reset() { fx.forEach { it.reset() }; fy.forEach { it.reset() }; fz.forEach { it.reset() } }
}
```

---

## 3. `perception/JointGeometry.kt`

Angles come from **world** landmarks — metric and hip-centred. Image-space angles drift with
camera tilt, and the phone is propped at a bad angle by design.

```kotlin
object LM {
    const val LEFT_SHOULDER = 11; const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13;    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15;    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23;      const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25;     const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27;    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29;     const val RIGHT_HEEL = 30
    const val LEFT_FOOT_INDEX = 31; const val RIGHT_FOOT_INDEX = 32
}

enum class Side { LEFT, RIGHT }

val JOINT: Map<String, Pair<Int, Int>> = mapOf(
    "SHOULDER" to (LM.LEFT_SHOULDER to LM.RIGHT_SHOULDER),
    "ELBOW"    to (LM.LEFT_ELBOW to LM.RIGHT_ELBOW),
    "WRIST"    to (LM.LEFT_WRIST to LM.RIGHT_WRIST),
    "HIP"      to (LM.LEFT_HIP to LM.RIGHT_HIP),
    "KNEE"     to (LM.LEFT_KNEE to LM.RIGHT_KNEE),
    "ANKLE"    to (LM.LEFT_ANKLE to LM.RIGHT_ANKLE),
    "HEEL"     to (LM.LEFT_HEEL to LM.RIGHT_HEEL),
    "FOOT_INDEX" to (LM.LEFT_FOOT_INDEX to LM.RIGHT_FOOT_INDEX),
)

fun idx(joint: String, side: Side): Int =
    JOINT.getValue(joint).let { if (side == Side.LEFT) it.first else it.second }

/** Angle at [b], in degrees. NaN when degenerate — the caller treats that as an invalid frame. */
fun angle3(a: Landmark, b: Landmark, c: Landmark): Float {
    val v1x = a.x - b.x; val v1y = a.y - b.y; val v1z = a.z - b.z
    val v2x = c.x - b.x; val v2y = c.y - b.y; val v2z = c.z - b.z
    val n1 = kotlin.math.sqrt(v1x * v1x + v1y * v1y + v1z * v1z)
    val n2 = kotlin.math.sqrt(v2x * v2x + v2y * v2y + v2z * v2z)
    if (n1 < 1e-9f || n2 < 1e-9f) return Float.NaN
    val cos = ((v1x * v2x + v1y * v2y + v1z * v2z) / (n1 * n2)).coerceIn(-1f, 1f)
    return Math.toDegrees(kotlin.math.acos(cos).toDouble()).toFloat()
}

data class SideSelection(val side: Side, val both: Boolean, val visibility: Float, val valid: Boolean)

/** Score from whichever side is better seen; average when both are usable. */
fun chooseSide(lms: List<Landmark>, joints: List<String>, threshold: Float): SideSelection {
    fun vis(s: Side) = joints.map { lms[idx(it, s)].visibility }.average().toFloat()
    val l = vis(Side.LEFT); val r = vis(Side.RIGHT)
    val best = if (l >= r) Side.LEFT else Side.RIGHT
    val bestVis = maxOf(l, r)
    return SideSelection(best, l >= threshold && r >= threshold, bestVis, bestVis >= threshold)
}

fun primaryAngle(
    lms: List<Landmark>, a: String, b: String, c: String,
    joints: List<String>, threshold: Float,
): Pair<Float, SideSelection> {
    val sel = chooseSide(lms, joints, threshold)
    if (!sel.valid) return Float.NaN to sel
    fun one(s: Side) = angle3(lms[idx(a, s)], lms[idx(b, s)], lms[idx(c, s)])
    val deg = if (sel.both) (one(Side.LEFT) + one(Side.RIGHT)) / 2f else one(sel.side)
    return deg to sel
}

/** Distance in metres between the outer two joints of the primary angle. Shortens as the rep
 *  deepens, so max-minus-min across a rep is depth travel in real units. */
fun spanMetres(lms: List<Landmark>, a: String, c: String, side: Side): Float {
    val p = lms[idx(a, side)]; val q = lms[idx(c, side)]
    return kotlin.math.sqrt(
        (p.x - q.x) * (p.x - q.x) + (p.y - q.y) * (p.y - q.y) + (p.z - q.z) * (p.z - q.z))
}
```

---

## 4. `perception/detector/RepCycleDetector.kt`

**The direction trick is the part to get right.** Calf raises and glute bridges *increase* their
primary angle toward the effort position; squats and push-ups decrease it. Normalising to signed
coordinates `u = s · θ` means every comparison is written once — and it is why half the exercise
library does not silently count nothing.

```kotlin
enum class RepState { TOP, DESCENDING, BOTTOM, ASCENDING }

data class RepDetectorConfig(
    val topEnter: Float, val topExit: Float,
    val bottomEnter: Float, val bottomExit: Float,
    val targetAngle: Float,
    val minDescendMs: Long = 200, val minBottomMs: Long = 120,
    val minRepMs: Long = 800, val maxRepMs: Long = 8000,
)

class RepStateMachine(private val d: RepDetectorConfig) {

    private val s: Float = if (d.topEnter > d.bottomEnter) 1f else -1f   // +1 decreasing, -1 increasing
    private val uTopEnter = s * d.topEnter
    private val uTopExit = s * d.topExit
    private val uBottomEnter = s * d.bottomEnter
    private val uBottomExit = s * d.bottomExit
    private val uTarget = s * d.targetAngle

    var state = RepState.TOP; private set
    private var repIndex = 0
    private var stateEnteredMs = 0L
    private var topRefU: Float? = null
    private var lastRepEndMs: Long? = null
    private var seenValid = false

    private var rep: Partial? = null

    private class Partial(
        var tStartMs: Long, var uMin: Float, var uMax: Float,
        var tBottomStart: Long? = null, var tBottomEnd: Long? = null,
        var uAtBottomExit: Float = 0f, var frames: Int = 0, var validFrames: Int = 0,
    )

    fun setTopRef(angleDeg: Float) { topRefU = s * angleDeg }
    fun reset() {
        state = RepState.TOP; repIndex = 0; rep = null
        lastRepEndMs = null; topRefU = null; seenValid = false
    }

    fun onFrame(angleDeg: Float, tMs: Long): RepEvent? {
        val valid = !angleDeg.isNaN()
        rep?.let { it.frames++; if (valid) it.validFrames++ }
        if (!valid) return null                          // invalid frames never move the machine

        val u = s * angleDeg
        if (!seenValid) { seenValid = true; stateEnteredMs = tMs }
        if (topRefU == null) topRefU = u

        rep?.let { if (u < it.uMin) it.uMin = u; if (u > it.uMax) it.uMax = u }
        val dwell = tMs - stateEnteredMs

        when (state) {
            RepState.TOP -> {
                if (u >= uTopEnter && u > topRefU!!) topRefU = u
                if (u < uTopExit) {
                    rep = Partial(tMs, u, u); enter(RepState.DESCENDING, tMs)
                }
            }
            RepState.DESCENDING -> {
                if (u >= uTopEnter) { rep = null; enter(RepState.TOP, tMs) }         // aborted
                else if (u <= uBottomEnter && dwell >= d.minDescendMs) {
                    rep?.tBottomStart = tMs; enter(RepState.BOTTOM, tMs)
                }
            }
            RepState.BOTTOM -> {
                if (u > uBottomExit && dwell >= d.minBottomMs) {
                    rep?.tBottomEnd = tMs; rep?.uAtBottomExit = u; enter(RepState.ASCENDING, tMs)
                }
            }
            RepState.ASCENDING -> {
                if (u <= uBottomEnter) { enter(RepState.BOTTOM, tMs) }
                else if (u >= uTopEnter) {
                    val out = complete(tMs, u)
                    enter(RepState.TOP, tMs); rep = null
                    return out
                }
            }
        }
        return null
    }

    private fun enter(next: RepState, tMs: Long) { state = next; stateEnteredMs = tMs }

    private fun complete(tMs: Long, u: Float): RepEvent? {
        val r = rep ?: return null
        val dur = tMs - r.tStartMs
        if (dur < d.minRepMs || dur > d.maxRepMs) return null
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
            repIndex = repIndex, exerciseId = "", tStartMs = r.tStartMs, tEndMs = tMs,
            thetaMin = s * (if (s > 0) r.uMin else r.uMax),
            thetaMax = s * (if (s > 0) r.uMax else r.uMin),
            uMin = r.uMin, uMax = r.uMax, uTopRef = topRefU!!, uTarget = uTarget,
            tEccSec = tEcc, tBottomSec = tBottom, tConSec = tCon,
            concentricVelocity = vel, gapSec = gap, validFrameRatio = ratio,
        )
    }
}
```

---

## 5. `perception/FormScorer.kt`

Four named geometric quantities, never a vague "form quality". Depth is **superlinear** — a
linear map scored a quarter-squat at 0.65, which is kinder than it deserves and left the damage
gap too small for a bystander to notice.

```kotlin
data class FormWeights(val depth: Float, val rom: Float, val tempo: Float, val alignment: Float)

object FormScorer {

    fun depth(e: RepEvent, exponent: Float = 1.5f): Float {
        val span = e.uTopRef - e.uTarget
        if (kotlin.math.abs(span) < 1e-6f) return 0f
        val linear = ((e.uTopRef - e.uMin) / span).coerceIn(0f, 1f)
        return Math.pow(linear.toDouble(), exponent.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    /** Against the player's OWN calibrated baseline. This is what makes scoring fair across body
     *  types — everyone is measured against themselves, not a population average. */
    fun rom(e: RepEvent, baselineU: Float): Float {
        if (baselineU < 1e-6f) return 1f
        return ((e.uMax - e.uMin) / baselineU).coerceIn(0f, 1f)
    }

    /** tEccSec is the MEASURED window (topExit -> bottomEnter), about a third of the full
     *  descent. eccentricTarget must be expressed in those terms — 0.80 silently demanded a
     *  2.4 second descent and capped tempo at ~0.6 for perfectly controlled reps. */
    fun tempo(e: RepEvent, eccentricTarget: Float = 0.35f, pauseTarget: Float = 0.12f): Float {
        val ecc = if (e.tEccSec >= eccentricTarget) 1f else (e.tEccSec / eccentricTarget).coerceIn(0f, 1f)
        val pause = if (e.tBottomSec >= pauseTarget) 1f else 0.6f
        return (0.7f * ecc + 0.3f * pause).coerceIn(0f, 1f)
    }

    fun kneeTracking(lms: List<Landmark>, side: Side, full: Float = 0.15f, zero: Float = 0.45f): Float {
        val knee = lms[idx("KNEE", side)]; val ankle = lms[idx("ANKLE", side)]
        val shin = kotlin.math.sqrt(
            (knee.x - ankle.x) * (knee.x - ankle.x) +
            (knee.y - ankle.y) * (knee.y - ankle.y) +
            (knee.z - ankle.z) * (knee.z - ankle.z))
        if (shin < 1e-6f) return 1f
        val off = kotlin.math.abs(knee.x - ankle.x) / shin
        return ((zero - off) / (zero - full)).coerceIn(0f, 1f)
    }

    fun torsoLine(lms: List<Landmark>, side: Side, full: Float = 172f, zero: Float = 150f): Float {
        val deg = angle3(lms[idx("SHOULDER", side)], lms[idx("HIP", side)], lms[idx("ANKLE", side)])
        if (deg.isNaN()) return 1f
        return ((deg - zero) / (full - zero)).coerceIn(0f, 1f)
    }

    fun score(e: RepEvent, w: FormWeights, romBaselineU: Float,
              alignment: Float, depthExponent: Float = 1.5f): FormScore {
        val d = depth(e, depthExponent)
        val r = rom(e, romBaselineU)
        val t = tempo(e)
        val sum = (w.depth + w.rom + w.tempo + w.alignment).let { if (it > 0f) it else 1f }
        val total = ((w.depth * d + w.rom * r + w.tempo * t + w.alignment * alignment) / sum)
            .coerceIn(0f, 1f)

        // The weakest sub-score names the fault. We do not ask the model to diagnose — we ask it
        // to phrase a diagnosis we already made, which is what keeps the coaching truthful.
        val parts = mutableListOf("depth" to d, "rom" to r, "tempo" to t)
        if (w.alignment > 0f) parts += "alignment" to alignment
        val reason = parts.minByOrNull { it.second }!!.first

        return FormScore(d, r, t, alignment, total, reason)
    }
}
```

---

## 6. `perception/FatigueEstimator.kt`

**One model, five families.** Fatigue is always decay of a family's primary output against the
player's own early-set baseline. Only the output changes.

```kotlin
data class Signal(val key: String, val decay: Boolean, val weight: Float, val norm: Float = 3f)

object Signals {
    val REP_CYCLE = listOf(
        Signal("velocity", decay = true,  weight = 0.45f),
        Signal("rom",      decay = true,  weight = 0.35f),
        Signal("gap",      decay = false, weight = 0.20f, norm = 3f),
    )
    // An isometric has no velocity. Fatigue shows as shake, which is what happens to a human.
    val ISOMETRIC_HOLD = listOf(
        Signal("tremor",  decay = false, weight = 0.60f, norm = 1f),
        Signal("quality", decay = true,  weight = 0.40f),
    )
    val CADENCE = listOf(
        Signal("cadence",   decay = true, weight = 0.55f),
        Signal("amplitude", decay = true, weight = 0.45f),
    )
    val BALLISTIC = listOf(
        Signal("height",   decay = true, weight = 0.60f),
        Signal("softness", decay = true, weight = 0.40f),
    )
    val POSE_MATCH = listOf(Signal("accuracy", decay = true, weight = 1.0f))

    fun forFamily(f: Family) = when (f) {
        Family.REP_CYCLE -> REP_CYCLE
        Family.ISOMETRIC_HOLD -> ISOMETRIC_HOLD
        Family.CADENCE -> CADENCE
        Family.BALLISTIC -> BALLISTIC
        Family.POSE_MATCH -> POSE_MATCH
    }
}

data class FatigueConfig(
    val baselineReps: Int = 3,
    val ema: Float = 0.4f,
    val working: Float = 0.15f,
    val fading: Float = 0.30f,
    val gassed: Float = 0.50f,
    val bandLatchReps: Int = 1,
)

class FatigueEstimator(
    private val cfg: FatigueConfig,
    private val signals: List<Signal>,
) {
    private val samples = mutableListOf<Map<String, Float>>()
    private var baseline: Map<String, Float>? = null
    private var value = 0f
    private var band = FatigueBand.FRESH
    private var pendingBand: FatigueBand? = null
    private var pendingCount = 0
    private var losses: Map<String, Float> = emptyMap()
    private var frozen = false

    /** A pause must never read as fatigue. */
    fun freeze() { frozen = true }
    fun unfreeze() { frozen = false }

    fun reset() {
        samples.clear(); baseline = null; value = 0f; band = FatigueBand.FRESH
        pendingBand = null; pendingCount = 0; losses = emptyMap(); frozen = false
    }

    fun onRep(e: RepEvent): FatigueState = onSignals(mapOf(
        "velocity" to e.concentricVelocity,
        "rom" to (e.uMax - e.uMin),
        "gap" to e.gapSec,
    ))

    fun onSignals(sample: Map<String, Float>): FatigueState {
        if (frozen) return state()
        samples += sample
        if (samples.size < cfg.baselineReps) return state()

        if (baseline == null) {
            val first = samples.take(cfg.baselineReps)
            baseline = signals.associate { s ->
                s.key to first.map { it[s.key] ?: 0f }.average().toFloat()
            }
        }
        val b = baseline!!

        var total = 0f; var wsum = 0f
        val out = mutableMapOf<String, Float>()
        for (s in signals) {
            val cur = sample[s.key] ?: 0f
            val base = b[s.key] ?: 0f
            val loss = if (s.decay) {
                if (base > 1e-6f) (1f - cur / base).coerceIn(0f, 1f) else 0f
            } else {
                ((cur - base) / s.norm).coerceIn(0f, 1f)
            }
            out[s.key] = loss
            total += s.weight * loss
            wsum += s.weight
        }
        val raw = (if (wsum > 0f) total / wsum else 0f).coerceIn(0f, 1f)

        value = if (samples.size == cfg.baselineReps) raw else cfg.ema * raw + (1 - cfg.ema) * value
        losses = out
        latch(bandFor(value))
        return state()
    }

    private fun bandFor(v: Float) = when {
        v >= cfg.gassed -> FatigueBand.GASSED
        v >= cfg.fading -> FatigueBand.FADING
        v >= cfg.working -> FatigueBand.WORKING
        else -> FatigueBand.FRESH
    }

    /** Latched in both directions so the meter cannot flicker on a boundary. */
    private fun latch(candidate: FatigueBand) {
        if (candidate == band) { pendingBand = null; pendingCount = 0; return }
        if (candidate == pendingBand) {
            pendingCount++
            if (pendingCount > cfg.bandLatchReps) {
                band = candidate; pendingBand = null; pendingCount = 0
            }
        } else { pendingBand = candidate; pendingCount = 1 }
    }

    fun state() = FatigueState(value, band, losses,
        minOf(samples.size, cfg.baselineReps), baseline != null)
}
```

> **Bands are 0.15 / 0.30 / 0.50, not 0.20 / 0.45 / 0.70.** The original numbers made `GASSED`
> require roughly a 72% concentric velocity loss — far past what a real set produces — so the
> mercy rule would never have fired. Verified by fixture F3.

---

## 7. `combat/CombatEngine.kt`

Pure and time-free by design — no internal timers, so it unit-tests in milliseconds.

```kotlin
data class ComboConfig(val step: Float = 0.12f, val cap: Float = 2.5f,
                       val threshold: Float = 0.75f, val graceAtStreak: Int = 6)

class ComboTracker(var cfg: ComboConfig) {
    var streak = 0; private set
    private var graceUsed = false

    fun onRep(formScore: Float): Float {
        when {
            formScore >= cfg.threshold -> { streak++; graceUsed = false }
            streak >= cfg.graceAtStreak && !graceUsed -> graceUsed = true   // one forgiven rep
            else -> { streak = 0; graceUsed = false }
        }
        return multiplier
    }

    val multiplier: Float
        get() = if (streak <= 0) 1f else minOf(1f + cfg.step * (streak - 1), cfg.cap)

    fun reset() { streak = 0; graceUsed = false }
}

data class BossPhase(val fromHpPct: Float, val modifier: Float, val label: String)
data class BossConfig(val id: String, val name: String, val maxHp: Int, val phases: List<BossPhase>)
data class FatigueResponse(val modifier: Float = 1f, val regenPerRep: Int = 0,
                           val staggerReps: Int = 0, val mercyRepsToFinish: Int = 0)

class CombatEngine(
    private val baseDamage: Int = 100,
    private val formFloor: Float = 0.35f,
    private val formExponent: Float = 1.2f,
    private var boss: BossConfig,
    private val responses: Map<FatigueBand, FatigueResponse>,
    val combo: ComboTracker,
    private val casual: Boolean = false,
) {
    var maxHp = boss.maxHp; private set
    var hp = maxHp; private set
    var reps = 0; private set
    var totalDamage = 0; private set
    var dead = false; private set
    var staggerRepsLeft = 0; private set
    var mercyActive = false; private set
    var mercyDisabled = false

    private val applied = HashSet<String>()          // duel dedupe: "player:seq"
    private val recent = ArrayDeque<Float>()         // combo-neutral, for the mercy estimate

    val hpPct: Float get() = if (maxHp > 0) hp.toFloat() / maxHp else 0f

    fun phaseModifier(): Pair<Float, String> {
        var mod = 1f; var label = "phase1"
        for (p in boss.phases) if (hpPct <= p.fromHpPct) { mod = p.modifier; label = p.label }
        return mod to label
    }

    fun damageFor(formScore: Float, band: FatigueBand): Int {
        val factor = formFloor + (1 - formFloor) *
            Math.pow(maxOf(0f, formScore).toDouble(), formExponent.toDouble()).toFloat()
        val fr = responses[band] ?: FatigueResponse()
        val fatigueMod = if (staggerRepsLeft > 0)
            (responses[FatigueBand.FADING]?.modifier ?: 1f) else fr.modifier
        return Math.round(baseDamage * factor * combo.multiplier * phaseModifier().first * fatigueMod)
    }

    fun onRep(formScore: Float, band: FatigueBand): Int {
        if (dead) return 0
        combo.onRep(formScore)
        val dmg = damageFor(formScore, band)
        apply(dmg)
        reps++
        responses[band]?.regenPerRep?.takeIf { it > 0 && !dead }?.let {
            hp = minOf(maxHp, hp + it)
        }
        if (staggerRepsLeft > 0) staggerRepsLeft--
        recent.addLast(dmg / maxOf(1f, combo.multiplier))     // combo-neutral
        if (recent.size > 5) recent.removeFirst()
        return dmg
    }

    /** Idempotent by (playerId, seq): order and duplicates are irrelevant by construction. */
    fun onRemoteDamage(playerId: String, seq: Int, damage: Int) {
        if (!applied.add("$playerId:$seq")) return
        apply(damage)
    }

    private fun apply(d: Int) {
        totalDamage += d
        hp = maxOf(0, hp - d)
        if (hp <= 0) dead = true
    }

    private fun recentMeanDamage(): Float =
        if (recent.isEmpty()) baseDamage * formFloor else recent.average().toFloat()

    /**
     * The boss cannot outlast you, but it makes you earn the ending. Also the safest possible
     * demo behaviour: a judge who tires mid-demo still reaches a victory screen.
     */
    fun onFatigueBand(band: FatigueBand, expectedDamagePerRep: Float = recentMeanDamage()) {
        if (band == FatigueBand.FADING && staggerRepsLeft == 0) {
            staggerRepsLeft = responses[FatigueBand.FADING]?.staggerReps ?: 5
        }
        if (band == FatigueBand.GASSED && !mercyActive && !mercyDisabled) {
            mercyActive = true
            val n = responses[FatigueBand.GASSED]?.mercyRepsToFinish ?: 4
            hp = minOf(hp, maxOf(1, Math.round(n * expectedDamagePerRep)))
            if (hp <= 0) dead = true
        }
    }

    fun reset(newBoss: BossConfig = boss) {
        boss = newBoss
        maxHp = if (casual) (newBoss.maxHp * 0.5f).toInt() else newBoss.maxHp
        hp = maxHp; reps = 0; totalDamage = 0; dead = false
        staggerRepsLeft = 0; mercyActive = false
        combo.reset(); applied.clear(); recent.clear()
    }
}
```

**Damage curve, asserted in the JS suite — safe to cite in the deck:**
`0.00 → 35 · 0.30 → 50 · 0.55 → 67 · 0.80 → 85 · 0.95 → 96 · 1.00 → 100`

---

## 8. `duel/DuelSession.kt`

Sync events, not state. Boss HP is a set reduction over the deduplicated union of all events,
so it is order-independent, duplicate-safe, and identical for two players or eight.

```kotlin
data class DuelMessage(
    val v: Int = 1,
    val type: String,                       // HELLO | WELCOME | PING | REP
    val playerId: String,
    val seq: Int = 0,
    val tMs: Long = 0,
    val damage: Int = 0,
    val formScore: Float = 0f,
    val exerciseId: String = "",
    val fatigueBand: String = "FRESH",
    val recent: List<Pair<Int, Int>> = emptyList(),   // (seq, damage), last 8
)

interface DuelTransport {
    fun send(msg: DuelMessage)
    fun onMessage(handler: (DuelMessage) -> Unit): () -> Unit
    fun close()
}

class DuelSession(
    private val transport: DuelTransport,
    private val playerId: String,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val onRemote: (String, Int, Int) -> Unit,
    private val onState: (String) -> Unit = {},
) {
    private companion object {
        const val TAIL = 8
        const val HEARTBEAT_MS = 1200L
        const val LOST_AFTER_MS = 4000L
    }

    private var seq = 0
    private val sent = ArrayDeque<Pair<Int, Int>>()
    private val applied = HashSet<String>()
    private val peers = HashMap<String, Long>()          // playerId -> lastSeenMs
    private var lastBeat = 0L
    private var state = "SEARCHING"
    private val unsub = transport.onMessage(::receive)

    init { transport.send(DuelMessage(type = "HELLO", playerId = playerId, tMs = now())) }

    fun sendRep(damage: Int, formScore: Float, exerciseId: String, band: FatigueBand) {
        seq++
        sent.addLast(seq to damage)
        if (sent.size > TAIL) sent.removeFirst()
        transport.send(DuelMessage(
            type = "REP", playerId = playerId, seq = seq, tMs = now(),
            damage = damage, formScore = formScore, exerciseId = exerciseId,
            fatigueBand = band.name, recent = sent.toList(),
        ))
    }

    fun tick(): String {
        val t = now()
        if (t - lastBeat > HEARTBEAT_MS) {
            lastBeat = t
            transport.send(DuelMessage(type = "PING", playerId = playerId, tMs = t))
        }
        val anyLive = peers.values.any { t - it < LOST_AFTER_MS }
        val next = when {
            peers.isEmpty() -> "SEARCHING"
            anyLive -> "LINKED"
            else -> "LOST"
        }
        if (next != state) { state = next; onState(next) }
        return state
    }

    private fun receive(m: DuelMessage) {
        if (m.playerId == playerId) return
        peers[m.playerId] = now()

        // HELLO must never be answered with another HELLO, or two peers announce at each other
        // until the stack blows. WELCOME carries the tail and is never answered.
        if (m.type == "HELLO") {
            transport.send(DuelMessage(type = "WELCOME", playerId = playerId,
                seq = seq, tMs = now(), recent = sent.toList()))
            return
        }
        if (m.type != "REP" && m.type != "WELCOME") return

        val events = m.recent.ifEmpty { listOf(m.seq to m.damage) }
        for ((s, d) in events) {
            if (applied.add("${m.playerId}:$s")) onRemote(m.playerId, s, d)
        }
    }

    fun close() { unsub(); transport.close() }
}
```

---

## 9. Port order at the event

Write these in this order — each one unblocks the next, and the first three are what make a demo
possible at all.

| # | File | Why here |
|---|---|---|
| 1 | `core/model` | Everything depends on it. Ten minutes. |
| 2 | `JointGeometry` + `OneEuroFilter` | Pure, testable, no Android. |
| 3 | `RepStateMachine` | **The demo does not exist without this.** Gate G1. |
| 4 | `FormScorer` | Turns reps into a score. Gate G2. |
| 5 | `CombatEngine` + `ComboTracker` | Turns a score into a hit. Gate G2. |
| 6 | `FatigueEstimator` | The novelty. Gate G4. |
| 7 | `DuelSession` | Tier 1, Sunday. Gate G5b. |
| 8 | Family detectors | Only if genuinely ahead. |

**Write the test first for 3, 4, 5 and 6.** They are pure functions with no Android dependency, the
fixtures are in [14-TEST-PLAN](../14-TEST-PLAN.md), and the JS suite already tells you exactly what
the answers should be.
