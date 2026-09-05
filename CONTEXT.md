# ClashFit — context for a new session

Read this first if you are joining the project, with or without Claude. It is the map, the rules
that are easy to break, and the parts that are not obvious from the code.

**What it is.** Your body is the controller, your camera is the referee. Every rep is graded before
it counts, and the grade becomes damage against a boss. Pose scoring runs on the phone; camera
frames and landmarks never leave it. Built for the iQOO Hackathon 2026, Pune City Battle, HealthTech.

**Team.** Da Goats — Omkar Kadam and Ujjwal Pardeshi. **Live site:** clash-fit.vercel.app.

---

## 1. Repository map

| Path | What it is |
|---|---|
| `android/` | The production app. Kotlin, Compose, Room, MediaPipe. This is the deliverable. |
| `src/`, `app.html`, `index.html` | The original TypeScript prototype and the marketing site. Vercel serves the site from the repo root. |
| `serve.js`, `package.json`, `test/` | The prototype's server and its own test suite. `npm start` serves the site, `npm test` runs `test/run.js`. |
| `config/exercises/`, `config/clinic/`, `config/ghosts/`, `config/*.json` | The prototype's copy of every exercise, clinic protocol, pacer ghost and tuning record. |
| `android/app/src/main/assets/config/` | The app's copy of the same records. **Must stay byte-identical to `config/`.** |
| `docs/` | Thirty-plus pre-event design documents. `docs/README.md` is the index. |
| `ClashFit_Exercise_Detection_Source_of_Truth.md` | The authoritative spec for landmarks, angles and per-exercise detection. Newer than most of `docs/`. |
| `tools/angles.html` | The angle-measuring page. Same model and filter as the phone, in a browser. |
| `traces/` | Recorded landmark traces. `reference-*.jsonl` are the four picker animations as the shipped model reads them, replayed by `ReferenceFormTest`; `synthetic-f3-to-failure.jsonl` is replayed by `TraceReplayTest` and is duplicated into the app's assets. `tools/make-trace.js` writes new ones. |
| `firebase/` | Firestore rules, indexes and the data model. Deployed with the Firebase CLI, not by the app. |
| `deck/`, `deck-phase1/` | The event-day and Phase-1 pitch decks. Not code; they record what was promised. |
| `README.md`, `android/README.md`, `firebase/README.md` | The per-area detail this file compresses: full command lists, key names, deployment steps. |
| `android/ci/android.yml` | CI, parked outside `.github/` on purpose. See rule 4. |

---

## 2. Rules that are easy to break

1. **Push to both remotes.** `origin` is `omkarrr88/ClashFit`, `fork` is `UjjwalPardeshi/ClashFit`.
   Vercel deploys from the fork, so `git push origin main && git push fork main` every time.
2. **The two config copies must stay identical.** Edit `config/exercises/<id>.json`, then copy it to
   `android/app/src/main/assets/config/exercises/<id>.json`. Both `index.json` files list every id
   on disk and must match each other. Tests read the app copy; the prototype reads the other.
3. **Source the environment before Gradle.** `source ~/.clashfit-android-env.sh`. The whole
   toolchain lives in the user's home directory with no sudo.
4. **Never commit under `.github/workflows/`.** The token has no workflow scope and GitHub rejects
   the entire push. The workflow file lives at `android/ci/android.yml` until someone supplies a
   scoped token.
5. **One Gradle build at a time.** The machine is CPU and memory bound. Run builds detached and read
   the log rather than holding a foreground process.
6. **Screenshot tests need JDK 21.** Fourteen Robolectric classes are pinned to SDK 36, which
   refuses Java 17. Point `JAVA_HOME` at `~/.jdks/jdk-21.0.12.1+1` for `testDebugUnitTest`. The app
   itself still compiles to JVM 17.
