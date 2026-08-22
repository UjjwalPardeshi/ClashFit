// Form scoring: four named geometric quantities, never a vague "form quality".
// This is the answer to the jury question we will definitely be asked.
// Spec: docs/05-POSE-ENGINE-SPEC.md §5

import { kneeTracking, torsoLine } from './geometry.js';

export const clamp01 = (v) => (Number.isFinite(v) ? Math.min(1, Math.max(0, v)) : 0);

/** Depth: how far past the target, relative to the player's own rest position.
 *  Superlinear (default exponent 1.5) — a quarter rep is worth much less than a quarter of a
 *  full one, which is both true in training terms and what makes the damage gap visible. */
export function depthScore(raw, exponent = 1.5) {
  const span = raw.uTopRef - raw.uTarget;
  if (Math.abs(span) < 1e-6) return 0;
  const linear = clamp01((raw.uTopRef - raw.uMin) / span);
  return clamp01(Math.pow(linear, exponent));
}

/** Range of motion against the player's own calibrated baseline. This is what makes
 *  scoring fair across body types — everyone is measured against themselves. */
export function romScore(raw, romBaselineU) {
  if (!romBaselineU || romBaselineU < 1e-6) return 1;
  return clamp01((raw.uMax - raw.uMin) / romBaselineU);
}

/** Tempo: reward a controlled eccentric and a real pause, punish bouncing.
 *  NOTE: tEccSec is the MEASURED window (topExit crossing -> bottomEnter crossing), which is
 *  roughly a third of the full descent. eccentricTargetSec must be set in those terms — a 0.80s
 *  target silently demanded a ~2.4s descent and capped tempo at ~0.6 for perfectly good reps. */
export function tempoScore(raw, cfg) {
  const target = cfg.eccentricTargetSec ?? 0.8;
  const pauseTarget = cfg.bottomPauseSec ?? 0.12;
  const tEcc = raw.tEccSec >= target ? 1 : clamp01(raw.tEccSec / target);
  const tPause = raw.tBottomSec >= pauseTarget ? 1 : 0.6;
  return clamp01(0.7 * tEcc + 0.3 * tPause);
}

/** Alignment: exercise-specific, and the noisiest of the four — which is why it carries
 *  the smallest weight and why its weight can be zeroed in config on the day. */
export function alignmentScore(align, sample) {
  if (!align || align.type === 'none' || !sample) return 1;
  if (align.type === 'KNEE_TRACKING') {
    const off = sample.kneeOffset;
    if (!Number.isFinite(off)) return 1;
    const full = align.fullMarksOffset ?? 0.15, zero = align.zeroMarksOffset ?? 0.45;
    return clamp01((zero - off) / (zero - full));
  }
  if (align.type === 'TORSO_LINE') {
    const deg = sample.torsoDeg;
    if (!Number.isFinite(deg)) return 1;
    const full = align.fullMarksDeg ?? 172, zero = align.zeroMarksDeg ?? 150;
    return clamp01((deg - zero) / (full - zero));
  }
  return 1;
}

/** Combine into a single 0..1 score plus the sub-scores, which we keep for the coach. */
export function scoreRep(raw, exercise, romBaselineU, alignSample) {
  const w = exercise.form.weights;
  const depth = depthScore(raw, exercise.form.depthExponent ?? 1.5);
  const rom = romScore(raw, romBaselineU);
  const tempo = tempoScore(raw, exercise.form.tempo ?? {});
  const alignment = alignmentScore(exercise.form.alignment, alignSample);

  const sum = (w.depth ?? 0) + (w.rom ?? 0) + (w.tempo ?? 0) + (w.alignment ?? 0);
  const norm = sum > 0 ? sum : 1;
  const formScore = clamp01(
    ((w.depth ?? 0) * depth + (w.rom ?? 0) * rom + (w.tempo ?? 0) * tempo + (w.alignment ?? 0) * alignment) / norm
  );

  // The single weakest sub-score names the fault. We do not ask the model to diagnose —
  // we ask it to phrase a diagnosis we already made. docs/06-AI-COACH-SPEC.md §3
  const parts = [['depth', depth], ['rom', rom], ['tempo', tempo]];
  if ((w.alignment ?? 0) > 0) parts.push(['alignment', alignment]);
  parts.sort((a, b) => a[1] - b[1]);

  return { depth, rom, tempo, alignment, formScore, reason: parts[0][0] };
}

export function verdict(formScore, bands = { clean: 0.8, ok: 0.55 }) {
  if (formScore >= bands.clean) return 'CLEAN';
  if (formScore >= bands.ok) return 'OK';
  return 'SHALLOW';
}

/** Alignment sample taken at the deepest point of the rep. */
export function alignmentSample(lms, side, type) {
  if (type === 'KNEE_TRACKING') return { kneeOffset: kneeTracking(lms, side) };
  if (type === 'TORSO_LINE') return { torsoDeg: torsoLine(lms, side) };
  return null;
}
