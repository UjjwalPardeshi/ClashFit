// Headless trace replay. Record a set in the browser, then push it through the exact same
// engine with no camera and no body — for regression after a threshold change, for tuning
// without doing 400 squats, and as evidence to show a judge.
//
//   node test/replay.js traces/f3-to-failure.jsonl [exerciseId]
//   node test/replay.js traces/*.jsonl --brief

import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, basename } from 'node:path';
import { parseTrace } from '../src/trace.js';
import { SessionEngine } from '../src/engine.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..');
const load = (p) => JSON.parse(readFileSync(join(ROOT, p), 'utf8'));

const EXERCISES = readdirSync(join(ROOT, 'config/exercises'))
  .filter((f) => f.endsWith('.json'))
  .map((f) => load(`config/exercises/${f}`));

const store = {
  pose: load('config/pose.json'),
  combat: load('config/combat.json'),
  exercises: Object.fromEntries(EXERCISES.map((e) => [e.id, e])),
};

const args = process.argv.slice(2);
const brief = args.includes('--brief');
const files = args.filter((a) => !a.startsWith('--'));
if (!files.length) {
  console.error('usage: node test/replay.js <trace.jsonl> [exerciseId] [--brief]');
  process.exit(2);
}

// A bare exercise id may follow the file.
const maybeId = files[1];
const explicitId = maybeId && store.exercises[maybeId] ? maybeId : null;
const traceFiles = explicitId ? [files[0]] : files;

for (const f of traceFiles) {
  const text = readFileSync(f.startsWith('/') ? f : join(ROOT, f), 'utf8');
  const { meta, frames } = parseTrace(text);
  const id = explicitId ?? meta.exercise ?? 'squat';
  if (!store.exercises[id]) { console.error(`unknown exercise "${id}" in ${f}`); continue; }

  const engine = new SessionEngine(store, id);
  for (const fr of frames) engine.frame(fr.world, null, fr.t);

  const s = engine.state();
  const reps = engine.reps;
  const mean = (k) => (reps.length ? reps.reduce((a, r) => a + r[k], 0) / reps.length : 0);
  const bands = [...new Set(reps.map((r) => r.fatigue.band))];

  console.log(`\n\x1b[1m${basename(f)}\x1b[0m  ·  ${id}  ·  ${frames.length} frames  ·  ${(frames.at(-1)?.t ?? 0) / 1000}s`);
  console.log(`  reps ${reps.length}   mean form ${mean('formScore').toFixed(3)}   ` +
              `depth ${mean('depth').toFixed(2)}  rom ${mean('rom').toFixed(2)}  ` +
              `tempo ${mean('tempo').toFixed(2)}  align ${mean('alignment').toFixed(2)}`);
  console.log(`  fatigue ${s.fatigue.value.toFixed(3)} (${s.fatigue.band})   ` +
              `bands seen: ${bands.join(' -> ') || '—'}`);
  console.log(`  boss ${s.combat.hp}/${s.combat.maxHp}   damage ${s.combat.totalDamage}` +
              `${s.combat.mercyActive ? '   MERCY FIRED' : ''}${s.combat.dead ? '   DEAD' : ''}`);

  if (brief) continue;
  console.log('  ' + '-'.repeat(74));
  console.log('  #   form  verdict   depth  rom   tempo align  vel°/s  fatigue band     dmg');
  for (const r of reps) {
    console.log(
      `  ${String(r.repIndex).padStart(2)}  ${r.formScore.toFixed(2)}  ${r.verdict.padEnd(8)} ` +
      `${r.depth.toFixed(2)}   ${r.rom.toFixed(2)}  ${r.tempo.toFixed(2)}  ${r.alignment.toFixed(2)}  ` +
      `${String(Math.round(r.concentricVelocity)).padStart(5)}   ${r.fatigue.value.toFixed(2)}  ` +
      `${r.fatigue.band.padEnd(8)} ${String(r.damage).padStart(4)}`
    );
  }
}
console.log('');
