// Regenerates the synthetic landmark traces in traces/.
//
//   node tools/make-trace.js              rewrite every trace and print what it replays to
//   node tools/make-trace.js --check      replay only; fail if a trace is stale (exit 1)
//
// Why this exists
// ---------------
// traces/synthetic-f3-to-failure.jsonl is judge-facing evidence: it is the set behind the
// fatigue curve in tools/fatigue-chart.html and behind the replay output quoted in README.md.
// It was originally produced by hand from the browser recorder, which meant that when the squat
// thresholds moved in config/, the trace quietly stopped reproducing the numbers printed beside
// it — 13 reps to GASSED became 7 reps to WORKING, and nothing failed to say so.
//
// A recorded trace of a real body cannot be regenerated. A *synthetic* one can, and this is the
// generator. The frames are the same synthWorldSet the test suite uses, so the trace and the
// tests describe the same body, and `--check` in CI turns "the evidence went stale" into a
// build failure instead of a discovery at a judging table.

import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { synthWorldSet } from '../test/synth.js';
import { parseTrace } from '../src/trace.js';
import { SessionEngine } from '../src/engine.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..');
const load = (p) => JSON.parse(readFileSync(join(ROOT, p), 'utf8'));

const KEEP = [11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32];
const r4 = (v) => Math.round(v * 1e4) / 1e4;
const r2 = (v) => Math.round(v * 100) / 100;

/**
 * The traces this repository ships.
 *
 * `bottom` is the knee angle at the first rep's deepest point and `decay` is how much range is
 * lost per rep. Both are chosen against the *current* config: the set has to start clear of
 * `bottomEnter` and end just inside it, because that is what a set carried to failure looks like
 * — the last rep is the one that barely counts, not the one that silently does not.
 */
const TRACES = [
  {
    file: 'traces/synthetic-f3-to-failure.jsonl',
    exercise: 'squat',
    note: 'synthetic F3 — set to failure',
    opts: { reps: 18, top: 172, bottom: 40, decay: 0.012, tempoDecay: 0.05, fps: 30, eccSec: 1.0, pauseSec: 0.3, gapSec: 0.5, restGrowth: 0.6 },
    // What this trace exists to demonstrate. Checked on every run.
    expect: { minReps: 12, band: 'GASSED' },
    // The chart that quotes this replay. Rewritten from the same run, so the picture a judge is
    // shown and the command they are invited to run can never drift apart again.
    chart: 'tools/fatigue-chart.html',
    // The Android app ships its own copy in assets and replays it in TraceReplayTest. Written
    // here rather than copied by hand, because a hand-copied mirror is a mirror that goes stale.
    mirrors: ['android/app/src/main/assets/traces/synthetic-f3-to-failure.jsonl'],
  },
];

const WORDS = [
  'Zero', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten',
  'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen',
  'Nineteen', 'Twenty',
];

/** Rewrites the chart's hard-coded series and its headline from a fresh replay. */
function writeChart(path, reps) {
  const file = join(ROOT, path);
  const before = readFileSync(file, 'utf8');
  const data = reps.map((r) => ({
    rep: r.repIndex,
    form: round(r.formScore, 2),
    verdict: r.verdict,
    depth: round(r.depth, 2),
    rom: round(r.rom, 2),
    tempo: round(r.tempo, 2),
    align: round(r.alignment, 2),
    vel: Math.round(r.concentricVelocity),
    fatigue: round(r.fatigue.value, 2),
    band: r.fatigue.band,
    dmg: r.damage,
  }));

  // Check the patterns are still there rather than that the text changed: on a second run
  // nothing changes, and treating "already correct" as a failure would cry wolf every time.
  const dataRe = /^const D = \[.*\];$/m;
  const headRe = /<h1>[A-Za-z]+ reps\./;
  if (!dataRe.test(before) || !headRe.test(before)) {
    console.error(`  could not rewrite ${path}; its data line or headline has changed shape`);
    return false;
  }
  const word = WORDS[reps.length] ?? String(reps.length);
  const after = before
    .replace(dataRe, `const D = ${JSON.stringify(data)};`)
    .replace(headRe, `<h1>${word} reps.`);
  if (after !== before) writeFileSync(file, after);
  return true;
}

