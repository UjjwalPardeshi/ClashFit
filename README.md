# ClashFit

**Offline AI fitness combat.** Your body is the controller, the camera is the referee, and nothing
leaves the device.

Built for the **iQOO Hackathon 2026 · Pune City Battle · HealthTech** — 5–6 September 2026.
Phase 1 submission closes **1 September, 23:59 IST**.

---

## What is in here

| | |
|---|---|
| [`docs/`](docs/) | The full design set — 39 documents. Start at [`docs/README.md`](docs/README.md). |
| `src/`, `config/`, `test/` | A **runnable prototype** of the perception and combat core. |

> **The prototype is a throwaway.** It exists to prove the pose → rep → form → fatigue → damage
> loop works, to tune thresholds against a real body, and to record the Phase 1 video. **It is not
> carried into the hackathon.** A fresh repository is created at check-in and the Android app is
> written from scratch there. This repository is disclosed in the Phase 1
> pre-existing-components field.

The core algorithms are specified in [05-POSE-ENGINE-SPEC](docs/05-POSE-ENGINE-SPEC.md) and
[04-GAME-DESIGN](docs/04-GAME-DESIGN.md). The one page to read during the event is
[18-EVENT-CARD](docs/18-EVENT-CARD.md).

---

## Run

```bash
npm start          # http://localhost:8080
npm test           # 32 tests, no camera or browser needed
```

No install step — no dependencies. MediaPipe loads from a CDN, so the first run needs internet.

> Open it through `npm start`, not by double-clicking `index.html`. The camera API requires a
> secure context, and `http://localhost` is one; `file://` is not.

**Setup for a squat:** stand **side-on**, 2–2.5 m back, whole body in frame. Stand still for a
second — that captures your rest angle — then start. First completed rep sets your range-of-motion
baseline, so everything after is scored against *you*.

Hit **Debug** to watch the live angle, the state machine, the four sub-scores and the fatigue
signals. That panel is the tuning instrument.

**Modes.** *Boss Fight* is the full loop. *Time Attack* is 60 seconds for maximum damage — the
demo-shaped mode, because a rotating judge may not give you four minutes. *Ghost Race* runs a
recorded rep timeline alongside you: race a shipped pacer, your own past set, or a file someone
sent you. **Save ghost** exports the run you just did.

Ghosts ride the duel's own code path — a ghost is a remote player that happens to come from a
file. That makes Ghost Race the instant fallback if two-phone pairing fails in the hall, because
on screen it looks identical.

**Last Standing** is the one only this product can offer: everyone does the same movement, and
you are out when your **measured fatigue** reaches GASSED — not when you run out of reps. Every
other fitness app would eliminate on rep count. One device, any number of people, which is
exactly what a judging table has.

*Survival* removes the mercy rule and raises the clean-rep bar each wave — the one mode where
fatigue genuinely ends the run. *Clinic* runs the 30-second sit-to-stand protocol: no boss, no
damage, no ranking. A count, the protocol it came from, and an explicit not-a-medical-device line.

**Summary** draws the fatigue curve and exports it as a PNG — that image is deck slide 4 — plus a
CSV of every rep's raw telemetry.

> **Clinic norms are deliberately absent.** `config/clinic/sit_to_stand_30s.json` ships with an
> empty reference range and `showNormComparison: false`. Publishing an age band we cannot
> attribute is worse than publishing none. Source a real citation before it goes in the deck; the
> raw count and the personal trend need no citation and are the defensible part anyway.

---

## What works

