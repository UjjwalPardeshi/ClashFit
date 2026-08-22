# 10 · Build Runbook

Sat 5 – Sun 6 September 2026 · Pune · two builders, two loaner phones.

**Real build time is ~19 focused hours inside a 25-hour window.** Roughly 10.5 of those hours are
Red Light — phone only, laptops closed as build machines, everything routed through Office Kit.
Plan against 19, not 30.

---

## 1. Lane assignment

| | Omkar | Ujjwal |
|---|---|---|
| Owns | `perception`, `duel` transport, camera, CameraX, MediaPipe | `combat`, `coach`, `ui`, Compose, Gemma, audio, duel lobby UI |
| Frozen contract | `PoseEngine` | `CombatEngine`, `CoachEngine` |
| Never touches | Compose screens | camera or pose internals |

Both write `core/model` and `core/config` together in the first hour, then split and do not merge
lanes until Sunday.

---

## 2. Hour by hour

### Saturday

| Time | Mode | Work |
|---|---|---|
| **08:00** | ops | Check-in, ID, NDA, **two loaner phones**, desk. Breakfast 08:30–09:30. Immediately: enable developer options + USB debugging on both phones, pair Office Kit, side-load the Gemma `.task` over Office Kit file transfer. |
| **10:00** | open | Keynote, Green/Red reveal, HackTracker + Office Kit teach-in, rubric walkthrough. Listen. Note the exact Red Light enforcement rules — they may differ from the published schedule. |
| **11:00–13:00** | 🟢 **GREEN** | **Both on laptops — this is precious.** New repo. Project skeleton, `core/model`, `ConfigStore` + hot reload, `FakePoseEngine`, `TemplateOnlyCoachEngine`. Then split: Omkar → CameraX + MediaPipe landmarks rendering on device. Ujjwal → Fight screen driven by `FakePoseEngine`, boss HP bar, damage numerals. **Exit criterion at 13:00: landmarks visible on the phone, and a fake rep visibly damages a boss.** |
| **13:00–15:30** | 🔴 RED | Phone only. Omkar: tune squat thresholds in `pose.json` by physically doing squats and watching the debug overlay. Ujjwal: tune `combat.json` damage curve against `FakePoseEngine`, write the template coach bank, draft the Eval R1 script. **Neither of you compiles anything.** |
| **15:30–16:30** | 🟢 GREEN | Compile everything tuned in Red. Wire the real `PoseEngine` into the Fight screen — first real rep lands. **Mentor Round 1 runs now: one of you goes, one keeps building. Never both away.** |
| **16:30–19:00** | 🔴 RED | Playtest on device. Audio feedback in (rep tick, hit, combo). Calibration flow cues. Rehearse the Eval R1 pitch out loud, twice. |
| **19:00–22:00** | ⚪ **EVAL 1** | **Tier 0 must be done and demoable.** Scored, no elimination, but it stacks into the Top 10 shortlist. Demo between judge visits; build in the gaps. Write down every question a judge asks — that is your Sunday pitch, pre-tested. |
| **22:00–01:00** | 🔴 RED | Prompt engineering for Gemma, editing `config/prompts/*.txt` directly on the phone. Duel transport testing across the two phones — pure on-device work, ideal Red Light material. Eat. |

### Sunday

| Time | Mode | Work |
|---|---|---|
| **01:00–06:30** | 🟢 **GREEN — 5.5h, your biggest laptop window of the weekend** | Everything compile-heavy goes here, in **two parallel Tier-1 lanes**. **Omkar:** finish the fatigue estimator, then `HotspotSocketTransport` + `DuelSession`. **Ujjwal:** real `LlmEngine` + TTS + rest screen, then the duel lobby and opponent UI. **Sleep staggered: Omkar 01:00–03:30, Ujjwal 03:30–06:00** — each solo-drives their own lane while the other is down. You will run Sunday on ~2.5 hours. That is the cost of being two: plan it, do not discover it. |
| **06:30** | — | **FEATURE FREEZE.** No new code paths after this. Golden APK on both phones. |
| **06:30–09:00** | 🔴 RED | Rehearse. Tune numbers only. Fix crashes only. Charge both phones to 100%. Run the full demo three times, including the duel pairing sequence, timed. |
| **09:00–12:00** | ⚪ **EVAL 2** | All teams judged at tables. **Squats, not push-ups** — there is no floor space. R1 + R2 combine to pick the Top 10 per bucket. Final build ends 12:00. |
| **12:00–13:30** | — | Repos lock before pitches. Eat, shower if possible, change shirt. Rehearse the 3-minute pitch twice more. |
| **13:45** | — | Top 10 pitches. 3–5 minutes, live, demo on hardware. See `11-DEMO-SCRIPT.md`. |
| **16:15** | — | Awards. Top 6 advance — 3 student, 3 professional. |

