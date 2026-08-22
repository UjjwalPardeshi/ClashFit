// Fatigue estimation — the novelty claim.
// Decay of the family's primary output against the player's own early-set baseline.
// For REP_CYCLE that is concentric velocity, range of motion, and inter-rep pause.
// Spec: docs/05-POSE-ENGINE-SPEC.md §6

export const Band = { FRESH: 'FRESH', WORKING: 'WORKING', FADING: 'FADING', GASSED: 'GASSED' };
const ORDER = [Band.FRESH, Band.WORKING, Band.FADING, Band.GASSED];

const clamp01 = (v) => (Number.isFinite(v) ? Math.min(1, Math.max(0, v)) : 0);

/**
 * Signal declarations per movement family. This is the mechanism behind the claim that one
 * fatigue model covers five families: fatigue is always decay of that family's primary output
 * against the player's own early-set baseline. Only the output changes.
 *
 *   dir 'decay'  — higher is better, so loss = 1 - current/baseline   (velocity, range, height)
 *   dir 'growth' — lower is better, so loss = (current - baseline)/norm (rest gaps, tremor)
 *
 * docs/19-EXERCISE-LIBRARY.md §2
 */
export const SIGNALS = {
  REP_CYCLE: [
    { key: 'velocity', dir: 'decay',  weight: 0.45 },
    { key: 'rom',      dir: 'decay',  weight: 0.35 },
    { key: 'gap',      dir: 'growth', weight: 0.20, norm: 3.0 },
  ],
  ISOMETRIC_HOLD: [
    // An isometric has no velocity. Fatigue shows up as shake, which is what actually happens
    // to a human holding a plank.
    { key: 'tremor',  dir: 'growth', weight: 0.60, norm: 1.0 },
    { key: 'quality', dir: 'decay',  weight: 0.40 },
  ],
  CADENCE: [
    { key: 'cadence',   dir: 'decay', weight: 0.55 },
    { key: 'amplitude', dir: 'decay', weight: 0.45 },
  ],
  BALLISTIC: [
    { key: 'height',   dir: 'decay', weight: 0.60 },
    { key: 'softness', dir: 'decay', weight: 0.40 },
  ],
  POSE_MATCH: [
    { key: 'accuracy', dir: 'decay', weight: 1.00 },
  ],
};

export class FatigueEstimator {
  /** @param cfg pose.json .fatigue  @param family one of the SIGNALS keys */
  constructor(cfg, family = 'REP_CYCLE') {
    this.cfg = cfg;
    this.signals = SIGNALS[family] ?? SIGNALS.REP_CYCLE;
    this.family = family;
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
    this.losses = {};
    this.frozen = false;
  }

  /** Framing loss and pause must not be read as fatigue. */
  freeze() { this.frozen = true; }
  unfreeze() { this.frozen = false; }

  /** REP_CYCLE convenience wrapper — maps a completed rep onto the declared signals. */
  onRep(raw) {
    return this.onSignals({
      velocity: raw.concentricVelocity,
      rom: raw.uMax - raw.uMin,
      gap: raw.gapSec,
    });
  }

  /** The general path. Every family reports through this, so bands, latching, freezing and the
   *  downstream contract are identical no matter what is being measured. */
  onSignals(sample) {
    if (this.frozen) return this.state();
    this.samples.push(sample);

    const n = this.cfg.baselineReps ?? 3;
    if (this.samples.length < n) return this.state();
    if (!this.baseline) {
      const first = this.samples.slice(0, n);
      this.baseline = {};
      for (const sig of this.signals)
        this.baseline[sig.key] = first.reduce((a, s) => a + (s[sig.key] ?? 0), 0) / first.length;
    }

    const losses = {};
    let total = 0, wsum = 0;
    for (const sig of this.signals) {
      const cur = sample[sig.key] ?? 0;
      const base = this.baseline[sig.key] ?? 0;
      let loss;
      if (sig.dir === 'growth') {
        loss = clamp01((cur - base) / (sig.norm ?? this.cfg.pauseGrowthNormSec ?? 3.0));
      } else {
        loss = base > 1e-6 ? clamp01(1 - cur / base) : 0;
      }
      losses[sig.key] = loss;
      total += sig.weight * loss;
      wsum += sig.weight;
    }
    const raw01 = clamp01(wsum > 0 ? total / wsum : 0);

    const a = this.cfg.ema ?? 0.4;
    this.value = this.samples.length === n ? raw01 : a * raw01 + (1 - a) * this.value;
    this.losses = losses;
    this.last = {
      velocityLoss: losses.velocity ?? losses.cadence ?? losses.height ?? losses.accuracy ?? 0,
      romLoss: losses.rom ?? losses.amplitude ?? losses.quality ?? losses.softness ?? 0,
      pauseGrowth: losses.gap ?? losses.tremor ?? 0,
    };

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
      family: this.family,
      losses: this.losses,
    };
  }
}
