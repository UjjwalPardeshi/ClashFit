# ClashFit — context for a new session

Read this first if you are joining the project, with or without Claude. It is the map, the rules
that are easy to break, and the parts that are not obvious from the code.

**What it is.** Your body is the controller, your camera is the referee. Every rep is graded before
it counts, and the grade becomes damage against a boss. Pose scoring runs on the phone; camera
frames and landmarks never leave it. Built for the iQOO Hackathon 2026, Pune City Battle, HealthTech.

**Team.** Omkar Kadam and Ujjwal Pardeshi. **Live site:** clash-fit.vercel.app.

---

## 1. Repository map

| Path | What it is |
|---|---|
| `android/` | The production app. Kotlin, Compose, Room, MediaPipe. This is the deliverable. |
| `src/`, `app.html`, `index.html` | The original TypeScript prototype and the marketing site. Vercel serves the site from the repo root. |
| `config/exercises/`, `config/*.json` | The prototype's copy of every exercise and tuning record. |
| `android/app/src/main/assets/config/` | The app's copy of the same records. **Must stay byte-identical to `config/`.** |
| `docs/` | Thirty-plus pre-event design documents. `docs/README.md` is the index. |
| `ClashFit_Exercise_Detection_Source_of_Truth.md` | The authoritative spec for landmarks, angles and per-exercise detection. Newer than most of `docs/`. |
| `tools/angles.html` | The angle-measuring page. Same model and filter as the phone, in a browser. |
| `traces/` | Recorded landmark traces, replayed by `TraceReplayTest`. |
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
6. **Screenshot tests need JDK 21.** Thirteen Robolectric classes are pinned to SDK 36, which
   refuses Java 17. Point `JAVA_HOME` at `~/.jdks/jdk-21.0.12.1+1` for `testDebugUnitTest`. The app
   itself still compiles to JVM 17.
7. **Debug and release APKs are signed differently.** Switching between them needs an uninstall
   first, which wipes the app's local data.

---

## 3. Building, testing, installing

```bash
source ~/.clashfit-android-env.sh
cd android
./gradlew :app:assembleDebug            # ~2 min warm, 150 MB APK
./gradlew :app:assembleRelease          # ~17 min cold, 70 MB APK
JAVA_HOME=~/.jdks/jdk-21.0.12.1+1 ./gradlew :app:testDebugUnitTest   # 661 tests
./gradlew :app:lintDebug                # fails the build on errors
./gradlew :app:recordRoborazziDebug     # re-render the 72 screenshot baselines
./install-to-phone.sh                   # build, install, launch, report
```

Maven Central intermittently times out on `org.robolectric:nativeruntime-dist-compat:1.0.18`, a
159 MB jar. If it does, download it with curl into a local directory laid out as a Maven repo and
pass an init script that inserts that repo first. Dropping the jar into the Gradle cache is not
enough.

The site runs with `node serve.js` on port 8080.

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
  the angle. This is what the four core exercises use.
- Everything else uses `RepStateMachine`, a hysteresis state machine over the One Euro filtered
  angle with dwell gates, minimum and maximum rep durations, and a valid-frame ratio.

**The One Euro filter lags about 160 ms.** Anything on the filtered path needs each end of a rep
held for roughly 0.4 s. The stage counter deliberately bypasses it. Tests on the filtered path use
500 ms holds for this reason.

**Angles are measured on the 3D world landmarks**, which are metric and hip-centred, so they do not
change when you move nearer the camera or turn slightly. When both sides are visible above 0.6
confidence the two sides are averaged; otherwise the better-seen side is used.

---

## 5. The four core exercises

Measured by hand against the BlazePose keypoints, pinned to the top of every exercise list by
`featuredRank` in `ui/screens/picker/ExercisePickerScreen.kt`, and enabled for Boss Fight and Duel.

