# 05 · Pose Engine Specification

**This is the most important document in the folder.** Rep detection, form scoring and fatigue
estimation are the technical core, the novelty claim, and the thing a jury will interrogate. Every
number below is a starting value that lives in `config/pose.json` and is tuned on-device during Red
Light blocks — not recompiled.

---

## 1. Model

MediaPipe Tasks · **Pose Landmarker** · `pose_landmarker_full.task` (fall back to `_lite` if the
frame budget is missed) · `RunningMode.LIVE_STREAM` · GPU delegate · `numPoses = 1`.

Outputs per frame:
- `landmarks[33]` — normalised image coordinates, x/y in [0,1], z relative
- `worldLandmarks[33]` — metric coordinates in metres, origin at the hip midpoint
- `visibility[33]`, `presence[33]` — confidence in [0,1]

**Use `worldLandmarks` for angles wherever possible.** Image-space angles distort with perspective
and camera tilt; world landmarks are already hip-centred and metric. Use image-space landmarks only
for the on-screen skeleton overlay and the framing/bounding-box checks.

Rationale for MediaPipe over MoveNet/PoseNet in [ADR-002](adr/ADR-002-pose-model.md).

---

## 2. Filtering — One Euro

Raw landmarks jitter enough to produce phantom rep transitions. A plain exponential moving average
trades jitter for lag, and lag at the bottom of a squat is exactly where the depth measurement
matters.

**Use a One Euro filter per landmark per axis.** It adapts its cutoff to speed: heavy smoothing when
the joint is nearly still, light smoothing when it is moving fast. That is precisely the behaviour
we want — stable at the top and bottom of a rep, responsive through the transitions.

```
minCutoff = 1.0     lower  → smoother, more lag
beta      = 0.007   higher → more responsive to fast motion
dCutoff   = 1.0
```

Filter the world-landmark coordinates, then compute angles from the filtered values. Do not filter
the angle itself — filtering the inputs preserves geometric consistency.

---

## 3. Frame validity gate

A frame is **valid** for the current exercise only if every joint in that exercise's required set
has `visibility ≥ 0.60`.

| Exercise | Required joints |
|---|---|
| Squat | hip, knee, ankle (both sides preferred, one side acceptable — see §8) |
| Push-up | shoulder, elbow, wrist, plus hip and ankle for the alignment sub-score |

**Invalid frames do not update the state machine.** They increment an invalid-frame counter. At
>45 consecutive invalid frames (~1.5s) the fight enters `FRAMING_LOST` and the fatigue baseline
freezes. This matters: a player walking out of frame must not be recorded as a slowing, fatiguing
athlete.

---

## 4. Rep detection — hysteretic finite state machine

Naive thresholding on a single angle double-counts on every tremor at the threshold. Two
thresholds per transition (hysteresis) plus minimum dwell times solve it.

### Primary angle

| Exercise | Angle | Vertices |
|---|---|---|
| Squat | knee flexion | hip → knee → ankle |
| Push-up | elbow flexion | shoulder → elbow → wrist |

`angle(a,b,c)` = the angle at `b`, computed from the 3D world landmarks via the dot product of
(a−b) and (c−b), clamped and converted to degrees.

### The machine

```
        θ > θ_top_enter
   ┌────────────────────────► TOP ◄────────────────┐
   │                           │                   │
   │                  θ < θ_top_exit               │ θ > θ_top_enter
   │                           ▼                   │   AND rep valid
   │                     DESCENDING                │   → EMIT RepEvent
   │                           │                   │
   │                  θ < θ_bottom_enter           │
   │                           ▼                   │
   │                       BOTTOM                  │
   │                           │                   │
   │                  θ > θ_bottom_exit            │
   │                           ▼                   │
   └────────────────────── ASCENDING ──────────────┘
```

### Default thresholds

| Exercise | θ_top_enter | θ_top_exit | θ_bottom_enter | θ_bottom_exit |
|---|---|---|---|---|
| Squat | 158° | 150° | 100° | 110° |
| Push-up | 155° | 148° | 95° | 105° |
| Chair squat | 150° | 143° | 115° | 125° |
| Knee push-up | 155° | 148° | 100° | 110° |

### Validity guards (all must pass or the rep is discarded silently)

| Guard | Default | Why |
|---|---|---|
| min dwell in `DESCENDING` | 200 ms | rejects jitter crossings |
| min dwell in `BOTTOM` | 120 ms | rejects a bounce that never actually reached depth |
| min total rep duration | 800 ms | nobody does a legitimate squat in 0.4s |
| max total rep duration | 8000 ms | beyond this it is a rest, not a rep |
| ≥90% valid frames within the rep | — | a rep half-observed is not scored |

