// F4 · CADENCE — periodic motion, for cardio.
// Peak detection with a minimum prominence and a refractory period on one landmark axis.
// Amplitude is the form analogue: it is what catches someone doing tiny fake high-knees.
//
// The same detector pointed at shoulder Y gives breathing rate essentially for free, which is
// what makes the mindfulness work in docs/22-HEALTH-DOMAINS.md §3 cheap.
// docs/19-EXERCISE-LIBRARY.md §4 (F4)

import { JOINT } from '../geometry.js';

export class CadenceDetector {
  static family = 'CADENCE';

  constructor(spec) {
    this.spec = spec;
    this.d = spec.detector;
    this.reset();
  }

  reset() {
    this.repIndex = 0;
    this.buf = [];              // {t, v}
    this.lastPeakMs = null;
    this.calibAmplitude = null;
    this.lastTrough = null;
  }

  #signal(lms, side) {
    const j = JOINT[this.d.signalJoint][side];
    const p = lms[j];
    if (!p) return NaN;
    return this.d.signalAxis === 'x' ? p.x : this.d.signalAxis === 'z' ? (p.z ?? 0) : p.y;
  }

  onFrame(lms, tMs, side) {
    const v = this.#signal(lms, side);
    if (!Number.isFinite(v)) return null;
    this.buf.push({ t: tMs, v });
    while (this.buf.length && tMs - this.buf[0].t > 4000) this.buf.shift();
    if (this.buf.length < 9) return null;

    // local maximum at the centre of a five-sample window, with prominence and refractory gates
    const n = this.buf.length;
    const i = n - 3;
    const w = this.buf.slice(i - 2, i + 3);
    const mid = w[2];
    const isPeak = w.every((p, k) => k === 2 || p.v <= mid.v);
    if (!isPeak) return null;

    const recent = this.buf.slice(-45);
    const lo = Math.min(...recent.map((p) => p.v));
    const hi = Math.max(...recent.map((p) => p.v));
    const prominence = hi - lo;
    const minProm = this.d.minProminence ?? 0.03;
    if (prominence < minProm) return null;

    const refractory = this.d.refractoryMs ?? 260;
    if (this.lastPeakMs !== null && mid.t - this.lastPeakMs < refractory) return null;

    const interval = this.lastPeakMs === null ? null : mid.t - this.lastPeakMs;
    this.lastPeakMs = mid.t;
    if (interval === null) return null;                 // first peak only establishes the clock

    if (this.calibAmplitude === null) this.calibAmplitude = prominence;
    const cadence = 60000 / interval;                    // reps per minute
    const amplitude = Math.min(1.5, prominence / this.calibAmplitude);

    const band = this.d.targetCadence ?? { min: 60, max: 200 };
    const inBand = cadence >= band.min && cadence <= band.max
      ? 1
      : Math.max(0, 1 - Math.abs(cadence - (cadence < band.min ? band.min : band.max)) / band.min);

    this.repIndex += 1;
    return {
      family: 'CADENCE',
      repIndex: this.repIndex,
      tStartMs: mid.t - interval, tEndMs: mid.t,
      cadence, amplitude, prominence,
      formScore: Math.max(0, Math.min(1, 0.45 * inBand + 0.55 * Math.min(1, amplitude))),
      reason: amplitude < 0.7 ? 'amplitude' : inBand < 0.7 ? 'cadence' : 'none',
      signals: { cadence, amplitude },
    };
  }
}
