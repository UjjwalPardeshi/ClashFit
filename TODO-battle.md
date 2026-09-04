# ClashFit · battle build · 4 Sep 2026

Event is 5–6 Sep. Priority: polish first, then close promise gaps by visibility.

**State:** 528 tests passing, 0 failures. 46 screenshot baselines. 13 commits ahead
of origin, none pushed. Nothing broken that I know of.

## Audit

438 promises across docs, deck and landing page. 322 present, 48 honestly
labelled roadmap, 81 gaps. Most remaining gaps are roadmap items the docs
already flag. Audio and the "Back at it" streak rule were false positives.

## Done

- [x] Firebase keys wired, installed, signed in, sign-up verified on the phone
- [x] THE PACEMAKER: a real character in code, six states from the asset brief
      (idle, hit, enrage, desperation, stagger, 1.2s death)
- [x] Chart kit: trend line, bars, stacked bar, heatmap, radar, donut, legend
- [x] Character sheet: all seven health domains on a radar, honest about the
      two that need sleep and food data
- [x] Streaks: twelve-week training heatmap
- [x] History: form trend and a verdict donut over every rep
- [x] Segmented HP bar and per-exercise calibration silhouettes
- [x] Share a result as an image, launcher shortcuts, predictive back
- [x] Screen-reader labels on every chart, bar, ring and badge
- [x] On-device coach: the placeholder replaced with the real MediaPipe call,
      guarded so any failure falls back to templates
- [x] About and How to play screens
- [x] Screenshot tests seed a real six-week history, so the data screens are
      reviewed with content rather than empty states
- [x] Reviewed all 46 screens as contact sheets and fixed what they showed:
      week bars rendered as circles, the form trend read as a solid block, and
      Power pegged at 100 after a fortnight
- [x] Shipped pacers now sort easiest first instead of in map order
- [x] The empty character sheet says what to do about being empty
- [x] Accounts, boards, levels, badges and the weekly challenge written down in
      `docs/34-ACCOUNTS-SOCIAL.md` — the layer had no document at all
- [x] `docs/29-BUILD-STATUS.md` made true again: it still said the Android app
      was work to be created at the event
- [x] Casual sessions no longer count toward the weekly damage target, which is
      what the weekly screen has always told the player
- [x] Six screens render at 1.5× text in the suite; the Progress stat strip
      collided there and now wraps

## Now

- [ ] Read back the marketing-copy audit and the Kotlin review, fix what is real
- [ ] Re-run the suite, re-render, commit

## Waiting on the phone

- [ ] Install the current build and walk the whole app on the test account
      (`omkar.test@clashfit.app`). Everything below the fold on Progress, You,
      Character and History has only been seen as a JVM render.
- [ ] One real fight end to end, to watch the boss animate on a real screen

## Waiting on Omkar

```bash
cd /home/omkar-kadam/Desktop/ClashFit/firebase
npx -y firebase-tools login
npx -y firebase-tools deploy --only firestore
```

Until that runs, every leaderboard read fails closed and the board honestly
says it is not reachable. Sign-up and sign-in already work without it.

## Deliberately not doing

rPPG heart rate, hydration, meal timing, AR, NFC pairing, crew objectives,
Outbreak, extra clinic protocols, home-screen widget. Every one is roadmap in
the docs, and saying so is worth more than a rushed stub.
