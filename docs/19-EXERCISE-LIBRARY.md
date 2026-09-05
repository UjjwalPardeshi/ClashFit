# 19 · Exercise Library & Family Games

**The ask:** strength, yoga, cardio — everything — with different games for different exercises.
This document specifies the whole library. Weekend build tiering is §8, kept separate and clearly
labelled, so scope decisions stay yours.

**The architectural insight that makes this possible:** you do not build 60 exercises. You build
**5 detectors** and then every exercise is a JSON entry. Adding an exercise becomes editing a
config file on the phone — which means it is *Red Light work*, not laptop work.

---

## 1. Five movement families

Exercises differ in how they are *detected*, not in what they are called. Group by detection
mechanic and the library collapses from 60 problems to 5.

| # | Family | Detection mechanic | Primary measure | Example |
|---|---|---|---|---|
| **F1** | `REP_CYCLE` | Hysteretic angle FSM | reps + depth | Squat, push-up, sit-up |
| **F2** | `ISOMETRIC_HOLD` | Angle-in-range timer | time under tension | Plank, wall sit |
| **F3** | `POSE_MATCH` | Whole-skeleton template match | angular accuracy | Yoga asanas |
| **F4** | `CADENCE` | Periodic signal detection | cadence + amplitude | Jumping jacks, high knees |
| **F5** | `BALLISTIC` | Airborne phase + peak displacement | jump height + landing softness | Burpee, jump squat |

---

## 2. One fatigue framework, five families

**This is the strongest technical point in the whole project, and it only appears once you have
more than one family.**

Fatigue is always the same thing: **decay of the family's primary output, measured against the
player's own early-set baseline.** What changes is the output.

| Family | Fatigue signal | Why it's the right one |
|---|---|---|
| F1 `REP_CYCLE` | concentric **velocity loss** + ROM collapse + pause growth | Velocity-based training uses exactly this |
| F2 `ISOMETRIC_HOLD` | **tremor** — rising landmark positional variance — plus angular drift | An isometric has no velocity. Fatigue shows as shake. |
| F3 `POSE_MATCH` | **accuracy drift** across the hold | The pose degrades before it collapses |
| F4 `CADENCE` | **cadence decay + amplitude decay** | Direct analog of velocity + ROM |
| F5 `BALLISTIC` | **peak height decay** + landing stiffness increase | Explosive output falls first, and landings get sloppier — which is where injuries happen |

> **The pitch line:** *"One fatigue model, five movement families. Whatever you're doing, we measure
> how your output decays against your own first three reps — and the game responds."*

Every family therefore feeds the same `FatigueState` contract, the same bands, and the same
adaptive-boss behaviour. Nothing downstream changes.

---

## 3. A game shaped like the movement

Different exercise families get different games, because a game whose shape matches the movement's
shape feels designed rather than reskinned.

| Family | Game | Mechanic |
|---|---|---|
| **F1** Strength reps | **BOSS FIGHT** | Discrete reps → discrete damage. Plus Time Attack, Ghost Race, Survival, Boss Rush. |
| **F2** Isometric holds | **SIEGE** | The boss attacks continuously. Your hold *is* the shield. Shield strength = hold quality; break form and the shield drops and you take damage. Win by surviving. |
| **F3** Yoga | **SIGIL** | No combat, deliberately. Each asana fills a segment of a constellation; accuracy determines how brightly it lights. Calm audio, no boss, no damage. |
| **F4** Cardio | **PURSUIT** | Something chases you. Distance gained = integral of cadence. Cadence drops below threshold and it closes. Sustain and you escape. |
| **F5** Ballistic | **BREAKER** | Each jump is an impact. Height = force, and you smash down through floors of a tower. Landing softness scores — a stiff landing costs you. |

**Why Sigil has no boss.** Yoga framed as combat is tonally wrong and would read as a reskin. A
separate, calm mode is the honest design, and having one non-combat mode makes the product look
like a product rather than a single mechanic wearing hats.

**Cross-family modes:** **CIRCUIT** (a sequence mixing families — 10 squats, 30s plank, 30s high
knees, one asana), **DUEL** (any family, head to head), **DAILY** (one prescribed sequence per day).

---

## 4. Detector specifications

