# 17 · Game Modes

**Read the warning first.** Modes are Tier 2 and Tier 3. They come *after* fatigue detection, the
on-device coach, and the duel. A mode-select screen with six entries and a broken fatigue system
loses to a single boss fight that works. Nothing in this document is permitted to displace Tier 1.

That said — the reason to add modes is real. "Would someone keep using it" is 30% of the score, and
one boss fight with one exercise reads as a demo. **A mode selector visibly full of options signals
a product**, even if you only ever demo one of them.

---

## 1. The cost model

Every mode below reuses the **same perception layer** — same camera, same rep FSM, same form
scoring, same fatigue estimator. That is the whole point. They are `combat` and `ui` work, which is
Ujjwal's lane, and they cost hours rather than days.

**New *exercises* are expensive** — a new state machine, new thresholds, new camera framing, new
test fixtures. **New *modes* are cheap** — they are different rules applied to the same `RepEvent`
stream. Add modes, not exercises.

| Mode | Est. cost | Perception work | Verdict |
|---|---|---|---|
| Boss Fight | — | — | **Tier 0.** The core. |
| Time Attack | ~40 min | none | **Build it.** Pays for itself — see §3. |
| Ghost Race | ~1 h | none | **Build it.** Highest value single addition — see §4. |
| Tempo Trial | ~45 min | none | Only if the tempo sub-score proves reliable. |
| Survival | ~30 min | none | Config-cheap. Free depth. |
| Boss Rush | ~20 min | none | Config data only. Nearly free. |
| Arena Idle | ~30 min | none | Legitimate ambient feature, see §8. |
| Plank / isometric hold | ~1.5 h | **new detector** | Different mechanic (timer on angle-in-range, not a rep cycle). Cut unless well ahead. |
| Jumping jacks, lunges, burpees | ~2 h each | **new FSM each** | No. This is how the weekend dies. |

---

## 2. Priority order — confirmed 21 Aug

**Committed:** Boss Fight (Tier 0) · **Time Attack · Ghost Race · Survival · Boss Rush** (Tier 2).
Tempo Trial only if the tempo sub-score proves reliable.

1. **Time Attack** — the demo-shaped mode
2. **Ghost Race** — the duel fallback and the retention mechanic
3. **Survival** and **Boss Rush** — config-only depth for the mode screen
4. **Tempo Trial** — conditional
5. Everything else — deck roadmap

> **Family games.** The five modes above are all `REP_CYCLE` games. Isometric holds, yoga, cardio
> and ballistic movements each get a game shaped like the movement — Siege, Sigil, Pursuit, Breaker.
> Those are specified in [19-EXERCISE-LIBRARY](19-EXERCISE-LIBRARY.md) §3.

---

## 3. Time Attack — 60 seconds

**Rules:** 60-second timer. Maximum total damage. Boss has no HP cap — it is a damage sponge with a
score readout. Combo carries. Fatigue still applies, so pacing matters: go out too hard and your
velocity decay costs you the back half.

**Why it earns its place, and this is the important part:** a full boss fight runs 2–4 minutes.
**At Eval R2 judges rotate between tables and may not give you four minutes.** Time Attack is
exactly the length of a judge interaction. It is a product decision that happens to be a demo
decision.

It is also **the best duel format** — two players, 60 seconds, highest damage wins. Bounded,
timeboxed, no risk of a duel that drags while a jury waits.

**Cost:** a timer, a score readout, and a config flag. No new perception, no new art.

---

## 4. Ghost Race — the single best addition

**Rules:** race a recorded rep timeline. Your past self, a teammate's set, or a pre-recorded
"bronze / silver / gold" pacer shipped in config. Two HP bars drain side by side on one boss.

**Why it is the highest-value thing in this document:**

| It solves | How |
|---|---|
| **The duel needs two people** | A ghost is always available. No opponent, no pairing, no radio. |
| **The duel might not pair on the day** | Ghost Race is the instant fallback and it looks *identical* on screen — two bars, one boss. A judge cannot tell it is not live unless you say so, and you will say so. |
| **Retention after the hackathon** | The cheapest retention mechanic in fitness apps. Beat yesterday's you. |
| **Solo demo** | If Ujjwal is talking to a mentor, you can still demo head-to-head. |

