// Synthetic rep generator — lets the whole engine be tested with no camera and no body.
// This is the same seam that lets a recorded trace replay through the engine at the event
// when the camera is failing in front of judges. docs/14-TEST-PLAN.md §1

/** Samples one rep at `fps`, moving top -> bottom -> top with the given phase durations. */
export function synthRep({ top, bottom, eccSec = 1.0, pauseSec = 0.3, conSec = 0.8, fps = 30, t0 = 0 }) {
  const out = [];
  const step = 1000 / fps;
  let t = t0;
  const lerp = (a, b, k) => a + (b - a) * k;

  const nEcc = Math.max(1, Math.round(eccSec * fps));
  for (let i = 1; i <= nEcc; i++) { out.push([lerp(top, bottom, i / nEcc), t]); t += step; }
  const nPause = Math.max(1, Math.round(pauseSec * fps));
  for (let i = 0; i < nPause; i++) { out.push([bottom, t]); t += step; }
  const nCon = Math.max(1, Math.round(conSec * fps));
  for (let i = 1; i <= nCon; i++) { out.push([lerp(bottom, top, i / nCon), t]); t += step; }
  return { samples: out, tEnd: t };
}

/**
 * A whole set. `decay` shrinks the range rep by rep and, unless `tempoDecay` says otherwise, slows
 * the concentric by the same amount — a fatiguing set.
 *
 * The two are separable because they run into each other. Range loss is what eventually stops a rep
 * counting, since the movement no longer reaches the threshold; concentric slow-down is what the
 * fatigue estimator actually reads for a squat. Tied together, the only way to show real velocity
 * loss was to collapse the range until the late reps stopped counting, and those are exactly the
 * reps the evidence is about.
 */
export function synthSet({ reps, top, bottom, fps = 30, decay = 0, tempoDecay = null, gapSec = 0.4, restGrowth = 0, eccSec = 1.0, pauseSec = 0.3 }) {
  const all = [];
  let t = 0;
  // settle at the top so the machine starts in TOP
  for (let i = 0; i < fps; i++) { all.push([top, t]); t += 1000 / fps; }
  for (let r = 0; r < reps; r++) {
    const k = decay * r;
    const b = bottom + (top - bottom) * Math.min(0.8, k);        // range collapses
    const kt = (tempoDecay === null ? decay : tempoDecay) * r;
    const con = 0.8 * (1 + Math.min(3, kt * 6));                // concentric slows
    const rep = synthRep({ top, bottom: b, eccSec, pauseSec, conSec: con, fps, t0: t });
    all.push(...rep.samples);
    t = rep.tEnd;
    const gap = gapSec + restGrowth * r;
    for (let i = 0; i < Math.round(gap * fps); i++) { all.push([top, t]); t += 1000 / fps; }
  }
  return all;
}

/** Synthetic WORLD landmarks for a given hip-knee-ankle angle. Drives the full SessionEngine,
 *  not just the FSM — so mode logic, calibration and ghost sync are all covered too. */
export function synthWorldFrame(angleDeg, rightBiasDeg = 0) {
  const P = (x, y, z = 0, v = 0.95) => ({ x, y, z, visibility: v });
  const lm = Array.from({ length: 33 }, () => P(0, 0, 0, 0));
  for (const side of [0, 1]) {
    // side 1 is the right; a bias lets us synthesise a limb moving through less range
    const a = (((side === 1 ? angleDeg + rightBiasDeg : angleDeg)) * Math.PI) / 180;
    const sx = side ? 0.12 : -0.12;
    lm[11 + side] = P(sx, -0.45);
    lm[13 + side] = P(sx, -0.25, 0, 0.9);
    lm[15 + side] = P(sx, -0.05, 0, 0.9);
    lm[23 + side] = P(sx, 0.0);
    lm[25 + side] = P(sx, 0.42);
    lm[27 + side] = P(sx + 0.42 * Math.sin(Math.PI - a), 0.42 + 0.42 * Math.cos(Math.PI - a));
    lm[29 + side] = lm[27 + side];
    lm[31 + side] = lm[27 + side];
  }
  return lm;
}

/** A full set as (worldLandmarks, tMs) frames, ready to push into SessionEngine.frame(). */
export function synthWorldSet(opts) {
  const { top = 172, rightBias = 0 } = opts;
  const frames = [];
  let t = 0;
  const step = 1000 / (opts.fps ?? 30);
  for (let i = 0; i < 45; i++) { frames.push([synthWorldFrame(top, 0), t]); t += step; }  // settle
  // rightBias shortens the right side's travel by a fraction of the rep's depth, which is what a
  // person guarding one leg actually does.
  for (const [angle, ms] of synthSet({ ...opts, top })) {
    const bias = rightBias ? (top - angle) * rightBias : 0;
    frames.push([synthWorldFrame(angle, bias), ms + t]);
  }
  return frames;
}

// ---------------------------------------------------------------- stick figure
// A coherent 2D skeleton driven by joint angles, so the non-REP_CYCLE detectors can be tested
// against known geometry rather than assumed to work. Self-validating: the tests assert that
// angle3 reads back the angles this builder was asked for.

