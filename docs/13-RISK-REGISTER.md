# 13 · Risk Register

Ranked by expected damage. Each risk has a mitigation you can execute and, where relevant, a kill
switch — the thing you do when mitigation has failed and the clock is running.

---

## Tier 1 — can lose the weekend

### R1 · Pose detection fails in the venue
**Likelihood: high. Impact: total.**
A hackathon hall has bad mixed lighting, cluttered backgrounds, and constant human movement behind
you. MediaPipe will latch onto the wrong person or produce noisy landmarks.

*Mitigation:* lock onto the pose nearest frame centre at fight start and re-acquire only after 2s of
loss. Raise `minPoseDetectionConfidence` rather than accepting garbage. **Test in a crowded moving
environment before the event** — a mall or a co-working floor, not your living room.
*Kill switch:* Arena Mode (rear ultra-wide, wider FOV, phone lower and closer to the floor), then
recorded-trace replay for the demo.

### R2 · Front camera FOV can't frame a standing squat
**Likelihood: medium-high. Impact: high.**
A propped phone with a front camera may need 2.5m+ to see hips through ankles. Judging tables do not
have 2.5m.

*Mitigation:* build **Arena Mode from the start**, not as a patch — rear ultra-wide plus Office Kit
mirror. Calibration gives explicit distance guidance. Measure the actual required distance on the
loaner in the first hour.
*Kill switch:* chair squats as the demo exercise — smaller vertical extent, works closer.

### R3 · Live demo fails in front of judges
**Likelihood: medium. Impact: high.**
Three separate demos across a 30-hour sleep-deprived window.

*Mitigation:* golden APK on both phones from 19:00 Saturday. Full failure playbook rehearsed
(`11-DEMO-SCRIPT.md` §7). Recorded trace replay available as a camera-free path.
*Kill switch:* recorded demo video + SUMMARY screen as evidence, narrated honestly.

### R4 · Both of you sleep through the biggest Green window
**Likelihood: medium. Impact: high.**
Sunday 01:00–06:30 is 5.5 hours of laptop time — more than any other block — and it is exactly when
a two-person team wants to sleep.

*Mitigation:* staggered sleep written into the runbook. Alarms set on both phones on Saturday
morning, not decided at 01:00.
*Kill switch:* if both crash, the 06:30 feature freeze holds anyway. Ship Tier 1 without duel.

---

## Tier 2 — can lose a feature

### R5 · Gemma too slow, too hot, or won't load
**Likelihood: medium. Impact: medium.**
2–3 GB model, GPU delegate, sharing thermal headroom with continuous camera inference.

*Mitigation:* never concurrent with the camera loop. 25s load timeout at splash, 5s generation
timeout. Template fallback built **first**, and built well enough to ship alone.
*Kill switch:* templates only. **Do not mention the LLM in the pitch if it did not ship.** An
unshipped feature described as shipped is unrecoverable if a judge asks to see it.

### R6 · Duel won't pair in an RF-hostile hall
**Likelihood: medium-high. Impact: medium-high — raised, the duel is now Tier 1.**
Hundreds of contested radios. Nearby Connections discovery is exactly where this bites.

*Mitigation:* hotspot + sockets as primary, not Nearby. Transport behind an interface so it can be
swapped in ten minutes on site. Test in a crowded venue during the eleven days.
*Kill switch:* pass-the-phone duel. Zero networking, arguably a better stage demo.

### R7 · Thermal throttling degrades the demo
**Likelihood: medium. Impact: medium.**
Sustained 30fps GPU inference on a phone will heat it, and the demos are at hour 9, 23 and 28.

*Mitigation:* drop preview resolution before inference rate. Pause inference between sets. Screen
brightness 60% outside demos. **You have two loaner phones — alternate them.**
*Kill switch:* swap devices mid-demo and turn it into a technical talking point.

### R8 · Fatigue estimator produces nonsense
**Likelihood: medium. Impact: medium — it is the novelty.**
Baselines from three reps are thin, and a mistuned estimator will flap between bands.

*Mitigation:* EMA smoothing, one-rep latched band transitions, baseline frozen during framing loss.
Validate against the recorded fatiguing-set fixture (`14-TEST-PLAN.md`).
*Kill switch:* show the meter as a readout only; drop the adaptive boss behaviour. Pitch shifts
weight onto privacy and the on-device coach.

### R8b · Two parallel Tier-1 lanes both slip
**Likelihood: medium. Impact: high.** *New, 21 Aug — created by promoting the duel to Tier 1.*
Sunday 01:00–06:30 now carries two independent must-ship workstreams, run by two sleep-deprived
people who are also sleeping in shifts. If both slip, Sunday's pitch is Tier 0 plus excuses.

*Mitigation:* hard per-lane gates — G4 at 02:30 (fatigue), G5 at 03:30 (coach), G5b at 05:30 (link).
Neither person starts duel work until their own first item clears. Each lane has an independent
fallback, so one slipping does not take the other with it.
*Kill switch:* fatigue meter as a readout only, template coach, pass-the-phone duel. That
combination is still a complete, honest demo and still scores.

---

## Tier 3 — manageable

### R9 · Art assets not ready
*Mitigation:* generate and source everything in the eleven days (`15-ASSET-BRIEF.md`). Assets are
pre-event work and are disclosed; code is not.
*Kill switch:* geometric boss rendered in Compose — a shape with a phase colour shift. Ugly but
functional, and it costs 20 minutes.

### R10 · Scope creep during the build
*Mitigation:* tier gates G1–G6. The "will not ship" list in `00-PRD.md` §5 is binding.
*Kill switch:* the 06:30 freeze.

### R11 · Contract churn between lanes
*Mitigation:* contracts frozen 13:00 Saturday, forbidden to change after 19:00.
*Kill switch:* adapter layer, never a refactor.

### R12 · One of you is ill or delayed
*Mitigation:* both people can run the demo alone. Rehearse the solo version.
*Kill switch:* solo demo, pass-the-phone, no duel.

### R13 · Loaner phone lost or damaged
Devices remain iQOO property and damage may be charged to the team. Keep them in the venue and
on lanyards. Return both before exit.

---

## Non-risks — decided, do not revisit during the build

| Not a risk because |
|---|
| **Network** — the app has no `INTERNET` permission. Venue wifi is irrelevant. |
| **Backend downtime** — there is no backend. |
| **Account/login failures** — there are no accounts. |
| **Health Connect empty on a fresh device** — we do not use it. Cut in the PRD. |
| **Play Services availability** — hotspot sockets do not need it; only the Nearby fallback does. |

---

## Watchlist — check at every tier gate

- [ ] Camera hours accumulated (drives 15% of the score)
- [ ] Office Kit connected and in use (drives 10%)
- [ ] Golden APK current on both phones
- [ ] Both phones above 40% battery
- [ ] Last commit within 30 minutes
- [ ] Someone has eaten in the last 6 hours
