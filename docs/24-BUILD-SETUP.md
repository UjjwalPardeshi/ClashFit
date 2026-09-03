# 24 · Build Setup

The gap between "we have specs" and "we can type" — dependencies, file tree, manifest, and the
landmark table you would otherwise be googling at hour three.

**Nothing here is application code.** It is the setup surface: coordinates, structure, and lookup
data.

---

## 1. Toolchain — install before you travel

| | |
|---|---|
| Android Studio | Current stable. Both machines. |
| JDK | 17 (bundled) |
| Kotlin | 2.x |
| compileSdk / targetSdk | 35 · **minSdk 29** |
| Device | iQOO 15, OriginOS 6. Developer options + USB debugging enabled at check-in. |

**Both laptops must build a Hello-Compose APK to a physical device before 5 September.** If that has
not been proven, hour one at the event is toolchain archaeology, and hour one is 12% of your Green
Light time.

---

## 2. Dependencies

Resolve exact versions at setup, **then freeze them and commit the lockfile.** No dependency changes
after Saturday 19:00 ([10-BUILD-RUNBOOK](10-BUILD-RUNBOOK.md) rule 3).

| Purpose | Artifact |
|---|---|
| Pose Landmarker | `com.google.mediapipe:tasks-vision` |
| On-device LLM | `com.google.mediapipe:tasks-genai` |
| Camera | `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view` |
| UI | `androidx.compose:compose-bom` → `compose-ui`, `compose-material3`, `compose-ui-graphics` |
| Lifecycle | `androidx.lifecycle:lifecycle-runtime-compose`, `lifecycle-viewmodel-compose` |
| Persistence | `androidx.room:room-runtime`, `room-ktx`, `room-compiler` (KSP) |
| Async | `org.jetbrains.kotlinx:kotlinx-coroutines-android` |
| Config parsing | `org.jetbrains.kotlinx:kotlinx-serialization-json` |
| Duel — optional | `com.google.android.gms:play-services-nearby` (only for `NearbyTransport`) |

**Deliberately absent:** Hilt/Koin (construct by hand in one `AppGraph`), Navigation-Compose (a
sealed `Screen` class and a `when`), Retrofit/OkHttp (see §7b: INTERNET is for sign-in and scores only), Timber,
any analytics or crash SDK. Firebase Auth and Firestore are optional, initialised only when keys are present (§7b).
Every one of the removed dependencies is a real hour you do not have.

**Model asset** — `gemma-3n-e2b-int4.task`, ~2–3 GB. Never in the APK. Side-loaded to app external
files over Office Kit at check-in.

**Cloud keys** (optional) — Firebase Auth + Firestore initialisation. Extracted from `google-services.json`:
- `FIREBASE_API_KEY` → `current_key`
- `FIREBASE_APP_ID` → `mobilesdk_app_id`
- `FIREBASE_PROJECT_ID` → `project_id`

Add to `android/local.properties` (git-ignored, never committed):
```
FIREBASE_API_KEY=<value>
FIREBASE_APP_ID=<value>
FIREBASE_PROJECT_ID=<value>
```

If any key is missing, the app works offline with a local account. If all three are present, Firestore
is initialised at startup. **The `google-services.json` file itself is never committed.**

---

## 3. File tree

