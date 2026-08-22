// Threshold tuning against recorded traces.
//
// The naive loop is: do fifteen squats, edit JSON, do fifteen more. That is slow, it is
// inconsistent because you never repeat a set exactly, and at the event you will not have the
// time or the legs for it.
//
// Record each fixture ONCE, then sweep offline in seconds. The same trace replays identically
// every time, so a threshold change is measured rather than guessed.
//
//   node tools/tune.js traces/f1-clean.jsonl --expect 10
//   node tools/tune.js traces/*.jsonl --exercise squat --expect 10 --apply
//
// --expect is ground truth: how many reps you actually did. Everything is scored against it.

import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseTrace } from '../src/trace.js';
import { SessionEngine } from '../src/engine.js';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const load = (p) => JSON.parse(readFileSync(join(ROOT, p), 'utf8'));

const argv = process.argv.slice(2);
const flag = (name, dflt = null) => {
  const i = argv.indexOf(`--${name}`);
  return i >= 0 && argv[i + 1] && !argv[i + 1].startsWith('--') ? argv[i + 1] : dflt;
};
const has = (name) => argv.includes(`--${name}`);
const files = argv.filter((a) => !a.startsWith('--') && a.endsWith('.jsonl'));

if (!files.length) {
  console.error(`usage: node tools/tune.js <trace.jsonl…> [--exercise squat] [--expect N] [--apply] [--wide]

  --expect N   how many reps you actually did in the trace. Without it, tuning has no target.
  --apply      write the winning thresholds back into the exercise config
  --wide       search a wider grid (slower, use when the current values are badly off)`);
  process.exit(2);
}

const exerciseId = flag('exercise') ?? 'squat';
const expect = flag('expect') ? Number(flag('expect')) : null;
const wide = has('wide');

const exercises = Object.fromEntries(
  readdirSync(join(ROOT, 'config/exercises'))
    .filter((f) => f.endsWith('.json') && f !== 'index.json')
    .map((f) => { const e = load(`config/exercises/${f}`); return [e.id, e]; }));

const base = { pose: load('config/pose.json'), combat: load('config/combat.json'), exercises };
const spec = exercises[exerciseId];
if (!spec) { console.error(`unknown exercise: ${exerciseId}`); process.exit(2); }
if (spec.family !== 'REP_CYCLE') { console.error('tuning currently covers REP_CYCLE exercises'); process.exit(2); }

const traces = files.map((f) => {
  const t = parseTrace(readFileSync(f.startsWith('/') ? f : join(ROOT, f), 'utf8'));
  return { name: basename(f), ...t };
});

/** Replay every trace with one candidate detector config. */
function run(detector) {
  const cfg = { ...base, exercises: { ...exercises, [exerciseId]: { ...spec, detector } } };
  return traces.map(({ name, frames, meta }) => {
    const e = new SessionEngine(cfg, exerciseId);
    for (const f of frames) e.frame(f.world, null, f.t);
    const reps = e.reps;
    const form = reps.length ? reps.reduce((a, r) => a + r.formScore, 0) / reps.length : 0;
    const depth = reps.length ? reps.reduce((a, r) => a + r.depth, 0) / reps.length : 0;
    return { name, expect: meta?.expect ?? expect, count: reps.length, form, depth };
  });
}

const d0 = spec.detector;
const dec = d0.topEnter > d0.bottomEnter;      // does the angle decrease into the effort position
const step = (v, k) => v + (dec ? k : -k);

const grid = [];
const teRange = wide ? [-12, -8, -4, 0, 4, 8, 12] : [-6, -3, 0, 3, 6];
const beRange = wide ? [-16, -10, -5, 0, 5, 10, 16] : [-8, -4, 0, 4, 8];
const hystRange = wide ? [5, 8, 11, 14] : [Math.abs(d0.topEnter - d0.topExit), 8, 11];

for (const te of teRange)
  for (const be of beRange)
    for (const hy of hystRange) {
      const topEnter = step(d0.topEnter, -te);
      const bottomEnter = step(d0.bottomEnter, be);
      grid.push({
        ...d0,
        topEnter, topExit: step(topEnter, hy),
        bottomEnter, bottomExit: step(bottomEnter, -hy),
      });
    }

