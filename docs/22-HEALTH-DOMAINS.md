# 22 · Health Domains — Everything, Gamified

**The ask:** everything related to health and fitness, gamified.

**The structure that makes it coherent** rather than a pile of features: every health domain feeds
**one character sheet**. Exercise is your damage. Nutrition is your gear. Sleep is your energy pool.
Breathing is your shield. Mobility is your movement. Consistency is the world itself.

That is how role-playing games have always worked, and it is why this maps so cleanly: **a person's
health genuinely is a set of interacting resources.** The game does not gamify health by bolting
points onto it — it models it.

> **Weekend reality, stated once and meant:** a loaner phone handed over at 08:00 Saturday has no
> history. Nutrition, sleep, habits and streaks all demo as empty screens. **None of this ships.**
> It is the product vision, it belongs on the deck, and it is the strongest answer we have to
> "would someone keep using it" — which is 30% of the score. See §9 for the warning that goes with
> that.

---

## 1. The character sheet

| Stat | Fed by | Effect in play |
|---|---|---|
| **POWER** | Strength exercise quality | Base damage |
| **STAMINA** | Cardio cadence and endurance | How long before fatigue bands escalate |
| **FOCUS** | Breathing and mindfulness sessions | Reduces boss critical damage; steadies the tremor threshold |
| **MOBILITY** | Stretching, yoga accuracy, range-of-motion tests | Unlocks dodges and higher-difficulty rungs |
| **ENERGY** | Sleep duration and regularity | Daily action pool. Poor sleep means fewer fights, not a lecture. |
| **NOURISHMENT** | Meals and hydration logged | Temporary buffs; protein → damage, hydration → stamina regen |
| **RESILIENCE** | Consistency and recovery days taken | Passive: softens the penalty for a missed day |

One screen. Seven bars. Every health behaviour visibly moves one of them.

---

## 2. Movement — shipped

Covered in [19-EXERCISE-LIBRARY](19-EXERCISE-LIBRARY.md): 5 detector families, 61 exercises,
family-shaped games, one fatigue model. Feeds **POWER**, **STAMINA** and **MOBILITY**.

---

## 3. Breathing and mindfulness — cheaper than it looks

**Sensing:** we already run pose at 30fps. Shoulder-Y oscillation *is* a breathing signal, and the
`CADENCE` detector already extracts periodic motion from a landmark axis. **Breathing rate is
almost free** — it is an existing detector pointed at a different landmark.

| Session | Mechanic | Stat |
|---|---|---|
| **Box breathing** | 4-4-4-4, paced by haptics so it works with eyes closed | FOCUS |
| **Wind-down** | Extended exhale, 4-in 8-out | FOCUS, ENERGY |
| **Between-set recovery** | Breathe to accelerate fatigue-band recovery — *this one is a real in-fight mechanic, not a side activity* | Reduces fatigue |
| **Mood check-in** | Two taps, no camera | RESILIENCE |

**The gamification:** FOCUS is a shield. Breathing before a fight grants damage reduction; breathing
*during* rest recovers a fatigue band faster. Mindfulness becomes mechanically useful rather than a
virtue tab nobody opens.

**Sensor note:** heart-rate estimate via rPPG (§7) makes a breathing session measurable — HR
descending during the exhale phase is visible and satisfying feedback.

---

## 4. Nutrition — the multimodal model earns its place

**Sensing:** photograph the plate. **Gemma 3n takes image input**, identifies foods on-device, and
the player confirms or corrects. The photo never leaves the phone.

This is where the privacy thesis compounds: *photos of your body and photos of your food, both
processed on the device, neither uploaded.* One coherent claim covering the two most sensitive data
types a health app touches.

| Feature | Gamified as |
|---|---|
| Meal log | **Consumables.** Protein → damage buff; carbs → stamina; vegetables → sustained regen. |
| Hydration | **Stamina regeneration rate.** A visible water bar that drains through the day. |
| Meal timing | **Pre-fight buff window** — eating well before a session grants a temporary bonus |
| Weekly pattern | **NOURISHMENT** trend, shown as a curve, never as a score out of ten |

