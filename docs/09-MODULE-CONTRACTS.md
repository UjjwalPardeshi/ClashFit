# 09 · Module Contracts

**These interfaces are written first, in the Saturday 11:00 Green block, and frozen by 13:00.**

Once frozen, Omkar and Ujjwal work in parallel against them and never read each other's internals.
A contract change after 13:00 requires both people to stop and agree — that is the cost, and it is
why we get them right now.

---

## 1. The seam

```
perception  ──emits──►  SharedFlow<RepEvent>
            ──emits──►  StateFlow<FatigueState>
            ──emits──►  StateFlow<FrameQuality>
                              │
                              ▼
                          combat  ──►  StateFlow<CombatState>  ──►  ui
                              │
                              └──►  coach (between sets only)
```

`perception` imports nothing from `combat`, `coach`, `duel` or `ui`. It is a sensor. This is what
lets us feed it a recorded trace instead of a camera during testing, and it is what lets two people
build at full speed from hour one.

---

## 2. Core models — `core/model`

```kotlin
enum class Exercise { SQUAT, PUSHUP, CHAIR_SQUAT, KNEE_PUSHUP }
enum class FatigueBand { FRESH, WORKING, FADING, GASSED }
enum class Verdict { CLEAN, OK, SHALLOW }

data class RepEvent(
  val repIndex: Int, val exercise: Exercise,
  val tStartMs: Long, val tEndMs: Long,
  val thetaMin: Float, val thetaMax: Float,
  val depth: Float, val rom: Float, val tempo: Float, val alignment: Float,
  val formScore: Float, val concentricVelocity: Float, val validFrameRatio: Float
) { val verdict: Verdict get() = when { formScore >= 0.80f -> Verdict.CLEAN
                                        formScore >= 0.55f -> Verdict.OK
                                        else -> Verdict.SHALLOW } }

data class FatigueState(
  val value: Float, val band: FatigueBand,
  val velocityLoss: Float, val romLoss: Float, val pauseGrowth: Float,
  val baselineReps: Int
) { companion object { val FRESH = FatigueState(0f, FatigueBand.FRESH, 0f, 0f, 0f, 0) } }

data class FrameQuality(
  val poseDetected: Boolean,
  val requiredJointsVisible: Boolean,
  val framing: Framing,              // OK | TOO_CLOSE | TOO_FAR | PARTIAL | NONE
  val missingJoints: List<String>,   // human-readable, drives the spoken cue
  val fps: Float
)
```

---

## 3. `perception` — owner: Omkar

```kotlin
interface PoseEngine {
  val repEvents: SharedFlow<RepEvent>
  val fatigue: StateFlow<FatigueState>
  val frameQuality: StateFlow<FrameQuality>
  val skeleton: StateFlow<List<PointF>>     // image-space, overlay only

  fun start(exercise: Exercise, camera: CameraFacing)
  fun stop()
  fun pause()          // holds baselines, stops emitting
  fun resume()

  /** Capture calibration for this exercise. Suspends until held for 2s or cancelled. */
  suspend fun calibrate(exercise: Exercise): CalibrationResult

  /** Test seam: replay a recorded landmark trace instead of the camera. */
  fun startFromTrace(trace: LandmarkTrace, exercise: Exercise)
}

data class CalibrationResult(val topRefDeg: Float, val romBaselineDeg: Float)
```

**Contract guarantees Omkar owes:**
- Exactly one `RepEvent` per completed rep, never on a partial
- `repIndex` monotonic from 1 within a set, reset by `start()`
- No emission while `paused` or during `FRAMING_LOST`
- `fatigue` reports `FRESH` until `baselineReps` valid reps exist
- Every field of `RepEvent` populated — no NaN, no sentinel values

---

## 3b. `ExerciseDetector` — the seam that makes 61 exercises possible

Five detector families, one interface. `PoseEngine` selects an implementation from the exercise's
`family` field and feeds it filtered landmarks. Everything downstream sees the same `RepEvent` and
`FatigueState` regardless of family.

```kotlin
interface ExerciseDetector {
  val family: Family                       // REP_CYCLE | ISOMETRIC_HOLD | POSE_MATCH |
                                           // CADENCE | BALLISTIC
  fun configure(spec: ExerciseSpec)        // parsed from config/exercises/<id>.json
  fun onFrame(landmarks: Landmarks, tMs: Long)
  val events: SharedFlow<RepEvent>         // holds/poses emit on completion or per second
  val fatigue: StateFlow<FatigueState>     // family-specific signal, identical contract
  fun reset()
}
```

