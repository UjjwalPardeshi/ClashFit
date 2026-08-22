# Config Pack

Copy-ready starting values for `Android/data/<pkg>/files/config/`. **Data, not code.** Every number
here is a *starting* value to be tuned on-device during Red Light ([ADR-005](../adr/ADR-005-hot-reload-config.md)).

---

## `pose.json`

```json
{
  "version": 1,
  "filter": { "minCutoff": 1.0, "beta": 0.007, "dCutoff": 1.0 },
  "visibilityThreshold": 0.60,
  "framingLostFrames": 45,
  "framing": {
    "targetBoxHeightMin": 0.55,
    "targetBoxHeightMax": 0.92,
    "holdToStartMs": 2000,
    "reacquireAfterLossMs": 2000
  },
  "detector": {
    "minPoseDetectionConfidence": 0.60,
    "minPosePresenceConfidence": 0.60,
    "minTrackingConfidence": 0.60,
    "model": "pose_landmarker_full.task",
    "delegate": "GPU"
  },
  "fatigue": {
    "baselineReps": 3,
    "weights": { "velocityLoss": 0.45, "romLoss": 0.35, "pauseGrowth": 0.20 },
    "ema": 0.4,
    "pauseGrowthNormSec": 3.0,
    "bands": { "working": 0.15, "fading": 0.30, "gassed": 0.50 },
    "bandLatchReps": 1
  },
  "debugOverlay": false
}
```

> `debugOverlay` must be `false` in the config you demo with. It is on the pre-demo ritual
> ([14-TEST-PLAN](../14-TEST-PLAN.md) §6) because it is the single easiest thing to forget.

---

## `combat.json`

```json
{
  "version": 1,
  "baseDamage": 100,
  "formFloor": 0.35,
  "formExponent": 1.2,

  "combo": { "step": 0.12, "cap": 2.5, "threshold": 0.75, "graceAtStreak": 6 },

  "boss": {
    "id": "pacemaker",
    "name": "THE PACEMAKER",
    "maxHp": 3000,
    "phases": [
      { "fromHpPct": 1.00, "modifier": 1.00, "label": "phase1" },
      { "fromHpPct": 0.60, "modifier": 0.90, "label": "enrage" },
      { "fromHpPct": 0.25, "modifier": 1.15, "label": "desperation" }
    ]
  },

  "fatigueResponse": {
    "FRESH":   { "modifier": 0.92, "regenPerRep": 8 },
    "WORKING": { "modifier": 1.00, "regenPerRep": 0 },
    "FADING":  { "modifier": 1.20, "staggerReps": 5 },
    "GASSED":  { "mercyRepsToFinish": 4 }
  },

  "casual": { "damageMultiplier": 1.6, "formFloor": 0.60, "bossHpMultiplier": 0.5 },

  "rest": { "freshSeconds": 30, "gassedSeconds": 75 },

  "setEnd": { "noRepTimeoutSec": 12, "noFrameTimeoutSec": 30 },

  "modes": {
    "BOSS_FIGHT":  { "enabled": true },
    "TIME_ATTACK": { "enabled": true, "durationSec": 60, "bossHpUncapped": true },
    "GHOST_RACE":  { "enabled": true, "defaultGhost": "pacer_silver" },
    "SURVIVAL":    { "enabled": true, "hpPerWave": 900, "formThresholdStep": 0.03,
                     "mercyDisabled": true },
    "BOSS_RUSH":   { "enabled": true, "sequence": ["pacemaker", "pacemaker_red", "pacemaker_black"] },
    "DUEL":        { "enabled": true, "rules": "TIME_ATTACK" }
  }
}
```

**Balance targets** to tune against ([04-GAME-DESIGN](../04-GAME-DESIGN.md) §9):
a fresh unfit person kills the boss in **25–45 reps across 2–4 sets**; combo reaches ×2 inside a
realistic 9-rep set; a full fight completes in **under 4 minutes from app launch**.

---

## `ui.json`

```json
{
  "version": 1,
  "flash": { "durationMs": 120, "cleanColor": "#22D3A0", "shallowColor": "#F5A524" },
  "damageNumeral": { "riseMs": 700, "punchScale": 1.25 },
  "hpBar": { "animateMs": 250, "overshootPct": 8 },
  "screenShake": { "px": 6, "durationMs": 180 },
  "transitionMs": 200,
  "bossIdleLoopMs": 3000,

  "verdictBands": { "clean": 0.80, "ok": 0.55 },
  "fatigueLabels": { "FRESH": "FRESH", "WORKING": "WORKING",
                     "FADING": "FADING", "GASSED": "GASSED" },

  "haptics": { "repTick": true, "tempoMetronome": true, "framingLostPattern": [0, 60, 80, 60] },

  "tts": { "enabled": true, "locale": "en-IN", "fallbackLocale": "en-US",
           "coachPitch": 1.0, "bossPitch": 0.75, "bossRate": 0.9, "duckDb": 12 },

  "accessibility": { "reducedMotion": false, "verdictWordAlways": true },

  "camera": { "mode": "SOLO", "solo": "FRONT", "arena": "REAR_ULTRAWIDE" }
}
```

---

## Robustness rule

**Malformed JSON must never crash the app.** Parse defensively; on failure keep the last good
config and show a small non-blocking banner. Every file ships with a compiled-in default so a
missing file is not a failure.

Test this deliberately before the event — you *will* fat-finger a comma at 03:00 on a phone
keyboard, and the app going down at that moment is how a weekend ends.
