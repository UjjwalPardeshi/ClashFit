# 04 · Game Design

All numbers live in `config/combat.json` and are tuned on-device during Red Light. Nothing here is
compiled in.

---

## 1. Design goals

1. **A clean rep must feel meaningfully better than a sloppy one** — through numbers, sound and
   colour simultaneously.
2. **A fight must be winnable by anyone.** The target is 25–45 reps for a fresh beginner, and the
   fight resolves early if they fatigue first.
3. **Never a fail state for being tired.** The boss adapts; the player always gets an ending.
4. **A first fight lasts 2–4 minutes.** Long enough to feel like a fight, short enough to demo
   twice in a judging slot.

---

## 2. Damage

```
base        = 100
formFactor  = 0.35 + 0.65 · formScore^1.2
damage      = round( base · formFactor · comboMultiplier · phaseModifier )
```

**A bad rep still does 35%.** Zero damage for effort is punishing and teaches nothing; a visible
gap teaches everything. The exponent 1.2 makes excellence slightly superlinear, so the difference
between 0.8 and 0.95 is felt.

Worked examples at combo ×1, phase ×1. **These are computed, not estimated** — they are
asserted in the prototype test suite (`prototype/test/run.js`, "damage curve is monotonic and
bounded"), so the deck can cite them safely.

| formScore | Verdict | Damage |
|---|---|---|
| 0.00 | SHALLOW | 35 |
| 0.30 | SHALLOW | 50 |
| 0.55 | OK | 67 |
| 0.80 | CLEAN | 85 |
| 0.95 | CLEAN | 96 |
| 1.00 | CLEAN | 100 |

---

## 3. Combo

Consecutive reps with `formScore ≥ 0.75` build a streak.

```
comboMultiplier = min( 1.0 + 0.12 · (streak − 1), 2.5 )
```

| Streak | Multiplier |
|---|---|
| 1 | ×1.00 |
| 3 | ×1.24 |
| 6 | ×1.60 |
| 10 | ×2.08 |
| 14+ | ×2.50 (cap) |

**Break rule:** one rep below 0.75 drops the streak to 0 — but only *after* a one-rep grace at
streak ≥ 6. Losing a ten-rep streak to a single tired rep feels unjust, and the grace costs
nothing.

**The audio design carries this.** Rep impact pitch rises with the multiplier, so the player hears
the streak building without looking at the screen. This is the single cheapest piece of game feel
in the project.

---

## 4. Boss

One boss ships. Design it properly rather than shipping three shallow ones.

**THE PACEMAKER** — a mechanical construct that mirrors the player's tempo. Thematically it *is*
the fatigue system, which makes the novelty legible rather than hidden in a meter.

| | |
|---|---|
| Max HP | 3000 (config) — roughly 30 clean reps at ×1, fewer with combo |
| Phase 1 | 100–60% HP. Baseline behaviour. |
| Phase 2 | 60–25% HP. Enrage: demands tempo, `phaseModifier` 0.9 (its armour hardens). Colour shift, one screen shake. |
| Phase 3 | 25–0%. Desperation: `phaseModifier` 1.15, HP bar pulses. The finish should feel fast. |
| Death | 1.2s sequence, then VICTORY. |

Idle animation is a 3-second breathing loop so the screen is never dead between reps.

---

## 5. Fatigue-adaptive behaviour — the mechanic that makes us different

`BossController` consumes `FatigueState` (see `05-POSE-ENGINE-SPEC.md` §6) and changes the fight in
real time.

| Band | Boss behaviour | What the player experiences |
|---|---|---|
| `FRESH` | `phaseModifier` 0.92; boss regenerates 8 HP per rep-interval | "It's shrugging this off — go harder." |
| `WORKING` | Neutral. No modifier, no regen. | The normal fight. |
| `FADING` | Boss **staggers**: `phaseModifier` 1.20 for the next 5 reps. Offers a rest that grants a damage shield rather than costing progress. | "It's opening up. Push." |
| `GASSED` | **Mercy resolution.** Remaining HP is recomputed so the fight ends in `n` more reps (default 4). A finisher prompt appears. | "One last push and it's done." |

```
onFatigueBand(GASSED):
    remainingHp = min(remainingHp, 4 · expectedDamagePerRep)
```

**This is the line for the pitch:** *the boss cannot outlast you, but it makes you earn the
ending.* It is novel, it is demonstrable in 40 seconds in front of a judge, and it solves a real
product problem — the frustration spiral that makes beginners quit fitness games.

It is also the safest possible demo behaviour: a judge who gets tired mid-demo still reaches a
victory screen.

---

## 6. Rep targets and session shape

There is no fixed rep target. The set ends when the boss dies, when the player asks for it with a
thumb up to the camera, or when they walk away — never on a timer, and never because they stood
still. Rep count is an outcome, not a quota — which is what makes the fatigue system meaningful
rather than cosmetic.

**Session:** one boss, fought across as many sets as it takes, with no rest between them — the
next set starts the moment the last one closes. Typical first session: 3 sets, 8–15 reps each,
3–4 minutes total.

---

## 7. Duel scoring

Two players, one shared boss. Each phone scores its own reps locally and broadcasts damage events.

```
bossHp = maxHp − Σ damage over the deduplicated union of all events
```

Winner = higher total damage contributed, **not** higher rep count. A player doing 12 clean reps
beats one doing 18 sloppy ones. This is the entire point of the product and the duel makes it
visible in a way single-player never can — say this when demoing.

**Casual mode** (for the judge who is player two): `base` damage ×1.6, `formFactor` floor raised to
0.6, boss max HP ×0.5. Someone in office trousers who has not squatted since school still gets to
land satisfying hits and see a victory screen.

---

## 8. Progression — deck only, not built

| Mechanic | Note |
|---|---|
| Boss ladder (5 bosses, escalating mechanics) | Roadmap. One boss ships. |
| Ghost races against your own recorded set | Cheapest retention mechanic we have. First thing to build post-event. |
| Daily city boss with a shared damage pool | Ties directly to iQOO's city-battle framing. Strong deck slide. |
| Forgiving streaks (one rest day per week) | Retention. Punitive streaks drive churn. |
| Cosmetic unlocks tied to *consistency*, not performance | So a less fit player still progresses. |

---

## 9. The boss hits back: Player HP and attacks

The boss deals periodic damage to the player, creating risk and urgency. Player HP defaults to off
(disabled in TIME_ATTACK, DUEL, and games with family game active).

```
playerMaxHp   = 100
everySec      = 5.0
damage        = 8
healPerRep    = 3
graceSec      = 4.0
modes         = ["BOSS_FIGHT", "BOSS_RUSH", "SURVIVAL"]
```

**Each rep heals by `healPerRep`.** A player maintains or gains HP by hitting reps. Missing reps or
taking too long between them costs HP.

**Attack damage scales by phase.** Each boss phase has an `attackModifier` (default 1.0) that scales
the base damage, making later phases more dangerous: phase 2 enrage ×1.25, phase 3 desperation ×1.5.

**The mercy rule:** When the player reaches GASSED (out of breath), the boss holds its attacks until
they complete `mercyRepsToFinish` reps. This prevents an exhausted player from being pummeled while
they have no stamina. Once they hit the target reps, they either recover or the run ends.

**Timing:**

- **Grace period:** First attack arrives `graceSec` after the fight starts (default 4s), giving the
  player time to land initial reps and build momentum.
- **Resume:** When a set ends and the next begins, attacks re-arm at the same grace interval.
- **Framing loss:** When the player steps out of frame (FRAMING_LOST), attacks are suppressed. When
  framing returns, attacks resume with 1500ms delay, preventing spam during frame transitions.

**Session end:** If player HP reaches 0, the session ends with EndReason.DEFEATED. Reps are still
banked and count toward progression, but the "won" flag is false for meta-tracking (analytics,
leaderboards).

---

## 10. Balance testing plan

Tune these on-device during Red Light, using the config file, with real reps:

1. A fresh, unfit person should kill the boss in 25–45 reps across 2–4 sets.
2. Clean-versus-shallow damage difference must be obvious to a bystander who was not told about it.
3. Combo should reach ×2 within a realistic 9-rep set.
4. `GASSED` mercy must trigger before the player physically quits — this is the one that matters
   most for the demo, and the one most likely to be mistuned.
5. A full fight must be completable in under 4 minutes from app launch.
