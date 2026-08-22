# ADR-002 · Pose model

**Status:** Accepted
**Date:** 21 Aug 2026

## Context

Three realistic candidates for on-device human pose estimation: PoseNet, MoveNet, and MediaPipe
Pose Landmarker. Ujjwal proposed "PoseNet ya MediaPipe".

## Options

| | Keypoints | 3D | Runtime | Notes |
|---|---|---|---|---|
| **PoseNet** | 17 | no | TF Lite / TF.js | The oldest of the three and effectively superseded. Multi-person by design, which we do not want. |
| **MoveNet** (Lightning / Thunder) | 17 | no | TF Lite / TF.js | Fast and accurate for 2D. Thunder is the quality tier. No world-space output. |
| **MediaPipe Pose Landmarker** | **33** | **yes — metric world landmarks** | MediaPipe Tasks (Android) | Per-landmark visibility and presence. `LIVE_STREAM` mode with GPU delegate. Same SDK family as MediaPipe LLM Inference. |

## Decision

**MediaPipe Pose Landmarker**, `pose_landmarker_full.task`, LIVE_STREAM, GPU delegate,
`numPoses = 1`.

Four reasons, in order of weight:

1. **World landmarks.** Metric, hip-centred 3D coordinates mean our joint angles are far less
   sensitive to camera tilt and perspective than image-space angles from a 17-keypoint 2D model.
   Since the phone is propped at an awkward low angle, this is not a nicety — it is the difference
   between a depth measurement that means something and one that drifts with camera placement.
2. **33 landmarks** include the foot and hand detail that push-up alignment scoring needs.
3. **Per-landmark visibility** drives our frame-validity gate directly (`05-POSE-ENGINE-SPEC.md`
   §3). Without it we would have to infer occlusion.
4. **One SDK family** with MediaPipe LLM Inference — one dependency set, one mental model, one set
   of delegate semantics.

PoseNet is rejected outright: superseded, fewer keypoints, no world space. MoveNet Thunder is a
reasonable 2D fallback if MediaPipe misses the frame budget on the loaner, but we expect it will
not.

## Consequences

- `_lite` is the fallback tier if `_full` misses the 22ms budget. Swap the asset, not the code.
- Everything downstream assumes 33-landmark indices and world coordinates. Switching to MoveNet
  later means rewriting `JointGeometry` and re-tuning every threshold.
- `numPoses = 1` means MediaPipe picks the most prominent person. We additionally lock to the
  frame-centre pose at fight start — see `05-POSE-ENGINE-SPEC.md` §8.