**Implementation is trivial because of the existing architecture.** A ghost is just a replay of
recorded `RepEvent`s fed into `CombatEngine.onRemoteDamage()` on a timer — the *exact same code path
the duel already uses*. You are not building a feature, you are pointing an existing pipe at a file.

Ship pre-recorded pacer ghosts in `config/ghosts/` so a fresh install has something to race
immediately.

---

## 5. Tempo Trial

**Rules:** the boss sets a target eccentric tempo (e.g. 3 seconds down). Damage scales with how
closely you match it:

```
tempoAccuracy = clamp(1 − |t_ecc − t_target| / t_target, 0, 1)
damage        = base · (0.3 + 0.7 · tempoAccuracy) · comboMultiplier
```

Metronome audio drives it, so it works entirely eyes-free — which is a good showcase of the audio
design.

**Why it is interesting:** it demonstrates that the engine measures *how* you move, not just *that*
you moved. It reframes the form scoring in a way the main mode does not.

**Condition:** only build this if the tempo sub-score has proven reliable in testing. If tempo is
noisy, this mode makes the noise the entire experience.

---

## 6. Survival

**Rules:** endless waves. Each wave adds HP and tightens the form threshold for full damage. Score
is waves cleared. Ends when you stop.

**Fatigue interaction:** the mercy rule is *disabled* here — this is the one mode where fatigue
genuinely ends the run, and that is the point. It is the leaderboard mode.

**Cost:** a wave counter and a config table. Perhaps 30 minutes.

---

## 7. Boss Rush

Three bosses back to back, one health pool each, no rest between. Pure config data — the same key
art recoloured with different phase modifiers and HP. Twenty minutes of work for a visibly fuller
mode screen.

Only do this if the boss art supports recolouring cleanly. Do not commission new art for it.

---

## 8. Arena Idle — ambient presence

Not a mode. A home-screen state: the boss idles, and when the camera detects a person entering the
frame it reacts — turns, powers up, taunts. Tap to fight.

**Why it belongs here:** it is a genuine product touch (the phone is propped in your room; it should
feel alive), and it is a legitimate reason for the camera to be running outside a fight.

**One honest caveat.** Creative phone use is 15% of the score and HackTracker measures camera
duration. Building a real feature that happens to use the camera is fine. **Idling the camera purely
to inflate a telemetry number is gaming a metric**, and if it is noticed it is worse than the points
are worth. Build Arena Idle because it makes the product better; take the telemetry as a side
effect, not as the reason.

---

## 9. Mode select screen

One screen, six tiles. Locked modes show as locked rather than hidden — a visibly larger product.

```
┌─────────────┬─────────────┐
│ BOSS FIGHT  │ TIME ATTACK │
├─────────────┼─────────────┤
│ GHOST RACE  │    DUEL     │
├─────────────┼─────────────┤
│  SURVIVAL   │ TEMPO TRIAL │
└─────────────┴─────────────┘
```

**Show this screen to judges even if you only demo one mode.** It takes four seconds and it changes
what the product looks like.

---

## 10. Where each mode fits the pitch

| Mode | Use it for |
|---|---|
| Time Attack | **Eval R2 at the table** — 60 seconds fits a rotating judge |
| Duel (Time Attack rules) | **The Top 10 stage pitch** — judge as player two, bounded at 60s |
| Ghost Race | **The instant fallback** when pairing fails, and the retention slide |
| Boss Fight | The full experience, if a judge gives you four minutes |
| Survival | One line on the roadmap slide. Do not demo it. |

---

## 11. Exercises — superseded

An earlier draft of this section argued against adding exercises. **That position was overruled on
21 August, and the reasoning behind the override is better than the original position.**

The original objection was that each exercise costs a new state machine. That is only true if
exercises are code. [19-EXERCISE-LIBRARY](19-EXERCISE-LIBRARY.md) makes them **config data** behind
five generic detectors — so the marginal cost of the 21st `REP_CYCLE` exercise is a JSON record and
a test fixture, not a rewrite.

The full library — 61 exercises across strength, isometrics, yoga, cardio and ballistics — is
specified there, along with the family-specific games.

**What survives from the original position, and still holds:**
- **Squats carry both judging rounds.** Eval R2 is judged at tables, where there is no floor space.
- **Demo Time Attack on squats.** Everything else is depth, not demo.
- **Do not ship a menu of 61 entries where 53 are dead.** Ship what works; put the taxonomy on a
  slide.
