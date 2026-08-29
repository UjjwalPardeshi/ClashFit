// Bilateral asymmetry — the signal a physiotherapist actually looks for.
//
// We already compute the joint angle on both sides of the body every frame, and we were averaging
// them and throwing the difference away. That difference is the most clinically meaningful thing
// this pipeline can see, and it costs almost nothing to keep.
//
// The metric is the Limb Symmetry Index, which is standard in rehabilitation and
// return-to-activity assessment: the weaker side expressed as a percentage of the stronger.
//
//     LSI = (weaker / stronger) x 100
//
// It matters because a person compensating for one side will still produce a perfectly good
// average. Someone six months after a knee injury can hit depth, tempo and range on the combined
// reading while loading one leg noticeably less — and that is exactly the pattern that leads to
// re-injury. An averaged score is blind to it by construction.
//
// IMPORTANT, and non-negotiable: this is not a diagnosis. We report a measured ratio and a trend.
// Thresholds quoted in the literature exist, but we do not classify anyone against them and we do
// not tell anyone they are injured or cleared. Same discipline as docs/25-CLINIC-MODE.md §6.

export const Confidence = { GOOD: 'GOOD', FAIR: 'FAIR', POOR: 'POOR' };

/** Per-rep tracker. Fed the same filtered landmarks the rep machine sees. */
export class AsymmetryTracker {
  constructor() { this.reset(); }

  reset() { this.samples = []; }

  /** @param left angle in degrees @param right angle in degrees @param tMs */
  onFrame(left, right, tMs) {
    if (!Number.isFinite(left) || !Number.isFinite(right)) return;
    this.samples.push({ t: tMs, left, right });
    if (this.samples.length > 900) this.samples.shift();      // ~30s at 30fps
  }

  /**
   * Range of motion achieved by each side within one rep's window, and the ratio between them.
   * Returns null when the window is too thin to say anything honest about.
   */
  forRep(tStartMs, tEndMs) {
    const w = this.samples.filter((s) => s.t >= tStartMs && s.t <= tEndMs);
    if (w.length < 8) return null;

    const lRom = Math.max(...w.map((s) => s.left)) - Math.min(...w.map((s) => s.left));
    const rRom = Math.max(...w.map((s) => s.right)) - Math.min(...w.map((s) => s.right));
    if (!(lRom > 1) || !(rRom > 1)) return null;

    const weaker = Math.min(lRom, rRom);
    const stronger = Math.max(lRom, rRom);
    const lsi = (weaker / stronger) * 100;

    // A side-on camera foreshortens the far limb, which manufactures asymmetry that is not there.
    // Report how much to trust the reading rather than quietly presenting a number as fact.
    const meanGap = w.reduce((a, s) => a + Math.abs(s.left - s.right), 0) / w.length;
    const spread = Math.abs(lRom - rRom);
    const confidence = w.length >= 20 && meanGap < 25 ? Confidence.GOOD
                     : w.length >= 12 && meanGap < 40 ? Confidence.FAIR
                     : Confidence.POOR;

    return {
      leftRom: lRom,
      rightRom: rRom,
      weakerSide: lRom <= rRom ? 'left' : 'right',
      lsi,
      deficitPct: 100 - lsi,
      spreadDeg: spread,
      confidence,
      samples: w.length,
    };
  }
}

/** Session-level roll-up. One rep proves nothing; a consistent lean across a set is the signal. */
export function summariseAsymmetry(reps) {
  const usable = reps
    .map((r) => r.asymmetry)
    .filter((a) => a && a.confidence !== Confidence.POOR);
  if (usable.length < 3) {
    return { usable: usable.length, enough: false,
             note: 'Not enough clean bilateral frames to say anything about symmetry.' };
  }
  const meanLsi = usable.reduce((a, x) => a + x.lsi, 0) / usable.length;
  const leftWeak = usable.filter((x) => x.weakerSide === 'left').length;
  const consistency = Math.max(leftWeak, usable.length - leftWeak) / usable.length;
  const side = leftWeak > usable.length / 2 ? 'left' : 'right';

  return {
    usable: usable.length,
    enough: true,
    meanLsi,
    deficitPct: 100 - meanLsi,
    weakerSide: side,
    // A lean that flips between reps is noise. One that holds across a set is worth showing.
    consistency,
    consistent: consistency >= 0.7,
  };
}

/**
 * Phrasing. Observational only — never a verdict, never a diagnosis, never "cleared" or "at risk".
 * The number and the trend are the product; interpretation belongs to a professional.
 */
export function describeAsymmetry(sum) {
  if (!sum.enough) return sum.note;
  const d = Math.round(sum.deficitPct);
  if (!sum.consistent) {
    return `Sides are within noise of each other across this set — no consistent lean.`;
  }
  if (d < 8) return `Both sides moved through the same range, within ${d} percent.`;
  return `Your ${sum.weakerSide} side moved through ${d} percent less range than the other, `
       + `consistently across the set. Worth mentioning to a physiotherapist if it persists.`;
}
