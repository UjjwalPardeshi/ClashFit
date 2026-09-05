# 01 · Technical Requirements Document

**Target device:** iQOO 15 loaner · Snapdragon 8 Elite Gen 5 · up to 16GB LPDDR5X · OriginOS 6 (Android 16 base)
**Delivery:** signed debug APK, installed and demoed on the loaner. No cloud dependency for the demo: the app opens and trains fully without an account or a signal. The leaderboard is the one thing that needs the network, and it says so rather than failing.

---

## 1. Stack

Decided in [ADR-001](adr/ADR-001-stack.md). Summary:

| Layer | Choice |
|---|---|
| Language / UI | Kotlin + Jetpack Compose |
| Camera | CameraX `ImageAnalysis` (`STRATEGY_KEEP_ONLY_LATEST`) |
| Pose | MediaPipe Tasks — Pose Landmarker, `LIVE_STREAM` mode, GPU delegate |
| On-device LLM | MediaPipe LLM Inference API — Gemma 3n E2B int4 |
| Speech out | Android `TextToSpeech` |
| Local persistence | Room (SQLite) |
| Tunable content | JSON + text files on external storage, hot-reloaded ([ADR-005](adr/ADR-005-hot-reload-config.md)) |
| Duel transport | Pluggable — hotspot sockets primary ([ADR-004](adr/ADR-004-duel-transport.md)) |
| Async | Coroutines + `StateFlow`. Pose on a dedicated single-thread dispatcher. |

**Explicitly rejected:** TensorFlow.js in a WebView for the perception core, Unity, Health Connect.
Reasons in the ADRs. Firebase Auth and Firestore are initialised when keys are present (see §10).

---

## 2. Module map

Two people, two lanes, one seam. Both lanes code against `09-MODULE-CONTRACTS.md` and never touch
each other's internals.

```
app/
├── perception/          ← OMKAR
│   ├── CameraSource            CameraX binding, frame → ImageProxy
│   ├── PoseDetector            MediaPipe Tasks wrapper, LIVE_STREAM callback
│   ├── LandmarkFilter          One Euro filter per landmark
│   ├── JointGeometry           angle(), visibility gating, frame validity
│   ├── RepStateMachine         per-exercise FSM with hysteresis
│   ├── FormScorer              depth / ROM / tempo / alignment → 0..1
│   └── FatigueEstimator        rolling baselines → fatigue 0..1 + band
│        emits ──────────────►  RepEvent, FrameQuality, FatigueState
│
├── combat/              ← UJJWAL
│   ├── CombatEngine            RepEvent → damage → boss HP
│   ├── BossController          phases, adaptive behaviour off FatigueState
│   ├── ComboTracker            streak multiplier
│   └── SessionRecorder         writes reps/sets to Room
│
├── coach/               ← UJJWAL
│   ├── LlmEngine               MediaPipe LLM Inference lifecycle, warm session
│   ├── TelemetrySummariser     SetTelemetry → compact prompt payload
│   ├── PersonaRouter           COACH vs BOSS prompt selection
│   ├── TemplateFallback        deterministic lines when the model is unavailable
│   └── SpeechOut               TextToSpeech queue, ducking
│
├── duel/                ← OMKAR (Sunday)
│   ├── DuelTransport           interface
│   ├── HotspotSocketTransport  primary
│   ├── NearbyTransport         secondary
│   └── DuelSession             event log, dedupe, reconciliation
│
├── ui/                  ← UJJWAL
│   ├── screens/                Calibration, Fight, Rest, Victory, Duel*, Summary
│   ├── hud/                    HpBar, RepCounter, FormFlash, FatigueMeter, ComboRail
│   └── theme/                  tokens, type scale, motion
│
└── core/                ← SHARED, written first, frozen by 13:00 Saturday
    ├── model/                  RepEvent, FormScore, FatigueState, DuelMessage
    ├── config/                 ConfigStore, hot-reload watcher
    └── util/                   Clock, Logger, Telemetry
```

**The seam rule:** `perception` emits immutable `RepEvent`s onto a `SharedFlow`. `combat` consumes
them. Neither imports the other. This is what lets two people work at full speed in parallel from
hour one, and it is what lets us swap a stubbed pose source for a recorded one during testing.

---

## 3. Data flow

```
CameraX frame (front cam, 720p, ~30fps)
   │
   ▼
PoseDetector (MediaPipe, GPU delegate)          budget: ≤22ms
   │  33 landmarks + world landmarks + visibility
   ▼
LandmarkFilter (One Euro, per-axis)             budget: ≤1ms
   │
   ▼
JointGeometry → primary angle θ, frame validity  budget: ≤1ms
   │
   ▼
RepStateMachine ──── on rep complete ──► FormScorer ──► RepEvent
   │                                          │
   ▼                                          ▼
FatigueEstimator ──► FatigueState        CombatEngine ──► damage ──► BossController
                          │                                              │
                          └──────────────────────────────────────────────┘
                                              │
                                    UI (Compose, 60fps) + SpeechOut
                                              │
              between sets only ──► TelemetrySummariser ──► LlmEngine ──► coach line + taunt
```