**Two rules, and they are not negotiable:**
1. **No calorie numbers presented as fact.** Portion estimation from a single photo is
   unreliable, and precise-looking calorie counts are a credibility trap. Log foods and patterns,
   show trends, never a hard number.
2. **No weight, body-composition, or appearance framing anywhere.** This is already
   [00-PRD](00-PRD.md) §8 and it holds across every domain.

---

## 5. Sleep — the energy economy

**Sensing:** microphone plus accelerometer while charging overnight gives movement and audio-based
sleep-phase estimation. Manual entry as the fallback. Health Connect aggregation in the real
product.

| Signal | Gamified as |
|---|---|
| Duration | **ENERGY pool** — your daily action budget |
| Regularity (consistent times) | **RESILIENCE** — irregular sleep decays it |
| Wind-down adherence | Morning buff |

**The design rule that matters:** low energy **restricts content, it never punishes**. A badly slept
player gets shorter fights and a gentler boss — not a red screen telling them they failed. The
system must never make a tired person feel worse; that is how health apps get deleted.

---

## 6. Posture and mobility

**Sensing:** the camera already does this. A 20-second posture check from a standing side view gives
forward head carriage, shoulder rounding and pelvic tilt as angles.

| Feature | Gamified as |
|---|---|
| Desk posture check | Clears the **"Slouched"** debuff for the day |
| Range-of-motion test | Monthly **MOBILITY** benchmark — sit-and-reach, shoulder flexion, hip rotation, ankle dorsiflexion |
| Stretch routines | **MOBILITY** gains, which unlock higher rungs on the ladders |
| Yoga (`POSE_MATCH`) | **SIGIL** mode — see [19-EXERCISE-LIBRARY](19-EXERCISE-LIBRARY.md) §3 |

Mobility gating progression is honest game design *and* honest training advice: you should not be
loading a movement you cannot reach the bottom of.

---

## 7. Vitals and recovery

**Sensing:** **rPPG** — remote photoplethysmography. Subtle colour changes in facial skin, captured
by the front camera, yield a heart-rate estimate. Requires a still face, decent lighting, and ~20
seconds.

| Feature | Gamified as |
|---|---|
| Resting HR trend | **Readiness gauge** — determines which content the day offers |
| HR recovery after a set | **RESILIENCE** growth. Recovery rate is a genuine fitness marker. |
| HR during breathing | Live feedback that makes a breathing session feel real |

**Say "estimate", never a medical claim.** Accuracy varies with lighting and skin tone. Frame it as
a trend indicator, show a confidence state, and never present it as a diagnostic. A judge asking
about accuracy should get "it is an estimate, here is its confidence, and we never use it for
anything clinical" — not a defence of the number.

---

## 8. Consistency — the world layer

The meta-progression that makes all of the above matter is specified in
[23-META-PROGRESSION](23-META-PROGRESSION.md). Briefly:

- **Forgiving streaks** — one rest day per week is protected. Punitive streaks are a leading cause
  of churn in fitness apps, and breaking someone's 40-day streak because they had flu is a product
  choosing to lose a user.
- **Rest days are scored positively.** Taking one grows RESILIENCE. This is the single most
  contrarian and most correct design decision in the product.
- **The world map lights up** with consistency, so the reward for showing up is visible and
  cumulative rather than a number incrementing.

---

## 9. The warning that comes with all of this

**Everything in this document is roadmap.** It belongs on deck slide 8 and in the answer to "would
someone keep using it".

**It must not appear in the Phase-1 description as weekend scope.** *"Idea and scope fit for 30
hours"* is an explicit shortlisting criterion. A submission promising nutrition, sleep, vitals and
a seven-stat RPG reads as a team that has not done the arithmetic, and it gets cut before anyone
sees the demo.

**The correct framing, in both the submission and the pitch:**

> "We are building a gamified health platform. This weekend we shipped the hardest part of it — the
> movement engine and the fatigue model — because everything else is data entry and that is not
> where the risk is."

That sentence gets you the vision credit *and* the scope credit. Claiming to have built the platform
gets you neither.
