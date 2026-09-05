# 29 · Build Status

What is built and verified, what is deliberately event-only, and what is genuinely still open.

**Last updated:** 5 Sep 2026 · Android app shipping · 597 tests passing · 51 exercises · 16 modes · 72 screenshot baselines · 195 commits

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
| **Preflight** — the pre-demo ritual as a button, 10 checks | ✅ | [14](14-TEST-PLAN.md) §6 |
| **Mode grid** — all 16 modes on one screen | ✅ | [17](17-GAME-MODES.md) §9 |
| **Challenge codes** — a run becomes a ~300-char shareable string, no server | ✅ | [20](20-MULTIPLAYER-MODES.md) §5 |
| **Trend chart** — session over session, form and reps | ✅ | [23](23-META-PROGRESSION.md) |
| **The deck** — 15 slides, PDF and PPTX at 1920×1080 | ✅ | [26](26-DECK-COPY.md), `deck/` |
| **Tuning harness** — record once, sweep thresholds offline, rank by robustness | ✅ | `tools/tune.js` |
| **The Android app** — Kotlin, Jetpack Compose, Material 3 dark, type-safe navigation | ✅ | `android/` |
| **Accounts** — email and password, Firebase Auth, profile setup, password reset | ✅ | [34](34-ACCOUNTS-SOCIAL.md) |
| **Leaderboards** — weekly and all-time, everyone and friends, Firestore-backed | ✅ | [34](34-ACCOUNTS-SOCIAL.md) |
| **Friends** — a shareable code, no email or phone number required | ✅ | [34](34-ACCOUNTS-SOCIAL.md) |
| **XP, levels and ranks** — earned from measured reps, never from opening the app | ✅ | [23](23-META-PROGRESSION.md) |
| **Badges** — 18 achievements across bronze, silver and gold | ✅ | [23](23-META-PROGRESSION.md) |
| **Weekly challenge** — one target a week, same for everyone, resets on Monday | ✅ | [23](23-META-PROGRESSION.md) |
| **The Pacemaker** — a rigged glTF model, five clips, rendered by Filament over the camera | ✅ | [15](15-ASSET-BRIEF.md) §1 |
| **Boss fallback** — the Compose-drawn boss, same six states, if the renderer will not start | ✅ | [15](15-ASSET-BRIEF.md) §1 |
| **Exo-suit overlay** — the player on full-screen camera with armour drawn from the landmarks | ✅ | [03](03-UI-UX-SPEC.md) |
| **Four tabs** — Train · Leaderboard · Progress · You, each a hub with its own sections. Library sits on Train | ✅ | [03](03-UI-UX-SPEC.md) §2 |
| **Progress dashboard** — the trend, the verdict donut and the radar on the page, not behind a list | ✅ | [23](23-META-PROGRESSION.md) |
| **Game feel** — damage-weighted shake, impact sparks, crit stamp, combo fire, staged rewards | ✅ | [04](04-GAME-DESIGN.md) |
| **Character sheet** — seven health domains on a radar, two honestly marked unmeasured | ✅ | [22](22-HEALTH-DOMAINS.md) §1 |
| **Charts** — trend, bar, stacked, heatmap, radar and donut, all drawn on a Canvas | ✅ | [03](03-UI-UX-SPEC.md) |
| **About and How to play** — the vocabulary of a fight, explained for a stranger | ✅ | [03](03-UI-UX-SPEC.md) |
| **Screenshot suite** — 72 baselines rendered on the JVM, no phone needed to review the UI | ✅ | [14](14-TEST-PLAN.md) |
| **Baseline profile** — the startup path, the component kit and the fight loop compiled ahead of time | ✅ | `app/src/main/baseline-prof.txt` |
| **Permission lock file** — every permission the APK may hold, with its reason; the build fails on any drift in either direction | ✅ | `PERMISSION_ALLOW_LIST`, `checkPermissions<Variant>` |
| **Opens with no network** — a third door on the welcome screen; an account buys the leaderboard and nothing else | ✅ | [34](34-ACCOUNTS-SOCIAL.md) |

---

## 2. Event-only, on purpose

None of these can exist outside the venue. All are specified and their seams are built.

| Item | Why it waits | Seam that is ready |
|---|---|---|
| **Gemma 3n on-device** | The 3GB model is pushed to the phone at the venue, not committed | `LlmEngine` loads it if present and falls back to the template bank if not — both paths are tested |
| **NFC tap-to-pair** | Needs two phones | `DuelTransport` interface — swap the transport, nothing else changes |
| **Hotspot socket transport** | Needs two phones | same interface |
| **Proximity push-up depth** | Needs the loaner's sensor, which may be binary | verify at check-in, [24](24-BUILD-SETUP.md) §7 |
| **IMU plank sag** | Needs a second phone on the body | [21](21-SENSOR-PLAYBOOK.md) §2.2 |
| **Office Kit** | Needs the loaner | [11](11-DEMO-SCRIPT.md) §5 |

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
| **Which problem statement is locked** — visible on the dashboard, and the leader must lock one before the idea form opens | You |
| **Grand Finale scope question** with the organisers | Email |
| **A Windows or macOS laptop for Office Kit** — deferred 29 Aug, still R0 | You |

