# 25 · Clinic Mode — Validated Assessments

**Why this exists.** Everything else in ClashFit measures quantities we invented. Clinic Mode
measures quantities that clinicians already use, with protocols that already have published
normative data. That changes the impact claim from *"we made a fitness game"* to *"we put a set of
standard functional assessments on a phone that anyone already owns."*

**Novelty and impact is 20% of the score, and impact is where we were thinnest.** This is the fix.

> ⚠️ **Not a medical device. No diagnosis, ever.** §6 is not optional reading — it is the part that
> keeps this asset rather than a liability.

---

## 1. The tests

All five are performed standing or seated, need no equipment beyond a chair, and are measurable
from the pose pipeline we are already building.

| Test | Protocol | What it indicates | Our detector |
|---|---|---|---|
| **30-second sit-to-stand** | Chair, arms crossed on chest. Stand fully and sit, as many times as possible in 30s. | Lower-body functional strength | **A timed squat count.** `REP_CYCLE`, already built. |
| **Five-times sit-to-stand** | Time taken to complete 5 full reps. | Same, time-based variant | Same detector, different scoring |
| **Single-leg stance** | Stand on one leg, eyes open. Time until the raised foot touches down. | Static balance | `ISOMETRIC_HOLD` on ankle separation + hip level |
| **Functional reach** | Feet fixed, reach forward as far as possible without stepping. | Dynamic balance, stability limits | Wrist X displacement with ankle position locked |
| **Timed up-and-go** | Stand from a chair, walk 3m, turn, return, sit. Timed. | Mobility and gait | Whole-body bounding box trajectory. **Needs 3m of clear floor** — the only test that does. |

**The 30-second sit-to-stand is nearly free.** It is a squat with a chair and a timer, running on
the detector that already ships. If you build one test, build that one.

---

## 2. Why these are camera-measurable

| Test | Measurement |
|---|---|
| Sit-to-stand | Hip-knee-ankle angle cycle, same FSM as `chair_squat`. Full stand gated on hip angle > 158°, full sit on contact with the seat plane established at calibration. |
| Single-leg stance | Ankle Y separation exceeding a threshold starts the clock; it stops when separation collapses or the pelvis tilts beyond tolerance. |
| Functional reach | Ankles locked as the origin, wrist X displacement measured in metres from world landmarks. |
| Timed up-and-go | Segment on state transitions: seated → rising → walking away → turn → walking back → seated. Bounding-box centroid trajectory drives all five. |

**World landmarks give real units.** Functional reach in centimetres, not pixels — which is what
makes a comparison to published norms meaningful rather than decorative.

---

## 3. Presentation — calm, not combat

Like Sigil for yoga ([19-EXERCISE-LIBRARY](19-EXERCISE-LIBRARY.md) §3), Clinic Mode gets **no boss,
no damage, no health bar.** Framing a fall-risk assessment as a fight would be tasteless and would
undermine the credibility the mode exists to create.

**Field Test flow:**
```
Select test → protocol explained in one screen and spoken aloud
            → guided setup (chair placement, camera distance)
            → 3-2-1 → measurement → result card
```

**Result card** shows:
- The raw number, with its unit — "14 stands in 30 seconds", "22 cm reach"
- A band relative to published age-group references, shown as a **range**, never a verdict
- Your own trend across previous attempts — **this is the number that matters most**
- A confidence indicator if framing quality was marginal

---

## 4. Gamification, without cheapening it

The game layer sits *around* the assessment, never inside it.

| Mechanic | Design |
|---|---|
| **Benchmark day** | A recurring Field Test that unlocks the next season. Assessment as a gate, not as a fight. |
| **Trend badges** | Awarded for improvement against **your own** previous result. Never for beating a population norm. |
| **Stat translation** | A better sit-to-stand result raises **POWER**; better single-leg stance raises **MOBILITY** ([22-HEALTH-DOMAINS](22-HEALTH-DOMAINS.md) §1). Clinic results feed the character sheet — which is what makes them matter to a player rather than being a separate serious tab nobody opens. |
| **Crew benchmark** | A whole team runs the same test on one phone. Genuinely useful for a gym, an office, or a class. |
| **No leaderboards on clinical results** | Ranking people by a fall-risk proxy is not something we do. |

---

## 5. Impact — the argument, and how to make it honestly

These tests are standard practice in physiotherapy, geriatric care and cardiac rehabilitation. They
are also, today, something that generally requires a visit to a clinician with a stopwatch. Access
to physiotherapy and structured geriatric assessment is uneven outside major Indian cities.

A phone that already exists in the household, running the protocol correctly, with the recording
never leaving the device, is a meaningful reduction in the cost of *knowing where you stand*.

**Three use cases worth naming in the pitch:**
1. **Ageing parents at home** — a family member runs a two-minute test monthly and sees a trend.
2. **Post-injury rehab adherence** — a physio prescribes home exercises; the patient's form and
   progress are measured without a follow-up visit.
3. **Group screening** — a school, an office, or a residents' association tests everyone on one
   phone in an afternoon.

> **Source your own citation before the deck.** Do not use a number you cannot attribute. One
> properly attributed statistic on physiotherapist availability or elderly fall incidence in India
> is worth more than three vague ones, and a judge who checks will find out either way.

---

## 6. The rules that keep this an asset

**Non-negotiable. Break any of these and the mode becomes a liability in front of a jury.**

1. **Never diagnose.** No "high fall risk", no "you have sarcopenia", no risk percentages. The
   phrasing is *"below the typical range for your age group — worth discussing with a physiotherapist."*
2. **Norms are references, not verdicts.** Always shown as a range, always labelled as a
   population reference, never as a pass or fail.
3. **Cite the protocol, not a claim.** "This is the 30-second sit-to-stand test" is a fact.
   "This tells you your risk of falling" is a claim we are not making.
4. **Say "not a medical device"** on the mode's first screen and in the pitch, before anyone asks.
5. **Never recommend treatment.** We measure and we trend. Any next step is "talk to a professional."
6. **Camera frames and pose landmarks never leave the device** — the same guarantee as everything else. In a clinical context, this means the raw measurements stay completely local: what the patient sees is what they take away, with no sync to a server or a cloud record.
7. **Never combine with the weight, body-composition or appearance framing** already banned in
   [00-PRD](00-PRD.md) §8.

**Rehearse the medical-claims answer.** A judge with a clinical background will probe this, and
handling it crisply is worth more than the feature itself:

> "We are not a medical device and we do not diagnose. We run a published protocol correctly, we
> report the raw measurement with its unit, and we show the user their own trend. The comparison to
> age norms is shown as a reference range, and the only recommendation the app ever makes is to
> speak to a professional."

---

## 7. Weekend scope

| | |
|---|---|
| **Realistically ships** | **30-second sit-to-stand.** It is a timed `chair_squat` on the detector you already have. Roughly 45 minutes including the result card. |
| **If ahead** | Single-leg stance — needs the `ISOMETRIC_HOLD` detector, so it comes free *if* that lands for Siege. |
| **Deck and roadmap** | All five tests, the three use cases, and the character-sheet integration |
| **Do not attempt** | Timed up-and-go. It needs three metres of clear floor and you will not have it at a judging table. |

**The demo moment:** run a 30-second sit-to-stand on a judge. Thirty seconds, a real protocol, a
real number, and a result card that compares it to a reference range. It is short, it is credible,
and it is completely different from every other demo in the room.

**The line:** *"Everything else in this app measures something we invented. This measures something
a physiotherapist would recognise."*
