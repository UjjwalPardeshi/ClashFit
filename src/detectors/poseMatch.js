// F3 · POSE_MATCH — whole-skeleton template matching, for yoga.
//
// The difference between a yoga feature and a yoga toy is the cue. We surface the single worst
// joint by name — "straighten your left arm" — rather than an accuracy percentage, because a
// percentage tells you that you are wrong without telling you what to move.
//
// Mirror tolerant: a tree pose on either leg is the same pose.
// docs/19-EXERCISE-LIBRARY.md §4 (F3)

import { angle3, JOINT, SIDE } from '../geometry.js';

const READABLE = {
  SHOULDER: 'shoulder', ELBOW: 'arm', WRIST: 'wrist',
  HIP: 'hip', KNEE: 'leg', ANKLE: 'ankle',
};

export class PoseMatchDetector {
  static family = 'POSE_MATCH';

  constructor(spec) {
    this.spec = spec;
    this.d = spec.detector;
    this.reset();
  }

  reset() {
    this.repIndex = 0;
    this.inPose = false;
    this.tEnter = null;
    this.tLastGood = null;
    this.accSum = 0; this.accN = 0;
    this.enterCandidateSince = null;
  }

  #accuracy(lms, side) {
    let wsum = 0, acc = 0, worst = null, worstDev = -1;
    for (const t of this.d.reference) {
      const [a, b, c] = t.angle;
      const deg = angle3(lms[JOINT[a][side]], lms[JOINT[b][side]], lms[JOINT[c][side]]);
      if (!Number.isFinite(deg)) continue;
      const tol = t.tolerance ?? 18;
      const dev = Math.abs(deg - t.value) / tol;
      const w = t.weight ?? 1;
      acc += w * Math.max(0, 1 - dev);
      wsum += w;
      if (dev > worstDev) { worstDev = dev; worst = { joint: b, side, deg, target: t.value }; }
    }
    return wsum ? { accuracy: acc / wsum, worst } : null;
  }

  onFrame(lms, tMs, side) {
    // Mirror tolerance: score both sides, keep the better reading.
    const a = this.#accuracy(lms, SIDE.LEFT);
    const b = this.#accuracy(lms, SIDE.RIGHT);
    const best = !a ? b : !b ? a : (a.accuracy >= b.accuracy ? a : b);
    if (!best) return null;

    const enter = this.d.enterAccuracy ?? 0.70;
    const holdMs = this.d.enterHoldMs ?? 1500;
    this.last = { accuracy: best.accuracy, cue: this.#cue(best.worst) };

    if (!this.inPose) {
      if (best.accuracy >= enter) {
        this.enterCandidateSince ??= tMs;
        if (tMs - this.enterCandidateSince >= holdMs) {
          this.inPose = true; this.tEnter = tMs; this.tLastGood = tMs;
          this.accSum = 0; this.accN = 0;
        }
      } else this.enterCandidateSince = null;
      return null;
    }

    if (best.accuracy >= enter) { this.tLastGood = tMs; this.accSum += best.accuracy; this.accN++; }
    const heldSec = (tMs - this.tEnter) / 1000;
    const target = this.d.targetDurationSec ?? 20;
    const lostFor = tMs - this.tLastGood;

    if (lostFor >= (this.d.breakToleranceMs ?? 800) || heldSec >= target) {
      this.inPose = false; this.enterCandidateSince = null;
      const held = (this.tLastGood - this.tEnter) / 1000;
      if (held < 1) return null;
      this.repIndex += 1;
      const accuracy = this.accN ? this.accSum / this.accN : 0;
      return {
        family: 'POSE_MATCH',
        repIndex: this.repIndex,
        tStartMs: this.tEnter, tEndMs: tMs,
        heldSec: held, accuracy, completed: heldSec >= target,
        formScore: Math.max(0, Math.min(1, accuracy * Math.min(1, held / target))),
        reason: accuracy < 0.8 ? 'alignment' : held < target ? 'endurance' : 'none',
        cue: this.#cue(best.worst),
        signals: { accuracy },
      };
    }
    return null;
  }

  #cue(worst) {
    if (!worst) return null;
    const part = READABLE[worst.joint] ?? worst.joint.toLowerCase();
    const which = worst.side === SIDE.LEFT ? 'left' : 'right';
    return worst.deg < worst.target
      ? `Open your ${which} ${part} further.`
      : `Ease your ${which} ${part} back.`;
  }
}
