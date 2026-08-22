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

/** A whole set. `decay` shrinks range and slows the concentric, rep by rep — a fatiguing set. */
export function synthSet({ reps, top, bottom, fps = 30, decay = 0, gapSec = 0.4, restGrowth = 0, eccSec = 1.0, pauseSec = 0.3 }) {
  const all = [];
  let t = 0;
  // settle at the top so the machine starts in TOP
  for (let i = 0; i < fps; i++) { all.push([top, t]); t += 1000 / fps; }
  for (let r = 0; r < reps; r++) {
    const k = decay * r;
    const b = bottom + (top - bottom) * Math.min(0.8, k);        // range collapses
    const con = 0.8 * (1 + Math.min(3, k * 6));                // concentric slows
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
export function synthWorldFrame(angleDeg) {
  const P = (x, y, z = 0, v = 0.95) => ({ x, y, z, visibility: v });
  const lm = Array.from({ length: 33 }, () => P(0, 0, 0, 0));
  const a = (angleDeg * Math.PI) / 180;
  for (const side of [0, 1]) {
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
  const { top = 172 } = opts;
  const frames = [];
  let t = 0;
  const step = 1000 / (opts.fps ?? 30);
  for (let i = 0; i < 45; i++) { frames.push([synthWorldFrame(top), t]); t += step; }  // settle
  for (const [angle, ms] of synthSet({ ...opts, top })) frames.push([synthWorldFrame(angle), ms + t]);
  return frames;
}