**Hard rule: the LLM never runs while the camera loop is active.** Gemma inference and 30fps pose
detection contend for the same GPU/NPU and thermal headroom. Sequence is: set ends → camera drops
to 5fps preview or pauses → LLM generates → TTS speaks → camera resumes at full rate.

---

## 4. Performance budgets

| Path | Budget | Fails if |
|---|---|---|
| Frame → landmark result | ≤ 22 ms | pose delegate falls back to CPU |
| Landmark → RepEvent emitted | ≤ 4 ms | filter window too long |
| RepEvent → visible + audible hit | ≤ 100 ms total | the product stops feeling responsive |
| Compose frame | 16.6 ms | HUD does layout work per frame |
| LLM cold load | ≤ 25 s, once at app start | blocks first fight — must be behind the splash |
| LLM generation (2 sentences) | ≤ 4 s | rest gap feels dead; fall back to templates at 5s |
| Duel event delivery | ≤ 250 ms p95 | HP bars visibly desync |

**Thermal:** sustained camera + GPU inference will heat the device. Mitigations, in order of
preference: drop preview resolution before dropping inference rate; pause inference entirely
between sets; never run LLM and pose concurrently; keep screen brightness at 60% outside demos.

---

## 5. Camera configuration — read this before writing CameraX code

This is the most under-estimated technical constraint in the project.

**The framing problem.** A standing squat needs the full body in frame from roughly 2.0–2.5 m.
The phone is propped low (floor or against a wall). A typical front camera has a narrower field of
view than the rear ultra-wide, so the required distance is larger than people expect.

**Two supported modes:**

| Mode | Camera | Display | When |
|---|---|---|---|
| **Solo** (default) | Front | Phone screen faces the player | Normal use. Player must stand further back. Calibration overlay enforces framing. |
| **Arena** | Rear ultra-wide | Phone mirrored to a laptop over Office Kit | Demos, duels, tight spaces. Wider FOV, bigger display, and it exercises Office Kit — which is 10% of the rubric, measured from device telemetry. |

Arena Mode is not a workaround. It is the demo configuration and it should be built deliberately.

**Calibration must actively help.** A silhouette guide, a distance hint driven by the bounding box
of detected landmarks, and a hard gate: do not start a fight until all required joints are visible
above the confidence threshold for 2 continuous seconds.

**Orientation.** Push-ups put the player face-down, side-on to the phone. The HUD must be readable
in that orientation — see `03-UI-UX-SPEC.md` §4. Audio carries the load here, not the screen.

---

## 6. Threading

| Work | Thread |
|---|---|
| CameraX analysis | dedicated single-thread executor |
| MediaPipe pose | LIVE_STREAM callback thread (do not block it) |
| Filter, FSM, scoring | same callback thread — pure CPU, microseconds |
| RepEvent emission | `SharedFlow`, replay 0, extra buffer 16, `DROP_OLDEST` |
| Combat / UI state | `Dispatchers.Main.immediate` |
| Room writes | `Dispatchers.IO`, fire and forget |
| LLM inference | `Dispatchers.Default`, cancellable, always with a timeout |
| TTS | its own queue; flush on new set start |

---

## 7. Offline and permissions

**Network:** the camera pipeline and pose model have zero path to the network. All network code
lives in a single package, `android/app/src/main/java/com/clashfit/cloud/`, which carries only
scores, names and levels to the cloud, never camera frames or pose data. The manifest declares
this boundary in its permission comment. Duel transport uses local-only permissions.

**Permissions requested:** `CAMERA`, plus for duel: `NEARBY_WIFI_DEVICES`, `ACCESS_FINE_LOCATION`
(pre-33 discovery), `BLUETOOTH_ADVERTISE`/`BLUETOOTH_CONNECT` if the BLE fallback ships.

**Model assets:** the Gemma `.task` file (~2–3 GB) is too large to ship in an APK. It is
side-loaded to app external files at first run, transferred from the laptop over **Office Kit file
transfer**. Document this in the demo — it is a legitimate and scoreable use of the bridge.

---

## 8. Build and tooling

- Min SDK 29, target SDK 35, compileSdk 35
- Kotlin 2.x, Compose BOM current, `kotlinx.coroutines`
- No dependency added after Saturday 19:00. A new Gradle sync at 02:00 is how weekends die.
- Single module to start. Split only if compile time exceeds 45s.
- Debug APK only. Keep a known-good APK on both phones at all times — see §9.

## 9. The golden APK rule

**From Saturday 19:00 onward, a working APK is installed on both phones at all times.**

After every green build that demonstrably works, copy the APK to `/sdcard/ClashFit/golden/` with a
timestamp. If the tree breaks at 03:00 and cannot be fixed, we still have something to demo. This
single practice has saved more hackathon teams than any other.

## 10. What we do not build

No analytics SDK. No crash reporter. No dependency injection framework — construct objects by
hand in one `AppGraph` object. No navigation library — a sealed `Screen` class and a `when` block.
Firebase Auth and Firestore are initialised only if keys are present; with no keys, the app
keeps a local account. Every architectural choice is a real hour we do not have.
