# ClashFit

**Your body is the controller. Your camera is the referee.**

Every fitness app on your phone counts what you *tell* it. Tap ten, it logs ten. None of them can
see whether the rep actually happened — so the log goes up while the movement quietly falls apart.

ClashFit makes the front camera the referee. Every rep is graded before it counts, and the grade
becomes damage against a boss. A shallow rep does almost nothing, so the only way to win is to move
better. Pose scoring runs on-device. Leaderboards, achievements and progression are in the cloud,
but camera frames, pose landmarks and coaching text never leave the phone.

> **iQOO Hackathon 2026 · Pune City Battle · HealthTech**
> Team **Da Goats** — Omkar Kadam, Ujjwal Pardeshi
> Phase-1 submission closes **1 September 2026, 23:59 IST** · Battle **5–6 September**

**Live:** [clash-fit.vercel.app](https://clash-fit.vercel.app) ·
**Prototype:** [/app](https://clash-fit.vercel.app/app) ·
**Deck:** [`deck-phase1/`](deck-phase1/)

---

## The loop

```
front camera · 30 fps
        ↓
Pose landmarker · GPU · 33 world landmarks
        ↓
One Euro filter → joint angles
        ↓
rep state machine  →  form score      (depth · range · tempo · alignment)
        ↓                  ↓
fatigue estimator  →  adaptive boss   (staggers when you fade, mercy when you're done)
        ↓
[between sets] Gemma 3n on-device → speech
        ↓
local storage · this phone only
        ↓
[after session] scores → Firestore    (only scores, names, levels)
        ↓
leaderboards (global and friends)
```

Camera frames, pose data and coaching text never reach Firestore. The camera stays closed during
leaderboard reads and all multiplayer sync.

---

## What is in here

| Path | What it is |
| --- | --- |
| [`index.html`](index.html) | The **product site**. Deployed at the root of the Vercel project. |
| [`app.html`](app.html) | The **runnable prototype**, served at `/app`. |
| [`src/`](src/) | The engine — 31 modules, 5 032 lines, **zero runtime dependencies**. |
| [`config/`](config/) | Exercises, detectors, combat and pose tuning. The page's numbers are read from here. |
| [`test/`](test/) | 163 tests, including ones that fail the build if the site out-claims the config. |
| [`deck-phase1/`](deck-phase1/) | The Phase-1 pitch deck — animated HTML, plus PDF and PPTX. |
| [`deck/`](deck/) | The event-day deck for 5–6 September. Different audience, different content. |
| [`docs/`](docs/) | The design set — 40 documents. Start at [`docs/README.md`](docs/README.md). |
| [`tools/`](tools/) | Threshold tuning, the fatigue chart generator, the OG card. |
| [`traces/`](traces/) | Recorded landmark traces for headless replay. |

---

## Run it

No install step. No build step. No dependencies.

```bash
python3 -m http.server 8080          # then open http://127.0.0.1:8080
```

| | |
| --- | --- |
| `http://127.0.0.1:8080/` | the product site |
| `http://127.0.0.1:8080/app.html` | the prototype — needs a camera and a lit room |
| `http://127.0.0.1:8080/deck-phase1/` | the pitch deck (`F` for full screen, `←` `→` to move) |

### The tests

```bash
node test/run.js
```

163 assertions over the rep state machine, the form scorer, the fatigue estimator, the duel sync,
the coach's output validator, and the landing page's own claims. That last group is the unusual one:
**the build fails if the site prints a number `config/` does not hold.** It has caught a wrong
exercise count, a wrong telemetry field count, and five stale mode counts.

### Replay a set without a body

```bash
node test/replay.js traces/synthetic-f3-to-failure.jsonl
```

Pushes a recorded trace through the exact shipping engine — no camera, no human. This is how
thresholds get tuned without doing four hundred squats, and it is where the fatigue curve in the
deck comes from.

The synthetic traces are generated rather than hand-recorded, by `node tools/make-trace.js`, which
also rewrites the fatigue chart from the same replay. `node tools/make-trace.js --check` fails if a
trace no longer reproduces what is printed beside it — which is how this one went stale once
already when the squat thresholds moved underneath it.

```
reps 14   mean form 0.890   depth 0.94  rom 0.86  tempo 1.00  align 0.67
fatigue 0.530 (GASSED)   bands seen: FRESH -> WORKING -> FADING -> GASSED
boss 384/3000   damage 2293   MERCY FIRED
```

---

## How it actually works

### Grading a rep

A rep is not a count. It is four measurements the camera can make, weighted per exercise —
because depth matters more in a squat than tempo does.

| Weight | Measure | Why |
| --- | --- | --- |
| `0.40` | **Depth**, in real centimetres | Scored superlinearly, so a shallow rep loses more than it looks like it should |
| `0.25` | **Range** | This rep's arc against the best arc in your own first three |
| `0.20` | **Tempo** | The eccentric — where reps get cheap and where people get hurt |
| `0.15` | **Alignment** | The joint that betrays the movement: knee over toe, hip above shoulder |

Damage is that score on a curve — `baseDamage · ((form − 0.35) / 0.65) ^ 1.2` — so a bad rep
visibly does less.

### Five families, five ways to fatigue

Fatigue is not one signal. Each family degrades differently, so each has its own weighted signals
feeding one estimator and one set of bands (`0.15 / 0.30 / 0.50`, latched).

| Family | Exercises | Game | Fatigue reads as |
| --- | --- | --- | --- |
| `REP_CYCLE` | 10 | Boss Fight | velocity decay `.45` · range decay `.35` · pause growth `.20` |
| `ISOMETRIC_HOLD` | 9 | Siege | tremor growth `.60` · quality decay `.40` |
| `POSE_MATCH` | 14 | Sigil | accuracy drift `1.00` |
| `CADENCE` | 11 | Pursuit | cadence decay `.55` · amplitude decay `.45` |
| `BALLISTIC` | 7 | Breaker | height decay `.60` · landing softness `.40` |

A plank has no velocity, so we measure the shake.

### The coach

Between sets — never during one — a **23-field summary** of the set goes to Gemma 3n running on the
phone. Counts, form means, first-three against last-three, depth lost in centimetres, velocity and
range loss, the fatigue band, best and worst rep, bilateral deficit. **Never a frame of video.**

Every sentence it returns is checked against the telemetry it was given. A line citing a number we
did not measure is dropped and a template bank keyed on the same numbers speaks instead, so the
athlete never hears a hallucination.

### Two phones, no server

Phones exchange **events**, not state:

```
bossHp = maxHp − Σ damage over dedupe(union of every event either phone has seen)
```

Every message carries a self-healing tail of the last eight events, so a dropped packet repairs
itself on the next one. No acknowledgements, no retransmit logic, no clock sync — and it works in
airplane mode.

---

## Accounts and privacy

**Required for leaderboards and progression sync:** Email and password sign-up (Firebase Auth +
Firestore, initialised from local.properties keys at build time; if keys are absent, the app works
fully offline with a local account).

**Privacy promise:** Camera frames, pose landmarks and rep timelines never leave the phone. Pose
scoring runs on-device (MediaPipe GPU), on-device coaching runs on-device (Gemma 3n), and voice
commands are offline (Android SpeechRecognizer). Only scores, display names and levels are sent to
Firestore — strictly after signing in, and only what the leaderboard needs to display.

Nearby Connections multiplayer (duel, raid, relay) stays serverless and works in airplane mode.
Leaderboards need INTERNET only to read and to publish a score. Signing up needs it once; after that
the training itself runs with the radio off and scores go up when there is a connection.

---

## Not a medical device

Clinic mode runs the 30-second sit-to-stand the way the published protocol defines it, and reports
bilateral asymmetry as a Limb Symmetry Index with a confidence grade — because a side-on camera
foreshortens the far limb and will invent an asymmetry that is not there.

It is a **measurement, not a diagnosis**, and that is enforced rather than promised: a validator
blocks the words *injury*, *risk*, *diagnos*, *cleared* and *abnormal* from ever reaching the
athlete, and a test asserts it.

---

## Accounts, levels and leaderboards

**Progression**: XP earned from rep quality (4 base + 8 × formMean per rep), bonuses for a boss
defeated (25 XP), a new personal best (30 XP), streak days (5–35 XP per day, up to 7 days),
and weekly challenge completion (100 XP). Casual sessions earn half XP. Cumulative XP determines
level (100 × (L−1)^1.5 for level L ≥ 2) and rank title (Recruit → Legend, 9 titles total).

**Achievements**: 22 badges in bronze/silver/gold tiers across fights, wins, streaks, form quality,
damage, versatility, personal bests, weekly challenges, game modes, levels and ground covered outdoors.

**Weekly challenges**: Same challenge for all players each ISO week (e.g. "2026-W36"). One of 11
possible challenges rotating deterministically (damage, clean reps, sessions or streak days, each
with three difficulty tiers). Completing a challenge grants 100 XP.

**Leaderboards**: Global (this week's damage, this week's clean reps, all-time XP, longest streak)
and friends leaderboards. Friend codes (6 chars, derived from player UID) enable adding friends
without sharing email. Your standing is published whenever it changes, debounced so one finished
session is one upload; all multiplayer sync still happens locally via Nearby Connections.

See [`firebase/README.md`](firebase/README.md) for Firestore schema and setup instructions.

---

## Status

| | |
| --- | --- |
| Running today | rep detection across 5 families · 4-part form scoring · fatigue estimator · adaptive boss · bilateral asymmetry · two-phone duel · on-device coach · sit-to-stand |
| Needs the native app | wake-up alarm · app-unlock budget · background posture sampling · run tracker |
| Further out | group raid rooms · vision-grounded critique · physio-prescribed protocols · the full clinic battery |

**The prototype is a throwaway.** It exists to prove the loop works, to tune thresholds against a
real body, and to record the Phase-1 video. A fresh repository is created at check-in and the
Android app is written from scratch there. This repository is disclosed in the Phase-1
pre-existing-components field.

---

## Credits

Photography is public domain or Creative Commons — attributions in [`credits.html`](credits.html)
and [`img/CREDITS.md`](img/CREDITS.md). The iQOO 15 shown is the manufacturer's own product render,
used to identify the device this is built for; ClashFit is an independent entry and is not published
or endorsed by iQOO or vivo.
