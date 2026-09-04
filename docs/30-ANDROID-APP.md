# 30 · Android App — Build Plan

The Android app lives in [`android/`](../android/), a Gradle project alongside the prototype.
Everything the product site at clash-fit.vercel.app promises is in scope; everything the
prototype proves is ported, not redesigned. The JavaScript in `src/` and its 143 assertions are
the specification for the engine.

---

## 1. Stack

| Layer | Choice | Pinned |
|---|---|---|
| Build | Gradle 9.7.1 · AGP 9.4.0 (`android.builtInKotlin=false`, `android.newDsl=false` — standalone KGP) · Kotlin 2.3.21 · KSP 2.3.11 · JDK 17 | `gradle/libs.versions.toml` |
| SDK | compileSdk 37 · targetSdk 37 · minSdk 29 | |
| UI | Jetpack Compose · Material 3 (BOM 2026.08) · Navigation Compose 2.10 type-safe routes | |
| Camera | CameraX 1.6 `ImageAnalysis`, keep-only-latest | |
| Pose | MediaPipe Tasks Vision 1.0 — Pose Landmarker, LIVE_STREAM, GPU with CPU fallback | models bundled in `assets/models` |
| Coach | MediaPipe Tasks GenAI 0.10 — Gemma 3n E2B int4, side-loaded to `files/models/` | template bank always ships |
| Speech | Android `TextToSpeech`; offline `SpeechRecognizer` for commands | |
| Persistence | Room 2.8 v3 (sessions, sets, reps, streaks, bests, ladders, runs, alarms, ghosts, posture samples; migrations 1→2→3) · DataStore prefs | |
| Multiplayer | Nearby Connections `P2P_STAR`, event-sourced sync from `07-MULTIPLAYER-SPEC` | no server, no INTERNET permission |
| Run tracker | Fused location in a foreground service; route stays on the phone | |
| Alarm | `AlarmManager` exact + full-screen ring activity; dismissed by counted reps | |
| DI / nav | Manual `AppGraph`; type-safe Navigation Compose | no Hilt |

The APK carries **no `INTERNET` permission**. That is the privacy claim, made checkable: a transitive Google
telemetry library declares it, the manifest strips it in the merge (`tools:node="remove"`), and the
`checkNoInternet<Variant>` Gradle task fails the build if the merged manifest ever carries it again.

## 2. Package map

```
com.clashfit
├── core/            model (frozen contracts) · config (JSON mirrors + hot reload) · pose (PoseSource) · util
├── engine/          pure Kotlin port of src/*.js — JVM-tested, no Android imports
│   ├── core/        OneEuro · Geometry · RepStateMachine · FormScorer · FatigueEstimator · Combat · Ghost · Challenge · Asymmetry
│   ├── detect/      IsometricHold · Cadence · Ballistic · PoseMatch detectors
│   ├── games/       Siege · Pursuit · Breaker · Sigil · Roster
│   ├── coach/       TelemetrySummariser · TemplateBank · OutputValidator
│   ├── summary/     stats, curves, CSV/JSON export · Progression (streaks, bests, ladders) · Breathing · Clinic · Posture
│   └── session/     SessionEngine — the hub every mode plugs into
├── perception/      CameraX + MediaPipe PoseSource · trace replay · skeleton overlay
├── coach/           LlmEngine · CoachEngine · SpeechOut
├── audio/  voice/   synthesised SFX + haptics · offline voice commands
├── duel/            DuelTransport · NearbyTransport · DuelSession · RaidSession · RepRaceSession
├── run/             RunTrackingService · run screens
├── alarm/           scheduler · receivers · ring activity with rep-gated dismiss · alarm screens
├── play/            PlayHub — a Nearby link or a pass-the-phone roster that outlives one screen
├── desk/            the desk timer: inexact repeating alarm, quiet hours, a notification that opens a sixty-second set
├── data/            Room entities, DAOs, Prefs · ProgressionRepository (streaks, bests, ladders)
└── ui/              theme · components kit · nav · screens/{home, modes, picker, session, duel, roster, ghosts,
                     challenge, breathing, desk, posture, library, character, streaks, history, clinic, preflight, privacy, settings}
```

## 3. Phases

| Phase | What | Gate |
|---|---|---|
| 0 | Skeleton: Gradle, manifest, theme, core contracts, config store, Room, nav with stubs | `assembleDebug` green |
| 1 | Parallel ports and features, one agent per package, no shared files touched | each package compiles, JVM tests pass |
| 2 | `SessionEngine` + session screens (calibration → fight → rest → victory → summary) · duel/raid/rep-race lobbies · roster board · ghosts, challenge codes, breathing · alarm camera gate · progression banked | done: compiles, JVM tests pass, debug APK builds |
| 3 | Review (correctness vs JS, lifecycle, permissions, performance on the camera thread) · lint clean · animations · baseline profile · CI | release build green |

Commits are small and per package. Push to **both** remotes (`origin` and `fork`).

Release builds: R8 + resource shrinking, ABIs `arm64-v8a`/`x86_64`, signing from `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` with a debug-key fallback so `assembleRelease` always builds. See `android/README.md`.

## 4. Rules that hold across every package

- The JS is the spec. Port semantics, keep every number in config.
- `perception` imports nothing from `combat`, `coach`, `duel` or `ui`.
- No damage outside `FIGHTING`. A pause never reads as fatigue.
- Nothing the player must read mid-set goes below 28sp. Audio fires first.
- No video, images or landmarks are ever stored — only derived per-rep scalars.
- The LLM never runs while the camera loop is at full rate.