7. **Debug and release APKs are signed differently.** Switching between them needs an uninstall
   first, which wipes the app's local data. If `adb install` says
   `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, somebody sideloaded the other kind; check
   `adb shell dumpsys package com.clashfit | grep installerPackageName` before you wipe anything.
8. **Keys are never in git.** Firebase configures itself from `android/app/google-services.json`,
   the file the console gives you, which is git-ignored at both levels. The build reads the three
   cloud keys straight out of it, and `local.properties` or an environment variable still overrides.
   The cloud coach's `OPENROUTER_API_KEY` arrives the same way, from `local.properties` or the
   environment; without it the coach falls back down its ladder and nothing breaks. The copy of
   `google-services.json` at the repo root is read by nothing; the build looks in `android/app/`.
9. **Firestore rules ship by hand, not with the app.** From `firebase/`, run
   `npx -y firebase-tools deploy --only firestore`. Until they are deployed a production-mode
   database denies every read and write, which on the phone looks like a broken leaderboard
   rather than a permissions problem.
10. **A release APK will sign itself with the debug key if you let it.** `KEYSTORE_FILE`,
   `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` come from the environment, and with none of
   them set `assembleRelease` still succeeds — quietly using the debug signing config.

---

## 3. Building, testing, installing

```bash
source ~/.clashfit-android-env.sh
cd android
./gradlew :app:assembleDebug            # ~2 min warm, 168 MB APK
./gradlew :app:assembleRelease          # ~17 min cold, 71 MB APK
JAVA_HOME=~/.jdks/jdk-21.0.12.1+1 ./gradlew :app:testDebugUnitTest   # 836 tests
./gradlew :app:lintDebug                # fails the build on errors
./gradlew :app:recordRoborazziDebug     # re-render the 82 screenshot baselines
./install-to-phone.sh                   # build, install, launch, report
cd .. && npm test                       # 165 tests, the prototype engine, no Android at all
```

Maven Central intermittently times out on `org.robolectric:nativeruntime-dist-compat:1.0.18`, a
159 MB jar. If it does, download it with curl into a local directory laid out as a Maven repo and
pass an init script that inserts that repo first. Dropping the jar into the Gradle cache is not
enough.

The site runs with `node serve.js` on port 8080. `node test/replay.js traces/<file>` puts a
recorded trace back through the prototype engine, and `node tools/make-trace.js` writes new ones.

---

## 4. How a rep becomes damage

```
camera 30 fps → MediaPipe pose landmarker (33 landmarks, world + image)
    → the counter                  (StageCounter, or the hysteresis state machine)
    → form score                   (depth · range · tempo · alignment)
    → fatigue                      (velocity loss, range loss, pause growth)
    → combat                       (damage, combo, boss phases, mercy)
    → HUD
