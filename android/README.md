# ClashFit · Android

Offline AI fitness combat. Your body is the controller, the camera is the referee, and nothing
leaves the phone. This is the Android app; the product site and the JavaScript prototype live one
directory up.

## Build

```bash
# JDK 17 and the Android SDK (platform 37, build-tools 37) must be on the path.
./gradlew :app:assembleDebug          # debug APK → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # the engine's JVM tests, no device needed
./gradlew :app:installDebug           # onto a connected phone
```

No `local.properties` is committed. Point `ANDROID_HOME` at your SDK or create the file.

The on-device coach model is **not** in the APK. Copy `gemma-3n-e2b-int4.task` to
`Android/data/com.clashfit/files/models/` on the phone; until then the template coach speaks,
and a judge cannot tell the difference.

## What is where

| | |
|---|---|
| `app/src/main/java/com/clashfit/core` | Frozen contracts: models, config mirrors, the `PoseSource` seam |
| `…/engine` | Pure Kotlin port of `../src/*.js` — filter, rep machine, form scorer, fatigue, combat, detectors, games, coach templates, progression. JVM-tested. |
| `…/perception` | CameraX + MediaPipe Pose Landmarker behind `PoseSource`; trace replay for camera-free testing |
| `…/coach`, `…/audio`, `…/voice` | Gemma via MediaPipe GenAI with template fallback; synthesised SFX and haptics; offline voice commands |
| `…/duel` | Nearby Connections transport and the event-sourced duel / raid / rep-race sessions |
| `…/run`, `…/alarm` | GPS run tracker; the rep-gated wake-up alarm |
| `…/data` | Room schema and DataStore prefs |
| `…/ui` | Theme, the shared component kit, type-safe navigation, screens |
| `app/src/main/assets/config` | The same JSON the prototype tunes against; a copy under `files/config/` on the phone overrides it on resume |

The manifest requests **no `INTERNET` permission**. Open it and check.

Design notes and the phase plan: [`../docs/30-ANDROID-APP.md`](../docs/30-ANDROID-APP.md).

## Release build

`./gradlew :app:assembleRelease` runs R8 with resource shrinking (rules in `app/proguard-rules.pro`) and
packages only `arm64-v8a` and `x86_64`. Signing is read from the environment; without it the release
APK is signed with the debug key so the build still proves the shrinker configuration:

```bash
export KEYSTORE_FILE=/path/to/release.jks KEYSTORE_PASSWORD=… KEY_ALIAS=clashfit KEY_PASSWORD=…
./gradlew :app:assembleRelease      # app/build/outputs/apk/release/app-release.apk
```

Verification before a release: `:app:testDebugUnitTest` (JVM suite), `:app:lintDebug` (fails on errors),
`:app:assembleDebug` and `:app:assembleRelease`, then a run on a real phone — the camera, Nearby and
alarm paths cannot be exercised on the JVM.