---

## 4. Bugs the tests caught

Every one of these would have surfaced at hour 20 instead. This is the argument for the suite.

Numbers 16 to 35 came from adversarial audit passes run against the code after it was written, each finding verified against the source before it was believed. Several of them — the alignment sample, the occluded joints, the blind pause score — were quietly wrong in the measurement layer, which is the part of this that has to be right for any of the rest to mean anything.

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
| 14 | **Calibration required stillness, not visibility** | Took 27s on a real trace and ate 6 of 13 reps — and the fatigue baseline was then computed from an already-degraded part of the set, silently corrupting the one thing we uniquely measure |
| 15 | Tuning harness generated inverted hysteresis | Proposed `topExit` above `topEnter`, and would have "recommended" nonsense |
| 16 | Alignment sampled with the legs straight | Measured at the top of the rep, where every body is aligned, so the sub-score was near-perfect for everyone and graded nothing |
| 17 | Occluded joints scored as visible | `presence()` reads whether a landmark exists, not whether it can be seen; a knee behind a thigh was scored as confidently as one in clear view |
| 18 | The pause sub-score was blind to a bounce | Scored 1.0 for any rep that reached the bottom, so bouncing out of the hole graded the same as holding it |
| 19 | `awaitClose` inside a nested `collect` | Friends screen crashed on open — the handle must be registered in the producer scope |
| 20 | Friends queried `whereIn("uid", …)` | The uid *is* the document id, so the query matched nobody and the list was always empty |
| 21 | Duel sequence maps not cleared on timeout | A player who reconnected had every message rejected as stale |
| 22 | `RouteTrace` never called after the run ended | The map drawn for the whole run vanished at the finish line, under a caption promising the route was on the phone |
| 23 | `splitsJson` parsed as a bare list | Bracketed JSON would silently drop the first and last split and renumber the rest — five kilometres reported as three |
| 24 | Text-to-speech caught its own cancellation and restarted | Leaving a fight did not stop the coach, it made the phone finish reading the queue to an empty room |
| 25 | Twenty more catch-alls swallowing `CancellationException` | Cancelled work in auth, the cloud friends list and both pose sources carried on running for nobody |
| 26 | Breathing had no way back out | Reached from Train with no top bar, no bottom bar and no arrow, on a phone with gestures off it was a dead end |
| 27 | The one-phone roster had the same dead end | The only exit was to start a game you had changed your mind about |
| 28 | Seven days of a week in a three-by-three grid | New alarm stranded Sunday alone across a full-width row |
| 29 | The rep-cycle family mark was the letter C | The break in the ring sat on the right, so the library showed a column of nine identical Cs |
| 30 | Raw enum ids printed at people | "BOSS_FIGHT" and "TIME_ATTACK" on the screen that introduces a movement |
| 31 | Radar axes reduced to three-letter codes | The web left twelve pixels of clearance, so seven health domains read as PWR, STA, NUT — a chart that needs a decoder |
| 32 | The profile said the rank twice | "Recruit" beside the emblem and "Level 1 · Recruit" on the bar directly beneath it |
| 33 | `AppCard` lost its button semantics | The hand-rolled rewrite dropped `Role.Button` from every card in the app — invisible to sight, total to a screen reader |
| 34 | The crash log raced itself | Two threads crashing at once, and the second write erased the first |
| 35 | "Leaderboard" broke across two lines at 1.5x text | The tab a judge is told to open, mid-word, on every screen |
| 36 | **A fresh install with no signal could not be opened** | Both doors on the welcome screen talked to Firebase. On a venue's wifi, a plane, or a phone with data off, the first screen was the last screen — for an app whose whole claim is that the referee runs on the phone |
| 37 | Two permissions in the APK nobody had asked for | Play services added `READ_GSERVICES` and androidx a receiver permission; found the moment the permission set was written down as a lock file |
| 38 | **A new account had no name until the app was relaunched** | Firebase's auth listener fires before the display name is written and never fires again for a profile update; an empty string is not null, so the fallback never ran. The profile card showed nothing beside "Recruit" and the avatar drew "?" — only in the sign-up session, which is the one a judge watches. Found by making an account on the demo phone at the venue |

---

## 5. Run it

The Android app:

```bash
cd android
./gradlew :app:installDebug          # onto a connected phone
./gradlew :app:testDebugUnitTest     # 597 tests, no phone needed
./gradlew :app:recordRoborazziDebug  # re-render the 72 screenshot baselines
```

Firebase keys live in `android/local.properties`, which is git-ignored. `android/README.md`
says what to put there and where to push the coach model.

The phase-one web prototype, still the fastest way to replay a trace:

```bash
npm start     # http://localhost:8080
npm test      # no camera or browser needed
npm run manifest
node test/replay.js traces/*.jsonl
```
