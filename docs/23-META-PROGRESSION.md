# 23 · Meta-Progression, Economy and Retention

XP, levels and achievements are shipped. Seasons and energy budget remain roadmap. This is where
the "would someone keep using it" answer begins.

---

## Shipped: XP, Levels, Achievements and Weekly Challenges

### XP and Levels

Reps earn XP based on form quality: 4 base + 8 × formMean per rep (casual sessions earn half).
Damage dealt, personal bests set, streak milestones and weekly challenge completion each award
bonus XP. Cumulative XP determines level via `100 × (L − 1)^1.5` for level L ≥ 2.

| Level | XP to reach | Title |
|---|---|---|
| 1 | 0 | Recruit |
| 3 | 200 | Contender |
| 5 | 632 | Brawler |
| 8 | 1,414 | Fighter |
| 12 | 2,969 | Warrior |
| 16 | 5,239 | Gladiator |
| 20 | 8,242 | Champion |
| 30 | 24,975 | Titan |
| 40 | 59,129 | Legend |

### Achievements (22 badges)

Bronze, Silver and Gold tiers across four categories:

**Fights**: First Blood (1 session), Committed (10), Obsessed (50) · **Wins**: Victory (first boss win)
**Streaks**: Week Strong (7 days), Unstoppable (30 days) · **Form**: Precision (100 clean reps), Master (1000)
**Damage**: Powerful (10k total), Legendary Power (100k) · **Versatility**: Versatile (5 families)
**Personal Bests**: Progressing (5 PBs) · **Weekly Challenges**: Challenge Accepted (first weekly challenge)
**Modes**: Competitive (first versus), Team Player (first raid), Calibrated (first clinic)
**Levels**: Fighter (level 10), Titan (level 25)

### Weekly Challenges

A single challenge rotates per ISO week (e.g. "2026-W36"), same for all players. Eleven possible
challenges over damage, clean reps, sessions and streak days, each with three difficulty tiers:

| Metric | Target 1 | Target 2 | Target 3 |
|---|---|---|---|
| Damage | 3000 (Damage Dealer) | 5000 (Heavy Hitter) | 8000 (Demolition) |
| Clean Reps | 150 (Clean Form) | 250 (Pristine) | 400 (Flawless) |
| Sessions | 3 (Consistency) | 5 (Dedication) | — |
| Streak Days | 3 (Building Momentum) | 5 (Unstoppable) | 7 (Perfect Week) |

Completing a weekly challenge grants 100 XP bonus.

---

## 1. The three loops

| Loop | Length | Job |
|---|---|---|
| **Session** | 3–10 min | Feel good right now. Reps → damage → boss dies. |
| **Day** | ~24 h | Give a reason to open it. A daily prescription, an energy budget, a crew objective. |
| **Season** | 6–8 weeks | Give a reason to still be here in two months. A campaign with an ending. |

Most fitness apps build only the first and wonder why day-7 retention is around 10%.

---

## 2. Session loop

Fight → fatigue → coach → rest → fight. Specified in [04-GAME-DESIGN](04-GAME-DESIGN.md).

The one design rule that governs it: **every session must end in a resolution.** The fatigue mercy
rule guarantees a victory screen for anyone who genuinely tired out. Nobody closes the app on a
failure they did not choose.

---

## 3. Day loop

- **Energy budget** from sleep. Restricts content; never scolds.
- **The Daily** — one prescribed sequence, roughly 6 minutes, mixing families. Missing it costs
  nothing. Completing it advances the season.
- **Crew objective** — a shared target your crew is chipping at.
- **Readiness gauge** from resting-HR trend decides whether today offers a hard boss or a mobility
  session. **The app choosing an easier day for you is a feature**, and it is why people trust it.

---

## 4. Season loop

A **campaign map** of 30–40 nodes, each a fight, hold, asana or cardio drill. Boss nodes gate
regions. Six to eight weeks, then it resets with a new theme and your stats carry.

Seasons work because they have an **ending**. An infinite treadmill has no natural re-entry point;
a season gives lapsed users a clean place to come back to, which is when most re-engagement
actually happens.

---

## 5. Economy

**One currency: Effort.** Earned from quality, not quantity — a clean rep is worth more than a
sloppy one, a held plank more than a broken one.

Spent on: cosmetic unlocks, boss skins, new campaign regions, crew banners.

**Three rules:**
1. **Nothing that affects difficulty is purchasable.** No pay-to-win, no grind-to-win. The game must
   never be easier for someone who played more.
2. **Cosmetics unlock on consistency, not performance.** Show up eleven days in a row and you get
   the thing — whether you are strong or not. This is what keeps a beginner in the product.
3. **No real money.** Not this product, not this pitch.

---

## 6. Streaks, done correctly

The single most common way fitness apps lose users is a punitive streak.

| Rule | Why |
|---|---|
| **One protected rest day per week**, automatic | Rest is training. Breaking a 40-day streak because someone had flu is a product choosing to lose a user. |
| **Streak freeze** earned every 10 days, up to 3 | Life happens. |
| **Rest days score positively** — they grow RESILIENCE | The most contrarian and most correct decision in the design. |
| **A broken streak never shows a number you lost** | "Back at it" — never "you lost 41 days." |

---

## 7. Social retention

- **Leaderboards** (shipped, Firestore-backed): global (this week's damage, this week's clean reps,
  all-time XP, longest streak) plus friends leaderboards. Built with friend codes.
- **Weekly Challenge** (shipped): same for every player that ISO week, rotates deterministically.
- **Friend codes** (shipped): 6-character codes to add friends without email.
- **Ghost sharing** — a ghost is a file. Send it to anyone; they race it. No server.
- **Challenge cards** — a QR encoding exercise, mode, target and ghost. Scan, attempt, beat.
- **Raid nights** — a standing time when a crew fights one boss together in a room.

**"Multiplayer without a server, scores in the cloud"** — leaderboards are Firestore-backed
(email + password accounts), but the match engine runs on-device and Nearby Connections handles
peer-to-peer play. No frame of video or pose data ever reaches the cloud.

---

## 8. Notifications

At most one per day, and it must be **specific**:

> ✅ "Your crew is 400 damage from the weekly boss."
> ✅ "Yesterday's set had your best depth in two weeks. Beat it?"
> ❌ "Don't forget to work out today!"

A generic reminder is the notification people turn off, and once they turn it off it never comes
back.

---

## 9. What this is worth in the pitch

Retention is where "would someone keep using it" (30%) is actually decided, and almost every
hackathon fitness demo has no answer at all.

**Four sentences on slide 8:**

> "Seasons give a reason to be here in two months. Ghosts and crews make it social with no server,
> which keeps the privacy claim intact. Rest days *earn* points, because punishing rest is how
> fitness apps lose people. And the day's difficulty is set by your recovery, not by a plan you
> wrote when you were fresh."

None of this shipped in 30 hours. **Say that.** The scope honesty is worth more than the feature
list.
