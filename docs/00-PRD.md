# 00 · Product Requirements Document

**Product:** ClashFit — Offline AI Fitness Combat
**Status:** Pre-build. Locked for the Pune City Battle unless an ADR supersedes.
**Owners:** Omkar (Android + perception), Ujjwal (game systems + inference)

---

## 1. Problem

Two failures, and the gap between them:

**Trackers are inert.** You log a workout, you see a graph, nothing pulls you back tomorrow.
Motivation is entirely externalised to the user's willpower.

**Fitness games are disconnected from effort.** Movement is a rough input signal — did you wave,
did you jump — with no notion of whether the movement was *correct*. Doing a bad squat and a good
squat produce the same outcome, so the game teaches nothing and can actively reinforce injury-prone
patterns.

**And every camera-based product in this space uploads the footage.** A camera pointed at you in
your bedroom, mid-workout, half-dressed, is the most intimate sensor stream a consumer product
ever asks for. Every competitor streams it to a server for inference. That is a real objection,
not a hypothetical one, and it is the reason a large share of people never try these products.

## 2. Thesis

> Real, camera-verified physical effort is the input. A boss fight is the output. Nothing leaves
> the device.

The specific claim we are making that nobody else makes: **the system reads fatigue from your
movement and the difficulty responds to it.** Not a preset difficulty curve, not a self-reported
RPE slider — measured velocity decay, collapsing range of motion, and tempo drift, computed
per-rep on-device.

## 3. Users

| Persona | Situation | What ClashFit gives them |
|---|---|---|
| **The lapsed starter** (primary) | Downloaded three fitness apps, quit all three inside two weeks. Owns no equipment. Trains at home or not at all. | A reason to do the next set that isn't discipline. Progression that survives a missed day. |
| **The form-anxious beginner** | Wants to train but is genuinely unsure whether they are doing it right, and won't go to a gym to find out. | A referee that tells them, per rep, what was wrong — without a human watching. |
| **The privacy-refuser** | Would use a camera coach but will not upload video of themselves. | Airplane mode on, everything works. |

Not our user this weekend: athletes optimising a program, gym members with a trainer, anyone
needing equipment-based lifts.

## 4. Product principles

1. **The referee is honest.** A bad rep must visibly do less. If the scoring can be cheated by
   flailing, the product is a toy.
2. **Never punish fatigue.** Getting tired is the point of exercise. The system responds to
   fatigue by changing the fight, never by shaming the player.
3. **Nothing leaves the device.** No account, no upload, no network requirement. Ever. This is a
   product constraint, not an implementation detail.
4. **Eyes-free must work.** During a push-up your face is 30cm from the floor. If the product only
   communicates visually, it does not work at all.
5. **Every rep gets feedback inside 100ms.** Latency between the movement and the hit is the entire
   feel of the product.

## 5. Scope — the weekend

### Must ship (Tier 0) — judgeable by Sat 19:00
- Squat detection: rep counting with hysteresis, per-rep form score
- One boss: HP bar, damage on rep, hit feedback, death state
- Calibration flow that gets a user framed correctly in under 30 seconds
- Audio feedback per rep (works eyes-free)

### Should ship (Tier 1) — the differentiator. Two parallel lanes, Saturday night into Sunday.

| Lane | Owner | Work |
|---|---|---|
| Perception | Omkar | **Fatigue detection** and fatigue-adaptive boss behaviour → then **duel transport** and event-sourced sync |
| Inference + UI | Ujjwal | **Gemma 3n on-device coach** — coaching line + boss taunt, spoken via TTS → then duel lobby and opponent UI |

- **Two-phone duel** over offline peer-to-peer is **Tier 1**, not a stretch. Team decision, 21 Aug.
- Both lanes must clear their first item before either starts the duel. Fatigue and the coach are
  the novelty; the duel is the demo.

### Could ship (Tier 2) — only if genuinely ahead by Sun 06:30
- **Game modes: Time Attack, Ghost Race, Survival, Boss Rush.** All reuse the `RepEvent` stream —
  `combat`/`ui` work, no perception cost. Ghost Race doubles as the duel fallback.
- **6–8 `REP_CYCLE` exercises via config** — squat, chair squat, push-up, knee push-up, sit-up,
  glute bridge, lunge, calf raise.
