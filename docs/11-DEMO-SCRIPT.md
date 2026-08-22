# 11 · Demo Script

Three separate performances, each with a different job. Rehearse all three out loud on Saturday,
not Sunday.

---

## 1. Eval Round 1 — Sat 19:00–22:00 · ~3 minutes at your table

**Job:** bank a score, and harvest the questions that become Sunday's pitch.

- Lead with the demo, not the deck. "Can I show you something — it takes forty seconds."
- Do 6 squats. Make reps 4 and 5 deliberately shallow. Say nothing about it.
- Wait for them to notice the damage difference. If they don't, point at it once.
- Then the one-liner: *"Every camera fitness app uploads your video. Ours never does — pose and the
  coach both run on the phone's NPU. We can show you in airplane mode."*
- **Write down every question they ask.** That list is your Sunday script, pre-tested against real
  judges.

Do not oversell what is not built yet. At 19:00 you have Tier 0. Say "the fatigue system lands
tonight" — do not say it works.

---

## 2. Eval Round 2 — Sun 09:00–12:00 · judged at tables

**Job:** the score that decides the Top 10, combined with R1.

**Squats only.** There is no floor space for push-ups at a judging table. Two metres and standing
is all you need.

Sequence, ~4 minutes:

1. **Airplane mode on, visibly.** Do it in front of them, do not narrate it.
2. Launch, calibrate — this must take under 20 seconds. Rehearsed.
3. Six squats: four clean, two deliberately shallow. Let the damage gap speak.
4. Keep going until the fatigue meter moves to `FADING`. Point at it once: *"that's measured
   velocity loss and range-of-motion collapse, not a timer."*
5. Boss staggers. Land the line: **"the boss can't outlast you, but it makes you earn the ending."**
6. Rest screen: the Gemma coach line appears and is spoken aloud. Let the TTS play — do not talk over
   it.
7. Open SUMMARY. Show the fatigue curve. *"This is what we actually measured."*
8. Stop. Ask what they want to see.

**If they have time, offer the duel.** If they don't, do not push it.

---

## 3. Top 10 pitch — Sun 13:45 · 3 to 5 minutes, live, full jury

**Job:** win.

| Time | Beat |
|---|---|
| 0:00–0:20 | **Open on the demo, not on slides.** Phone already propped, boss on the mirrored laptop screen. Do three squats. Boss takes damage. Then stop and introduce yourselves. |
| 0:20–0:50 | The problem, in one breath: trackers are inert, movement games ignore whether the movement was correct, and every camera product in this space uploads the footage. |
| 0:50–1:40 | **The judge duel.** Invite a judge up. Casual mode. Two phones, one boss, thirty seconds. This is the memorable moment of your weekend — everything before it is setup and everything after is explanation. |
| 1:40–2:20 | The novelty, on the mirrored screen: fatigue meter moving, boss staggering. *"Velocity decay, range-of-motion collapse, tempo drift — measured per rep, on-device."* |
| 2:20–2:50 | Architecture in one sentence and one slide. Pose model and a Gemma 3n coach, both on the Snapdragon NPU. **Airplane mode still on.** Point at the status bar once. |
| 2:50–3:20 | Scope honesty: what shipped in 30 hours, what is roadmap. Judges reward teams who know the difference. |
| 3:20–3:40 | Close: *"The phone isn't a screen here. It's the sensor, the referee, the coach and the opponent."* |
| 3:40+ | Questions. |

---

## 3b. The improvement narrative — a scoring lever almost nobody uses

**The same jury sees you twice**: Eval R1 on Saturday evening and Eval R2 on Sunday morning, and
both scores stack into the Top 10 shortlist.

So make the second visit explicitly reference the first.

> *"At seven last night you asked whether the form score punishes taller people. It did. We
> normalised every sub-score against the player's own calibration rep overnight — here's the same
> set, scored both ways."*

Why this works: it proves you listened, it proves you can ship inside a night, and it converts a
weakness a judge already noticed into evidence of velocity. Teams almost never do it, because
almost nobody writes the questions down at R1.

**So write them down at R1.** Every question, verbatim, with the judge's name if you catch it.
Pick the two most substantive, fix them overnight, and open R2 by naming them.

