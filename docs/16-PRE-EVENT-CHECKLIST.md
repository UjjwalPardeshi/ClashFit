# 16 · Pre-Event Checklist

**Today is 21 August 2026. Pune Phase 1 closes 1 September, 23:59 IST. Target submission: 29
August.** The event is 5–6 September.

The purpose of these eleven days is not to build the product. It is to make every hard problem
boring before you walk in.

---

## Today — 21 August

- [ ] **Register for the Pune City Battle.** You cannot submit without it, and registration closes
      at the same cutoff.
- [ ] **Also register for Hyderabad (26–27 Sep, submission 22 Sep).** Separate ₹6L pool, separate
      submission, three more working-professional Finale slots, and the same prep carries over.
      Only tick it if you can genuinely attend in person.
- [ ] Open the Pune dashboard. **Check whether problem statements are published** — there is a
      mandatory Step 2 where the team leader locks one before the idea form unlocks.
- [ ] Decide who is team leader. Only the leader can lock the problem statement and submit.
- [ ] Ujjwal joins the team with the 6-character code. Both registered as working professionals.
- [ ] **Accept the Gemma licence on Hugging Face and start the download.**
- [ ] Email sameera@reskilll.com: does a Grand Finale entry have to be a different project if we
      compete in a city battle? The organisers flagged their own answer as unconfirmed.

---

## 22–23 August · Prototype spike

Goal: prove the pose → rep → damage loop, and produce footage for the video.

- [ ] New **public** repo, `clashfit-prototype`, dated. This is not the event repo.
- [ ] CameraX + MediaPipe Pose Landmarker, landmarks on screen
- [ ] Squat rep state machine with hysteresis
- [ ] Depth + ROM form score
- [ ] One boss, HP bar, damage on rep
- [ ] **Trace recorder** — dump landmark frames to JSON lines (this is the hour that pays for itself)
- [ ] Record fixtures F1–F5 from `14-TEST-PLAN.md`

---

## 24–25 August · On-device LLM

You have both shipped on-device inference before, so this is **not** a feasibility spike. It is a
latency and thermal measurement on this specific silicon, plus prompt work. Move faster here and
give the extra time to 26 August.

- [ ] MediaPipe LLM Inference running with `gemma-3n-e2b-int4`, GPU delegate
- [ ] Measure: cold load time, tokens/sec, peak RAM
- [ ] **Measure again with a 30fps camera pipeline running concurrently** — this is the number that
      confirms the "never run them together" rule and sizes the rest gap
- [ ] Tune the COACH and BOSS prompts against a hand-written telemetry payload until the output is
      quotable, and save them as `config/prompts/*.txt`
- [ ] Build the output validator (sentence cap, hallucinated-numeral check, blocklist)
- [ ] Confirm: generation under 4 seconds between sets. If not, drop to Gemma 2 2B.
- [ ] **Phase 1 form: tick "Deployed local LLMs on-device."** It is true, and it is the top
      shortlisting signal on that dropdown.

---

## 26 August · Duel transport — **now critical, the duel is Tier 1**

This spike is the difference between two hours of Sunday-night work and a lost lane. Give it the
whole day.

- [ ] `HotspotSocketTransport` between two Android phones
- [ ] Event-sourced HP with dedupe and `recent` tail repair
- [ ] **Test in an RF-hostile place** — a mall, a food court, a co-working floor
- [ ] Measure: time to connect, packet loss, reconnect behaviour
- [ ] Try `NearbyTransport` in the same place and compare honestly
- [ ] Implement and test the event-sourced `recent`-tail repair with artificial 30% packet loss
- [ ] Verify: duplicate flood changes nothing; out-of-order arrival changes nothing
- [ ] Time the full lobby → countdown sequence. Target under 30 seconds.
- [ ] Record the ADR-004 decision with real numbers

---

## 27 August · Deck and video

- [ ] Nine-slide deck per `11-DEMO-SCRIPT.md` and the outline in the battle plan
- [ ] Architecture diagram
- [ ] 75-second video, shot per the beat sheet — **airplane mode visible in the status bar**
- [ ] Export deck as PDF, under 25 MB

---

## 28 August · Assets

- [ ] Boss frames generated and consistent (fixed prompt + seed, kept in `assets/PROMPTS.md`)
- [ ] All audio sourced, CC0/CC-BY, credits recorded
- [ ] ~25 template coach lines and ~15 boss taunts written
- [ ] Calibration cue copy written
- [ ] Everything staged in a folder ready to copy to the phone at check-in

---

## 29 August · Submit

- [ ] Leader locks the problem statement
- [ ] Paste the title, description, uniqueness, prior-work and disclosure copy
- [ ] Upload the deck; paste the video and prototype URLs
- [ ] **Answer both proficiency dropdowns honestly** — after 24–25 Aug you should be able to tick
      "Deployed local LLMs on-device" truthfully
- [ ] Tick the originality confirmation
- [ ] Screenshot the confirmation

---

## 30–31 August · Buffer and rehearsal

- [ ] Rehearse the 3-minute pitch aloud, timed, three times
- [ ] Rehearse the Eval R1 forty-second demo
- [ ] Write the ADRs with the real measured numbers from 24–26 Aug
- [ ] Dry-run the whole build plan on paper: who does what in each block

---

## 1–4 September · Standby

- [ ] Watch for the shortlist email. **Book nothing non-refundable before it arrives.**
- [ ] Pack per `15-ASSET-BRIEF.md` §7 — especially the two phone tripods
- [ ] Confirm venue when announced
- [ ] Charge everything
- [ ] Set the staggered-sleep alarms on both phones **before** you travel

---

## Check-in morning — 5 September, 08:00

- [ ] Collect both loaner phones
- [ ] Developer options + USB debugging on both
- [ ] Pair Office Kit on both — **and leave it connected all weekend**
- [ ] Side-load the Gemma `.task` over Office Kit file transfer
- [ ] Copy the asset folder and config files to both phones
- [ ] Measure the actual camera framing distance for a squat on the loaner, at the desk
- [ ] Create the fresh event repo, first commit before 11:00
- [ ] Note the exact Red Light rules from the 10:00 briefing — they may differ from the published
      schedule

---

## Standing reminders

- Travel and accommodation are not provided. Book only after the shortlist email.
- Devices remain iQOO property. Keep them in the venue, return both before exit.
- Original work only, written during the event. The prototype stays in its own repo and is
  disclosed.
- Teams cannot mix students and working professionals. Both of you are professionals — this is
  already satisfied.