```

**Two counters, chosen per exercise by config.**

- `"counter": "STAGE"` uses `StageCounter`, the rule ported from
  [fitmon](https://github.com/UjjwalPardeshi/fitmon): a stage flips to *rest* when the joint passes
  one threshold and a rep counts the moment it passes the other, debounced. No dwell, no minimum
  duration, and **no smoothing** — it reads raw landmarks, so a rep registers the instant you reach
  the angle. Eight exercises use it: the four core ones below, plus the bench press, front raise,
  lunge and triceps extension.
- Everything else uses `RepStateMachine`, a hysteresis state machine over the One Euro filtered
  angle with dwell gates, minimum and maximum rep durations, and a valid-frame ratio.

**A set never ends by itself.** There is no rest phase and no idle timeout: standing still keeps
you in the fight. A set closes only when the player asks for it — a thumb up to the camera — and the
next one begins in the same breath, inside `endSet()`, which calls `onSetEnd` *before* `nextSet()`
so the listener can still read `currentSetReps` — swap those two lines and every set summarises as
empty. The coach is fetched after that and spoken over the top of a fight that never left the
screen, so the coaching is heard rather than read.

`session_set.rest_sec` outlived the feature. It is still a column in schema v6 and is still written,
always as 0, because dropping it would cost a migration and the rows written before the change are
worth keeping. Nothing reads it.

**The One Euro filter lags about 160 ms.** Anything on the filtered path needs each end of a rep
held for roughly 0.4 s. The stage counter deliberately bypasses it. Tests on the filtered path use
500 ms holds for this reason.

**Angles are measured on the 3D world landmarks**, which are metric and hip-centred, so they do not
change when you move nearer the camera or turn slightly. When both sides are visible above 0.6
confidence the two sides are averaged; otherwise the better-seen side is used.

---

## 5. The four core exercises

Measured by hand against the BlazePose keypoints, and listed for all six rep modes: Boss Fight,
Time Attack, Ghost Race, Survival, Boss Rush and Duel. `FEATURED_EXERCISES` in
`ui/screens/picker/ExercisePickerScreen.kt` is the whole selectable set — the picker and the library
both filter to it, so the other fifty-three records on disk are loaded and scored but never offered.
Widen that list to put them back; nothing else has to change.

| Exercise | Keypoints | Rest | Counts at | Sides |
|---|---|---|---|---|
| Lateral raise | 23-11-13 / 24-12-14 | both arms < 25° | both arms > 90°, out to the side, never past 110° | both, counted on the way down |
| Bicep curl | 11-13-15 / 12-14-16 | elbow > 135° | elbow < 80°, elbow within 35° of the torso, lift ≥ 120 ms | either arm, one rep |
| Shoulder press | 13-11-23 / 14-12-24 | arm-to-torso < 75° | arm-to-torso > 135°, wrist above the shoulder | better-seen side |
| Squat | 23-25-27 / 24-26-28 | both knees < 72° | both knees > 150° | both together |

Debounce is 800 ms for the curl and 1000 ms for the rest.

**The curl has two guards the angle cannot supply**, added 6 Sep 2026 because the player asked for
reps that only count when they are clean. `armLine` requires the upper arm to be within 35° of the
torso **at the counting frame**, measured on the shoulder and elbow the rep already uses against the
hip-to-shoulder axis: an elbow driven forward and up closes through exactly the same angles as one
pinned to the ribs, so without it a swung rep counted identically to a curled one. `minConcentricMs`
requires the lift — rest to the counting line — to take 120 ms; `debounceMs` never did this, it only
spaces reps apart, so a flung rep counted as long as the previous one was far enough back.

Both numbers are **measured**, from a 65 s logcat capture of the player's own curls on 6 Sep 2026.
At the counting frame a correct curl reads 3.9-22.1° off the torso, and the lift takes 159-262 ms.
The guesses they replaced — 30° and 500 ms — refused all fourteen reps he performed: ten died on
tempo, four on the arm line. The tempo gate is deliberately modest at his pace: it refuses a throw,
not a brisk rep.

**The arm line is judged at the counting frame, not accumulated over the rep.** That was the four:
somewhere earlier in a good rep the reading spikes as far as 48°, so an any-frame rule threw away
good reps on a single noisy frame. The `plane` guard is checked at that same moment for the same
reason. The lesson generalises — a form guard on a noisy landmark belongs at the one instant the
movement is defined, not across every frame of it.

There is deliberately **no guard against hammer curls**, which the player also asked for. Telling a
supinated curl from a neutral grip needs landmarks 17-22; the model reports those at 0.3-0.7
confidence against 0.8-0.95 for the torso, and drops them exactly when the forearm occludes the
hand, which is most of a curl. Worse, `Condition.holds` filters NaN, so a missing-hand frame passes
silently: the guard would report strictness it was not delivering. It is better absent than
pretended.

Both guards are read off the **working** arm, not "either" arm. Under `EITHER` the arm left hanging
satisfies rest on every frame and is tucked by definition, so judging form on either arm excused
every swing of the arm doing the work, and the rest phase never ended — which made every one-armed
curl measure as an instant lift. `StageCounter` picks the leading arm for both, the same one it
already takes the rep's angle from.

The lateral raise is the odd row, and it is worth understanding why. Its counting column has three
numbers in it, not two: a floor at 95, a ceiling at 105, and a direction. **The hip–shoulder–elbow
angle cannot tell a lateral raise from a front raise** — the arm is a hundred degrees away from the
torso whether you take it out to the side or straight out in front — so on the angle alone every
front raise counted as a lateral one, which is what the player reported on 6 Sep 2026. The fix is a
third axis: `"plane": [">", 0.5]` in the stage block asks for the upper arm to lie within sixty
degrees of straight out to the side, measured as a cosine against the line between the shoulders
with the torso's own lean projected out of it, so it survives leaning, turning and a crooked phone.
The front raise carries the same guard read backwards, `["<", 0.5]`, because a lateral raise puts
the wrist over the shoulder too and the two exercises were counting each other in both directions.
Measured on `traces/reference-lateral-raise.jsonl`: at the top of a correct raise the upper arm
reads 0.62 to 1.00 sideways, median 0.84, and a raise out in front reads about zero. That gap is
why 0.5 is safe. `StageCounter.lateralFraction` is the whole of it.

**The rest threshold is the one that bites, and it bites silently.** Under `countAt: RETURN` a rep
can only land on a frame where the movement is back at rest, so an unreachable rest threshold does
not make counting strict — it makes it *silent*, and no amount of widening the counting band brings
the reps back. `rest < 20` shipped for one build and looked perfectly reasonable. Captured off the
player's own phone that night, 2710 frames with the arms hanging down: only **26%** of them read
under 20 on both arms at once, and after a raise it took **up to twenty seconds** for a qualifying
frame to appear. At `< 25` it is 58% and at most 1.8 seconds. An arm hanging straight down does not
read zero at the shoulder — the shoulders are wider than the hips, so the shoulder-to-hip line
slants inward and a hanging arm makes a real 20-25° angle with it.

**And in `BOTH` mode the ceiling is judged on the two arms together, not on whichever reads higher.**
The same capture: the player's left shoulder reads **6.3° higher than the right on every single
rep**, so the per-arm maximum is dominated by whichever arm carries the larger measurement bias.
Judging by the higher arm threw away 8 of 27 raises performed identically to the ones it accepted;
judging on the mean — which is already the number this mode records as the rep's angle — accepted
all 27. A genuine one-armed fling still fails, because one arm at 140 and one at 95 is 117 to the
mean. If you ever wonder whether a threshold is reachable, do not reason about it: set
`SessionEngine.diagnostics` and read what the model says while the person moves.

**Three of these four rows are no longer fitmon's, and the reason is the single most important
thing to know before tuning anything.** Angles come from the 3D world landmarks, whose depth axis is the
noisiest, and a limb pointed toward or away from the camera is nearly edge-on to it. So an arm the
player believes is straight reads 150 to 159 on the phone, not the 170 to 180 the geometry says,
and a pressed-out arm overhead reads about 146. Every threshold taken off a diagram armed a counter
the model can never reach, and the exercise silently counted nothing: the press asked for 160, the
curl for 80, and the lateral raise asked for the wrist *above* the shoulder when a textbook lateral
raise ends level with it. Measure before choosing a threshold; do not take the number off a diagram.

`traces/reference-*.jsonl` are the shipped model's own reading of the four picker animations, and
`ReferenceFormTest` replays them through the real engine and the real config. It asserts only that
a correct rep counts at all — nothing about how many or how well scored — so a threshold that
drifts out of human reach fails the build instead of failing the player.

Each of the four rows in the picker opens into a looping clip of the movement, which is where those
reference traces come from. `ui/components/ExerciseDemo.kt` plays them, and they are **animated
WebP** in `res/drawable-nodpi/`, not video: a `LazyColumn` of rows each owning a media player is a
lifecycle problem, while `AnimatedImageDrawable` needs no dependency and no surface. It wants API 28
and the app's floor is 29. `drawable-nodpi` because the frames are pre-rendered at a fixed 440 px
and must not be resampled per density. Reduce-motion holds the first frame instead of looping.

Two further rules were added on top of fitmon, because fitmon miscounts without them: a triceps
extension needs the wrist above the shoulder to be at rest, and dropping the arm clears the stage,
so curling at your sides after an overhead set cannot count.

Every other exercise follows `ClashFit_Exercise_Detection_Source_of_Truth.md`.

---

## 6. The camera pipeline, and the four ways it can lie

Everything above depends on the landmarks being right, and four things between the sensor and the
engine can quietly make them wrong. All four were wrong at once on 5 Sep 2026, which drew the
skeleton in a wide box beside the player and held the cue at "Come closer." for a whole session.
They live in `perception/MediaPipePoseSource.kt` and `perception/FrameGeometry.kt`.

1. **The frame arrives on its side.** The sensor is mounted at an angle to the phone, and CameraX
   reports the clockwise turn needed to stand the frame up. Reading that number and not applying it
   shows the detector a body lying down. Everything downstream then fails together: the framing
   check measures the body's height as a fraction of image height, which on a sideways frame is its
   width, so it reads TOO_FAR forever.
2. **The buffer is wider than the image.** A plane's rows are padded out to its row stride. Copying
   that buffer into a bitmap of the image's width slides every row after the first sideways, and
   the picture shears into diagonal stripes. Size the copy to `rowStride / pixelStride`.
3. **`landmarks()` is not `worldLandmarks()`.** `landmarks()` is normalised to the picture, so an
   angle read off it changes when you step nearer the camera, and a distance read off it is in
   fractions of a frame rather than metres. `worldLandmarks()` is metric and hip-centred, and it is
   what every threshold, every angle and the jump-height scale are written against. Feeding the
   picture coordinates into `PoseFrame.world` makes all of them meaningless while still looking
   plausible.
4. **Frames must arrive in order.** Every window in the engine is a `now - then > n` test, and a
   negative difference passes none of them, so one swapped pair can switch off the rep counter, the
   cadence window and the hold timer at once. Frames go into a conflated channel with `trySend`
   from the detector's own callback thread, and one no newer than the last is dropped. Never hand
   a frame to a coroutine on a multi-threaded dispatcher on the way in.

`FrameGeometry` holds the arithmetic for the first two and carries no Android types, so
`FrameGeometryTest` pins it without a device. **The aspect ratio the overlay is given must be the
upright one.** `PreviewView` fills the view and crops the overflow, and `ExercisePoints` reproduces
that crop from `sourceAspect`; give it the sensor's landscape shape and the dots land in a wide box
beside the body even when everything else is right.

The front camera is mirrored in the preview, so image landmarks have their x mirrored to match.
World landmarks are deliberately left alone, which keeps the model's left arm the player's left arm.

---

## 7. What is on screen during a fight

The camera fills the frame. On top of it are the BlazePose dots exactly as fitmon draws them: a red
dot on every confident landmark, blue lines along the twelve body connections, and the measured
angle beside the joint it is measured at. There is no avatar and no exo-suit; `ExercisePoints` in
`perception/` replaced them so that what you see is what the counter reads.

The boss stands in the upper part of the frame and takes damage per rep. It also hits back: in Boss
Fight, Boss Rush and Survival it attacks every five seconds for eight damage after a four-second
grace, you heal three per counted rep, and running out of health ends the session as `DEFEATED`.
Your health bar sits under the boss's, with a strip that fills as the next attack charges.

---

## 8. What the app does besides fight

The boss fight is the spine, but it is no longer the whole app. Each of these is self-contained and
tested on the JVM, so you can work on one without a phone.

| Area | Package | What it is |
|---|---|---|
| Zombie Run | `engine/games/ZombieRunGame.kt`, `ui/screens/zombierun/` | The outdoor chase. Pure Kotlin in a local metric frame, so the rules are tested indoors. Layered over the run tracker, so a chase is also a recorded activity. Its config key is still `OUTBREAK`, because that string is persisted in saved sessions; only what a person reads was renamed. |
| The outdoors | `run/`, `map/` | GPS fixes, filtering and dead reckoning, moving time, elevation, cadence, route maths, map tiles. The only part of the app that asks for location. |
| Sharing | `share/` | The share card. The one thing that deliberately leaves the phone, so it has its own rendered test. |
| The coach | `coach/` | Three rungs, in order: Gemma on the phone if its weights are installed, then the cloud model but only if the player switched Cloud coach on, then a template bank that always has a line and needs nothing. `coach/chat/` answers questions, and `FactSheet` is what bounds it: the coach can only say what was measured. |
| The referee's eyes | `coach/RefereeEyes.kt`, `perception/vision/FrameRing.kt` | A rep finishes a second or two after its deepest moment, by which time that frame has gone. The ring keeps twenty quarter-scale frames, about four seconds and roughly 3 MB, so the referee can look back at the bottom of the rep. |
| Hand control | `perception/gesture/` | Seven MediaPipe gestures read off the same frames as the body, on every third frame, and only while the fight wants them. A raised palm during calibration means nothing. |

---

## 9. Measuring new thresholds

```bash
node serve.js          # then open http://localhost:8080/tools/angles.html
```

The page runs the same model and the same filter as the phone. Pick an exercise, do reps in front of
the laptop camera, and everything is drawn on the video: the skeleton, the angle at each measured
joint, the thresholds, the machine state and a rep log. It suggests thresholds from what your reps
actually reached.

A number is never the only thing that moves. Changing a threshold means all of: both config copies,
the tests that pin it, the table in `docs/19-EXERCISE-LIBRARY.md`, and the table in §5 above. Run
`diff -rq config android/app/src/main/assets/config` afterwards — it must print nothing.

The other way to measure is to replay: `ReferenceFormTest` puts `traces/reference-*.jsonl` back
through the real engine and the real config, so a proposed number can be tried against a known-good
rep without a camera or a phone.

---

## 10. Where things live in the app

`android/app/src/main/java/com/clashfit/`

| Package | What it holds |
|---|---|
| `core/model` | Landmarks, session state, combat state, enums. No logic. |
| `core/config` | The JSON records as Kotlin types, and the loader. |
| `core/pose` | `SyntheticBody`, which builds landmark sets for tests with exact joint angles. |
| `engine/core` | `Geometry`, `RepStateMachine`, `StageCounter`, `FormScorer`, `CombatEngine`, the One Euro filter. |
| `engine/session` | `SessionEngine`, the frame loop that ties all of the above together. |
| `engine/detect` | The four non-rep detector families: holds, cadence, ballistic, pose match. |
| `engine/games` | Per-mode rules: the breaker, the siege, the sigil, the pursuit, the Zombie Run. |
| `engine/coach`, `engine/summary` | Which coach line to pick and whether it may be said, and the per-session numbers the summaries read. |
| `perception` | Camera, MediaPipe, frame geometry, and the on-screen landmark overlay. |
| `perception/gesture`, `perception/vision` | The hand, and the ring of recent frames the referee looks back at. |
| `coach` | The three-rung coach, its chat, and the fact sheet that bounds what it may say. |
| `run`, `map`, `share` | The outdoors: tracking, tiles, and the share card. |
| `ui/screens/*` | Compose screens, one package per area. `session/` is the fight. |
| `ui/nav`, `ui/theme`, `ui/components`, `ui/insight` | The nav graph and scaffold, colour and type, the shared widgets, and the post-session charts. |
| `data`, `cloud` | Room and preferences on the phone; Firestore leaderboards, friend codes and score sync off it, with a `NoCloud` stand-in when there is none. |
| `play`, `desk`, `voice` | The mode hub, the desk-break scheduler and its receivers, and voice commands. |
| `core/util`, `util` | A `Clock` seam so time can be faked in tests, and the crash log. |
| `auth`, `duel`, `meta`, `alarm`, `audio` | Accounts, live play, progression, alarms, sound. |

Tests are under `android/app/src/test/java/com/clashfit/`. Two in `engine/session/` replay real
landmarks through the shipped configuration rather than synthetic frames: `TraceReplayTest`, a full
recorded set and the closest thing to an integration test, and `ReferenceFormTest`, which asserts
that a textbook rep still counts for each of the four measured exercises.

---

## 11. Things worth knowing before you change something

- **`SessionEngine` is the one place that owns a session.** Adding a counting rule means adding it
  there and in a config record, not in a screen.
- **Adding an exercise needs no code**, only a JSON record in both copies plus an index entry.
- **`SyntheticBody.world()` gives exact joint angles**, so a detector can be tested without a camera.
  It bends the knee in the sagittal plane, so any measurement that drops the depth axis degenerates
  on it. Measure in 3D.
- **Form scoring is separate from counting** and stays that way. The counter decides whether a rep
  happened; the scorer decides what it was worth.
- **Some counting gates are deliberately tight, and a test pins each one.** The chair squat arms at
  115° against a target of 110°, five degrees where the squat's own pair are fifteen. A stand that
  only reaches 120° therefore counts nothing and says nothing, because the `tooHigh` cue is emitted
  only for a rep that was counted. That is the intended strictness and it stays; `test/run.js` pins
  it so it reads as a choice rather than a surprise. Read the test before you widen a gate.
- **The permission set is a lock file.** `checkPermissions<Variant>` fails the build if the merged
  manifest gains a permission nobody approved. INTERNET is deliberately present for Firebase.
- **A skipped test looks exactly like a passing one.** `ReferenceFormTest` was written with
  `assumeTrue` around a missing-trace check, pointed at the wrong directory, and reported green
  while measuring nothing at all; only the JUnit XML (`skipped="1"`) gave it away. It now fails
  instead. `TraceReplayTest` still uses `assumeTrue`, deliberately, because its trace is optional.
  If a test guards something that must never regress, do not let it skip — and read
  `app/build/test-results/` rather than trusting an exit code.
- **Nothing records where the four demo clips came from.** They were supplied to the team rather
  than sourced, and no licence or attribution is written down for them — `img/CREDITS.md` covers the
  site photography only. Settle that before any of them is published outside the hackathon.
