# 29 · Build Status

What is built and verified, what is deliberately event-only, and what is genuinely still open.

**Last updated:** 22 Aug 2026 · 140 assertions passing · 31 modules · 51 exercises · 33 commits

---

## 1. Built and tested

| Area | State | Doc |
|---|---|---|
| **Pose engine** — One Euro filtering, joint geometry, direction-aware rep FSM | ✅ | [05](05-POSE-ENGINE-SPEC.md) |
| **Form scoring** — depth (superlinear) · ROM · tempo · alignment, worst sub-score names the fault | ✅ | [05](05-POSE-ENGINE-SPEC.md) §5 |
| **Fatigue** — signal-driven, one model across all five families, latched bands | ✅ | [05](05-POSE-ENGINE-SPEC.md) §6 |
| **Five detector families** — reps · holds · cardio · jumps · yoga | ✅ | [19](19-EXERCISE-LIBRARY.md) |
| **51 exercises** as config records | ✅ | [19](19-EXERCISE-LIBRARY.md) §5 |
| **Combat** — damage curve, combo with grace, boss phases, fatigue-adaptive mercy | ✅ | [04](04-GAME-DESIGN.md) |
| **Family games** — Siege · Pursuit · Breaker · Sigil (non-combat) | ✅ | [19](19-EXERCISE-LIBRARY.md) §3 |
| **Modes** — Boss Fight · Time Attack · Ghost Race · Survival · Boss Rush · Tempo Trial · Duel | ✅ | [17](17-GAME-MODES.md) |
| **Group play** — Pass the phone · Last Standing · Circuit | ✅ | [20](20-MULTIPLAYER-MODES.md) |
| **Duel sync** — event-sourced, self-healing tails, live over a real channel | ✅ | [07](07-MULTIPLAYER-SPEC.md) |
| **Coach** — telemetry summariser, template bank, output validator, LLM seam | ✅ | [06](06-AI-COACH-SPEC.md) |
| **Audio + speech + haptics** — synthesised, no assets to license | ✅ | [03](03-UI-UX-SPEC.md) §5, [21](21-SENSOR-PLAYBOOK.md) §4 |
| **Voice commands** — offline, the only mid-set input that works | ✅ | [21](21-SENSOR-PLAYBOOK.md) §5 |
| **Calibration gating** — six states, named cues, stillness required | ✅ | [02](02-APP-FLOW.md) §2 |
| **Clinic** — 30-second sit-to-stand, norms deliberately uncited | ✅ | [25](25-CLINIC-MODE.md) |
| **Breathing + recovery** — detection nearly free, bounded fatigue recovery | ✅ | [22](22-HEALTH-DOMAINS.md) §3 |
| **Persistence + progression** — sessions, bests, trends, forgiving streaks, ladders | ✅ | [08](08-DATA-MODEL.md), [23](23-META-PROGRESSION.md) |
| **Summary** — fatigue curve, PNG for the deck, CSV of raw telemetry | ✅ | [02](02-APP-FLOW.md) §2 |
| **Traces** — record and headless replay | ✅ | [14](14-TEST-PLAN.md) §1 |
| **Arena Mode + reduced motion + accessible rungs** | ✅ | [03](03-UI-UX-SPEC.md) §9 |
| **Preflight** — the pre-demo ritual as a button, 11 checks | ✅ | [14](14-TEST-PLAN.md) §6 |
| **Mode grid** — all 15 modes on one screen | ✅ | [17](17-GAME-MODES.md) §9 |
| **Challenge codes** — a run becomes a ~300-char shareable string, no server | ✅ | [20](20-MULTIPLAYER-MODES.md) §5 |
| **Trend chart** — session over session, form and reps | ✅ | [23](23-META-PROGRESSION.md) |
| **The deck** — 10 slides, print-to-PDF at 16:9 | ✅ | [26](26-DECK-COPY.md), `deck/` |

---

## 2. Event-only, on purpose

None of these can exist outside the venue. All are specified and their seams are built.

| Item | Why it waits | Seam that is ready |
|---|---|---|
| **Gemma 3n on-device** | Needs the phone and the NPU | `coachFor(telemetry, llm)` — pass an llm and it takes over; validation and fallback already run |
| **NFC tap-to-pair** | Needs two phones | `DuelTransport` interface — swap the transport, nothing else changes |
| **Hotspot socket transport** | Needs two phones | same interface |
| **Proximity push-up depth** | Needs the loaner's sensor, which may be binary | verify at check-in, [24](24-BUILD-SETUP.md) §7 |
| **IMU plank sag** | Needs a second phone on the body | [21](21-SENSOR-PLAYBOOK.md) §2.2 |
| **Office Kit** | Needs the loaner | [11](11-DEMO-SCRIPT.md) §5 |
| **The Android app itself** | Original work must be created at the event | [KOTLIN-CORE](reference/KOTLIN-CORE.md), retype not copy |

---

## 3. Genuinely open

| | Owner |
|---|---|
| **Tuning against a real body.** Every threshold was set against synthetic geometry. The logic is proven; the numbers are not. | You, today |
| **Fixtures F1–F9** from [14](14-TEST-PLAN.md) §2, especially F3 (a set to failure) and F9 (tall vs short) | You |
| **Front-camera framing distance** on an actual device | You |
| **Clinic reference norms** — shipped empty on purpose, `showNormComparison: false` | Needs a citation |
| **Yoga reference angles** — approximate, flagged for capture from a reference performer | Needs a session |
| **Credentials for the Phase 1 team fields** | You |
| **Grand Finale scope question** with the organisers | Email |

---

## 4. Bugs the tests caught

Every one of these would have surfaced at hour 20 instead. This is the argument for the suite.

| # | Bug | Consequence if shipped |
|---|---|---|
| 1 | `bottomEnter` too close to `targetAngle` | Depth scored ~0.9 for every counted rep — the sub-score discriminated nothing |
| 2 | Fatigue bands unreachable | `GASSED` needed ~72% velocity loss, so the mercy rule would never have fired |
| 3 | Mercy cap inherited a hot combo | Set a target the player could not reach once the streak broke |
| 4 | Tempo measured the wrong window | 0.80s target demanded a 2.4s descent; capped tempo at ~0.6 for perfect reps |
| 5 | Six template lines broke the two-sentence rule | The fallback bank could not pass its own validator |
| 6 | Validator counted decimal points as sentence ends | Would have rejected any model output containing "4.2 centimetres" |
| 7 | Low-HP nudge suppressed fatigue coaching | Silenced the one thing we uniquely measure, exactly when it mattered |
| 8 | Duel `HELLO` answered with `HELLO` | Announce storm; two peers blew the stack. **Would have hit the phones identically.** |
| 9 | Ballistic used hip-centred world landmarks | Jump height was structurally unmeasurable — world landmarks cannot see vertical translation |
| 10 | Streak freeze covered a month-long gap | Claimed an unbroken streak the user had not earned |
| 11 | Ladders referenced two ungenerated exercises | Promotion would have failed silently at the top of the push ladder |
| 12 | Challenge buttons wired but never added to the markup | Share and Accept simply would not have existed — caught by the id cross-check |
| 13 | A deck CSS class used but never defined | The shallow-rep number would have rendered plain beside its coloured counterpart |

---

## 5. Run it

```bash
npm start     # http://localhost:8080
npm test      # 126 assertions, no camera or browser needed
npm run manifest
node test/replay.js traces/*.jsonl
```
