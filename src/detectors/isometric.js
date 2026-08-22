// F2 · ISOMETRIC_HOLD — angle-in-range timer.
// An isometric has no velocity, so fatigue shows up as tremor: rising positional variance of
// the tracked joints. That is what actually happens to a human holding a plank, and it is why
// the fatigue framework needed to be signal-driven rather than velocity-driven.
// docs/19-EXERCISE-LIBRARY.md §4 (F2)

import { angle3, JOINT } from '../geometry.js';

export class IsometricHoldDetector {
  static family = 'ISOMETRIC_HOLD';

  constructor(spec) {
    this.spec = spec;
    this.d = spec.detector;
    this.reset();
  }

  reset() {
    this.repIndex = 0;
    this.holding = false;
    this.tStart = null;
    this.tLastInRange = null;
    this.qualitySum = 0;
    this.qualityN = 0;
    this.window = [];          // recent joint positions, for tremor
    this.baselineTremor = null;
  }

  #evaluate(lms, side) {
    let wsum = 0, acc = 0, worst = null, worstDev = -1;
    for (const t of this.d.targets) {
      const [a, b, c] = t.angle;
      const deg = angle3(lms[JOINT[a][side]], lms[JOINT[b][side]], lms[JOINT[c][side]]);
      if (!Number.isFinite(deg)) return null;
      const dev = Math.abs(deg - t.value) / t.tolerance;
      const w = t.weight ?? 1;
      acc += w * Math.max(0, 1 - dev);
      wsum += w;
      if (dev > worstDev) { worstDev = dev; worst = b; }
    }
    return { quality: wsum ? acc / wsum : 0, inRange: worstDev <= 1, worstJoint: worst };
  }

  /** Positional variance of the tracked joints over a one-second window. */
  #tremor(lms, side, tMs) {
    const pts = this.d.targets.map((t) => lms[JOINT[t.angle[1]][side]]);
    const centroid = pts.reduce((a, p) => ({ x: a.x + p.x / pts.length, y: a.y + p.y / pts.length }), { x: 0, y: 0 });
    this.window.push({ t: tMs, ...centroid });
    while (this.window.length && tMs - this.window[0].t > 1000) this.window.shift();
    if (this.window.length < 8) return 0;
    const mx = this.window.reduce((a, p) => a + p.x, 0) / this.window.length;
    const my = this.window.reduce((a, p) => a + p.y, 0) / this.window.length;
    const v = this.window.reduce((a, p) => a + (p.x - mx) ** 2 + (p.y - my) ** 2, 0) / this.window.length;
    return Math.sqrt(v) * 1000;      // millimetres of wobble
  }

  onFrame(lms, tMs, side) {
    const e = this.#evaluate(lms, side);
    if (!e) return null;
    const tremor = this.#tremor(lms, side, tMs);

    if (!this.holding) {
      if (e.inRange) { this.holding = true; this.tStart = tMs; this.tLastInRange = tMs;
                       this.qualitySum = 0; this.qualityN = 0; this.baselineTremor = null; }
      this.last = { ...e, tremor, holdSec: 0 };
      return null;
    }

    if (e.inRange) { this.tLastInRange = tMs; this.qualitySum += e.quality; this.qualityN++; }
    const holdSec = (tMs - this.tStart) / 1000;
    if (this.baselineTremor === null && holdSec > 3) this.baselineTremor = tremor;
    this.last = { ...e, tremor, holdSec };

    const brokenFor = tMs - this.tLastInRange;
    const target = this.d.targetDurationSec ?? 45;
    const done = holdSec >= target;
    if (brokenFor >= (this.d.breakToleranceMs ?? 500) || done) return this.#complete(tMs, done, tremor);
    return null;
  }

  #complete(tMs, completed, tremor) {
    const holdSec = (this.tLastInRange - this.tStart) / 1000;
    this.holding = false;
    if (holdSec < 1) return null;               // a stumble is not a hold
    this.repIndex += 1;
    const quality = this.qualityN ? this.qualitySum / this.qualityN : 0;
    const target = this.d.targetDurationSec ?? 45;
    return {
      family: 'ISOMETRIC_HOLD',
      repIndex: this.repIndex,
      tStartMs: this.tStart, tEndMs: tMs,
      holdSec, quality, completed, tremor,
      formScore: Math.max(0, Math.min(1, quality * Math.min(1, holdSec / target))),
      reason: quality < 0.7 ? 'alignment' : holdSec < target ? 'endurance' : 'none',
      signals: { tremor, quality },
    };
  }
}