### F1 · `REP_CYCLE` — specified in full in [05-POSE-ENGINE-SPEC](05-POSE-ENGINE-SPEC.md)

Hysteretic four-state FSM on one primary joint angle, with dwell guards. Form = depth + ROM +
tempo + alignment. This is the shipping detector.

### F2 · `ISOMETRIC_HOLD`

```
target      = vector of joint angles + per-angle tolerance
inRange(t)  = every target angle within its tolerance
holdTime    = Σ dt where inRange
quality(t)  = 1 − mean( |θᵢ(t) − targetᵢ| / toleranceᵢ )
break       = out of range continuously for > 0.5 s
score       = holdTime × mean(quality)
```

**Fatigue = tremor.** Compute a rolling variance of the filtered landmark positions over a 1-second
window. Variance in the first 3 seconds is the baseline; rising variance is fatigue. Elegant,
cheap, and it is what actually happens to a human holding a plank.

**Siege mapping:** `shieldHp = holdTime × quality`. A break drops the shield and the boss lands a
hit. Damage dealt to the boss accrues per second held.

### F3 · `POSE_MATCH`

```
reference   = ~12 joint angles (both elbows, shoulders, hips, knees, torso lean, spine proxy)
accuracy    = 1 − Σ wᵢ·|θᵢ − refᵢ| / Σ wᵢ·tolᵢ
enter pose  = accuracy > 0.70 held for 1.5 s
completion  = accuracy sustained for the asana's target duration
```

- **Mirror-tolerant**: accept left/right symmetric matches so a tree pose on either leg counts.
- **The cue is the worst joint**: surface the single largest angular deviation as the spoken hint —
  *"straighten your left arm"* — rather than a generic accuracy percentage. This is the difference
  between a yoga feature and a yoga toy.
- **Fatigue = accuracy drift** across the hold.

### F3 Yoga rules checked against fitmon

Reference poses were sourced from fitmon's JavaScript detector, which uses simple 2D pixel rules.
This section validates that a real person holding each pose within fitmon's acceptance criteria
will score ≥ 0.70 in ClashFit's 3D angle matching.

**Methodology:** Translate fitmon's 2D pixel thresholds to 3D joint angle expectations, then calculate
ClashFit accuracy. Adjust tolerances conservatively where fitmon-compliant poses would be rejected.

| Pose | fitmon rule | Fitmon scenario → ClashFit accuracy | Decision | Adjustment |
|---|---|---|---|---|
| **Vrikshasana (Tree)** | One ankle ≥ 80 px higher; wrists < 100 px apart | Standing leg at 176°, arms at 165° → **accuracy 1.0** | Accept | None |
| **Utkatasana (Chair)** | Both knees < 100° | Knees at 95° → **accuracy 0.615** ✗ | Adjust | HIP-KNEE-ANKLE: `120 ± 14` → `110 ± 20` |
| **Virabhadrasana I (Warrior I)** | One knee < 120°, other > 150° | Front knee at 115° → **accuracy 0.630** ✗ | Adjust | HIP-KNEE-ANKLE: `100 ± 14` → `100 ± 20` |
| **Virabhadrasana II (Warrior II)** | One knee < 120°, other > 150° | Front knee at 110° → **accuracy 0.643** ✗ | Adjust | HIP-KNEE-ANKLE: `95 ± 14` → `95 ± 20` |
| **Tadasana (Mountain)** | Wrists below shoulders; shoulders & hips level ±40 px | Perfect posture at 178° → **accuracy 1.0** | Accept | None |

**Adjustments applied:** All three tolerance widening changes result in fitmon-compliant poses
scoring ≥ 0.70. Vrikshasana and Tadasana require no changes. The changes are minimal and conservative,
preserving the distinction between poses while accommodating the full range of human variation within
each pose's fitmon-defined acceptance window.

### F4 · `CADENCE`

```
signal      = one landmark axis per exercise (jacks → wrist Y; high knees → knee Y;
              mountain climbers → knee X)
detrend     → peak detection with min-prominence + refractory period
cadence     = 60 / median(inter-peak interval)     [reps per minute]
amplitude   = peak-to-trough, normalised to the calibration rep
```

`amplitude` is the form analog: it is what catches someone doing tiny fake high-knees. Score =
cadence held in the target band × amplitude quality.