**Emit exactly one `RepEvent` on the `ASCENDING → TOP` transition.** Never on partials.

---

## 5. Form scoring

Four geometric sub-scores, each in [0,1], combined into one `formScore`.

**We never claim to measure "good form".** We measure four named quantities and say so. This is the
honest answer to the jury question "how did you validate that?" — and it is the answer that
survives scrutiny.

### 5.1 Depth (D) — weight 0.40

How far past the target the player actually went, relative to their own top position.

```
linear = clamp( (θ_top_ref − θ_min) / (θ_top_ref − θ_target) , 0 , 1 )
D      = linear ^ depthExponent            // default 1.5
```
- `θ_top_ref` — the player's calibrated standing/plank angle, captured in CALIBRATION
- `θ_min` — minimum angle reached during this rep
- `θ_target` — the **full-marks** depth (90° squat), *not* the minimum to count a rep

**Superlinear, deliberately.** A linear map scored a quarter-squat at 0.65, which is far kinder
than a quarter-squat deserves in training terms — and it made the damage gap between a clean rep
and a shallow one too small for a bystander to notice, which is exactly what the demo depends on.

> **Threshold design rule, learned the hard way in testing:** `bottomEnter` is the *generous*
> "this counts as a rep" gate; `targetAngle` is the *strict* "full marks" depth. Set them close
> together — as an earlier draft of the config did, with `bottomEnter` 100 and `targetAngle` 90 —
> and every rep that counts already scores ~0.9 on depth, so the sub-score discriminates nothing.

### 5.2 Range of motion (ROM) — weight 0.25

```
ROM_rep = θ_max − θ_min
R = clamp( ROM_rep / ROM_baseline , 0 , 1 )
```

`ROM_baseline` is the player's own calibrated first rep. **This is what makes the score fair across
body types.** A 6'3" player and a 5'2" player are each measured against themselves, not against a
population average. Say this out loud in the pitch — it pre-empts the obvious objection.

### 5.3 Tempo (T) — weight 0.20

Split the rep into eccentric (descending) and concentric (ascending) durations.

```
T_ecc  = 1.0 if t_ecc ≥ 0.80s
       = t_ecc / 0.80 otherwise            (penalises dropping/bouncing)
T_pause = 1.0 if t_bottom ≥ 0.12s else 0.6 (penalises the bounce out of the hole)
T = 0.7·T_ecc + 0.3·T_pause
```

### 5.4 Alignment (A) — weight 0.15

Exercise-specific, and the sub-score most likely to be noisy. If it is unreliable on the day,
zero its weight in config and redistribute — do not delete the code.

| Exercise | Measure | Full marks | Zero marks |
|---|---|---|---|
| Push-up | torso line: angle(shoulder, hip, ankle) | ≥ 172° | ≤ 150° (hips sagging or piked) |
| Squat | knee tracking: horizontal offset of knee from ankle, normalised by shin length | ≤ 0.15 | ≥ 0.45 (knees collapsing inward) |

### 5.5 Combination

```
formScore = clamp( 0.40·D + 0.25·R + 0.20·T + 0.15·A , 0 , 1 )
```

Verdict banding for the UI:

| formScore | Verdict | Flash |
|---|---|---|
| ≥ 0.80 | CLEAN | `--clean` |
| 0.55 – 0.79 | OK | dim `--clean` |
| < 0.55 | SHALLOW | `--shallow` |

---

## 6. Fatigue estimation — the novelty

Three independent signals from movement quality, each normalised against the player's own early-set
baseline. This is grounded in velocity-based training practice, where concentric velocity loss
across a set is a standard proxy for accumulated fatigue.

**Baseline:** the mean of reps 1–3 of the current set. Fatigue is undefined (reported `FRESH`)
until three valid reps exist. The baseline freezes during `FRAMING_LOST` and `PAUSED`.

### 6.1 Concentric velocity loss — weight 0.45

Mean angular velocity during the ascending phase:
```
ω = (θ_top_exit_crossing − θ_bottom_exit_crossing) / t_concentric      [deg/s]
v_loss = clamp( 1 − (ω_rep / ω_baseline) , 0 , 1 )
```
Roughly 20% velocity loss is meaningful accumulated fatigue; 40%+ is high. Those anchors set the
band boundaries below.

### 6.2 Range-of-motion collapse — weight 0.35

```
rom_loss = clamp( 1 − (ROM_rep / ROM_baseline_set) , 0 , 1 )
```
Depth quietly disappearing across a set is the most visible fatigue tell and the easiest to
demonstrate to a judge — ask them to keep going and watch the meter move.

