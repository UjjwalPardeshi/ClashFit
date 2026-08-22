# ADR-003 · On-device language model

**Status:** ACCEPTED — 21 Aug 2026. Both builders have prior on-device LLM deployment experience, so this is a latency and thermal question, not a feasibility one. Confirm numbers on 24–25 Aug.
**Date:** 21 Aug 2026

## Context

The coach and the boss voice are generated on-device. This is half the novelty claim and the reason
the project reads as an AI project rather than a computer-vision project. The event's own guidance
names Sarvam, Gemma and Phi-class models and points at the Snapdragon NPU.

Constraints: it shares a thermal envelope with continuous 30fps pose inference, the model file must
be side-loaded (too large for an APK), and it must be entirely optional at runtime.

## Options

| Model | Size (int4) | Runtime on Android | Notes |
|---|---|---|---|
| **Gemma 3n E2B** | ~2–3 GB | MediaPipe LLM Inference, first-class `.task` bundle | Designed for on-device; small effective memory footprint for its capability. Named in the event guidance. |
| Gemma 2 2B | ~1.5 GB | MediaPipe LLM Inference | Smaller and faster, less capable. Good fallback. |
| Phi-3.5 mini | ~2.2 GB | ONNX Runtime / llama.cpp | Strong model, but a heavier integration path on Android in our timeframe. |
| Sarvam (Indic) | varies | varies | Compelling if we wanted Indic-language coaching. We do not, this weekend — English + TTS is the scope. |

## Decision

**Gemma 3n E2B int4 via the MediaPipe LLM Inference API**, GPU delegate, with **Gemma 2 2B as the
drop-in fallback** if load time or generation latency misses budget.

Rationale: it is the shortest path from "we have MediaPipe already" to "a language model runs on
this phone", it is explicitly named in the event's stack guidance, and the `.task` bundle format
removes an entire class of conversion problems we cannot afford to debug at hour 20.

**Integration risk is low — both builders have shipped on-device inference before.** What is *not*
known is how Gemma behaves on this specific silicon while a 30fps camera pipeline is competing for
the same thermal envelope. That is what 24–25 August measures. Record cold load time,
tokens/sec, peak RAM, and — critically — the same numbers *with the camera pipeline running*. If
generation cannot complete in under 4 seconds between sets, drop to Gemma 2 2B. If that also fails,
templates become primary and the LLM is cut from the pitch.

## Consequences

- ~3 GB must be transferred to the phone at check-in. Route: **Office Kit file transfer** — which is
  both the practical option and scoreable under the 10% Office Kit criterion.
- Hard rule: **never run inference concurrently with the camera loop.** See `01-TRD.md` §3.
- The template fallback (`06-AI-COACH-SPEC.md` §6) is built **first** and must be good enough to
  ship alone. If Gemma does not land, we do not mention it in the pitch.
- Accept the Gemma licence on Hugging Face today — the download is gated.