| Exercise | Keypoints | Rest | Counts at | Sides |
|---|---|---|---|---|
| Lateral raise | 23-11-13 / 24-12-14 | both wrists below the shoulders | both wrists above them | both together |
| Bicep curl | 11-13-15 / 12-14-16 | elbow > 150° | elbow < 40° | each arm counts |
| Shoulder press | 11-13-15 / 12-14-16 | elbow < 100° | elbow > 160°, wrist above the shoulder | better-seen side |
| Squat | 23-25-27 / 24-26-28 | both knees < 110° | both knees > 160° | both together |

Debounce is 800 ms for the curl and 1000 ms for the rest. Two rules were added on top of fitmon,
because fitmon miscounts without them: a triceps extension needs the wrist above the shoulder to be
at rest, and dropping the arm clears the stage, so curling at your sides after an overhead set
cannot count.

Every other exercise follows `ClashFit_Exercise_Detection_Source_of_Truth.md`.

---

## 6. What is on screen during a fight

The camera fills the frame. On top of it are the BlazePose dots exactly as fitmon draws them: a red
dot on every confident landmark, blue lines along the twelve body connections, and the measured
angle beside the joint it is measured at. There is no avatar and no exo-suit; `ExercisePoints` in
`perception/` replaced them so that what you see is what the counter reads.

The boss stands in the upper part of the frame and takes damage per rep. It also hits back: in Boss
Fight, Boss Rush and Survival it attacks every five seconds for eight damage after a four-second
grace, you heal three per counted rep, and running out of health ends the session as `DEFEATED`.
Your health bar sits under the boss's, with a strip that fills as the next attack charges.

---

## 7. Measuring new thresholds

```bash
node serve.js          # then open http://localhost:8080/tools/angles.html
```

The page runs the same model and the same filter as the phone. Pick an exercise, do reps in front of
the laptop camera, and everything is drawn on the video: the skeleton, the angle at each measured
joint, the thresholds, the machine state and a rep log. It suggests thresholds from what your reps
actually reached. Feed those numbers back into both config copies, update the tests that pin them,
and update the table in `docs/19-EXERCISE-LIBRARY.md`.

---

## 8. Where things live in the app

`android/app/src/main/java/com/clashfit/`

| Package | What it holds |
|---|---|
| `core/model` | Landmarks, session state, combat state, enums. No logic. |
| `core/config` | The JSON records as Kotlin types, and the loader. |
| `core/pose` | `SyntheticBody`, which builds landmark sets for tests with exact joint angles. |
| `engine/core` | `Geometry`, `RepStateMachine`, `StageCounter`, `FormScorer`, `CombatEngine`, the One Euro filter. |
| `engine/session` | `SessionEngine`, the frame loop that ties all of the above together. |
| `engine/detect` | The four non-rep detector families: holds, cadence, ballistic, pose match. |
| `perception` | Camera, MediaPipe, and the on-screen landmark overlay. |
| `ui/screens/*` | Compose screens, one package per area. `session/` is the fight. |
| `data` | Room database and preferences. |
| `auth`, `duel`, `meta`, `alarm`, `audio` | Accounts, live play, progression, alarms, sound. |

Tests are under `android/app/src/test/java/com/clashfit/`. `TraceReplayTest` replays a real recorded
set through the shipped configuration and is the closest thing to an integration test.

---

## 9. Things worth knowing before you change something

- **`SessionEngine` is the one place that owns a session.** Adding a counting rule means adding it
  there and in a config record, not in a screen.
- **Adding an exercise needs no code**, only a JSON record in both copies plus an index entry.
- **`SyntheticBody.world()` gives exact joint angles**, so a detector can be tested without a camera.
  It bends the knee in the sagittal plane, so any measurement that drops the depth axis degenerates
  on it. Measure in 3D.
- **Form scoring is separate from counting** and stays that way. The counter decides whether a rep
  happened; the scorer decides what it was worth.
- **The permission set is a lock file.** `checkPermissions<Variant>` fails the build if the merged
  manifest gains a permission nobody approved. INTERNET is deliberately present for Firebase.