console.log(`\n  ${exerciseId} · ${traces.length} trace(s) · ${grid.length} candidates` +
            (expect ? ` · expecting ${expect} reps` : ' · no --expect, reporting counts only'));
console.log(`  baseline: top ${d0.topEnter}/${d0.topExit}  bottom ${d0.bottomEnter}/${d0.bottomExit}\n`);

const baselineRuns = run(d0);
for (const r of baselineRuns) {
  const mark = r.expect == null ? ' ' : r.count === r.expect ? '\x1b[32m✓\x1b[0m' : '\x1b[31m✗\x1b[0m';
  console.log(`  ${mark} baseline  ${r.name.padEnd(34)} ${String(r.count).padStart(3)} reps` +
              (r.expect != null ? ` (want ${r.expect})` : '') +
              `   form ${r.form.toFixed(3)}  depth ${r.depth.toFixed(3)}`);
}

if (expect == null) { console.log('\n  Pass --expect N to search for better thresholds.\n'); process.exit(0); }

const scored = grid.map((detector) => {
  const runs = run(detector);
  const exact = runs.filter((r) => r.count === (r.expect ?? expect)).length;
  const err = runs.reduce((a, r) => a + Math.abs(r.count - (r.expect ?? expect)), 0);
  const form = runs.reduce((a, r) => a + r.form, 0) / runs.length;
  return { detector, runs, exact, err, form };
}).sort((a, b) => b.exact - a.exact || a.err - b.err || b.form - a.form);

const best = scored[0];

/**
 * Robustness matters more than a single exact hit. A setting that counts correctly but sits one
 * degree from miscounting will fail on a real body in different light. Count how many neighbours
 * in the grid also get it exactly right.
 */
function robustness(cand) {
  const near = scored.filter((s) =>
    Math.abs(s.detector.topEnter - cand.detector.topEnter) <= 4 &&
    Math.abs(s.detector.bottomEnter - cand.detector.bottomEnter) <= 6);
  return near.length ? near.filter((s) => s.exact === traces.length).length / near.length : 0;
}

const perfect = scored.filter((s) => s.exact === traces.length);
const ranked = (perfect.length ? perfect : scored.slice(0, 12))
  .map((s) => ({ ...s, robust: robustness(s) }))
  .sort((a, b) => b.robust - a.robust || b.form - a.form);

console.log(`\n  ${perfect.length} of ${grid.length} candidates matched every trace exactly.\n`);
console.log('  rank  top        bottom     robust  mean form   counts');
console.log('  ' + '-'.repeat(62));
for (const [i, s] of ranked.slice(0, 8).entries()) {
  const d = s.detector;
  console.log(
    `  ${String(i + 1).padStart(4)}  ${String(d.topEnter).padStart(3)}/${String(d.topExit).padEnd(4)}  ` +
    `${String(d.bottomEnter).padStart(3)}/${String(d.bottomExit).padEnd(4)}  ` +
    `${(s.robust * 100).toFixed(0).padStart(5)}%  ${s.form.toFixed(3).padStart(9)}   ` +
    s.runs.map((r) => r.count).join(','));
}

const win = ranked[0];
if (!win || win.exact < traces.length) {
  console.log('\n  \x1b[33mNo candidate matched every trace.\x1b[0m Try --wide, or check that --expect is right' +
              ' and the trace is of the exercise you named.\n');
  process.exit(1);
}

console.log(`\n  \x1b[32mRecommended\x1b[0m  top ${win.detector.topEnter}/${win.detector.topExit}` +
            `  bottom ${win.detector.bottomEnter}/${win.detector.bottomExit}` +
            `  (${(win.robust * 100).toFixed(0)}% of nearby settings also correct)`);

if (has('apply')) {
  const p = join(ROOT, 'config/exercises', `${exerciseId}.json`);
  const rec = JSON.parse(readFileSync(p, 'utf8'));
  Object.assign(rec.detector, {
    topEnter: win.detector.topEnter, topExit: win.detector.topExit,
    bottomEnter: win.detector.bottomEnter, bottomExit: win.detector.bottomExit,
  });
  writeFileSync(p, JSON.stringify(rec, null, 2) + '\n');
  console.log(`  written to config/exercises/${exerciseId}.json`);
} else {
  console.log('  re-run with --apply to write it into the config.');
}
console.log('');