**Fatigue = cadence decay + amplitude decay** against the first 10 seconds.

**Pursuit mapping:** `distance += cadence × amplitude × dt`. Pursuer speed is constant. Drop below
the threshold and the gap closes visibly.

### F5 · `BALLISTIC`

```
grounded    = both ankles within ε of their standing baseline Y
airborne    = not grounded, for ≥ 2 consecutive frames
jumpHeight  = peak hip Y displacement, in metres (world landmarks give this directly)
landing     = knee flexion within 300 ms of regaining ground
softness    = clamp((θ_stand − θ_landMin) / 35°, 0, 1)   — stiff landing scores low
```

**Landing softness is a genuine injury-prevention measure**, not a game gimmick, and it is a strong
line in the pitch. World landmarks being metric is what makes jump height a real number in
centimetres rather than a pixel count.

**Fatigue = peak height decay + softness decline.**

---

## 5. The catalogue

Every entry is a config record, not code. `framing` = where the phone must be.

### F1 · `REP_CYCLE` — 27 exercises

| Exercise | Primary angle | Framing | Tags |
|---|---|---|---|
| Squat | hip–knee–ankle | side | strength, lower |
| Chair squat | hip–knee–ankle | side | accessible, lower |
| Sumo squat | hip–knee–ankle | front | strength, lower |
| Split squat | hip–knee–ankle | side | strength, lower, balance |
| Forward lunge | hip–knee–ankle | side | strength, lower |
| Reverse lunge | hip–knee–ankle | side | strength, lower |
| Calf raise | knee–ankle–toe | side | strength, lower |
| Glute bridge | shoulder–hip–knee | side | strength, posterior |
| Good morning | shoulder–hip–knee | side | strength, posterior |
| Push-up | shoulder–elbow–wrist | side | strength, upper |
| Knee push-up | shoulder–elbow–wrist | side | accessible, upper |
| Wall push-up | shoulder–elbow–wrist | side | accessible, upper |
| Incline push-up | shoulder–elbow–wrist | side | strength, upper |
| Pike push-up | shoulder–elbow–wrist | side | strength, shoulders |
| Chair dip | shoulder–elbow–wrist | side | strength, triceps |
| Sit-up | shoulder–hip–knee | side | strength, core |
| Crunch | shoulder–hip–knee | side | strength, core |
| Leg raise | shoulder–hip–knee | side | strength, core |
| Dead bug | shoulder–hip–knee | side | core, control |
| Bird dog | shoulder–hip–knee | side | core, control |
| Superman | shoulder–hip–knee | side | posterior, control |
| Bicep curl | shoulder–elbow–wrist | front | strength, upper, arms |
| Lateral raise | hip–shoulder–elbow | front | strength, upper, shoulders |
| Front raise | hip–shoulder–elbow | front | strength, upper, shoulders |
| Shoulder press | shoulder–elbow–wrist | front | strength, upper, shoulders |
| Overhead triceps extension | shoulder–elbow–wrist | front | strength, upper, arms |
| Floor press | shoulder–elbow–wrist | side | strength, upper |

### F2 · `ISOMETRIC_HOLD` — 9 holds

Plank · Forearm plank · Side plank (L) · Side plank (R) · Wall sit · Hollow hold · Squat hold ·
Glute bridge hold · Superman hold

### F3 · `POSE_MATCH` — 14 asanas