```
app/src/main/java/com/clashfit/
├── App.kt                        Application, builds AppGraph
├── AppGraph.kt                   manual DI — one object, constructed once
├── MainActivity.kt               single activity, Compose host
│
├── core/
│   ├── model/
│   │   ├── Exercise.kt           Exercise, Family, Framing enums
│   │   ├── RepEvent.kt           + Verdict
│   │   ├── FatigueState.kt       + FatigueBand
│   │   ├── FrameQuality.kt       + Framing
│   │   ├── SetTelemetry.kt
│   │   └── DuelMessage.kt        + LinkState, CompactEvent
│   ├── config/
│   │   ├── ConfigStore.kt        StateFlows + reload() + version
│   │   ├── PoseConfig.kt         @Serializable, mirrors pose.json
│   │   ├── CombatConfig.kt       @Serializable, mirrors combat.json
│   │   ├── ExerciseSpec.kt       @Serializable, mirrors exercises/*.json
│   │   └── Prompts.kt
│   └── util/
│       ├── Clock.kt              injectable — never call System directly
│       ├── OneEuroFilter.kt
│       └── Trace.kt              landmark trace read/write
│
├── perception/                   ── OMKAR ──
│   ├── PoseEngine.kt             the interface from 09-MODULE-CONTRACTS
│   ├── PoseEngineImpl.kt
│   ├── FakePoseEngine.kt         ★ built FIRST — unblocks the other lane
│   ├── TracePoseEngine.kt        replay from file, camera-free
│   ├── CameraSource.kt           CameraX binding
│   ├── PoseDetector.kt           MediaPipe Tasks wrapper
│   ├── LandmarkFilter.kt
│   ├── JointGeometry.kt          angle(), visibility gate, Landmark indices
│   ├── FatigueEstimator.kt
│   └── detector/
│       ├── ExerciseDetector.kt   the family interface
│       ├── RepCycleDetector.kt   ★ the only one that must ship
│       └── IsometricHoldDetector.kt   Tier 3
│
├── combat/                       ── UJJWAL ──
│   ├── CombatEngine.kt
│   ├── CombatEngineImpl.kt
│   ├── BossController.kt
│   ├── ComboTracker.kt
│   ├── GhostSource.kt            Ghost Race — replays into onRemoteDamage
│   └── SessionRecorder.kt
│
├── coach/                        ── UJJWAL ──
│   ├── CoachEngine.kt
│   ├── LlmEngine.kt
│   ├── TemplateFallback.kt       ★ built FIRST, must ship alone
│   ├── TelemetrySummariser.kt
│   ├── OutputValidator.kt
│   └── SpeechOut.kt
│
├── duel/                         ── OMKAR, Sunday ──
│   ├── DuelTransport.kt
│   ├── HotspotSocketTransport.kt
│   ├── NfcPairing.kt             Tier 2 — see 21-SENSOR-PLAYBOOK §3
│   └── DuelSession.kt            dedupe, recent-tail repair
│
├── data/
│   ├── ClashDb.kt                Room
│   ├── entities/
│   └── dao/
│
└── ui/
    ├── Screen.kt                 sealed class — this is the navigation
    ├── screens/                  Splash, Home, Calibration, Fight, Rest,
    │                             Victory, Summary, DuelLobby, ModeSelect
    ├── hud/                      HpBar, RepCounter, FormFlash, FatigueMeter,
    │                             ComboRail, SkeletonOverlay
    └── theme/                    Color.kt, Type.kt, Motion.kt
```

**★ marks the three files that must exist before anything else.** `FakePoseEngine` and
`TemplateFallback` are what let two people work in parallel from hour one, and `RepCycleDetector` is
the only thing standing between you and a demo.

---

## 4. AndroidManifest

```
REQUIRED
  android.permission.CAMERA

CLOUD ACCOUNTS + LEADERBOARDS (if keys present)
  android.permission.INTERNET
  android.permission.ACCESS_NETWORK_STATE

DUEL (Tier 1)
  android.permission.ACCESS_WIFI_STATE
  android.permission.CHANGE_WIFI_STATE
  android.permission.BLUETOOTH_ADVERTISE
  android.permission.BLUETOOTH_CONNECT
  android.permission.BLUETOOTH_SCAN       (usesPermissionFlags="neverForLocation")
  android.permission.NEARBY_WIFI_DEVICES  (API 33+, usesPermissionFlags="neverForLocation")

RUN TRACKER
  android.permission.ACCESS_FINE_LOCATION
  android.permission.ACCESS_COARSE_LOCATION

AUDIO + HAPTICS
  android.permission.RECORD_AUDIO
  android.permission.VIBRATE
  android.permission.POST_NOTIFICATIONS

ALARM
  android.permission.SCHEDULE_EXACT_ALARM
  android.permission.USE_EXACT_ALARM
```

**`INTERNET` is conditional on having Firebase keys.** Camera frames and pose data never use it.
Only scores, names and levels are transmitted, and only to Firestore for leaderboards. Voice
commands and the on-device coach both run offline.

Also: `android:screenOrientation="portrait"`, `android:keepScreenOn` on the fight screen,
`largeHeap="true"` for the LLM, and a comment block explaining the privacy contract.

---

## 5. MediaPipe landmark indices

The lookup you would otherwise be searching for mid-build. 33 landmarks, both image-space and world.

