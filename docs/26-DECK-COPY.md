# 26 · Deck Copy

Nine slides, written. Required for Phase 1 — **PDF or PPT, max 25 MB, due 27 Aug** in your own plan.

**Design:** neon tactical, matching the app ([03-UI-UX-SPEC](03-UI-UX-SPEC.md) §2). Near-black
ground, one hot accent, huge type. No bullet-point walls. Every slide should be readable from the
back of a room in under four seconds.

Slot markers `[LIKE THIS]` need your input.

---

## Slide 1 · Title

> # ClashFit
> ### Offline AI fitness combat
>
> Your body is the controller. Your camera is the referee.
> Nothing leaves the phone.
>
> `Omkar Kadam · Ujjwal [SURNAME] · iQOO Hackathon 2026 · Pune · HealthTech`

*Visual:* a single frame from the fight screen — boss, HP bar, damage numeral. Nothing else.

---

## Slide 2 · The field, named

> # This category is crowded.
> ## Here's what nobody does.

| Two columns |
|---|
| **The field** — Kinect *Your Shape* (2010) · Ring Fit Adventure · Onyx · Kemtai · Peloton Guide · Tempo |
| **What they share** — dedicated hardware, a preset difficulty curve, or a server that receives your video |

> **Nothing reads fatigue from how you actually move. Nothing runs the coach on the device.**

*Why this slide.* Naming your competitors first is a confidence move, and it disarms the "isn't
this Ring Fit?" question before a judge can ask it. Do your own 30-minute search before finalising
this list ([research/COMPETITIVE-LANDSCAPE](research/COMPETITIVE-LANDSCAPE.md)).

---

## Slide 3 · The loop

> # Reps in. Damage out.

Four frames, no prose:
`prop the phone` → `boss appears` → `every rep lands a hit` → `boss dies`

One line underneath:

> Damage is weighted by measured form — depth, range, tempo, alignment. A shallow rep does 50.
> A clean one does 85. A perfect one does 100.

*Use real screenshots and the real numbers from `combat.json`. Do not mock this slide up.*

---

## Slide 4 · The novelty

> # It knows when you're gassed.

> Concentric velocity decay. Collapsing range of motion. Growing pauses between reps.
> Measured per rep, on-device, against your own first three.
>
> The boss staggers when you fade. It finishes the fight when you're done.

> ## "The boss can't outlast you — but it makes you earn the ending."

*Visual:* the fatigue curve from a real session, with the band transitions marked. **This is the
most important image in the deck** — it is the one thing on screen that no other team has.

---

## Slide 5 · The coach and the villain are the same model

> # Gemma 3n. On the NPU. Airplane mode.

> After every set, a twenty-three-field summary of how your movement changed goes to a 2-billion-parameter
> model running on the phone. It writes the coaching. It writes the taunt.
>
> It never sees your video. Nothing does.

Two quote cards side by side:

> **COACH** — "Your last three reps lost four centimetres of depth. Take forty seconds, then finish it."
>
> **THE PACEMAKER** — "Your knees are negotiating. I don't negotiate."

---

## Slide 6 · Architecture

> # No cloud box on this diagram.

```
front camera 30fps
      ↓
MediaPipe Pose Landmarker · GPU delegate · 33 world landmarks
      ↓
One Euro filter → joint angles → hysteretic rep FSM → form score
      ↓                                                    ↓
fatigue estimator ──────────────→ adaptive boss ← combat engine
      ↓
[between sets only] Gemma 3n E2B int4 · MediaPipe LLM Inference → TTS
```

> Two models, one thermal envelope, never run concurrently.
> **The release manifest has no INTERNET permission.** You can check.

*This slide carries the 15% technical-depth score. Keep it a diagram, not a paragraph.*

---

## Slide 7 · Two phones, one boss, no server

> # And it scales past two.

> `bossHp = maxHp − Σ damage over the deduplicated union of all events`
>
> Events, not state. Two players or eight, the maths is identical — and none of it needs a
> server. Phones tap over NFC to pair.

> **Community without a server:** ghosts are files you send. Challenge cards are QR codes. Crew
> ledgers merge when two phones are in the same room.

---

## Slide 8 · What shipped in 30 hours, and what didn't

Two honest columns. **Do not blur them.**

| **Shipped this weekend** | **Roadmap** |
|---|---|
| Rep detection, form scoring, fatigue model | 5 detector families → 61 exercises |
| Fatigue-adaptive boss | Yoga, cardio, isometrics, ballistics |
| On-device Gemma coach + TTS | Nutrition via the same multimodal model |
| Two-phone duel over offline P2P | Sleep, recovery, HR estimation |
| `[MODES ACTUALLY BUILT]` | Group raids, seasons, crews |
| 30-second sit-to-stand assessment | Full Clinic Mode — five validated tests |

> We're building a gamified health platform. This weekend we shipped the hardest part of it — the
> movement engine and the fatigue model — because everything else is data entry, and that's not
> where the risk is.

*Why this slide matters.* "Idea and scope fit for 30 hours" is an explicit scoring criterion.
Teams who blur what shipped get caught; teams who separate it cleanly read as senior.

---

## Slide 9 · Team

> # Two working engineers. Two phones. One weekend.

> **Omkar Kadam** — Android and perception. `[ONE LINE: role, strongest shipped thing]`
> **Ujjwal [SURNAME]** — game systems and on-device inference. `[ONE LINE: same]`
>
> Both have deployed local LLMs on-device before. We built and threw away a prototype before
> submitting, so we already know where MediaPipe breaks: oblique angles, cluttered frames, and
> depth from a front view.

> `[WHY THIS PROBLEM — one honest sentence]`

---

## Optional slide 10 · Impact

Only if the pitch is running short, or if the jury leans clinical. Content from
[27-IMPACT-BUSINESS](27-IMPACT-BUSINESS.md).

> # The tests a physio uses, on a phone you already own.
>
> 30-second sit-to-stand. Single-leg balance. Functional reach. Published protocols, real units,
> your own trend over time. Not a medical device — a measurement anyone can take at home.

---

## Production notes

- **Export as PDF.** Under 25 MB. Check it opens on a phone.
- **Real screenshots only.** A mocked-up UI in a deck next to a live demo reads as dishonest.
- **Slides 4 and 8 are the ones that win.** Novelty and scope honesty. Everything else is support.
- **Nine slides, four minutes.** Roughly 25 seconds each — if a slide needs more, it is two slides.
- The pitch runs off the demo, not the deck ([11-DEMO-SCRIPT](11-DEMO-SCRIPT.md) §3). The deck is
  what a judge flips through afterwards.
