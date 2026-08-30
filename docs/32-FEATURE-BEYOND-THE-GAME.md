# 32 · Beyond the game — four health tools

Decided 29 Aug 2026. Companions to [31-FEATURE-ALARM.md](31-FEATURE-ALARM.md),
which established the pattern: a real utility whose price is verified movement.
Two of these extend that pattern. Two do not — they are measurement, and they
never involve a boss.

All four run on the pose pipeline that already exists. None of them needs a
wearable, a chest strap, GPS, or a network.

On the landing page they live in `#tools`, labelled by what they require:
**needs the native app** or **camera only**. No timeline is claimed for any of
them, because the page is a pre-launch page and the whole product is dated 2026.

---

## 01 · App-unlock tax

Give one app a daily budget. Past it, it stays shut until the reps are paid.

- Same `SessionEngine` grading as a boss fight, so the price cannot be faked.
- The budget is user-set and user-deletable **on any day, without friction**.
  A lock somebody cannot get out of is a trap, and a trap is a defect. The
  escape is one tap, and it is never hidden behind a countdown or a dark pattern.
- **Needs the native app**: Android usage-access permission and an accessibility
  or overlay path. Impossible in a browser.

Open question: does an unlock cost more the second time in a day, or is a flat
price more honest? Escalation is stickier and closer to gambling design; a flat
price is the one we would defend to a judge.

## 02 · Desk timer — sixty seconds, every fifty minutes

The eight hours nobody trains in. One minute of verified movement per hour.

- Ten squats, or a hold long enough to count. Interval and price both user-set.
- Runs in the current web build with no native shell — this is the feature that
  speaks most directly to the working-professional bucket.
- Must be silenceable for a block of hours. A prompt during a meeting is worse
  than no prompt at all.

## 03 · Posture score

While you work, one frame every few minutes, scored for neck flexion and
shoulder elevation. A number at the end of the day, a curve across the week.

- **Frames are read and discarded.** Nothing is written to disk, nothing is
  uploaded. The score is the only artefact. This has to be true in the code and
  visible in the UI, or the feature is a surveillance product wearing a health
  costume.
- Sampling, not streaming: the camera opens for a frame and closes. Battery and
  the red camera indicator both matter to whether anybody keeps it on.
- **Never a diagnosis.** Neck flexion is a posture measurement, not a
  musculoskeletal finding. The same blocklist that governs the coach's language
  (`injury`, `risk`, `diagnos`, `cleared`, `abnormal`) governs this copy.

## 04 · Guided breathing, verified

After a set: six breaths a minute for two minutes, with the phone confirming the
rate from chest and shoulder landmark motion rather than trusting the user.

- Reuses the same landmark stream; the signal is low-frequency vertical motion
  of the shoulder and chest points, which is a cleaner problem than rep
  detection, not a harder one.
- Reports the rate actually held. That is the whole point — every other
  breathing app asks you to believe you followed along.
- **No claims about heart rate, HRV, blood pressure or stress.** We cannot
  measure any of them and will not imply that we can.

---

## What these four share

1. They are useful to somebody who never opens the game.
2. They need no hardware beyond the phone.
3. Nothing leaves the device, which is the same promise the rest of the product
   makes and the reason it is credible here.
4. None of them is a medical device, and the copy on every one of them has to
   keep passing the language validator in `test/run.js`.
