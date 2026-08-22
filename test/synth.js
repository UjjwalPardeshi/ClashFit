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
