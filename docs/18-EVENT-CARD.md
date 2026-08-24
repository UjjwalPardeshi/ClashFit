# 18 · Event Card

**The one page you read at hour 26.** Twenty-four documents are unnavigable on no sleep. This is
everything that matters on the day. Print it, or keep it open on the laptop.

---

## Gates — the only checkpoints

| Gate | Time | Must be true | If not |
|---|---|---|---|
| G1 | **Sat 13:00** | Landmarks on screen · fake rep damages boss · **config hot-reload working** | Both people on the camera pipeline. Nothing else matters. |
| G2 | **Sat 16:30** | Real squat → real damage → sound | Squats only for the weekend. Drop everything else. |
| G3 | **Sat 19:00** | **Tier 0 complete and demoable** | Demo what exists, honestly. Never fake it. |
| G4 | **Sun 02:30** | Fatigue bands sane (Omkar) | Meter as readout only. Omkar → duel. |
| G5 | **Sun 03:30** | Gemma producing usable lines (Ujjwal) | Templates. **Do not mention the LLM in the pitch.** |
| G5b | **Sun 05:30** | Two phones linked, identical boss HP | Pass-the-phone. Stop transport work. |
| G6 | **Sun 06:30** | **FEATURE FREEZE** · golden APK on both phones | Non-negotiable. Freeze regardless. |

---

## Cut order — when time runs out

1. Extra duel transports → hotspot only
2. Extra game modes → Boss Fight + Time Attack only
3. Push-ups → squats only
4. Opponent rep feed → shared HP bar only
5. Duel → **Ghost Race**, then pass-the-phone
6. Fatigue-adaptive boss → fatigue meter as readout
7. Gemma → templates

**Never cut past 7.** Rep counting + form-weighted damage + audio is still a complete thesis.

---

## Ten standing rules

1. **Golden APK** on both phones after every working build. `/sdcard/ClashFit/golden/`
2. Commit every 30 minutes
3. **No new dependency after Sat 19:00**
4. **No contract change after Sat 19:00** — write an adapter
5. **Never both away from the desk** — stagger mentors, food, bathroom
6. **Office Kit connected all weekend** (10%, measured as duration)
7. Camera running whenever plausible (15%, measured as duration)
8. Stuck 20 minutes → swap tasks
9. Eat 13:00 · 20:00 · 02:00 · 08:00. Alarms set Saturday morning.
10. **Charge both phones every Green block**

**Sleep:** Omkar 01:00–03:30 · Ujjwal 03:30–06:00. Alarms set before you travel.

---

## Pre-demo ritual — 3 minutes, every round

- [ ] Both phones ≥ 80%
- [ ] Golden APK present on both
- [ ] **Airplane mode on, app still works** — verified, not assumed
- [ ] Calibration completes under 20s **at this actual table**
- [ ] Office Kit mirroring
- [ ] Hotspot up, duel pairs under 30s
- [ ] Config files = tuned versions, not debug
- [ ] **Debug overlay OFF**
- [ ] TTS audible over room noise
- [ ] One full fight completed in the last 30 minutes

---

## Failure playbook

| Breaks | Say and do |
|---|---|
| Pose won't detect | → **Arena Mode** (rear ultra-wide + mirror). One tap. |
| Reps miscounting | "That's a false positive — threshold's tuned for a lower camera angle." → trace replay. |
| App crashes | Golden APK. 20 seconds. |
| Duel won't pair | → **Ghost Race**. Looks identical. No fumbling, no apology. |
| Gemma slow | Templates fire automatically. Say nothing. |
| Phone overheats | Swap to the second loaner. "Thermal throttling is real at 30fps inference." |
| Total failure | Recorded video + SUMMARY screen as evidence, narrated honestly. |

**Never let a judge watch you silently fight a bug.** Narrate → diagnose → switch.

---

## The three sentences

1. Every camera fitness app uploads the most intimate footage you own. **We never do** — pose and the
   coach both run on the NPU, airplane mode on.
2. **ClashFit reads fatigue from your movement** — velocity decay, range-of-motion collapse, tempo
   drift — and the boss adapts in real time.
3. The phone isn't a screen here. It's the **sensor, the referee, the coach and the opponent**.

**The line:** *"The boss can't outlast you, but it makes you earn the ending."*

---

## Judge Q&A — one line each

| | |
|---|---|
| "Isn't this Ring Fit / Kinect?" | "Both exist, both are good. Neither reads fatigue, neither runs a language model on the device, both need hardware you buy." |
| "How do you validate form?" | "We don't claim 'good form'. We measure four named geometric quantities against your own calibration rep." |
| "Unfair to taller people?" | "Every score normalises against your own calibration rep, not a population average." |
| "Why on-device?" | "It's a camera in your bedroom. 30fps over a network isn't possible. And airplane mode is on right now." |
| "What's on the NPU?" | "MediaPipe Pose at 30fps on the GPU delegate, Gemma 3n E2B int4 between sets. Never concurrently — thermal." |
| "Built during the event?" | "Yes. Prototype was disclosed in our submission, fresh repo at check-in. Here's Saturday's commit history." *(have it ready)* |
| "Retention?" | "Ghost races, a daily city boss, streaks that survive a rest day. Roadmap — not this weekend." |

---

## Demo config

- **Squats.** Never push-ups at a table.
- **Time Attack, 60 seconds** — fits a rotating judge.
- **Casual mode** for the judge duel: damage ×1.6, form floor 0.6, boss HP ×0.5.
- **Ask a judge to be player two during Eval R2**, so they've already agreed before the stage.
- Show the **mode select screen** for four seconds even if you demo one mode.
- Show the **SUMMARY fatigue curve** at the end. It is evidence.

---

## Contacts

Organisers: sameera@reskilll.com (published on iqoo.reskilll.com)
Team-code issues: the support number shared in the participant WhatsApp group — kept out of this repo deliberately, since it is a personal mobile and this is public.
