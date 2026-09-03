# 28 · Judge-Proofing

Every claim ClashFit makes, the defence, and **the evidence you show.** A team that answers a hard
question with a number and a screen wins the exchange. A team that answers with adjectives loses it.

**Rehearse these out loud on Saturday.** Harvest the real questions at Eval R1 and add them here
([11-DEMO-SCRIPT](11-DEMO-SCRIPT.md) §1).

---

## 1. Have these ready to show, not describe

| Evidence | Where | Answers |
|---|---|---|
| **Airplane mode in the status bar, mid-fight** | The device | "Is it really offline?" |
| **The manifest permission comment + cloud/ package** | Laptop, two files open | "How do we know the camera never uploads?" |
| **The SUMMARY fatigue curve** | In-app | "Does the fatigue thing actually work?" |
| **Saturday's commit history, first commit before 11:00** | Laptop, `git log` | "Did you build this during the event?" |
| **The five ADRs** | Laptop | "Why MediaPipe? Why not TensorFlow.js?" |
| **A recorded trace replayed through the engine** | In-app | "Show me again" when the camera is failing |
| **`pose.json` open on the phone** | The device | "How is it tuned?" — and it demonstrates the hot-reload layer |

Have all seven reachable in under ten seconds. Practise the reaching, not just the answering.

---

## 2. The technical questions

### "How do you know the form score is right?"
> "We don't claim to measure 'good form' — that isn't a well-defined quantity. We measure four
> named geometric ones: depth against your own calibrated top position, range of motion against
> your own baseline rep, eccentric tempo in seconds, and joint alignment in degrees. Each is
> checkable. We combine them with fixed weights that are in a config file you can read."

**Never** defend "form quality" as a concept. Defend the four measurements.

### "Isn't that unfair to taller people / different body types?"
> "Every sub-score normalises against that player's own calibration rep, not a population average.
> That's the entire reason calibration exists. A 6'3" player and a 5'2" player are each measured
> against themselves."

*Evidence:* fixture F9 in [14-TEST-PLAN](14-TEST-PLAN.md) — two subjects of different heights,
form-score means within 0.1.

### "How did you validate the fatigue model?"
> "Against three things. Velocity loss across a set is standard practice in velocity-based
> training, so the signal isn't invented. Second, we recorded sets to genuine failure and checked
> the band progresses monotonically after smoothing — that's fixture F3. Third, we froze the
> baseline during framing loss so a pause can't be misread as fatigue.
>
> What we have *not* done is a controlled study against a lab measure. That's a study, not a
> hackathon, and we'd say so on a slide before we'd say it to you."

**The admission is what makes the rest believable.** Do not overclaim here.

### "Why MediaPipe over MoveNet or PoseNet?"
> "Metric world landmarks. The phone is propped at an awkward low angle, so image-space angles from
> a 2D model drift with camera placement — world landmarks are hip-centred and in metres, so a
> depth measurement means the same thing at any angle. Also 33 landmarks instead of 17, which we
> need for push-up torso alignment, and per-landmark visibility which drives our frame-validity
> gate. PoseNet is superseded. MoveNet Thunder is a reasonable 2D fallback and we'd have taken it
> if we'd missed the frame budget."

*Evidence:* [ADR-002](adr/ADR-002-pose-model.md).

### "Why not just run it in a browser with TensorFlow.js?"
> "We considered it seriously — it's more iterable during Red Light. But WebGL can't reach the
> NPU, which means no on-device language model, which is the whole novelty. We solved the Red Light
> problem with a hot-reload config layer instead of by changing runtime."

*Evidence:* [ADR-001](adr/ADR-001-stack.md), all three options written up.

### "What's actually running on the NPU?"
> "MediaPipe Pose Landmarker on the GPU delegate at 30fps during a set, and Gemma 3n E2B int4
> through MediaPipe LLM Inference between sets. Never concurrently — they contend for the same
> thermal envelope, and dropping frames is the one thing we can't afford. Generation is timed out
> at five seconds with a deterministic fallback."

### "What happens if the model isn't there?"
> "A template bank keyed on the same telemetry, filling the same placeholders from the same numbers.
> You've been watching it and you couldn't tell which fired."

**Only say the last sentence if it's true.** If the LLM shipped, say so and offer to kill the model
file and re-run.

