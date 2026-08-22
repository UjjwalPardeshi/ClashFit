// Fatigue estimation — the novelty claim.
// Decay of the family's primary output against the player's own early-set baseline.
// For REP_CYCLE that is concentric velocity, range of motion, and inter-rep pause.
// Spec: docs/05-POSE-ENGINE-SPEC.md §6

export const Band = { FRESH: 'FRESH', WORKING: 'WORKING', FADING: 'FADING', GASSED: 'GASSED' };
const ORDER = [Band.FRESH, Band.WORKING, Band.FADING, Band.GASSED];

const clamp01 = (v) => (Number.isFinite(v) ? Math.min(1, Math.max(0, v)) : 0);

export class FatigueEstimator {
  constructor(cfg) {
    this.cfg = cfg;                       // pose.json .fatigue
    this.reset();
  }

  reset() {
    this.samples = [];                    // {velocity, rom, gap}
    this.baseline = null;
    this.value = 0;
    this.band = Band.FRESH;
    this.pendingBand = null;
    this.pendingCount = 0;
    this.last = { velocityLoss: 0, romLoss: 0, pauseGrowth: 0 };
    this.frozen = false;
  }

  /** Framing loss and pause must not be read as fatigue. */
  freeze() { this.frozen = true; }
  unfreeze() { this.frozen = false; }

  /** @param {object} raw completed-rep measurements from RepStateMachine */
  onRep(raw) {
    if (this.frozen) return this.state();

    const sample = {
      velocity: raw.concentricVelocity,
      rom: raw.uMax - raw.uMin,
      gap: raw.gapSec,
    };
    this.samples.push(sample);

    const n = this.cfg.baselineReps ?? 3;
    if (this.samples.length < n) return this.state();
    if (!this.baseline) {
      const first = this.samples.slice(0, n);
      const mean = (k) => first.reduce((a, s) => a + s[k], 0) / first.length;
      this.baseline = { velocity: mean('velocity'), rom: mean('rom'), gap: mean('gap') };
    }

    const b = this.baseline;
    const velocityLoss = b.velocity > 1e-6 ? clamp01(1 - sample.velocity / b.velocity) : 0;
    const romLoss = b.rom > 1e-6 ? clamp01(1 - sample.rom / b.rom) : 0;
    const norm = this.cfg.pauseGrowthNormSec ?? 3.0;
    const pauseGrowth = clamp01((sample.gap - b.gap) / norm);

    const w = this.cfg.weights;
    const raw01 = clamp01(w.velocityLoss * velocityLoss + w.romLoss * romLoss + w.pauseGrowth * pauseGrowth);

    const a = this.cfg.ema ?? 0.4;
    this.value = this.samples.length === n ? raw01 : a * raw01 + (1 - a) * this.value;
    this.last = { velocityLoss, romLoss, pauseGrowth };

    this.#latch(this.#bandFor(this.value));
    return this.state();
  }

  #bandFor(v) {
    const t = this.cfg.bands;
    if (v >= t.gassed) return Band.GASSED;
    if (v >= t.fading) return Band.FADING;
    if (v >= t.working) return Band.WORKING;
    return Band.FRESH;
  }

  /** Band changes are latched in both directions so the meter cannot flicker on a boundary. */
  #latch(candidate) {
    const need = this.cfg.bandLatchReps ?? 1;
    if (candidate === this.band) { this.pendingBand = null; this.pendingCount = 0; return; }
    if (candidate === this.pendingBand) {
      if (++this.pendingCount > need) { this.band = candidate; this.pendingBand = null; this.pendingCount = 0; }
    } else { this.pendingBand = candidate; this.pendingCount = 1; }
    if (need === 0) this.band = candidate;
  }

  state() {
    return {
      value: this.value,
      band: this.band,
      bandIndex: ORDER.indexOf(this.band),
      velocityLoss: this.last.velocityLoss,
      romLoss: this.last.romLoss,
      pauseGrowth: this.last.pauseGrowth,
      baselineReps: Math.min(this.samples.length, this.cfg.baselineReps ?? 3),
      hasBaseline: !!this.baseline,
    };
  }
}