const D2R = Math.PI / 180;
const dir = (deg) => ({ x: Math.cos(deg * D2R), y: Math.sin(deg * D2R) });
const add = (p, d, len) => ({ x: p.x + d.x * len, y: p.y + d.y * len, z: 0, visibility: 0.95 });

/**
 * @param {object} o
 *  knee      angle at the knee (hip-knee-ankle)
 *  hipTorso  angle at the hip (shoulder-hip-knee)
 *  elbow     angle at the elbow (shoulder-elbow-wrist)
 *  femurDir  direction hip->knee, degrees (90 = straight down)
 *  lift      whole-body vertical offset, for ballistic tests
 */
export function stick({ knee = 176, hipTorso = 178, elbow = 172, femurDir = 90, lift = 0, wristX = 0 } = {}) {
  const P = (x, y, v = 0.95) => ({ x, y: y - lift, z: 0, visibility: v });
  const lm = Array.from({ length: 33 }, () => P(0, 0, 0));

  const hip = { x: 0, y: 0, z: 0, visibility: 0.95 };
  const kneeP = add(hip, dir(femurDir), 0.42);
  const ankleP = add(kneeP, dir(femurDir + 180 - knee), 0.42);
  const shoulderP = add(hip, dir(femurDir + hipTorso), 0.45);
  const elbowDir = femurDir + hipTorso + 175;
  const elbowP = add(shoulderP, dir(elbowDir), 0.28);
  const wristP = add(elbowP, dir(elbowDir + 180 - elbow), 0.26);

  const put = (idx, p) => { lm[idx] = P(p.x + wristX * 0, p.y); };
  for (const side of [0, 1]) {
    put(23 + side, hip); put(25 + side, kneeP); put(27 + side, ankleP);
    put(11 + side, shoulderP); put(13 + side, elbowP); put(15 + side, wristP);
    put(29 + side, ankleP); put(31 + side, ankleP);
  }
  return lm;
}

/** Frames holding one posture, with optional positional noise for tremor tests. */
export function holdFrames(opts, seconds, { fps = 30, noise = 0, t0 = 0 } = {}) {
  const out = [];
  const n = Math.round(seconds * fps);
  let seed = 12345;
  const rnd = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff - 0.5);
  for (let i = 0; i < n; i++) {
    const lm = stick(opts);
    if (noise) for (const p of lm) { p.x += rnd() * noise; p.y += rnd() * noise; }
    out.push([lm, t0 + (i * 1000) / fps]);
  }
  return out;
}

/** Periodic motion for the cadence detector: one landmark axis oscillating at a given rpm. */
export function cadenceFrames({ rpm, seconds, amplitude = 0.12, fps = 30, t0 = 0, joint = 'WRIST', axis = 'y' }) {
  const out = [];
  const n = Math.round(seconds * fps);
  const idx = { WRIST: [15, 16], KNEE: [25, 26], ANKLE: [27, 28] }[joint] ?? [15, 16];
  for (let i = 0; i < n; i++) {
    const t = t0 + (i * 1000) / fps;
    const phase = (2 * Math.PI * rpm * (t - t0)) / 60000;
    const v = Math.sin(phase) * amplitude;
    const lm = stick({});
    for (const j of idx) { if (axis === 'x') lm[j].x += v; else lm[j].y += v; }
    out.push([lm, t]);
  }
  return out;
}

/** A jump: hip rises in IMAGE space (world landmarks are hip-centred and cannot see it),
 *  with knee flexion on landing. Returns [world, image, t] triples. */
export function jumpFrames({ heightNorm = 0.09, fps = 30, t0 = 0, standSeconds = 1.2,
                             flightSeconds = 0.5, landKnee = 130, settleSeconds = 0.6 } = {}) {
  const out = [];
  let t = t0;
  const step = 1000 / fps;
  const push = (kneeDeg, riseNorm) => {
    const world = stick({ knee: kneeDeg });
    const image = Array.from({ length: 33 }, (_, i) => ({
      x: 0.5, y: 0.6 - riseNorm, z: 0, visibility: 0.95,
    }));
    // hip and ankle need a real image-space separation for the metric scale calibration
    for (const s of [0, 1]) {
      image[23 + s] = { x: 0.5, y: 0.60 - riseNorm, z: 0, visibility: 0.95 };
      image[27 + s] = { x: 0.5, y: 0.88 - riseNorm, z: 0, visibility: 0.95 };
    }
    out.push([world, image, t]); t += step;
  };
  for (let i = 0; i < standSeconds * fps; i++) push(176, 0);
  const nf = Math.round(flightSeconds * fps);
  for (let i = 0; i < nf; i++) push(150, Math.sin((i / nf) * Math.PI) * heightNorm);
  for (let i = 0; i < 0.3 * fps; i++) push(landKnee, 0);
  for (let i = 0; i < settleSeconds * fps; i++) push(176, 0);
  return out;
}