### "How does the duel stay in sync?"
> "It doesn't need to. Each phone scores its own reps and broadcasts a damage event. Boss HP is a
> set reduction over the deduplicated union of all events — order-independent, so there's no
> lockstep and no rollback. Every message carries the last eight events, so a dropped packet
> repairs itself with no acknowledgements. And it scales past two players unchanged."

### "What if it can't pair on the day?"
> "Phones tap over NFC to hand off credentials, so there's no discovery step to fail. If that
> doesn't work, there's a manual hotspot join, and below that pass-the-phone, which needs no
> networking at all."

### "Did you build this during the event?"
> "Yes. We built a throwaway prototype beforehand to prove feasibility and record the submission
> video, and we disclosed it on the Phase-1 form. It lives in a separate public repo dated before
> the event. We started a fresh repo at check-in — here's the first commit, Saturday 10:52."

*Evidence:* `git log`. Have it open.

---

## 3. The product questions

### "Isn't this Ring Fit / Kinect?"
> "Kinect's *Your Shape* did form-scored exercise with game feedback in 2010, and Ring Fit is the
> best-executed thing in this space. Both need hardware you buy. Neither reads fatigue from your
> movement, and neither runs a language model on the device. We're on a phone that's already in the
> room, offline."

**Name them first, on slide 2.** Then this question never gets asked as a gotcha.

### "Would anyone actually keep using this?"
> "That's the honest open question and it's why we built the fatigue loop. Fitness apps churn
> because they punish you for getting tired and for missing a day. We do the opposite — the boss
> adapts when you fade, rest days earn points, and streaks survive one missed day a week. Whether
> that moves day-7 retention is a six-week beta, not a claim we can make today."

### "Isn't a fitness game a bit trivial for a health track?"
> "The game is the adherence mechanism. The measurement underneath it is the product — and Clinic
> Mode runs published functional assessments: 30-second sit-to-stand, single-leg balance,
> functional reach. Those are protocols a physiotherapist would recognise, on a phone, at home."

### "Are you making medical claims?"
> "No, and it's on the first screen of that mode. We're not a medical device, we don't diagnose,
> and we don't recommend treatment. We run a published protocol, report the raw measurement with
> its unit, show your own trend, and compare to age-group norms as a reference *range*. The only
> recommendation the app ever makes is to speak to a professional."

*This one will come from any judge with a clinical background. Rehearse it verbatim.*

### "What about accessibility?"
> "Accessible variants are rungs on the ladder, not an easy mode — chair squats and knee push-ups
> use the same scoring and the same boss. All critical feedback works through audio alone, because
> during a push-up you're facing the floor. There's a reduced-motion toggle. And colour is never
> the only carrier of meaning — the verdict word always appears with the flash."

---

## 4. The uncomfortable questions

Answer these fast and without defensiveness. Hesitation costs more than the admission does.

| Question | Answer |
|---|---|
| "Can I cheat this?" | "Partially. You could game rep count with a partial movement, though the depth score would tank. We're measuring movement quality, not preventing fraud — there's no prize for lying to a fitness app." |
| "Your alignment score looks noisy." | "It is the noisiest of the four, which is why it carries the smallest weight and why its weight is in a config file we can zero on the day." |
| "This only has one exercise." | "One exercise, five game modes, and the detector is generic — adding an exercise is a JSON record, not code. We shipped depth over breadth deliberately, and slide 8 says exactly what shipped." |
| "Why should the phone be the platform?" | "Because everyone already has the sensor. Every strong product in this space asks you to buy hardware first." |
| "What did you fail at?" | *Answer honestly with something real.* Judges remember teams who can name a failure. Rehearse one true answer. |

---

## 5. Rules of engagement

1. **Never bluff a number.** "I don't have that measured" is a strong answer. An invented figure is
   fatal if they follow up.
2. **Lead with the measurement, follow with the interpretation.** "Velocity dropped 31%" then "which
   is why the boss staggered" — never the reverse.
3. **Show, then explain.** Reach for the phone or the laptop before you reach for an adjective.
4. **Name your own limits before they do.** It buys credibility for everything else.
5. **Never let a judge watch you silently debug.** Narrate, diagnose, switch
   ([11-DEMO-SCRIPT](11-DEMO-SCRIPT.md) §7).
6. **Write down every question you're asked at Eval R1.** That list, answered, *is* Sunday's pitch.