---

## 4. The judge duel — plan it, don't improvise it

The strongest move available to you, and the highest-variance. Reduce the variance:

- **Ask before the pitch,** during Eval R2: "would you be up for being player two later?" A judge
  who has already agreed will not decline on stage.
- **Casual mode is mandatory.** Damage ×1.6, form floor 0.6, boss HP ×0.5. A judge in office clothes
  who has not squatted since school still lands satisfying hits and reaches a victory screen.
- **Squats, never push-ups.** Nobody is getting on the floor in front of a room.
- **Thirty seconds, not a full fight.** Timeboxed, then thank them and move on.
- **Have your phone pre-calibrated and the hotspot already up.** Pairing must take under 15 seconds,
  rehearsed.
- **If they decline:** Ujjwal is player two. Rehearse that version too.

---

## 5. Office Kit staging

Office Kit usage is 10% of the rubric and is measured from device telemetry, not self-report. Use it
because it genuinely improves the demo, and note that it also scores:

- **Mirror one phone to the laptop** so the room watches the shared boss HP on a large display while
  the players' phones face the players. Without this, a duel is two people staring at objects the
  audience cannot see.
- Mention once, in passing, that the Gemma weights were moved onto the device over Office Kit file
  transfer. Once. Do not labour it.

---

## 6. Answers to the questions you will be asked

| Question | Answer |
|---|---|
| **"Isn't this Ring Fit / Kinect?"** | "Those exist and they're good. Neither reads fatigue from your movement, neither runs a language model on the device, and both need a console. We're on a phone you already own, offline." |
| **"How do you know the form score is correct?"** | "We don't claim to measure 'good form'. We measure four named geometric quantities — depth against your own calibrated baseline, range of motion, eccentric tempo, and joint alignment — and we score those. Each one is checkable." |
| **"Isn't this unfair to taller people?"** | "Every score is normalised against that player's own calibration rep, not a population average. That's the whole reason calibration exists." |
| **"Why on-device and not cloud?"** | "Three reasons. It's a camera in your bedroom. Thirty-frames-a-second inference over a network isn't possible. And our user is frequently offline. Airplane mode is on right now." |
| **"What's actually running on the NPU?"** | "MediaPipe Pose Landmarker on the GPU delegate at 30fps, and Gemma 3n E2B int4 through MediaPipe LLM Inference between sets. Never concurrently — they contend for thermal headroom." |
| **"Did you build this during the event?"** | "Yes. We built a throwaway prototype before, disclosed it in our submission, and started a fresh repo at check-in. Here's the commit history from Saturday 11am." **Have this ready to show.** |
| **"How does this retain users?"** | "Ghost races against your own recorded sets, a daily city boss with a shared damage pool, and streaks that survive one rest day a week. None of that shipped this weekend — it's on the roadmap slide." |
| **"What happens if the model isn't there?"** | "It falls back to a deterministic template bank keyed on the same telemetry. You've been watching it switch between the two and you couldn't tell." (Only say this if it is true.) |

---

## 7. Failure playbook

Rehearse these. Every one of them will feel calm if you have said the words before.

| Failure | What you do |
|---|---|
| Pose won't detect | Switch to Arena Mode (rear ultra-wide + mirror). Rehearsed as a one-tap change. |
| Reps miscounting | Say it: "we're getting a false positive here — the threshold is tuned for a lower camera angle." Judges respect diagnosis. Then switch to the recorded trace replay. |
| App crashes | Install the golden APK. It takes 20 seconds and you have it on both phones. |
| Duel won't pair | Immediately to pass-the-phone. "Same mechanic, one device." No apology, no fumbling. |
| Gemma too slow | Templates fire automatically. Do not mention it. |
| Phone overheats | You have a second loaner. Swap. Say "we're on our second device — thermal throttling is real at 30fps inference" and turn it into a technical point. |
| Total failure | The recorded video, narrated honestly, plus the SUMMARY screen from an earlier session as evidence. |

**The rule underneath all of these: never let a judge watch you silently fight a bug.** Narrate,
diagnose, switch. A team that debugs calmly in front of a jury reads as senior. A team that goes
quiet and taps at a phone reads as lost.
