// One Euro filter — adaptive low-pass for landmark coordinates.
// Heavy smoothing when a joint is nearly still, light smoothing when it moves fast.
// That is exactly what we want: stable at the top and bottom of a rep (where depth is
// measured), responsive through the transitions.
// Spec: docs/05-POSE-ENGINE-SPEC.md §2

const TWO_PI = Math.PI * 2;

function alpha(cutoff, dt) {
  const tau = 1 / (TWO_PI * cutoff);
  return 1 / (1 + tau / dt);
}

class LowPass {
  constructor() { this.y = null; }
  filter(x, a) {
    this.y = this.y === null ? x : a * x + (1 - a) * this.y;
    return this.y;
  }
  reset() { this.y = null; }
}

export class OneEuro {
  constructor({ minCutoff = 1.0, beta = 0.007, dCutoff = 1.0 } = {}) {
    this.minCutoff = minCutoff;
    this.beta = beta;
    this.dCutoff = dCutoff;
    this.x = new LowPass();
    this.dx = new LowPass();
    this.prev = null;
    this.tPrev = null;
  }

  /** @param {number} value @param {number} tMs @returns {number} */
  filter(value, tMs) {
    if (this.tPrev === null) {
      this.tPrev = tMs;
      this.prev = value;
      return this.x.filter(value, alpha(this.minCutoff, 1 / 30));
    }
    let dt = (tMs - this.tPrev) / 1000;
    if (!(dt > 0)) dt = 1 / 30;           // guard duplicate / non-monotonic timestamps
    this.tPrev = tMs;

    const dxRaw = (value - this.prev) / dt;
    this.prev = value;
    const dxHat = this.dx.filter(dxRaw, alpha(this.dCutoff, dt));
    const cutoff = this.minCutoff + this.beta * Math.abs(dxHat);
    return this.x.filter(value, alpha(cutoff, dt));
  }

  reset() { this.x.reset(); this.dx.reset(); this.prev = null; this.tPrev = null; }
}

/** A OneEuro per landmark per axis. Filters inputs, never the derived angle —
 *  filtering the angle would break geometric consistency. */
export class LandmarkFilter {
  constructor(params, landmarkCount = 33) {
    this.params = params;
    this.f = Array.from({ length: landmarkCount }, () => ({
      x: new OneEuro(params), y: new OneEuro(params), z: new OneEuro(params),
    }));
  }
  /** @param {{x:number,y:number,z:number,visibility?:number}[]} lms */
  apply(lms, tMs) {
    return lms.map((p, i) => ({
      x: this.f[i].x.filter(p.x, tMs),
      y: this.f[i].y.filter(p.y, tMs),
      z: this.f[i].z.filter(p.z ?? 0, tMs),
      visibility: p.visibility ?? 1,
    }));
  }
  reset() { this.f.forEach(a => { a.x.reset(); a.y.reset(); a.z.reset(); }); }
}
