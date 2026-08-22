# 08 · Data Model

Everything is local. No backend, no account, no sync. Two stores: **Room** for session history,
**config files** for everything tunable.

---

## 1. Room schema

```kotlin
@Entity(tableName = "profile")
data class ProfileEntity(
  @PrimaryKey val id: Int = 1,          // single row
  val createdAtMs: Long,
  val squatTopRefDeg: Float?,           // calibrated standing angle
  val squatRomBaselineDeg: Float?,
  val pushupTopRefDeg: Float?,
  val pushupRomBaselineDeg: Float?,
  val preferredExercise: String,
  val arenaMode: Boolean
)

@Entity(tableName = "sessions")
data class SessionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val startedAtMs: Long,
  val endedAtMs: Long?,
  val bossId: String,
  val mode: String,                     // "solo" | "duel" | "casual"
  val outcome: String?,                 // "victory" | "abandoned"
  val totalDamage: Int,
  val totalReps: Int
)

@Entity(tableName = "sets")
data class SetEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val sessionId: Long,
  val setIndex: Int,
  val exercise: String,
  val startedAtMs: Long,
  val endedAtMs: Long,
  val reps: Int,
  val formMean: Float,
  val fatigueEnd: Float,
  val fatigueBandEnd: String,
  val coachLine: String?,               // what was actually said
  val bossLine: String?,
  val coachSource: String               // "llm" | "template" — for our own honesty
)

@Entity(tableName = "reps")
data class RepEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val setId: Long,
  val repIndex: Int,
  val tStartMs: Long, val tEndMs: Long,
  val thetaMin: Float, val thetaMax: Float,
  val depth: Float, val rom: Float, val tempo: Float, val alignment: Float,
  val formScore: Float,
  val concentricVelocity: Float,
  val damage: Int,
  val comboAtRep: Float,
  val validFrameRatio: Float
)
```

**Why store per-rep telemetry.** Three reasons, all of which pay off on the day:
1. The SUMMARY screen's fatigue curve and form sparkline read straight from it.
2. `TelemetrySummariser` builds the LLM payload from it.
3. **When the demo misbehaves, this is the only evidence we have.** Being able to open the summary
   screen and show a judge exactly what the engine measured turns a glitch into a credibility win.

Writes go to `Dispatchers.IO`, fire-and-forget. Never block the pose thread on a database write.

---

## 2. Config files — hot-reloadable

Location: `Android/data/<pkg>/files/config/`. Loaded at splash, re-read on every `onResume`.
See [ADR-005](adr/ADR-005-hot-reload-config.md).

```
config/
├── pose.json          filter params, visibility gate, framing thresholds
├── exercises/         one JSON record per exercise — see 19-EXERCISE-LIBRARY §6
│   ├── squat.json
│   ├── wall_sit.json
│   └── …              adding an exercise is a file, not a release
├── ghosts/            recorded rep timelines for Ghost Race pacers
├── combat.json        base damage, combo curve, boss HP, phase modifiers, fatigue responses
├── ui.json            flash durations, rest lengths, band labels
└── prompts/
    ├── system.txt
    ├── coach.txt
    └── boss.txt
```

### `pose.json` shape

```json
{
  "filter": { "minCutoff": 1.0, "beta": 0.007, "dCutoff": 1.0 },
  "visibilityThreshold": 0.60,
  "framingLostFrames": 45,
  "exercises": {
    "squat": {
      "topEnter": 158, "topExit": 150, "bottomEnter": 100, "bottomExit": 110,
      "targetAngle": 90,
      "minDescendMs": 200, "minBottomMs": 120,
      "minRepMs": 800, "maxRepMs": 8000,
      "weights": { "depth": 0.40, "rom": 0.25, "tempo": 0.20, "alignment": 0.15 }
    },
    "pushup": { "...": "..." }
  },
  "fatigue": {
    "baselineReps": 3,
    "weights": { "velocityLoss": 0.45, "romLoss": 0.35, "pauseGrowth": 0.20 },
    "ema": 0.4,
    "bands": { "working": 0.20, "fading": 0.45, "gassed": 0.70 }
  }
}
```

### `combat.json` shape

```json
{
  "baseDamage": 100,
  "formFloor": 0.35,
  "formExponent": 1.2,
  "combo": { "step": 0.12, "cap": 2.5, "threshold": 0.75, "graceAtStreak": 6 },
  "boss": {
    "id": "pacemaker", "maxHp": 3000,
    "phases": [
      { "from": 1.00, "modifier": 1.00 },
      { "from": 0.60, "modifier": 0.90 },
      { "from": 0.25, "modifier": 1.15 }
    ]
  },
  "fatigueResponse": {
    "FRESH":   { "modifier": 0.92, "regenPerRep": 8 },
    "WORKING": { "modifier": 1.00, "regenPerRep": 0 },
    "FADING":  { "modifier": 1.20, "staggerReps": 5 },
    "GASSED":  { "mercyRepsToFinish": 4 }
  },
  "casual": { "damageMultiplier": 1.6, "formFloor": 0.60, "bossHpMultiplier": 0.5 },
  "rest": { "freshSeconds": 30, "gassedSeconds": 75 }
}
```

**Every number in the game lives here.** Changing balance during a Red Light block is editing a text
file on the phone and backgrounding/foregrounding the app. No laptop, no Gradle, no rebuild.

---

## 3. Assets

`Android/data/<pkg>/files/assets/` — boss frames, HUD art, audio. Also hot-swappable, so art can be
replaced on-device without a rebuild.

`Android/data/<pkg>/files/models/gemma-3n-e2b-int4.task` — side-loaded over Office Kit.

`Android/data/<pkg>/files/traces/` — recorded landmark traces for offline replay testing (see
`14-TEST-PLAN.md`).

---

## 4. Export

One button on the SUMMARY screen: write the session (sets + reps) to a JSON file in
`files/export/`. Purpose is not user-facing — it is so we can pull real data off the phone during
the build and tune against it, and so we can hand a judge a file if they ask what we measured.

## 5. What we deliberately do not store

No video. No images. No landmark data beyond the derived per-rep scalars. No identifiers beyond a
local random `playerId` for duels. **If a judge asks what a compromised phone would leak, the answer
is: a list of knee angles.** That is the privacy claim, made concrete.
