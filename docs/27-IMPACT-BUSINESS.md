# 27 · Impact & Business Case

"Novelty **and impact**" is 20% of the score, and impact is the half most hackathon teams have no
answer to at all. This document is that answer.

> **Rule for every number in here: cite it or cut it.** A judge who checks one statistic and finds
> it invented discards everything else you said. Where this document says `[CITE]`, find a real
> source before the deck — one properly attributed figure beats three vague ones.

---

## 1. The problem, stated in a way that survives scrutiny

Three real, separable problems. We address the second and third directly, and the first partially.

| Problem | Evidence to source |
|---|---|
| **People start exercising and stop.** Fitness-app retention is famously poor — a large share of installs are inactive within weeks. | `[CITE: app retention benchmark]` |
| **People training at home don't know if they're doing it right,** and won't pay for or travel to supervision to find out. | Qualitative; can be argued without a statistic |
| **Functional assessment requires a clinician.** Sit-to-stand, balance and gait tests are standard practice but need someone with a stopwatch, and access is uneven outside metros. | `[CITE: physiotherapist availability / access]` |

**What makes the third one ours specifically:** we already measure joint angles at 30fps. Running a
published protocol correctly is a scoring change, not a new product.

---

## 2. Who this is for

| Segment | Size argument | Why they use us |
|---|---|---|
| **Home exercisers with no equipment** | The default for most people who train at all | A reason to do the next set that isn't willpower |
| **Beginners who won't go to a gym** | Largest and least served | A referee that doesn't judge them |
| **Ageing adults and their families** | Growing, and the most under-served by fitness software | A two-minute monthly test with a trend |
| **Rehab patients between appointments** | Prescribed home exercise, low adherence, no feedback | Form scoring and a record their physio can see |
| **Schools, offices, residents' associations** | One phone screens a whole group | Group benchmarking with no equipment |

---

## 3. Why India, specifically

Not decoration — these are genuine structural advantages of the design here.

1. **Offline-first is not a feature, it's a requirement.** Connectivity is uneven and data is
   metered for a large share of users. An app that works fully in airplane mode is usable where a
   cloud-inference competitor is not.
2. **Zero hardware.** Kinect, Ring Fit, Peloton Guide and Tempo all require a purchase in the
   hundreds of dollars. We require a phone that is already in the house.
3. **Privacy is a real objection, not a theoretical one.** A camera pointed at a woman exercising
   at home, uploading video to a foreign server, is a hard no for a very large number of Indian
   households. On-device inference is what makes the product installable at all for that segment.
4. **Assessment access.** `[CITE]` Functional testing that currently requires travelling to a
   clinician can be done at home, monthly, for free.
5. **Language.** The coach is a local LLM — Indic-language coaching via a Sarvam-class model is a
   config change, not a rewrite. Not this weekend; a strong roadmap line.

---

## 4. Who pays

Be honest that none of this is built and none of it is the point this weekend. Have the answer
anyway — most teams don't.

| Model | Who | Note |
|---|---|---|
| **Free core, forever** | Everyone | The fight, the coach, the assessments. Non-negotiable — a paywall on the referee defeats the product. |
| **Cosmetic and season passes** | Engaged players | Cosmetics unlock on *consistency*, never on performance or payment ([23-META-PROGRESSION](23-META-PROGRESSION.md) §5). |
| **Clinic / physio licence** | Physiotherapists, rehab clinics | Prescribe a home programme, receive the trend. The patient's video still never leaves their phone. Clearest revenue, clearest value. |
| **Corporate wellness** | Employers | Per-seat, group benchmarking, zero hardware to deploy. |
| **Institutional screening** | Schools, elder-care, insurers | One device screens a cohort. |

**No advertising.** Ever. An app watching you exercise cannot also be selling attention — the
privacy claim collapses the moment it does, and the privacy claim is the product.

---

## 5. Why this is defensible

Not "we'll move fast." Four structural reasons:

1. **On-device inference is an engineering moat, not a feature toggle.** Thermal budgeting, two
   models sharing an NPU, filtering that survives a phone propped at a bad angle — a cloud-first
   competitor cannot retrofit this.
2. **Config-driven exercises.** Five detectors, 61 exercises, new ones added as data. A competitor
   who hard-codes each exercise adds them linearly; we add them in a text file.
3. **The fatigue model generalises.** One framework across five movement families
   ([19-EXERCISE-LIBRARY](19-EXERCISE-LIBRARY.md) §2). Every new family inherits it.
4. **Server-free social.** Ghost files and local mesh raids give us community without the
   infrastructure cost — and without contradicting the privacy claim, which every cloud competitor
   must.

---

## 6. Traction plan — what happens after the weekend

| When | What |
|---|---|
| Weeks 1–2 | Ship the second detector family, close the exercise library to 20 working exercises |
| Weeks 3–6 | Closed beta, 50 users, measure real day-7 retention against the 25% target |
| Month 2 | One physiotherapy clinic pilot — prescribe-and-trend |
| Month 3 | Play Store release, free, no accounts |

**The metric that matters:** day-7 retention. If we cannot beat the fitness-app median substantially,
the whole thesis about fatigue-adaptive difficulty was wrong, and we should know that in six weeks
rather than six months.

---

## 7. Risks we'd raise before a judge does

Naming your own risks is a credibility move. Practise saying these calmly.

| Risk | Honest answer |
|---|---|
| Pose accuracy in bad conditions | Real. Mitigated by calibration gating, world landmarks, and a proximity-sensor path for push-ups. Not solved. |
| Form scoring isn't clinically validated | Correct, and we don't claim it is. We report four named geometric quantities. Validation is a study, not a hackathon. |
| Fitness apps churn regardless | The fatigue-adaptive loop is our hypothesis for why. Six-week beta tells us if it's true. |
| Clinic Mode invites regulatory questions | We are not a medical device, we don't diagnose, we report protocol measurements and trends. §6 of [25-CLINIC-MODE](25-CLINIC-MODE.md). |
| Camera-based fitness has failed commercially before | Onyx, and others. Every one of them needed a server or a device. We need neither. |

---

## 8. The 45 seconds, if a judge asks "so what?"

> "Two hundred million people in this country have a phone that can do this and no equipment,
> no trainer, and no reason to trust an app that uploads video of them exercising at home.
> We removed the upload, we removed the hardware, and we made the difficulty respond to how tired
> they actually are — which is the thing that makes beginners quit.
>
> And because we're already measuring joint angles at thirty frames a second, we can run the same
> functional tests a physiotherapist runs. Not to diagnose anything — to give someone a number and
> a trend they'd otherwise have to travel to a clinic for."

Replace the first figure with something you can cite, or drop it. **The argument works without it.**