- **Push-ups specifically are demoted** from Tier 1: Eval R2 is judged *at tables*, where there is
  no floor space, so squats carry both judging rounds regardless.
- Combo system polish and audio pitch-shifting
- Session summary card with the fatigue curve
- Additional duel transports beyond hotspot sockets

### Tier 3 — only if the weekend goes unusually well
- `IsometricHoldDetector` (~1.5h) and the **Siege** game, unlocking 9 holds
- Tempo Trial

### The product beyond the weekend — [19-EXERCISE-LIBRARY](19-EXERCISE-LIBRARY.md)
Five detector families, 61 exercises across strength, isometrics, yoga, cardio and ballistics, each
family with a game shaped like the movement (Siege, Sigil, Pursuit, Breaker), one shared fatigue
model, progression ladders, and exercises defined as config rather than code. **This is the answer
to "would someone keep using it" — and it belongs on the deck, not in the weekend build.**

### Will not ship — roadmap only, slide 8 of the deck
Guilds and raids · step-based city events and Health Connect · cloud leaderboards · skins and
cosmetics · avatar progression · exercise library beyond squats and push-ups · AR overlay ·
injury-aware routing · account system

**Rationale for the cuts:** the loaner phone is handed over at 08:00 Saturday, so any feature
depending on accumulated history (steps, streaks, Health Connect) demos as an empty screen. Cloud
features contradict principle 3 and add venue-network risk. Cosmetics need an art pipeline we
cannot feed in 19 hours.

## 6. Success criteria

**For the weekend:**
- A judge who has never seen the app performs a set and the rep count is correct — verified by
  a human counting out loud alongside
- A deliberately shallow rep visibly deals less damage, and the judge notices without being told
- The app runs a full fight with the device in airplane mode
- The pitch lands inside 4 minutes with a live demo that does not fail

**For the product (post-event, for the deck):**
- Day-7 retention above 25% (fitness app median is ~10%)
- Median session ≥ 2 sets
- ≥60% of sessions completed offline

## 7. Feature roadmap — the retention story

These are *not* built this weekend. They exist to answer "would someone keep using it" (30% of
the rubric) and they belong in the deck.

| Feature | Why it drives usage |
|---|---|
| **Ghost races** | Race a recorded rep-timeline of your past self. Head-to-head feel, zero networking, no opponent needed. Cheapest retention mechanic available to us. |
| **Daily city boss** | One shared boss per day per city; every player's damage contributes. Ties directly to iQOO's own city-battle framing. |
| **Forgiving streaks** | A streak that survives one rest day per week. Punitive streaks are the single biggest cause of churn in fitness apps. |
| **Form report card** | Post-session shareable image: depth trend, fatigue curve, best rep. A viral loop that costs one render. |
| **Accessibility ladders** | Chair squats, knee push-ups, wall push-ups as first-class progression steps rather than "easy mode". Expands the addressable user base and is the honest thing to do. |
| **Voice-only mode** | Screen off, TTS coach only. Turns the phone into an audio trainer and makes the product usable when it can't see the screen anyway. |
| **Local co-op raid** | Three-plus phones in a room against one boss. Gyms, hostels, offices. Natural spread vector. |

## 8. Non-goals

- Medical or diagnostic claims. We say "form score", never "injury risk assessment".
- Calorie estimation. Unvalidatable from pose alone; claiming it damages credibility.
- Replacing a trainer.
- Any weight-loss or body-composition framing. The product is about effort and consistency.

## 9. What we tell the jury when challenged

| Challenge | Answer |
|---|---|
| "Isn't this Ring Fit / Kinect?" | Yes, those exist and we name them on slide 2. Neither reads fatigue, neither runs a language model on-device, neither works offline with no console. |
| "How do you know the form score is right?" | We do not claim to measure "good form". We measure three geometric quantities — depth against a calibrated baseline, range of motion, and eccentric tempo — and we say exactly that. |
| "Why does this need to be on-device?" | Because it is a camera pointed at a person in their bedroom. Also: 30fps inference over a network is not possible, and our users are frequently offline. |
| "Why a phone and not a console?" | Everyone already owns the sensor. |
