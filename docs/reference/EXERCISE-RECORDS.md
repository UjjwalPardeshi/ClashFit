# Exercise Records

The 8 `REP_CYCLE` exercises for the weekend build, as `config/exercises/*.json`. Schema is
[19-EXERCISE-LIBRARY](../19-EXERCISE-LIBRARY.md) §6.

**Starting values.** Tune on-device during Red Light against a real body.

---

## Two full exemplars

### `squat.json`

```json
{
  "id": "squat",
  "family": "REP_CYCLE",
  "name": "Squat",
  "tags": ["strength", "lower", "no-equipment"],
  "framing": "side",
  "difficulty": 1,
  "games": ["BOSS_FIGHT", "TIME_ATTACK", "GHOST_RACE", "SURVIVAL", "BOSS_RUSH", "DUEL"],

  "detector": {
    "primaryAngle": { "a": "HIP", "b": "KNEE", "c": "ANKLE" },
    "topEnter": 158, "topExit": 150,
    "bottomEnter": 100, "bottomExit": 110,
    "targetAngle": 90,
    "minDescendMs": 200, "minBottomMs": 120,
    "minRepMs": 800, "maxRepMs": 8000,
    "requiredJoints": ["HIP", "KNEE", "ANKLE"]
  },

  "form": {
    "weights": { "depth": 0.40, "rom": 0.25, "tempo": 0.20, "alignment": 0.15 },
    "tempo": { "eccentricTargetSec": 0.80, "bottomPauseSec": 0.12 },
    "alignment": { "type": "KNEE_TRACKING", "fullMarksOffset": 0.15, "zeroMarksOffset": 0.45 }
  },

  "fatigue": { "signal": "VELOCITY_LOSS", "baselineReps": 3 },

  "cues": {
    "enter": "Stand side-on, feet shoulder width.",
    "tooHigh": "Go lower — you're stopping short of your first reps.",
    "tooFast": "Control the way down.",
    "framing": "I can't see your knees — step back."
  }
}
```

### `wall_sit.json` — the Tier-3 isometric exemplar

```json
{
  "id": "wall_sit",
  "family": "ISOMETRIC_HOLD",
  "name": "Wall Sit",
  "tags": ["strength", "lower", "accessible", "no-equipment"],
  "framing": "side",
  "difficulty": 2,
  "games": ["SIEGE", "CIRCUIT", "DUEL"],

  "detector": {
    "targets": [
      { "angle": ["HIP", "KNEE", "ANKLE"],    "value": 90, "tolerance": 12, "weight": 1.0 },
      { "angle": ["SHOULDER", "HIP", "KNEE"], "value": 90, "tolerance": 15, "weight": 0.6 }
    ],
    "breakToleranceMs": 500,
    "targetDurationSec": 45
  },

  "fatigue": { "signal": "TREMOR", "baselineSec": 3 },

  "cues": {
    "enter": "Slide down until your knees are at ninety.",
    "break": "Your hips are rising — slide back down."
  }
}
```

---

## All 8 weekend exercises — parameter table

Every row is the same schema as `squat.json`; only these values differ.

**Corrected 22 Aug.** An earlier draft set `bottomEnter` too close to `targetAngle`, so every rep
that counted already scored ~0.9 on depth and the sub-score discriminated nothing. The rule:
**`bottomEnter` is the generous "this counts", `targetAngle` is the strict "full marks".**

| id | Primary angle | topEnter | topExit | botEnter | botExit | target | Alignment | Framing |
|---|---|---|---|---|---|---|---|---|
| `squat` | HIP–KNEE–ANKLE | 158 | 150 | 120 | 130 | 90 | KNEE_TRACKING | side |
| `chair_squat` | HIP–KNEE–ANKLE | 150 | 143 | 130 | 138 | 110 | KNEE_TRACKING | side |
| `lunge` | HIP–KNEE–ANKLE | 160 | 152 | 120 | 130 | 90 | KNEE_TRACKING | side |
| `calf_raise` | KNEE–ANKLE–FOOT_INDEX | 100 | 105 | 115 | 110 | 135 | none | side |
| `glute_bridge` | SHOULDER–HIP–KNEE | 120 | 128 | 150 | 143 | 170 | none | side |
| `push_up` | SHOULDER–ELBOW–WRIST | 155 | 148 | 120 | 130 | 90 | TORSO_LINE | side |
| `knee_push_up` | SHOULDER–ELBOW–WRIST | 155 | 148 | 125 | 135 | 95 | TORSO_LINE | side |
| `sit_up` | SHOULDER–HIP–KNEE | 150 | 143 | 110 | 120 | 70 | none | side |

All eight also carry `form.depthExponent: 1.5` — depth is superlinear, see
[05-POSE-ENGINE-SPEC](../05-POSE-ENGINE-SPEC.md) §5.1. **Live, tested values are in
`prototype/config/exercises/`** — treat those as the source of truth and this table as the summary.

**Note the inverted pairs.** `calf_raise` and `glute_bridge` *increase* their angle toward the top of
the rep, so `topEnter < topExit` and `bottomEnter > bottomExit`. The FSM must read direction from the
config rather than assuming a decreasing angle — get this wrong and half your library silently
counts nothing.

### Alignment sub-scores

| Type | Measure | Full marks | Zero marks |
|---|---|---|---|
| `KNEE_TRACKING` | horizontal knee-to-ankle offset ÷ shin length | ≤ 0.15 | ≥ 0.45 |
| `TORSO_LINE` | angle(shoulder, hip, ankle) | ≥ 172° | ≤ 150° |
| `none` | — | weight redistributed across depth/rom/tempo | — |

### Ladders

```
PUSH    wall_push_up → incline_push_up → knee_push_up → push_up
SQUAT   chair_squat  → squat           → lunge
CORE    sit_up       → leg_raise       → hollow_hold
```

Promotion at rolling form ≥ 0.85 across three sessions; a lower rung is quietly offered below 0.5.
**Never phrased as failure** ([19-EXERCISE-LIBRARY](../19-EXERCISE-LIBRARY.md) §7).

---

## Demo note

**`squat` and `chair_squat` carry both judging rounds.** Eval R2 is judged at tables — no floor
space. `push_up` is Tier 2 and exists for the stage pitch only. Do not spend Saturday tuning
push-up thresholds you cannot demo.
