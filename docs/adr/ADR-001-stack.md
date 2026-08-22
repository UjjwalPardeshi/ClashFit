# ADR-001 · Application stack

**Status:** ACCEPTED — 21 Aug 2026. Ujjwal proposed TensorFlow.js; considered, and set aside for the reasons in Options.
**Date:** 21 Aug 2026
**Deciders:** Omkar, Ujjwal

---

## Context

Ujjwal's proposal: *"PoseNet ya MediaPipe, using TensorFlow.js — angles ke through rep detection."*

That is a legitimate architecture and the angle-based rep detection is exactly right regardless of
runtime. The open question is only **where the models run**: native Android, or JavaScript in a
WebView/PWA.

Two constraints shape this decision, and they pull in opposite directions:

1. **The event rewards NPU work.** The published stack guidance names the Snapdragon NPU explicitly
   and names Gemma/Phi/Sarvam-class models. Technical depth is 15% and creative phone use is 15%.
2. **Red Light is 55% of build time** — phone only, laptops closed as build machines. A Gradle
   toolchain cannot be iterated from a phone; a Node/Vite toolchain in Termux can.

---

## Options

### A · Native Kotlin + Compose + MediaPipe Tasks
- ✅ GPU/NPU delegate, 30fps pose comfortably
- ✅ **MediaPipe LLM Inference gives us Gemma 3n on-device** — this is the novelty
- ✅ CameraX gives real camera control: resolution, FPS, ultra-wide lens selection
- ✅ An APK is unambiguous as "runs on the phone"
- ✅ TTS, audio latency, thermal APIs all native
- ❌ Gradle builds need the laptop → Red Light is hostile

### B · TensorFlow.js (PoseNet/MoveNet) in a PWA or WebView
- ✅ Red Light friendly: Termux + node + vite, editable entirely on-device
- ✅ Faster iteration, and Ujjwal is faster in JS
- ❌ **No NPU access.** WebGL/WASM backends cannot reach the Hexagon NPU. That is the specific thing
  the event asks for.
- ❌ Realistically 15–25 fps on a mobile browser versus 30+ native — and frame rate *is* the product
- ❌ **Gemma 3n on-device is not viable in a phone browser** in this timeframe. Losing it costs us the
  entire novelty claim, which is 20% of the score.
- ❌ Weaker camera control; no reliable ultra-wide selection for Arena Mode
- ❌ "It's a web page" is a materially weaker answer to "real use of the hardware"

### C · Hybrid — native perception, JS content layer
Native Kotlin owns camera, pose and LLM. The game's presentation layer runs in a WebView with
HTML/Canvas loaded from device storage, so it is editable on-device during Red Light.
- ✅ Keeps NPU access *and* gives Ujjwal a JS surface he can iterate on the phone
- ❌ A JS↔native bridge to build and debug, at a cost of hours we do not have
- ❌ Two rendering models to reason about at hour 26

---

## Decision

**Option A — native Kotlin + Compose + MediaPipe Tasks — with the Red Light problem solved by
tooling rather than by language.**

The Red Light objection is real, and it is the strongest argument for B. But it is solvable without
giving up the NPU: **[ADR-005](ADR-005-hot-reload-config.md)** puts every tunable number, prompt and
asset in files on device storage, re-read on resume. During Red Light we tune thresholds, damage
curves, prompts and art on the phone, with a real body, in front of the real camera — which is
better tuning than we would get on a laptop anyway.

Choosing B to make 10.5 hours easier would cost the on-device LLM, the NPU story, and roughly 35% of
the rubric. That is not a trade worth making.

**Ujjwal's angle-based rep detection is adopted in full** — see `05-POSE-ENGINE-SPEC.md` §4. The
technique is identical in either runtime; only the host changes. His lane is `combat`, `coach` and
`ui`, all of which are expressible in Compose and JSON config without deep Android internals, and
`FakePoseEngine` means he is never blocked on the camera pipeline.

---

## Consequences

- Both of us need a working Android toolchain before the event. Non-negotiable.
- Red Light productivity depends entirely on ADR-005 landing in the first Green block. If the config
  layer is not working by 13:00 Saturday, we lose 10.5 hours. **This is the highest-leverage hour of
  the weekend.**
- No dependency added after Saturday 19:00.
- If Ujjwal wants a JS surface for the game layer, Option C can be added later behind the same
  `CombatState` contract without disturbing `perception`. It is not in the weekend plan.

**Documents affected if this is overturned:** `01-TRD.md`, `09-MODULE-CONTRACTS.md`,
`05-POSE-ENGINE-SPEC.md` §1, `06-AI-COACH-SPEC.md` (entirely — Option B has no on-device LLM),
`10-BUILD-RUNBOOK.md`.