**Standing:** Tadasana (Mountain) · Vrikshasana (Tree) · Virabhadrasana I (Warrior I) ·
Virabhadrasana II (Warrior II) · Trikonasana (Triangle) · Utkatasana (Chair) · Garudasana (Eagle) ·
Natarajasana (Dancer)
**Floor:** Adho Mukha Svanasana (Downward Dog) · Bhujangasana (Cobra) · Balasana (Child's) ·
Setu Bandha (Bridge) · Ustrasana (Camel) · Marjaryasana (Cat–Cow, treated as a slow F1 rep cycle)

> Use the Sanskrit names with English in parentheses. It is correct, and for an Indian jury it reads
> as respect rather than decoration.

### F4 · `CADENCE` — 10 movements

Jumping jacks · Seal jacks · High knees · Butt kicks · Mountain climbers · Running in place ·
Skater hops · Skipping (no rope) · Shadow boxing (jabs) · Standing torso twists

### F5 · `BALLISTIC` — 7 movements

Burpee · Squat thrust · Jump squat · Tuck jump · Star jump · Lateral bound · Broad jump

**Total: 67 exercises across 5 detectors.**

> **How reps are counted (5 Sep 2026).** Two counters, chosen per exercise by config. A record with
> `"counter": "STAGE"` uses the rule ported from fitmon: a stage flips to *rest* when the joint
> passes one threshold and a rep counts the moment it passes the other, debounced, on unfiltered
> landmarks so it registers the instant the angle is reached. Everything else uses the hysteresis
> state machine over the One Euro filtered angle. The four core exercises are stage-counted and lead
> every list:
>
> | Exercise | Keypoints | Rest | Counts at | Sides | Debounce |
> |---|---|---|---|---|---|
> | Lateral raise | 23-11-13 / 24-12-14 | both arms < 20° | both arms 95-105°, out to the side | both | 1000 ms |
> | Bicep curl | 11-13-15 / 12-14-16 | elbow > 135° | elbow < 80° | either arm | 800 ms |
> | Shoulder press | 11-13-15 / 12-14-16 | elbow < 95° | elbow > 134° overhead | better side | 1000 ms |
> | Squat | 23-25-27 / 24-26-28 | both knees < 72° | both knees > 150° | both | 1000 ms |
>
> Lunge, front raise, overhead triceps extension and floor press are stage-counted too.
>
> The lateral raise and the front raise also carry a `plane` condition, because the
> hip-shoulder-elbow angle is the same whether the arm goes out to the side or straight out in
> front and without it each was counting the other. It asks the upper arm to lie within (or
> beyond) sixty degrees of straight out to the side. See CONTEXT.md section 5.
>
> Six of those eight numbers — every one but the squat's — were retuned on 6 Sep 2026 against
> `traces/reference-*.jsonl`, the model's own reading of the four picker animations. As shipped,
> only the squat counted a textbook rep. The world-landmark depth axis compresses limbs pointed at the camera, so a pressed-out elbow
> reads about 146° and never the 160° the diagram promises. `ReferenceFormTest` now replays those
> traces and fails the build if a threshold drifts back out of reach.
>
> Two rules go beyond fitmon, because fitmon miscounts without them: an overhead exercise needs
> the wrist above the shoulder to be at rest, and dropping the arm clears the stage, so curling at your sides after
> an overhead set cannot count. Every other exercise follows
> `ClashFit_Exercise_Detection_Source_of_Truth.md`.

> **Shipped, as of 5 Sep 2026: 57 of these 67.** Six additional upper-body strength exercises
> were added to the catalogue: Bicep Curl, Lateral Raise, Front Raise, Shoulder Press, Overhead
> Triceps Extension, and Floor Press. The remaining ten exercises need reference angles captured
> from a performer who can hold the pose properly, and shipping an approximate one is worse than
> shipping none. The app's About screen reads its count from the config at runtime, so it always
> states the honest number rather than this one.

---

## 6. Exercise as data

An exercise is a JSON record in `config/exercises/`. Adding one requires no code.

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
      { "angle": ["HIP","KNEE","ANKLE"], "value": 90, "tolerance": 12, "weight": 1.0 },
      { "angle": ["SHOULDER","HIP","KNEE"], "value": 90, "tolerance": 15, "weight": 0.6 }
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

**Consequences of this design:**
- New exercises are Red Light work — edit a file on the phone, background/foreground the app
- The library grows without a rebuild
- A detector bug is fixed once for 21 exercises, not 21 times
- Test fixtures attach per exercise, not per code path

### F1 · `REP_CYCLE` detector schema extensions

Two new optional fields extend the rep-cycle detector for exercises that need them:

#### `sides` — for alternating or bilateral movements

```json
"sides": "EACH",
"mergeWindowMs": 500
```

- **`EACH`**: Count reps independently per side. Used for alternating movements like bicep curls,
  where each arm is credited separately. The `mergeWindowMs` field specifies the window in which
  reps on both sides are temporally grouped (default 500 ms).

#### `gate` — form-gating constraints

```json
"gate": {
  "angle": ["HIP", "SHOULDER", "ELBOW"],
  "min": 120,
  "max": 180,
  "at": "END"
}
```

A gate enforces that a specific angle is within a valid range at a critical point in the movement:
- **`angle`**: Three joints forming the angle to monitor.
- **`min`, `max`**: Valid range in degrees.
- **`at`**: When the gate is enforced:
  - **`"END"`**: Only at the top of the movement (topExit crossing); used for pressing movements where overhead lockout matters
  - **`"ALWAYS"`**: Throughout the movement; used for holds where posture must be constant (e.g., keeping elbows high during overhead triceps)

When a gate is violated, the rep is rejected and a corrective cue is provided.

#### `alignment` — new type for upper-body movements

```json
"alignment": {
  "type": "ELBOW_EXTENSION",
  "fullMarksDeg": 150,
  "zeroMarksDeg": 100
}
```

- **`ELBOW_EXTENSION`**: Scores the straightness of the arms. Used for lateral and front raises where
  proper form requires extended elbows (not bent). The scores linearly interpolate between
  `fullMarksDeg` (perfect, fully extended) and `zeroMarksDeg` (poor, too bent).

#### Compatibility note

The JavaScript prototype (web version in `src/repFsm.js`) does not explicitly interpret the
`sides` and `gate` fields — it only reads the core threshold and duration fields from the
detector. This means the prototype tolerates these new fields without modification; they are
simply ignored by the rep state machine, which the engine builder implements separately. This
allows the same config files to work across both implementations immediately.

---

## 7. Progression and difficulty ladders

Ladders are the retention mechanic that a large library unlocks, and they are the honest answer to
inclusion: an accessible variant is a **rung**, never an "easy mode".

```
PUSH   wall push-up → incline push-up → knee push-up → push-up → pike push-up
SQUAT  chair squat  → squat          → split squat  → jump squat
CORE   dead bug     → crunch         → sit-up       → leg raise → hollow hold
CARDIO march in place → jumping jacks → high knees   → burpee
YOGA   Tadasana     → Utkatasana     → Vrikshasana  → Natarajasana
```

The system promotes you when your rolling form score at a rung exceeds 0.85 across three sessions,
and quietly offers a lower rung when it drops below 0.5 — **without ever saying you failed.**

---

## 8. Weekend reality — read this before planning the build

Everything above is the product. Here is what 19 hours actually holds.

| | Weekend | Rationale |
|---|---|---|
| **Detectors shipped** | **F1 only.** F2 if genuinely ahead. | F1 is specced, tested and understood. F2 is the cheapest second family (~1.5h) and unlocks Siege. |
| **Exercises shipped** | **6–8 F1 exercises via config** — squat, chair squat, push-up, knee push-up, sit-up, glute bridge, lunge, calf raise | Once the FSM is generic, each is thresholds plus a fixture. Cheap. |
| **Games shipped** | Boss Fight, Time Attack, Ghost Race, Survival, Boss Rush | All rep-based, all confirmed. Siege only if F2 lands. |
| **Demoed** | **Squats. Time Attack, 60 seconds.** | Eval R2 is at tables. No floor space. |
| **Deck** | The full 5-family, 61-exercise architecture as the product vision | This is where the library earns its 30% |

**One firm recommendation.** Do not ship a library screen listing 61 exercises where 53 do not
work. A judge who taps a dead entry is worse off than one who never saw it. Ship the 6–8 that work,
and put the full taxonomy on a slide — the architecture is the impressive part, and a slide
communicates it better than a broken menu.

**The line for the deck:** *"Five detectors, sixty-one exercises, one fatigue model. Adding an
exercise is a config file, not a release."*

---

## Angle Check Page

**Developer tool for threshold calibration:** open `tools/angles.html` on a laptop with a webcam to
see live 3D and 2D joint angles as the Android app computes them. Select an exercise from the dropdown,
perform reps in front of the camera, and watch the state machine track them in real time. A
horizontal gauge shows the current angle against topEnter/topExit/bottomEnter/bottomExit thresholds;
a rep log records each counted rep with times and depths. "Suggest Thresholds" computes empirical
recommendations from the reps you just did (10th and 90th percentiles). The page is self-contained
(one ES module, no build, no npm install) and loads exercises from the config directory plus cached
MediaPipe pose estimation. `window.__angles.runSequence()` exposes the ported rep state machine for
integration test validation.