**The contract that matters:** every family reports fatigue as *decay of its primary output against
the player's own early-set baseline* — velocity for reps, tremor for holds, accuracy drift for
poses, cadence for cardio, height for jumps. `combat`, `coach` and `ui` therefore need no knowledge
of which family is running. See [19-EXERCISE-LIBRARY](19-EXERCISE-LIBRARY.md) §2 and §4.

**Weekend scope:** `RepCycleDetector` ships. `IsometricHoldDetector` if genuinely ahead. The
interface ships regardless — it costs nothing and it is what makes the architecture claim true
rather than aspirational.

---

## 4. `combat` — owner: Ujjwal

```kotlin
interface CombatEngine {
  val state: StateFlow<CombatState>
  fun startFight(boss: BossConfig, mode: FightMode)
  fun onRep(event: RepEvent, fatigue: FatigueState)
  fun onRemoteDamage(playerId: String, seq: Int, damage: Int)
  fun endSet(): SetTelemetry
  fun reset()
}

data class CombatState(
  val bossId: String, val bossHp: Int, val bossMaxHp: Int, val phase: Int,
  val reps: Int, val comboStreak: Int, val comboMultiplier: Float,
  val lastDamage: Int?, val lastVerdict: Verdict?,
  val fatigueBand: FatigueBand, val staggered: Boolean, val mercyActive: Boolean
)
```

**Contract guarantees Ujjwal owes:**
- `bossHp` never negative, never above `bossMaxHp`
- Damage applied exactly once per `(playerId, seq)` in duel mode
- `onRep` is pure with respect to time — no timers inside, so it is unit-testable
- `endSet()` returns a complete `SetTelemetry` even for a zero-rep set

---

## 5. `coach` — owner: Ujjwal

```kotlin
interface CoachEngine {
  val status: StateFlow<CoachStatus>       // LOADING | READY | OFFLINE | THROTTLED
  suspend fun warmUp()
  /** Never throws, never exceeds [timeoutMs], always returns usable lines. */
  suspend fun speakFor(telemetry: SetTelemetry, timeoutMs: Long = 5_000): CoachOutput
}

data class CoachOutput(
  val coachLine: String,
  val bossLine: String,
  val source: Source                        // LLM | TEMPLATE
)
```

**Contract guarantee:** `speakFor` **always** returns within `timeoutMs` with non-empty lines. If
the model is missing, slow, thermally throttled, or its output fails validation, the template bank
fires. The caller never handles a failure case, and the player never sees a loading state.

---

## 6. `duel` — owner: Omkar (Sunday)

```kotlin
interface DuelTransport {
  val state: StateFlow<LinkState>
  val incoming: SharedFlow<DuelMessage>
  suspend fun host(): Result<Unit>
  suspend fun join(): Result<Unit>
  fun send(msg: DuelMessage)
  fun close()
}
```

Wire format and reconciliation in `07-MULTIPLAYER-SPEC.md`.

---

## 7. `core/config` — owner: written together, first hour

```kotlin
interface ConfigStore {
  val pose: StateFlow<PoseConfig>
  val combat: StateFlow<CombatConfig>
  val ui: StateFlow<UiConfig>
  val prompts: StateFlow<Prompts>
  fun reload()          // called on every onResume
  val version: Int      // bumps on reload, so screens can react
}
```

**Both lanes depend on this, so it is written before either lane starts.** Every tunable number in
either module reads from here — no compiled constants in `perception` or `combat`.

---

## 8. Stubs, so neither lane blocks

Both are written in the first Green block, before the real implementations:

- **`FakePoseEngine`** — emits synthetic `RepEvent`s on a timer with configurable form scores and a
  scripted fatigue ramp. Lets Ujjwal build and tune the entire combat and UI stack before the camera
  pipeline exists.
- **`TemplateOnlyCoachEngine`** — returns template lines immediately. Lets the whole rest-screen flow
  be built and demoed before Gemma is integrated, and doubles as the production fallback.

**This is the single most important scheduling decision in the build.** With these two stubs, Ujjwal
is never blocked on Omkar, and the app is demoable end-to-end from roughly hour three.

---

## 9. Change protocol

Before 13:00 Saturday: change freely, say it out loud.
After 13:00: both people stop, agree, both update, and the change is written into this file.
After 19:00 Saturday: contract changes are forbidden. Add an adapter instead.