| | |
|---|---|
| Pose | MediaPipe Pose Landmarker, 33 world landmarks, GPU delegate |
| Filtering | One Euro per landmark per axis |
| Rep detection | Hysteretic 4-state FSM with dwell guards, **direction-aware** |
| Form scoring | Depth (superlinear) · ROM · tempo · alignment |
| Fatigue | Velocity loss · ROM collapse · pause growth → 4 bands, latched |
| Combat | Damage curve, combo with grace, boss phases, fatigue-adaptive boss, mercy resolution |
| Duel | Event-sourced sync with self-healing tails, live over BroadcastChannel between two tabs |
| Traces | Record to JSON Lines for replay and regression |
| 48 exercises | 8 reps · 9 holds · 10 cardio · 7 jumps · 14 asanas |
| Modes | Boss Fight · Time Attack · Ghost Race · Survival · Boss Rush · Clinic · Duel |
| Group | **Pass the phone · Last Standing · Circuit** — one device, any number of people |
| Families | **5 detectors** — reps · holds · cardio · jumps · yoga |
| Family games | Siege (holds) · Pursuit (cardio) · Breaker (jumps) · Sigil (yoga) |
| Coach | Telemetry summariser, 20-line template bank, output validator, TTS |
| Sensors | Haptics · offline voice commands · Arena Mode (rear camera) |
| Accessibility | Reduced-motion toggle · verdict word always shown with the flash · accessible exercise rungs |
| Audio | Fully synthesised — rep pitch climbs with the combo |
| Summary | Fatigue curve chart, PNG export for the deck, CSV of raw per-rep data |
| Persistence | Local only — sessions, per-rep telemetry, calibration, personal bests, trends |
| Progression | **Forgiving streaks** (a rest day earns it), difficulty ladders with quiet demotion |
| Preflight | The pre-demo ritual as a button — eleven checks in two seconds |
| Mode grid | All fifteen modes on one screen, because a dropdown is invisible to a judge |
| Challenges | **Community without a server** — a run becomes a ~300-character code you can send anywhere |
| Ghosts | Three shipped pacers, save your own run, load a friend's |

## What is deliberately absent

On-device LLM coach, TTS, duel transport, NFC pairing, sensors, art, audio. All specified in
`docs/`, all built at the event.

---

## Tuning

**Record once, sweep offline.** The naive loop — do fifteen squats, edit JSON, do fifteen more —
is slow, inconsistent because you never repeat a set exactly, and impossible at the event when
your legs are gone.

```bash
node tools/tune.js traces/f1-clean.jsonl --expect 10
node tools/tune.js traces/*.jsonl --exercise squat --expect 10 --apply
```

`--expect` is ground truth: how many reps you actually did. The sweep replays every trace against
a grid of thresholds and ranks by **robustness** — how many nearby settings also count correctly —
rather than by a single exact hit. A setting that works but sits one degree from miscounting will
fail on a real body in different light.

**`config/` is the source of truth for every threshold.** Edit the JSON, hit **Reload config** in
the page — no restart. These exact files are copied to the phone at check-in, so tuning done here
is not thrown away even though the code is.

- `config/pose.json` — filter, visibility gate, fatigue weights and bands
- `config/combat.json` — damage curve, combo, boss, fatigue responses
- `config/exercises/*.json` — per-exercise thresholds

Malformed JSON never takes the app down; it keeps the last good config and says so.

---

## Tests

`npm test` runs 32 assertions with no camera and no body, using synthetic angle sequences from
`test/synth.js`. It covers the fixtures in [14-TEST-PLAN](docs/14-TEST-PLAN.md): clean reps,
shallow reps, threshold jitter, too-fast and too-slow reps, framing loss mid-set, inverted-direction
exercises, a set to failure reaching GASSED, band latching, the damage curve, combo grace, mercy
resolution, and duel sync under duplication, reordering and 30% packet loss.

**The suite has already earned its place — it caught three real bugs:**

1. **`bottomEnter` sat too close to `targetAngle`**, so every rep that counted already scored ~0.9
   on depth. The sub-score discriminated nothing. Rule now: `bottomEnter` is the generous "this
   counts", `targetAngle` is the strict "full marks".
2. **The fatigue bands were unreachable.** `GASSED` at 0.70 needed roughly a 72% concentric
   velocity loss — far past what a real set produces. The mercy rule, which is the kindest
   behaviour in the product *and* the safest thing that can happen during a live demo, would never
   have fired. Bands are now 0.15 / 0.30 / 0.50.
3. **The mercy cap inherited a hot combo multiplier**, so it set a target the player could not
   reach once the streak broke. The estimate is combo-neutral now.

The published damage table was also wrong. The real curve, asserted in the suite:

| formScore | Verdict | Damage |
|---|---|---|
| 0.00 | SHALLOW | 35 |
| 0.30 | SHALLOW | 50 |
| 0.55 | OK | 67 |
| 0.80 | CLEAN | 85 |
| 0.95 | CLEAN | 96 |
| 1.00 | CLEAN | 100 |

---

## Recording fixtures

**Record** → do the set → **Stop** → **Download**. Drop the `.jsonl` into `traces/`.

Capture F1–F9 from [14-TEST-PLAN](docs/14-TEST-PLAN.md) §2 — especially **F3** (a set to
genuine failure, which validates the fatigue model) and **F9** (a tall and a short subject, which
proves the ROM normalisation is fair).