### 6.3 Inter-rep pause growth — weight 0.20

```
pause_growth = clamp( (gap_rep − gap_baseline) / 3.0s , 0 , 1 )
```
where `gap` is the time from the previous rep's completion to this rep's start.

### 6.4 Composite

```
fatigue_raw = 0.45·v_loss + 0.35·rom_loss + 0.20·pause_growth
fatigue     = EMA(fatigue_raw, α = 0.4)      // one bad rep must not spike the band
```

| fatigue | Band |
|---|---|
| < 0.15 | `FRESH` |
| 0.15 – 0.30 | `WORKING` |
| 0.30 – 0.50 | `FADING` |
| ≥ 0.50 | `GASSED` |

> **Recalibrated 22 Aug after the prototype test suite proved the original bands unreachable.**
> With the published weights, `GASSED` at 0.70 required roughly a **72% concentric velocity
> loss** — far beyond what a real set produces before failure. The mercy rule, which is both the
> kindest behaviour in the product and the safest thing that can happen during a live demo, would
> effectively never have fired. Verified by fixture F3.

Band transitions are **latched with a one-rep delay in both directions** so the meter does not
flicker between bands on the boundary.

### 6.5 What the game does with it

Consumed by `BossController` — see `04-GAME-DESIGN.md` §5. The headline behaviour: **the boss
cannot outlast you.** Once `GASSED` is latched, remaining boss HP is recomputed so the fight
resolves within a small number of further reps. The player always gets an ending.

---

## 7. Emitted contract

```kotlin
data class RepEvent(
  val repIndex: Int,
  val exercise: Exercise,
  val tStartMs: Long,
  val tEndMs: Long,
  val thetaMin: Float,
  val thetaMax: Float,
  val depth: Float,        // D
  val rom: Float,          // R
  val tempo: Float,        // T
  val alignment: Float,    // A
  val formScore: Float,    // combined 0..1
  val concentricVelocity: Float,
  val validFrameRatio: Float
)

data class FatigueState(
  val value: Float,        // 0..1
  val band: FatigueBand,   // FRESH | WORKING | FADING | GASSED
  val velocityLoss: Float,
  val romLoss: Float,
  val pauseGrowth: Float,
  val baselineReps: Int
)
```

Both are immutable and are the *only* things `perception` exposes to the rest of the app.

---

## 8. Robustness — the things that break this in a hackathon hall

| Failure | Mitigation |
|---|---|
| **Multiple people in frame** | `numPoses = 1`; MediaPipe returns the most prominent. Additionally lock onto the pose whose bounding box centre is nearest the frame centre at fight start, and re-acquire only after 2s of loss. **Test this before Saturday** — a hackathon hall is full of moving people. |
| **One side occluded** | Compute the angle from whichever side has higher mean visibility. If both sides are usable, average them. Report which side was used in the debug overlay. |
| **Bad venue lighting** | Prefer `_full` model; drop to `_lite` only for frame budget, never for accuracy. Raise `minPoseDetectionConfidence` rather than accepting garbage landmarks. |
| **Oblique camera angle** | World landmarks absorb most of this. Calibration captures `θ_top_ref` *in the actual camera position*, so the baseline is already angle-compensated. |
| **Front-camera field of view** | See `01-TRD.md` §5. Solo mode needs ~2.5m. Arena mode (rear ultra-wide + Office Kit mirror) is the fallback and the demo default. |
| **Player wearing loose clothing** | Nothing to do algorithmically. Note it in the demo script — wear fitted clothing. |
| **Thermal throttling after 20 min** | Drop preview resolution before dropping inference rate. Never run the LLM concurrently. |

---

## 9. Test fixtures — build these in the eleven days

Record and keep, as raw video plus a landmark trace:

1. **10 clean squats** — ground truth: 10 reps, mean formScore > 0.8
2. **10 deliberately shallow squats** — 10 reps, mean formScore < 0.5
3. **A fatiguing set to failure** — fatigue band must progress FRESH → GASSED monotonically
4. **Jitter set**: bouncing at the threshold without completing reps — expected count: 0
5. **Framing-loss set**: walk out of frame mid-set and return — no phantom reps, baseline preserved
6. **Cluttered background**: a second person walking behind — lock must hold on player one
7. **10 push-ups with deliberate hip sag** — alignment sub-score < 0.4

These become the acceptance suite in `14-TEST-PLAN.md`. Being able to replay a recorded trace
through the engine without a camera is worth several hours on the day.
