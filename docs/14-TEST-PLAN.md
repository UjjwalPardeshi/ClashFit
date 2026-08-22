# 14 · Test Plan

A 19-hour build cannot afford a test suite. It cannot afford *no* verification either — the
failure mode of an untested pose engine is discovering at hour 28, in front of a judge, that it
counts three reps for every two. This plan is the minimum that actually protects the demo.

---

## 1. The one investment that pays for itself: trace replay

**`PoseEngine.startFromTrace()` (see `09-MODULE-CONTRACTS.md` §3) is the highest-value hour in the
whole project.**

Record landmark traces once, before the event, then replay them through the engine with no camera,
no body, and no floor space. It gives you:

- Deterministic regression checking after every threshold change
- Tuning without doing 400 squats over a weekend on no sleep
- A camera-free demo fallback when detection fails in the hall
- Evidence to show a judge: "here is a recorded set, here is what the engine measured"

Build the recorder in the eleven days. Trace format: JSON lines, one frame per line, timestamp plus
33 world landmarks plus visibility.

---

## 2. Fixtures — record these before the event

| # | Fixture | Expected result |
|---|---|---|
| F1 | 10 clean squats | count = 10, mean formScore > 0.80 |
| F2 | 10 deliberately shallow squats | count = 10, mean formScore < 0.50 |
| F3 | Set to genuine failure (~20 reps) | fatigue band progresses FRESH → GASSED, monotonic after smoothing |
| F4 | Bouncing at the threshold, no full reps | count = 0 |
| F5 | Walk out of frame mid-set, return, continue | no phantom reps; fatigue baseline preserved across the gap |
| F6 | Cluttered background, second person walking behind | lock holds on player one throughout |
| F7 | 10 push-ups with deliberate hip sag | count = 10, alignment sub-score < 0.40 |
| F8 | 10 chair squats | count = 10 with chair-squat thresholds |
| F9 | Very tall and very short subject, same set | formScore means within 0.1 of each other — proves ROM normalisation works |

F9 is the fixture that proves fairness. If you can only record one extra thing, record that one.

---

## 3. Unit tests — write only these

Roughly 20 tests, one hour of work, all pure functions with no Android dependency.

| Component | Tests |
|---|---|
| `JointGeometry.angle()` | known triangles; degenerate/collinear input; NaN safety |
| `RepStateMachine` | synthetic angle sequences → expected rep counts. Include: clean rep, jitter at threshold, too-fast rep, too-slow rep, incomplete rep |
| `FormScorer` | sub-score boundaries at 0 and 1; weight sum = 1.0; clamping |
| `FatigueEstimator` | flat set → FRESH; decaying velocity → rising band; band latching does not flap on the boundary |
| `CombatEngine.damageFor()` | the worked examples in `04-GAME-DESIGN.md` §2 exactly |
| `ComboTracker` | streak build, break, grace at streak ≥ 6, cap at 2.5 |
| `DuelSession` | dedupe by (playerId, seq); out-of-order arrival; duplicate flood; `recent` tail repair |
| `CoachOutput` validation | rejects >2 sentences, hallucinated numerals, blocklist terms |

**`CombatEngine` and `DuelSession` are pure and deterministic by design** — that is why the
contracts specify no internal timers. It makes them testable in seconds.

---

## 4. Acceptance criteria per tier

### Tier 0 — gate G3, Saturday 19:00
- [ ] A person who has never seen the app completes a fight without verbal help
- [ ] Rep count matches a human counting aloud, 10 out of 10 reps, twice
- [ ] A deliberately shallow rep visibly does less damage, noticed by a bystander who was not told
- [ ] Audio feedback distinguishes clean from shallow with the screen turned away
- [ ] App survives 15 minutes of continuous use without crash or frame collapse
- [ ] Launch to first rep under 20 seconds

### Tier 1 — gate G5, Sunday 05:00
- [ ] Fatigue band reaches FADING within a genuine set to near-failure
- [ ] Boss stagger fires visibly at FADING
- [ ] GASSED mercy resolves the fight within ~4 reps
- [ ] Coach line is spoken aloud and cites a real number from the set
- [ ] Killing the model file falls back to templates with no visible difference
- [ ] Full fight completes with airplane mode on

### Tier 2 — Sunday 06:30
- [ ] Two phones, 20 reps each, identical final boss HP
- [ ] Link killed mid-fight → both keep playing, banner shown, neither hangs
- [ ] Link restored → both converge within 2 seconds
- [ ] Pairing from lobby to countdown in under 30 seconds, timed, three times

---

## 5. Device checks — run at every tier gate

| Check | Threshold |
|---|---|
| Pose inference latency | ≤ 22 ms, GPU delegate confirmed (not silently on CPU) |
| Sustained FPS after 10 min | ≥ 25 fps |
| Device temperature after 20 min | no visible frame-rate collapse |
| Memory with model loaded | no OOM under a full fight |
| Battery drain | ≥ 1 full fight per 10% charge |
| Cold start to HOME | ≤ 1.5 s |

---

## 6. The pre-demo ritual — run before every judging round

**Built as a button.** `src/preflight.js` runs all of it in about two seconds and names exactly
what is wrong — under sleep deprivation at hour 26, a checklist you have to remember to run is a
checklist you skip. It reports READY / READY WITH WARNINGS / NOT READY, and a debug overlay left
on is a hard failure because it is the single easiest thing to forget and it reads as unfinished
in front of a jury.

The manual list it encodes:

1. Both phones ≥ 80% battery
2. Golden APK present on both
3. Airplane mode on, app still works — verified, not assumed
4. Calibration completes in under 20 seconds at the actual table
5. Office Kit connected and mirroring
6. Hotspot pre-configured, duel pairs in under 30 seconds
7. Config files at the tuned versions, not a debug variant
8. Debug overlay **off**
9. Audio volume up, TTS audible over room noise
10. One full fight completed successfully in the last 30 minutes