const round = (v, places) => {
  const f = 10 ** places;
  return Math.round(v * f) / f;
};

function build(spec) {
  const frames = synthWorldSet(spec.opts);
  const head = JSON.stringify({
    type: 'clashfit-trace',
    v: 1,
    keep: KEEP,
    meta: { exercise: spec.exercise, note: spec.note, generator: 'tools/make-trace.js' },
    frames: frames.length,
  });
  const lines = frames.map(([world, tMs], i) => JSON.stringify({
    t: Math.round(tMs - frames[0][1]),
    lm: KEEP.map((idx) => {
      const p = world[idx];
      return p ? [r4(p.x), r4(p.y), r4(p.z ?? 0), r2(p.visibility ?? 1)] : null;
    }),
  }));
  return [head, ...lines].join('\n') + '\n';
}

function replay(text, exerciseId) {
  const exercises = readdirSync(join(ROOT, 'config/exercises'))
    .filter((f) => f.endsWith('.json') && f !== 'index.json')
    .map((f) => load(`config/exercises/${f}`));
  const store = {
    pose: load('config/pose.json'),
    combat: load('config/combat.json'),
    exercises: Object.fromEntries(exercises.map((e) => [e.id, e])),
  };
  const { frames } = parseTrace(text);
  const engine = new SessionEngine(store, exerciseId);
  for (const fr of frames) engine.frame(fr.world, null, fr.t);
  const s = engine.state();
  const reps = engine.reps;
  return {
    reps,
    bands: [...new Set(reps.map((r) => r.fatigue.band))],
    fatigue: s.fatigue,
    combat: s.combat,
    frames: frames.length,
  };
}

const check = process.argv.includes('--check');
let failed = false;

for (const spec of TRACES) {
  const text = check ? readFileSync(join(ROOT, spec.file), 'utf8') : build(spec);
  if (!check) {
    writeFileSync(join(ROOT, spec.file), text);
    for (const m of spec.mirrors ?? []) writeFileSync(join(ROOT, m), text);
  } else {
    for (const m of spec.mirrors ?? []) {
      if (readFileSync(join(ROOT, m), 'utf8') !== text) {
        failed = true;
        console.error(`  \x1b[31mMIRROR STALE\x1b[0m — ${m} differs from ${spec.file}`);
      }
    }
  }

  const r = replay(text, spec.exercise);
  const mean = (k) => (r.reps.length ? r.reps.reduce((a, x) => a + x[k], 0) / r.reps.length : 0);
  const endBand = r.fatigue.band;

  console.log(`\n${spec.file}  ·  ${spec.exercise}  ·  ${r.frames} frames`);
  console.log(`  reps ${r.reps.length}   mean form ${mean('formScore').toFixed(3)}   ` +
              `depth ${mean('depth').toFixed(2)}  rom ${mean('rom').toFixed(2)}  ` +
              `tempo ${mean('tempo').toFixed(2)}  align ${mean('alignment').toFixed(2)}`);
  console.log(`  fatigue ${r.fatigue.value.toFixed(3)} (${endBand})   bands seen: ${r.bands.join(' -> ') || '—'}`);
  console.log(`  boss ${r.combat.hp}/${r.combat.maxHp}   damage ${r.combat.totalDamage}` +
              `${r.combat.mercyActive ? '   MERCY FIRED' : ''}`);

  const problems = [];
  if (r.reps.length < spec.expect.minReps) {
    problems.push(`expected at least ${spec.expect.minReps} reps, replayed ${r.reps.length}`);
  }
  if (endBand !== spec.expect.band) {
    problems.push(`expected the set to end ${spec.expect.band}, it ended ${endBand}`);
  }
  if (problems.length) {
    failed = true;
    console.error(`  \x1b[31mSTALE\x1b[0m — ${problems.join('; ')}`);
    if (check) console.error('  Run `node tools/make-trace.js` to regenerate against the current config.');
  } else {
    console.log(`  \x1b[32mok\x1b[0m — ${check ? 'reproduces' : 'written'} against the current config`);
  }

  if (!check && spec.chart && !problems.length) {
    if (writeChart(spec.chart, r.reps)) console.log(`  ${spec.chart} rewritten from this replay`);
  }
}

console.log('');
process.exit(failed ? 1 : 0);