| # | Name | | # | Name |
|---|---|---|---|---|
| 0 | nose | | 17 | left_pinky |
| 1 | left_eye_inner | | 18 | right_pinky |
| 2 | left_eye | | 19 | left_index |
| 3 | left_eye_outer | | 20 | right_index |
| 4 | right_eye_inner | | 21 | left_thumb |
| 5 | right_eye | | 22 | right_thumb |
| 6 | right_eye_outer | | **23** | **left_hip** |
| 7 | left_ear | | **24** | **right_hip** |
| 8 | right_ear | | **25** | **left_knee** |
| 9 | mouth_left | | **26** | **right_knee** |
| 10 | mouth_right | | **27** | **left_ankle** |
| **11** | **left_shoulder** | | **28** | **right_ankle** |
| **12** | **right_shoulder** | | 29 | left_heel |
| **13** | **left_elbow** | | 30 | right_heel |
| **14** | **right_elbow** | | 31 | left_foot_index |
| **15** | **left_wrist** | | 32 | right_foot_index |
| **16** | **right_wrist** | | | |

**Bold = every landmark ClashFit actually uses.** Eleven of thirty-three.

- **Squat angle** = angle(23/24 hip, 25/26 knee, 27/28 ankle)
- **Push-up angle** = angle(11/12 shoulder, 13/14 elbow, 15/16 wrist)
- **Torso line** (push-up sag) = angle(11/12 shoulder, 23/24 hip, 27/28 ankle)
- **Breathing signal** = shoulder Y (11/12) oscillation

Use **world landmarks** for angles, **image landmarks** for the overlay and framing checks
([05-POSE-ENGINE-SPEC](05-POSE-ENGINE-SPEC.md) §1).

---

## 5a. Screenshot Tests (Roborazzi)

JVM-based screenshot comparison tests via Roborazzi. No device needed for generation, but comparison
can run on a real device or emulator.

```bash
./gradlew :app:recordRoborazziDebug     # generate PNG baselines from Composables
./gradlew :app:verifyRoborazziDebug     # compare current state against baselines
```

Baselines are stored in `android/app/screenshots/`. Use these to verify Material 3 theme tokens,
layout responsiveness and HUD readability at different sizes. Baseline changes are reviewed and
committed alongside design changes.

---

## 6. Git

Two people, thirty hours. Keep it trivial.

- **One repo, one branch (`main`), both push to it.** Feature branches cost merge time you do not
  have, and your lanes barely touch.
- Commit every 30 minutes. `git commit -am wip` is acceptable.
- **The lane split is the merge strategy** — Omkar owns `perception/` and `duel/`, Ujjwal owns
  `combat/`, `coach/` and `ui/`. `core/` is written together in the first hour and then frozen.
- Fresh repo created at check-in. The prototype stays in its own separate, publicly dated repo and
  is disclosed on the Phase-1 form.
- **First commit before 11:00 Saturday** — it timestamps your build window and answers the "did you
  build this during the event" question with evidence.

---

## 7. Hour-zero checklist — 08:00 Saturday

- [ ] Collect both loaner phones
- [ ] Developer options + USB debugging, both phones
- [ ] Pair Office Kit, both phones — **leave connected all weekend**
- [ ] Side-load `gemma-3n-e2b-int4.task` over Office Kit file transfer
- [ ] Copy `config/` and `assets/` folders to both phones
- [ ] **Verify the proximity sensor reports continuous distance, not binary** — decides whether
      [21-SENSOR-PLAYBOOK](21-SENSOR-PLAYBOOK.md) §2.1 is viable
- [ ] **Verify the hotspot can be enabled** on OriginOS — decides ADR-004's primary transport
- [ ] Measure the actual camera framing distance for a squat, at your desk
- [ ] New repo, first commit
- [ ] Both phones on chargers

---

## 8. Reference pack

Copy-ready data, not code, in [`reference/`](reference/):

| File | Contents |
|---|---|
| [CONFIG-PACK](reference/CONFIG-PACK.md) | Complete `pose.json`, `combat.json`, `ui.json` with tuned starting values |
| [EXERCISE-RECORDS](reference/EXERCISE-RECORDS.md) | The 8 weekend exercises as JSON records, with thresholds |
| [PROMPT-PACK](reference/PROMPT-PACK.md) | System/coach/boss prompts, 25 template coach lines, 15 taunts |

These drop straight into `Android/data/<pkg>/files/` at check-in. Having them ready turns hour one
from authoring into copying.