---

## 3. Tier gates — the only checkpoints that matter

| Gate | Time | Must be true | If it is not |
|---|---|---|---|
| **G1** | Sat 13:00 | Landmarks on screen; fake rep damages a boss | Stop everything. Both people on the camera pipeline. Nothing else matters. |
| **G2** | Sat 16:30 | Real squat rep produces real damage, with sound | Drop push-ups permanently. Squat only for the weekend. |
| **G3** | Sat 19:00 | **Tier 0 complete and demoable** | Demo what exists, honestly. Do not fake it — judges see it every time. |
| **G4** | Sun 02:30 | Fatigue estimator emitting sane bands (Omkar) | Cut the fatigue-adaptive boss; keep the meter as a readout only. Pitch shifts weight onto privacy + coach. **Omkar goes straight to duel.** |
| **G5** | Sun 03:30 | Gemma generating usable lines (Ujjwal) | Ship templates. **Do not mention the LLM in the pitch** — an unshipped feature described as shipped is a credibility loss you cannot recover from. |
| **G5b** | Sun 05:30 | Two phones linked, identical boss HP after 20 reps each | Cut to **pass-the-phone** duel. Zero networking, arguably a better stage demo. Do not spend Sunday morning on transport. |
| **G6** | Sun 06:30 | Feature freeze, golden APK on both phones | Non-negotiable. Freeze regardless of what is unfinished. |

**The Tier-1 rule with two parallel lanes:** neither person starts their duel work until their own
first item clears its gate. Fatigue and the coach are the novelty; the duel is the demo. Shipping a
duel with no differentiator is the one outcome worse than shipping no duel.

---

## 4. Standing rules

1. **Golden APK.** After every green build that demonstrably works, copy the APK to
   `/sdcard/ClashFit/golden/<timestamp>.apk` on both phones. If the tree breaks at 03:00 and cannot
   be fixed, you still have a demo.
2. **Commit every 30 minutes**, message or not. `git commit -am wip` is fine.
3. **No new dependency after Saturday 19:00.** A Gradle sync at 02:00 is how weekends die.
4. **No contract change after Saturday 19:00.** Write an adapter.
5. **Both people never leave the desk at once.** Mentors, food, bathroom — stagger.
6. **Keep Office Kit connected all weekend.** It is 10% of the rubric, measured as counts and
   durations from device telemetry, not self-reported.
7. **Camera stays running whenever plausible.** Creative-phone-use is 15%, also telemetry-measured.
   An app that keeps the camera live for six hours is an outlier in that dataset.
8. **When stuck for 20 minutes, swap tasks.** Fresh eyes at hour 20 are worth more than persistence.
9. **Eat at 13:00, 20:00, 02:00, 08:00.** Set alarms. You will forget.
10. **Charge both phones at every Green block.** A dead demo phone at 13:45 Sunday ends the weekend.

---

## 5. Red Light survival

Red Light is 55% of build time. Without preparation it is dead time. With `ConfigStore` hot reload
(see [ADR-005](adr/ADR-005-hot-reload-config.md)) it becomes the most productive tuning time you
have, because you are testing on the real device with a real body.

**Productive Red Light work, ranked:**
1. Tuning `pose.json` thresholds by doing actual reps and watching the debug overlay
2. Tuning `combat.json` damage and combo curves by playing
3. Writing and testing `config/prompts/*.txt`
4. Two-phone duel transport testing
5. Swapping art assets in `files/assets/`
6. Rehearsing the pitch
7. Writing the demo script and the judge Q&A answers

**Do not attempt in Red Light:** anything needing a Gradle build, dependency changes, or refactors.

---

## 6. If everything goes wrong

The minimum viable demo, in priority order. Be able to fall back to any level instantly:

| Level | What you show |
|---|---|
| **A** | Full: duel, fatigue-adaptive boss, on-device Gemma coach, airplane mode |
| **B** | Solo fight, fatigue-adaptive boss, Gemma coach |
| **C** | Solo fight, fatigue meter as a readout, template coach |
| **D** | Solo fight, rep counting and form-weighted damage, audio feedback |
| **E** | Golden APK from an earlier hour + the recorded demo video, narrated honestly |

**Level D still demonstrates the core thesis and still scores.** Level E is not a disaster if you
own it — judges respond far better to "here is exactly where we got to and why" than to a demo that
visibly fails while being described as working.
